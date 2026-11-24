<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete, ShoppingCart } from '@element-plus/icons-vue'
import { cartApi, productApi } from '@/api'
import { useProductActions } from '@/composables/useProductActions'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const items = ref([])
const loading = ref(false)
const guess = ref([])
const favIds = ref([])
const { loadFavIds, addToCart, toggleFav } = useProductActions(favIds)

const selectedRows = computed(() => items.value.filter(r => r.selected === 1))
const allChecked = computed(() => items.value.length > 0 && selectedRows.value.length === items.value.length)
const anySelected = computed(() => selectedRows.value.length > 0)
const selectedCount = computed(() => selectedRows.value.reduce((s, r) => s + Number(r.quantity || 0), 0))
const selectedTotal = computed(() => selectedRows.value.reduce((s, r) => s + Number(r.subtotal || 0), 0))

// 多商家：购物车按店铺分组，结算时每个店铺生成一笔独立订单
const shopGroups = computed(() => {
  const map = new Map()
  for (const row of items.value) {
    const key = row.shopId ?? 0
    if (!map.has(key)) {
      map.set(key, { shopId: key, shopName: row.shopName || '店铺', items: [], subtotal: 0 })
    }
    const g = map.get(key)
    g.items.push(row)
    g.subtotal += Number(row.subtotal || 0)
  }
  return [...map.values()]
})
const selectedShopCount = computed(() => new Set(selectedRows.value.map(r => r.shopId ?? 0)).size)

const isShopAll = g => g.items.length > 0 && g.items.every(r => r.selected === 1)

const toggleShop = async (g, v) => {
  const targets = g.items.filter(r => (r.selected === 1) !== v)
  if (!targets.length) return
  await Promise.all(targets.map(r => cartApi.updateSelected({ cartId: r.id, selected: v ? 1 : 0 })))
  await load()
}

const load = async () => {
  loading.value = true
  try {
    const r = await cartApi.list()
    items.value = r.data || []
  } finally { loading.value = false }
}

const toggleAll = async (v) => {
  const targets = items.value.filter(row => (row.selected === 1) !== v)
  if (!targets.length) return
  await Promise.all(targets.map(row => cartApi.updateSelected({ cartId: row.id, selected: v ? 1 : 0 })))
  await load()
}

const onToggle = async (row, v) => {
  await cartApi.updateSelected({ cartId: row.id, selected: v ? 1 : 0 })
  await load()
}

const updateQty = async (row, v) => {
  try {
    await cartApi.updateQty({ cartId: row.id, quantity: v })
    await load()
    // 顶栏购物车件数由 MainLayout 独立维护，这里通知它同步
    window.dispatchEvent(new Event('cart:refresh'))
  } catch (e) { ElMessage.error(e.message || '更新失败') }
}

const onDelete = async (row) => {
  await cartApi.delete(row.id)
  ElMessage.success('已移出购物车')
  await load()
}

const goCheckout = () => router.push('/checkout')

const loadGuess = async () => {
  try {
    const r = await productApi.hot({ limit: 8 })
    const inCart = new Set(items.value.map(r2 => r2.productId))
    guess.value = (r.data || []).filter(p => !inCart.has(p.id)).slice(0, 4)
  } catch (e) { guess.value = [] }
}

