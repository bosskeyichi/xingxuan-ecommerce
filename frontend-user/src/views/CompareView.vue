<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { categoryApi, cartApi } from '@/api'
import { compare } from '@/utils/compare'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const items = ref(compare.list())
const catMap = ref({})

const loadCats = async () => {
  try {
    const r = await categoryApi.list()
    (r.data || []).forEach(c => { catMap.value[c.id] = c.name })
  } catch (e) {}
}

const catName = id => catMap.value[id] || '—'

const remove = id => {
  compare.remove(id)
  items.value = compare.list()
}

const addToCart = async p => {
  if (!userStore.isLogin) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    await cartApi.add({ productId: p.id, quantity: 1 })
    ElMessage.success('已加入购物车')
    window.dispatchEvent(new Event('cart:refresh'))
  } catch (e) { /* 统一拦截提示 */ }
}

const goDetail = id => router.push(`/product/${id}`)

const rows = [
  { key: 'cover', label: '商品' },
  { key: 'price', label: '价格', money: true },
  { key: 'stock', label: '库存' },
  { key: 'sales', label: '销量' },
  { key: 'categoryId', label: '分类', fn: catName }
]

onMounted(loadCats)
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">商品对比</h2>
        <div class="section-sub">最多可同时对比 {{ compare.MAX }} 件商品 · 在商品卡片或详情页点击「对比」即可加入</div>
      </div>
      <el-button v-if="items.length" text type="danger" @click="compare.clear(); items = compare.list()">清空对比</el-button>
    </div>

    <div v-if="!items.length" class="empty">
      <div class="empty-icon">⇄</div>
      <div class="empty-title">还没有加入对比的商品</div>
      <p class="empty-sub">在商品卡片或详情页点击「对比」即可加入此处。</p>
      <el-button class="mt-16" type="primary" @click="router.push('/')">去逛逛</el-button>
    </div>

    <div v-else class="compare-table-wrap">
      <table class="compare-table">
        <thead>
          <tr>
            <th class="ct-label">项目</th>
            <th v-for="p in items" :key="p.id" class="ct-prod">
              <button class="ct-remove" @click="remove(p.id)">移除 ×</button>
              <div class="ct-thumb">
                <img v-if="p.cover" :src="p.cover" :alt="p.name" />
                <div v-else class="img-ph">{{ (p.name || '?')[0] }}</div>
              </div>
              <div class="ct-name" @click="goDetail(p.id)">{{ p.name }}</div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.key">
            <td class="ct-label">{{ row.label }}</td>
            <td v-for="p in items" :key="p.id" class="ct-cell">
              <template v-if="row.key === 'cover'">
                <button class="btn-ghost" style="height:34px;padding:0 14px" @click="addToCart(p)">加入购物车</button>
              </template>
              <template v-else-if="row.money">
                <span class="price"><span class="cur">¥</span>{{ p.price }}</span>
              </template>
              <template v-else>{{ row.fn ? row.fn(p[row.key]) : p[row.key] }}</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.compare-table-wrap { overflow-x: auto; }
.compare-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--surface);
  border: 1px solid var(--border);
}
.compare-table th, .compare-table td {
  border: 1px solid var(--border);
  padding: 16px;
  vertical-align: middle;
  text-align: center;
}
.ct-label {
  background: var(--surface-2);
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: .08em;
  color: var(--ink-2);
  white-space: nowrap;
  text-align: left;
  width: 110px;
}
.ct-prod { position: relative; }
.ct-remove {
  position: absolute; top: 6px; right: 6px;
  border: none; background: transparent; color: var(--ink-3);
  cursor: pointer; font-size: 12px;
}
.ct-remove:hover { color: var(--danger); }
.ct-thumb {
  width: 96px; height: 96px; margin: 6px auto 10px;
  background: var(--surface-2); border-radius: var(--r-xs); overflow: hidden;
}
.ct-thumb img { width: 100%; height: 100%; object-fit: cover; }
.ct-name {
  font-size: 13.5px; font-weight: 500; cursor: pointer; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.ct-name:hover { color: var(--pine); }
.ct-cell { font-size: 14px; color: var(--ink); }
</style>
