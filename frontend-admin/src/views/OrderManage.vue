<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, CopyDocument } from '@element-plus/icons-vue'
import { adminOrderApi, statsApi, shopApi } from '@/api'
import { getUser } from '@/utils/auth'

// 超管能看全部店铺订单；但只能对自己绑定的店铺（如官方旗舰店）发货 / 标记完成
const isPlatform = computed(() => getUser()?.role === 'ADMIN')
const ownShopId = ref(null)
// 是否为本店订单（可跑发货 / 完成）：商家看到的就是本店；超管只有自己名下店铺的订单才可操作
const canOperate = (row) => {
  if (!row) return false
  if (!isPlatform.value) return true
  return !!row.shopId && !!ownShopId.value && row.shopId === ownShopId.value
}

const route = useRoute()
const router = useRouter()
const list = ref([])
const query = reactive({ status: '', keyword: '' })
const page = reactive({ current: 1, size: 10, total: 0 })
const loading = ref(false)
const shipVisible = ref(false)
const shipRow = ref(null)
const shipForm = reactive({ trackingCompany: '', trackingNo: '' })
const shipLoading = ref(false)
const expressList = ['顺丰速运', '圆通速递', '中通快递', '申通快递', '韵达速递', '京东物流', 'EMS', '德邦快递']
const counts = ref({})
const tableRef = ref()
const selectedRows = ref([])

