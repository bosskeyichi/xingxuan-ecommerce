<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Odometer, User, Goods, FolderOpened, Tickets, ArrowDown, Monitor, Ticket, Shop,
  ChatDotRound, UserFilled, Warning, Setting
} from '@element-plus/icons-vue'
import { authApi, statsApi, messageApi, reportApi } from '@/api'
import { getUser, clearAuth } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const adminUser = ref(getUser())
const adminName = computed(() => adminUser.value?.nickname || adminUser.value?.username || '管理员')
const initial = computed(() => adminName.value[0].toUpperCase())
const isPlatform = computed(() => adminUser.value?.role === 'ADMIN')
const shopName = computed(() => adminUser.value?.shopName || '未绑定店铺')

// 个人中心保存资料后同步顶栏，无需刷新页面
const onProfileUpdated = () => { adminUser.value = getUser() }
onMounted(() => window.addEventListener('admin:profile-updated', onProfileUpdated))
onUnmounted(() => window.removeEventListener('admin:profile-updated', onProfileUpdated))

const clock = ref('')
let timer = null
let sessionTimer = null
const tick = () => {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}
// 待发货订单数：侧边栏角标提醒，60s 刷新一次
const pendingShip = ref(0)
let badgeTimer = null
const loadBadge = async () => {
  try {
    const r = await statsApi.overview()
    pendingShip.value = Number(r.data?.ordersCount?.PAID || 0)
  } catch (e) { /* 无权限或网络异常时静默 */ }
}
// 单设备登录守卫：被其他设备/窗口挤下线时，/auth/me 返回 401，由拦截器跳转登录页
const checkSession = async () => {
  try { await authApi.me() } catch (e) { /* 401 已统一处理 */ }
}

// 未读消息数 / 待处理举报数：侧边栏角标
const unread = ref(0)
const reportPending = ref(0)
const loadSocialBadge = async () => {
  try {
    const r = await messageApi.unread()
    unread.value = Number(r.data?.total || 0)
  } catch (e) { /* 静默 */ }
  try {
    const r = await reportApi.pending()
    reportPending.value = Number(r.data || 0)
  } catch (e) { /* 静默 */ }
}

onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
  sessionTimer = setInterval(checkSession, 10000)
  loadBadge()
  loadSocialBadge()
  badgeTimer = setInterval(loadBadge, 60000)
  window.addEventListener('focus', checkSession)
  window.addEventListener('admin:orders-changed', loadBadge)
  window.addEventListener('admin:message-changed', loadSocialBadge)
  window.addEventListener('admin:reports-changed', loadSocialBadge)
})
// 切换页面即刷新角标，发货后无需等 60 秒
watch(() => route.path, () => { loadBadge(); loadSocialBadge() })
onUnmounted(() => {
  clearInterval(timer)
  clearInterval(sessionTimer)
  clearInterval(badgeTimer)
  window.removeEventListener('focus', checkSession)
  window.removeEventListener('admin:orders-changed', loadBadge)
  window.removeEventListener('admin:message-changed', loadSocialBadge)
  window.removeEventListener('admin:reports-changed', loadSocialBadge)
})

const menus = [
  { path: '/dashboard', label: '概览', icon: Odometer },
  { path: '/products', label: '商品管理', icon: Goods },
  { path: '/orders', label: '订单管理', icon: Tickets },
  { path: '/shops', label: '店铺管理', icon: Shop },
  { path: '/reports', label: '举报管理', icon: Warning },
  { path: '/users', label: '用户管理', icon: User, platformOnly: true },
  { path: '/categories', label: '分类管理', icon: FolderOpened, platformOnly: true },
  { path: '/coupons', label: '优惠券', icon: Ticket },
  { path: '/friends', label: '好友', icon: UserFilled },
  { path: '/messages', label: '消息', icon: ChatDotRound },
  { path: '/settings', label: '设置', icon: Setting }
]
const visibleMenus = computed(() => menus
  .filter(m => !m.platformOnly || isPlatform.value)
  .map(m => (m.path === '/shops' && !isPlatform.value ? { ...m, label: '我的店铺' } : m)))

// 侧边栏角标：待发货订单 / 未读消息 / 待处理举报
const badgeOf = m => {
  if (m.path === '/orders') return pendingShip.value
  if (m.path === '/messages') return unread.value
  if (m.path === '/reports') return reportPending.value
  return 0
}

