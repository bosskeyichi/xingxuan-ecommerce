<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Clock, SuccessFilled, Shop } from '@element-plus/icons-vue'
import { categoryApi, productApi, browseApi, shopApi } from '@/api'
import { coverUrl } from '@/utils/img'
import { useProductActions } from '@/composables/useProductActions'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const favIds = ref([])
const { loadFavIds, addToCart, toggleFav } = useProductActions(favIds)

const categories = ref([])
const shops = ref([])
const activeCat = ref('')
const activeShop = computed(() => (route.query.shopId ? Number(route.query.shopId) : ''))
const sort = ref('default')
const inStock = ref(false)
const minPrice = ref('')
const maxPrice = ref('')

const SORT_OPTIONS = [
  { label: '综合', value: 'default' },
  { label: '销量', value: 'sales_desc' },
  { label: '价格 ↑', value: 'price_asc' },
  { label: '价格 ↓', value: 'price_desc' },
  { label: '新品', value: 'newest' }
]
const list = ref([])
const total = ref(0)
const loading = ref(false)
const recos = ref([])
const hot = ref([])
const newest = ref([])
const page = reactive({ current: 1, size: 12 })

const pages = computed(() => Math.max(1, Math.ceil(total.value / page.size)))

/* ---------------- 轮播 ---------------- */
const slides = [
  {
    tag: '数码焕新',
    title: '编辑部甄选 · 数码上新',
    desc: '手机、笔记本、平板逐台验机后上架，附赠正品凭证与 7 天无理由退换。',
    cta: '去逛数码',
    cat: '手机数码',
    img: '/img/products/1.jpg'
  },
  {
    tag: '高效办公',
    title: '把工作台，升级一次',
    desc: '轻薄本、显示器与外设组合推荐，满 2 件享包邮，48 小时内发出。',
    cta: '看看办公好物',
    cat: '电脑办公',
    img: '/img/products/4.jpg'
  },
  {
    tag: '居家日常',
    title: '慢一点，也很好',
    desc: '从一杯手冲开始的生活提案：家电、餐厨与香氛，安静而耐用。',
    cta: '进入居家馆',
    cat: '家用电器',
    img: '/img/products/3.jpg'
  }
]
const slide = ref(0)
let slideTimer = null
const goSlide = i => { slide.value = (i + slides.length) % slides.length }
const nextSlide = () => goSlide(slide.value + 1)
const prevSlide = () => goSlide(slide.value - 1)
const startSlide = () => {
  stopSlide()
  slideTimer = setInterval(nextSlide, 5200)
}
const stopSlide = () => { if (slideTimer) { clearInterval(slideTimer); slideTimer = null } }

const slideCatId = name => {
  const c = categories.value.find(x => x.name === name)
  return c ? c.id : ''
}
const goSlideCat = s => {
  const id = slideCatId(s.cat)
  if (id) pickCat(id)
  else router.push('/')
}

/* ---------------- 限时特惠倒计时 ---------------- */
const remain = ref({ h: '00', m: '00', s: '00' })
let cdTimer = null
const tick = () => {
  const now = new Date()
  const end = new Date(now)
  end.setHours(24, 0, 0, 0)
  let diff = Math.max(0, Math.floor((end - now) / 1000))
  const h = String(Math.floor(diff / 3600)).padStart(2, '0')
  const m = String(Math.floor((diff % 3600) / 60)).padStart(2, '0')
  const s = String(diff % 60).padStart(2, '0')
  remain.value = { h, m, s }
}
const soldRate = p => {
  const sold = Number(p.sales || 0)
  const stock = Number(p.stock || 0)
  const base = sold + stock
  if (!base) return 0
  return Math.min(100, Math.round((sold / base) * 100))
}

