import { defineStore } from 'pinia'

/**
 * 登录态存储说明：
 * token 放在 sessionStorage（按标签页隔离），所以「新开窗口 = 新会话」，
 * 同一账号在另一窗口/设备登录时，服务端只保留最后一次的 token，旧窗口会被挤下线。
 * 同一标签页内刷新页面登录态仍然保留。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: sessionStorage.getItem('token') || '',
    userInfo: JSON.parse(sessionStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLogin: state => !!state.token,
    isAdmin: state => state.userInfo?.role === 'ADMIN'
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = {
        id: data.id,
        username: data.username,
        nickname: data.nickname,
        avatar: data.avatar,
        role: data.role
      }
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('userInfo')
    }
  }
})
