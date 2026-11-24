# 系统架构说明

> 本项目的落地方式是：**按业务域划分的模块化单体（Modular Monolith）+ 独立网关 + 独立数据库 + 前端静态服务分离，并以 Docker Compose 做分布式编排**。
> 这是在交付成本与扩展性之间较稳妥的折中：既保证业务边界清晰、可拆分，又不引入注册中心 / 配置中心带来的运维复杂度。

---

## 1. 总体架构

```
                        ┌─────────────────────────────┐
   浏览器  ──:8080──▶   │  gateway (Nginx, 单入口)     │
                        │  /        → frontend-user    │
                        │  /admin/  → frontend-admin   │
                        │  /api/    → backend          │
                        └──────────────┬──────────────┘
                                       │ ecommerce-net (bridge)
        ┌──────────────────────────────┼──────────────────────────────┐
        ▼                              ▼                              ▼
┌───────────────┐            ┌──────────────────┐            ┌────────────────┐
│ frontend-user │            │ backend (8080)   │            │ frontend-admin │
│ Nginx + SPA   │            │ Spring Boot      │            │ Nginx + SPA    │
└───────────────┘            │ 模块化单体        │            └────────────────┘
                             └────────┬─────────┘
                                      │ JDBC (3306)
                                      ▼
                             ┌──────────────────┐
                             │ mysql:8.0        │
                             │ volume mysql-data│
                             └──────────────────┘
```

**要点**

| 关注点 | 设计 |
|--------|------|
| 唯一入口 | 只有 `gateway` 暴露宿主端口 `8080`；backend / 两个前端不对外暴露宿主端口；mysql 通过 `3307:3306` 映射到宿主 **3307**，既便于本机排查，又别让与本机已安装的 MySQL(3306) 冲突 |
| 服务发现 | Docker 内置 DNS：`backend`、`frontend-user`、`frontend-admin`、`mysql` 即服务名，Nginx/JDBC 直接按服务名寻址 |
| 健康依赖 | `backend` 等 `mysql` 变为 healthy 才启动；`gateway` 等 `backend` healthy（另两个前端 started）；网关自身暴露 `/health` 探针 |
| 数据持久化 | MySQL 数据卷 `mysql-data`，容器重建数据不丢 |
| 配置外置 | 连接信息 / JWT 密钥均通过环境变量注入，不写死 |
| 路径边界 | `/admin` 未带尾斜杠时 301 跳 `/admin/`，别让落到用户端 `location /`；`/api/**` 原样转发（后端 `context-path=/api`） |
| 跨域 | 同域部署不触发 CORS；跨域由后端 `WebConfig` 统一下发响应头，**网关不再重复添加**（别让 `ACAO: *` 与 `allowCredentials` 冲突） |
| 端职责 | 用户端与管理端是两套独立 SPA，仅共享 `/api`；用户端**不提供后台入口**，管理端地址由运营直接访问 |

---

## 2. 后端：模块化单体

按**业务域**纵向切分，每个域拥有自己的 `controller → service → mapper → entity` 完整链路，域之间只通过 Service 接口调用，不跨域直接访问 Mapper。

```
com.ecommerce
├── common/            Result、BusinessException、全局异常处理
├── config/            WebConfig（CORS + 拦截器顺序）、MybatisPlusConfig（分页方言 MySQL）
├── interceptor/       LoginInterceptor（JWT + 单设备）、AdminInterceptor（ADMIN / MERCHANT）、PlatformInterceptor（仅 ADMIN）
├── controller/        Auth / Shop / Product / Category / Cart / Order / Stats / Review / Favorite / Address / Coupon / BrowseHistory / Health
├── service + impl/    11 个业务服务接口（category 由 ProductService 承载，店铺域独立 ShopService）
├── mapper/            MyBatis-Plus Mapper
├── entity/ dto/ vo/   分层数据模型
├── task/              OrderTimeoutTask（超时取消）
└── utils/             JwtUtil / PasswordUtil / SessionStore
```

**业务域划分**

