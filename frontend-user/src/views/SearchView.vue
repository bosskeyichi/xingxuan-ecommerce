<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, ChatDotRound } from '@element-plus/icons-vue'
import { productApi, friendApi } from '@/api'
import { HOT_KEYWORDS } from '@/utils/searchHistory'
import { useProductActions } from '@/composables/useProductActions'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref(route.query.keyword || '')
const products = ref([])
const favIds = ref([])
const { loadFavIds, addToCart, toggleFav } = useProductActions(favIds)
const loading = ref(false)
const total = ref(0)
const sort = ref('default')
const inStock = ref(false)
const minPrice = ref('')
const maxPrice = ref('')
const page = reactive({ current: 1, size: 12 })

/* ---------- 用户搜索：关键词同时匹配昵称 / 用户名 / 用户 ID ---------- */
const users = ref([])
const usersLoading = ref(false)
const userInitial = u => (u.nickname || u.username || '?').slice(0, 1).toUpperCase()

const loadUsers = async () => {
  if (!userStore.isLogin || !keyword.value.trim()) { users.value = []; return }
  usersLoading.value = true
  try {
    const r = await friendApi.search({ keyword: keyword.value.trim(), current: 1, size: 6 })
    users.value = r.data?.records || []
  } catch (e) {
    users.value = []
  } finally {
    usersLoading.value = false
  }
}

const addUserFriend = async u => {
  try {
    await friendApi.add({ userId: u.id })
    ElMessage.success(`已向 ${u.nickname} 发送好友申请，等待对方同意`)
    u.pending = true
  } catch (e) { /* 统一提示 */ }
}

const chatWithUser = u => {
  router.push({
    path: '/messages',
    query: { peerId: u.id, scene: 'FRIEND', peerName: u.nickname || u.username }
  })
}

const SORT_OPTIONS = [
  { label: '综合', value: 'default' },
  { label: '销量', value: 'sales_desc' },
  { label: '价格 ↑', value: 'price_asc' },
  { label: '价格 ↓', value: 'price_desc' },
  { label: '新品', value: 'newest' }
]
const hotWords = HOT_KEYWORDS.slice(0, 8)

const load = async () => {
  if (!keyword.value) { products.value = []; total.value = 0; return }
  loading.value = true
  try {
    const r = await productApi.search({
      keyword: keyword.value,
      current: page.current,
      size: page.size,
      sort: sort.value === 'default' ? undefined : sort.value,
      inStock: inStock.value || undefined,
      minPrice: minPrice.value ? Number(minPrice.value) : undefined,
      maxPrice: maxPrice.value ? Number(maxPrice.value) : undefined
    })
    products.value = r.data.records || []
    total.value = r.data.total || 0
  } catch (e) {
    products.value = []
    total.value = 0
  } finally { loading.value = false }
}

const reload = () => { page.current = 1; load() }
const resetFilter = () => {
  sort.value = 'default'
  inStock.value = false
  minPrice.value = ''
  maxPrice.value = ''
  reload()
}

const filtered = computed(() => sort.value !== 'default' || inStock.value || minPrice.value || maxPrice.value)

// 后端是四级渐进匹配：名称/别名 → 去修饰词 → 分类名 → 描述。
// 结果里没有一个商品的「名称或别名」包含完整关键词时，说明这次是放宽匹配的，要说明原因
const nameMissed = computed(() => {
  if (!keyword.value || !products.value.length) return false
  const kw = keyword.value
  return !products.value.some(p => `${p.name || ''}${p.keywords || ''}`.includes(kw))
})

