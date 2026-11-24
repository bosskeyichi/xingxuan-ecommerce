# 数据库设计与初始化

## 1. 数据库选型

- **MySQL 8.0**（生产 / 开发统一使用同一数据库），引擎 `InnoDB`，字符集 `utf8mb4`（兼容 emoji 与生僻字）。
- 启动脚本 `db/schema-mysql.sql`（建表）与 `db/data-mysql.sql`（种子数据）均**幂等**：
  - 建表使用 `CREATE TABLE IF NOT EXISTS`，并以内联 `KEY / UNIQUE KEY` 定义索引；
  - 种子数据使用 `INSERT INTO ... SELECT ... FROM DUAL WHERE NOT EXISTS (...)`，重启不会丢数据、不会重复插入。
- 自动初始化由 Spring 的 `spring.sql.init` 完成（`mode: always`），无需手动执行。

> 早期版本使用 H2 嵌入式数据库，现已整体迁移至 MySQL，以满足事务一致性与 Docker 分布式部署需要。

## 2. 连接配置

`application-dev.yml`（可通过环境变量覆盖，便于 Docker / 云环境）：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3307}/${MYSQL_DATABASE:ecommerce}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&createDatabaseIfNotExist=true
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:123456}
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema-mysql.sql
      data-locations: classpath:db/data-mysql.sql
```

Docker Compose 中 `mysql` 服务会创建名为 `ecommerce` 的数据库，后端 `depends_on: condition: service_healthy` 在其就绪后再启动，初始化脚本自动跑。

## 3. 表结构（共 21 张：20 张业务表 + 1 张 `seed_meta` 初始化标记表）

除 `seed_meta`（仅 `id` + `created_at`）外，业务表均含 `created_at / updated_at`（MyBatis-Plus 自动填充）与逻辑删除字段 `deleted`。

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `user_table` | 用户 | `username`(唯一) / `password`(BCrypt) / `role`(USER/MERCHANT/ADMIN) / `status` / `deleted` |
| `shop` | 店铺（多商家） | `owner_id`(唯一，店主账号) / `name` / `intro` / `cover` / `status`(1 营业 0 关店) |
| `category` | 商品分类 | `name` / `icon` / `sort_order` / `parent_id` |
| `product` | 商品 | `shop_id`(所属店铺) / `name` / `description` / `keywords`(搜索别名，逗号分隔，如「鞋子,运动鞋」) / `price`(DECIMAL) / `stock` / `cover` / `category_id` / `sales` / `status` |
| `cart` | 购物车 | `user_id` / `product_id` / `quantity` / `selected`（`UNIQUE(user_id, product_id)`；删除为逻辑删除，重新加购时由 `restoreDeleted` 复用原行，别让唯一键冲突） |
| `orders` | 订单 | `order_no`(唯一) / `shop_id`(所属店铺) / `user_id` / `total_amount` / `discount_amount` / `coupon_id` / `status` / 收货信息 / 各状态时间戳 |
| `order_item` | 订单明细 | `order_id` / `product_id` / 商品快照 / `price` / `quantity` / `subtotal` |
| `favorite` | 商品收藏 | `user_id` / `product_id`（`UNIQUE(user_id, product_id)`，移除即物理删除） |
| `review` | 商品评价 | `user_id` / `product_id` / `order_id` / `rating`(1-5) / `content`；`UNIQUE(user_id, product_id, deleted)`，`deleted=0` 为有效评价，删除时置为自身 id 作墓碑 |
| `review_append` | 评价追评 | `review_id` / `content` / `created_at` |
| `review_hidden` | 管理员屏蔽的评价 | `review_id`(主键) / `created_at` |
| `address` | 收货地址簿 | `user_id` / `receiver_name` / `receiver_phone` / `region` / `detail` / `is_default` |
| `coupon` | 优惠券 | `shop_id`(为空=全平台券) / `name` / `description` / `threshold`(满减门槛) / `discount`(减免) / `total` / `claimed` / 有效期 / `status` |
| `user_coupon` | 用户优惠券 | `user_id` / `coupon_id` / `status`(UNUSED/USED) / `order_id` / `used_at`（`UNIQUE(user_id, coupon_id)`） |
| `browse_history` | 浏览足迹 | `user_id` / `product_id` / `created_at`（`UNIQUE(user_id, product_id)`，按用户保存，重置时清空） |
| `user_setting` | 隐私与消息设置 | `user_id`(唯一) / `searchable`(可被搜索) / `friend_policy`(ALL/NOBODY) / `show_email` / `show_phone` / `show_reviews` / `receive_friend_msg` / `receive_service_msg` / `receive_system_msg` / `notify_sound`；缺行时后端按默认值返回 |
| `friend` | 好友关系 | `user_id` / `friend_id` / `status`(0 申请中 / 1 已同意) / `remark`；`UNIQUE(user_id, friend_id)`。申请时写一行 `status=0`，同意后改为 1 并补写反向行 |
| `message` | 消息 | `conversation_key`(较小 id:较大 id:scene，便于按会话聚合) / `scene`(FRIEND/SERVICE/SYSTEM) / `from_id`(0=系统) / `to_id` / `type`(TEXT/PRODUCT/SHOP/REVIEW) / `content` / `target_id` / `shop_id` / `read_at` |
| `conversation_hide` | 会话隐藏 | `user_id` / `conversation_key` / `last_message_id`（隐藏时的最大消息 id；对方再有更大 id 的新消息时会话自动恢复）；`UNIQUE(user_id, conversation_key)` |
| `report` | 举报 | `reporter_id` / `target_type`(PRODUCT/SHOP) / `target_id` / `shop_id`(被举报对象所属店铺，用来按店限定) / `type` / `content` / `status`(PENDING/ACCEPTED/REJECTED) / `handle_note` / `handler_id` / `handled_at` |
| `seed_meta` | 初始化标记 | `id` / `created_at`，用来保证用户业务示例数据只写入一次 |

> 关键索引：`uk_order_no`、`uk_username`、`uk_shop_owner`、`idx_cart_user`、`idx_orders_user`、`idx_orders_shop`、`idx_oi_order`、`idx_product_cat`、`idx_product_shop`、`idx_review_product`、`idx_uc_user`、`uk_browse`、`uk_setting_user`、`uk_friend_pair`、`idx_msg_conv`、`idx_msg_to`、`uk_hide_pair`、`idx_report_shop` 等，别让热点表全表扫描。
>
> 邮箱唯一索引迁移：注册邮箱早期仅在应用层查重，极端并发下可能出现同一邮箱注册多个账号。现由 `SchemaUpgradeRunner` 在启动时**幂等补建** `user_table.email` 的唯一索引 `uk_user_email`；补建前先统计重复的非空邮箱，若存在重复则**仅告警并跳过**（不阻断启动），交由人工清理后再补。
>
> **无外键约束**：全库不使用 `FOREIGN KEY`，表间关联（如 `order_item.order_id`、`product.shop_id`、`report.shop_id`）的一致性由应用层与事务保证，便于按用户 / 店铺批量清理与迁移。
>
> **启动期迁移（`config/SchemaUpgradeRunner.java`，共 4 类，失败仅告警不阻断启动）**：① `friend.status` 列（申请-同意制）；② `product.free_shipping` / `ship_hours` / `return_days`（服务承诺）；③ `orders.tracking_no` / `tracking_company`（物流信息）；④ `user_table.email` 唯一索引 `uk_user_email`。
>
> **种子数据规模**：7 个账号（1 ADMIN + 2 MERCHANT + 4 USER）/ 3 家店铺 / 6 个分类 / **100 件商品**（逐一对应 `/img/products/{id}.jpg`）/ 6 张优惠券（4 平台券 + 2 店铺券）/ 2 笔订单 / 3 条评价 / 3 条地址 / 1 条收藏。好友、消息、举报、足迹、购物车、已领券为运行时产生，**不预置数据**。
>
> 多商家迁移：`shop` 表为新增表；`product.shop_id`、`orders.shop_id`、`coupon.shop_id` 通过 `information_schema` 判断后幂等 `ALTER`（`coupon` 同时补 `idx_coupon_shop` 索引），历史订单按订单内商品回填 `shop_id`（`UPDATE orders JOIN order_item/product`），老库升级不丢数据。
>
> 搜索别名迁移：`product.keywords` 同样用 `information_schema` 判重后动态 `ALTER`（MySQL 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`），随后按 `seed_meta` 标记一次性回填 100 件商品的别名，只补空值、不覆盖商家后来改过的内容。
>
> 评价唯一键迁移：旧版为 `(user_id, order_id)`，一单多商品时第二件商品评价会冲突；现改为 `(user_id, product_id, deleted)`，并保留墓碑行防止种子数据复活。迁移脚本在 `schema-mysql.sql` 中幂等执行。
>
> 收藏移除改为物理删除，别让 `(user_id, product_id)` 唯一键被逻辑删除残留占用，导致取消收藏后无法再次收藏。

