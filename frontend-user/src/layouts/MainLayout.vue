<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, ShoppingCart, ArrowDown, ArrowUp, Menu as MenuIcon,
  User, List, Star, Clock, Ticket, SwitchButton,
  ChatDotRound, UserFilled, ChatLineSquare, Setting, Warning, Shop, Monitor
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { categoryApi, cartApi, messageApi } from '../api'
import { HOT_KEYWORDS, searchHistory } from '../utils/searchHistory'
import CompareBar from '../components/CompareBar.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const keyword = ref('')
const showHistory = ref(false)
const history = ref([])
const categories = ref([])
const catOpen = ref(false)
const userMenu = ref(false)
const cartCount = ref(0)
const unreadCount = ref(0)
const scrolled = ref(false)

const isLogin = computed(() => userStore.isLogin)
const user = computed(() => userStore.userInfo || {})
// 管理员 / 商家也能登录商城端，给一个回后台的入口
const isStaff = computed(() => ['ADMIN', 'MERCHANT'].includes(user.value.role))
const ADMIN_URL = import.meta.env.VITE_ADMIN_URL || 'http://localhost:5174'
const openAdmin = () => {
  userMenu.value = false
  window.open(ADMIN_URL, '_blank')
}
const initial = computed(() => {
  const n = user.value.nickname || user.value.username || '星'
  return n.trim().charAt(0).toUpperCase()
})
const hotWords = computed(() => HOT_KEYWORDS.slice(0, 6))

watch(() => route.fullPath, () => {
  keyword.value = route.path === '/search' ? (route.query.keyword || '') : ''
  showHistory.value = false
  userMenu.value = false
  catOpen.value = false
})

const syncKeyword = () => {
  if (route.path === '/search') keyword.value = route.query.keyword || ''
  else keyword.value = ''
}

const doSearch = word => {
  const kw = (typeof word === 'string' ? word : keyword.value || '').trim()
  if (!kw) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  keyword.value = kw
  searchHistory.push(kw)
  history.value = searchHistory.list()
  showHistory.value = false
  router.push({ path: '/search', query: { keyword: kw } })
}

const pickKeyword = word => doSearch(word)

// 顶栏搜索框也能找人：跳到搜索页，商品结果下方直接展示匹配的用户
const searchUser = () => {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入昵称、用户名或用户 ID')
    return
  }
  showHistory.value = false
  router.push({ path: '/search', query: { keyword: kw } })
}

// 顶栏搜索框也能找店：跳到店铺列表页并按关键词搜索
const searchShop = () => {
  const kw = keyword.value.trim()
  showHistory.value = false
  router.push({ path: '/shops', query: kw ? { keyword: kw } : {} })
}

const clearSearchHistory = () => {
  searchHistory.clear()
  history.value = []
}

const refreshCartCount = async () => {
  if (!userStore.isLogin) { cartCount.value = 0; return }
  try {
    const r = await cartApi.list()
    const list = r.data || []
    cartCount.value = list.reduce((sum, i) => sum + (i.quantity || 0), 0)
  } catch { cartCount.value = 0 }
}

const refreshUnread = async () => {
  if (!userStore.isLogin) { unreadCount.value = 0; return }
  try {
    const r = await messageApi.unread()
    unreadCount.value = r.data?.total || 0
  } catch {
    unreadCount.value = 0
  }
}

const loadCategories = async () => {
  try {
    const r = await categoryApi.list()
    categories.value = r.data || []
  } catch { categories.value = [] }
}

const gotoCategory = id => {
  catOpen.value = false
  router.push({ path: '/', query: { categoryId: id } })
}

const handleLogout = () => {
  userStore.logout()
  cartCount.value = 0
  userMenu.value = false
  ElMessage.success('已退出登录')
  router.push('/login')
}

const go = path => { userMenu.value = false; router.push(path) }

const closeOnClickOutside = e => {
  if (!e.target.closest('.user-menu')) userMenu.value = false
  if (!e.target.closest('.cat-entry')) catOpen.value = false
  if (!e.target.closest('.search-wrap')) showHistory.value = false
}
const onScroll = () => { scrolled.value = window.scrollY > 420 }
const scrollTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
const onCartSync = () => refreshCartCount()
const onUnreadSync = () => refreshUnread()
let unreadTimer = null