| 域 | 职责 | 对外接口前缀 |
|----|------|--------------|
| 认证用户 | 注册 / 登录 / 资料 / 密码 / 用户管理 | `/api/auth/**` |
| 商品 | 商品 CRUD、列表、搜索、详情、热销 | `/api/product/**` |
| 分类 | 分类 CRUD、列表 | `/api/category/**` |
| 购物车 | 加购 / 改量 / 删除 / 勾选 | `/api/cart/**` |
| 订单 | 提交 / 支付 / 发货 / 完成 / 取消 / 导出 | `/api/order/**` |
| 优惠券 | 发放 / 领取 / 可用券 / 核销回退 | `/api/coupon/**` |
| 评价 | 发表 / 追评 / 修改 / 删除 / 管理员屏蔽 | `/api/review/**` |
| 浏览足迹 | 记录 / 最近浏览 / 移除 / 清空 | `/api/browse/**` |
| 收藏 | 收藏 / 取消 / 列表 | `/api/favorite/**` |
| 地址 | 地址簿 CRUD / 默认地址 | `/api/address/**` |
| 统计 | 概览 / 趋势 / 热销 / 库存预警 | `/api/admin/stats/**` |
| 店铺 | 店铺资料 / 开店关店 / 归属上下文（多商家） | `/api/shop/**` |
| 好友 | 搜索 / 申请 / 同意 / 拒绝 / 列表 / 删除 | `/api/friend/**` |
| 消息 | 会话 / 历史 / 发送 / 已读 / 未读 / 系统通知 | `/api/message/**` |
| 举报 | 提交 / 我的举报 / 后台分页与处理 | `/api/report/**` |
| 设置 | 隐私与消息设置读取 / 更新 | `/api/setting/**` |

> 共 **16 个业务域**（另有 `/api/health` 探活接口），对应 17 个 Controller。

**为什么是「模块化单体」而不是一上来就拆 16 个服务**

1. 系统功能完整且需稳定运行，单体部署成本最低；
2. 域边界已经按包隔离，后续拆分时可按包整体搬迁为独立 Spring Boot 服务；
3. 跨域事务（下单 = 扣库存 + 写订单 + 用券 + 清购物车）在单体内用本地事务即可保证一致性，拆成微服务后需要引入 Saga / 消息最终一致，属于过度设计。

---

## 2.5 多商家：店铺域与拆单

在模块化单体的基础上增加了一个**店铺域**，把「谁在卖」这一维度补上：

```
user_table.role = MERCHANT ──1:1── shop（owner_id 唯一）
                                     │
                    product.shop_id ─┘        orders.shop_id ── 拆单后每单只属于一家店
```

- 归属：商品建品时自动挂「操作人自己的店铺」（商家=本店，超管=其绑定的店铺，均不接受前端传的 shopId）；订单在下单瞬间按商品所属店铺落到 `orders.shop_id`。
- 拆单：`OrderServiceImpl.submit()` 先按 `shop_id` 分组，再逐店建单，库存扣减、明细写入、购物车清理、优惠券核销都在同一事务内，别让「一半成功」。
- 权限三层：`LoginInterceptor`（认证）→ `AdminInterceptor`（ADMIN / MERCHANT 进后台）→ `PlatformInterceptor`（用户 / 分类 / 评价管理 / 店铺运营仅 ADMIN）。优惠券对商家开放，靠「适用范围」限定：商家只能看到并维护本店券，平台券对其不可见。
- 数据范围：读 / 筛选入口是 `ShopService.currentShopId()`，ADMIN 返回 `null`（不限），MERCHANT 返回本店 id，看板统计、订单导出据此限定；一切写操作用 `requireOperableShopId()` 或等价校验（商品增删改 / 上下架、订单删除 / 发货 / 完成、店铺资料），只作用于自己名下的店铺（商家本店 / 超管的官方旗舰店），替别家店写入一律 403。
- 看板口径：商家看到的「用户数」= 本店下单用户数，「分类数」= 本店商品覆盖的分类数，销售额只统计本店已支付及之后的订单。

详见 [`multi-merchant.md`](multi-merchant.md)。

---

## 3. 一次请求的完整链路

以「用户下单并使用优惠券」为例：

```
Vue (CheckoutView)
  └─ POST /api/order/submit  { receiver..., userCouponId }
        │  Authorization: Bearer <JWT>
        ▼
Nginx gateway  ── /api/ ──▶  backend:8080
        ▼
LoginInterceptor  校验 JWT 签名 + SessionStore 中 token 是否仍为「当前会话」
        ▼
OrderController  @Validated 校验 DTO
        ▼
OrderServiceImpl.submit()  【单本地事务】
  1. 读取购物车中 selected=1 的条目
  2. 逐条条件扣库存：UPDATE product SET stock=stock-n, sales=sales+n WHERE id=? AND stock>=n
     └─ 影响行数为 0 → 抛 BusinessException（事务回滚）
  3. 校验优惠券归属 / 有效期 / 门槛，user_coupon: UNUSED → USED（条件更新）
  4. 计算 totalAmount / discountAmount，写 orders + order_item
  5. 清空已结算购物车条目
        ▼
MySQL (InnoDB, 行锁 + 唯一索引兜底)
        ▼
Result<List<orderNo>> → 单店直接跳订单详情；跨店生成多笔，前端提示合并支付
```

