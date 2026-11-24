<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminCouponApi, shopApi } from '@/api'
import { getUser } from '@/utils/auth'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const showDialog = ref(false)
const editingId = ref(null)
const saving = ref(false)

// 平台管理员可发全平台券或指定店铺券；商家只能发本店券
const isPlatform = computed(() => getUser()?.role === 'ADMIN')
const shops = ref([])
const myShopId = ref(null)

const form = reactive({
  shopId: null,
  name: '', description: '', threshold: 0, discount: 0,
  total: 100, startAt: '', endAt: '', status: 1
})

const query = reactive({ keyword: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const r = await adminCouponApi.page({ keyword: query.keyword || undefined, current: query.page, size: query.size })
    list.value = r.data.records
    total.value = r.data.total
  } finally { loading.value = false }
}

const openCreate = () => {
  editingId.value = null
  Object.assign(form, {
    shopId: isPlatform.value ? null : myShopId.value,
    name: '', description: '', threshold: 0, discount: 0,
    total: 100, startAt: '', endAt: '', status: 1
  })
  showDialog.value = true
}
const openEdit = row => {
  editingId.value = row.id
  Object.assign(form, {
    shopId: row.shopId ?? null,
    name: row.name, description: row.description || '', threshold: row.threshold || 0,
    discount: row.discount || 0, total: row.total || 0, startAt: row.startAt || '', endAt: row.endAt || '', status: row.status
  })
  showDialog.value = true
}
const save = async () => {
  if (!form.name.trim()) { ElMessage.warning('请填写券名称'); return }
  if (Number(form.discount) <= 0) { ElMessage.warning('减免金额需大于 0'); return }
  if (Number(form.threshold) > 0 && Number(form.discount) >= Number(form.threshold)) {
    ElMessage.warning('减免金额需小于使用门槛，否则无法抵扣')
    return
  }
  if (form.startAt && form.endAt && form.endAt <= form.startAt) {
    ElMessage.warning('结束时间需晚于开始时间')
    return
  }
  saving.value = true
  try {
    // 日期留空必须传 null：空字符串无法反序列化成 LocalDateTime，后端会返回 400
    const payload = { ...form, startAt: form.startAt || null, endAt: form.endAt || null }
    if (editingId.value) await adminCouponApi.update(editingId.value, payload)
    else await adminCouponApi.create(payload)
    ElMessage.success('已保存')
    showDialog.value = false
    load()
  } finally { saving.value = false }
}
const remove = async row => {
  await ElMessageBox.confirm(`确认删除优惠券「${row.name}」？`, '删除确认', { type: 'warning' })
  await adminCouponApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

const statusText = s => (s === 1 ? '上架中' : '已下架')
const claimRate = row => {
  const total = Number(row.total || 0)
  const claimed = Number(row.claimed || 0)
  if (!total || claimed <= 0) return 0
  return Math.min(100, Math.round(claimed / total * 1000) / 10)
}
const claimBarWidth = row => (Number(row.claimed || 0) > 0 ? Math.max(claimRate(row), 2) : 0) + '%'
const scopeText = row => (row.shopId ? (row.shopName || `店铺 #${row.shopId}`) : '全平台')

const loadScope = async () => {
  try {
    const c = await shopApi.context()
    myShopId.value = c.data?.shopId ?? null
  } catch (e) { myShopId.value = null }
  if (!isPlatform.value) return
  try {
    const r = await shopApi.adminList()
    shops.value = r.data || []
  } catch (e) { shops.value = [] }
}

onMounted(() => {
  load()
  loadScope()
})
</script>

<template>
  <div>
    <div class="page-head" style="display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap">
      <div>
        <h1 class="page-title">优惠券管理</h1>
        <p class="page-desc">
          {{ isPlatform ? '平台券全平台可用，店铺券仅能在对应店铺的订单上抵扣' : '发行本店专属优惠券，只能用于本店商品的订单' }}
        </p>
      </div>
      <el-button type="primary" @click="openCreate">发放新券</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="券名称"
        clearable
        style="width:240px"
        @keyup.enter="query.page = 1; load()"
      />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <el-button @click="query.keyword = ''; query.page = 1; load()">重置</el-button>
      <span class="text-xs text-muted" style="margin-left:auto">共 {{ total }} 种优惠券</span>
    </div>

    <div class="card">
      <el-table :data="list" v-loading="loading" class="admin-table">
        <el-table-column label="券名称" min-width="158">
          <template #default="{ row }">
            <div style="font-weight:600;font-size:12.5px">{{ row.name }}</div>
            <div class="text-xs text-muted">{{ row.description || '无描述' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="优惠规则" width="128">
          <template #default="{ row }">
            <span class="pill paid">{{ row.threshold > 0 ? `满 ${row.threshold} 减 ${row.discount}` : `无门槛减 ${row.discount}` }}</span>
          </template>
        </el-table-column>

        <el-table-column label="适用范围" width="150">
          <template #default="{ row }">
            <span class="pill" :class="row.shopId ? 'paid' : 'completed'">{{ scopeText(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="领取进度" width="132">
          <template #default="{ row }">
            <div class="claim">
              <div class="claim-bar"><i :style="{ width: claimBarWidth(row) }"></i></div>
              <span class="claim-text">{{ row.claimed || 0 }} / {{ row.total || 0 }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="有效期" width="120">
          <template #default="{ row }">
            <span class="font-num text-xs">至 {{ row.endAt ? String(row.endAt).replace('T', ' ').slice(0, 10) : '长期' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <span class="pill" :class="row.status === 1 ? 'completed' : 'mute'">{{ statusText(row.status) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="112" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button class="act-del" text @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">券</div>
            <div class="empty-title">还没有优惠券</div>
            <p class="empty-sub">点击右上角「发放新券」创建第一张券。</p>
          </div>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="query.page = 1; load()"
      />
    </div>

    <el-dialog v-model="showDialog" :title="editingId ? '编辑优惠券' : '发放新券'" width="520px">
      <el-form label-width="92px">
        <el-form-item label="适用店铺">
          <el-select v-if="isPlatform" v-model="form.shopId" placeholder="全平台可用" clearable :disabled="!!editingId" style="width:100%">
            <el-option :value="null" label="全平台可用" />
            <el-option v-for="s in shops" :key="s.id" :value="s.id" :label="`仅限 ${s.name}`" />
          </el-select>
          <el-input v-else :model-value="'仅限本店铺（系统自动绑定）'" disabled />
          <div v-if="editingId" class="text-xs text-muted" style="margin-top:4px">适用范围创建后不可更改</div>
        </el-form-item>
        <el-form-item label="券名称"><el-input v-model="form.name" placeholder="如：满 300 减 50" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" placeholder="可选" /></el-form-item>
        <el-form-item label="使用门槛(元)"><el-input-number v-model="form.threshold" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="减免金额(元)"><el-input-number v-model="form.discount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="发行总量"><el-input-number v-model="form.total" :min="0" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="默认立即生效" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空为长期" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
