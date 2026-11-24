# REST 接口文档

- 基础路径：直连后端 `http://localhost:8080/api`（端口由 `server.port` 决定）；经网关 `http://<host>:8080/api`（Docker / 服务器部署，网关把 `/api/**` 原样转发）
- 认证方式：请求头 `Authorization: Bearer <token>`
- 响应格式：

```jsonc
{ "code": 200, "message": "success", "data": { } }   // 成功
{ "code": 400, "message": "错误原因", "data": null }  // 业务/参数错误
{ "code": 401, "message": "Not logged in" }          // 未登录（HTTP 401）
{ "code": 403, "message": "无权限操作" }               // 无权限（HTTP 403）
```

> 说明：业务异常返回 HTTP 200 + `code != 200`；认证/授权失败返回对应 HTTP 状态码。

> **权限与端职责**：`/admin/**` 接口由 `AdminInterceptor` 校验后台身份（`ADMIN` 或 `MERCHANT`），其中标注「平台管理员」的接口再由 `PlatformInterceptor` 收紧为 `role=ADMIN`；写操作另用 `ShopService.requireOperableShopId()` 限定为「自己名下的店铺」。**用户端 API 层不再声明这些方法**（`exportCsv`、`adminCouponApi` 已从 `frontend-user` 移除），只保留在管理端 `frontend-admin/src/api/index.js`。用户端页面也不提供任何跳转管理端的入口。

**权限分层（与 `config/WebConfig.java` 完全一致）**

| 层级 | 拦截器 | 覆盖路径 | 放行条件 |
|------|--------|----------|----------|
| 公开 | — | `/health`、`/auth/login`、`/auth/register`、`/product/list`、`/product/detail/**`、`/product/search`、`/product/hot`、`/product/recommend`、`/review/list/**`、`/review/summary/**`、`/category/list`、`/coupon/available`、`/shop/list`、`/shop/search`、`/shop/detail/**` | 无需登录 |
| 登录 | `LoginInterceptor` | 上表之外的全部路径 | 有效 token |
| 后台身份 | `AdminInterceptor` | `/product/admin/**`、`/category/admin/**`、`/order/admin/**`、`/review/admin/**`、`/coupon/admin/**`、`/auth/admin/**`、`/shop/admin/**`、`/report/admin/**`、`/admin/**` | `ADMIN` 或 `MERCHANT` |
| 平台级 | `PlatformInterceptor` | `/auth/admin/**`、`/category/admin/**`、`/review/admin/**`、`/shop/admin/list`、`/shop/admin/*/status` | 仅 `ADMIN` |

> **易错点**：`/coupon/admin/**`、`/order/admin/**`、`/product/admin/**`、`/report/admin/**` **对商家开放**，商家的数据范围由 `ShopService.currentShopId()` 限定在本店。优惠券正是刻意留给商家发本店券的（`WebConfig` 注释明确说明），所以它**不在**平台级路径里。
> 评价管理（`/review/admin/**`）为平台级仅 ADMIN，且屏蔽 / 取消屏蔽不做店铺归属校验；评价**没有商家回复**接口，只能发表、追评、修改、删除。

---

## 〇、健康检查 `/health`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/health` | 公开 | 后端存活探针，供 Docker healthcheck 与部署验收使用 |

```jsonc
// GET /api/health
{ "code": 200, "message": "服务正常",
  "data": { "status": "UP", "service": "ecommerce-backend",
            "instance": "608d3f4afba9", "port": "8080", "time": "2026-10-31 10:14:11" } }
```

> `instance` 取容器 hostname，多实例部署时可据此确认请求实际落在哪个实例上。
> 网关层另有 `GET /health`（不走后端），直接返回文本 `gateway ok`。

---

