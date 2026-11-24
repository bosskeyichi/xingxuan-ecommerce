import request from '@/utils/request'

export const authApi = {
  login: data => request.post('/auth/login', data),
  register: data => request.post('/auth/register', data),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout'),
  updateProfile: data => request.put('/auth/profile', data),
  changePassword: data => request.put('/auth/password', data)
}

export const productApi = {
  list: params => request.get('/product/list', { params }),
  detail: id => request.get(`/product/detail/${id}`),
  search: params => request.get('/product/search', { params }),
  hot: params => request.get('/product/hot', { params }),
  recommend: params => request.get('/product/recommend', { params })
}

export const categoryApi = {
  list: () => request.get('/category/list')
}

/** 店铺（多商家） */
export const shopApi = {
  list: () => request.get('/shop/list'),
  search: (keyword, limit = 10) => request.get('/shop/search', { params: { keyword, limit } }),
  detail: id => request.get(`/shop/detail/${id}`)
}

/** 好友：申请-同意制，用户端只在普通用户之间互加 */
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

/** 消息：好友私聊 / 店铺客服 / 系统通知 */
export const messageApi = {
  conversations: scene => request.get('/message/conversations', { params: { scene } }),
  history: params => request.get('/message/history', { params }),
  send: data => request.post('/message', data),
  read: (peerId, scene) => request.post('/message/read', null, { params: { peerId, scene } }),
  peer: (peerId, scene) => request.get('/message/peer', { params: { peerId, scene } }),
  hide: (peerId, scene) => request.post('/message/hide', null, { params: { peerId, scene } }),
  unread: () => request.get('/message/unread'),
  system: params => request.get('/message/system', { params }),
  serviceTarget: shopId => request.get('/message/service-target', { params: { shopId } })
}

/** 个人设置：隐私 / 消息开关 */
export const settingApi = {
  get: () => request.get('/setting'),
  save: data => request.put('/setting', data)
}

/** 举报：商品 / 店铺 */
export const reportApi = {
  submit: data => request.post('/report', data),
  mine: params => request.get('/report/mine', { params })
}

export const cartApi = {
  list: () => request.get('/cart/list'),
  add: data => request.post('/cart/add', data),
  updateQty: data => request.put('/cart/quantity', data),
  updateSelected: data => request.put('/cart/selected', data),
  delete: id => request.delete(`/cart/${id}`)
}

export const orderApi = {
  submit: data => request.post('/order/submit', data),
  list: params => request.get('/order/list', { params }),
  detail: orderNo => request.get(`/order/detail/${orderNo}`),
  pay: orderNo => request.post(`/order/pay/${orderNo}`),
  payBatch: orderNos => request.post('/order/pay/batch', { orderNos }),
  cancel: orderNo => request.post(`/order/cancel/${orderNo}`),
  confirm: orderNo => request.post(`/order/confirm/${orderNo}`),
  remove: orderNo => request.delete(`/order/${orderNo}`),
  batchDelete: orderNos => request.post('/order/batch-delete', { orderNos })
}

/** 优惠券 */
export const couponApi = {
  available: () => request.get('/coupon/available'),
  usable: amount => request.get('/coupon/usable', { params: { amount } }),
  mine: () => request.get('/coupon/mine'),
  claim: id => request.post(`/coupon/claim/${id}`)
}

/** 浏览足迹（服务端按账号存储，随「重置数据」一并清空） */
export const browseApi = {
  record: productId => request.post('/browse/record', { productId }),
  list: () => request.get('/browse/list'),
  remove: productId => request.delete(`/browse/${productId}`),
  clear: () => request.delete('/browse/clear')
}

/** 商品收藏 */
export const favoriteApi = {
  list: () => request.get('/favorite/list'),
  ids: () => request.get('/favorite/ids'),
  add: productId => request.post(`/favorite/${productId}`),
  remove: productId => request.delete(`/favorite/${productId}`),
  check: productId => request.get(`/favorite/check/${productId}`)
}

/** 商品评价 */
export const reviewApi = {
  list: productId => request.get(`/review/list/${productId}`),
  summary: productId => request.get(`/review/summary/${productId}`),
  mineByOrder: orderNo => request.get(`/review/mine/order/${orderNo}`),
  add: data => request.post('/review', data),
  update: (id, data) => request.put(`/review/${id}`, data),
  append: (id, content) => request.post(`/review/${id}/append`, { content }),
  remove: id => request.delete(`/review/${id}`),
  minePage: params => request.get('/review/mine/page', { params }),
  batchDelete: ids => request.post('/review/batch-delete', { ids })
}

/** 收货地址簿 */
export const addressApi = {
  list: () => request.get('/address/list'),
  add: data => request.post('/address', data),
  update: (id, data) => request.put(`/address/${id}`, data),
  remove: id => request.delete(`/address/${id}`),
  setDefault: id => request.put(`/address/${id}/default`)
}
