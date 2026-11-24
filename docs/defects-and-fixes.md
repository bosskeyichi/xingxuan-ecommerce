# 缺陷分析与修复清单

> 对 `ecommerce-system` 全量代码审计后发现的问题及修复记录。
> 前四类共 **33 项**，按严重程度分组：致命（P0）/ 严重（P1）/ 一般（P2）/ 优化（P3）。
> 第一节的 33 项为全量审计结论；其后各节为后续迭代整改（第二轮～第十一轮，共 10 轮），与审计项合计 70+ 项（第二十二节为功能增强，不计入整改轮次）。

---

## 一、总览

| 级别 | 数量 | 说明 |
|------|------|------|
| P0 致命 | 11 | 直接影响数据安全、资金正确性或可被越权利用 |
| P1 严重 | 9 | 业务逻辑错误，导致功能不可用或数据不一致 |
| P2 一般 | 8 | 代码质量、抗造、可维护性问题 |
| P3 优化 | 5 | 性能与体验优化 |

---

## 二、P0 致命缺陷

### P0-1 后台接口完全越权（安全）

**问题**：`WebConfig` 只注册了 `LoginInterceptor`（仅校验登录），所有 `/product/admin/**`、`/category/admin/**`、`/order/admin/**`、`/auth/admin/**` 接口对任何已登录用户开放。普通用户只需拿到自己的 token 即可增删改商品、删除用户、发货。

**修复**：新增 `interceptor/AdminInterceptor.java`，读取 `LoginInterceptor.ROLE` ThreadLocal，非 `ADMIN` 直接返回 HTTP 403 + `Result.error(403, "无权限操作，仅管理员可访问")`。在 `WebConfig` 中**按注册顺序**注册在 `LoginInterceptor` 之后。

**验证**：普通用户请求 `/admin/stats/overview` → HTTP 403；匿名请求 `/cart/list` → HTTP 401。

---

### P0-2 库存超卖（并发）

**问题**：`OrderServiceImpl.submit` 采用「先查库存 → 判断 → 再更新」的非原子流程：
```java
Product p = productMapper.selectById(...);          // ① 查询
if (p.getStock() < qty) throw ...                    // ② 判断
productMapper.updateById(p);                         // ③ 更新（覆盖式）
```
并发下两个请求同时读到 stock=1，均通过判断，最终库存被扣成 -1。

**修复**：改为单条**条件更新** SQL + 影响行数判断：
```java
LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
        .setSql("stock = stock - " + qty)
        .setSql("sales = sales + " + qty)
        .eq(Product::getId, v.getProductId())
        .ge(Product::getStock, qty);
if (productMapper.update(null, uw) == 0) {
    throw new BusinessException("商品 " + v.getProductName() + " 库存不足，请重新下单");
}
```

---

### P0-3 每次启动删除并重建数据库（数据安全）

**问题**：早期 `schema-h2.sql` 开头为 `DROP TABLE IF EXISTS ...`，配合 `spring.sql.init.mode: always`，**每次重启服务都会清空全部业务数据**。

**修复**：
- 建表改为 `CREATE TABLE IF NOT EXISTS`，索引内联到建表语句（`KEY` / `UNIQUE KEY`）
- 种子数据改为幂等写法：`INSERT INTO t(...) SELECT ... FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t WHERE ...)`
- 保留 `mode: always`，实现「既自动初始化，又不丢数据、不重复插入」
- 后续整体将数据库由 H2 迁移至 **MySQL 8.0**（`db/schema-mysql.sql` / `db/data-mysql.sql`），utf8mb4，彻底消除嵌入式数据库的并发与部署限制

---

### P0-4 数据库控制台对外暴露（安全）

**问题**：早期 H2 阶段 `application-dev.yml` 中 `spring.h2.console.settings.web-allow-others: true`，任意主机可通过 `/api/h2-console` 访问数据库控制台。

**修复**：改为 `false`；并在 `application.yml` 增加 `server.error.include-message: never` / `include-stacktrace: never`。后续迁移至 MySQL 后，数据库以独立服务形式部署、网络隔离，不再提供任何 Web 控制台，从架构层面消除该风险。

---

### P0-5 订单无法取消（逻辑错误）

**问题**：`OrderServiceImpl.cancel` 中判断 `!"PENDING_PAYMENT".equals(o.getStatus())` 后抛异常，但调用处拼装状态逻辑错误，导致待支付订单反而无法取消，且取消后**不回退库存**。

**修复**：
- 使用条件更新 `WHERE status = 'PENDING_PAYMENT'` 保证幂等
- 取消成功后回退库存、回退销量，并将商品**还原进购物车**
- 补充 `cancelledAt` 时间戳

---

### P0-6 购物车数量为负 / 无上限（业务）

**问题**：`/cart/add`、`/cart/quantity` 未校验数量，传入 `-1` 会导致库存被反向"增加"；传入极大值可占满库存。

**修复**：新增 `CartAddDTO` / `CartQuantityDTO` / `CartSelectedDTO`，使用 `jakarta.validation` 注解：
```java
@Min(value = 1, message = "购买数量必须大于 0")
@Max(value = 999, message = "单件商品最多购买 999 件")
private Integer quantity;
```
并在 `CartServiceImpl` 内做二次 `checkQuantity()` 兜底。

---

### P0-7 订单号并发碰撞

**问题**：`genOrderNo()` 用 `yyyyMMddHHmmss` + 6 位随机数，`order_no` 有唯一约束，高并发下碰撞导致 500。

**修复**：改为 `ORD + 时间戳 + UUID 前 8 位`：
```java
return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
```

---

### P0-8 500 异常泄露内部信息（安全）

**问题**：`GlobalExceptionHandler.handleAll` 直接 `Result.error(500, e.getMessage())`，把 SQL 语句、表结构、堆栈片段返回给前端。

**修复**：只记录日志，返回通用文案「服务器开小差了，请稍后再试」，并通过 `ResponseEntity.status(500)` 返回真实 HTTP 状态码。

---

### P0-9 商品删除破坏历史订单

**问题**：`ProductServiceImpl.delete` 直接物理删除，若该商品已被下单，`order_item` 中的商品记录变成孤儿数据（外键丢失、无法展示）。

**修复（最终版）**：删除 = **逻辑删除**（`productMapper.deleteById`），商品同时从管理端与用户端消失；`order_item` 保存商品名称 / 价格快照，历史订单不受影响；删除时级联清理 `cart` / `favorite` / `browse_history` / `review`（含追评与屏蔽记录）。「下架」是另一个独立动作（`status = 0`，商品仍保留在管理端列表）。早期曾用「有订单就转下架」的过渡方案，已被上述方案取代。

---

### P0-10 逻辑删除表上的唯一索引冲突

**问题**：本次为购物车新增了 `UNIQUE(user_id, product_id, deleted)` 索引。由于 MyBatis-Plus 逻辑删除是 `UPDATE ... SET deleted = 1`，同一用户重复删除再添加同一商品时，会与已存在的 `deleted=1` 行冲突（H2 不支持部分索引 `WHERE deleted=0`），导致 500。

**修复**：先移除含 `deleted` 列的唯一索引（改为普通索引 `idx_cart_user`，唯一性由 Service 层 `selectOne` 保证）以解决 H2 下的冲突；**迁移 MySQL 8.0 后**在 `cart` 上恢复 `UNIQUE(user_id, product_id)`（`uk_cart`），并把「逻辑删除 + 重新加购」改成 `CartMapper.restoreDeleted` 复用原行（把 `deleted` 置回 0），既保住唯一约束又不产生 500。索引统一内联到 `CREATE TABLE`，不再使用 `DROP INDEX IF EXISTS` 迁移语句。

---

### P0-11 自我评价/删除他人评价越权

**问题**：评价删除未校验归属。

**修复**：`ReviewServiceImpl.delete` 校验 `r.getUserId().equals(userId) || ROLE == ADMIN`，否则抛「无权删除他人评价」。

---

## 三、P1 严重缺陷

