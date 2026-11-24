<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ShoppingCart, Star, ChatLineSquare, Switch, CircleCheck, RefreshLeft, Van, Service, Share, Warning
} from '@element-plus/icons-vue'
import { productApi, categoryApi, cartApi, favoriteApi, reviewApi, browseApi, messageApi } from '@/api'
import { compare } from '@/utils/compare'
import { catImgUrl } from '@/utils/img'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import ReportDialog from '@/components/ReportDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref(null)
const qty = ref(1)
const categoryName = ref('')
const categoryId = ref('')
const imgSrc = ref('')
const imgError = ref(false)
const favOn = ref(false)
const buyLoading = ref(false)
const cartLoading = ref(false)
const recos = ref([])
const favIds = ref([])
const loadError = ref('')

const tab = ref('detail')
const reviews = ref([])
const summary = ref({ count: 0, avgRating: 0, distribution: {} })
const myRating = ref(5)
const myContent = ref('')
const myAnonymous = ref(false)
const reviewLoading = ref(false)
const shareVisible = ref(false)
const reportVisible = ref(false)

const initial = computed(() => (product.value?.name || '?').trim()[0] || '?')
const onSale = computed(() => product.value?.status === 1)
const soldOut = computed(() => Number(product.value?.stock || 0) <= 0)
// 店铺关店时商品仍可查看历史信息，但不能再下单
const shopClosed = computed(() => product.value?.shopStatus === 0)
const canBuy = computed(() => onSale.value && !soldOut.value && !shopClosed.value)
const maxQty = computed(() => Math.max(1, Number(product.value?.stock || 1)))

// 服务承诺由店铺在后台商品编辑里自行维护，0 / 未设置即不展示
const freeShipping = computed(() => Number(product.value?.freeShipping ?? 1) === 1)
const shipHours = computed(() => Number(product.value?.shipHours ?? 0))
const returnDays = computed(() => Number(product.value?.returnDays ?? 0))
const hasPromise = computed(() => freeShipping.value || shipHours.value > 0 || returnDays.value > 0)

/** 封面：优先商品图，其次分类图（jpg 失败换 svg 海报） */
function handleImgError() {
  if (imgSrc.value.endsWith('.jpg')) imgSrc.value = imgSrc.value.replace(/\.jpg$/, '.svg')
  else imgError.value = true
}

const load = async () => {
  loadError.value = ''
  try {
    const r = await productApi.detail(route.params.id)
    if (!r.data) throw new Error('商品不存在或已下架')
    product.value = r.data
    qty.value = 1
    imgError.value = false
    imgSrc.value = r.data.cover || catImgUrl(r.data.categoryId)
    // 记录浏览足迹（服务端按账号保存，随「重置数据」一并清空）
    if (userStore.isLogin) {
      browseApi.record(r.data.id).catch(() => {})
    }
    const cr = await categoryApi.list()
    const cat = (cr.data || []).find(x => x.id === r.data.categoryId)
    categoryName.value = cat ? cat.name : ''
    categoryId.value = r.data.categoryId
    loadRecos()
  } catch (e) {
    product.value = null
    loadError.value = e?.message || '商品不存在或已下架'
  }
}

const loadRecos = async () => {
  if (!product.value?.categoryId) return
  try {
    const r = await productApi.recommend({ categoryIds: String(product.value.categoryId), limit: 5 })
    recos.value = (r.data || []).filter(p => p.id !== product.value.id).slice(0, 4)
  } catch (e) { recos.value = [] }
}

const loadFav = async () => {
  if (!userStore.isLogin) { favOn.value = false; return }
  try {
    const [check, ids] = await Promise.all([
      favoriteApi.check(route.params.id),
      favoriteApi.ids()
    ])
    favOn.value = !!check.data?.favorited
    favIds.value = ids.data || []
  } catch (e) { favOn.value = false }
}

const loadReviews = async () => {
  try {
    const [a, b] = await Promise.all([
      reviewApi.list(route.params.id),
      reviewApi.summary(route.params.id)
    ])
    reviews.value = a.data || []
    summary.value = b.data || { count: 0, avgRating: 0, distribution: {} }
  } catch (e) { reviews.value = [] }
}

