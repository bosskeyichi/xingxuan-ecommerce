<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { adminProductApi, adminCategoryApi, shopApi } from '@/api'
import ReviewManageDialog from '@/components/ReviewManageDialog.vue'

const list = ref([])
const categories = ref([])
const shops = ref([])
const ctx = ref({})
const isPlatform = computed(() => ctx.value.platformAdmin === true)
// 超管的商品只能归到自己绑定的店铺；其他店铺的商品仅可查看
const ownShopId = computed(() => ctx.value.shopId || null)
const ownShopName = computed(() => ctx.value.shopName || '')
// 是否本店商品：超管对别家店商品只读（只能评价治理，不能改价 / 上下架 / 删除）
const canEdit = row => !isPlatform.value || (!!row?.shopId && row.shopId === ownShopId.value)
const query = reactive({ keyword: '', categoryId: null, status: null, shopId: null })
const page = reactive({ current: 1, size: 10, total: 0 })
const dlg = reactive({ visible: false, title: '新增商品', form: {} })
// 弹窗里的店铺只读：新增=自己店铺，编辑=商品原店铺
const dialogShopLabel = computed(() => dlg.form.shopName || ownShopName.value || '未绑定店铺')
const formRef = ref()
const loading = ref(false)

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

const load = async () => {
  loading.value = true
  try {
    const r = await adminProductApi.page({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status === null || query.status === '' ? undefined : query.status,
      shopId: query.shopId || undefined
    })
    list.value = r.data.records
    page.total = r.data.total
  } catch (e) {
    // 接口异常时保持空白列表并重置分页，别让静默失败 / 数据错乱
    list.value = []
    page.total = 0
  } finally { loading.value = false }
}

const loadCategories = async () => {
  const r = await adminCategoryApi.list()
  categories.value = r.data || []
}

const loadShops = async () => {
  try {
    const c = await shopApi.context()
    ctx.value = c.data || {}
  } catch (e) { ctx.value = {} }
  if (!ctx.value.platformAdmin) return
  try {
    const r = await shopApi.adminList()
    shops.value = r.data || []
  } catch (e) { shops.value = [] }
}

const reset = () => { query.keyword = ''; query.categoryId = null; query.status = null; query.shopId = null; page.current = 1; load() }
const onAdd = () => {
  if (isPlatform.value && !ownShopId.value) {
    ElMessage.warning('当前管理员账号还没有绑定店铺，无法新增商品')
    return
  }
  dlg.title = '新增商品'
  dlg.form = {
    name: '', categoryId: null, price: 0, stock: 0, cover: '', description: '', keywords: '', status: 1,
    freeShipping: 1, shipHours: 48, returnDays: 7,
    shopId: isPlatform.value ? ownShopId.value : undefined
  }
  dlg.visible = true
}
const onEdit = (row) => { dlg.title = '编辑商品'; dlg.form = { ...row }; dlg.visible = true }

const reviewVisible = ref(false)
const reviewProduct = ref(null)
const openReviews = row => {
  reviewProduct.value = row
  reviewVisible.value = true
}

const onSubmit = async () => {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (isPlatform.value && !dlg.form.shopId) {
    ElMessage.warning('当前管理员账号还没有绑定店铺，无法保存商品')
    return
  }
  if (dlg.form.id) await adminProductApi.update(dlg.form.id, dlg.form)
  else await adminProductApi.create(dlg.form)
  ElMessage.success('已保存')
  dlg.visible = false
  load()
}

// 列表内直接上下架，别让每次都打开编辑弹窗
const onToggleStatus = async (row, v) => {
  try {
    await adminProductApi.update(row.id, { ...row, status: v ? 1 : 0 })
    row.status = v ? 1 : 0
    ElMessage.success(v ? '已上架' : '已下架')
  } catch (e) {
    ElMessage.error('状态更新失败')
    load()
  }
}