## 一、认证 `/auth`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/auth/login` | 公开 | 登录，返回 token 与用户信息；可传 `scope: "ADMIN"` 登录后台（会话与用户端分开保存） |
| POST | `/auth/register` | 公开 | 注册 |
| GET | `/auth/me` | 登录 | 当前登录用户 |
| PUT | `/auth/profile` | 登录 | 更新个人资料 |
| PUT | `/auth/password` | 登录 | 修改密码（成功后强制重新登录） |
| POST | `/auth/logout` | 登录 | 退出登录（只清当前端会话，另一端保留） |
| GET | `/auth/admin/users` | 仅平台管理员 | 用户列表（不分页，前端已改用分页版） |
| GET | `/auth/admin/users/page` | 仅平台管理员 | 用户分页列表 |
| PUT | `/auth/admin/users/{id}/status` | 仅平台管理员 | 启用/禁用用户 |
| DELETE | `/auth/admin/users/{id}` | 仅平台管理员 | 删除用户（店主账号需先取消普通管理员身份） |
| POST | `/auth/admin/users/{id}/reset` | 仅平台管理员 | 重置数据：清空订单 / 购物车 / 优惠券 / 收藏 / 评价 / 地址 / 足迹并回补库存；商家额外清空本店商品与优惠券、店铺资料恢复默认；账号资料恢复初始，密码重置为默认密码（见初始化文档，不在接口回显） |
| PUT | `/auth/admin/users/{id}/role` | 超级管理员 | 调整角色 `{ "role": "MERCHANT" \| "USER" }`：提升为普通管理员时自动建店；降级前须清空店铺商品与订单 |

### POST `/auth/login`
```jsonc
// 请求
{ "username": "admin", "password": "123456" }
// 响应 data
{ "id": 1, "username": "admin", "nickname": "超级管理员", "role": "ADMIN", "token": "eyJhbGci..." }
```

### PUT `/auth/profile`
```jsonc
{
  "nickname": "爱丽丝",
  "email": "alice@demo.com",
  "phone": "13800138001",
  "avatar": "data:image/jpeg;base64,/9j/4AAQ..."  // 可选：自定义头像（前端裁剪为 200×200 后传 base64），传 "" 表示恢复默认
}
```
响应为更新后的用户对象，`avatar` 原样回显；`user_table.avatar` 使用 TEXT，可容纳 base64 图片。

### PUT `/auth/password`
```jsonc
{ "oldPassword": "123456", "newPassword": "newpass123" }
```

---

## 二、商品 `/product`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/product/list` | 公开 | 商品分页（默认仅上架） |
| GET | `/product/search` | 公开 | 关键词搜索 |
| GET | `/product/detail/{id}` | 公开 | 商品详情 |
| GET | `/product/hot?limit=8` | 公开 | 热销商品（按销量倒序，`limit` 默认 8，范围 1–50） |
| GET | `/product/recommend?categoryIds=1,2&limit=4` | 公开 | 猜你喜欢（按浏览分类聚合热销，`categoryIds` 逗号分隔，最多取 5 个分类；`limit` 默认 4，范围 1–12） |
| GET | `/product/admin/list` | 管理员 | 后台商品分页（全部状态） |
| POST | `/product/admin` | 管理员 | 新增商品 |
| PUT | `/product/admin/{id}` | 管理员 | 修改商品 |
| DELETE | `/product/admin/{id}` | 管理员 | 删除商品（逻辑删除，商品从管理端/用户端消失；历史订单保留快照） |

> 商品「下架」与「删除」是两个独立动作：下架用 `PUT /product/admin/{id}` 把 `status` 置 0，商品仍保留在后台列表；删除走 `DELETE /product/admin/{id}`，执行逻辑删除，并清理该商品的购物车、收藏、浏览足迹与评价；`order_item` 保存了商品名称/价格快照，历史订单不受影响。

### GET `/product/list`
参数：`current`(默认1) `size`(默认12，最大100) `categoryId` `keyword` `status`
`minPrice` `maxPrice`（价格区间，min > max 时自动交换） `inStock`（`true` 只看有货）
`sort`：`default`（综合，上架时间倒序）/ `sales_desc`（销量）/ `price_asc` / `price_desc` / `newest`（最新上架）

> 关键词按**四级渐进**匹配，命中即停，越靠前越精确：
> ① 匹配**商品名 + 搜索别名 `keywords`**（搜「鞋子」命中 `Nike Air Force 1`，别名里写了「鞋子,鞋,运动鞋」；搜「耳机」不会带出充电器）；
> ② 名称 / 别名无命中时，**剥掉 1~2 个字的前缀或后缀**再试，覆盖中文「修饰词 + 品类词」的说法（「男鞋」→「鞋」、「男士运动鞋」→「运动鞋」、「苹果手机」→「手机」）；
> ③ 再不行才退到**分类名**（搜「手机数码」「数码」→ 整类）；
> ④ 最后放宽到**名称 / 别名 / 描述**（如「降噪」只写在卖点里）。
> 走 ③ / ④ 时前端会在结果上方提示「没有精确匹配『xx』的商品，以下是放宽匹配的结果」，别让"搜手机出来一堆配件"的误解。