const clampQty = () => {
  const n = Math.floor(Number(qty.value) || 1)
  qty.value = Math.min(maxQty.value, Math.max(1, n))
}
const decQty = () => { if (qty.value > 1) qty.value-- }
const incQty = () => { if (qty.value < maxQty.value) qty.value++ }

const onAddCart = async () => {
  if (!userStore.isLogin) { ElMessage.warning('请先登录'); router.push('/login'); return }
  if (!canBuy.value) return
  cartLoading.value = true
  try {
    await cartApi.add({ productId: product.value.id, quantity: qty.value })
    ElMessage.success('已加入购物车')
    window.dispatchEvent(new Event('cart:refresh'))
  } finally { cartLoading.value = false }
}

const onBuyNow = async () => {
  if (!userStore.isLogin) { ElMessage.warning('请先登录'); router.push('/login'); return }
  if (!canBuy.value) return
  buyLoading.value = true
  try {
    await cartApi.add({ productId: product.value.id, quantity: qty.value })
    // 立即购买只结算这一件：先把购物车其它行取消勾选，仅保留当前商品，
    // 否则结算页会把之前已勾选的其它店铺商品一起下单
    const r = await cartApi.list()
    const rows = r.data || []
    await Promise.all(rows.map(row => {
      const want = row.productId === product.value.id ? 1 : 0
      return Number(row.selected) === want
        ? null
        : cartApi.updateSelected({ cartId: row.id, selected: want })
    }))
    window.dispatchEvent(new Event('cart:refresh'))
    router.push('/checkout')
  } finally { buyLoading.value = false }
}

const toggleFav = async () => {
  if (!userStore.isLogin) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    if (favOn.value) {
      await favoriteApi.remove(product.value.id)
      favIds.value = favIds.value.filter(id => id !== product.value.id)
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.add(product.value.id)
      favIds.value = [...favIds.value, product.value.id]
      ElMessage.success('已加入收藏')
    }
    favOn.value = !favOn.value
  } catch (e) { /* 统一拦截提示 */ }
}

const addRecoToCart = async p => {
  if (!userStore.isLogin) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    await cartApi.add({ productId: p.id, quantity: 1 })
    ElMessage.success('已加入购物车')
    window.dispatchEvent(new Event('cart:refresh'))
  } catch (e) { /* 统一拦截提示 */ }
}