| 编号 | 问题 | 修复 |
|------|------|------|
| P1-1 | 统计"今日销售额 / 总销售额"把已取消订单也算进去 | 改为按 `paid_at` 统计，且仅统计 `PAID / SHIPPED / COMPLETED` |
| P1-2 | 订单状态流转非幂等，重复点击"支付/发货"会重复更新时间 | 全部改为条件更新 `WHERE status = ?` + 影响行数判断 |
| P1-3 | 订单列表 N+1 查询（每单单独查明细） | 批量 `selectList(in orderIds)` + `Collectors.groupingBy` 组装 |
| P1-4 | 商品删除后分类空转；分类删除不检查是否挂靠商品 | 分类删除前检查商品数，有则拒绝并提示数量 |
| P1-5 | 搜索关键词未清洗，`%` 可匹配全表；且 `OR` 优先级错误导致分类筛选失效 | 清洗 `%`/`_`/`\` 并限长 50；`OR` 条件用 `q.and(w -> ...)` 加括号 |
| P1-6 | 订单取消 / 超时后库存长期被占用 | 新增 `OrderTimeoutTask` 定时任务（默认 30 分钟超时，每 5 分钟扫描） |
| P1-7 | 用户禁用后 token 在有效期内仍可用 | `updateStatus(0)` 时主动 `sessionStore.remove(key)` 踢下线 |
| P1-8 | 修改密码后旧 token 仍有效 | 修改密码后清除 session，强制重新登录（前端同步跳登录页） |
| P1-9 | 管理员可禁用/删除自己，或删光所有管理员导致后台失去入口 | 增加自保护：不能改自己状态、不能删自己、不能删管理员、至少保留 1 个启用管理员 |

---

## 四、P2 一般问题

| 编号 | 问题 | 修复 |
|------|------|------|
| P2-1 | `LoginInterceptor` 重复解析 JWT 三次（`getUserId`/`getUsername`/`getRole` 各调 `parse`） | 一次 `parse` 得到 `Claims`，新增 `getUserId(Claims)` / `getRole(Claims)` 重载 |
| P2-2 | 未登录返回业务码 401 但 HTTP 状态仍是 200 | 改为 `response.setStatus(401)`，语义正确 |
| P2-3 | `BusinessException` 默认 code 为 500，业务校验失败被误判为服务器错误 | 默认改为 400 |
| P2-4 | `CartServiceImpl` 大量 NPE 风险（`c.getQuantity()`、`p.getStock()` 未判空） | 统一 `nvl()` 判空处理 |
| P2-5 | 购物车 `selected` 可传任意值 | 新增 `CartSelectedDTO` 限制只能为 0/1 |
| P2-6 | 注册并发下用户名重复导致 500 | 捕获 `DuplicateKeyException` → 返回「用户名已存在」 |
| P2-7 | `ProductServiceImpl.save/update` 未加事务、未校验分类存在性 | 增加 `@Transactional` 与分类存在性校验 |
| P2-8 | `PasswordUtil` 含 `main` 方法（生产代码不应有测试入口） | 移除 |

---

## 五、P3 优化项

| 编号 | 优化 | 说明 |
|------|------|------|
| P3-1 | 分页大小无上限，`size=99999` 可致 OOM | `PaginationInnerInterceptor.setMaxLimit(100)` + 参数 `@Max(100)` |
| P3-2 | 用户管理无分页，用户量大时全表返回 | 新增 `/auth/admin/users/page` 分页接口 |
| P3-3 | 前台商品列表会返回已下架商品 | 新增 `/product/admin/list`（后台全部状态）与 `/product/list`（前台默认仅上架） |
| P3-4 | 订单列表不显示下单用户名 | `OrderVO` 增加 `username`，批量查询用户后填充 |
| P3-5 | 所有查询接口缺少参数校验 | Controller 增加 `@Validated` + `@Min/@Max/@Valid` |

---

## 六、前端功能 Bug 修复

| 编号 | 问题 | 修复 |
|------|------|------|
| F-1 | 用户端顶栏显示用户名用了 `userStore.user?.nickname`，但 store 中实际字段是 `userInfo`，导致**永远显示游客** | 改为 `userStore.userInfo?.nickname` |
| F-2 | `CartView` 中 `selectedRows` 被定义为 `ref([])` 且从未赋值，"去结算"按钮**永远禁用** | 改为 `computed`：`items.value.filter(r => r.selected === 1)` |
| F-3 | 管理端 `AdminLayout` 调用 `authApi.logout()`，但 `authApi` 中**没有定义 logout**，抛 TypeError | `api/index.js` 中补充 `logout` |
| F-4 | 管理端 `request.js` 中文提示为 GBK 乱码 | 重新以 UTF-8 写入 |
| F-5 | 用户端无全局搜索入口 | 顶栏新增搜索框，回车跳转 `/search?keyword=` |
| F-6 | 商品图片加载失败显示破图 | `ProductCard` 增加 `imgError` 状态 + 渐变首字母占位图 |
| F-7 | 后端重启后内存 Session 失效，管理端/用户端对 HTTP 401 只弹「Request failed with status code 401」红色报错，页面卡死且不清除本地凭据 | 两个前端 `request.js` 错误拦截器统一处理：HTTP 401 → 清除本地 token/用户信息 → 提示并跳转登录页（管理端别让在 /login 页循环跳转） |

---

## 七、新增功能清单

| 功能 | 后端 | 前端 |
|------|------|------|
| 商品收藏 | `Favorite` 实体 / `FavoriteService` / `FavoriteController` | `FavoritesView`、商品卡片收藏星标、头像面板「我的收藏」唯一入口 |
| 商品评价 | `Review` 实体 / `ReviewService` / `ReviewController` | 商品详情 Tab「商品评价」、星级评分、评分分布条、发表评价 |
| 确认收货 | `OrderService.confirmReceipt` + `/order/confirm/{no}` | 订单列表与详情页「确认收货」按钮 |
| 订单超时自动取消 | `OrderTimeoutTask`（`@Scheduled`） + `OrderService.autoCancelExpired` | 结算页与购物车提示文案 |
| 个人资料编辑 | `PUT /auth/profile` | 个人中心「编辑资料」Tab |
| 修改密码 | `PUT /auth/password` | 个人中心「修改密码」Tab |
| 销售趋势图 | `GET /admin/stats/trend?days=7` | Dashboard 纯 CSS 柱状图 |
| 热销排行 | `GET /admin/stats/top-products` | Dashboard TOP 6 排行条 |
| 库存预警 | `GET /admin/stats/low-stock` | Dashboard 预警列表 |
| 热销商品 | `GET /product/hot` | （接口已就绪，供首页推荐位使用） |
| 后台用户分页 | `GET /auth/admin/users/page` | 用户管理分页 + 关键词搜索 |
| 收货地址簿 | `address` 表 / `AddressService` / `AddressController`（list/add/update/remove/default） | 个人中心「收货地址」Tab（增删改查、设默认）；结算页地址卡片直选 + 快捷新增 |
| 商品筛选排序 | `/product/list`、`/product/search` 增加 `minPrice` `maxPrice` `sort` 参数（排序白名单校验） | 首页筛选栏 5 档排序（综合/销量/价格升降/新品）+ 价格区间 + 仅看有货 + 已选条件标签 |
| 种子数据扩充 | `db/data-mysql.sql` 商品 15 → 100 件（6 分类），新增 3 条示例地址、4 张优惠券 | — |
| 优惠券功能（新增） | `coupon` / `user_coupon` 实体与 Mapper、`CouponService` / `CouponController`、下单与取消时的原子抵扣与回退 | 「优惠券中心」领取 / 我的优惠券、结算页优惠券选择、管理端「优惠券」发放与列表 |
| 商品对比（新增） | — | 商品卡片 / 详情「对比」按钮、全局对比栏（最多 4 件）、独立对比页横向参数对照 |
| 搜索历史与热门词（新增） | — | 顶栏搜索聚焦弹出历史 + 热词，一键复用 |
| 订单 CSV 导出（新增） | `OrderService.exportCsv`（UTF-8 BOM）、`GET /order/admin/export` | 管理端订单页「导出 CSV」按钮 |

---

## 八、回归验证结果

在干净数据库上跑端到端回归，全部通过：

```
T1  管理员/普通用户登录                        ✅ role=ADMIN / role=USER
T2  /product/hot 匿名可访问                    ✅ HTTP 200
T3  购物车数量越界拦截（99999）                ✅ 400 quantity: 单件商品最多购买 999 件
T4  商品参数校验（空名称/负价格/负库存）        ✅ 400 stock: 库存不能为负数
T5  分页大小上限（size=99999）                 ✅ 400 list.size: 最大不能超过 100
T6  管理员禁用自己                             ✅ 400 不能修改自己的账号状态
T7  删除挂靠商品的分类                         ✅ 400 该分类下还有 3 件商品
T8  重复评价                                   ✅ 400 您已评价过该商品
T9  未购买评价                                 ✅ 400 请购买并支付后再评价该商品
T10 提交订单                                   ✅ 库存 1000 → 998
T11 取消订单                                   ✅ 库存 998 → 1000（回退 +2）
T12 取消后购物车还原                           ✅ 1 项，数量 2
T13 模拟支付                                   ✅ 状态 → PAID
T14 管理员发货 + 用户确认收货                  ✅ 状态 → COMPLETED，写入 completedAt
T15 购买后发表评价                             ✅ 200 评价已发布
T16 评分汇总                                   ✅ count=1 avg=5.0，分布 {5:1,4:0,3:0,2:0,1:0}
T17 修改密码                                   ✅ 200 密码已修改，请重新登录
T18 改密后旧 token                             ✅ 401 Session expired
T19 非管理员访问后台接口                       ✅ HTTP 403
T20 匿名访问需登录接口                         ✅ HTTP 401
T21 过期待支付订单自动取消                     ✅ 定时任务回退库存
T22 领取优惠券                               ✅ claimed 0 → 1，一人一张
T23 结算使用优惠券                           ✅ 8999 − 20 = 8979，券状态 USED
T24 取消订单后优惠券回退                     ✅ 券恢复 UNUSED，库存/购物车同步回退
T25 取消后重新加购同一商品                   ✅ 复用购物车行，数量累加，无唯一键冲突
T26 订单 CSV 导出                            ✅ text/csv + UTF-8 BOM，含优惠与实付列
```

---

## 九、这轮联调补充修复（MySQL / 优惠券）

在真实 MySQL 8.0 上联调时又发现并修复了以下问题，均已回归验证：

| 编号 | 问题 | 影响 | 修复 |
|------|------|------|------|
| N-1 | `StatsServiceImpl` 引用未定义变量 `couponClaimed` | 后端编译失败，服务无法启动 | 改为 `couponUsed` |
| N-2 | 分页插件方言仍为 `DbType.H2` | MySQL 下分页 SQL 生成错误 | 改为 `DbType.MYSQL` |
| N-3 | 订单 `totalAmount` 存的是优惠后金额，而前端 / 导出按 `totalAmount - discountAmount` 计算 | 实付金额被重复扣减，展示错误 | `totalAmount` 存优惠前商品总额，销售额统计按实付（总额 − 优惠）汇总 |
| N-4 | 优惠券核销为「先查后改」 | 并发下同一张券可能被两个订单使用 | 改为条件更新 `WHERE id=? AND status='UNUSED'` + 影响行数判断 |
| N-5 | 购物车 `UNIQUE(user_id, product_id)` 与逻辑删除冲突 | 取消订单还原购物车 / 再次加购同一商品时触发唯一键冲突（400 数据已存在） | 新增 `CartMapper.restoreDeleted`，优先复用 `deleted=1` 的行；取消还原同样复用 |
| N-6 | 取消订单时用 `updateById` 置空 `used_at` / `order_id` 失效 | 券回退后仍残留旧订单号与使用时间 | 改为显式 `LambdaUpdateWrapper.set(..., null)` 条件更新 |

> 上述修复使「领券 → 下单抵扣 → 取消退券 → 再次加购 → 重新下单」全链路在 MySQL 上跑通。

---

## 十、体验与合理性整改（对照淘宝 / 京东 / 拼多多）

| 编号 | 不合理点 | 表现 | 整改 |
|------|----------|------|------|
| U-1 | 排序只有 4 个固定按钮且小屏被隐藏 | 缺少「最新上架」；≤1080px 时整块 `display:none`，手机端无法排序 | 扩为 5 个排序（综合 / 销量 / 价格↑ / 价格↓ / 新品）并新增「仅看有货」；小屏不再隐藏，改为换行栅格（≤760px 3 列） |
| U-2 | 首页分页参数写成 `page` | 后端接收的是 `current`，导致**翻页永远返回第 1 页** | 统一为 `current`，实测第 2 页返回不同商品 |
| U-3 | 分类页点击后跳 `/?categoryId=x`，但首页从不读取该参数 | 点分类回到首页仍展示全部商品 | 首页读取并监听 `categoryId`，分类切换同步写入 URL，可分享/刷新保持 |
| U-4 | 首页「猜你喜欢」调用 `footprint.getTopProducts()` | 该方法不存在，控制台报 `is not a function`，推荐区永不显示 | 新增后端 `/product/recommend`（按浏览分类聚合热销），前端改为接口取数并排除已浏览 |
| U-5 | 新增推荐接口未加入公开白名单 | 匿名用户访问首页时该请求 401，连带触发退出登录并跳转 | `WebConfig` 公开路径补充 `/product/recommend` |
| U-6 | 页脚「管理后台」写死 `http://localhost:8080/admin` | 局域网/开发环境点击打不开 | 先改为按主机推断地址；**这轮进一步整改：用户端不再保留任何后台入口**（见第十三节 R-1） |
| U-7 | 搜索只匹配商品名/描述 | 搜「手机」返回 0 条（商品名里没有「手机」二字） | 关键词同时匹配**分类名**，搜「手机」命中手机数码分类 17 件 |
| U-8 | 商品卡片无库存状态 | 售罄商品仍可「add +」，下单才报错 | 售罄显示「已售罄」遮罩并禁用按钮；库存 ≤10 显示「仅剩 N 件」 |
| U-9 | 详情页缺少服务承诺 | 与主流电商差距明显 | 增加「正品保障 / 7 天无理由退换 / 48 小时内发货 / 售后无忧」服务条 |