### GET `/product/search`
参数：`keyword`（必填）`current` `size` `minPrice` `maxPrice` `inStock` `sort`（取值同上）

### POST `/product/admin`
```jsonc
{
  "name": "商品名称",        // 必填，≤100 字符
  "categoryId": 1,          // 分类 ID
  "price": 99.00,           // 必填，> 0
  "stock": 100,             // 必填，≥ 0
  "cover": "https://...",   // 封面 URL
  "description": "描述",     // ≤500 字符
  "keywords": "鞋子,运动鞋",  // 搜索别名，逗号分隔，≤200 字符；商品名里没有的品类词写这里
  "status": 1               // 1 上架 / 0 下架
}
```

---

## 二·B、店铺 `/shop`（多商家）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/shop/list` | 公开 | 营业中的店铺列表（前台筛选用） |
| GET | `/shop/detail/{id}` | 公开 | 店铺详情 |
| GET | `/shop/admin/context` | 管理员 / 商家 | 当前身份：`role` / `shopId` / `shopName` / `platformAdmin` |
| GET | `/shop/admin/mine` | 管理员 / 商家 | 我的店铺（平台管理员返回 null） |
| PUT | `/shop/admin/mine` | 管理员 / 商家 | 修改我的店铺资料 `{ name, intro, cover }` |
| GET | `/shop/admin/list` | 仅平台管理员 | 全部店铺 + `productCount` / `orderCount` / `salesAmount` |
| PUT | `/shop/admin/{id}/status` | 仅平台管理员 | 开店 / 关店 `{ status: 1|0 }` |

> 商品列表 `GET /product/list` 新增可选参数 `shopId`：首页「店铺」下拉即用它筛选。
> 后台商品 / 订单列表：商家自动限定在本店，平台管理员可用 `shopId` 参数按店铺筛查。

---

## 三、分类 `/category`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/category/list` | 公开 | 分类列表 |
| POST | `/category/admin` | 管理员 | 新增分类（名称不可重复） |
| PUT | `/category/admin/{id}` | 管理员 | 修改分类 |
| DELETE | `/category/admin/{id}` | 管理员 | 删除分类（分类下无商品才可删） |

---

## 四、购物车 `/cart`（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/cart/list` | 购物车列表（含商品实时价格、库存、小计） |
| POST | `/cart/add` | 加入购物车 `{ productId, quantity }` |
| PUT | `/cart/quantity` | 修改数量 `{ cartId, quantity }` |
| PUT | `/cart/selected` | 修改选中 `{ cartId, selected }`（0/1） |
| DELETE | `/cart/{cartId}` | 删除购物车项 |

> `quantity` 取值 1–999，超出返回 400。

---

## 五、订单 `/order`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/order/submit` | 登录 | 提交订单（结算购物车中 `selected=1` 的商品；**按店铺拆单**，返回订单号数组） |
| GET | `/order/list` | 登录 | 我的订单分页 |
| GET | `/order/detail/{orderNo}` | 登录（本人） | 订单详情 |
| POST | `/order/pay/{orderNo}` | 登录（本人） | 模拟支付 |
| POST | `/order/pay/batch` | 登录（本人） | **合并支付**：一次付清多笔待支付订单（跨店拆单后无需逐笔点） |
| POST | `/order/cancel/{orderNo}` | 登录（本人） | 取消订单（仅待支付，回退库存+还原购物车） |
| POST | `/order/confirm/{orderNo}` | 登录（本人） | 确认收货（仅已发货） |
| DELETE | `/order/{orderNo}` | 登录（本人） | 删除历史订单（仅已完成 / 已取消） |
| POST | `/order/batch-delete` | 登录 | 批量删除历史订单 `{ orderNos: [] }`（≤100 笔） |
| GET | `/order/admin/list` | 管理员 / 商家 | 订单分页（商家只看本店；管理员可用 `shopId` 筛查） |
| POST | `/order/admin/ship/{orderNo}` | 商家 / 超管 | 发货（仅已支付；只作用于自己名下的店铺，商家=本店、超管=官方旗舰店，别家店 403） |
| POST | `/order/admin/complete/{orderNo}` | 商家 / 超管 | 标记完成（仅已发货；店铺范围同上） |
| DELETE | `/order/admin/{orderNo}` | 管理员 / 商家 | 删除历史订单（仅已完成 / 已取消） |
| POST | `/order/admin/batch-delete` | 管理员 / 商家 | 批量删除历史订单 `{ orderNos: [] }`（≤100 笔） |
| GET | `/order/admin/export` | 管理员 / 商家 | 导出订单为 CSV（含「店铺」列；商家只导出本店） |