onMounted(() => {
  syncKeyword()
  history.value = searchHistory.list()
  loadCategories()
  refreshCartCount()
  refreshUnread()
  unreadTimer = setInterval(refreshUnread, 30000)
  window.addEventListener('click', closeOnClickOutside)
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('cart:refresh', onCartSync)
  window.addEventListener('message:refresh', onUnreadSync)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeOnClickOutside)
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('cart:refresh', onCartSync)
  window.removeEventListener('message:refresh', onUnreadSync)
  if (unreadTimer) clearInterval(unreadTimer)
})
</script>

<template>
  <div class="app-root">
    <!-- 服务承诺通告条 -->
    <div class="promo-bar">
      <div class="promo-inner">
        <div class="promo-promises">
          <span>逐件验货</span><i class="dot" />
          <span>一次只推 100 件</span><i class="dot" />
          <span>48 小时内发货</span><i class="dot" />
          <span>7 天无理由退换</span>
        </div>
        <div class="promo-links">
          <template v-if="isLogin">
            <span class="promo-guest">你好，{{ user.nickname || user.username }}</span>
          </template>
          <template v-else>
            <router-link to="/login">登录</router-link>
            <span class="sep">|</span>
            <router-link to="/register">注册</router-link>
          </template>
        </div>
      </div>
    </div>

    <!-- 主顶栏：品牌 / 全部分类 / 搜索 / 购物车 / 账户 -->
    <header class="site-header">
      <div class="header-inner">
        <div class="brand" @click="router.push('/')">
          <div class="brand-mark">星</div>
          <div class="brand-text">
            <span class="brand-name">星选商城</span>
            <span class="brand-sub">Curated Store</span>
          </div>
        </div>

        <div class="cat-entry" @mouseenter="catOpen = true" @mouseleave="catOpen = false">
          <button class="cat-trigger" type="button" @click="catOpen = !catOpen">
            <el-icon><MenuIcon /></el-icon>
            全部分类
            <el-icon :class="['user-caret', { 'is-open': catOpen }]"><ArrowDown /></el-icon>
          </button>
          <transition name="ud-fade">
            <div v-if="catOpen" class="cat-panel">
              <a v-for="c in categories" :key="c.id" class="cat-panel-item" @click="gotoCategory(c.id)">
                {{ c.name }}
              </a>
              <router-link to="/categories" class="cat-panel-all" @click="catOpen = false">
                查看全部分类 →
              </router-link>
            </div>
          </transition>
        </div>

        <div class="search-wrap">
          <el-input
            v-model="keyword"
            placeholder="搜索商品 / 品牌，如 手机、耳机、图书"
            clearable
            @focus="showHistory = true; history = searchHistory.list()"
            @keyup.enter="doSearch()"
          >
            <template #append>
              <el-button @click="doSearch()">
                <el-icon><Search /></el-icon>
              </el-button>
            </template>
          </el-input>

          <div class="hot-line">
            <span class="hot-label">热搜</span>
            <a v-for="w in hotWords" :key="w" @click="pickKeyword(w)">{{ w }}</a>
          </div>

          <transition name="ud-fade">
            <div v-if="showHistory" class="search-hist">
              <div v-if="history.length" class="sh-block">
                <div class="sh-head">
                  <span>搜索历史</span>
                  <span class="sh-clear" @click="clearSearchHistory">清除</span>
                </div>
                <div class="sh-tags">
                  <span v-for="h in history" :key="h" class="sh-tag" @click="pickKeyword(h)">{{ h }}</span>
                </div>
              </div>
              <div class="sh-block">
                <div class="sh-head"><span>热门搜索</span></div>
                <div class="sh-tags">
                  <span v-for="w in hotWords" :key="w" class="sh-tag hot" @click="pickKeyword(w)">{{ w }}</span>
                </div>
              </div>
              <div class="sh-block">
                <div class="sh-head">
                  <span>找用户 / 找店铺</span>
                  <span class="sh-clear" @click="searchUser">去好友页 →</span>
                </div>
                <div class="sh-tags">
                  <span class="sh-tag" @click="searchUser">
                    搜用户「{{ keyword || '昵称 / 用户名 / ID' }}」
                  </span>
                  <span class="sh-tag" @click="searchShop">
                    搜店铺「{{ keyword || '店铺名称 / 简介' }}」
                  </span>
                </div>
              </div>
            </div>
          </transition>
        </div>

        <div class="header-actions">
          <router-link v-if="isLogin" to="/messages" class="cart-btn">
            <el-icon :size="17"><ChatDotRound /></el-icon>
            消息
            <span v-if="unreadCount" class="cart-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>

          <router-link to="/cart" class="cart-btn">
            <el-icon :size="17"><ShoppingCart /></el-icon>
            购物车
            <span v-if="cartCount" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
          </router-link>

          <div v-if="isLogin" class="user-menu">
            <button class="user-chip" type="button" @click.stop="userMenu = !userMenu">
              <img v-if="user.avatar" :src="user.avatar" class="user-avatar-img" alt="头像" />
              <span v-else class="user-avatar">{{ initial }}</span>
              <span class="user-name">{{ user.nickname || user.username }}</span>
              <el-icon :class="['user-caret', { 'is-open': userMenu }]"><ArrowDown /></el-icon>
            </button>
            <transition name="ud-fade">
              <div v-if="userMenu" class="user-dropdown">
                <div class="ud-head">
                  <img v-if="user.avatar" :src="user.avatar" class="ud-avatar" alt="头像" />
                  <div v-else class="ud-avatar ud-avatar-text">{{ initial }}</div>
                  <div class="ud-head-info">
                    <div class="ud-name">{{ user.nickname || user.username }}</div>
                    <div class="ud-handle">@{{ user.username }}</div>
                  </div>
                </div>
                <button class="ud-item" @click="go('/profile')"><el-icon><User /></el-icon>个人中心</button>
                <button class="ud-item" @click="go('/orders')"><el-icon><List /></el-icon>我的订单</button>
                <button class="ud-item" @click="go('/favorites')"><el-icon><Star /></el-icon>我的收藏</button>
                <button class="ud-item" @click="go('/footprints')"><el-icon><Clock /></el-icon>浏览足迹</button>
                <button class="ud-item" @click="go('/coupons')"><el-icon><Ticket /></el-icon>优惠券中心</button>
                <button class="ud-item" @click="go('/messages')">
                  <el-icon><ChatDotRound /></el-icon>消息中心
                  <span v-if="unreadCount" class="ud-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
                </button>
                <button class="ud-item" @click="go('/friends')"><el-icon><UserFilled /></el-icon>我的好友</button>
                <button class="ud-item" @click="go('/my-reviews')"><el-icon><ChatLineSquare /></el-icon>我的评价</button>
                <button class="ud-item" @click="go('/my-reports')"><el-icon><Warning /></el-icon>我的举报</button>
                <button class="ud-item" @click="go('/settings')"><el-icon><Setting /></el-icon>设置</button>
                <button v-if="isStaff" class="ud-item" @click="openAdmin"><el-icon><Monitor /></el-icon>进入管理后台</button>
                <div class="ud-sep" />
                <button class="ud-item is-danger" @click="handleLogout"><el-icon><SwitchButton /></el-icon>退出登录</button>
              </div>
            </transition>
          </div>
          <template v-else>
            <router-link to="/login" class="btn-login">登录</router-link>
            <router-link to="/register" class="btn-register">注册</router-link>
          </template>
        </div>
      </div>
    </header>

    <main class="app-main">
      <div class="page-shell">
        <router-view />
      </div>
    </main>

    <!-- 页脚：商城只需要一条信息栏，不做企业官网式四栏 -->
    <footer class="site-footer">
      <div class="footer-bar">
        <div class="footer-brand">
          <span class="footer-mark">星</span>
          <span class="footer-copy">© 2026 星选商城 · 买手严选</span>
        </div>
        <nav class="footer-links">
          <router-link to="/">首页</router-link>
          <router-link to="/categories">全部分类</router-link>
          <router-link to="/shops">全部店铺</router-link>
          <router-link to="/compare">商品对比</router-link>
          <router-link to="/cart">购物车</router-link>
        </nav>
        <span class="footer-note">商品示例 · 模拟支付</span>
      </div>
    </footer>

    <CompareBar />
    <button v-show="scrolled" class="to-top" type="button" aria-label="回到顶部" @click="scrollTop">
      <el-icon><ArrowUp /></el-icon>
    </button>
  </div>
</template>
