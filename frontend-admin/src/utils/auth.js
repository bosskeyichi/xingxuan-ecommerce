/**
 * 后台登录态存储
 *
 * 放 localStorage 而不是 sessionStorage：后台是工作台，正常用法是同一浏览器开多个
 * 标签页（概览 / 商品 / 订单）。sessionStorage 按标签页隔离，新标签必然要求重新登录，
 * 登录又会把旧标签的同账号会话覆盖，表现就是「每次登录都被挤号」。
 * 多标签共享登录态后，同一账号在另一浏览器 / 设备登录仍会被服务端挤下线（单设备登录不变）。
 */
const TOKEN_KEY = 'admin_token'
const USER_KEY = 'admin_user'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''

export const getUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

export const setAuth = (token, user) => {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
}

export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