const titles = {
  '/dashboard': '概览',
  '/users': '用户管理',
  '/products': '商品管理',
  '/categories': '分类管理',
  '/orders': '订单管理',
  '/shops': '店铺管理',
  '/coupons': '优惠券',
  '/friends': '好友',
  '/messages': '消息',
  '/reports': '举报管理',
  '/settings': '设置'
}
const pageTitle = computed(() => {
  if (titles[route.path]) return titles[route.path]
  // 用户主页 /user/:id
  if (route.path.startsWith('/user/')) return '用户主页'
  return '后台管理'
})

// 开发态管理端跑在 5174，用户端在 5173；网关部署下两端同域，直接回根路径
const shopUrl = import.meta.env.VITE_SHOP_URL
  || (location.port === '5174' ? `${location.protocol}//${location.hostname}:5173` : '/')

const reloadPage = () => window.location.reload()
const onCommand = async (cmd) => {
  if (cmd === 'frontend') {
    window.open(shopUrl, '_blank')
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'settings') {
    router.push('/settings')
  } else if (cmd === 'logout') {
    try { await authApi.logout() } catch (e) { /* 本地也要清理 */ }
    clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <div class="admin-shell">
    <!-- 侧边栏 -->
    <aside class="admin-side">
      <div class="side-brand">
        <div class="side-logo">星</div>
        <div>
          <div class="side-brand-name">星选商城</div>
          <div class="side-brand-sub">{{ isPlatform ? 'Admin Console' : 'Merchant Console' }}</div>
        </div>
      </div>

      <div class="side-section">{{ isPlatform ? '平台管理' : shopName }}</div>
      <nav class="side-menu">
        <button
          v-for="m in visibleMenus"
          :key="m.path"
          class="side-item"
          :class="{ 'is-active': route.path === m.path }"
          @click="router.push(m.path)"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
          <span v-if="badgeOf(m) > 0" class="side-badge">{{ badgeOf(m) > 99 ? '99+' : badgeOf(m) }}</span>
        </button>
      </nav>

      <div class="side-foot">
        <div class="side-foot-card">
          <b>运营提示</b>
          待支付订单超过 30 分钟将自动取消并释放库存，可在「订单管理」中查看。
        </div>
      </div>
    </aside>

    <!-- 主体 -->
    <div class="admin-body">
      <header class="admin-topbar">
        <div>
          <div class="topbar-crumb">后台管理 / {{ pageTitle }}</div>
          <div class="topbar-title">{{ pageTitle }}</div>
        </div>

        <div class="topbar-right">
          <span v-if="!isPlatform" class="topbar-shopname">{{ shopName }}</span>
          <a class="topbar-shop" :href="shopUrl" target="_blank" rel="noopener">
            <el-icon :size="14"><Monitor /></el-icon>
            <span>商城前台</span>
          </a>
          <span class="topbar-clock">{{ clock }}</span>
          <el-dropdown trigger="click" @command="onCommand">
            <button class="admin-user">
              <span class="admin-avatar">
                <img v-if="adminUser?.avatar" :src="adminUser.avatar" alt="avatar" />
                <template v-else>{{ initial }}</template>
              </span>
              <span style="font-weight:600;font-size:14px">{{ adminName }}</span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ isPlatform ? '超级管理员' : '普通管理员（商家）' }}</el-dropdown-item>
                <el-dropdown-item command="profile" :icon="User" divided>个人中心</el-dropdown-item>
                <el-dropdown-item command="settings" :icon="Setting">隐私与消息设置</el-dropdown-item>
                <el-dropdown-item command="frontend" :icon="Monitor">查看商城前台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="admin-content">
        <router-view v-slot="{ Component }">
          <component v-if="Component" :is="Component" :key="route.path" />
          <div v-else class="page-load-fallback">
            <p class="empty-title">页面加载失败</p>
            <p class="empty-sub">可能是网络波动或开发服务器正在更新，请刷新重试。</p>
            <el-button type="primary" @click="reloadPage">重新加载</el-button>
          </div>
        </router-view>
      </section>
    </div>
  </div>
</template>

<style>
/* 多商家：店铺标识（表格列 / 顶栏共用） */
.shop-tag {
  display: inline-flex;
  align-items: center;
  max-width: 130px;
  padding: 2px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-2);
  background: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topbar-shopname {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--brand-soft, #fdeceb);
  color: var(--brand-600, #c91d14);
  font-size: 12.5px;
  font-weight: 600;
}
.topbar-shopname::before { content: '店'; font-size: 11px; opacity: .8; }
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity .24s ease, transform .24s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(12px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
