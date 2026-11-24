/**
 * 单设备登录守卫
 * 登录态按标签页隔离（sessionStorage），服务端只保留每个账号最新一次登录的 token。
 * 这里定时（以及切回窗口时）请求 /auth/me，一旦发现自己已被其他设备/窗口挤下线，
 * 服务端返回 401，由 request 拦截器统一提示并跳转登录页。
 */
import router from '@/router'
import { useUserStore } from '@/stores/user'

export function startSessionGuard() {
  const userStore = useUserStore()

  let checking = false
  const check = async () => {
    if (!userStore.isLogin || checking) return
    checking = true
    try {
      const { authApi } = await import('@/api')
      await authApi.me()
    } catch (err) {
      // 401 已由 request 拦截器处理（清登录态 + 提示 + 跳转登录页）
    } finally {
      checking = false
    }
  }

  setInterval(check, 10000)
  window.addEventListener('focus', check)
  document.addEventListener('visibilitychange', () => { if (!document.hidden) check() })

  // 路由切换时也校验一次，尽早发现被挤下线
  router.afterEach(() => { check() })
}