> 删除订单采用逻辑删除，仅允许终态（`COMPLETED` / `CANCELLED`）；进行中的订单需先取消或完成，别让库存锁定与状态机无法追踪。
>
> 合并支付：`POST /order/pay/batch` 入参 `{ "orderNos": ["ORD...", "ORD..."] }`（≤50 笔），逐笔条件更新 `PENDING_PAYMENT → PAID` 并返回实际支付笔数；已支付的订单自动跳过，不会重复支付。

### POST `/order/submit`
```jsonc
{
  "receiverName": "张三",
  "receiverPhone": "13800138000",
  "receiverAddr": "北京市朝阳区xx路1号",
  "remark": "工作日送达",
  "userCouponId": 12            // 可选：使用的用户优惠券 id（来自 /coupon/mine 或 /coupon/usable）
}
// 响应 data：订单号数组（同一店铺 1 个，跨店铺 N 个）
// ["ORD20261023225020A1B2C3D4", "ORD20261023225020E5F6G7H8"]
// message：单店「下单成功」；跨店「已按店铺生成 2 笔订单」
```

> 多商家：结算时按商品所属店铺分组，**每个店铺生成一笔独立订单**，全部在同一事务内完成；任一店铺扣库存失败则整批回滚。
> 优惠券规则：平台券（`coupon.shop_id` 为空）抵扣「金额最高且满足门槛」的那一笔店铺订单；店铺券（`shop_id` 非空）只能抵扣该店铺的订单，且本店小计必须达到门槛，否则返回「该优惠券仅限某店使用」。`user_coupon.order_id` 记录实际抵扣的订单。
> 提交时会校验优惠券归属、有效期、使用门槛（满减金额），并在同一事务内将 `user_coupon.status` 置为 `USED`、订单 `discount_amount / coupon_id` 落地；取消 / 超时取消会原子回退优惠券与库存。

### 订单响应字段（list / detail / admin/list）

订单对象在原有字段基础上新增：

| 字段 | 类型 | 说明 |
|------|------|------|
| `discountAmount` | number | 优惠金额（优惠后实付 = `totalAmount - discountAmount`） |
| `couponId` | number | 使用的用户优惠券 id（未用为 null） |
| `shopId` | number | 订单所属店铺 id |
| `shopName` | string | 订单所属店铺名（后端回填，免二次请求） |

订单列表 / 详情前端据此展示「优惠 -¥X / 实付 ¥Y」。

### 订单状态枚举

| 值 | 含义 |
|----|------|
| `PENDING_PAYMENT` | 待支付 |
| `PAID` | 已支付 |
| `SHIPPED` | 已发货 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消 |

---

## 六、收藏 `/favorite`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/favorite/list` | 登录 | 我的收藏商品列表 |
| GET | `/favorite/ids` | 登录 | 已收藏商品 ID 数组 |
| GET | `/favorite/check/{productId}` | 登录 | 是否已收藏 `{ favorited: true }` |
| POST | `/favorite/{productId}` | 登录 | 收藏 |
| DELETE | `/favorite/{productId}` | 登录 | 取消收藏 |

---

## 六·B、收货地址簿 `/address`（新增）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/address/list` | 登录 | 我的地址列表（默认地址排最前） |
| POST | `/address` | 登录 | 新增地址（首次添加自动设为默认；最多 20 条） |
| PUT | `/address/{id}` | 登录 | 修改地址（仅能操作自己的） |
| DELETE | `/address/{id}` | 登录 | 删除地址（若删的是默认，自动将最新一条提升为默认） |
| PUT | `/address/{id}/default` | 登录 | 设为默认地址（原默认自动取消） |