> 上述改动已用无头 Edge 实测：排序、仅看有货、条件标签、翻页、分类直达、猜你喜欢均正常，控制台无报错。

---

## 十一、界面一致性与入口去重整改（这轮）

对照「同一功能出现多个入口、影响使用」的问题，逐个模块做了清理，并补齐跨端一致性：

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| D-1 | 收藏入口重复 | 顶栏导航「收藏」、头像面板「我的收藏」、个人中心侧栏「我的收藏」三处并列 | **只保留头像面板唯一入口**；删除顶栏导航项与个人中心侧栏跳转（侧栏仅留资料概览 / 编辑资料 / 修改密码 / 收货地址） |
| D-2 | 优惠券入口重复 | 顶栏「优惠券」+ 头像面板「优惠券中心」+ 个人中心侧栏 + 页脚 | 统一收进头像面板「优惠券中心」，顶栏与侧栏不再罗列 |
| D-3 | 账户类跳转混在业务侧栏 | 个人中心侧栏混入订单 / 收藏 / 券 / 足迹，与资料类页签语义不同 | 侧栏只承载「资料类」页签，账户跳转归头像面板，语义边界清晰 |
| D-4 | 顶部导航与「全部分类」重复 | 顶栏一排导航 + 分类悬层并列，占用高度且路径重复 | 移除导航条，顶栏固定为「品牌 / 全部分类 / 搜索 / 购物车 / 头像」，吸顶高度 98px，不跳动 |
| D-5 | 历史路由 `/products` 与首页重复 | 「全部商品」既指 `/products` 又指首页商品流，返回/分享行为不一致 | 保留 `/products` 为兼容重定向（`redirect → /`，query 透传），站内链接统一指向 `/` |
| D-6 | 对比页加购不刷新角标 | 对比页加购后顶栏购物车角标不更新 | 统一派发 `cart:refresh` 事件，与卡片加购同一通路 |
| D-7 | 小屏顶栏横向溢出 | 视口 ≤900px 时搜索框与「注册」按钮被裁切，页面出现横向滚动 | 顶栏改 **grid 两行**（品牌 + 操作区 / 搜索整行），搜索组合框 `min-width:0`；≤640px 隐藏文字「登录」与昵称，仅留头像 |
| D-8 | 轮播图切换发虚 | `visibility` 立即生效导致过渡期间两张图叠加发灰 | 过渡改为 `opacity + visibility` 同步 600ms，切换干净 |
| D-9 | 管理端在网关下白屏 | 构建未带 `--base=/admin/`，`/admin/` 下资源 404、子路由刷新 404 | Dockerfile 构建加 `--base=/admin/`，路由 `createWebHistory(import.meta.env.BASE_URL)` |
| D-10 | Docker MySQL 与本机 MySQL 端口冲突 | compose 映射 `3306:3306`，本机已装 MySQL 时启动失败 | 改为 `3307:3306`，与后端默认 `MYSQL_PORT=3307` 一致；文档同步 |
| D-11 | 点分类后页面毫无反应 | 点轮播「看看办公好物」、左侧分类目录、顶部「全部分类」后，列表其实已按分类过滤，但视图停在首屏，用户以为没跳转 | 切换分类后**立即定位到商品区**（扣除吸顶高度，别让被顶栏盖住），并显示金色提示条「已切换到「电脑办公」，下面是 17 件该分类的选品」+ 主栏红边闪一下；**重复点击同一分类也会滚动**，保证「点了就有反应」 |
| D-14 | 点分类必须点两次才跳 | 首次点击只高亮、不滚动；再点一次才滚到商品区 | 根因是 `router` 的 `scrollBehavior: () => ({ top: 0 })`：切分类会 `router.replace(?categoryId=)`，导航完成后把刚做完的滚动重置回顶部。改为**同路径且仅 `categoryId` 变化时返回 `false`**（不干预滚动，交给页面处理），并在点击瞬间先定位一次、接口返回后再按最新布局对齐一次 |
| D-12 | 吸顶高度变量与实际不符 | `--header-h: 71px`，而吸顶头实际 98px（含热搜行），分类定位与粘性栏都有 27px 偏差 | 变量修正为 `98px`，筛选栏 / 详情信息栏 / 分类定位统一对齐 |
| D-13 | 登录页展示测试账号 | 登录 / 注册页出现「测试账号 alice / admin + 一键填入」提示块，观感像测试环境 | 用户端与管理端登录页**移除该提示**及配套脚本与样式；账号信息只保留在 README / quick-start / PPT 等文档中 |

> 验证：`frontend-user` / `frontend-admin` 均 `npm run build` 通过；用户端在 390 / 768 / 1280 / 1440 四档宽度下逐页截图检查，无横向滚动、无控制台报错；分类定位用「打开 `/?categoryId=2` 后测量 `.feed` 距顶 110px、提示条完整可见」的方式验证通过。

---

## 十二、去同质化：品牌化改造（这轮）

