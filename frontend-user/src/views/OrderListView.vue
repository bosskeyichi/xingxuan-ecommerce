<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, cartApi } from '@/api'
import { formatPrice, moneyMinus } from '@/utils/price'
import OrderReviewDialog from '@/components/OrderReviewDialog.vue'

const router = useRouter()
const route = useRoute()
const list = ref([])
const loading = ref(true)
const page = reactive({ current: 1, size: 10, total: 0 })

const selected = ref([])
const reviewVisible = ref(false)
const reviewOrder = ref(null)

const tabs = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

// 跨店结算后跳转过来会带 ?status=PENDING_PAYMENT，直接落在待支付页
const status = ref(tabs.some(t => t.value === route.query.status) ? route.query.status : '')
const pendingCount = computed(() => list.value.filter(o => o.status === 'PENDING_PAYMENT').length)

const statusLabel = s => ({
  PENDING_PAYMENT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消'
}[s] || s)
const statusPill = s => ({
  PENDING_PAYMENT: 'pending', PAID: 'paid', SHIPPED: 'shipped', COMPLETED: 'completed', CANCELLED: 'cancelled'
}[s] || '')
const format = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''

const load = async () => {
  loading.value = true
  selected.value = []
  try {
    const r = await orderApi.list({ status: status.value || undefined, current: page.current, size: page.size })
    list.value = r.data.records || []
    page.total = r.data.total || 0
  } catch (e) {
    list.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

const switchTab = t => { status.value = t; page.current = 1; load() }

const onPay = async (row) => {
  await ElMessageBox.confirm('确认支付该订单？（模拟支付，不会产生真实交易）', '模拟支付', { type: 'warning' })
  await orderApi.pay(row.orderNo)
  ElMessage.success('支付成功')
  load()
}

const onCancel = async (row) => {
  await ElMessageBox.confirm('确认取消该订单？取消后库存将自动回退。', '取消订单', { type: 'warning' })
  await orderApi.cancel(row.orderNo)
  ElMessage.success('订单已取消，商品已退回购物车')
  // 商品被退回购物车，同步顶栏件数
  window.dispatchEvent(new Event('cart:refresh'))
  load()
}

const onConfirm = async (row) => {
  await ElMessageBox.confirm('确认已收到货物？', '确认收货', { type: 'warning' })
  await orderApi.confirm(row.orderNo)
  ElMessage.success('已确认收货，现在可以评价商品了')
  await load()
  openReview(row)
}

// ---- 批量操作：待支付可合并支付，已完成 / 已取消可批量删除（同一套勾选） ----
const canDelete = row => ['COMPLETED', 'CANCELLED'].includes(row.status)
const deletable = computed(() => list.value.filter(canDelete))
const payable = computed(() => list.value.filter(o => o.status === 'PENDING_PAYMENT'))
// 勾选池 = 可合并支付的待支付订单 + 可删除的终态订单
const selectable = computed(() => [...payable.value, ...deletable.value])
const paySelected = computed(() => selected.value.filter(no => payable.value.some(o => o.orderNo === no)))
const delSelected = computed(() => selected.value.filter(no => deletable.value.some(o => o.orderNo === no)))
const paySum = computed(() => payable.value
  .filter(o => paySelected.value.includes(o.orderNo))
  .reduce((s, o) => s + Number(moneyMinus(o.totalAmount, o.discountAmount) || 0), 0))
const allSelected = computed(() => selectable.value.length > 0
  && selectable.value.every(o => selected.value.includes(o.orderNo)))
const indeterminate = computed(() => selected.value.length > 0 && !allSelected.value)
// 顶部一键合并支付：当前页所有待支付订单，不限店铺
const payableSum = computed(() => payable.value
  .reduce((s, o) => s + Number(moneyMinus(o.totalAmount, o.discountAmount) || 0), 0))
const payableShopCount = computed(() => new Set(payable.value.map(o => o.shopId)).size)

const toggleSelect = (orderNo, checked) => {
  const set = new Set(selected.value)
  if (checked) set.add(orderNo)
  else set.delete(orderNo)
  selected.value = [...set]
}
const toggleAll = checked => {
  selected.value = checked ? selectable.value.map(o => o.orderNo) : []
}

// 合并支付：跨店结算拆出的多笔待支付订单一次付清
const doBatchPay = async (nos, sum) => {
  await ElMessageBox.confirm(
    `确认一次性支付 ${nos.length} 笔订单，合计 ¥${Number(sum).toFixed(2)}？（模拟支付，不会产生真实交易）`,
    '合并支付',
    { type: 'warning', confirmButtonText: '确认支付', cancelButtonText: '再想想' }
  )
  const r = await orderApi.payBatch(nos)
  ElMessage.success(`已支付 ${r.data ?? nos.length} 笔订单`)
  load()
}

// 勾选后合并支付
const onBatchPay = () => {
  const nos = paySelected.value
  if (!nos.length) return
  return doBatchPay(nos, paySum.value)
}

// 顶部一键：当前页全部待支付订单一起付（跨店也能一次付清）
const onPayAllPending = () => {
  const nos = payable.value.map(o => o.orderNo)
  if (!nos.length) return
  return doBatchPay(nos, payableSum.value)
}

const onDelete = async row => {
  await ElMessageBox.confirm(
    `确认删除订单 ${row.orderNo}？删除后该订单不再显示在「我的订单」中，此操作不可恢复。`,
    '删除订单',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  await orderApi.remove(row.orderNo)
  ElMessage.success('订单已删除')
  if (list.value.length === 1 && page.current > 1) page.current -= 1
  load()
}

const onBatchDelete = async () => {
  const nos = delSelected.value
  const count = nos.length
  if (!count) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${count} 笔历史订单？删除后不可恢复。`,
    '批量删除订单',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  const r = await orderApi.batchDelete(nos)
  ElMessage.success(`已删除 ${r.data ?? count} 笔订单`)
  if (list.value.length === count && page.current > 1) page.current -= 1
  load()
}

// ---- 商品评价 ----
const openReview = row => {
  reviewOrder.value = row
  reviewVisible.value = true
}

// 再次购买：把订单内商品重新加入购物车
const onRebuy = async row => {
  const items = row.items || []
  if (!items.length) return
  try {
    for (const i of items) {
      await cartApi.add({ productId: i.productId, quantity: i.quantity })
    }
    ElMessage.success('已加入购物车')
    window.dispatchEvent(new Event('cart:refresh'))
    router.push('/cart')
  } catch (e) { /* 统一拦截提示 */ }
}

const canRebuy = row => ['SHIPPED', 'COMPLETED', 'CANCELLED'].includes(row.status)

onMounted(load)
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">我的订单</h2>
        <div class="section-sub">
          共 {{ page.total }} 笔订单 · 可按状态筛选
          <template v-if="pendingCount">
            · <span class="pending-hint">当前页有 {{ pendingCount }} 笔待支付</span>
          </template>
        </div>
      </div>
    </div>

    <div class="order-tabs">
      <button
        v-for="t in tabs"
        :key="t.value"
        class="order-tab"
        :class="{ 'is-active': status === t.value }"
        @click="switchTab(t.value)"
      >{{ t.label }}</button>
    </div>

    <div v-if="payable.length > 1" class="order-paybar">
      <div>
        <b>当前页有 {{ payable.length }} 笔待支付订单</b>
        <span class="order-paybar-sub">（{{ payableShopCount }} 家店铺）· 可一次付清，无需逐笔支付</span>
      </div>
      <div class="order-paybar-act">
        <span class="price price-md"><span class="cur">¥</span>{{ payableSum.toFixed(2) }}</span>
        <el-button type="primary" @click="onPayAllPending">合并支付</el-button>
      </div>
    </div>

    <div v-if="selectable.length" class="order-batch">
      <el-checkbox :model-value="allSelected" :indeterminate="indeterminate" @change="toggleAll">
        全选本页可操作订单
      </el-checkbox>
      <span class="order-batch-tip">
        已选 {{ selected.length }} 笔
        <template v-if="paySelected.length"> · 待支付 {{ paySelected.length }} 笔合计 ¥{{ paySum.toFixed(2) }}</template>
        <template v-if="delSelected.length"> · 可删除 {{ delSelected.length }} 笔</template>
      </span>
      <el-button v-if="paySelected.length" size="small" type="primary" @click="onBatchPay">
        合并支付（{{ paySelected.length }}）
      </el-button>
      <el-button v-if="delSelected.length" size="small" @click="onBatchDelete">批量删除</el-button>
    </div>

    <div v-loading="loading" class="order-panel">
    <div v-if="!loading && !list.length" class="empty">
      <div class="empty-icon">单</div>
      <div class="empty-title">{{ status ? '该状态下暂无订单' : '还没有订单' }}</div>
      <p class="empty-sub">去挑几件喜欢的商品，下单后可在这里查看物流与状态。</p>
      <el-button class="mt-16" type="primary" @click="router.push('/')">去逛逛</el-button>
    </div>

    <template v-else>
      <article v-for="o in list" :key="o.orderNo" class="order-card">
        <div class="order-head">
          <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <el-checkbox
              v-if="canDelete(o) || o.status === 'PENDING_PAYMENT'"
              :model-value="selected.includes(o.orderNo)"
              @change="v => toggleSelect(o.orderNo, v)"
            />
            <span class="pill" :class="statusPill(o.status)">{{ statusLabel(o.status) }}</span>
            <router-link
              v-if="o.shopName"
              class="shop-chip shop-chip-link"
              :to="{ name: 'shop', params: { id: o.shopId } }"
            >{{ o.shopName }}</router-link>
            <span class="order-no">{{ o.orderNo }}</span>
          </div>
          <span class="order-time">{{ format(o.createdAt) }}</span>
        </div>

        <div class="order-body">
          <router-link
            v-for="i in (o.items || []).slice(0, 3)"
            :key="i.id"
            class="order-goods order-goods-link"
            :to="`/product/${i.productId}`"
            :title="`查看商品：${i.productName}`"
          >
            <div class="cart-thumb">
              <img v-if="i.productCover" :src="i.productCover" :alt="i.productName" loading="lazy" />
              <div v-else class="img-ph">{{ (i.productName || '?')[0] }}</div>
            </div>
            <div style="min-width:0">
              <div class="cart-name">{{ i.productName }}</div>
              <div class="cart-meta">¥{{ formatPrice(i.price) }} × {{ i.quantity }}</div>
            </div>
          </router-link>
          <div v-if="(o.items?.length || 0) > 3" class="order-more">等 {{ o.items.length }} 件商品</div>
        </div>

        <div class="order-foot">
          <div style="display:flex;align-items:baseline;gap:8px">
            <span class="text-sm text-muted">订单合计</span>
            <span v-if="o.discountAmount" class="text-sm text-muted" style="text-decoration:line-through">¥{{ formatPrice(o.totalAmount) }}</span>
            <span class="price price-md"><span class="cur">¥</span>{{ moneyMinus(o.totalAmount, o.discountAmount) }}</span>
          </div>
          <div class="order-actions">
            <el-button v-if="canRebuy(o)" @click="onRebuy(o)">再次购买</el-button>
            <el-button @click="router.push(`/order/${o.orderNo}`)">查看详情</el-button>
            <el-button v-if="o.status === 'PENDING_PAYMENT'" type="primary" @click="onPay(o)">立即支付</el-button>
            <el-button v-if="o.status === 'PENDING_PAYMENT'" @click="onCancel(o)">取消订单</el-button>
            <el-button v-if="o.status === 'SHIPPED'" type="success" @click="onConfirm(o)">确认收货</el-button>
            <el-button v-if="o.status === 'COMPLETED'" type="primary" plain @click="openReview(o)">评价商品</el-button>
            <el-button v-if="canDelete(o)" class="order-del-btn" @click="onDelete(o)">删除订单</el-button>
          </div>
        </div>
      </article>

      <el-pagination
        v-if="page.total > page.size"
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </template>
    </div>

    <OrderReviewDialog v-model="reviewVisible" :order="reviewOrder" />
  </div>
</template>