**并发正确性的三道防线**

| 场景 | 手段 |
|------|------|
| 库存超卖 | 条件更新 `WHERE stock >= n` + 影响行数判断 |
| 优惠券重复使用 / 超发 | `user_coupon` 唯一索引 + `status='UNUSED'` 条件更新 + `coupon.claimed < total` 条件更新 |
| 订单状态重复流转 | 所有流转 `WHERE status = 期望值`，影响行数为 0 即拒绝 |

---

## 4. 分布式部署

`docker-compose.yml` 定义 5 个服务、1 个 bridge 网络、1 个数据卷：

| 服务 | 作用 | 是否对外 |
|------|------|----------|
| `mysql` | 业务数据库 | 宿主 3307 → 容器 3306（便于本机排查，生产可移除） |
| `gateway` | 统一入口 / 路由 / CORS | **8080（唯一入口）** |
| `backend` | REST API | 否（内网） |
| `frontend-user` | 用户端静态站 | 否（经 gateway） |
| `frontend-admin` | 管理端静态站 | 否（经 gateway） |

**分布式特征**

- 进程隔离：前端、后端、数据库分别运行在独立容器（独立进程 / 独立文件系统）。
- 网络隔离：仅 gateway 暴露端口，内部服务通过服务名互访。
- 状态外置：业务状态全部落在 MySQL，后端容器可随时重建。
- 构建分离：每个服务独立 `Dockerfile`，可独立构建、独立升级、独立扩缩容。

**横向扩展路径（不在当前交付范围，但架构已预留）**

| 扩展点 | 当前实现 | 扩展方案 |
|--------|----------|----------|
| 后端多实例 | 单实例 | 业务数据已在 MySQL，可扩为多实例：先去掉 `backend` 的 `container_name`（Compose 不允许对固定容器名 `--scale`），再 `docker compose up -d --scale backend=3` 并在 upstream 增加节点；**前提是先把 SessionStore 换成 Redis**，否则「单设备登录」在实例间不一致 |
| 登录态共享 | `ConcurrentHashMap` 内存 SessionStore（无 TTL、进程重启即失效） | 替换为 Redis（`SET user:session:{id} token EX 7d`），接口不变，只换实现类；这也是水平扩展的唯一硬前提 |
| 网关多实例 | 单 Nginx | 外层再加一层 LB（云 LB / HAProxy），或 K8s Ingress |
| 数据库 | 单 MySQL | 主从复制读写分离；MyBatis-Plus 多数据源或 ShardingSphere |
| 静态资源 | Nginx 托管 | 接入 CDN |
| 可观测性 | 容器日志 | 接入 Prometheus + Grafana / ELK |

> 说明：多实例部署时**必须**先把 SessionStore 换成 Redis，否则「单设备登录」在不同实例间不一致。这一点在 `docs/concurrent-login-test.md` 与本文件均有标注，属于已知的、有明确解法的限制。

---

## 5. 关键设计取舍

| 决策 | 选择 | 理由 |
|------|------|------|
| 服务拆分粒度 | 模块化单体 | 当前规模下降低运维成本，域边界已就绪，可渐进拆分 |
| 认证方式 | 无状态 JWT + 有状态 SessionStore | JWT 免查库，SessionStore 提供「挤下线」能力，两者互补 |
| 密码存储 | BCrypt | 自带盐、可调 cost，业界标准 |
| 分页 | MyBatis-Plus 分页插件 | 方言切 MySQL，单页上限 100 防拖库 |
| 数据库 | MySQL 8.0 + utf8mb4 | 真实数据库、支持并发与事务、容器化友好 |
| 前端 | Vue 3 + Vite + Element Plus | 组件生态成熟，构建快，SPA 与网关路由天然契合 |
| 可视化 | 纯 CSS/SVG 图表 | 零额外依赖，包体小，运行稳定 |

---

## 6. 安全设计概览

详见 README「六、安全设计」。架构层面的要点：

1. **最小暴露面**：只有网关一个端口，数据库不直接对公网。
2. **双层拦截**：认证（Login）→ 授权（Admin）按注册顺序执行，防止越权。
3. **归属校验**：所有用户私有资源（购物车 / 订单 / 收藏 / 地址 / 优惠券）均按 `userId` 过滤，杜绝横向越权。
4. **异常不泄露**：500 只返回通用文案，堆栈仅进日志。
5. **密钥外置**：`JWT_SECRET`、数据库口令全部走环境变量。