问题：第一版视觉基本照搬京东 / 淘宝的「红白 + 秒杀 + 猜你喜欢」组合，虽然好用但缺少自己的气质，对外展示时容易被认为只是换色的模板。

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| B-1 | 首页与平台电商高度同构 | 红色顶栏 + 限时秒杀 + 猜你喜欢，与京东首页几乎一一对应 | 定位明确为「买手严选店」：暖纸底 `#f7f5f1` + 白卡，朱砂红 `#d8342a` 只用来价格与行动点，不再满屏红 |
| B-2 | 缺少自有品牌识别色 | 全站只有红与灰，和平台无差异 | 新增**星选金** `#a87a3c`：承担商品编号、区块星标、英文栏目标签，形成识别层 |
| B-3 | 没有自有栏目 | 全是「限时特惠 / 新品 / 猜你喜欢」等通用模块 | 新增**买手笔记**：`★ 买手笔记 Editor's Note`，三张编辑卡（NOTE 01–03 + 推荐语 + 署名 + 商品价）；**这轮复审认为推荐语与具体商品无关联，已整体删除**（见 R-5） |
| B-4 | 区块标题缺少节奏 | 各区块只有中文标题 | 标题统一为「金色四角星 + 中文衬线标题 + 英文小标」（Editor's Note / Flash / All Picks / New In / For You） |
| B-5 | 商品卡与平台同构 | 名称 + 价格 + 销量三段式 | 增加左上角 `NO.xxx` 选品编号与「一句话卖点」（取商品 description），底部改为「已售 N 件 · 库存 M」 |
| B-6 | 文案是通用承诺 | 正品保障 / 全国包邮 / 专属客服 | 改为店铺主张：逐件验货、一次只推 100 件、现货 48h 发出、买手答疑；通告条、服务保障、页脚、详情页四处统一 |
| B-7 | 详情页与店铺语言脱节 | 只有分类标签与通用服务行 | 分类旁增加金色 `NO.xxx` 编号，服务行同步为「逐件验货 / 7 天无理由 / 48h 发货 / 买手答疑」 |
| B-8 | 管理端与用户端色系不一致 | 用户端红白、管理端墨绿 | 管理端主色同步为朱砂红 `#d8342a`，底色转暖，两端同一套令牌 |

> 验证：`frontend-user` / `frontend-admin` 构建通过；1280 宽下首页（含闪购、商品流）、商品详情、搜索结果逐页截图核对，卡片编号、卖点行、星级标题均正常渲染，控制台无报错。

---

## 十三、角色边界与体验限定（2026-10 第二轮）

复查范围：用户端全部 16 个页面、管理端 9 个页面、网关配置与 API 分层。

### 13.1 角色边界与分层

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-1 | 用户端混入后台入口 | 页脚导航与顶部通告条各有「商家后台」外链，普通用户点击会进入管理端登录页 | **两处入口全部删除**；页脚改为单行信息栏（品牌 / 站点导航 / 站点说明），页脚链接只剩首页、全部分类、商品对比、购物车 |
| R-2 | 用户端 API 层越界 | `orderApi.exportCsv`（`/order/admin/export`）与整块 `adminCouponApi` 留在用户端，虽无调用但可被误用 | 从 `frontend-user/src/api/index.js` 删除，管理员接口只保留在管理端 API 层 |
| R-3 | 用户端出现实现细节文案 | 登录页「JWT 认证 · BCrypt 密码加密」「库存原子扣减」，注册页「BCrypt 加盐哈希」，个人中心「密码使用 BCrypt 存储」，优惠券页「运营会在后台发放」 | 全部改为用户视角话术（精选好物 / 七天无理由 / 订单可追踪 / 新人券）；商品详情删除「示例数据说明」段落，保留商品编号、库存锁定与退换政策 |
| R-4 | 订单进度用后台口吻 | 用户端步骤条写「商家发货」，像是运营视角 | 改为「已发货」，与订单状态 `SHIPPED` 的用户语义一致 |

### 13.2 页面冗长与繁琐

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-5 | 首页 7 个区块偏长 | 「买手笔记」三张卡的推荐语套用在任意热销商品上，「服务保障」四项与顶部承诺条 + `Our Way` 卡三处重复 | 删除 `notes` 与 `service-rail` 两块及其样式，首页限定为 5 块（分类+轮播 / 闪购 / 筛选商品流 / 新品+主张 / 为你挑选） |
| R-6 | 闪购进度条是估算值 | `soldRate` 在无销量无库存时固定返回 60%，进度条恒长 | 改为 `sales/(sales+stock)` 真实占比，无数据显示 0，去掉 8%/99% 的人为夹取 |
| R-7 | 首页分类条目显示英文标识 | 分类接口 `icon` 字段是 `phone/computer/home` 等 slug，左侧目录直接渲染成英文 | 改为统一的 `›` 指示符，与「全部」条目视觉一致 |
| R-8 | 页脚四栏是空壳 | 企业官网式的品牌 / 指南 / 账户 / 承诺四栏，多数链接无落点 | 改为单行信息栏；响应式在 1080px 收紧间距、640px 纵向堆叠 |

### 13.3 加载态、容错与一致性

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-9 | 订单列表无加载态 | 首屏先闪「还没有订单」，接口返回后才出现订单 | 新增 `loading` + `v-loading`，空态判断加 `!loading` 前置 |
| R-10 | 订单详情失败即白屏 | `v-if="order"` 且无 catch，404/异常时整页空白 | `load()` 加 try/catch/finally，失败显示「订单不存在或无权查看」+ 返回列表按钮 |
| R-11 | 结算页首屏是空表单 | `load()` 无 try/catch、模板无 loading，进入页先看到空地址与空清单 | 增加 `pageLoading` + `v-loading`，`cartApi.list()` 失败兜底为空清单 |
| R-12 | 搜索/首页请求失败残留旧数据 | `try/finally` 无 `catch`，失败后仍显示上一次结果 | 补 `catch` 清空列表与总数，空态文案保持一致 |
| R-13 | 手机号校验两套标准 | 结算页 `^1\d{10}$`、地址簿与个人中心 `^1[3-9]\d{9}$`，同一号码两种结论 | 统一为 `^1[3-9]\d{9}$` |
| R-14 | 表单校验失败产生未捕获异常 | `await formRef.validate()` 在管理端登录、商品弹窗、结算提交处直接 await | 改为 `validate().catch(() => false)` 守卫，校验失败静默返回 |

### 13.4 管理端缺陷

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-15 | 发货按钮与占位符并存 | `v-else` 挂在第二个 `v-if` 上，已支付订单同时显示「发货」和「—」 | 改为 `v-if / v-else-if / v-else` 三态 |
| R-16 | 优惠券页样式类不存在 | 使用 `page-sub` / `panel`，`main.css` 只有 `page-desc` / `card`；标题用 `h2` 而其他页是 `h1` | 统一为 `page-desc` / `card` / `h1`，与商品、订单页一致 |
| R-17 | 优惠券表单无校验 | 可提交空名称、「减免 > 门槛」、负数金额 | 保存前校验名称非空、减免 > 0、减免 < 门槛 |
| R-18 | 删除当前页最后一条停在空白页 | 商品 / 用户删除后未回退页码 | 删除后若当前页仅剩 1 条且页码 > 1，先回退一页 |
| R-19 | 商品状态筛选清空后 400 | `el-select` 清空得到 `''`，直传 `@RequestParam Integer status` 会类型转换失败 | 空字符串与 null 一并转为 `undefined` |
| R-20 | 概览页无加载态 | `loading` 声明未使用，请求期间指标卡显示 0 | 根节点加 `v-loading="loading"` |
| R-21 | 「访问用户端」地址写死 5173 | 网关部署（同域 + `/admin/`）下点击跳到不存在的端口 | 开发态（`:5174`）推断 5173，其他情况回根路径 `/`；文案改为「查看商城前台」 |

### 13.5 网关与部署细节

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-22 | `/admin` 无尾斜杠落到用户端 | `location /admin/` 只匹配带斜杠路径，访问 `http://host:8080/admin` 返回商城首页 | 增加 `location = /admin { return 301 /admin/; }` |
| R-23 | CORS 头重复下发 | Nginx `add_header ACAO *` 与后端 `WebConfig` 同时写响应头，`*` 与 `allowCredentials` 冲突 | 删除网关 CORS 头，由后端统一签发；同域部署不触发跨域 |
| R-24 | 网关注释夸大架构 | 注释写「微服务网关」，实际是模块化单体的统一入口 | 注释改为「Spring Boot 模块化单体，可水平扩展为多实例」，与 `architecture.md` 口径一致 |

> 验证：两端 `npm run build` 均通过（`frontend-user` 6.5s / `frontend-admin` 6.1s）；用户端源码全文检索「商家后台 / 管理后台 / BCrypt / JWT」已无命中（判断项仅保留在文档中）。构建产物（`dist`、`backend/target`）属可再生文件，已从仓库移除。

---

## 十四、管理端对齐用户端（2026-10 第三轮）

起因：用户反馈两点，① 模拟支付后，管理端看不到「让订单完成」的按钮；② 管理端与用户端在配色、图标、标题风格上像两套系统。

