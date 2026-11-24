import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
      // 历史链接兼容：全部商品即首页商品流
      { path: 'products', redirect: to => ({ path: '/', query: to.query }) },
      { path: 'categories', name: 'categories', component: () => import('@/views/CategoriesView.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/SearchView.vue') },
      { path: 'product/:id', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue') },
      { path: 'shop/:id', name: 'shop', component: () => import('@/views/ShopView.vue') },
      { path: 'cart', name: 'cart', component: () => import('@/views/CartView.vue'), meta: { auth: true } },
      { path: 'orders', name: 'orders', component: () => import('@/views/OrderListView.vue'), meta: { auth: true } },
      { path: 'order/:orderNo', name: 'order-detail', component: () => import('@/views/OrderDetailView.vue'), meta: { auth: true } },
      { path: 'checkout', name: 'checkout', component: () => import('@/views/CheckoutView.vue'), meta: { auth: true } },
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { auth: true } },
      { path: 'favorites', name: 'favorites', component: () => import('@/views/FavoritesView.vue'), meta: { auth: true } },
      { path: 'coupons', name: 'coupons', component: () => import('@/views/CouponView.vue'), meta: { auth: true } },
      { path: 'compare', name: 'compare', component: () => import('@/views/CompareView.vue') },
      { path: 'footprints', name: 'footprints', component: () => import('@/views/FootprintView.vue'), meta: { auth: true } },
      { path: 'user/:id', name: 'user-profile', component: () => import('@/views/UserProfileView.vue'), meta: { auth: true } },
      { path: 'friends', name: 'friends', component: () => import('@/views/FriendsView.vue'), meta: { auth: true } },
      { path: 'messages', name: 'messages', component: () => import('@/views/MessagesView.vue'), meta: { auth: true } },
      { path: 'my-reviews', name: 'my-reviews', component: () => import('@/views/MyReviewsView.vue'), meta: { auth: true } },
      { path: 'my-reports', name: 'my-reports', component: () => import('@/views/MyReportsView.vue'), meta: { auth: true } },
      { path: 'shops', name: 'shops', component: () => import('@/views/ShopListView.vue') },
      { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { auth: true } }
    ]
  },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) return savedPosition
    // 同一页面仅切换 ?categoryId=（首页点分类）时不干预滚动，
    // 由首页自己定位到商品区；否则会被这里重置回顶部，导致"要点两次"。
    const samePath = to.path === from.path
    const onlyCategoryChanged =
      samePath && to.query.categoryId !== from.query.categoryId
    if (onlyCategoryChanged) return false
    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.auth && !userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
