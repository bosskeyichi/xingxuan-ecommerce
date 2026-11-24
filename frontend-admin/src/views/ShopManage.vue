<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { shopApi } from '@/api'
import { getUser } from '@/utils/auth'

const isPlatform = computed(() => getUser()?.role === 'ADMIN')

/* ---------- 平台管理员：全部店铺 ---------- */
const list = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const r = await shopApi.adminList()
    list.value = r.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

const onToggle = async (row, v) => {
  const next = v ? 1 : 0
  try {
    await shopApi.status(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '店铺已开店' : '店铺已关店，前台不再展示其商品')
  } catch (e) {
    ElMessage.error('操作失败')
    load()
  }
}

/* ---------- 普通管理员：维护自己的店铺 ---------- */
const myShop = ref(null)
const mineLoading = ref(false)
const saving = ref(false)
const form = reactive({ name: '', intro: '', cover: '' })

const loadMine = async () => {
  mineLoading.value = true
  try {
    const r = await shopApi.mine()
    myShop.value = r.data || null
    if (r.data) {
      form.name = r.data.name || ''
      form.intro = r.data.intro || ''
      form.cover = r.data.cover || ''
    }
  } finally {
    mineLoading.value = false
  }
}

const saveMine = async () => {
  if (!form.name.trim()) { ElMessage.warning('店铺名称不能为空'); return }
  saving.value = true
  try {
    await shopApi.updateMine({ name: form.name.trim(), intro: form.intro, cover: form.cover })
    ElMessage.success('店铺资料已更新')
    loadMine()
  } finally {
    saving.value = false
  }
}

onMounted(() => (isPlatform.value ? load() : loadMine()))
</script>

<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">{{ isPlatform ? '店铺管理' : '我的店铺' }}</h1>
      <p v-if="isPlatform" class="page-desc">
        共 {{ list.length }} 家店铺 · 商家账号与店铺一一对应，商品与订单都按店铺隔离统计
      </p>
      <p v-else class="page-desc">
        维护店铺名称与简介，会展示在商城的店铺页；商品、订单、优惠券都归属这家店
      </p>
    </div>

    <!-- 商家：只维护自己的店铺 -->
    <div v-if="!isPlatform" v-loading="mineLoading" class="card">
      <div v-if="!myShop" class="empty">
        <div class="empty-icon" style="font-family:var(--font-display)">店</div>
        <div class="empty-title">还没有绑定店铺</div>
        <p class="empty-sub">请联系超级管理员在「用户管理」中为你开通店铺。</p>
      </div>
      <el-form v-else label-position="top" style="max-width:560px">
        <el-form-item label="店铺状态">
          <span class="pill" :class="myShop.status === 1 ? 'completed' : 'mute'">
            {{ myShop.status === 1 ? '营业中' : '已关店（联系超级管理员开店）' }}
          </span>
        </el-form-item>
        <el-form-item label="店铺名称">
          <el-input v-model="form.name" maxlength="60" show-word-limit placeholder="如：极客数码专营店" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="一句话介绍店铺主营类目"
          />
        </el-form-item>
        <el-form-item label="店铺封面图地址（可选）">
          <el-input v-model="form.cover" placeholder="https://... 或 /img/shops/xxx.jpg" />
          <div class="text-xs text-muted" style="margin-top:6px">留空则使用品牌默认底色，修改后同步到商城店铺页</div>
        </el-form-item>
        <div v-if="form.cover" class="shop-cover-preview">
          <img :src="form.cover" alt="cover" />
        </div>
        <el-button type="primary" :loading="saving" @click="saveMine">保存店铺资料</el-button>
      </el-form>
    </div>

    <!-- 平台管理员：全部店铺 -->
    <div v-else class="card">
      <el-table :data="list" class="admin-table" v-loading="loading">
        <el-table-column label="店铺" min-width="220">
          <template #default="{ row }">
            <div style="font-weight:600">{{ row.name }}</div>
            <div class="text-xs text-muted">{{ row.intro || '暂无简介' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="店主账号" width="190">
          <template #default="{ row }">
            <div>{{ row.ownerName || '—' }}</div>
            <div class="text-xs text-muted">{{ row.ownerUsername ? '@' + row.ownerUsername : '未绑定账号' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="90">
          <template #default="{ row }"><span class="font-num">{{ row.productCount }}</span></template>
        </el-table-column>
        <el-table-column label="订单" width="90">
          <template #default="{ row }"><span class="font-num">{{ row.orderCount }}</span></template>
        </el-table-column>
        <el-table-column label="成交额" width="130">
          <template #default="{ row }">
            <span class="price price-sm"><span class="cur">¥</span>{{ Number(row.salesAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-switch :model-value="row.status === 1" @change="v => onToggle(row, v)" />
              <span class="text-xs" :style="{ color: row.status === 1 ? 'var(--success)' : 'var(--text-3)' }">
                {{ row.status === 1 ? '营业中' : '已关店' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">店</div>
            <div class="empty-title">暂无店铺</div>
            <p class="empty-sub">在「用户管理」里把普通用户设为普通管理员即可自动开店。</p>
          </div>
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.shop-cover-preview {
  margin: -6px 0 16px;
  width: 100%;
  max-width: 320px;
  aspect-ratio: 16 / 7;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.shop-cover-preview img { width: 100%; height: 100%; object-fit: cover; }
</style>