### 14.1 订单操作按钮消失（根因不是模板，是布局）

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-25 | 订单表列宽超出容器 | 8 列合计约 1280px，而 1080 窗口的内容区仅约 830px；`fixed="right"` 的操作列被挤压/覆盖，视觉上「操作」只剩一个「—」 | 列宽重排为 6 列（订单号+时间 / 用户 / 商品 / 实付 / 状态 / 操作），合计 ≈ 800px；收货人信息移入详情抽屉 |
| R-26 | 文字按钮白底白字 | 全局 `.el-button--primary` 用 `!important` 把文字变白，而 `.is-text` 又把背景设透明，导致「详情 / 编辑 / 删除」等文字按钮完全不可见 | 补 `.el-button--primary.is-text`、`.el-button--success.is-text` 的显式文字色与 hover 底色 |
| R-27 | 操作列按钮竖排 | 单元格内两个文字按钮宽度溢出，自动换行堆叠 | 统一 `.table-actions` 横排容器（`display:flex` + 去默认 margin） |

### 14.2 订单流转补全

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-28 | 看不到订单详情 | 列表只有金额和状态，无法核对商品、收货信息与时间 | 新增右侧详情抽屉：商品清单（封面/单价/数量/小计）、收货信息、金额明细、四段进度时间线 |
| R-29 | 详情底部操作需滚动才可见 | 内容较长时「发货 / 标记完成」被推到滚动区底部 | 操作区改用抽屉 `footer` 插槽，常驻底部；左侧提示「下一步该做什么」 |
| R-30 | 状态筛选只能靠下拉 | 无法一眼看到「待发货有几单」，也无法从概览直达 | 改为带数量的状态标签页（全部 / 待支付 / 待发货 / 已发货 / 已完成 / 已取消），并支持 `?status=PAID` 深链 |
| R-31 | 待发货无提醒 | 运营必须手动翻列表才发现有新支付订单 | 侧边栏「订单管理」增加待发货角标，切页与 60s 定时刷新；概览页状态卡可点击直达对应筛选 |
| R-32 | 待支付订单无说明 | 只有一条灰线，不知道下一步 | 行内显示「等待支付」禁用按钮，详情提示「30 分钟后自动取消」 |

### 14.3 视觉与用户端统一

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-33 | 主色不一致 | 用户端主按钮朱砂红，管理端主按钮却是墨黑 | `.el-button--primary` 统一为 `#d8342a`，hover 深红；金额色同步为红 |
| R-34 | Logo 无品牌感 | 管理端侧栏是灰白细线方块「星」 | 改为朱砂红方块 + 白字「星」，带红色投影，与用户端 `brand-mark` 同款 |
| R-35 | 导航激活态是白底 | 与用户端红色行动点无关联 | 激活项改朱砂红底 + 白字，保留可读性 |
| R-36 | 标题缺少品牌语汇 | 管理端标题是纯黑字，用户端是衬线标题 + 金色四角星 | `.page-title` / `.topbar-title` 增加金色四角星前缀，面包屑改金色大写 |
| R-37 | 优惠券页像半成品 | 主按钮掉到说明文字下方、查询是白按钮、状态用 `el-tag`、无进度显示 | 页头改 flex 两端对齐；查询改主按钮并补重置；状态改药丸；新增领取进度条；表格入卡并带空态 |
| R-38 | 商品上下架要点开弹窗 | 每次改状态都要开弹窗、填表单 | 列表内直接 switch 上下架（失败回滚并刷新） |
| R-39 | 用户头像不显示 | 用户端已支持上传头像，管理端只显示首字 | 有 `avatar` 时渲染头像，无头像回退首字圆形块 |
| R-40 | 分类图标是内部 slug | 列表直接显示 `phone / computer / home` | 显示「手机 / 电脑 / 家电」等可读名，slug 作为副行小字 |
| R-41 | 概览 5 张卡换行 | 第 5 张「优惠券」独占第二行 | 限定为 4 张 KPI，优惠券数据并入标题说明；订单状态卡改自适应网格并支持点击跳转 |
| R-42 | 登录页文案偏技术 | 写着 `AdminInterceptor` 校验角色 | 改为运营视角的三条能力描述；装饰大字由「鑒」改为「星」 |

### 14.4 种子数据清洗

| 编号 | 问题 | 表现 | 整改 |
|------|------|------|------|
| R-43 | 商品重名 | 「优衣库摇粒绒外套」「阿迪达斯 Ultraboost」「雅诗兰黛小棕瓶」各出现两次 | 后 3 件改为「立领外套 / Ultraboost Light / 100ml 双支装」，保留 100 个 SKU |
| R-44 | 分类错配 | 蓝牙音箱被归到「图书音像」 | 改到「手机数码」；各分类数量变为 18/17/17/17/16/15 |
| R-45 | 名称错误 | 「新 balance 574」「Herman Miller Aeron 坐椅」 | 改为「New Balance 574」「Herman Miller Aeron 人体工学椅」 |

> 验证方式（浏览器实测，1280×720）：管理端登录 → 订单管理 → 待发货 1 → 「发货」确认 → 用户端订单变「已发货」→ 详情抽屉「标记完成」→ 状态变「已完成」，角标与标签页数量同步为 0/6；商品列表 switch 上下架、优惠券页新增/编辑、分类页均正常；`npm run build` 通过（16.3s），无控制台报错。
>
> 说明：管理端 `public/img` 下 6 张分类图与 4 个 SVG 兜底图从未被引用（分类用文字头像），已随这轮清理移除；用户端同名文件仍在使用（商品无封面时的兜底），保留。

---

## 十五、金额显示精度（2026-10 第四轮）

起因：用户反馈使用优惠券后，订单/结算页金额出现一长串浮点数字（如 `169.09999999999997`）。

### 15.1 根因

后端用 `BigDecimal` 计算并存储 `totalAmount` / `discountAmount`，金额本身是正确的；问题出在**前端展示层**，Vue 模板里直接做 `totalAmount - discountAmount` 这类减法，而 JS 的 `number` 是双精度浮点，`199.1 - 30` 会得到 `169.10000000000002`，未格式化就直接渲染，于是出现一长串数字。

### 15.2 整改

| 编号 | 位置 | 处理 |
|------|------|------|
| R-46 | 新建 `frontend-user/src/utils/price.js` | 提供 `formatPrice(v)`（`Number(v).toFixed(2)`，并对非数字兜底为 `0.00`）与 `moneyMinus(a, b)`（先相减再保留两位小数，专门处理优惠减法） |
| R-47 | 订单详情 / 订单列表的「实付」 | `order.totalAmount - (order.discountAmount || 0)` → `moneyMinus(order.totalAmount, order.discountAmount)`；原价、优惠额、单价、小计一并用 `formatPrice` |
| R-48 | 结算页商品单价、小计、优惠券面额 | 改用 `formatPrice`，别让浮点尾巴 |

> 验证：单测式校验 `moneyMinus(199.1, 30) = "169.10"`、`moneyMinus(199.99, 19.99) = "180.00"`、`formatPrice(169.09999999999997) = "169.10"`；`frontend-user` 生产构建通过（11.3s），无 lint / 控制台报错。后端金额计算逻辑未改动，仍由 `BigDecimal` 保证精确。

---

## 十六、优惠券进度、用户数据重置与商品封面（2026-10 第五轮）

### 16.1 已领取后进度条仍显示 0%

- 根因：领取率用 `Math.round(claimed / total * 100)` 取整。新人券发行量 999、领取 1 张，`1 / 999 × 100 = 0.1001%`，四舍五入后为 `0%`，看起来像没领到。
- 整改：领取率改为保留 1 位小数（`0.1%`），小于 `0.1%` 显示 `<0.1%`；同时展示「已领 1 / 999」真实数量，并给进度条设置 2% 最小可见宽度。管理端优惠券列表的发行进度条同步处理。
- 文件：`frontend-user/src/views/CouponView.vue`、`frontend-admin/src/views/CouponManage.vue`。

### 16.2 管理端新增「重置数据」

- 入口：用户管理 → 操作列「重置数据」，二次确认后执行。
- 效果：清空该用户的订单（含明细）、购物车、优惠券、收藏、评价、收货地址；昵称恢复为用户名、头像与手机号清空；账号、密码、邮箱保留，所以仍可正常登录，效果等同于刚注册的新号。
- 数据一致性：重置前会回补未取消订单占用的库存并扣回销量；已取消订单跳过（取消时已回补）；被清空的优惠券会同步扣减 `coupon.claimed` 发行计数。业务数据采用物理删除，别让逻辑删除残留唯一键导致之后无法再次加购 / 领券。
- 安全：不能重置自己或管理员账号；重置后立即清除该用户的登录态，强制重新登录。
- 接口：`POST /auth/admin/users/{id}/reset`（走 `AdminInterceptor`，仅管理员可用）。
- 文件：`AuthController`、`UserService` / `UserServiceImpl`、`frontend-admin/src/api/index.js`、`frontend-admin/src/views/UserManage.vue`。

### 16.3 新增商品在用户端不显示封面