onMounted(async () => {
  await load()
  await loadFavIds()
  await loadGuess()
})
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">我的购物车</h2>
        <div class="section-sub">共 {{ items.length }} 件商品，勾选需要结算的商品后再下单</div>
      </div>
      <el-button v-if="items.length" text @click="$router.push('/')">继续逛逛 →</el-button>
    </div>

    <div v-if="loading" class="cart-panel">
      <div v-for="i in 3" :key="i" class="cart-row sk-row" />
    </div>

    <div v-else-if="!items.length" class="empty">
      <div class="empty-icon">购</div>
      <div class="empty-title">购物车还是空的</div>
      <p class="empty-sub">去挑几件喜欢的商品，加入购物车后可随时结算。</p>
      <el-button class="mt-16" type="primary" @click="router.push('/')">去逛逛</el-button>
    </div>

    <div v-else class="cart-layout">
      <div class="cart-panel">
        <div class="cart-panel-head">
          <el-checkbox :model-value="allChecked" @change="v => toggleAll(v)">全选</el-checkbox>
          <span class="text-sm text-muted">
            已选 {{ selectedRows.length }} / {{ items.length }} 件 · 来自 {{ shopGroups.length }} 家店铺
          </span>
        </div>

        <div v-for="g in shopGroups" :key="`shop-${g.shopId}`" class="cart-shop">
          <div class="cart-shop-head">
            <el-checkbox :model-value="isShopAll(g)" @change="v => toggleShop(g, v)" />
            <router-link
              v-if="g.shopId"
              class="cart-shop-name cart-shop-link"
              :to="{ name: 'shop', params: { id: g.shopId } }"
              :title="`进入 ${g.shopName} 的店铺页面`"
            >{{ g.shopName }}<span class="cart-shop-arrow">进店 ›</span></router-link>
            <span v-else class="cart-shop-name">{{ g.shopName }}</span>
            <span class="text-sm text-muted">{{ g.items.length }} 件 · 小计 ¥{{ g.subtotal.toFixed(2) }}</span>
          </div>

          <div
            v-for="row in g.items"
            :key="row.id"
            class="cart-row"
            :class="{ 'is-off': row.selected !== 1 }"
          >
          <el-checkbox :model-value="row.selected === 1" @change="v => onToggle(row, v)" />

          <div class="cart-thumb">
            <img
              v-if="row.productCover"
              :src="row.productCover"
              :alt="row.productName"
              loading="lazy"
            />
            <div v-else class="img-ph">{{ (row.productName || '?')[0] }}</div>
          </div>

          <div class="cart-info">
            <div class="cart-tags"><span class="mini-tag">包邮</span><span class="mini-tag">7 天无理由</span></div>
            <div class="cart-name" @click="router.push(`/product/${row.productId}`)">{{ row.productName }}</div>
            <div class="cart-meta">
              单价 ¥{{ Number(row.price).toLocaleString() }} · 库存 {{ row.stock }}
              <span v-if="row.stock <= 0" class="cart-warn">（已售罄，请取消勾选后再结算）</span>
            </div>
          </div>

          <el-input-number
            :model-value="row.quantity"
            :min="1"
            :max="Math.max(1, row.stock)"
            size="default"
            @change="v => updateQty(row, v)"
          />

          <div class="cart-sub price"><span class="cur">¥</span>{{ row.subtotal }}</div>

          <button class="cart-del" title="移出购物车" @click="onDelete(row)">
            <el-icon><Delete /></el-icon>
          </button>
          </div>
        </div>
      </div>

      <div class="cart-summary-card">
        <h3 style="font-size:16px;margin-bottom:18px">结算信息</h3>
        <div class="summary-row">
          <span>已选商品</span>
          <span class="fw-600">{{ selectedCount }} 件</span>
        </div>
        <div class="summary-row">
          <span>涉及店铺</span>
          <span class="fw-600">{{ selectedShopCount }} 家</span>
        </div>
        <div class="summary-row">
          <span>商品小计</span>
          <span class="price price-sm"><span class="cur">¥</span>{{ selectedTotal.toFixed(2) }}</span>
        </div>
        <div class="summary-row">
          <span>运费</span>
          <span class="fw-600" style="color:var(--success)">免运费</span>
        </div>
        <div class="summary-row total">
          <span>应付总额</span>
          <span class="price summary-total"><span class="cur">¥</span>{{ selectedTotal.toFixed(2) }}</span>
        </div>

        <button class="btn-grad" :disabled="!anySelected" @click="goCheckout">
          <el-icon><ShoppingCart /></el-icon>
          去结算（{{ selectedRows.length }}）
        </button>
        <div class="cart-note">
          支持模拟支付 · 30 分钟内未支付将自动取消
          <template v-if="selectedShopCount > 1">
            <br />跨店结算会按店铺生成 {{ selectedShopCount }} 笔订单，支持一次合并支付、各店分别发货
          </template>
        </div>
      </div>
    </div>

    <!-- 猜你喜欢 -->
    <section v-if="guess.length" class="cart-guess">
      <div class="cart-guess-head">
        <h2 class="cart-guess-title">买过的人也在看</h2>
        <span class="text-xs text-muted">根据热销数据推荐</span>
      </div>
      <div class="product-grid">
        <ProductCard
          v-for="p in guess"
          :key="'g' + p.id"
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
.cart-shop { border-top: 1px solid var(--line); }
.cart-shop:first-of-type { border-top: none; }
.cart-shop-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px 4px;
  background: var(--bg-soft, #fbfaf8);
}
.cart-shop-name {
  font-weight: 600;
  font-size: 14px;
  letter-spacing: .02em;
}
.cart-shop-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-1);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: color .2s, border-color .2s;
}
.cart-shop-link:hover { color: var(--brand); border-bottom-color: var(--brand); }
.cart-shop-arrow { font-size: 12px; font-weight: 400; color: var(--text-3); }
.cart-shop-link:hover .cart-shop-arrow { color: var(--brand); }
.cart-tags { display: flex; gap: 6px; margin-bottom: 4px; }
.cart-warn { color: var(--promo); }
.sk-row { height: 120px; }
.sk-row::after {
  content: '';
  position: absolute;
  inset: 18px 20px;
  border-radius: var(--r-sm);
  background: linear-gradient(100deg, #f3f3f3 30%, #fafafa 45%, #f3f3f3 60%);
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}
.cart-panel { position: relative; }
.cart-guess { margin-top: 34px; }
.cart-guess-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.cart-guess-title { font-family: var(--font-display); font-size: 21px; font-weight: 700; letter-spacing: .04em; }
@keyframes shimmer { from { background-position: 150% 0; } to { background-position: -50% 0; } }
</style>
