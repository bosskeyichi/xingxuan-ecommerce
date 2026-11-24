# 星选商城 · Star Shop

> 一套完整的前后端分离电商系统，涵盖 **用户认证 → 商品浏览 → 购物车 → 下单支付 → 订单流转 → 后台运营** 全链路。
> 技术栈：Spring Boot 3 + MyBatis-Plus + JWT + Vue 3 + Element Plus + Nginx 网关 + Docker 分布式部署。
> 作者：Bosskeyichi（GitHub：[bosskeyichi](https://github.com/bosskeyichi)）

---

## 一、项目概览

| 项目 | 技术栈 | 端口 | 说明 |
|------|--------|------|------|
| `backend` | Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / MySQL 8.0 / JWT / BCrypt | 8080 | 模块化单体后端（17 个 Controller），**16 个业务域**：user / shop / product / category / cart / order / coupon / review / favorite / address / browse / stats / friend / message / report / setting（另含 `/health` 探活） |
| `frontend-user` | Vue 3 + Vite 5 + Element Plus + Pinia | 5173 | 用户端商城（**23 个页面**：首页 / 商品 / 搜索 / 店铺 / 购物车 / 结算 / 订单 / 收藏 / 优惠券 / 对比 / 足迹 / 好友 / 消息 / 我的评价 / 我的举报 / 设置 / 个人中心 …），支持跨店选购与按店铺拆单结算 |
| `frontend-admin` | Vue 3 + Vite 5 + Element Plus | 5174 | 双角色后台（**14 个页面**）：超级管理员管全站，普通管理员（商家）只管自己店铺（概览 / 商品 / 订单 / 店铺 / 优惠券 / 用户 / 分类 / 好友 / 消息 / 举报 / 设置 / 个人中心） |
| `gateway` | Nginx | 8080（容器内 80） | 统一入口网关，按路径路由到各服务 |

### 这版都做了什么

- 多商家（多店铺）：`shop` 表 + `user_table.role = MERCHANT`，商品挂 `shop_id`；购物车按店铺分组，**结算时按店铺自动拆单**（一个事务内生成 N 笔订单），订单发货、看板统计、CSV 导出全部按店铺隔离；拆单不拆支付，结算页可直接「全部支付」，订单页也能勾选多笔「合并支付」（`POST /order/pay/batch`）；平台管理员（ADMIN）看全站，商家（MERCHANT）只看本店，详见 [`docs/multi-merchant.md`](docs/multi-merchant.md)
- 认证授权：JWT 令牌 + BCrypt 加盐哈希；`LoginInterceptor` 认证 → `AdminInterceptor` 后台身份（ADMIN / MERCHANT）→ `PlatformInterceptor` 平台级功能（用户 / 分类 / 评价管理 / 店铺运营）三级校验；优惠券对商家开放，靠「适用范围」限定
- 单设备登录（互斥）：JWT 带随机 `jti`，SessionStore 按 `userId + scope` 保留最新 token，用户端（USER）与后台（ADMIN）各一份，管理员可同时前台浏览、后台操作；同一端重复登录仍会挤掉旧会话。用户端登录态存 `sessionStorage`（标签页隔离），管理端存 `localStorage`（多标签共享），前端每 10s / 窗口聚焦 / 路由切换校验会话，被挤下线即提示并跳转登录页
- 库存防超卖：下单时使用 **条件更新**（`SET stock = stock - n WHERE stock >= n`）+ 影响行数判断
- 订单状态机：`PENDING_PAYMENT → PAID → SHIPPED → COMPLETED`，所有状态流转使用条件更新保证幂等
- 超时自动取消：`OrderTimeoutTask` 每 **5 分钟**扫描一次（`fixedDelay`，超时阈值 30 分钟可配），自动取消超时未支付的订单、回退库存并还原购物车；支付 / 发货 / 完成 / 取消全部为条件更新，保证幂等
- 新增业务：商品收藏（Favorite）、商品评价（Review，需购买后评价）、收货地址簿（Address，默认地址/下单选用）、个人资料编辑与修改密码
- 账户中心：顶栏只保留 **一个头像入口**，点击展开账户面板（个人中心 / 我的订单 / 我的收藏 / 浏览足迹 / 优惠券中心 / 收货地址 / 编辑资料 / 退出登录）；资料支持昵称、邮箱、手机号与**自定义头像**（前端居中裁剪压缩为 200×200 后入库，`user_table.avatar` 为 TEXT）
- 一个功能只留一个入口：同一功能全站只保留一个主入口，收藏 / 优惠券 / 足迹统一收进头像面板，顶栏不再重复罗列；商品卡片与详情页共用「收藏 / 对比 / 加购」三个动作按钮，不再叠加第二套；个人中心侧栏只放资料相关页签，账户类跳转交回头像面板
- 用户端和后台互不串门：用户端只服务购物场景，**顶栏与页脚均不放「商家后台」入口**（管理端由运营访问 `:5174` / 网关 `/admin/`）；页脚限定为单行信息栏（品牌 + 站点导航 + 站点说明），不再做企业官网式四栏
- 用户端不说技术黑话：用户端不再出现 BCrypt / JWT / 订单状态机等实现细节，登录页、注册页、个人中心统一改为购物权益话术；商品详情只保留商品编号、库存锁定与退换政策
- 优惠券功能（平台券 + 店铺券）：平台管理员可发「全平台券」或「指定店铺券」，商家只能发本店券；`coupon.shop_id` 为空即全平台，非空仅限该店铺订单使用。用户「优惠券中心」领取，结算时按店铺与门槛匹配抵扣（`coupon` / `user_coupon` 两张表，下单与取消订单原子回退）
- 商品对比：首页 / 详情 / 列表卡片「对比」一键加入，底部对比栏最多 4 件，独立对比页横向参数对照
- 搜索增强：顶栏搜索输入框聚焦弹出「搜索历史 + 热门词」，本地存储、一键复用
- 订单导出：管理后台一键导出全部订单为 CSV（UTF-8 BOM，Excel 直接打开）
- 角色管理（超级管理员 ↔ 普通管理员）：超级管理员可在用户管理里把普通用户「设为普通管理员」，系统自动创建一家店铺；取消管理员前需先清空商品与订单。角色变更会强制该账号重新登录
- 用户数据重置：一键「重置数据」，清空订单 / 购物车 / 优惠券 / 收藏 / 评价 / 地址 / 足迹并回补库存；商家账号额外清空本店商品与优惠券、店铺资料恢复默认；账号资料（昵称 / 邮箱 / 手机号 / 头像）恢复初始，**密码重置为 `123456`**。重置结果持久，重启不会被示例数据还原
- 后台个人中心：超级管理员与普通管理员都能维护自己的昵称、邮箱、手机号、头像并修改密码（与用户端账号互通，但两端登录会话独立）
- 历史订单删除：用户端与管理端都支持单笔 / 批量删除已完成、已取消的历史订单，带二次确认与终态校验，别误删进行中的订单
- 商品评价功能：确认收货后自动弹出评价窗，支持评分、评价、追加评价、修改与删除；商品页展示评价与追评时间；管理端商品管理内置评价管理，可屏蔽 / 取消屏蔽或删除用户评价
- 商品检索：列表支持**排序下拉**（综合 / 销量 / 价格升降 / 最新）、价格区间、**仅看有货**、每页 12/24/36 条与「已选条件」标签；关键词**四级渐进匹配**（商品名 + 搜索别名 → 剥掉修饰词前后缀 → 分类名 → 描述），命中即停：搜「鞋子 / 手机 / 耳机」走第一级别名或名称，搜「男鞋 / 男士运动鞋 / 苹果手机」自动去掉「男/男士/苹果」这类修饰词再匹配，都不行才退到分类整类与描述兜底并提示原因；100 件商品都配了搜索别名（`product.keywords`），管理端新增/编辑商品也可填；种子数据含 100 件商品
- 商品上下架与删除：下架 = `status=0`，商品仍留在管理端列表；删除 = 逻辑删除，商品从管理端与用户端同时消失，购物车 / 收藏 / 足迹 / 评价一并清理，历史订单因 `order_item` 保存商品快照而不受影响
- 个性化推荐：账号浏览足迹（服务端按用户保存，最近 12 件，跨设备同步，随「重置数据」清空；头像下拉「浏览足迹」独立页 + 单条移除 / 一键清空）还有「猜你喜欢」，按你看过的类目找同类热销商品
- 商品配图：100 件商品**逐一对应**真实商品图（`frontend-*/public/img/products/{id}.jpg`）。
  按商品全名联网检索候选图 → **标题相关性校验**（商品型号 / 品牌词必须与结果标题命中，并排除“合集、评测对比、套装”类图片）
  → 电商图源优先 → Pillow 统一 640×640 白底 JPEG（共约 5 MB）；缺失时按分类回退静物海报 / 衬线字占位，绝无灰底破图
- 版式与易用性：细线分组筛选工具栏、列表入场动效、悬停回到顶部按钮、登录/注册页一键返回商城、键盘焦点可见
- 数据可视化：后台 Dashboard 含销售趋势柱图、热销排行、库存预警（纯 CSS/SVG 实现，零额外依赖）；平台管理员可用「全平台 / 单店铺」下拉把整套统计下钻到某家店，并查看按累计销售额排序的「店铺经营排行」（点行即切换数据范围），商家则固定为本店口径
- 容器化与分布式部署（已经跑通）：5 个服务独立镜像 + 独立网络 + 独立数据卷，**已部署到 Ubuntu 24.04 服务器**（`ubuntu-server` / `VM_IP`），宿主机与局域网设备统一通过网关 `8080` 访问；18 项脚本自动验收全部通过（局域网访问另由人工确认），详见 [`docs/docker-deploy-server.md`](docs/docker-deploy-server.md)
- 部署这块：`.env` 外置配置（端口 / 数据库密码 / JWT 密钥 / JVM 参数）、5 个服务全部带 healthcheck（网关等后端就绪再启动）、`GET /api/health` 探活接口、`deploy/deploy-server.sh` 一键部署与 `deploy/verify_deployment.py` 一键验收
- 好友和消息：好友功能 `USER↔USER`、`MERCHANT↔MERCHANT` 采用**申请-同意制**（`friend.status`），`ADMIN↔MERCHANT` 为系统默认好友（关系不落库）；消息分三场景，好友私聊 `FRIEND`、买家咨询客服 `SERVICE`、系统通知 `SYSTEM`；发送权限按场景校验（`FRIEND` 校验好友关系、`SERVICE` 接收人强制为店铺店主、`SYSTEM` 由系统或官方发出），防止冒充；官方（ADMIN）消息**豁免**对方「拒绝接收」开关，事务性系统通知（好友申请、举报结果）**强制送达**，不会被用户设置静默丢弃
- 官方通知：超级管理员可在「用户管理 → 发通知」直接给任意用户发送系统通知（强制送达）。这样既满足「官方主动联系普通用户」的诉求，又不破坏好友模型（ADMIN 与 USER 本不构成好友关系）
- 举报管理：用户可举报商品 / 店铺；平台管理员看全部举报，商家只看本店（`shopService.currentShopId()` 限定范围）；处理结果以系统通知回馈举报人，用户端「我的举报」可查看处理进度
- 隐私开关：用户可分别开关「接收好友消息 / 客服消息 / 系统通知」与新消息提示音；官方与事务性系统通知不受开关限制，保证关键通知必达

---

## 二、快速开始

### 环境要求

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | 17+ | 已验证 JDK 23 可正常编译运行 |
| Node.js | 18+ | 推荐 20 LTS |
| Maven | 3.8+ | 项目内置 `backend/maven/` 免安装 |
| MySQL | 8.0 | 项目数据库（可用 Docker 一键启动，见 `docs/database.md`） |
| Docker（可选） | 20+ | 用来容器化 / 分布式部署（含 mysql 服务） |

### 方式零：一键安装环境（Windows，推荐首次使用）

项目根目录提供了 `setup.ps1`，会自动检测并补齐运行环境、下载全部依赖：

```powershell
# 在项目根目录执行（建议「以管理员身份运行」PowerShell）
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

脚本按 **4 步**跑，全部使用国内镜像，无需科学上网：

| 步骤 | 内容 | 检测 / 安装策略 | 镜像源 |
|------|------|----------------|--------|
| 1/4 | JDK 17+ | 已安装则跳过；否则 **winget → 便携 zip → Chocolatey** 依次兜底 | 清华 Adoptium 镜像 / GitHub Releases |
| 2/4 | Node.js 18+ | 已安装则跳过；同上三级兜底（便携版 Node 20.18.0） | npmmirror / 清华 nodejs-release |
| 3/4 | Maven 依赖 | 调用内置的 `backend\maven\bin\mvn.cmd dependency:resolve` | 阿里云 Maven 公共仓库 |
| 4/4 | npm 依赖 | 分别跑 `frontend-user` 与 `frontend-admin` 的 `npm install`（`node_modules` 已存在则跳过） | `registry.npmmirror.com`（根目录 `.npmrc`） |

其他要点：

- 便携安装：winget 不可用时，JDK / Node 会解压到 `%USERPROFILE%\tools\`，并自动写入**用户级** `PATH` 与 `JAVA_HOME`，不需要管理员权限。
- 执行策略：系统默认禁止运行脚本，需加 `-ExecutionPolicy Bypass`（只对本次进程，不修改系统设置）。
- 生效方式：脚本首次安装 JDK / Node 后，请**关闭并重新打开 PowerShell** 再执行启动命令，让新的 `PATH` 生效。
- 幂等：重复执行安全，已满足条件的步骤会显示 `[SKIP]`。
- 结束提示：全部成功后会打印三个窗口的启动命令，照抄即可。

### 方式一：本地开发（推荐）

```powershell
# 0) 启动 MySQL（首次，或使用本机已装 MySQL 8.0）
docker run -d --name ecommerce-mysql -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=ecommerce -p 3307:3306 mysql:8.0

# 1) 启动后端（Windows，使用内置 Maven）
cd backend
.\maven\bin\mvn.cmd spring-boot:run
#   Linux / macOS：已安装 Maven 3.8+ 时直接执行 mvn spring-boot:run

# 2) 启动用户端（新终端）
cd frontend-user
npm install
npm run dev          # http://localhost:5173（已监听 0.0.0.0，局域网可用 http://<本机IP>:5173 访问）

# 3) 启动管理端（新终端）
cd frontend-admin
npm install
npm run dev          # http://localhost:5174（同样支持局域网访问）
```

### 方式二：Docker 一键部署

```bash
docker compose up -d --build
# 访问 http://localhost:8080        用户端
# 访问 http://localhost:8080/admin/ 管理端（不带尾斜杠会 301）
# 后端 API: http://localhost:8080/api
# MySQL:  mysql:8.0 容器（数据卷 mysql-data 持久化）
```

### 方式三：服务器（Ubuntu）分布式部署（这次已经跑通）

在独立 Ubuntu 服务器上从源码构建并运行 5 个容器，宿主机与局域网设备通过网关统一访问：

```bash
# 1) 上传源码（排除 node_modules / dist / target）后在服务器上解压
tar -xzf ec-deploy-src.tar.gz -C ~/ecommerce-system && cd ~/ecommerce-system

# 2) 生成配置（随机 JWT 密钥 + 端口 / 密码）
cp .env.example .env

# 3) 一键构建并启动，脚本会自动等待网关就绪并打印访问地址
bash deploy/deploy-server.sh up

# 4) 从任意机器验收（默认 http://127.0.0.1:8080）
python deploy/verify_deployment.py http://HOST_IP:8080
```

| 项目 | 值 |
|------|-----|
| 服务器 | Ubuntu 24.04.3 LTS，`ubuntu-server`，`VM_IP`（VirtualBox NAT，宿主端口转发 8080/2222/3307） |
| 访问地址 | 用户端 `http://HOST_IP:8080/` ｜ 管理端 `http://HOST_IP:8080/admin/` ｜ API `http://HOST_IP:8080/api/health` |
| 容器 | `mysql`(healthy) / `backend`(healthy) / `frontend-user`(healthy) / `frontend-admin`(healthy) / `gateway`(healthy) |
| 验收 | 18/18 脚本自动验收通过（健康检查、静态站点、`/admin` 跳转、登录、购物车、订单、越权拦截、单设备登录互斥、公开列表不泄露下架商品），局域网访问另由人工确认 |

完整拓扑、`docker compose ps` 输出、镜像体积、验收明细与踩坑记录见 [`docs/docker-deploy-server.md`](docs/docker-deploy-server.md)。

### 内置账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 超级管理员 | `admin` | `123456` | 可进入管理后台，看全平台数据、管理用户与店铺、发平台券；同时是 1 号店「星选官方旗舰店」店主，可给本店建品、给本店订单发货 / 标记完成，别家店的商品与订单只读 |
| 普通管理员 | `merchant1` | `123456` | 2 号店「极客数码专营店」，后台只看到本店商品、订单、优惠券与看板 |
| 普通管理员 | `merchant2` | `123456` | 3 号店「生活优选馆」，后台只看到本店商品、订单、优惠券与看板 |
| 普通用户 | `alice` / `bob` / `charlie` / `david` | `123456` | 仅可访问用户端，可跨店加购与结算 |

> 登录页不展示账号提示（保持登录页视觉简洁），账号见上表或 `docs/quick-start.md`。

---

## 三、项目结构

```
ecommerce-system/
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/ecommerce/
│   │   ├── common/Result.java            # 统一响应封装
│   │   ├── config/
│   │   │   ├── WebConfig.java            # CORS + 拦截器注册（认证 → 授权）
│   │   │   └── MybatisPlusConfig.java    # 分页插件（单页上限 100）
│   │   ├── controller/                   # Auth / Shop / Product / Category / Cart / Order / Coupon / Stats / Review / Favorite / Address / BrowseHistory / Friend / Message / Report / Setting / Health（17 个）
│   │   ├── dto/  entity/  mapper/  vo/   # 分层数据模型
│   │   ├── interceptor/
│   │   │   ├── LoginInterceptor.java     # JWT 认证 + 单设备登录
│   │   │   ├── AdminInterceptor.java     # 后台身份：ADMIN / MERCHANT
│   │   │   └── PlatformInterceptor.java  # 平台级功能仅 ADMIN（★多商家新增）
│   │   ├── service/  service/impl/       # 业务接口与实现（含 CouponService）
│   │   ├── task/OrderTimeoutTask.java    # 订单超时自动取消（★新增）
│   │   ├── exception/                    # 全局异常处理
│   │   └── utils/                        # JwtUtil / PasswordUtil / SessionStore
│   ├── src/main/resources/
│   │   ├── application.yml               # 主配置（JWT / 分页 / 订单超时 / db-type: mysql）
│   │   ├── application-dev.yml           # MySQL 8.0 + SQL 初始化（环境变量可覆盖连接信息）
│   │   ├── db/schema-mysql.sql           # 幂等建表（CREATE TABLE IF NOT EXISTS，utf8mb4）
│   │   └── db/data-mysql.sql            # 幂等种子数据（INSERT ... FROM DUAL WHERE NOT EXISTS）
│   ├── Dockerfile
│   └── pom.xml
├── frontend-user/                 # 用户端 Vue 3
│   └── src/
│       ├── api/  router/  stores/  utils/
│       ├── components/ProductCard.vue   # 商品卡片（收藏 / 对比浮层、售罄遮罩、加购）
│       ├── components/CompareBar.vue    # 全局商品对比栏
│       ├── layouts/MainLayout.vue       # 顶栏（搜索历史）+ 页脚
│       ├── views/                       # 23 页：Home(分类目录+轮播+闪购+筛选商品流) / ProductDetail / Shop(店铺主页) / ShopList(店铺大全) / Cart / Checkout / OrderList / OrderDetail / Profile / Favorites / Coupons / Compare / Search / Categories / Footprint / Friends(好友) / Messages(消息中心) / MyReviews / MyReports / Settings(隐私与消息设置) / UserProfile / Login / Register
│       └── styles/main.css + layout.css # 设计令牌 + 页面组件样式
├── frontend-admin/                # 管理端 Vue 3
│   └── src/
│       ├── api/  router/  utils/
│       ├── layouts/AdminLayout.vue      # 侧边栏（按角色过滤菜单）+ 店铺标识顶栏
│       ├── views/                       # 14 页：Dashboard / ProductManage / OrderManage / ShopManage / UserManage / CategoryManage / CouponManage / FriendView(好友) / MessageView(消息) / ReportManage(举报治理) / SettingView(隐私与消息) / UserProfileView / ProfileView / Login
│       └── styles/main.css
├── gateway/nginx.conf             # 网关路由配置
├── deploy/
│   ├── deploy-server.sh           # 服务器一键部署（自检 → 构建 → 启动 → 健康等待 → 打印地址）
│   └── verify_deployment.py       # 部署验收脚本（18 项端到端检查）
├── docker-compose.yml             # 五服务编排（mysql / gateway / backend / frontend-user / frontend-admin）
├── .env.example                   # 部署配置模板（端口 / 数据库密码 / JWT 密钥 / JVM 参数）
├── setup.ps1                      # Windows 一键环境安装（JDK/Node/Maven/npm，全镜像加速）
├── .npmrc                         # npm 镜像指向 registry.npmmirror.com
└──docs/                          # 项目文档

```

---

## 四、订单状态机

```
                 ┌──────────────────────────────────────┐
                 │                                      │
  [提交订单] ──▶ PENDING_PAYMENT ──[用户支付]──▶ PAID ──[商家发货]──▶ SHIPPED
                     │                                                  │
                     │                                       [用户确认收货 / 商家标记完成]
                      │                                                  │
                      └──[用户取消 / 30分钟超时自动取消]──▶ CANCELLED      ▼
                                    (回退库存 + 还原购物车)          COMPLETED
```

所有状态流转均使用 **条件更新 + 影响行数判断**，杜绝并发场景下的重复支付、重复发货等问题。

---

## 五、数据库设计

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `user_table` | 用户 | username(唯一) / password(BCrypt) / role(USER/MERCHANT/ADMIN) / status / deleted |
| `shop` | 店铺（多商家） | owner_id(唯一) / name / intro / cover / status |
| `category` | 商品分类 | name / icon / sort_order / parent_id |
| `product` | 商品 | shop_id(所属店铺) / name / price(DECIMAL) / stock / cover / category_id / sales / status |
| `cart` | 购物车 | user_id / product_id / quantity / selected |
| `orders` | 订单 | order_no(唯一) / shop_id(所属店铺) / user_id / total_amount / status / 收货信息 / 各状态时间戳 |
| `order_item` | 订单明细 | order_id / product_id / 商品快照 / price / quantity / subtotal |
| `favorite` | 商品收藏（新增） | user_id / product_id |
| `review` | 商品评价（新增） | user_id / product_id / order_id / rating(1-5) / content |
| `address` | 收货地址簿（新增） | user_id / receiver_name / receiver_phone / region / detail / is_default |
| `coupon` | 优惠券（新增，支持店铺券） | shop_id(NULL=全平台) / name / description / threshold / discount / total / claimed / 有效期 / status |
| `user_coupon` | 用户优惠券（新增） | user_id / coupon_id / status(UNUSED/USED) / order_id / used_at |

业务表均含 `deleted` 字段，使用 MyBatis-Plus **逻辑删除**；上表为常用 12 张，另有 `review_append`（追评）、`review_hidden`（屏蔽记录）、`browse_history`（浏览足迹）、`user_setting`（隐私与消息设置）、`friend`（好友关系）、`message`（消息）、`conversation_hide`（会话隐藏）与 `report`（举报），加上 `seed_meta`（初始化标记），共 **21 张**（20 张业务表 + 1 张初始化标记表），完整字段见 [`docs/database.md`](docs/database.md)。

**种子数据规模**：7 个账号（1 超级管理员 + 2 商家 + 4 普通用户）/ 3 家店铺 / 6 个分类 / **100 件商品**（逐一对应真实配图 `/img/products/{id}.jpg`）/ 6 张优惠券（4 平台券 + 2 店铺券）/ 2 笔历史订单 / 3 条评价 / 3 条地址 / 1 条收藏。好友、消息、举报、足迹、购物车、已领券为运行时产生，**不含预置数据**。

**关键约束**：全库**不使用数据库外键**，关联一致性由应用层保证；收藏为**物理删除**（别让唯一键残留导致取消后无法再收藏）；浏览足迹服务端保留最近 **12 条**；评价唯一键 `(user_id, product_id, deleted)`，删除时置 `deleted = 自身 id` 作墓碑，允许重新评价；优惠券领取用 `claimed < total` 条件更新防超发，用户券唯一索引 + `status` 条件更新防重复使用。

---

## 六、安全设计

| 措施 | 实现 |
|------|------|
| 密码存储 | BCrypt 加盐哈希（`PasswordUtil`），永不明文 |
| 接口认证 | `LoginInterceptor` 校验 JWT + SessionStore 单设备登录 |
| 接口授权 | `AdminInterceptor` 只放行 `ADMIN` / `MERCHANT`；`PlatformInterceptor` 保证用户、分类、评价管理、店铺运营仅平台管理员可用；优惠券按店铺归属校验，商家只能维护本店券 |
| 数据隔离 | 读 / 筛选走 `ShopService.currentShopId()`（商家=本店，超管=不限），看板统计、订单导出按此限定；一切写操作（商品增删改 / 上下架、订单删除 / 发货 / 完成、店铺资料）走 `requireOperableShopId()` 或等价校验，只认自己名下的店铺，超管对别家店只读，跨店操作返回 403 |
| 越权防护 | 订单/购物车/收藏均按 `userId` 归属校验，`detail` 校验订单归属 |
| 异常处理 | 兜底异常只打日志，不返回堆栈，别让泄露表结构 |
| 参数校验 | Jakarta Validation + `@Validated`，分页大小上限 100 |
| SQL 安全 | MyBatis-Plus 参数绑定；搜索关键词清洗 `%` `_` 通配符 |
| 自保护 | 超级管理员不能禁用 / 删除 / 重置自己，也不能被删除或调整角色；店主账号不能直接删除（需先取消普通管理员身份） |
| 注册约束 | 注册强制 `role=USER`，无法越权注册成管理员；邮箱**唯一**，应用层查重 + 数据库 `uk_user_email` 唯一索引（启动时幂等补建，存在重复值时跳过并告警）双重保障 |
| 消息安全 | `FRIEND` 消息校验好友关系、`SERVICE` 消息接收人强制为店铺店主、`SYSTEM` 由系统或官方发出，防止冒充；官方消息与事务性系统通知豁免接收方开关 |
| JWT 密钥 | 支持环境变量 `JWT_SECRET` 注入，生产环境必须设置 |

---

## 七、本次迭代：缺陷修复与功能增强

详见 📄 [`docs/defects-and-fixes.md`](docs/defects-and-fixes.md)：全量审计 **33 项**（P0 致命 11 / P1 严重 9 / P2 一般 8 / P3 优化 5），其后 10 轮迭代整改合计 70+ 项（最新一轮见「二十三、安全加固、数据一致性与前端抗造」）。

关键修复包括：

1. **后台接口越权**（致命）：普通用户可直接调用所有 `/admin/**` 接口 → 新增 `AdminInterceptor`
2. **库存超卖**（致命）：先查后改导致并发超卖 → 改为原子条件更新
3. **每次启动删库**（致命）：`schema.sql` 含 `DROP TABLE` → 改为 `CREATE TABLE IF NOT EXISTS` + 幂等种子数据
4. **订单号碰撞**：时间戳+随机6位 → 改为时间戳+UUID片段
5. **购物车负数量**：可通过负数刷库存 → 增加数量边界校验
6. **订单无法取消**：`PENDING_PAYMENT` 判断逻辑错误 → 修复状态机并回退库存
7. **统计口径错误**：今日/总销售额统计了已取消订单 → 改为按 `paid_at` 且仅统计已支付及之后状态
8. **N+1 查询**：订单列表逐条查明细 → 改为批量装载
9. **商品删除与下架意思混淆**：删除曾被实现为「有订单就转下架」，用户点删除后商品仍留在列表。现改为：下架 = `status=0` 保留在管理端；删除 = 逻辑删除并从管理端/用户端消失，`order_item` 快照保证历史订单不受影响，同时清理购物车/收藏/足迹/评价
10. **线上控制台暴露**：早期 H2 控制台 `web-allow-others: true` → 改为 `false`；后续数据库迁移至 **MySQL 8.0**（独立服务 + 网络隔离），彻底消除内置控制台暴露风险
11. **500 错误泄露堆栈**：`e.getMessage()` 直接返回 → 改为通用提示 + 日志记录

新增功能：商品收藏、商品评价与评分、订单确认收货、订单超时自动取消、收货地址簿（结算页直接选用默认地址）、商品列表价格区间筛选与排序、个人资料编辑、修改密码、后台销售趋势/热销排行/库存预警、商品热销接口、后台分页查询。
**这轮（2026-10）迭代新增**：数据库由 H2 切换为 MySQL 8.0；优惠券功能（领取 / 结算抵扣 / 后台发放）、商品对比、搜索历史与热门词、订单 CSV 导出。

这轮修复：管理端/用户端 HTTP 401（后端重启导致内存会话失效）此前会整页报错，现统一清除本地凭据并跳转登录页。

**2026-10 第二轮整改（用户端体验 / 角色边界 / 工程细节）**：

| 类别 | 问题 | 处理 |
|------|------|------|
| 角色边界 | 用户端页脚与顶部通告条放了「商家后台」外链，普通用户会误入管理端 | 两处入口全部移除，页脚改为单行信息栏；管理端仍可通过 `:5174` 或网关 `/admin/` 访问 |
| 代码分层 | 用户端 `api/index.js` 混入 `orderApi.exportCsv`、`adminCouponApi`（管理员接口） | 从用户端 API 层删除，防止越权调用与无效代码 |
| 用户文案 | 登录/注册/个人中心/商品详情出现 BCrypt、JWT、订单状态机、实现说明 | 全部改为面向用户的购物权益话术 |
| 页面冗长 | 首页 7 个区块，「买手笔记」推荐语与商品无关、「服务保障」与承诺条重复 | 删除这两块，首页限定为 5 块，闪购进度条改用真实销售占比 |
| 加载与容错 | 订单列表/详情、结算页、搜索/首页无 loading 或失败兜底，首屏会闪空态 | 统一补 `v-loading` 与 `catch` 兜底，空态文案分「加载中 / 无数据 / 请求失败」 |
| 表单一致性 | 结算页手机号正则 `^1\d{10}$` 与地址簿 `^1[3-9]\d{9}$` 不一致；校验失败产生未捕获异常 | 统一正则，`validate().catch(() => false)` 守卫 |
| 后台缺陷 | 已支付订单同时显示「发货」和「—」；优惠券页用了不存在的 `page-sub` / `panel` 类；删除当前页最后一条停在空白页；Dashboard 无 loading | 逐项修复，并给优惠券表单补必填与金额校验 |
| 网关 | `/admin` 无尾斜杠会落到用户端首页；Nginx 与后端同时下发 CORS 头 | 增加 `301 /admin/`，删除网关 CORS 头，统一由后端 `WebConfig` 处理 |

**2026-10 第三轮整改（管理端对齐用户端，共 21 项）**：

| 类别 | 问题 | 处理 |
|------|------|------|
| 订单操作 | 8 列合计 1280px 撑破 830px 内容区，`fixed` 操作列被覆盖，看不到「发货」 | 重排为 6 列（约 800px），收货人移入详情抽屉 |
| 按钮可见性 | `.el-button--primary` 的白色文字 + `.is-text` 的透明背景 → 文字按钮白底白字 | 补齐 `.is-text` 各类型的文字色与 hover 底色 |
| 订单流转 | 无详情、无状态筛选、无待发货提醒 | 详情抽屉（商品 / 收货 / 金额 / 时间线 / 底部固定「发货」「标记完成」）、状态标签页 + 数量、侧栏待发货角标、`?status=PAID` 深链 |
| 视觉统一 | 管理端主按钮是墨黑、Logo 无品牌感、导航激活白底、标题无星标 | 主按钮 / 金额改朱砂红，Logo 改红底白字「星」，激活项红底白字，标题加金色四角星，登录页装饰字与强调色统一 |
| 页面质量 | 优惠券页按钮错位、状态用 `el-tag`、无进度；商品改状态要开弹窗；用户头像不显示；分类显示英文 slug；概览 5 卡换行 | 逐项重排：进度条 + 药丸 + 查询主按钮；列表内 switch 上下架；头像回退首字；图标 slug 中文化；概览 4 卡 + 状态网格可点击 |
| 种子数据 | 3 组商品重名、蓝牙音箱归到图书、`新 balance 574` 拼写错误 | 重命名区分（保留 100 SKU）、分类改回手机数码、修正中英文名称 |

**2026-11 第十一轮整改（安全加固 / 一致性 / 前端抗造）**：

| 类别 | 问题 | 处理 |
|------|------|------|
| 消息送达 | `sendSystem` 在入库前校验「接收方开关」，用户关闭系统通知后，**好友申请、举报结果等事务性通知被静默丢弃**，与设计约定（SYSTEM 豁免开关）矛盾 | 系统通知改为**强制送达**，不受接收方开关限制；另新增 `sendOfficial`，供官方（ADMIN）主动通知使用 |
| 商品可见性 | 商品公开详情接口未校验 `status`，下架商品仍可通过直接访问详情接口拿到数据 | 详情接口限定：下架 / 不存在一律返回「商品已下架或不存在」（前端已有友好空态） |
| 提权与脏数据 | 注册邮箱仅在应用层查重，极端并发下可能重复 | 启动时幂等补建 `user_table.email` 唯一索引 `uk_user_email`；补建前先查重，存在重复非空邮箱则告警跳过、不阻断启动 |
| SQL 安全 | 评价已购校验用 `inSql` 拼接 `userId` 字符串 | 改为 MyBatis-Plus **参数化子查询**，消除字符串拼接 |
| 路由守卫 | 用户端「我的优惠券」路由缺少 `auth` 守卫，未登录直接进入 | 补充 `meta.auth`，未登录引导登录 |
| 官方触达 | ADMIN 与 USER 不构成好友关系，官方无法联系普通用户 | 新增官方通知通道：后台「用户管理 → 发通知」以平台官方身份发送系统通知，强制送达（`POST /auth/admin/users/{id}/notify`） |
| 前端体验 | 官方 / 商家账号在用户端搜索好友时，普通用户不在可搜范围内，空结果易被误认为「搜不到人」 | 搜索无结果时按角色给出差异化引导，指向后台「发通知」 |
| 前端抗造 | 路由懒加载偶发失败时内容区空白；`<transition>` 包裹多根组件触发渲染异常；`router-view` 按 `fullPath` 设 key 导致点击会话（改写 query）后页面重建、选中态丢失 | ① 懒加载失败自动重试一次；② 组件缺失时显示「页面加载失败 + 重新加载」而非纯白；③ 移除 `<transition>` 包裹；④ key 改用 `route.path`，仅路径变化才重建 |

---

## 八、UI 设计

详见 📄 [`docs/ui-design.md`](docs/ui-design.md)。

**用户端**：定位「买手严选店」，不用平台货架式首页，暖纸底 `#f7f5f1` + 白卡，朱砂红 `#d8342a` 只用来价格与行动点，星选金 `#a87a3c` 承担品牌识别（商品编号 `NO.007`、标题前四角星、英文栏目标签）。首页为顶部承诺条 + 5 个区块：「分类目录 + 轮播」→「星选闪购倒计时」→「左侧筛选栏 + 4 列商品流（全部选品）」→「今日上新 / 为什么选择星选」→「为你挑选」；商品卡带编号与一句话卖点，顶栏搜索聚焦弹出「搜索历史 + 热门词」，账户类功能统一收进头像面板，对比入口常驻底部对比栏，页脚为单行站点信息栏（无后台入口）。

**管理端**：与用户端共用品牌令牌，墨黑侧边栏 + 朱砂红「星」Logo、红色主按钮与行动点、衬线标题前的金色四角星、暖纸底 `#f6f4f0`。概览页含 4 张 KPI + 7 日销售趋势柱图（纯 CSS）+ 热销 TOP6 + 可点击的订单状态分布 + 库存预警；订单页为状态标签页（带数量、支持 `?status=` 深链）+ 详情抽屉（商品 / 收货 / 金额 / 时间线 / 底部常驻发货与完成）+ CSV 导出；商品页支持列表内直接上下架；用户页展示真实头像与启用状态；优惠券页含领取进度条。侧边栏「订单管理」带待发货角标，60s 与切页自动刷新。

---

## 九、功能验证动线（5 分钟）

1. 用户端注册新账号 → 自动跳转登录
2. 首页浏览 → 店铺下拉切到「极客数码专营店」→ 加购 1 件；再切回全部店铺加购另一家店的商品
3. 购物车按店铺分组 → 勾选跨店商品 → 去结算 → 提交后弹「合并支付」，点「全部支付」一次付清 2 笔订单（也可「稍后支付」，到订单页勾选后合并支付）
4. 单店下单则直接进订单详情，点「模拟支付」→ 列表状态变为「已支付」
5. 用 `merchant1` 登录管理端 → 只看得到 2 号店的订单 → 「发货」；用 `admin` 登录则能看到全部店铺订单（只有本店「星选官方旗舰店」的行有发货按钮，别家店只读）与「店铺管理」页
6. 用户端发表商品评价 → 商品详情页查看评分与评价列表
7. 用户端「优惠券中心」领取满减券 → 结算页选用 → 订单金额立减
8. 商品卡片点「对比」加入 2–4 件 → 底部对比栏 → 进入对比页横向对照
9. 管理端「优惠券」发放新券 → 订单页「导出 CSV」→ 概览页查看销售趋势/热销排行/库存预警

---

## 十、文档索引

| 文档 | 内容 |
|------|------|
| [`README.md`](README.md) | 项目总览（本文件） |
| [`docs/quick-start.md`](docs/quick-start.md) | 快速启动与常见问题 |
| [`docs/api.md`](docs/api.md) | 完整 REST 接口文档 |
| [`docs/architecture.md`](docs/architecture.md) | 总体架构、请求链路与部署拓扑 |
| [`docs/defects-and-fixes.md`](docs/defects-and-fixes.md) | 缺陷分析与修复清单（含界面一致性整改） |
| [`docs/ui-design.md`](docs/ui-design.md) | UI 设计规范与实现（当前代码对应的设计令牌与页面结构） |
| [`docs/deployment.md`](docs/deployment.md) | 本地 / Docker 部署指南 |
| [`docs/docker-deploy-server.md`](docs/docker-deploy-server.md) | **Ubuntu 服务器 Docker 分布式部署实录**（拓扑 / 步骤 / 验收 18 项 / 踩坑） |
| [`docs/multi-merchant.md`](docs/multi-merchant.md) | **多商家改造说明**（模型 / 拆单 / 权限 / 种子数据 / 验证动线） |
| [`docs/database.md`](docs/database.md) | 数据库设计与初始化 |
| [`docs/concurrent-login-test.md`](docs/concurrent-login-test.md) | 单设备登录测试报告 |
| [`docs/ppt/星选商城项目ppt.pptx`](docs/ppt/星选商城项目ppt.pptx) | 项目介绍 PPT（20 页，16:9；覆盖用户端 23 页 / 管理端 14 页 / 16 个业务域 / 21 张表 / 会话存储 / 服务器部署口径） |
| [`项目报告.docx`](项目报告.docx) | 项目报告（Word 文档） |
| [`截图/1.png`](截图/1.png) ｜ [`截图/2.png`](截图/2.png) | 用户端 / 管理端界面截图（界面留档） |

### 仓库文件约定

| 类型 | 是否入库 | 说明 |
|------|----------|------|
| 源码 / 配置 / 文档 / 种子 SQL | 入库 | 交付主体 |
| `node_modules/`、`dist/`、`backend/target/` | 不入库 | 由 `npm install` / `npm run build` / Maven 生成 |
| 日志（`*.log`、`boot.err`） | 不入库 | 运行时产生，已清理 |
| `backend/maven/` | 不入库 | 内置 Maven（10MB），保证无 Maven 环境也能编译；仓库内保留以便离线构建 |
| PPT / DOCX 成品 | 入库 | 用来项目汇报与交付；仓库只保留成品，不附带生成工具 |

---

## 十一、许可证

本项目采用 [MIT License](LICENSE) 开源。项目为电商系统示例实现，所有商品与订单数据均为示例数据，支付流程为模拟实现，不涉及任何真实交易。