- 根因：封面字段存的是 URL。测试时把 `101.jpg` 只放进了 `frontend-admin/public/img/products/`，而用户端是另一个独立应用，只能访问自己的 `public` 目录；`/img/products/101.jpg` 在 5173 端口 404，于是卡片显示破图。
- 处理：已把 `101.jpg` 同步到 `frontend-user/public/img/products/`，刷新用户端即可看到面包封面。同时在用户端新增 `coverUrl()`，把 `img/products/101.jpg` 这类相对路径补成 `/img/products/101.jpg`，别让多级路由（如搜索页）下解析到错误目录。
- 操作约定：本地图片必须同时放进 `frontend-user/public/img/products/` 与 `frontend-admin/public/img/products/`，封面填 `/img/products/文件名`；或者封面直接填一个公网可访问的 `https://` 图片地址。放在某个前端的 `src/assets` 里不会被运行时按 URL 直接访问。

---

## 十七、历史订单删除与商品评价功能（2026-10 第六轮）

### 17.1 用户端删除历史订单

- 入口：我的订单 → 订单卡片勾选框 + 「删除订单」；列表上方「全选本页可删除订单 / 批量删除」。
- 规则：仅 `COMPLETED`（已完成）与 `CANCELLED`（已取消）可删；待支付 / 已支付 / 已发货属于进行中的订单，需先取消或完成，别让库存锁定与状态机无法追踪。
- 交互：单笔与批量都二次确认；批量单次最多 100 笔；删除后刷新列表，若当前页被删空自动回退一页。
- 实现：`Orders` 逻辑删除（保留 `order_item` 作为归档），接口 `DELETE /order/{orderNo}`、`POST /order/batch-delete`。
- 文件：`OrderService` / `OrderServiceImpl`、`OrderController`、`OrderBatchDeleteDTO`、`OrderListView.vue`。

### 17.2 管理端删除历史订单

- 入口：订单管理 → 表格多选列（仅终态订单可选）、工具栏「批量删除」、行内「删除」、详情抽屉「删除订单」。
- 接口：`DELETE /order/admin/{orderNo}`、`POST /order/admin/batch-delete`，同样只允许终态订单。
- 联动：删除后刷新列表与状态计数，并触发 `admin:orders-changed` 更新侧边栏待处理订单徽标。
- 文件：`OrderServiceImpl`、`OrderController`、`OrderManage.vue`。

### 17.3 确认收货后评价 / 追评 / 修改 / 删除

- 入口：确认收货成功后自动弹出评价窗；已完成订单卡片与订单详情页也有「评价商品」。
- 弹窗：按订单商品逐个展示。未评价 → 星级 + 文本发布；已评价 → 展示评分、内容、评价时间，并提供「追加评价 / 修改评价 / 删除评价」；追评作为独立记录，带自己的时间。
- 商品页：评价按时间展示；追评以「追评」标签 + 时间展示；修改过的评价额外显示「修改于 …」；删除或屏蔽后商品页评价与评分汇总同步消失。
- 防重复：同一用户对同一商品只能有一条主评价，重复发布会被拒绝并提示到「我的订单」修改或追加。
- 数据：新增 `review_append` 表保存追评；接口 `GET /review/mine/order/{orderNo}`、`PUT /review/{id}`、`POST /review/{id}/append`、`DELETE /review/{id}`。
- 文件：`ReviewController`、`ReviewService` / `ReviewServiceImpl`、`ReviewAppend`、`ReviewAppendMapper`、`ReviewAppendVO`、`ReviewAppendDTO`、`OrderReviewDialog.vue`、`OrderListView.vue`、`OrderDetailView.vue`、`ProductDetailView.vue`。

### 17.4 管理端商品评价管理

- 入口：商品管理 → 行内「评价」按钮，打开该商品的评价弹窗。
- 能力：查看评价用户、星级、内容、时间与追评；支持「屏蔽 / 取消屏蔽」与「删除」。
- 屏蔽：软屏蔽，写入 `review_hidden`；商品页列表与评分汇总都会过滤，用户在自己的订单里会看到「已被管理员屏蔽」标记，可随时取消屏蔽恢复。
- 删除：逻辑删除评价，并清理其追评与屏蔽记录；商品页立即消失。
- 接口：`GET /review/admin/list`、`POST /review/admin/{id}/hide`、`POST /review/admin/{id}/unhide`、`DELETE /review/admin/{id}`；`WebConfig` 已将 `/review/admin/**` 纳入 `AdminInterceptor`。
- 文件：`ReviewController`、`ReviewServiceImpl`、`ReviewHidden`、`ReviewHiddenMapper`、`WebConfig`、`ReviewManageDialog.vue`、`ProductManage.vue`、`frontend-admin/src/api/index.js`。

### 17.5 数据库与验证

- `schema-mysql.sql` 新增 `review_append`、`review_hidden` 两张表，均使用 `CREATE TABLE IF NOT EXISTS`；项目 `spring.sql.init.mode=always`，老库启动时会自动补表，无需手工迁移。
- 本地启动独立后端实例（8081）连真实 MySQL 验证：用户单笔删除、批量删除、管理员删除均返回 200 且列表归零；评价「发布 → 追评 → 修改 → 商品页展示 → 管理员屏蔽（商品页与评分消失）→ 取消屏蔽 → 删除」全链路通过；测试账号与临时订单已清理，既有示例评价数据保持不变。
- 构建：后端 `mvn compile` 通过；`frontend-user`、`frontend-admin` 生产构建通过。

---

## 十八、重置数据持久化、浏览足迹上云与数据一致性审计（2026-10 第七轮）

### 18.1 重置数据后浏览足迹仍在

- 现象：管理员「重置数据」后，用户重新登录，「浏览足迹」仍保留重置前的商品。
- 根因：浏览足迹原先只写 `localStorage`（`star_footprints`），属于浏览器本地数据；服务端重置只能清数据库，无法触及用户浏览器，换设备 / 换浏览器也不会同步。
- 整改：
  - 新增 `browse_history` 表（`user_id + product_id` 唯一，按浏览时间倒序，最多保留 12 条），足迹改为服务端按账号保存；
  - 新增 `BrowseHistoryController`：`POST /browse/record`、`GET /browse/list`、`DELETE /browse/{productId}`、`DELETE /browse/clear`；
  - 用户端 `utils/footprint.js` 下线；`ProductDetailView` 登录后上报，`HomeView`「猜你喜欢」与 `FootprintView` 改为调用接口；`/footprints` 路由增加登录校验；
  - 启动时自动清理旧版 `localStorage` 残留键，别让历史数据继续显示。
- 效果：重置后（以及换设备登录）足迹为空；清空 / 单条移除只影响当前账号。
- 文件：`BrowseHistory`、`BrowseHistoryMapper`、`BrowseHistoryService(Impl)`、`BrowseHistoryController`、`BrowseRecordDTO`、`schema-mysql.sql`、`frontend-user/src/api/index.js`、`views/FootprintView.vue`、`views/HomeView.vue`、`views/ProductDetailView.vue`、`main.js`、`router/index.js`。

### 18.2 重置 / 删除用户时的级联清理

- 问题：旧「重置数据」只清订单、购物车、地址、收藏、评价、优惠券；追评、屏蔽记录与浏览足迹会残留；「删除用户」只逻辑删除用户行，业务数据全部成为孤儿。
- 整改：抽出 `clearUserData(userId, restoreStock)` 统一处理：
  - 回补未取消订单的库存与销量、扣减优惠券 `claimed`；
  - 物理删除 `order_item / orders / cart / address / favorite / review / review_append / review_hidden / user_coupon / browse_history`；
  - 「重置数据」与「删除用户」共用，删除用户时同样先回补再清理。
- 文件：`UserServiceImpl`。

### 18.3 一单多商品只能评价一件

- 根因：`review` 唯一键是 `(user_id, order_id)`，一单多商品时第二件商品评价触发唯一键冲突，接口 500。
- 整改：唯一键改为 `(user_id, product_id, deleted)`；`deleted = 0` 为有效评价，删除时置为自身 id 作为墓碑；迁移脚本幂等处理旧键、历史墓碑与极端重复数据；种子 `NOT EXISTS` 能看到墓碑，不会把用户删除的评价重新塞回来。
- 验证：临时账号下单 2 件商品 → 支付 → 发货 → 完成 → 分别评价两件商品，均返回 200。
- 文件：`schema-mysql.sql`、`ReviewServiceImpl`。

### 18.4 取消收藏后无法再次收藏

- 根因：`favorite` 唯一键 `(user_id, product_id)`，取消收藏使用逻辑删除，墓碑占住唯一键；再次收藏时 `selectCount` 看不到墓碑，插入冲突。
- 整改：取消收藏改为物理删除；迁移时清理历史收藏墓碑。
- 验证：`DELETE /favorite/4` 后 `POST /favorite/4` 成功，收藏列表恢复。
- 文件：`FavoriteServiceImpl`、`schema-mysql.sql`。

### 18.5 重置结果被种子数据还原

- 根因：`data-mysql.sql` 每次启动都按 `NOT EXISTS` 补写示例订单、评价、收藏、地址；重置物理删除后，重启会被重新插入。
- 整改：新增 `seed_meta` 初始化标记；用户业务示例数据只在首次初始化写入（`@seed_user_data = 0` 才跑），脚本末尾写入标记；商品 / 分类 / 优惠券等基础种子保持原幂等逻辑。
- 效果：重置某用户后重启，示例订单 / 评价 / 收藏 / 地址不会复活；全新建库仍会生成种子数据。
- 文件：`schema-mysql.sql`、`data-mysql.sql`。