const label = s => ({ PENDING_PAYMENT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s)
const pill = s => ({ PENDING_PAYMENT: 'pending', PAID: 'paid', SHIPPED: 'shipped', COMPLETED: 'completed', CANCELLED: 'cancelled' }[s] || 'mute')
const fmt = t => String(t || '').replace('T', ' ').slice(0, 19) || '—'

const tabs = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const load = async () => {
  loading.value = true
  try {
    const r = await adminOrderApi.page({
      status: query.status || undefined,
      current: page.current,
      size: page.size
    })
    list.value = r.data.records || []
    page.total = r.data.total || 0
    selectedRows.value = []
    tableRef.value?.clearSelection()
  } catch (e) {
    list.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

const loadCounts = async () => {
  try {
    const r = await statsApi.overview()
    counts.value = r.data?.ordersCount || {}
  } catch (e) { counts.value = {} }
}

const countOf = v => (v === '' ? undefined : Number(counts.value?.[v] || 0))

// 关键词只对当前页生效（后端 admin/list 未提供 keyword 参数），命中订单号 / 收货人 / 账号
const keyword = computed(() => query.keyword.trim().toLowerCase())
const rows = computed(() => {
  if (!keyword.value) return list.value
  return list.value.filter(o =>
    String(o.orderNo || '').toLowerCase().includes(keyword.value)
    || String(o.receiverName || '').toLowerCase().includes(keyword.value)
    || String(o.username || '').toLowerCase().includes(keyword.value)
  )
})

const itemsOf = o => o?.items || []
const itemCount = o => itemsOf(o).reduce((s, i) => s + Number(i.quantity || 0), 0)

const switchTab = v => {
  query.status = v
  page.current = 1
  // 同步到地址栏，便于从概览页跳转 / 分享筛选结果
  router.replace({ path: '/orders', query: v ? { status: v } : {} })
  load()
}
const reload = () => { page.current = 1; load(); loadCounts() }

/* ---------- 详情抽屉 ---------- */
const detail = ref(null)
const detailOpen = ref(false)
const openDetail = row => { detail.value = row; detailOpen.value = true }

const timeline = computed(() => {
  const o = detail.value
  if (!o) return []
  const steps = [
    { label: '提交订单', time: o.createdAt, done: true },
    { label: '完成支付', time: o.paidAt, done: !!o.paidAt },
    { label: '商家发货', time: o.shippedAt, done: !!o.shippedAt },
    { label: '交易完成', time: o.completedAt, done: !!o.completedAt }
  ]
  if (o.status === 'CANCELLED') {
    steps.push({ label: '订单取消', time: o.cancelledAt, done: true })
  }
  return steps
})

const paidAmount = o => Number(o?.totalAmount || 0) - Number(o?.discountAmount || 0)

/* ---------- 操作 ---------- */
const openShip = row => {
  shipRow.value = row
  shipForm.trackingCompany = ''
  shipForm.trackingNo = ''
  shipVisible.value = true
}
const submitShip = async () => {
  if (!shipForm.trackingNo.trim()) { ElMessage.warning('请填写快递单号'); return }
  shipLoading.value = true
  try {
    await adminOrderApi.ship(shipRow.value.orderNo, {
      trackingCompany: shipForm.trackingCompany.trim() || undefined,
      trackingNo: shipForm.trackingNo.trim()
    })
    ElMessage.success('已发货，等待用户确认收货')
    shipVisible.value = false
    detailOpen.value = false
    load(); loadCounts()
    window.dispatchEvent(new CustomEvent('admin:orders-changed'))
  } finally { shipLoading.value = false }
}
const onComplete = async row => {
  await ElMessageBox.confirm(`将订单 ${row.orderNo} 标记为已完成？`, '完成确认', {
    type: 'warning', confirmButtonText: '标记完成', cancelButtonText: '取消'
  })
  await adminOrderApi.complete(row.orderNo)
  ElMessage.success('订单已完成')
  detailOpen.value = false
  load(); loadCounts()
  window.dispatchEvent(new CustomEvent('admin:orders-changed'))
}

// ---- 删除历史订单（仅已完成 / 已取消，且限本店订单） ----
const canDelete = row => canOperate(row) && ['COMPLETED', 'CANCELLED'].includes(row.status)
const onSelectionChange = rows => { selectedRows.value = rows }

const onDelete = async row => {
  await ElMessageBox.confirm(
    `确认删除订单 ${row.orderNo}？删除后用户端与管理端都不再显示，此操作不可恢复。`,
    '删除订单',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  await adminOrderApi.remove(row.orderNo)
  ElMessage.success('订单已删除')
  detailOpen.value = false
  if (list.value.length === 1 && page.current > 1) page.current -= 1
  load(); loadCounts()
  window.dispatchEvent(new CustomEvent('admin:orders-changed'))
}

const onBatchDelete = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${rows.length} 笔历史订单？删除后不可恢复。`,
    '批量删除订单',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  const r = await adminOrderApi.batchDelete(rows.map(o => o.orderNo))
  ElMessage.success(`已删除 ${r.data ?? rows.length} 笔订单`)
  if (list.value.length === rows.length && page.current > 1) page.current -= 1
  load(); loadCounts()
  window.dispatchEvent(new CustomEvent('admin:orders-changed'))
}

const copyNo = async no => {
  try {
    await navigator.clipboard.writeText(no)
    ElMessage.success('订单号已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择')
  }
}

const exportCsv = async () => {
  try {
    const res = await adminOrderApi.exportCsv()
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `orders_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  const s = String(route.query.status || '')
  if (tabs.some(t => t.value === s)) query.status = s
  try {
    const c = await shopApi.context()
    ownShopId.value = c.data?.shopId || null
  } catch (e) { ownShopId.value = null }
  load()
  loadCounts()
})
</script>

<template>
  <div>
    <div class="page-head" style="display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap">
      <div>
        <h1 class="page-title">订单管理</h1>
        <p class="page-desc">共 {{ page.total }} 笔订单 · 本店订单可发货 / 标记完成，别家店订单只读；支持删除已完成 / 已取消的历史订单</p>
      </div>
      <div style="display:flex;gap:10px">
        <el-button :disabled="!selectedRows.length" @click="onBatchDelete">
          批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
        </el-button>
        <el-button :icon="Download" @click="exportCsv">导出 CSV</el-button>
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
      </div>
    </div>

    <!-- 状态筛选：带各状态订单数 -->
    <div class="status-tabs">
      <button
        v-for="t in tabs"
        :key="t.value"
        class="status-tab"
        :class="{ 'is-active': query.status === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
        <i v-if="countOf(t.value) !== undefined">{{ countOf(t.value) }}</i>
      </button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        :prefix-icon="Search"
        placeholder="订单号 / 收货人 / 账号（当前页）"
        clearable
        style="width:280px"
      />
      <el-button type="primary" @click="page.current = 1; load()">查询</el-button>
      <el-button @click="query.keyword = ''; query.status = ''; page.current = 1; load(); loadCounts()">重置</el-button>
      <span v-if="keyword" class="text-xs text-muted" style="margin-left:auto">
        当前页命中 {{ rows.length }} / {{ list.length }} 笔
      </span>
    </div>

    <div class="card">
      <el-table ref="tableRef" :data="rows" class="admin-table" v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="42" :selectable="canDelete" />
        <el-table-column label="店铺" width="140">
          <template #default="{ row }">
            <span class="shop-tag">{{ row.shopName || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="订单号 / 下单时间" min-width="198">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:6px">
              <span class="font-num" style="font-size:12px">{{ row.orderNo }}</span>
              <el-icon class="copy-link" :size="13" title="复制订单号" @click="copyNo(row.orderNo)">
                <CopyDocument />
              </el-icon>
            </div>
            <div class="font-num text-xs text-muted">{{ fmt(row.createdAt) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="下单用户" width="130">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="row-avatar">{{ (row.username || row.receiverName || 'U')[0].toUpperCase() }}</span>
              <div style="min-width:0">
                <div style="font-weight:600;font-size:12.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                  {{ row.username || ('用户 ' + row.userId) }}
                </div>
                <div class="text-xs text-muted">{{ row.receiverName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="商品" width="124">
          <template #default="{ row }">
            <div style="display:flex;align-items:center">
              <div class="thumb-stack">
                <template v-for="(i, idx) in itemsOf(row).slice(0, 3)" :key="i.id || idx">
                  <span class="thumb-box">
                    <i>{{ (i.productName || '?')[0] }}</i>
                    <img v-if="i.productCover" :src="i.productCover" :alt="i.productName" loading="lazy" @error="e => e.target.remove()" />
                  </span>
                </template>
              </div>
              <span class="thumb-more">{{ itemCount(row) }} 件</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="实付" width="100">
          <template #default="{ row }">
            <span class="price"><span class="cur">¥</span>{{ paidAmount(row).toFixed(2) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }"><span class="pill" :class="pill(row.status)">{{ label(row.status) }}</span></template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canOperate(row) && row.status === 'PAID'" size="small" type="primary" @click="openShip(row)">发货</el-button>
            <el-button v-else-if="canOperate(row) && row.status === 'SHIPPED'" size="small" type="success" @click="onComplete(row)">标记完成</el-button>
            <el-button v-else-if="row.status === 'PAID'" size="small" disabled>待商家发货</el-button>
            <el-button v-else-if="row.status === 'SHIPPED'" size="small" disabled>待商家完成</el-button>
            <el-button v-else-if="row.status === 'PENDING_PAYMENT'" size="small" disabled>等待支付</el-button>
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canDelete(row)" size="small" text type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">单</div>
            <div class="empty-title">{{ keyword ? '当前页没有匹配的订单' : '暂无订单' }}</div>
            <p class="empty-sub">{{ keyword ? '试试清空关键词，或翻到其他页查看。' : '用户在前端下单后会出现在这里。' }}</p>
          </div>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="page.current = 1; load()"
      />
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="detailOpen" size="540px" class="order-drawer" :with-header="true">
      <template #header>
        <div class="od-head">
          <span v-if="detail" class="pill" :class="pill(detail.status)">{{ label(detail.status) }}</span>
          <h3>订单详情</h3>
        </div>
      </template>

      <template v-if="detail">
        <div class="od-head">
          <span class="od-no">{{ detail.orderNo }}</span>
          <span class="od-amount">¥{{ paidAmount(detail).toFixed(2) }}</span>
        </div>

        <div class="od-section">
          <div class="od-section-title">商品清单</div>
          <div class="od-items">
            <div v-for="i in itemsOf(detail)" :key="i.id" class="od-item">
              <img v-if="i.productCover" :src="i.productCover" class="od-item-thumb" :alt="i.productName" />
              <span v-else class="od-item-thumb" style="display:flex;align-items:center;justify-content:center">{{ (i.productName || '?')[0] }}</span>
              <div style="flex:1;min-width:0">
                <div class="od-item-name">{{ i.productName }}</div>
                <div class="od-item-meta">¥{{ i.price }} × {{ i.quantity }}</div>
              </div>
              <span class="od-item-sum">¥{{ Number(i.subtotal || 0).toFixed(2) }}</span>
            </div>
            <div v-if="!itemsOf(detail).length" class="od-item text-muted text-xs">该订单没有商品明细</div>
          </div>
        </div>

        <div class="od-section">
          <div class="od-section-title">收货信息</div>
          <div class="od-kv">
            <div><span>收货人</span><span>{{ detail.receiverName }}</span></div>
            <div><span>联系电话</span><span class="font-num">{{ detail.receiverPhone }}</span></div>
            <div class="full"><span>收货地址</span><span>{{ detail.receiverAddr }}</span></div>
            <div v-if="detail.remark" class="full"><span>订单备注</span><span class="text-2">{{ detail.remark }}</span></div>
          </div>
        </div>

        <div class="od-section">
          <div class="od-section-title">金额</div>
          <div class="od-kv">
            <div><span>商品金额</span><span class="font-num">¥{{ Number(detail.totalAmount || 0).toFixed(2) }}</span></div>
            <div v-if="Number(detail.discountAmount || 0) > 0">
              <span>优惠抵扣</span>
              <span class="font-num" style="color:var(--brand-600)">- ¥{{ Number(detail.discountAmount || 0).toFixed(2) }}</span>
            </div>
            <div><span>实付金额</span><span class="font-num fw-600" style="color:var(--price)">¥{{ paidAmount(detail).toFixed(2) }}</span></div>
            <div><span>下单用户</span><span>{{ detail.username || ('用户 ' + detail.userId) }}</span></div>
          </div>
        </div>

        <div class="od-section">
          <div class="od-section-title">订单进度</div>
          <ul class="od-timeline">
            <li v-for="s in timeline" :key="s.label" :class="{ 'is-done': s.done }">
              <b>{{ s.label }}</b>
              <span>{{ fmt(s.time) }}</span>
            </li>
          </ul>
        </div>

        <div class="od-section" v-if="detail.status === 'SHIPPED' || detail.status === 'COMPLETED'">
          <div class="od-section-title">物流信息</div>
          <div class="od-kv">
            <div><span>发货时间</span><span class="font-num">{{ fmt(detail.shippedAt) }}</span></div>
            <div v-if="detail.trackingCompany"><span>快递公司</span><span>{{ detail.trackingCompany }}</span></div>
            <div v-if="detail.trackingNo"><span>快递单号</span><span class="font-num">{{ detail.trackingNo }}</span></div>
            <div v-if="!detail.trackingCompany && !detail.trackingNo"><span>物流</span><span class="text-muted">商家尚未填写单号</span></div>
          </div>
        </div>

      </template>

      <template #footer>
        <div v-if="detail" class="od-foot">
          <span class="od-tip">
            {{ !canOperate(detail)
              ? '非本店订单仅可查看，发货与完成请由对应商家操作'
              : detail.status === 'PAID' ? '下一步：发货 → 用户确认收货后自动完成'
              : detail.status === 'SHIPPED' ? '等待用户确认收货，也可直接标记完成'
              : detail.status === 'PENDING_PAYMENT' ? '用户尚未支付，30 分钟后自动取消'
              : '订单已结束' }}
          </span>
          <el-button v-if="canOperate(detail) && detail.status === 'PAID'" type="primary" @click="openShip(detail)">发货</el-button>
          <el-button v-if="canOperate(detail) && detail.status === 'SHIPPED'" type="success" @click="onComplete(detail)">标记完成</el-button>
          <el-button v-if="canDelete(detail)" type="danger" text @click="onDelete(detail)">删除订单</el-button>
          <el-button @click="detailOpen = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="shipVisible" title="发货" width="460px">
      <p class="text-xs text-muted" style="margin-top:-6px">填写快递公司与单号，便于用户端查询物流。</p>
      <el-form label-width="84px">
        <el-form-item label="快递公司">
          <el-select v-model="shipForm.trackingCompany" filterable allow-create clearable placeholder="选择或输入快递公司" style="width:100%">
            <el-option v-for="c in expressList" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="快递单号">
          <el-input v-model="shipForm.trackingNo" placeholder="请输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipLoading" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>
