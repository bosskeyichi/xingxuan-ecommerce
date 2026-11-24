# 多商家改造说明（多店铺 · 跨店下单）

> 改造目标：原来只有一个商家（`admin`），现在有多个商家、多个店铺；同一个用户可以在购物车里同时放入不同店铺的商品，结算时按店铺自动拆成多笔订单，商家在后台只看得到自己店铺的商品与订单。

---

## 一、改造前后对比

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 商家数量 | 1 个（`admin` 兼任） | 3 家店铺 / 3 个经营主体（`admin` + `merchant1` + `merchant2`） |
| 商品归属 | 无店铺概念 | `product.shop_id`，商家建品自动挂到自己店铺 |
| 购物车 | 一条平铺列表 | 按店铺分组，支持「按店铺全选」与店铺小计 |
| 下单 | 1 次结算 = 1 笔订单 | 1 次结算 = **按店铺拆成 N 笔订单**（同一事务，全成功或全回滚） |
| 订单归属 | 无 | `orders.shop_id`，发货 / 完成 / 删除 / 导出都按店铺隔离；发货与完成只能作用于自己名下的店铺（超管能履约自己开的店，别家店只读） |
| 后台权限 | 只有 `ADMIN` 能进 | `ADMIN`（超级管理员，全平台）+ `MERCHANT`（普通管理员 / 商家，本店）；超级管理员可在用户管理里把普通用户提升为普通管理员，系统自动建店 |
| 看板统计 | 全平台口径 | 超级管理员全平台；普通管理员只看本店商品、订单、销售额 |
| 优惠券 | 直接抵扣整单 | 平台券抵扣**金额最高且满足门槛的那一笔店铺订单**；店铺券（`coupon.shop_id`）只能抵扣本店订单，商家可自行发行本店券 |

---

## 二、数据模型

```
user_table (role: USER / MERCHANT / ADMIN)
    │ 1
    │
    │ 1
shop (owner_id UNIQUE, name, intro, cover, status)
    │ 1
    ├──────────────< product (shop_id)  ────< cart (product_id)
    │
    └──────────────< orders (shop_id)   ────< order_item (order_id)
```

新增/变更的表结构：

```sql
-- 新增：店铺（一个商家账号 = 一家店）
CREATE TABLE shop (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,          -- 店主 user_table.id，唯一
  name VARCHAR(60) NOT NULL,
  intro VARCHAR(200),
  cover VARCHAR(1000),
  status INT NOT NULL DEFAULT 1,     -- 1 营业 / 0 关店
  created_at DATETIME, updated_at DATETIME, deleted INT
);

-- 变更：商品归属店铺
ALTER TABLE product ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE product ADD INDEX idx_product_shop (shop_id, status);

-- 变更：订单归属店铺（跨店结算时按店铺拆单）
ALTER TABLE orders ADD COLUMN shop_id BIGINT AFTER order_no;
ALTER TABLE orders ADD INDEX idx_orders_shop (shop_id, status);
```

两处变更都在 `schema-mysql.sql` 里写了**幂等迁移**：先用 `information_schema` 判断列/索引是否存在，不存在才 `ALTER`，所以老库、新库都能直接启动。历史订单会按订单内商品回填 `shop_id`，升级后商家不会丢单。

---

## 三、接口变化

| 接口 | 变化 |
|------|------|
| `GET /shop/list` | 新增，前台取营业中店铺（公开） |
| `GET /shop/detail/{id}` | 新增，店铺详情（公开） |
| `GET /shop/admin/context` | 新增，返回当前身份：`role` / `shopId` / `shopName` / `platformAdmin` |
| `GET /shop/admin/mine` | 新增，商家取自己的店铺 |
| `PUT /shop/admin/mine` | 新增，商家改店铺名 / 简介 |
| `GET /shop/admin/list` | 新增，平台管理员看全部店铺 + 商品数 / 订单数 / 成交额 |
| `PUT /shop/admin/{id}/status` | 新增，平台管理员开店 / 关店 |
| `GET /product/list` | 新增可选参数 `shopId`，首页店铺筛选用 |
| `GET /product/admin/list` | 商家强制只看本店；管理员可用 `shopId` 参数筛查 |
| `POST /order/submit` | **返回值由 `String` 改为 `List<String>`**：跨店时返回多个订单号 |
| `GET /order/admin/list` | 商家只看本店；管理员可用 `shopId` 参数筛查 |
| `GET /order/admin/export` | CSV 增加「店铺」列；商家只导出本店订单 |