### 18.6 审计结论（保留的合理设计）

- 搜索历史、商品对比清单仍保存在 `localStorage`：属于设备级交互偏好，不是账号业务数据，不随重置清除（与浏览足迹不同）。
- 删除订单采用逻辑删除，保留 `order_item` 归档；仅终态订单可删。
- 用户删除采用逻辑删除保留账号行（用户名不释放），别让历史引用悬空；业务数据已清空。
- 已删除订单下的评价继续在商品页公开展示，管理员可在「商品管理 → 评价」中处理。

### 18.7 验证与构建

- 独立后端 8081 + 真实 MySQL 验证：
  - 浏览足迹：登录上报 → 列表 → 清空 → 重置后为 0；
  - 重置：订单 / 评价 / 收藏 / 地址 / 足迹全部清空，库存与销量回补；
  - 多商品评价：同一订单两件商品均发布成功；
  - 收藏：取消后重新收藏成功；
  - 索引迁移：`review` 唯一键为 `(user_id, product_id, deleted)`，`seed_meta` 标记写入，`@seed_user_data = 1`。
- 构建：后端 `mvn package` 通过；`frontend-user`、`frontend-admin` 生产构建通过。

---

## 十九、商品「删除」被误实现为「下架」（2026-10 第八轮）

- 现象：管理员删除已下单的商品后，商品仍留在商品列表，状态变成「已下架」，看起来像没删掉。
- 根因：`ProductServiceImpl.delete` 在检测到该商品存在 `order_item` 时，直接改成 `status=0` 并 `return`，没有真正删除；本意是保护历史订单，但把「下架」和「删除」两个动作混在了一起。
- 整改：
  - **下架**：`status=0`，商品仍保留在管理端列表，由列表里的上下架开关控制。
  - **删除**：逻辑删除 `deleted=1`，商品从管理端和用户端同时消失，列表不再返回。
  - 删除时一并清理该商品的购物车、收藏、浏览足迹、评价（含追评与屏蔽记录），别让结算或推荐引用已删除商品。
  - 历史订单不依赖 `product` 表：`order_item` 已保存商品名称、价格、数量快照，删除商品不影响订单列表/详情展示。
  - 前端删除确认文案补充「如果只是暂时停售，请使用下架开关」。
- 验证：临时商品加入购物车、收藏、足迹、评价后删除，后台列表不再出现，关联数据全部清零；下架商品仍保留在后台列表且 `status=0`。
- 文件：`ProductServiceImpl`、`ProductManage.vue`、`docs/api.md`、`README.md`。

---

## 二十、超管写权限收口与全量写路径审计（2026-10 第九轮）

**根因**：`ShopService.currentShopId()` 对超管返回 `null`（平台视角 = 不限店铺），这个「读」语义被写操作复用后，凡是 `if (scope != null)` 的店铺条件都会被静默跳过，等价于「超管可替任意店铺写入」。

**整改**（写操作统一走 `requireOperableShopId()` 或等价校验，只认自己名下的店铺；读 / 筛选继续用 `currentShopId()`）：

| # | 问题 | 位置 | 整改 |
|---|------|------|------|
| 1 | 超管新增商品可选任意店铺，商品可挂到别家店 | `ProductServiceImpl.save`、`ProductManage.vue` | 后端忽略 `dto.shopId`，一律落自己店铺；前端「所属店铺」改只读，未绑定店铺时禁止新增 |
| 2 | 超管可改价 / 上下架 / 删除别家店商品 | `ProductServiceImpl.checkShopOwner/update/delete` | `null` 时回退 `mine()` 再比对，跨店返回 403；前端非本店行隐藏编辑 / 删除、禁用上下架 |
| 3 | 超管可删除别家店历史订单（单笔 + 批量） | `OrderController.adminDelete/adminBatchDelete` | 改走 `requireOperableShopId()` |
| 4 | 超管连自己店铺（官方旗舰店）的订单都不能发货 / 完成 | `ShopService.requireMerchantShopId` | 重命名为 `requireOperableShopId()`：商家本店 + 超管自己店铺可履约，别家店仍 403 |
| 5 | 超管改自己店铺资料必 403（提示「未绑定店铺」） | `ShopServiceImpl.updateMine` | 改走 `requireOperableShopId()` |
| 6 | 券面额零校验：`discount ≥ threshold` 可 0 元核销、负数抵扣反向抬高金额 | `CouponServiceImpl` | 新增 `validate()`：减免 > 0、减免 < 门槛、总量 ≥ 0、结束 > 开始 |
| 7 | 优惠券日期留空时前端传 `""`，`LocalDateTime` 反序列化失败 → 500 | `CouponManage.vue`、`GlobalExceptionHandler` | 前端空串转 `null`；后端把 `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException` 兜成 400 |
| 8 | 403 只显示 axios 英文原文 | 两端 `utils/request.js` | 优先展示 `error.response.data.message` |
| 9 | 商家手敲 `/users`、`/categories` 能进平台页（菜单已隐藏） | `router/index.js` | 加 `meta.platformOnly` 路由守卫 |
| 10 | 商家看板把「本店下单用户数」标成「注册用户」 | `Dashboard.vue` | 文案按 `scope === 'SHOP'` 切换 |
| 11 | 订单 CSV 可被公式注入 | `OrderServiceImpl.safe()` | 以 `= + - @ \t` 开头的单元格前置 `'` |
| 12 | 后台删除订单曾用 `currentShopId()` 传 `null`，店铺条件失效 | `OrderServiceImpl.findDeletable` | 传值后条件恒生效（商家 / 超管都只作用于自己的店） |

**验证**：`mvn compile`、`frontend-admin` / `frontend-user` `npm run build` 均通过；`read_lints` 0 告警。手工回归：超管看别家店商品 / 订单只显示只读态，本店（星选官方旗舰店）可发货、可编辑商品；商家侧功能无变化。

**文件**：`ProductServiceImpl`、`OrderController`、`ShopService(+Impl)`、`CouponServiceImpl`、`OrderServiceImpl`、`GlobalExceptionHandler`、`ProductManage.vue`、`OrderManage.vue`、`CouponManage.vue`、`Dashboard.vue`、`router/index.js`、两端 `utils/request.js`。

---

## 二十一、搜索匹配精度（2026-10 第十轮）

**现象**：搜「手机」返回 18 件，里面混着蓝牙音箱、移动电源、充电器。

**根因**：`ProductServiceImpl.doPage` 把「商品名 / 描述 / 分类名」用一个大 `OR` 拼在一起。关键词命中分类名（「手机」→「手机数码」）时，整类商品全部算命中；描述里写了「适配手机」的充电器同样命中，两类噪音叠加，结果既不准也无法排序。

**整改**：改为三级渐进匹配，命中即停：

| 级别 | 条件 | 效果 |
|------|------|------|
| ① | 商品名 LIKE 关键词 | 搜「耳机 / 充电器 / 手环」等精确命中，只返回这些，不再掺配件 |
| ② | ① 为 0 时，用分类名 LIKE 关键词整类返回 | 搜「手机」→ 分类「手机数码」下商品（种子数据里手机型号名不含「手机」二字，必须靠分类兜底） |
| ③ | ①② 都为 0 时，才放宽到「名称 OR 描述」 | 搜「降噪 / 钛金属」这类只写在卖点里的词 |

- 公共筛选（店铺 / 分类 / 状态 / 价格 / 有货）抽成 `baseQuery()`，关键词层各自新建 wrapper，别让 `OR` 与 `AND` 混排导致过滤条件失效
- 关键词清洗后为空（只输入 `%`、`_`）时不再拼接 LIKE，防止变成全表匹配
- 用户端 `SearchView.vue` 增加提示条：结果里没有任何商品名包含关键词时，显示「没有名称含『手机』的商品，以下为相关分类 / 描述匹配的结果」，把兜底行为讲清楚

**数据补全（第二轮）**：只改代码仍不精确，种子数据里 6 台手机的名字是「一加 12」「vivo X100 Pro」这种纯型号，压根不含「手机」二字，所以搜「手机」必然掉到 ② 分类兜底，还是 18 件。于是给这 6 件补上品类词，走 ① 名称命中：

| id | 原名 | 现名 |
|----|------|------|
| 1 | iPhone 15 Pro 256GB | iPhone 15 Pro 256GB 5G 手机 |
| 2 | 小米 14 Ultra | 小米 14 Ultra 5G 手机 |
| 3 | 华为 Mate60 Pro | 华为 Mate60 Pro 手机 |
| 25 | OPPO Find X7 | OPPO Find X7 5G 手机 |
| 26 | vivo X100 Pro | vivo X100 Pro 5G 手机 |
| 27 | 一加 12 | 一加 12 5G 手机 |

迁移写在 `data-mysql.sql` 末尾（`seed_meta` 打标 + `WHERE name = '旧名'` 双保险），`spring.sql.init.mode=always` 会在下次启动时自动跑，老库无需手工改。

