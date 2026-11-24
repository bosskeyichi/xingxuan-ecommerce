<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Shop } from '@element-plus/icons-vue'
import { shopApi } from '@/api'

const route = useRoute()
const router = useRouter()

const keyword = ref(String(route.query.keyword || ''))
const shops = ref([])
const loading = ref(false)
const searched = ref(false)

const initial = s => (s.name || '店').slice(0, 1)

const load = async () => {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const r = kw ? await shopApi.search(kw, 50) : await shopApi.list()
    shops.value = r.data || []
    searched.value = !!kw
  } catch (e) {
    shops.value = []
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  const kw = keyword.value.trim()
  router.replace({ path: '/shops', query: kw ? { keyword: kw } : {} })
  load()
}

const reset = () => {
  keyword.value = ''
  router.replace({ path: '/shops' })
  load()
}

const goShop = s => router.push(`/shop/${s.id}`)

// 从顶栏「找店铺」等入口带关键词跳转时（组件可能被复用），同步关键词并重新搜索
watch(() => route.query.keyword, k => {
  keyword.value = String(k || '')
  load()
})

onMounted(load)
</script>

<template>
  <div class="page shop-list-page">
    <div class="page-head">
      <h1 class="page-title">全部店铺</h1>
      <p class="page-desc">按店铺名称或简介搜索，进入店铺可查看该店全部商品。</p>
    </div>

    <div class="card shop-search-card">
      <el-input
        v-model="keyword"
        placeholder="搜索店铺名称 / 简介，如 数码、旗舰店"
        clearable
        size="large"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
        <template #append><el-button type="primary" @click="onSearch">搜索店铺</el-button></template>
      </el-input>
      <div class="shop-search-tip">
        <span v-if="searched">
          搜索「{{ keyword }}」共 {{ shops.length }} 家店铺
          <el-button text size="small" @click="reset">查看全部店铺</el-button>
        </span>
        <span v-else>共 {{ shops.length }} 家营业中的店铺</span>
      </div>
    </div>

    <div v-loading="loading" class="shop-grid">
      <div v-if="!shops.length" class="card empty-card">
        <el-icon :size="30"><Shop /></el-icon>
        <div class="empty-title">没有找到匹配的店铺</div>
        <p class="empty-sub">换个关键词试试，或直接浏览全部店铺。</p>
        <el-button size="small" @click="reset">查看全部店铺</el-button>
      </div>
      <div v-for="s in shops" :key="s.id" class="card shop-card" @click="goShop(s)">
        <div class="shop-cover">
          <img v-if="s.cover" :src="s.cover" :alt="s.name" loading="lazy" />
          <span v-else>{{ initial(s) }}</span>
        </div>
        <div class="shop-info">
          <div class="shop-name">{{ s.name }}</div>
          <div class="shop-intro">{{ s.intro || '这家店铺还没有填写简介' }}</div>
        </div>
        <el-button size="small" plain type="primary">进入店铺</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shop-list-page { max-width: var(--page-max); margin: 0 auto; }
.shop-search-card { padding: 20px 22px; margin-bottom: 16px; }
.shop-search-tip { margin-top: 10px; font-size: 12.5px; color: var(--text-3); display: flex; align-items: center; gap: 6px; }
.shop-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.empty-card {
  grid-column: 1 / -1;
  padding: 56px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}
.shop-card { display: flex; align-items: center; gap: 14px; padding: 16px 18px; cursor: pointer; transition: border-color .2s, transform .2s; }
.shop-card:hover { border-color: var(--brand); transform: translateY(-2px); }
.shop-cover {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 12px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
  font-family: var(--font-display);
  font-size: 20px;
}
.shop-cover img { width: 100%; height: 100%; object-fit: cover; }
.shop-info { flex: 1; min-width: 0; }
.shop-name { font-weight: 600; font-size: 15px; color: var(--text-1); }
.shop-intro {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-3);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
