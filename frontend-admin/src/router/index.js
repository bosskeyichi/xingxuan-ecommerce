import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getUser } from '@/utils/auth'

// 动态 import 偶发失败（dev HMR 抖动 / 网络瞬断 / 发版后旧 chunk 404）时重试一次，
// 别让 router-view 拿到 undefined 导致内容区空白。
const chunk = loader => () => loader().catch(() => new Promise(r => setTimeout(r, 300)).then(loader))

const routes = [
  { path: '/login', component: chunk(() => import('@/views/Login.vue')) },
  {
    path: '/',
    component: chunk(() => import('@/layouts/AdminLayout.vue')),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: chunk(() => import('@/views/Dashboard.vue')) },
      { path: 'users', component: chunk(() => import('@/views/UserManage.vue')), meta: { platformOnly: true } },
      { path: 'products', component: chunk(() => import('@/views/ProductManage.vue')) },
      { path: 'categories', component: chunk(() => import('@/views/CategoryManage.vue')), meta: { platformOnly: true } },
      { path: 'orders', component: chunk(() => import('@/views/OrderManage.vue')) },
      { path: 'shops', component: chunk(() => import('@/views/ShopManage.vue')) },
      { path: 'coupons', component: chunk(() => import('@/views/CouponManage.vue')) },
      { path: 'user/:id', component: chunk(() => import('@/views/UserProfileView.vue')) },
      { path: 'friends', component: chunk(() => import('@/views/FriendView.vue')) },
      { path: 'messages', component: chunk(() => import('@/views/MessageView.vue')) },
      { path: 'reports', component: chunk(() => import('@/views/ReportManage.vue')) },
      { path: 'settings', component: chunk(() => import('@/views/SettingView.vue')) },
      { path: 'profile', component: chunk(() => import('@/views/ProfileView.vue')) }
    ]
  }
]

// 本地开发 base 为 /，网关部署（/admin/）时由构建参数注入 VITE base
const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })

router.beforeEach((to, from, next) => {
  const token = getToken()
  if (!token && to.path !== '/login') {
    next('/login')
    return
  }
  // 平台专属页面（用户管理 / 分类管理）：商家手敲 URL 也不能进，后端接口同样会 403
  if (to.meta?.platformOnly && getUser()?.role !== 'ADMIN') {
    ElMessage.warning('该页面仅超级管理员可用')
    next('/dashboard')
    return
  }
  next()
})

// 动态 import 失败（dev server 重启 / HMR 抖动 / 发版后旧 chunk 404）时，router-view 的
// Component 会为 undefined，导致内容区空白但布局正常。自动刷新一次兜底，并用 sessionStorage 防抖别让死循环。
router.onError(err => {
  const msg = String((err && err.message) || '')
  if (/Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk .* failed|ChunkLoadError/i.test(msg)) {
    const KEY = '__admin_chunk_reload__'
    if (!sessionStorage.getItem(KEY)) {
      sessionStorage.setItem(KEY, '1')
      window.location.reload()
    } else {
      sessionStorage.removeItem(KEY)
      ElMessage.error('页面资源加载失败，请手动刷新重试')
    }
  }
})

export default router