请求/响应体：

```jsonc
{
  "receiverName": "爱丽丝",          // 必填，≤50 字符
  "receiverPhone": "13800138001",   // 必填，11 位手机号
  "region": "北京市 朝阳区 望京街道", // 必填，≤100 字符
  "detail": "融科橄榄城 3 号楼 501",  // 必填，≤200 字符
  "isDefault": 1                    // 可选，1 = 设为默认
}
```

> 说明：后端会话保存在内存（单设备登录），**后端重启后旧 Token 会话失效**，
> 前端收到 HTTP 401 会统一清除本地凭据并跳转登录页。

---

## 七、评价 `/review`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/review/list/{productId}` | 公开 | 商品评价列表（过滤已屏蔽） |
| GET | `/review/summary/{productId}` | 公开 | 评分汇总（平均分 / 总数 / 星级分布，过滤已屏蔽） |
| GET | `/review/mine/order/{orderNo}` | 登录 | 当前订单下我的评价（含追评、屏蔽标记） |
| POST | `/review` | 登录（需已确认收货） | 按订单商品发表评价 |
| PUT | `/review/{id}` | 登录（本人） | 修改评价评分 / 内容 |
| POST | `/review/{id}/append` | 登录（本人） | 追加评价 `{ content }` |
| DELETE | `/review/{id}` | 登录（本人） | 删除评价（保留墓碑，可重新评价） |
| GET | `/review/admin/list` | 仅平台管理员 | 商品评价分页（含已屏蔽，`productId/current/size`） |
| DELETE | `/review/admin/{id}` | 仅平台管理员 | 管理员删除评价 |
| POST | `/review/admin/{id}/hide` | 仅平台管理员 | 屏蔽评价（软屏蔽，可恢复） |
| POST | `/review/admin/{id}/unhide` | 仅平台管理员 | 取消屏蔽 |

### POST `/review`
```jsonc
{ "productId": 15, "rating": 5, "content": "书本质量很好", "orderNo": "ORD..." }
```

> 同一用户对同一商品仅允许一条有效评价；唯一键 `(user_id, product_id, deleted)`，删除时 `deleted = id` 作为墓碑，既允许重新评价，也别让种子数据把已删除评价重新插入。

### GET `/review/summary/{productId}`
```jsonc
{ "count": 3, "avgRating": 4.7, "distribution": { "5": 2, "4": 1, "3": 0, "2": 0, "1": 0 } }
```

---

## 七·B、浏览足迹 `/browse`（★ 新增，需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/browse/record` | 记录浏览 `{ productId }`（去重，仅保留最近 12 条） |
| GET | `/browse/list` | 最近浏览商品（按时间倒序，过滤下架 / 删除商品） |
| DELETE | `/browse/{productId}` | 移除单条足迹 |
| DELETE | `/browse/clear` | 清空全部足迹 |

> 足迹按账号保存在服务端，可跨设备同步；管理员「重置数据」或删除用户时会一并清空。旧版 `localStorage` 足迹键会在前端启动时自动清理。

---

## 八、统计 `/admin/stats`（后台身份：ADMIN / MERCHANT）

