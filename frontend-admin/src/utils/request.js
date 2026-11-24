import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

// 兼容 Docker 下挂载在 /admin 子路径的部署方式
const loginUrl = () => (location.pathname.startsWith('/admin') ? '/admin/login' : '/login')

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

let lastKickAt = 0
const handleUnauthorized = (message, delay = 0) => {
  clearAuth()
  const now = Date.now()
  if (now - lastKickAt < 1500) return
  lastKickAt = now
  ElMessage.error(message || '登录已失效，请重新登录')
  if (!window.location.pathname.endsWith('/login')) {
    setTimeout(() => { window.location.href = loginUrl() }, delay)
  }
}

request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') return response
    const r = response.data
    if (r.code === 200) return r
    if (r.code === 401) {
      handleUnauthorized(r.message)
      return Promise.reject(r)
    }
    ElMessage.error(r.message || '请求失败')
    return Promise.reject(r)
  },
  error => {
    // HTTP 401：Token 失效、后端重启清空会话，或被其他设备/窗口挤下线
    if (error.response?.status === 401) {
      handleUnauthorized(error.response?.data?.message, 600)
    } else {
      // 优先展示后端返回的中文提示（如拦截器写的 403 文案），别把 axios 的英文原文抛出来
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
