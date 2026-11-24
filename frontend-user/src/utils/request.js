import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

/**
 * 统一的 401 处理：清除本标签页登录态并跳转登录页（带 redirect 便于登录后返回）。
 * 服务端对「被其他设备/窗口挤下线」会返回专门文案，这里原样提示。
 */
let lastKickAt = 0
const handleUnauthorized = message => {
  const userStore = useUserStore()
  const msg = message || '登录已失效，请重新登录'

  userStore.logout()

  const now = Date.now()
  if (now - lastKickAt < 1500) return   // 并发请求只提示一次
  lastKickAt = now

  ElMessage.error(msg)
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
}

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 文件下载（如订单 CSV 导出）直接返回原始响应
    if (response.config.responseType === 'blob') return response
    const res = response.data
    if (res.code === 200) return res
    if (res.code === 401) {
      handleUnauthorized(res.message)
      return Promise.reject(res)
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(res)
  },
  error => {
    // HTTP 401：Token 失效、后端重启清空会话，或被其他设备挤下线
    if (error.response?.status === 401) {
      handleUnauthorized(error.response?.data?.message)
    } else {
      // 优先展示后端中文提示（如 403 的业务文案）
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