四个接口都支持可选 `shopId`：平台管理员可下钻到单个店铺，商家传了也会被忽略（固定本店）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/stats/overview?shopId=2` | 总览指标，返回 `scope`（PLATFORM/SHOP）与 `shopId` |
| GET | `/admin/stats/trend?days=7&shopId=2` | 近 N 天销售趋势（1–30） |
| GET | `/admin/stats/low-stock?threshold=10&shopId=2` | 库存预警商品（含 `shopName`） |
| GET | `/admin/stats/top-products?limit=8&shopId=2` | 热销商品排行（含 `shopName`） |

### GET `/admin/stats/overview`
```jsonc
{
  "scope": "PLATFORM",      // 传了 shopId 时为 SHOP
  "shopId": null,
  "userCount": 5,
  "productCount": 15,
  "categoryCount": 6,
  "orderCount": 3,
  "ordersCount": { "PENDING_PAYMENT": 0, "PAID": 0, "SHIPPED": 1, "COMPLETED": 1, "CANCELLED": 1 },
  "todaySales": 9498.00,
  "totalSales": 10997.00,
  "lowStockCount": 0,
  "couponCount": 4,
  "couponAvailable": 4,
  "couponUsed": 0
}
```

### GET `/admin/stats/trend`
```jsonc
[ { "date": "2026-10-23", "amount": 9498.00, "count": 1 }, ... ]
```

---

## 九、错误码

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 业务校验失败 / 参数错误 |
| 401 | 未登录 / token 失效 / 异地登录 |
| 403 | 无权限（非管理员） |
| 500 | 服务器内部错误（不返回具体原因） |

---

## 十、优惠券 `/coupon`（新增）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/coupon/available` | 公开 | 可领取的优惠券列表（上架 + 在有效期 + 未领完） |
| GET | `/coupon/usable?amount=0` | 登录 | 我的有效未使用券（含 `shopId/shopName`，由前端按店铺与门槛筛选） |
| GET | `/coupon/mine` | 登录 | 我的优惠券（含已使用 / 未使用） |
| POST | `/coupon/claim/{id}` | 登录 | 领取优惠券（一人一张，原子递增 `claimed`；店铺券需店铺营业中） |
| GET | `/coupon/admin/list` | 管理员 / 商家 | 优惠券分页（支持 `keyword`；商家只看本店券） |
| POST | `/coupon/admin` | 管理员 / 商家 | 新建优惠券（商家强制本店；管理员可传 `shopId`，不传即全平台券） |
| PUT | `/coupon/admin/{id}` | 管理员 / 商家 | 修改优惠券（商家只能改本店券；适用范围创建后不可变更） |
| DELETE | `/coupon/admin/{id}` | 管理员 / 商家 | 删除优惠券（商家只能删本店券） |

### 优惠券对象

```jsonc
{
  "id": 2,
  "shopId": null,        // 为 null 时是全平台券；非空表示仅该店铺可用
  "shopName": null,      // 店铺名（列表展示用，平台券为 null）
  "name": "满 300 减 50",
  "description": "图书音像 / 美妆个护满减券",
  "threshold": 300.00,   // 满 threshold 元可用
  "discount": 50.00,     // 减免金额
  "total": 500,          // 发行总量
  "claimed": 12,         // 已领取
  "startAt": "2026-10-23T00:00:00",
  "endAt": "2026-11-22T00:00:00",
  "status": 1            // 1 上架 / 0 下架
}
```

### 我的优惠券对象（`/coupon/mine`）

```jsonc
{
  "id": 9,              // user_coupon 主键
  "couponId": 2,
  "name": "满 300 减 50",
  "threshold": 300.00,
  "discount": 50.00,
  "status": "UNUSED",   // UNUSED 未使用 / USED 已使用
  "startAt": "2026-10-23T00:00:00",
  "endAt": "2026-11-22T00:00:00",
  "usedAt": null,
  "claimTime": "2026-10-23T10:11:00"
}
```

> 下单时在 `POST /order/submit` 传入 `userCouponId` 即可抵扣；同请求内完成优惠券状态翻转与订单金额计算，保证并发安全。

---

## 十一、好友 `/friend`（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/friend/search?keyword=` | 搜索用户，**按当前身份自动限定范围**（USER↔USER、MERCHANT↔MERCHANT；ADMIN 与 MERCHANT 为系统默认好友）。受对方 `user_setting.searchable` 约束 |
| GET | `/friend/list` | 我的好友列表 |
| GET | `/friend/card/{id}` | 用户名片（邮箱 / 手机号 / 评价按对方隐私设置决定是否展示） |
| POST | `/friend` | 发送好友申请 `{ friendId }` |
| GET | `/friend/requests` | 收到的好友申请列表 |
| POST | `/friend/{requesterId}/accept` | 同意申请（写入反向好友行） |
| POST | `/friend/{requesterId}/reject` | 拒绝申请 |
| DELETE | `/friend/{id}` | 删除好友 |

> 申请-同意制：`friend.status = 0`（申请中）/ `1`（已同意）。同意后双方各持一条 `status=1` 记录。
> `ADMIN ↔ MERCHANT` 为系统默认好友，关系不落库（`isFriend` 直接判定为真）；`USER` 不能与 `MERCHANT` 互加好友。