const onDelete = async (row) => {
  await ElMessageBox.confirm(
    `确认删除商品「${row.name}」？删除后商品将从管理端和用户端消失，历史订单保留商品快照；如果只是暂时停售，请使用「下架」开关。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  await adminProductApi.delete(row.id)
  ElMessage.success('已删除')
  // 删掉当前页最后一条时回退一页，别让停在空白页
  if (list.value.length === 1 && page.current > 1) page.current -= 1
  load()
}

onMounted(async () => {
  await loadShops()
  loadCategories()
  load()
})
</script>

<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">商品管理</h1>
      <p class="page-desc">共 {{ page.total }} 件商品 · 支持新增、编辑、上下架、服务承诺维护、评价管理与删除</p>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        :prefix-icon="Search"
        placeholder="商品名称"
        clearable
        style="width:200px"
        @keyup.enter="page.current = 1; load()"
      />
      <el-select v-model="query.categoryId" clearable placeholder="全部分类" style="width:160px" @change="page.current = 1; load()">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="全部状态" style="width:130px" @change="page.current = 1; load()">
        <el-option label="在售" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-select
        v-if="isPlatform"
        v-model="query.shopId"
        clearable
        placeholder="全部店铺"
        style="width:170px"
        @change="page.current = 1; load()"
      >
        <el-option v-for="s in shops" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button type="primary" @click="page.current = 1; load()">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button type="success" :icon="Plus" style="margin-left:auto" @click="onAdd">新增商品</el-button>
    </div>

    <div class="card">
      <el-table :data="list" class="admin-table" v-loading="loading">
        <el-table-column label="商品" min-width="216">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:12px">
              <img v-if="row.cover" :src="row.cover" class="row-cover" :alt="row.name" loading="lazy" />
              <div v-else class="row-cover-ph">{{ (row.name || '?')[0] }}</div>
              <div style="min-width:0">
                <div style="font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:220px">{{ row.name }}</div>
                <div class="text-xs text-muted">编号 {{ String(row.id).padStart(3, '0') }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="104">
          <template #default="{ row }">
            <span class="pill mute">{{ categories.find(c => c.id === row.categoryId)?.name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="店铺" width="140">
          <template #default="{ row }">
            <span class="shop-tag">{{ row.shopName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100">
          <template #default="{ row }"><span class="price"><span class="cur">¥</span>{{ row.price }}</span></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="84">
          <template #default="{ row }">
            <span class="font-num fw-600" :style="{ color: row.stock <= 10 ? 'var(--danger)' : 'inherit' }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="84">
          <template #default="{ row }"><span class="font-num">{{ row.sales }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-switch :model-value="row.status === 1" :disabled="!canEdit(row)" @change="v => onToggleStatus(row, v)" />
              <span class="text-xs" :style="{ color: row.status === 1 ? 'var(--success)' : 'var(--text-3)' }">
                {{ row.status === 1 ? '在售' : '已下架' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="172" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="isPlatform" text type="primary" @click="openReviews(row)">评价</el-button>
              <el-button v-if="canEdit(row)" text type="primary" @click="onEdit(row)">编辑</el-button>
              <el-button v-if="canEdit(row)" class="act-del" text @click="onDelete(row)">删除</el-button>
              <span v-if="!canEdit(row)" class="text-xs text-muted">仅可查看</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">货</div>
            <div class="empty-title">暂无商品</div>
            <p class="empty-sub">点击右上角「新增商品」创建第一件商品。</p>
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
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.title" width="640px">
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="90px">
        <el-form-item v-if="isPlatform" label="所属店铺">
          <div style="width:100%">
            <el-input :model-value="dialogShopLabel" disabled />
            <div class="text-xs text-muted" style="margin-top:4px">超管只能给自己店铺建品，其他店铺的商品仅可查看</div>
          </div>
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="dlg.form.name" placeholder="商品名称" /></el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="dlg.form.categoryId" style="width:100%" placeholder="请选择分类">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:0 20px">
          <el-form-item label="价格" prop="price">
            <el-input-number v-model="dlg.form.price" :precision="2" :min="0" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="库存" prop="stock">
            <el-input-number v-model="dlg.form.stock" :min="0" :step="1" style="width:100%" />
          </el-form-item>
        </div>
        <el-form-item label="封面 URL"><el-input v-model="dlg.form.cover" placeholder="图片地址，留空显示占位图" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="dlg.form.description" type="textarea" :rows="3" placeholder="商品描述（最多 500 字）" /></el-form-item>
        <el-form-item label="搜索别名">
          <el-input v-model="dlg.form.keywords" placeholder="逗号分隔，如：鞋子,运动鞋（商品名里没有的品类词写这里）" />
        </el-form-item>
        <el-form-item label="服务承诺">
          <div style="width:100%">
            <div class="promise-row">
              <span class="promise-label">包邮</span>
              <el-switch v-model="dlg.form.freeShipping" :active-value="1" :inactive-value="0" />
            </div>
            <div class="promise-row">
              <span class="promise-label">承诺发货</span>
              <el-input-number v-model="dlg.form.shipHours" :min="0" :max="720" :step="12" style="width:140px" />
              <span class="promise-unit">小时内发出（0 = 不承诺）</span>
            </div>
            <div class="promise-row">
              <span class="promise-label">无理由退货</span>
              <el-input-number v-model="dlg.form.returnDays" :min="0" :max="365" :step="1" style="width:140px" />
              <span class="promise-unit">天（0 = 不支持）</span>
            </div>
            <div class="text-xs text-muted" style="margin-top:2px">这三项会展示在商城商品详情页，由本店自行维护</div>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dlg.form.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <ReviewManageDialog v-model="reviewVisible" :product="reviewProduct" />
  </div>
</template>

<style scoped>
.promise-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.promise-label { width: 76px; color: var(--text-2); font-size: 13px; }
.promise-unit { font-size: 12px; color: var(--text-3); }
</style>
