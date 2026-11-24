<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { shopApi, productApi, messageApi } from '@/api'
import { coverUrl } from '@/utils/img'
import { useProductActions } from '@/composables/useProductActions'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import ReportDialog from '@/components/ReportDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shop = ref(null)
const list = ref([])
const loading = ref(true)
const sort = ref('default')
const page = reactive({ current: 1, size: 12, total: 0 })

const favIds = ref([])
const { loadFavIds, addToCart, toggleFav } = useProductActions(favIds)
const shareVisible = ref(false)
const reportVisible = ref(false)

const shopId = computed(() => Number(route.params.id))

const sortOptions = [
  { value: 'default', label: '综合排序' },
  { value: 'sales_desc', label: '销量优先' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'newest', label: '最新上架' }
]

const heroStyle = computed(() => (shop.value?.cover
  ? { backgroundImage: `linear-gradient(100deg, rgba(20,17,15,.9), rgba(20,17,15,.55)), url(${coverUrl(shop.value.cover)})` }
  : {}))

const ensureLogin = () => {
  if (userStore.isLogin) return true
  ElMessage.warning('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

const onService = async () => {
  if (!ensureLogin() || !shop.value) return
  if (shop.value.ownerId && shop.value.ownerId === userStore.userInfo?.id) {
    return ElMessage.info('这是你自己的店铺')
  }
  try {
    const r = await messageApi.serviceTarget(shop.value.id)
    router.push({
      path: '/messages',
      query: {
        peerId: r.data.userId,
        scene: 'SERVICE',
        peerName: r.data.nickname,
        shopId: r.data.shopId,
        shopName: r.data.shopName
      }
    })
  } catch (e) { /* 统一提示 */ }
}

const openShare = () => { if (ensureLogin()) shareVisible.value = true }
const openReport = () => { if (ensureLogin()) reportVisible.value = true }

const loadShop = async () => {
  try {
    const r = await shopApi.detail(shopId.value)
    shop.value = r.data || null
  } catch (e) {
    shop.value = null
  }
}

const loadList = async () => {
  try {
    const r = await productApi.list({
      shopId: shopId.value,
      current: page.current,
      size: page.size,
      sort: sort.value === 'default' ? undefined : sort.value
    })
    list.value = r.data.records || []
    page.total = Number(r.data.total || 0)
  } catch (e) {
    list.value = []
    page.total = 0
  }
}

const load = async () => {
  loading.value = true
  try {
    await Promise.all([loadShop(), loadList()])
  } finally {
    loading.value = false
  }
}

const onSort = () => { page.current = 1; loadList() }
const onPage = c => {
  page.current = c
  loadList()
  window.scrollTo({ top: 320, behavior: 'smooth' })
}

watch(() => route.params.id, () => {
  page.current = 1
  list.value = []
  load()
})

onMounted(async () => {
  await load()
  await loadFavIds()
})
</script>

<template>
  <div v-loading="loading" class="shop-page">
    <template v-if="shop">
      <header class="shop-hero" :style="heroStyle">
        <div class="shop-hero-inner">
          <div class="shop-mark">{{ (shop.name || '店')[0] }}</div>
          <div class="shop-meta">
            <h1 class="shop-title">{{ shop.name }}</h1>
            <p class="shop-intro">{{ shop.intro || '这家店还没有填写店铺简介。' }}</p>
            <div class="shop-facts">
              <span class="shop-fact">{{ shop.status === 1 ? '营业中' : '休息中' }}</span>
              <span class="shop-fact">在售 {{ page.total }} 件</span>
              <span v-if="shop.createdAt" class="shop-fact">开店于 {{ String(shop.createdAt).slice(0, 10) }}</span>
            </div>
          </div>
          <div class="shop-hero-actions">
            <button class="shop-ghost" @click="onService">联系客服</button>
            <button class="shop-ghost" @click="openShare">分享店铺</button>
            <button class="shop-ghost" @click="openReport">举报店铺</button>
            <button class="shop-ghost" @click="router.push('/')">回首页</button>
          </div>
        </div>
      </header>

      <div class="shop-toolbar">
        <div>
          <h2 class="shop-section-title">本店商品</h2>
          <span class="shop-section-sub">共 {{ page.total }} 件 · 同一笔订单只属于一家店铺</span>
        </div>
        <el-select v-model="sort" class="shop-sort" @change="onSort">
          <el-option v-for="s in sortOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
      </div>

      <div v-if="!list.length" class="empty">
        <div class="empty-icon">空</div>
        <div class="empty-title">这家店暂时没有在售商品</div>
        <p class="empty-sub">换个店铺看看，或回到首页浏览全部商品。</p>
        <el-button class="mt-16" type="primary" @click="router.push('/')">去逛逛</el-button>
      </div>

      <template v-else>
        <div class="product-grid">
          <ProductCard
            v-for="(p, i) in list"
            :key="p.id"
            :product="p"
            :index="(page.current - 1) * page.size + i + 1"
            :favIds="favIds"
            @add="addToCart"
            @toggle-fav="toggleFav"
          />
        </div>

        <el-pagination
          v-if="page.total > page.size"
          class="shop-pager"
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          layout="total, prev, pager, next"
          background
          @current-change="onPage"
        />
      </template>
    </template>

    <div v-else-if="!loading" class="empty">
      <div class="empty-icon">店</div>
      <div class="empty-title">店铺不存在或已关闭</div>
      <p class="empty-sub">可能已被平台下架，去看看其他店铺吧。</p>
      <el-button class="mt-16" type="primary" @click="router.push('/')">回首页</el-button>
    </div>
  </div>

  <ShareDialog
    v-model="shareVisible"
    type="SHOP"
    :target-id="shop?.id"
    :title="shop?.name"
  />
  <ReportDialog
    v-model="reportVisible"
    target-type="SHOP"
    :target-id="shop?.id"
    :target-title="shop?.name"
  />
</template>

<style scoped>
.shop-page { min-height: 60vh; }
.shop-hero {
  margin-top: 10px;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(110deg, #171412, #3b2c23);
  background-size: cover;
  background-position: center;
  color: #fff;
}
.shop-hero-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 26px 24px;
  flex-wrap: wrap;
}
.shop-mark {
  width: 62px;
  height: 62px;
  flex: none;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-family: var(--font-display, serif);
  font-size: 28px;
  font-weight: 800;
  color: #fff;
  background: var(--brand, #d8342a);
  box-shadow: 0 10px 24px rgba(216, 52, 42, .35);
}
.shop-meta { min-width: 0; flex: 1 1 260px; }
.shop-title {
  margin: 0 0 6px;
  font-family: var(--font-display, serif);
  font-size: 26px;
  letter-spacing: .02em;
}
.shop-intro {
  margin: 0 0 10px;
  max-width: 640px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, .72);
}
.shop-facts { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.shop-fact {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: rgba(255, 255, 255, .85);
  background: rgba(255, 255, 255, .12);
  border: 1px solid rgba(255, 255, 255, .14);
}
.shop-hero-actions { margin-left: auto; }
.shop-ghost {
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, .35);
  background: transparent;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: background .2s, border-color .2s;
}
.shop-ghost:hover { background: rgba(255, 255, 255, .14); border-color: rgba(255, 255, 255, .6); }

.shop-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 30px 0 16px;
  flex-wrap: wrap;
}
.shop-section-title {
  margin: 0;
  font-family: var(--font-display, serif);
  font-size: 21px;
  letter-spacing: .03em;
}
.shop-section-sub { font-size: 12.5px; color: var(--text-3, #8a8175); }
.shop-sort { width: 160px; }
.shop-pager { margin-top: 26px; justify-content: center; }

@media (max-width: 640px) {
  .shop-hero-inner { padding: 20px 16px; }
  .shop-title { font-size: 21px; }
  .shop-hero-actions { margin-left: 0; }
  .shop-sort { width: 100%; }
}
</style>