## 十二、消息 `/message`（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/message/conversations?scene=FRIEND\|SERVICE` | 会话列表（按场景，含未读数、最后一条消息） |
| GET | `/message/history?peerId=&scene=` | 聊天记录（分页） |
| POST | `/message` | 发送消息 `{ toId, scene, type, content, targetId?, shopId? }` |
| POST | `/message/read` | 标记已读 `{ peerId, scene }` |
| POST | `/message/hide` | 删除会话（写隐藏点，非物理删除）`{ peerId, scene }` |
| GET | `/message/unread` | 未读数（分 `FRIEND` / `SERVICE` / `SYSTEM` 与 `total`） |
| GET | `/message/system?current=&size=` | 系统通知列表（查询时后端即标记已读） |
| GET | `/message/service-target?shopId=` | 客服会话的接收人（强制为店铺店主） |
| GET | `/message/peer?peerId=&scene=` | 会话对方资料（昵称 / 头像 / 店铺，用来补齐临时会话标题） |

**消息场景与发送权限**：

| 场景 | 含义 | 校验规则 |
|------|------|----------|
| `FRIEND` | 好友私聊 | 校验双方好友关系 |
| `SERVICE` | 买家咨询店铺客服 | 接收人**强制**为店铺店主，防止冒充客服 |
| `SYSTEM` | 系统 / 官方通知 | `from_id = 0`；**强制送达**，不受接收方「拒绝系统通知」开关限制 |

> 会话按 `conversation_key = 较小 userId:较大 userId:scene` 聚合；系统通知为 `SYS:{userId}`。
> 「删除会话」写入 `conversation_hide.last_message_id`，对方再有更大 id 的新消息时会话自动恢复。
> 官方（ADMIN）消息同样豁免对方接收开关；普通好友 / 客服消息则受 `user_setting` 中 `receive_friend_msg` / `receive_service_msg` 约束。

## 十三、举报 `/report`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/report` | 提交举报 `{ targetType: PRODUCT\|SHOP, targetId, type, content }` |
| GET | `/report/mine` | 我的举报列表（含处理状态与处理说明） |
| GET | `/report/admin/page` | 后台举报分页（后台身份；商家自动限定在本店，ADMIN 看全部） |
| GET | `/report/admin/pending` | 待处理举报数（侧边栏角标） |
| POST | `/report/admin/{id}/handle` | 处理举报 `{ status: ACCEPTED\|REJECTED, note }`，结果以系统通知回馈举报人 |

## 十四、隐私与消息设置 `/setting`（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/setting` | 获取当前设置（无记录时按默认值返回，不写库） |
| PUT | `/setting` | 更新设置 |

可设置字段：

| 字段 | 说明 |
|------|------|
| `searchable` | 允许被昵称 / 用户名 / ID 搜索到 |
| `friendPolicy` | `ALL`（所有人可加）/ `NOBODY`（不允许） |
| `showEmail` / `showPhone` / `showReviews` | 他人可见的隐私项 |
| `receiveFriendMsg` / `receiveServiceMsg` / `receiveSystemMsg` | 接收开关 |
| `notifySound` | 新消息提示音 |

> **例外**：官方（ADMIN）发送的消息与事务性系统通知（好友申请、举报结果）**豁免**上述接收开关，保证关键通知必达。

## 十五、后台用户管理 `/auth/admin/users`（平台级，仅 ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/admin/users` | 用户列表 |
| GET | `/auth/admin/users/page` | 用户分页（支持 keyword） |
| PUT | `/auth/admin/users/{id}/status` | 启用 / 禁用账号（禁用即踢下线） |
| DELETE | `/auth/admin/users/{id}` | 删除用户（级联清理订单 / 购物车 / 优惠券 / 收藏 / 评价 / 地址 / 足迹） |
| POST | `/auth/admin/users/{id}/reset` | 重置用户数据（账号资料恢复初始，**密码重置为 `123456`**） |
| PUT | `/auth/admin/users/{id}/role` | 调整角色（`USER` ↔ `MERCHANT`，设为管理员时自动创建店铺） |
| POST | `/auth/admin/users/{id}/notify` | ★ 以**平台官方**身份发送系统通知 `{ content }`（≤500 字，强制送达） |

> `notify` 接口用来「官方主动联系普通用户」：ADMIN 与 USER 不构成好友关系，无法通过好友消息触达，改由系统通知通道强制送达，不破坏好友模型。