---

## 四、跨店拆单流程

```
购物车（多条，可能来自 N 家店）
        │ 选中项
        ▼
OrderServiceImpl.submit()
        │ 1. 按 product.shop_id 分组，服务端用当前库价重算小计
        │ 2. 优惠券校验：取金额最高、且满足门槛的那一笔店铺订单
        │ 3. 逐店创建订单（order_no 各自生成，shop_id 落到本店）
        │    每个商品：原子扣库存（stock >= qty）→ 写 order_item → 清购物车行
        │ 4. 优惠券标记 USED，绑定到实际抵扣的那笔订单
        ▼
返回订单号列表（1 笔 = 普通下单；N 笔 = 跨店拆单）
```

> **拆单不拆支付**：订单按店铺拆开是为了各自发货、各自结算；支付动作可以合并。结算页拿到订单号数组后可直接调 `POST /order/pay/batch` 一次付清（弹「合并支付」确认框），也可以「稍后支付」到订单页勾选多笔再合并支付。合并支付逐笔条件更新 `PENDING_PAYMENT → PAID`，已支付的自动跳过。

关键约束：

- 同一事务：任何一家店扣库存失败，整批订单回滚，不会出现「一半成功一半失败」。
- 价格以服务端为准：加购后商家改价，结算金额按当前价重算。
- 优惠券不重复：`user_coupon` 用条件更新 `WHERE status='UNUSED'` 标记，并发下只有一次生效。

---

## 五、权限模型

| 层 | 拦截器 | 作用 |
|----|--------|------|
| 1 | `LoginInterceptor` | JWT 校验 + 单设备登录 |
| 2 | `AdminInterceptor` | 后台身份：放行 `ADMIN` 与 `MERCHANT` |
| 3 | `PlatformInterceptor` | 平台级功能仅 `ADMIN`：用户、分类、评价管理、店铺运营；优惠券对商家开放，按店铺归属校验 |

数据范围由 `ShopService.currentShopId()` 统一决定：

```java
// ADMIN  -> null（不限制店铺）
// MERCHANT -> 自己的 shop.id（shop 表按 owner_id 唯一）
Long scope = shopService.currentShopId();
```

各业务方法都按这个 `scope` 追加 `shop_id` 条件，商家即便手动拼参数也跨不出自己的店：

- 商品：建品自动挂本店；改 / 删 / 上下架前校验 `product.shop_id == 自己店铺`（超管同样只能动自己店铺的商品，别家店只读）
- 订单：删除 / 导出带 `shop_id` 条件，跨店操作返回「订单不存在或不属于当前店铺」；发货 / 完成 / 删除走 `requireOperableShopId()`，只认自己名下的店铺，商家管本店，超管管自己开的店（官方旗舰店），别家店订单一律 403
- 看板：商品数、订单数、销售额、趋势、热销、库存预警都按本店统计

> 已知取舍：商品评价的管理仍归平台（`/review/admin/**` 仅 ADMIN），商家后台不显示「评价」按钮。

### 角色提升与重置

| 操作 | 效果 |
|------|------|
| 设为普通管理员（`PUT /auth/admin/users/{id}/role`） | `role` 改为 `MERCHANT`；没有店铺时自动创建「昵称 + 的店铺」，立即拥有后台商品 / 订单 / 优惠券 / 看板 |
| 取消普通管理员 | 校验店铺已无商品与订单后删除店铺，`role` 回到 `USER`；有内容时提示先「重置数据」 |
| 重置数据（商家） | 下架本店全部商品、删除本店优惠券、店铺资料恢复默认；个人业务数据清空；昵称 / 邮箱 / 手机号 / 头像恢复初始，**密码重置为 123456** |
| 角色变更后 | 该账号两端会话一并清除，必须重新登录（别让旧 token 里还带旧角色） |

