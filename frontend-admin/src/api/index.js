import request from '@/utils/request'

export const authApi = {
  // scope=ADMIN：后台会话与用户端会话分开保存，同一账号两端可同时在线
  login: data => request.post('/auth/login', { ...data, scope: 'ADMIN' }),
  logout: () => request.post('/auth/logout'),
  me: () => request.get('/auth/me'),
  updateProfile: data => request.put('/auth/profile', data),
  changePassword: data => request.put('/auth/password', data)
}

export const adminUserApi = {
  page: params => request.get('/auth/admin/users/page', { params }),
  list: () => request.get('/auth/admin/users'),
  status: (id, status) => request.put(`/auth/admin/users/${id}/status`, { status }),
  reset: id => request.post(`/auth/admin/users/${id}/reset`),
  role: (id, role) => request.put(`/auth/admin/users/${id}/role`, { role }),
  delete: id => request.delete(`/auth/admin/users/${id}`),
  notify: (id, content) => request.post(`/auth/admin/users/${id}/notify`, { content })
}

export const adminProductApi = {
  page: params => request.get('/product/admin/list', { params }),
  create: data => request.post('/product/admin', data),
  update: (id, data) => request.put(`/product/admin/${id}`, data),
  delete: id => request.delete(`/product/admin/${id}`)
}

export const adminCategoryApi = {
  list: () => request.get('/category/list'),
  create: data => request.post('/category/admin', data),
  update: (id, data) => request.put(`/category/admin/${id}`, data),
  delete: id => request.delete(`/category/admin/${id}`)
}

export const adminOrderApi = {
  page: params => request.get('/order/admin/list', { params }),
  ship: (orderNo, data) => request.post(`/order/admin/ship/${orderNo}`, data),
  complete: orderNo => request.post(`/order/admin/complete/${orderNo}`),
  remove: orderNo => request.delete(`/order/admin/${orderNo}`),
  batchDelete: orderNos => request.post('/order/admin/batch-delete', { orderNos }),
  exportCsv: () => request.get('/order/admin/export', { responseType: 'blob' })
}

export const adminReviewApi = {
  page: params => request.get('/review/admin/list', { params }),
  remove: id => request.delete(`/review/admin/${id}`),
  hide: id => request.post(`/review/admin/${id}/hide`),
  unhide: id => request.post(`/review/admin/${id}/unhide`)
}

export const adminCouponApi = {
  page: params => request.get('/coupon/admin/list', { params }),
  create: data => request.post('/coupon/admin', data),
  update: (id, data) => request.put(`/coupon/admin/${id}`, data),
  remove: id => request.delete(`/coupon/admin/${id}`)
}

export const shopApi = {
  list: () => request.get('/shop/list'),
  mine: () => request.get('/shop/admin/mine'),
  updateMine: data => request.put('/shop/admin/mine', data),
  adminList: () => request.get('/shop/admin/list'),
  status: (id, status) => request.put(`/shop/admin/${id}/status`, { status }),
  context: () => request.get('/shop/admin/context')
}

/** 好友：申请-同意制，管理端在商家之间互加；超级管理员默认拥有全部商家好友 */
export const friendApi = {
  search: params => request.get('/friend/search', { params }),
  list: () => request.get('/friend/list'),
  card: id => request.get(`/friend/card/${id}`),
  add: data => request.post('/friend', data),
  requests: () => request.get('/friend/requests'),
  accept: requesterId => request.post(`/friend/${requesterId}/accept`),
  reject: requesterId => request.post(`/friend/${requesterId}/reject`),
  remove: id => request.delete(`/friend/${id}`)
}

/** 消息：管理员私聊 + 买家客服 + 系统通知 */
export const messageApi = {
  conversations: scene => request.get('/message/conversations', { params: { scene } }),
  history: params => request.get('/message/history', { params }),
  send: data => request.post('/message', data),
  read: (peerId, scene) => request.post('/message/read', null, { params: { peerId, scene } }),
  peer: (peerId, scene) => request.get('/message/peer', { params: { peerId, scene } }),
  hide: (peerId, scene) => request.post('/message/hide', null, { params: { peerId, scene } }),
  unread: () => request.get('/message/unread'),
  system: params => request.get('/message/system', { params })
}

/** 个人设置：隐私 / 消息开关，与用户端共用同一份配置 */
export const settingApi = {
  get: () => request.get('/setting'),
  save: data => request.put('/setting', data)
}

/** 举报：商家看本店，平台管理员看全部 */
export const reportApi = {
  page: params => request.get('/report/admin/page', { params }),
  pending: () => request.get('/report/admin/pending'),
  handle: (id, data) => request.post(`/report/admin/${id}/handle`, data)
}

// params 可带 { shopId }：平台管理员下钻到单个店铺，商家传了也会被后端忽略
export const statsApi = {
  overview: params => request.get('/admin/stats/overview', { params }),
  trend: (days, params) => request.get('/admin/stats/trend', { params: { days, ...params } }),
  lowStock: (threshold, params) => request.get('/admin/stats/low-stock', { params: { threshold, ...params } }),
  topProducts: (limit, params) => request.get('/admin/stats/top-products', { params: { limit, ...params } })
}