/* ---------------- 数据加载 ---------------- */
const loadCategories = async () => {
  try {
    const r = await categoryApi.list()
    categories.value = r.data || []
  } catch (e) { categories.value = [] }
}
const loadShops = async () => {
  try {
    const r = await shopApi.list()
    shops.value = r.data || []
  } catch (e) { shops.value = [] }
}
const load = async () => {
  loading.value = true
  try {
    const r = await productApi.list({
      categoryId: activeCat.value || undefined,
      shopId: activeShop.value || undefined,
      sort: sort.value === 'default' ? undefined : sort.value,
      inStock: inStock.value || undefined,
      minPrice: minPrice.value ? Number(minPrice.value) : undefined,
      maxPrice: maxPrice.value ? Number(maxPrice.value) : undefined,
      current: page.current,
      size: page.size
    })
    list.value = r.data.records || []
    total.value = r.data.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally { loading.value = false }
}
const loadHot = async () => {
  try {
    const r = await productApi.hot({ limit: 6 })
    hot.value = r.data || []
  } catch (e) { hot.value = [] }
}
const loadNewest = async () => {
  try {
    const r = await productApi.list({ sort: 'newest', current: 1, size: 4 })
    newest.value = r.data.records || []
  } catch (e) { newest.value = [] }
}
// 猜你喜欢：按账号浏览足迹的分类聚合，取同类热销商品（排除已看过的）
const loadRecos = async () => {
  if (!userStore.isLogin) { recos.value = []; return }
  let viewed = []
  try {
    const r = await browseApi.list()
    viewed = r.data || []
  } catch (e) {
    recos.value = []
    return
  }
  const catIds = [...new Set(viewed.map(p => p.categoryId).filter(Boolean))].slice(0, 3)
  if (!catIds.length) { recos.value = []; return }
  try {
    const r = await productApi.recommend({ categoryIds: catIds.join(','), limit: 8 })
    const seen = new Set(viewed.map(p => p.id))
    recos.value = (r.data || []).filter(p => !seen.has(p.id)).slice(0, 4)
  } catch (e) { recos.value = [] }
}

const activeCatName = computed(() => {
  const c = categories.value.find(x => x.id === activeCat.value)
  return c ? c.name : ''
})

// 分类切换后的「落地反馈」：滚动到商品区 + 短暂高亮，让用户明确知道已切到该分类
const feedRef = ref(null)
const feedFlash = ref(false)
const scrollToFeed = async () => {
  await nextTick()
  const el = feedRef.value
  if (!el) return
  // 用即时定位（而非长距离平滑滚动）：点分类后立刻落在商品区，反馈更明确
  const headerH = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--header-h')) || 98
  const top = Math.max(0, el.getBoundingClientRect().top + window.scrollY - headerH - 12)
  if (Math.abs(window.scrollY - top) > 4) window.scrollTo({ top })
  feedFlash.value = true
  window.clearTimeout(scrollToFeed._t)
  scrollToFeed._t = window.setTimeout(() => { feedFlash.value = false }, 2400)
}

// 分类与 URL 同步：/products?categoryId=3 可直接分享、刷新后保持
const writeCategoryQuery = id => {
  const query = { ...route.query }
  if (id) query.categoryId = String(id)
  else delete query.categoryId
  router.replace({ path: route.path, query })
}
const pickCat = id => {
  const same = String(activeCat.value) === String(id)
  if (!same) {
    activeCat.value = id
    page.current = 1
    writeCategoryQuery(id)
    // 数据回来后按最新布局再对齐一次（列表由骨架屏换回真实卡片时会轻微变化）
    load().then(scrollToFeed)
  }
  // 先立刻跳过去，不等接口；同分类重复点击也照样给反馈
  scrollToFeed()
}
watch(() => route.query.categoryId, q => {
  const v = q ? Number(q) : ''
  if (v === activeCat.value) return
  activeCat.value = v
  page.current = 1
  load().then(scrollToFeed)
})

// 店铺与 URL 同步：/products?shopId=2 即只看该店铺商品
const pickShop = id => {
  const query = { ...route.query }
  if (id) query.shopId = String(id)
  else delete query.shopId
  page.current = 1
  // 选中同一店铺时 query 不变，watch 不会触发，需手动刷新一次
  if (String(route.query.shopId || '') === String(query.shopId || '')) {
    load().then(scrollToFeed)
    return
  }
  router.replace({ path: route.path, query })
}
// 店铺变化统一由 watch 触发一次加载，别让 pickShop 与 watch 重复请求
watch(() => route.query.shopId, () => { page.current = 1; load().then(scrollToFeed) })

const applyFilter = () => { page.current = 1; load() }
const onSize = s => { page.size = s; page.current = 1; load() }
const reset = () => {
  activeCat.value = ''
  sort.value = 'default'
  inStock.value = false
  minPrice.value = ''
  maxPrice.value = ''
  page.current = 1
  writeCategoryQuery('')
  const query = { ...route.query }
  delete query.shopId
  router.replace({ path: route.path, query })
  load()
}

// 已选筛选条件（可单独取消）
const activeFilters = computed(() => {
  const arr = []
  if (activeCat.value) {
    const c = categories.value.find(x => x.id === activeCat.value)
    arr.push({ key: 'cat', label: `分类：${c ? c.name : activeCat.value}`, clear: () => pickCat('') })
  }
  if (sort.value !== 'default') {
    const s = SORT_OPTIONS.find(x => x.value === sort.value)
    arr.push({ key: 'sort', label: `排序：${s ? s.label : sort.value}`, clear: () => { sort.value = 'default'; applyFilter() } })
  }
  if (minPrice.value || maxPrice.value) {
    const label = `价格：${minPrice.value ? '¥' + minPrice.value : '不限'} - ${maxPrice.value ? '¥' + maxPrice.value : '不限'}`
    arr.push({ key: 'price', label, clear: () => { minPrice.value = ''; maxPrice.value = ''; applyFilter() } })
  }
  if (inStock.value) {
    arr.push({ key: 'stock', label: '仅看有货', clear: () => { inStock.value = false; applyFilter() } })
  }
  if (activeShop.value) {
    const s = shops.value.find(x => x.id === activeShop.value)
    arr.push({ key: 'shop', label: `店铺：${s ? s.name : activeShop.value}`, clear: () => pickShop('') })
  }
  return arr
})
const onPage = c => { page.current = c; load(); window.scrollTo({ top: 380, behavior: 'smooth' }) }

onMounted(async () => {
  const q = route.query.categoryId
  if (q) activeCat.value = Number(q)
  tick()
  cdTimer = setInterval(tick, 1000)
  startSlide()
  await loadCategories()
  await loadShops()
  await load()
  await loadFavIds()
  await loadHot()
  await loadNewest()
  await loadRecos()
  // 所有区块渲染完成后再定位，别让上面的「买手笔记 / 闪购」把商品区推走
  if (q) scrollToFeed()
})

onBeforeUnmount(() => {
  stopSlide()
  if (cdTimer) clearInterval(cdTimer)
})
</script>

<template>
  <div class="home">
    <!-- 一、分类目录 + 主轮播 -->
    <section class="hero-grid">
      <aside class="cat-menu">
        <div class="cat-menu-title">
          <span>商品分类</span>
          <router-link to="/categories" class="cat-menu-all">全部</router-link>
        </div>
        <button class="cat-menu-item" :class="{ active: activeCat === '' }" @click="pickCat('')">
          <span>全部商品</span><span class="cat-count">All</span>
        </button>
        <button
          v-for="c in categories"
          :key="c.id"
          class="cat-menu-item"
          :class="{ active: activeCat === c.id }"
          @click="pickCat(c.id)"
        >
          <span>{{ c.name }}</span>
          <span class="cat-count">›</span>
        </button>
        <div class="cat-menu-foot">
          <span>一次只推 100 件</span>
          <span>逐件验货</span>
        </div>
      </aside>

      <div class="banner" @mouseenter="stopSlide" @mouseleave="startSlide">
        <div
          v-for="(s, i) in slides"
          :key="i"
          class="slide"
          :class="{ 'is-active': slide === i }"
        >
          <img class="slide-img" :src="s.img" :alt="s.title" />
          <div class="slide-body">
            <span class="slide-tag">{{ s.tag }}</span>
            <h2 class="slide-title">{{ s.title }}</h2>
            <p class="slide-desc">{{ s.desc }}</p>
            <button class="btn-grad slide-cta" @click="goSlideCat(s)">
              {{ s.cta }}
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
        <div class="dots">
          <button
            v-for="(s, i) in slides"
            :key="'d' + i"
            class="dot-btn"
            :class="{ 'is-active': slide === i }"
            :aria-label="`第 ${i + 1} 张`"
            @click="goSlide(i)"
          />
        </div>
        <div class="arrows">
          <button class="arrow" aria-label="上一张" @click="prevSlide"><el-icon><ArrowLeft /></el-icon></button>
          <button class="arrow" aria-label="下一张" @click="nextSlide"><el-icon><ArrowRight /></el-icon></button>
        </div>
      </div>
    </section>

    <!-- 二、星选闪购 -->
    <section v-if="hot.length" class="flash-card">
      <div class="flash-head">
        <h2 class="flash-title"><i class="h2-star" />星选闪购<span class="h2-kicker">Flash</span></h2>
        <span class="flash-sub">买手挑过的热销单品 · 今日 24:00 结束</span>
        <div class="countdown">
          <el-icon><Clock /></el-icon>
          <span class="cd-num">{{ remain.h }}</span>:<span class="cd-num">{{ remain.m }}</span>:<span class="cd-num">{{ remain.s }}</span>
        </div>
      </div>
      <div class="flash-list">
        <div v-for="p in hot" :key="'h' + p.id" class="flash-item" @click="router.push(`/product/${p.id}`)">
          <div class="flash-thumb">
            <img v-if="p.cover" :src="coverUrl(p.cover)" :alt="p.name" loading="lazy" />
            <div v-else class="img-ph">暂无图片</div>
          </div>
          <div class="flash-name">{{ p.name }}</div>
          <div class="price flash-price"><span class="cur">¥</span>{{ Number(p.price).toLocaleString() }}</div>
          <div class="progress"><i :style="{ width: soldRate(p) + '%' }" /></div>
          <div class="flash-sold">已抢 {{ soldRate(p) }}% · 仅剩 {{ p.stock }} 件</div>
        </div>
      </div>
    </section>

    <!-- 三、商品列表 + 筛选 -->
    <section ref="feedRef" class="feed" :class="{ 'is-flash': feedFlash }">
      <aside class="rail">
        <div class="rail-title"><i class="h2-star" />筛选条件</div>
        <div class="rail-block">
          <div class="rail-label">排序</div>
          <div class="rail-sorts">
            <button
              v-for="o in SORT_OPTIONS"
              :key="o.value"
              class="rail-sort"
              :class="{ 'is-active': sort === o.value }"
              @click="sort = o.value; applyFilter()"
            >{{ o.label }}</button>
          </div>
        </div>
        <div class="rail-block">
          <div class="rail-label">价格区间</div>
          <div class="price-range">
            <input v-model="minPrice" type="number" placeholder="最低" @keyup.enter="applyFilter" />
            <span class="dash">-</span>
            <input v-model="maxPrice" type="number" placeholder="最高" @keyup.enter="applyFilter" />
          </div>
          <button class="price-apply rail-apply" @click="applyFilter">确定</button>
        </div>
        <div class="rail-block">
          <label class="rail-check">
            <input v-model="inStock" type="checkbox" @change="applyFilter" />
            仅看有货
          </label>
        </div>
        <button class="rail-reset" @click="reset">重置全部条件</button>
      </aside>

      <div class="feed-main">
        <div v-if="activeCat && feedFlash" class="feed-notice">
          <el-icon><SuccessFilled /></el-icon>
          已切换到「{{ activeCatName }}」，下面是 {{ total }} 件该分类的选品
        </div>

        <div class="feed-head">
          <div>
            <h2 class="feed-title">
              <i class="h2-star" />{{ activeCat ? (categories.find(c => c.id === activeCat)?.name || '分类选品') : '全部选品' }}<span class="h2-kicker">{{ activeCat ? 'Category' : 'All Picks' }}</span>
            </h2>
            <p class="feed-sub">共 {{ total }} 件商品 · 每页 {{ page.size }} 件 · 第 {{ page.current }}/{{ pages }} 页</p>
          </div>
          <div class="feed-tools">
            <el-select
              :model-value="activeShop"
              class="feed-shop"
              placeholder="搜索或选择店铺"
              filterable
              clearable
              @change="v => pickShop(v || '')"
            >
              <el-option v-for="s in shops" :key="s.id" :value="s.id" :label="s.name" />
            </el-select>
            <el-button class="feed-shop-all" text @click="router.push('/shops')">
              <el-icon><Shop /></el-icon>全部店铺
            </el-button>
            <el-select :model-value="page.size" class="feed-size" @change="onSize">
              <el-option :value="12" label="12 件/页" />
              <el-option :value="24" label="24 件/页" />
              <el-option :value="36" label="36 件/页" />
            </el-select>
          </div>
        </div>

        <div v-if="activeFilters.length" class="chips">
          <span class="chips-label">已选</span>
          <button v-for="f in activeFilters" :key="f.key" class="chip" @click="f.clear()">
            {{ f.label }}<i>×</i>
          </button>
          <button class="chips-clear" @click="reset">清空</button>
        </div>

        <div v-if="loading" class="home-loading">
          <div v-for="i in 8" :key="i" class="skeleton" />
        </div>
        <div v-else-if="!list.length" class="empty">
          <div class="empty-icon">空</div>
          <div class="empty-title">没有符合条件的商品</div>
          <div class="empty-sub">换个分类或放宽价格区间试试</div>
          <el-button class="mt-16" @click="reset">查看全部商品</el-button>
        </div>
        <div v-else class="product-grid">
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
          v-if="pages > 1"
          :current-page="page.current"
          :page-size="page.size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="onPage"
        />
      </div>
    </section>

    <!-- 四、新品速递 -->
    <section v-if="newest.length" class="duo">
      <div class="duo-card">
        <div class="duo-head">
          <h2 class="duo-title"><i class="h2-star" />今日上新<span class="h2-kicker">New In</span></h2>
          <router-link to="/" class="duo-more">全部选品 →</router-link>
        </div>
        <div class="newest-grid">
          <div v-for="p in newest" :key="'n' + p.id" class="newest-item" @click="router.push(`/product/${p.id}`)">
            <div class="newest-thumb">
              <img v-if="p.cover" :src="coverUrl(p.cover)" :alt="p.name" loading="lazy" />
              <div v-else class="img-ph">暂无图片</div>
            </div>
            <div class="newest-info">
              <div class="newest-name">{{ p.name }}</div>
              <div class="price newest-price"><span class="cur">¥</span>{{ Number(p.price).toLocaleString() }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="duo-card promise-card">
        <h2 class="duo-title"><i class="h2-star" />为什么选择星选<span class="h2-kicker">Our Way</span></h2>
        <ul class="promise-list">
          <li><b>逐件验货</b><span>入库前统一质检，瑕疵率高于 1% 整批退回</span></li>
          <li><b>价格透明</b><span>页面价即到手价，结算时不再叠加莫名费用</span></li>
          <li><b>订单可追溯</b><span>待支付 → 已支付 → 已发货 → 已完成全程留痕</span></li>
          <li><b>售后无忧</b><span>7 天无理由退换，客服 1 对 1 跟进处理</span></li>
        </ul>
      </div>
    </section>

    <!-- 五、猜你喜欢 -->
    <section v-if="recos.length" class="reco">
      <div class="reco-head">
        <h2 class="reco-title"><i class="h2-star" />为你挑选<span class="h2-kicker">For You</span></h2>
        <span class="reco-sub">按你最近看过的类目，挑了几件同类型的</span>
      </div>
      <div class="product-grid">
        <ProductCard
          v-for="p in recos"
          :key="'r' + p.id"
          :product="p"
          :favIds="favIds"
          @add="addToCart"
          @toggle-fav="toggleFav"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.home { display: flex; flex-direction: column; gap: 18px; }

/* 轮播区 */
.hero-grid { display: grid; grid-template-columns: 232px 1fr; gap: 16px; }
.cat-menu {
  background: var(--surface);
  border-radius: var(--r-lg);
  padding: 8px;
  display: flex; flex-direction: column;
  box-shadow: var(--sh-card);
}
.cat-menu-title {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px 12px;
  font-size: 14px; font-weight: 700;
  border-bottom: 1px solid var(--border);
  margin-bottom: 4px;
}
.cat-menu-all { font-size: 12px; color: var(--ink-3); font-weight: 400; }
.cat-menu-all:hover { color: var(--brand); }
.cat-menu-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px;
  border: none; background: transparent;
  border-radius: var(--r-sm);
  font-size: 13.5px; color: var(--ink-2);
  cursor: pointer; text-align: left;
  transition: all .16s ease;
}
.cat-menu-item:hover { background: var(--surface-2); color: var(--brand); }
.cat-menu-item.active { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
.cat-count { font-size: 11px; color: var(--ink-4); }
.cat-menu-item.active .cat-count { color: var(--brand); }
.cat-menu-foot {
  margin-top: auto; padding: 12px;
  display: flex; justify-content: space-between;
  font-size: 10.5px; color: var(--ink-4);
  border-top: 1px dashed var(--border);
}

.banner {
  position: relative;
  height: 356px;
  border-radius: var(--r-lg);
  overflow: hidden;
  background: linear-gradient(115deg, #fff5f3 0%, #ffffff 55%, #f7f7f7 100%);
  box-shadow: var(--sh-card);
}
.slide {
  position: absolute; inset: 0;
  opacity: 0; visibility: hidden;
  transition: opacity .6s ease, visibility .6s ease;
}
.slide.is-active { opacity: 1; visibility: visible; }
.slide-img {
  position: absolute; right: 0; top: 0;
  width: 52%; height: 100%;
  object-fit: cover;
}
.slide::after {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(90deg, #fff 34%, rgba(255, 255, 255, .85) 46%, rgba(255, 255, 255, 0) 72%);
}
.slide-body { position: relative; z-index: 2; padding: 0 48px; max-width: 56%; top: 50%; transform: translateY(-50%); }
.slide-tag {
  display: inline-block;
  padding: 3px 12px;
  border-radius: var(--r-full);
  background: var(--brand);
  color: #fff; font-size: 11.5px; letter-spacing: .1em;
}
.slide-title {
  font-family: var(--font-display);
  font-size: 34px; font-weight: 700;
  line-height: 1.3; margin-top: 16px;
  letter-spacing: .02em;
}
.slide-desc { margin-top: 12px; font-size: 13px; color: var(--ink-3); line-height: 1.95; }
.slide-cta { margin-top: 22px; height: 40px; padding: 0 24px; font-size: 13.5px; letter-spacing: .06em; }
.dots { position: absolute; left: 48px; bottom: 20px; display: flex; gap: 6px; z-index: 3; }
.dot-btn {
  width: 18px; height: 4px; border: none;
  border-radius: 2px; background: rgba(0, 0, 0, .14);
  cursor: pointer; padding: 0;
  transition: all .25s ease;
}
.dot-btn.is-active { width: 30px; background: var(--brand); }
.arrows { position: absolute; right: 20px; bottom: 18px; display: flex; gap: 8px; z-index: 3; }
.arrow {
  width: 32px; height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, .06);
  background: rgba(255, 255, 255, .86);
  color: var(--ink-2);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: all .18s ease;
}
.arrow:hover { background: var(--brand); color: #fff; border-color: var(--brand); }


/* 限时特惠 */
.flash-card {
  background: var(--surface);
  border-radius: var(--r-lg);
  padding: 20px 22px 18px;
  box-shadow: var(--sh-card);
}
.flash-head { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.flash-title { font-family: var(--font-display); font-size: 22px; font-weight: 700; letter-spacing: .04em; }
.flash-sub { font-size: 12px; color: var(--ink-3); }
.countdown { margin-left: auto; display: flex; align-items: center; gap: 4px; font-size: 12.5px; color: var(--ink-3); }
.countdown > .el-icon { margin-right: 2px; color: var(--promo); }
.cd-num {
  display: inline-block;
  min-width: 24px;
  padding: 1px 5px;
  border-radius: var(--r-xs);
  background: #1a1a1a; color: #fff;
  font-family: var(--font-num);
  font-size: 12.5px; text-align: center;
}
.flash-list {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}
.flash-item { padding: 8px; border-radius: var(--r-md); cursor: pointer; transition: background .18s ease, transform .18s ease; }
.flash-item:hover { background: var(--surface-2); transform: translateY(-2px); }
.flash-thumb { aspect-ratio: 1 / 1; border-radius: var(--r-sm); overflow: hidden; background: var(--surface-2); }
.flash-thumb img { width: 100%; height: 100%; object-fit: cover; }
.flash-name {
  margin-top: 8px; font-size: 12.5px; line-height: 1.5;
  color: var(--ink-2); height: 38px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.flash-price { font-size: 16px; margin-top: 4px; }
.progress { height: 6px; border-radius: 3px; background: var(--surface-3); overflow: hidden; margin-top: 8px; }
.progress i { display: block; height: 100%; border-radius: 3px; background: var(--brand); transition: width .4s ease; }
.flash-sold { font-size: 11px; color: var(--ink-3); margin-top: 4px; }

/* 商品列表 */
.feed {
  display: grid; grid-template-columns: 232px 1fr; gap: 16px; align-items: start; margin-top: 10px;
  scroll-margin-top: calc(var(--header-h) + 12px);
}
/* 切换分类后的落地反馈 */
.feed-notice {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px; padding: 10px 14px;
  border-radius: var(--r-md);
  background: var(--gold-soft);
  border: 1px solid #e9dcc4;
  color: var(--gold-deep);
  font-size: 12.5px;
}
.feed-notice .el-icon { font-size: 15px; }
.feed.is-flash .feed-main { animation: feedFlash 1.2s ease; border-radius: var(--r-lg); }
@keyframes feedFlash {
  0% { background: var(--brand-soft); box-shadow: inset 0 0 0 1px #f3c9c4; }
  100% { background: transparent; box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0); }
}
.rail {
  position: sticky;
  top: calc(var(--header-h) + 16px);
  background: var(--surface);
  border-radius: var(--r-lg);
  padding: 16px;
  box-shadow: var(--sh-card);
}
.rail-title { font-size: 14px; font-weight: 700; margin-bottom: 14px; display: flex; align-items: center; gap: 7px; }
.rail-block { margin-bottom: 18px; }
.rail-label { font-size: 11.5px; color: var(--ink-3); margin-bottom: 10px; letter-spacing: .06em; }
.rail-sorts { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.rail-sort {
  height: 30px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-size: 12.5px; color: var(--ink-2);
  cursor: pointer;
  transition: all .15s ease;
}
.rail-sort:hover { border-color: var(--brand); color: var(--brand); }
.rail-sort.is-active { background: var(--brand); border-color: var(--brand); color: #fff; }
.rail-apply { width: 100%; margin-top: 8px; }
.rail-check { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--ink-2); cursor: pointer; }
.rail-check input { width: 14px; height: 14px; accent-color: var(--brand); cursor: pointer; }
.rail-reset {
  width: 100%; padding: 8px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-sm);
  background: transparent;
  font-size: 12px; color: var(--ink-3);
  cursor: pointer;
}
.rail-reset:hover { color: var(--brand); border-color: var(--brand); }

.feed-main { min-width: 0; }
.feed-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; margin-bottom: 14px; }
.feed-title { font-family: var(--font-display); font-size: 22px; font-weight: 700; letter-spacing: .04em; }
.feed-sub { font-size: 12px; color: var(--ink-3); margin-top: 4px; }
.feed-tools { display: flex; align-items: center; gap: 8px; }
.feed-shop { width: 190px; }
.feed-shop :deep(.el-select__wrapper) { min-height: 32px; font-size: 12.5px; }
.feed-shop-all { font-size: 12.5px; height: 32px; padding: 0 8px; }
.feed-shop-all .el-icon { margin-right: 4px; }
.feed-size { width: 108px; }
.feed-size :deep(.el-select__wrapper) { min-height: 32px; font-size: 12.5px; }

.chips { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.chips-label { font-size: 11.5px; color: var(--ink-4); }
.chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px;
  border: 1px solid #ffd4d0;
  border-radius: var(--r-full);
  background: var(--brand-soft);
  font-size: 12px; color: var(--brand);
  cursor: pointer;
}
.chip i { font-style: normal; opacity: .6; }
.chip:hover i { opacity: 1; }
.chips-clear { border: none; background: none; font-size: 12px; color: var(--ink-3); cursor: pointer; }
.chips-clear:hover { color: var(--brand); }

.home-loading { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.skeleton {
  height: 300px;
  border-radius: var(--r-md);
  background: linear-gradient(100deg, #f3f3f3 30%, #fafafa 45%, #f3f3f3 60%);
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}
@keyframes shimmer { from { background-position: 150% 0; } to { background-position: -50% 0; } }

/* 新品 + 品牌承诺 */
.duo { display: grid; grid-template-columns: 1.35fr 1fr; gap: 16px; margin-top: 10px; }
.duo-card { background: var(--surface); border-radius: var(--r-lg); padding: 20px 22px; box-shadow: var(--sh-card); }
.duo-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; }
.duo-title { font-family: var(--font-display); font-size: 20px; font-weight: 700; letter-spacing: .04em; }
.duo-more { font-size: 12px; color: var(--ink-3); }
.duo-more:hover { color: var(--brand); }
.newest-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.newest-item { display: flex; gap: 12px; padding: 8px; border-radius: var(--r-md); cursor: pointer; transition: background .18s ease; }
.newest-item:hover { background: var(--surface-2); }
.newest-thumb { width: 74px; height: 74px; border-radius: var(--r-sm); overflow: hidden; background: var(--surface-2); flex-shrink: 0; }
.newest-thumb img { width: 100%; height: 100%; object-fit: cover; }
.newest-info { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 6px; }
.newest-name {
  font-size: 13px; color: var(--ink-2); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.newest-price { font-size: 15px; }
.promise-card { background: linear-gradient(150deg, #f6efe2 0%, #ffffff 62%); }
.promise-list { display: flex; flex-direction: column; gap: 14px; margin-top: 4px; }
.promise-list li { display: flex; flex-direction: column; gap: 2px; padding-left: 12px; border-left: 2px solid var(--brand); }
.promise-list b { font-size: 13.5px; }
.promise-list span { font-size: 12px; color: var(--ink-3); line-height: 1.7; }

/* 猜你喜欢 */
.reco { margin-top: 16px; }
.reco-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.reco-title { font-family: var(--font-display); font-size: 22px; font-weight: 700; letter-spacing: .04em; }
.reco-sub { font-size: 12px; color: var(--ink-3); }

@media (max-width: 1180px) {
  .flash-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1080px) {
  .hero-grid { grid-template-columns: 1fr; }
  .cat-menu { flex-direction: row; flex-wrap: wrap; align-items: center; }
  .cat-menu-title { width: 100%; }
  .cat-menu-foot { display: none; }
  .feed { grid-template-columns: 1fr; }
  .rail { position: static; }
  .rail-sorts { grid-template-columns: repeat(5, 1fr); }
  .duo { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .banner { height: 260px; }
  .slide-body { padding: 0 24px; max-width: 78%; }
  .slide-title { font-size: 24px; }
  .slide-desc { display: none; }
  .slide-cta { margin-top: 16px; height: 36px; }
  .dots { left: 24px; bottom: 14px; }
  .flash-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .home-loading { grid-template-columns: repeat(2, 1fr); }
  .newest-grid { grid-template-columns: 1fr; }
  .rail-sorts { grid-template-columns: repeat(3, 1fr); }
  .feed-head { flex-direction: column; align-items: flex-start; }
}
</style>