> 订单是买家与商家的共同交易记录，重置商家时**保留已成交订单**，不做删除；商家清空商品后，这些订单仍在用户端可查。

---

### 社交与管理的店铺限定

| 能力 | 限定方式 |
|------|----------|
| 客服消息（`SERVICE`） | 会话带 `shop_id`，接收人强制为该店铺店主，防止冒充客服；商家只能看到并回复**本店**咨询 |
| 举报（`report`） | `report.shop_id` 记录被举报对象所属店铺；`GET /report/admin/page` 由 `shopService.currentShopId()` 限定，商家只看本店，平台管理员看全部 |
| 好友（`friend`） | 好友关系本身不按店铺隔离；`USER↔USER`、`MERCHANT↔MERCHANT` 为申请-同意制，`ADMIN↔MERCHANT` 为系统默认好友（便于平台与商家直接沟通） |
| 官方通知 | 平台管理员可通过「用户管理 → 发通知」以系统通知触达任意用户，不受店铺归属与好友关系限制（强制送达） |

## 六、种子数据

| 店铺 | 店主 | 商品范围（按分类分配） |
|------|------|------------------------|
| 1 星选官方旗舰店 | `admin` | 手机数码、电脑办公 |
| 2 极客数码专营店 | `merchant1` | 家用电器、图书音像 |
| 3 生活优选馆 | `merchant2` | 服饰鞋包、美妆个护 |

- 分配语句只在**首次初始化**执行（`seed_meta` 标记 `id=3`），之后重启不会覆盖商家自己调整的商品归属。
- 商家账号密码与内置账号一致：`123456`（BCrypt 存储）。

---

## 七、验证动线（3 分钟）

1. 用户端首页 → 店铺下拉选「极客数码专营店」→ 加购 1 件；切「生活优选馆」再加购 1 件。
2. 购物车：看到两条店铺分组，每组带店铺名与店铺小计 → 全选 → 去结算。
3. 结算页提示「本次结算涉及 2 家店铺，提交后将生成 2 笔订单」→ 提交 → 弹「合并支付」确认框，点「全部支付」一次付清两笔 → 跳已支付列表，2 笔订单各自带店铺标签。
4. 退出，用 `merchant1` 登录后台：概览显示「本店数据」，订单页只有 2 号店那笔。
5. 商家发货 → 用户端确认收货 → 评价；再用 `admin` 登录，能看到全部店铺订单（只有本店「星选官方旗舰店」的行有发货按钮，别家店只读）与「店铺管理」页的三家店经营数据，并可发行全平台券或指定店铺券。

---

## 八、涉及文件

**后端**

- 新增：`entity/Shop.java`、`mapper/ShopMapper.java`、`service/ShopService.java`、`service/impl/ShopServiceImpl.java`、`controller/ShopController.java`、`dto/ShopDTO.java`、`vo/ShopVO.java`、`interceptor/PlatformInterceptor.java`
- 修改：`Product` / `Orders`（shop_id）、`ProductService(+Impl)`、`OrderService(+Impl)`、`CartServiceImpl`、`StatsService(+Impl)`、`UserServiceImpl`（登录回填店铺）、`ProductController`、`OrderController`、`StatsController`、`WebConfig`、`AdminInterceptor`、`schema-mysql.sql`、`data-mysql.sql`

**前端**

- 用户端：`api/index.js`（shopApi）、`HomeView`（店铺筛选）、`CartView`（按店铺分组 + 店铺全选）、`CheckoutView`（分组展示 + 多单提示 + 券规则）、`ProductDetailView`（店铺徽标）、`OrderListView` / `OrderDetailView`（店铺标签）、`ProductCard`（店铺名）
- 管理端：`api/index.js`（shopApi）、`Login`（双角色 + 账号提示）、`AdminLayout`（按角色过滤菜单 + 店铺标识 + 个人中心入口）、`ProductManage`（店铺列 / 筛选 / 本店才可编辑）、`OrderManage`（店铺列 / 本店才可发货与删除）、`Dashboard`（数据范围标签）、`ShopManage`（平台总览 + 商家「我的店铺」双视图）、`UserManage`（角色提升 / 重置 / 删除）、`ProfileView`（后台个人中心）