## 4. 优惠券数据模型（这轮新增）

- `coupon` 支持两种口径：`shop_id IS NULL` 为平台券（平台管理员发放，全平台可用），`shop_id` 非空为店铺券（商家只能发本店券，平台管理员也可代发指定店铺券）。`claimed` 记录已领取数量，领券时通过原子更新 `claimed = claimed + 1 WHERE claimed < total` 防止超发，唯一索引 `uk_user_coupon` 保证一人一券；店铺券在店铺关店期间不可领取。
- 适用范围在创建时确定，编辑不改变归属，已领取的券若从店铺券改成平台券，会破坏既有订单的抵扣规则。
- `user_coupon` 记录每个用户持有的券，`status = UNUSED/USED`，下单时校验门槛并在同一事务内置为 `USED`、回填 `order_id`；订单取消 / 超时取消时自动回退为 `UNUSED`，库存与优惠均不丢失。
- 订单表新增 `discount_amount`（优惠金额）与 `coupon_id`（使用的用户券 id），订单详情与列表会展示「优惠 -¥X / 实付 ¥Y」。

## 5. 本地使用 MySQL（非 Docker）

```sql
-- 1) 创建数据库（已存在可跳过）
CREATE DATABASE ecommerce
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2) 执行初始化（首次或需重置时）
--    source db/schema-mysql.sql
--    source db/data-mysql.sql
```

或修改 `application-dev.yml` 的 `url` 指向你的实例，启动后端即可自动初始化。

## 6. 完全重置

删除数据库后重建（`DROP DATABASE ecommerce; CREATE DATABASE ...`），重启后端即会重新跑建表与种子脚本；也可在 MySQL 中 `DROP TABLE IF EXISTS <表名>` 后重启。

## 7. 数据库管理建议

- 本地环境账号：用户名 `root`，密码 `123456`（生产务必修改，并通过 `JWT_SECRET` 注入密钥）。
- 连接信息、密码均通过环境变量传入容器，不写死在代码里。
- 字符集务必使用 `utf8mb4`，否则商品名中的生僻字 / emoji 会写入失败。