watch(() => route.query.keyword, v => {
  keyword.value = v || ''
  page.current = 1
  load()
  loadUsers()
})
onMounted(async () => { await loadFavIds(); await load(); await loadUsers() })
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">搜索结果</h2>
        <div class="section-sub">
          <template v-if="keyword">关键词「{{ keyword }}」· 共 {{ total }} 件商品</template>
          <template v-else>输入关键词开始搜索</template>
        </div>
      </div>
      <el-button text @click="router.push('/')">全部商品 →</el-button>
    </div>

    <div v-if="nameMissed" class="search-tip">
      没有精确匹配「{{ keyword }}」的商品，以下是按名称 / 别名 / 分类放宽匹配的结果
    </div>

    <!-- 排序 / 筛选 -->
    <div class="filter-bar">
      <div class="fb-group">
        <span class="fb-label">排序</span>
        <div class="sort-tabs">
          <button
            v-for="o in SORT_OPTIONS"
            :key="o.value"
            class="sort-item"
            :class="{ 'is-active': sort === o.value }"
            @click="sort = o.value; reload()"
          >{{ o.label }}</button>
        </div>
      </div>
      <div class="fb-group">
        <label class="rail-check">
          <input v-model="inStock" type="checkbox" @change="reload" />
          仅看有货
        </label>
      </div>
      <div class="fb-group fb-price">
        <span class="fb-label">价格</span>
        <div class="price-range">
          <input v-model="minPrice" type="number" placeholder="最低" @keyup.enter="reload" />
          <span class="dash">-</span>
          <input v-model="maxPrice" type="number" placeholder="最高" @keyup.enter="reload" />
        </div>
        <button class="price-apply" @click="reload">确定</button>
        <button v-if="filtered" class="price-clear" @click="resetFilter">重置</button>
      </div>
      <el-select :model-value="page.size" class="tb-size" @change="v => { page.size = v; reload() }">
        <el-option :value="12" label="12 件/页" />
        <el-option :value="24" label="24 件/页" />
        <el-option :value="36" label="36 件/页" />
      </el-select>
    </div>

    <div v-if="loading" class="product-grid">
      <div v-for="i in 8" :key="i" class="sk" />
    </div>

    <div v-else-if="!products.length" class="empty">
      <div class="empty-icon">搜</div>
      <div class="empty-title">没有找到「{{ keyword }}」相关的商品</div>
      <p class="empty-sub">
        换个关键词试试<template v-if="inStock">，或取消「仅看有货」</template>，
        也可以看看这些热搜词：
      </p>
      <div class="hot-tags">
        <router-link v-for="w in hotWords" :key="w" class="hot-tag" :to="{ path: '/search', query: { keyword: w } }">{{ w }}</router-link>
      </div>
      <el-button class="mt-16" @click="router.push('/')">浏览全部商品</el-button>
    </div>

    <template v-else>
      <div class="product-grid">
        <ProductCard
          v-for="p in products"
          :key="p.id"
          :product="p"
          :favIds="favIds"
          @add="addToCart"
          @toggle-fav="toggleFav"
        />
      </div>
      <el-pagination
        v-if="total > page.size"
        layout="prev, pager, next"
        :total="total"
        :page-size="page.size"
        :current-page="page.current"
        background
        @current-change="c => { page.current = c; load() }"
      />
    </template>

    <!-- 用户搜索：同一关键词下匹配到的用户 -->
    <div v-if="userStore.isLogin && keyword.trim()" class="user-result">
      <div class="user-result-head">
        <div class="user-result-title">
          <el-icon><UserFilled /></el-icon> 找到的用户
          <span class="user-result-sub">{{ users.length ? `共 ${users.length} 位` : '' }}</span>
        </div>
        <el-button text size="small" @click="router.push({ path: '/friends', query: { keyword } })">
          去好友页查看全部 →
        </el-button>
      </div>

      <div v-loading="usersLoading">
        <div v-if="!users.length" class="user-result-empty">
          没有匹配「{{ keyword }}」的用户，可尝试对方的昵称 / 用户名 / 用户 ID
        </div>
        <div v-else class="user-result-grid">
          <div v-for="u in users" :key="u.id" class="user-item">
            <el-avatar
              :size="42"
              class="user-item-avatar"
              title="查看主页"
              :src="u.avatar || undefined"
              @click="router.push(`/user/${u.id}`)"
            >{{ userInitial(u) }}</el-avatar>
            <div class="user-item-main">
              <div class="user-item-name">
                {{ u.nickname }}
                <span v-if="u.friend" class="user-item-friend">已是好友</span>
                <span v-else-if="u.pending" class="user-item-pending">已申请</span>
              </div>
              <div class="user-item-meta">@{{ u.username }} · ID {{ u.id }}</div>
            </div>
            <div class="user-item-actions">
              <el-button v-if="u.friend" size="small" @click="chatWithUser(u)">
                <el-icon><ChatDotRound /></el-icon> 发消息
              </el-button>
              <el-button v-else-if="u.pending" size="small" disabled>已申请</el-button>
              <el-button
                v-else
                size="small"
                type="primary"
                :disabled="!u.addable"
                @click="addUserFriend(u)"
              >{{ u.addable ? '申请好友' : '不可添加' }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-tip {
  margin: -6px 0 14px;
  padding: 9px 14px;
  border: 1px solid #ffe0b8;
  border-radius: var(--r-sm);
  background: #fff8ec;
  color: #a8620c;
  font-size: 12.5px;
}
.tb-size { width: 112px; }
.tb-size :deep(.el-select__wrapper) { min-height: 32px; font-size: 12.5px; }
.hot-tags { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-top: 14px; }
.hot-tag {
  padding: 5px 14px;
  border: 1px solid #ffd4d0;
  border-radius: var(--r-full);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12.5px;
}
.hot-tag:hover { background: var(--brand); color: #fff; }
.sk {
  height: 300px;
  border-radius: var(--r-md);
  background: linear-gradient(100deg, #f3f3f3 30%, #fafafa 45%, #f3f3f3 60%);
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}
@keyframes shimmer { from { background-position: 150% 0; } to { background-position: -50% 0; } }

/* ---------- 用户搜索结果 ---------- */
.user-result {
  margin-top: 26px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface);
}
.user-result-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.user-result-title { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 14.5px; color: var(--text-1); }
.user-result-sub { font-size: 12px; font-weight: 400; color: var(--text-3); }
.user-result-empty { padding: 18px 0; text-align: center; font-size: 13px; color: var(--text-3); }
.user-result-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 10px; }
.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
}
.user-item-avatar { cursor: pointer; flex: none; }
.user-item-main { flex: 1; min-width: 0; }
.user-item-name { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 13.5px; color: var(--text-1); }
.user-item-friend {
  font-size: 11px;
  font-weight: 500;
  color: #2f9e5f;
  border: 1px solid color-mix(in srgb, #2f9e5f 35%, transparent);
  border-radius: 999px;
  padding: 1px 7px;
}
.user-item-pending {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-3);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 1px 7px;
}
.user-item-meta { margin-top: 3px; font-size: 12px; color: var(--text-3); }
.user-item-actions { flex: none; }
</style>