const ensureLogin = () => {
  if (userStore.isLogin) return true
  ElMessage.warning('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

/** 联系客服：找到商品所属店铺的店主，跳到客服会话 */
const onService = async () => {
  if (!ensureLogin()) return
  if (!product.value?.shopId) return ElMessage.warning('该商品暂无店铺客服')
  try {
    const r = await messageApi.serviceTarget(product.value.shopId)
    if (r.data?.userId && r.data.userId === userStore.userInfo?.id) {
      return ElMessage.info('这是你自己店铺的客服会话')
    }
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

const submitReview = async () => {
  if (!ensureLogin()) return
  if (!myContent.value.trim()) { ElMessage.warning('请先写下你的使用感受'); return }
  reviewLoading.value = true
  try {
    await reviewApi.add({
      productId: product.value.id,
      rating: myRating.value,
      content: myContent.value,
      anonymous: myAnonymous.value ? 1 : 0
    })
    ElMessage.success(myAnonymous.value ? '匿名评价已发布' : '评价已发布')
    myContent.value = ''
    myAnonymous.value = false
    await loadReviews()
  } finally { reviewLoading.value = false }
}

const stars = n => '★'.repeat(n) + '☆'.repeat(5 - n)
const fmtDate = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''
const fmtPrice = v => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })

const compareOn = computed(() => product.value ? compare.has(product.value.id) : false)
const toggleCompare = () => {
  if (!product.value) return
  if (compareOn.value) compare.remove(product.value.id)
  else compare.add(product.value)
  window.dispatchEvent(new Event('compare-changed'))
}

onMounted(async () => {
  await load()
  await loadFav()
  await loadReviews()
})
</script>

<template>
  <div v-if="product">
    <!-- 面包屑 -->
    <el-breadcrumb separator="/" class="mb-16">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-if="categoryId" :to="{ path: '/', query: { categoryId } }">
        {{ categoryName || '分类' }}
      </el-breadcrumb-item>
      <el-breadcrumb-item>商品详情</el-breadcrumb-item>
    </el-breadcrumb>

    <div class="detail-wrap">
      <!-- 左：图片 -->
      <div>
        <div class="detail-media">
          <div class="img-box">
            <img
              v-if="!imgError && imgSrc"
              :src="imgSrc"
              :alt="product.name"
              @error="handleImgError"
            />
            <div v-else class="img-ph" style="font-size:70px">{{ initial }}</div>
          </div>
        </div>
        <div class="detail-service detail-service--media">
          <span><el-icon><CircleCheck /></el-icon>逐件验货</span>
          <span v-if="returnDays > 0"><el-icon><RefreshLeft /></el-icon>{{ returnDays }} 天无理由</span>
          <span v-if="shipHours > 0"><el-icon><Van /></el-icon>{{ shipHours }}h 发货</span>
          <span><el-icon><Service /></el-icon>买手答疑</span>
        </div>
      </div>

      <!-- 右：信息 -->
      <div class="detail-info">
        <div class="detail-cat-row">
          <router-link v-if="categoryId" class="detail-cat" :to="{ path: '/', query: { categoryId } }">
            {{ categoryName || '未分类' }}
          </router-link>
          <span class="detail-no">NO.{{ String(product.id || 0).padStart(3, '0') }}</span>
        </div>
        <h1 class="detail-title">{{ product.name }}</h1>
        <div v-if="product.shopName" class="detail-shop">
          <router-link class="shop-pill" :to="{ name: 'shop', params: { id: product.shopId } }">
            {{ product.shopName }}
          </router-link>
          <router-link class="shop-visit" :to="{ name: 'shop', params: { id: product.shopId } }">
            进店逛逛 ›
          </router-link>
          <span v-if="shopClosed" class="text-sm" style="color:var(--promo)">该店铺暂停营业，商品暂不可下单</span>
          <span v-else class="text-sm text-muted">本店发货 · 独立售后 · 支持跨店合并结算</span>
        </div>
        <p class="detail-sub">{{ product.description || '暂无商品描述。' }}</p>

        <div class="detail-price-box">
          <span class="price"><span class="cur">¥</span>{{ fmtPrice(product.price) }}</span>
          <span class="detail-price-note">
            已售 {{ product.sales || 0 }} 件 ·
            {{ onSale ? '现货发售中' : '该商品已下架' }}
          </span>
        </div>

        <div class="detail-tags">
          <span v-if="freeShipping" class="detail-tag">包邮</span>
          <span v-if="shipHours > 0" class="detail-tag">{{ shipHours }}h 内发货</span>
          <span v-if="returnDays > 0" class="detail-tag">支持 {{ returnDays }} 天无理由</span>
          <span v-if="!hasPromise" class="detail-tag is-off">该商品暂无额外服务承诺</span>
        </div>

        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span class="detail-meta-key">库存</span>
            <span class="detail-meta-val">{{ product.stock }} 件</span>
          </div>
          <div class="detail-meta-item">
            <span class="detail-meta-key">累计销量</span>
            <span class="detail-meta-val">{{ product.sales || 0 }} 件</span>
          </div>
          <div class="detail-meta-item">
            <span class="detail-meta-key">商品状态</span>
            <span class="detail-meta-val" :style="{ color: onSale ? 'var(--success)' : 'var(--ink-3)' }">
              {{ onSale ? '在售' : '已下架' }}
            </span>
          </div>
          <div class="detail-meta-item">
            <span class="detail-meta-key">商品评分</span>
            <span class="detail-meta-val">
              {{ summary.count ? `${summary.avgRating} 分（${summary.count} 条）` : '暂无评价' }}
            </span>
          </div>
        </div>

        <div class="detail-qty-row">
          <span class="label">数量</span>
          <div class="qty-stepper">
            <button type="button" :disabled="qty <= 1" @click="decQty">−</button>
            <input v-model.number="qty" type="number" min="1" :max="maxQty" @blur="clampQty" />
            <button type="button" :disabled="qty >= maxQty" @click="incQty">+</button>
          </div>
          <span class="text-xs text-muted">库存 {{ product.stock }} 件</span>
        </div>

        <div class="detail-buy">
          <button class="btn-grad" :disabled="!canBuy || buyLoading" @click="onBuyNow">
            <el-icon><ShoppingCart /></el-icon>
            {{ shopClosed ? '店铺休息中' : soldOut ? '暂时缺货' : (onSale ? '立即购买' : '已下架') }}
          </button>
          <button class="btn-ghost" :disabled="!canBuy || cartLoading" @click="onAddCart">加入购物车</button>
          <button class="detail-fav" :class="{ 'is-on': favOn }" @click="toggleFav">
            <el-icon><Star /></el-icon>{{ favOn ? '已收藏' : '收藏' }}
          </button>
          <button class="detail-fav" :class="{ 'is-on': compareOn }" @click="toggleCompare">
            <el-icon><Switch /></el-icon>{{ compareOn ? '已对比' : '对比' }}
          </button>
          <button class="detail-fav" @click="onService">
            <el-icon><Service /></el-icon>联系客服
          </button>
          <button class="detail-fav" @click="openShare">
            <el-icon><Share /></el-icon>分享
          </button>
          <button class="detail-fav" @click="openReport">
            <el-icon><Warning /></el-icon>举报
          </button>
        </div>
      </div>
    </div>

    <!-- 详情 / 评价 -->
    <div class="detail-tabs">
      <div class="tab-bar">
        <button class="tab-item" :class="{ 'is-active': tab === 'detail' }" @click="tab = 'detail'">商品详情</button>
        <button class="tab-item" :class="{ 'is-active': tab === 'review' }" @click="tab = 'review'">
          商品评价（{{ summary.count || 0 }}）
        </button>
      </div>

      <div v-show="tab === 'detail'" class="prose">
        <p style="margin-bottom:14px">{{ product.description || '该商品暂无详细描述。' }}</p>
        <ul style="display:grid;gap:10px">
          <li>· 商品编号：{{ product.id }} ｜ 所属分类：{{ categoryName || '未分类' }}</li>
          <li>· 下单后库存即时锁定；待支付订单超过 30 分钟将自动取消并释放库存。</li>
          <li v-if="hasPromise">· {{ freeShipping ? '本商品包邮；' : '' }}{{ shipHours > 0 ? shipHours + ' 小时内发出；' : '' }}{{ returnDays > 0 ? '支持 ' + returnDays + ' 天无理由退换。' : '' }}</li>
        </ul>
      </div>

      <div v-show="tab === 'review'">
        <div class="review-summary">
          <div class="review-score">
            <b>{{ summary.avgRating || 0 }}</b>
            <span>综合评分 · {{ summary.count || 0 }} 条评价</span>
          </div>
          <div class="review-bars">
            <div v-for="s in [5,4,3,2,1]" :key="s" class="review-bar">
              <span style="width:32px">{{ s }} 星</span>
              <span class="rb-track">
                <span
                  class="rb-fill"
                  :style="{ width: (summary.count ? ((summary.distribution?.[s] || 0) / summary.count * 100) : 0) + '%' }"
                />
              </span>
              <span style="width:28px;text-align:right">{{ summary.distribution?.[s] || 0 }}</span>
            </div>
          </div>
        </div>

        <div v-if="!reviews.length" class="empty" style="padding:48px">
          <div class="empty-icon"><el-icon><ChatLineSquare /></el-icon></div>
          <div class="empty-title">还没有评价</div>
          <p class="empty-sub">确认收货后即可发表你的第一条评价。</p>
        </div>

        <div v-else class="review-list">
          <div v-for="r in reviews" :key="r.id" class="review-item">
            <div class="review-head">
              <span class="review-avatar">{{ (r.username || 'U')[0].toUpperCase() }}</span>
              <div>
                <div class="review-user">
                  {{ r.username }}
                  <span v-if="r.anonymous" class="anon-tag">匿名</span>
                </div>
                <div style="color:var(--promo);font-size:12px;letter-spacing:.12em">{{ stars(r.rating) }}</div>
              </div>
              <span class="review-time">
                {{ fmtDate(r.createdAt) }}
                <template v-if="r.updatedAt && fmtDate(r.updatedAt) !== fmtDate(r.createdAt)">
                  · 修改于 {{ fmtDate(r.updatedAt) }}
                </template>
              </span>
            </div>
            <div class="review-content">{{ r.content || '（该用户未填写评价内容）' }}</div>
            <div v-for="a in r.appends || []" :key="a.id" class="review-append">
              <span class="review-append-tag">追评</span>
              <span class="review-append-text">{{ a.content }}</span>
              <span class="review-append-time">{{ fmtDate(a.createdAt) }}</span>
            </div>
          </div>
        </div>

        <div class="review-form">
          <h3 style="font-size:16px;margin-bottom:14px">发表我的评价</h3>
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px">
            <span class="text-sm text-2">评分</span>
            <el-rate v-model="myRating" :colors="['#ff6a00','#ff6a00','#ff6a00']" />
          </div>
          <el-input
            v-model="myContent"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说说这件商品的使用感受吧（确认收货后才可评价）"
          />
          <div class="review-anon">
            <el-checkbox v-model="myAnonymous">匿名评价</el-checkbox>
            <span class="review-anon-tip">开启后商品页只显示「匿名用户」，你仍可在「我的评价」中看到自己的记录</span>
          </div>
          <button class="btn-grad" style="margin-top:14px" :disabled="reviewLoading" @click="submitReview">
            提交评价
          </button>
        </div>
      </div>
    </div>

    <!-- 看了又看 -->
    <section v-if="recos.length" class="detail-reco">
      <div class="reco-head">
        <h2 class="reco-title">看了又看</h2>
        <span class="reco-sub">同分类下的人气商品</span>
      </div>
      <div class="product-grid">
        <ProductCard
          v-for="p in recos"
          :key="'r' + p.id"
          :product="p"
          :favIds="favIds"
          @add="addRecoToCart"
          @toggle-fav="toggleFav"
        />
      </div>
    </section>
  </div>

  <div v-else-if="loadError" class="empty" style="padding:80px 20px">
    <div class="empty-title">{{ loadError }}</div>
    <p class="empty-sub">商品可能已下架或被删除，去看看其它商品吧。</p>
    <el-button type="primary" style="margin-top:16px" @click="$router.push('/')">返回首页</el-button>
  </div>

  <ShareDialog
    v-model="shareVisible"
    type="PRODUCT"
    :target-id="product?.id"
    :title="product?.name"
  />
  <ReportDialog
    v-model="reportVisible"
    target-type="PRODUCT"
    :target-id="product?.id"
    :target-title="product?.name"
  />
</template>

<style scoped>
.anon-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 4px;
  background: var(--surface-3);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 400;
  vertical-align: 1px;
}
.review-anon {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.review-anon-tip { font-size: 12px; color: var(--text-3); }
.detail-shop { display: flex; align-items: center; gap: 10px; margin: 8px 0 12px; flex-wrap: wrap; }
.shop-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: .04em;
  color: var(--ink-2);
  background: #fff;
  text-decoration: none;
  transition: border-color .2s, color .2s;
}
.shop-pill::before { content: '店'; font-size: 11px; color: var(--brand); }
.shop-pill:hover { border-color: var(--brand); color: var(--brand); }
.shop-visit {
  font-size: 12.5px;
  color: var(--text-3);
  text-decoration: none;
  transition: color .2s;
}
.shop-visit:hover { color: var(--brand); }
.detail-service--media { margin-top: 14px; padding-top: 0; border-top: none; justify-content: center; gap: 8px 14px; }
.detail-reco { margin-top: 28px; }
.reco-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.reco-title { font-family: var(--font-display); font-size: 21px; font-weight: 700; letter-spacing: .04em; }
.reco-sub { font-size: 12px; color: var(--ink-3); }
.detail-media .img-box img { transition: transform .5s cubic-bezier(.2,.7,.3,1); }
.detail-media .img-box:hover img { transform: scale(1.04); }
</style>