**验证**：`mvn compile`、`npm run build` 通过；搜「手机」返回 6 件且全部是手机（提示条消失），搜「耳机」只出耳机，搜「降噪」走描述兜底，搜不存在的词仍回空态页。

**别名补全（第三轮）**：接着发现更普遍的问题，搜「鞋子」返回 0 件，但 Nike Air Force 1、阿迪达斯 Ultraboost 明明在架。原因是这类商品标题只有型号，**没有任何品类词**，而「服饰鞋包」这个分类名也不含「鞋子」，三级匹配全线落空。逐个改商品名不可维护，于是加了专门的别名字段：

| 改动 | 内容 |
|------|------|
| 表结构 | `product.keywords VARCHAR(500)`，存「鞋子,鞋,运动鞋」这类逗号分隔别名；`schema-mysql.sql` 建表自带，老库用 `information_schema` 判重后动态 `ALTER`（MySQL 的 `ADD COLUMN` 无 `IF NOT EXISTS`） |
| 数据 | 100 件商品按品类回填别名（服饰 → 鞋子/裤子/外套/羽绒服…；数码 → 手机/平板/耳机/相机/手表…；图书 → 书/小说/考研…），`seed_meta` 标记一次性执行 |
| 检索 | 第一级条件改为 `name LIKE kw OR keywords LIKE kw`，描述兜底同样带上别名 |
| 后台 | 商品新增/编辑弹窗加「搜索别名」输入框，商家可自行补充；`ProductDTO` 加 `@Size(max=200)` 校验 |

**效果**：搜「鞋子」出 4 双鞋、搜「平板」出 3 台 iPad、搜「书」出图书、搜「吹风机」出 2 台，全部走第一级精确命中；搜一个真不存在的词仍回空态页。

**修饰词松弛（第四轮）**：别名是「整词包含」匹配，用户搜「男鞋」依然 0 条，中文常见「修饰词 + 品类词」的组合（男鞋 / 女士运动鞋 / 苹果手机 / 笔记本电脑），组合出来的词不可能穷举进别名表。于是加了一级通用松弛：

```
① 名称 / 别名包含「男鞋」      → 0 条
② 剥掉 1~2 个前缀或后缀字再试  → 「鞋」→ 4 条 ✅
   顺序：先少砍（1 字）后多砍（2 字），前后缀都试
③ 分类名                       → 如「数码」
④ 名称 / 别名 / 描述兜底        → 如「降噪」
```

- 只在前一级 0 条时才降级，所以「鞋子」「运动鞋」这类整词命中永远优先，不会被松弛结果污染
- 每次降级都带完整筛选条件（店铺 / 分类 / 状态 / 价格 / 有货）重新 count，别让放宽后越权或漏筛选
- 关键词清洗后为空（只输入 `%`、`_`）时不进入匹配，防止全表返回

**效果**：男鞋 / 女鞋 → 4 双鞋，男士运动鞋 → 4 双运动鞋，苹果手机 → 6 台手机，笔记本电脑 → 3 台笔记本，化妆 → 3 件彩妆，男装 → 衬衫；真正不存在的词（如「奶茶」）仍回空态页并给热搜词建议。前端提示条文案同步改为「没有精确匹配『xx』的商品，以下是按名称 / 别名 / 分类放宽匹配的结果」，判断依据也从「名称」改为「名称 + 别名」。

**文件**：`ProductServiceImpl`、`SearchView.vue`、`docs/api.md`。

---

## 二十二、跨店订单合并支付（2026-10 功能增强）

**现象**：购物车勾选 2 家店的商品，结算后生成 2 笔订单，只能逐笔点「立即支付」，用户以为支付被拆坏了。

**根因**：拆单是刻意设计（`orders.shop_id` 一单一店，发货、售后、结算都按店铺走），错在把**支付入口**也做成了逐笔，支付是用户侧的一次动作，不该被店铺维度切开。

**整改**：

| 层 | 改动 |
|----|------|
| 接口 | 新增 `POST /order/pay/batch`（`OrderBatchPayDTO`，≤50 笔）；逐笔 `UPDATE ... WHERE status='PENDING_PAYMENT'`，返回实际支付笔数，已支付的自动跳过，重复点击 / 并发不会二次支付 |
| 结算页 | 跨店提交成功后弹「合并支付」确认框：「全部支付」→ 批量支付后跳已支付列表；「稍后支付」→ 跳待支付列表 |
| 订单页 | 待支付订单可勾选，顶部「合并支付（N）」按钮显示「已选 N 笔 · 合计 ¥X」；勾选池同时服务「批量删除」（仅终态订单），两个按钮按选中子集各自显隐 |
| 文案 | 购物车提示改为「支持一次合并支付、各店分别发货」；结算提示不再说「请分别支付」 |

**验证**：`mvn compile`、`npm run build` 通过；跨店结算 → 全部支付 → 两笔订单同时变「已支付」，商家侧各自只有本店那笔；先「稍后支付」再在订单页勾两笔合并支付，结果一致。

**文件**：`OrderController`、`OrderService(+Impl)`、`OrderBatchPayDTO`、`api/index.js`、`CheckoutView.vue`、`OrderListView.vue`、`CartView.vue`、`docs/api.md`。

---

## 二十三、安全加固、数据一致性与前端抗造（2026-11 第十一轮）

| # | 类别 | 问题 | 根因 | 整改 |
|---|------|------|------|------|
| 1 | 消息送达 | 用户关闭「接收系统通知」后，**好友申请、举报结果等事务性通知被静默丢弃** | `MessageServiceImpl.sendSystem` 入库前调用 `settingService.canReceive(toId, SCENE_SYSTEM)`，与「SYSTEM 豁免接收方开关」的设计约定矛盾 | 移除该校验，**系统通知强制送达**（`from_id = 0`）；另新增 `sendOfficial()` 供官方（ADMIN）主动通知使用，同样强制送达 |
| 2 | 商品可见性 | 下架商品（`status = 0`）仍可通过商品详情接口直接取到数据 | `ProductServiceImpl.detail` 只判空、未校验 `status`（列表 / 搜索已过滤 `status = 1`，详情接口未限定） | 详情接口补齐校验：下架或不存在一律抛「商品已下架或不存在」；前端已有 `loadError` 友好空态承接 |
| 3 | 提权 / 脏数据 | 注册邮箱仅在应用层查重，极端并发下可能重复注册 | 数据库层无约束 | `SchemaUpgradeRunner` 启动时**幂等补建** `user_table.email` 唯一索引 `uk_user_email`；补建前先统计重复的非空邮箱，存在重复则**仅告警并跳过**，不阻断启动 |
| 4 | SQL 安全 | 评价「已购买」校验用 `inSql` 拼接 `userId` 字符串 | 历史实现为 `"SELECT id FROM orders WHERE user_id = " + userId` | 改为 MyBatis-Plus **参数化子查询**（`.in(OrderItem::getOrderId, orderWrapper)`），消除字符串拼接 |
| 5 | 路由守卫 | 用户端「我的优惠券」路由缺少 `auth`，未登录可直接进入 | 路由 `meta` 漏配 | 补充 `meta.auth = true`，未登录跳登录页并带 `redirect` |
| 6 | 官方触达 | ADMIN 与 USER 不构成好友关系，官方无法联系普通用户 | 社交域好友模型的刻意设计（不能破坏） | 新增**官方通知通道**：`MessageService.sendOfficial` + `UserService.notifyUser` + `POST /auth/admin/users/{id}/notify`（平台级仅 ADMIN，校验目标存在、内容非空且 ≤ 500 字）；后台「用户管理」每行加「发通知」按钮 |
| 7 | 前端体验 | 官方 / 商家账号在用户端搜索好友时搜不到普通用户，空结果易被误认为「搜不到人」 | 搜索范围按身份限定，但界面无解释 | 搜索无结果时按角色差异化提示，并引导到后台「发通知」 |
| 8 | 前端抗造 | ① 路由懒加载偶发失败 → 内容区空白；② `<transition>` 包裹多根组件 → 渲染异常（`v[w] is not a function`）；③ `router-view` 按 `fullPath` 设 key → 点击会话（改写 query）后页面重建、选中态丢失 | ① 动态 import 失败时 `Component` 为 `undefined`；② `UserManage` 为「根 `div` + `el-dialog`」两个根节点，被 transition 包裹后动画钩子调用失败；③ `fullPath` 含 query，会话切换即触发重建 | ① 懒加载包装 `chunk()`，失败自动重试一次；② 移除 `<transition>` 包裹，并把 `UserManage` 改为单根；③ key 改用 `route.path`，仅路径变化才重建；④ 组件缺失时显示「页面加载失败 + 重新加载」，不再纯白 |

**验证**：后端 `mvn compile` 通过；`frontend-user`、`frontend-admin` 的 `npm run build` 均通过。

**文件**：`MessageServiceImpl`、`ProductServiceImpl`、`ReviewServiceImpl`、`SchemaUpgradeRunner`、`UserServiceImpl`、`AuthController`、`frontend-user/src/router/index.js`、`frontend-admin/src/router/index.js`、`AdminLayout.vue`、`UserManage.vue`、`FriendsView.vue`、`frontend-admin/src/api/index.js`。
