# Ubuntu 服务器 Docker 分布式部署实录

> 本地 `docker compose` 仅验证容器化编排，本文记录的是**真实部署到独立服务器（Ubuntu 虚拟机）并由另一台机器访问**的完整过程与验收结果。
> 部署日期：2026-10-31 ｜ 验收结果：**18/18 项通过**

---

## 一、部署拓扑

```
┌──────────────── Windows 主机（HOST_IP）────────────────┐
│  浏览器：http://HOST_IP:8080/        （用户端）        │
│          http://HOST_IP:8080/admin/  （管理端）        │
│  局域网其他设备：同网段改为本机 IP 即可（并发登录测试用）    │
└───────────────────────────┬─────────────────────────────────┘
                            │  VirtualBox NAT 端口转发
                            │  8080→8080（Web）  2222→22（SSH）  3307→3307（MySQL）
┌───────────────────────────▼─────────────────────────────────┐
│  Ubuntu 24.04.3 LTS 服务器  ubuntu-server / VM_IP           │
│  Docker 29.3.0 · Compose v5.1.1 · 10 vCPU / 19 GiB / 98 GB  │
│                                                             │
│  ┌──── ecommerce-net（bridge 网络，容器间用服务名互访）────┐ │
│  │                                                        │ │
│  │  gateway:80 ──┬─ /api/   ──▶ backend:8080 (Spring Boot)│ │
│  │  (宿主 8080)  ├─ /       ──▶ frontend-user:80  (Vue3)  │ │
│  │               └─ /admin/ ──▶ frontend-admin:80 (Vue3)  │ │
│  │                                     │                  │ │
│  │                                     └─▶ mysql:3306 ────┼─┼─▶ 宿主 3307
│  │                                         （数据卷 mysql-data）│ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

- 对外只暴露一个端口：`8080`（网关）。后端、两个前端、数据库均在内部网络，不直接对外。
- 状态外置：业务数据全部落在 MySQL 容器 + `mysql-data` 数据卷，后端容器可随时重建。
- 配置外置：端口、数据库密码、JWT 密钥全部由根目录 `.env` 注入。

---

## 二、服务器环境

| 项目 | 值 |
|------|-----|
| 操作系统 | Ubuntu 24.04.3 LTS（内核 6.14.0-36-generic） |
| 主机名 / 用户 | `ubuntu-server` / `deployer`（sudo 需密码，部署本身不需要 root） |
| IP | `VM_IP`（VirtualBox NAT，网关 VM_GATEWAY） |
| 规格 | 10 vCPU / 19 GiB 内存（8 GiB Swap）/ 98 GB 磁盘（可用 72 GB） |
| 容器引擎 | Docker 29.3.0，Compose v5.1.1，已配置国内 registry-mirrors |
| 项目路径 | `/home/deployer/ecommerce-system`（源码 13 MB，不含 `node_modules`） |

---

## 三、部署步骤

### 1. 打包源码（排除依赖与产物）

```powershell
# Windows 主机执行
tar -czf ec-deploy-src.tar.gz -C <项目根目录> `
    --exclude=node_modules --exclude=*/node_modules `
    --exclude=dist --exclude=*/dist --exclude=target --exclude=*/target `
    --exclude=maven --exclude=*/maven --exclude=.git .
# 产物约 11 MB（105 个 Java / 34 个 Vue / 206 张商品图）
```

> 只传源码，镜像在服务器上构建：`node_modules`、`dist`、`target`、内置 Maven 全部排除，别让「把 Windows 依赖搬到 Linux」。

### 2. 传输并解压

```bash
scp -P 2222 ec-deploy-src.tar.gz deployer@127.0.0.1:/home/deployer/     # 2222 是 NAT 转发的 SSH 端口
ssh -p 2222 deployer@127.0.0.1
mkdir -p ~/ecommerce-system
tar -xzf ~/ec-deploy-src.tar.gz -C ~/ecommerce-system
```

### 3. 生成部署配置

```bash
cd ~/ecommerce-system
cp .env.example .env
# 生成随机 JWT 密钥（生产必须替换默认值）
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env
docker compose config -q && echo "compose 配置校验通过"
```

`.env` 关键项：

| 变量 | 作用 | 本次取值 |
|------|------|----------|
| `GATEWAY_PORT` | 网关对外端口 | `8080` |
| `MYSQL_HOST_PORT` | MySQL 映射到宿主的端口（容器内 3306） | `3307` |
| `MYSQL_ROOT_PASSWORD` | 数据库 root 密码（后端复用） | `123456` |
| `JWT_SECRET` | JWT 签名密钥 | 随机 64 字符（openssl 生成） |
| `JAVA_TOOL_OPTIONS` | 后端 JVM 参数 | `-XX:MaxRAMPercentage=70` |

### 4. 一键构建并启动

```bash
bash deploy/deploy-server.sh up        # 首次构建镜像（Maven + npm 依赖走国内镜像）
# 或
docker compose up -d --build
```

脚本会依次完成：Docker/Compose 自检 → 生成 `.env` → `docker compose up -d --build` → 轮询 `http://127.0.0.1:8080/health` 直至就绪 → 打印容器状态与访问地址。

### 5. 打通宿主机访问（VirtualBox NAT）

虚拟机使用 NAT 网络，`VM_IP` 只有宿主机可见且不能直连，需要端口转发：

```powershell
# 运行中添加（立即生效，VM 重启后失效）
VBoxManage controlvm ubuntu-24.04.3-gp-master natpf1 "web8080,tcp,,8080,,8080"

# 永久生效（需先关机）
VBoxManage modifyvm ubuntu-24.04.3-gp-master --natpf1 "web8080,tcp,,8080,,8080"
```

---

## 四、端口与访问地址

| 位置 | 映射 | 用途 |
|------|------|------|
| 宿主 `8080` → 宿主 NAT → VM `8080` → 容器 `gateway:80` | 8080:80 | 用户端 / 管理端 / API 统一入口 |
| 宿主 `2222` → VM `22` | 2222:22 | SSH 运维 |
| 宿主 `3307` → VM `3307` → 容器 `mysql:3306` | 3307:3306 | 数据库直连排查（Navicat 等） |

| 应用 | 地址 |
|------|------|
| 用户端 | http://HOST_IP:8080/ （服务器内：http://VM_IP:8080/） |
| 管理端 | http://HOST_IP:8080/admin/ （`/admin` 自动 301 到 `/admin/`） |
| 后端 API | http://HOST_IP:8080/api/health |
| 网关探活 | http://HOST_IP:8080/health → `gateway ok` |

> 内置账号：`admin / 123456`（超级管理员）、`merchant1 | merchant2 / 123456`（商家）、`alice | bob | charlie | david / 123456`（普通用户）。
> 并发登录测试：两台设备（本机 + 同网段另一台）同时访问 `http://HOST_IP:8080/`，分别用不同账号登录互不影响；同一账号二次登录会把前一处挤下线。

---

## 五、验收结果（2026-10-31）

### 1. 容器状态

```
NAME                       IMAGE                           STATUS                    PORTS
ecommerce-mysql            mysql:8.0                       Up (healthy)              0.0.0.0:3307->3306/tcp
ecommerce-backend          ecommerce-backend:latest        Up (healthy)              8080/tcp
ecommerce-frontend-user    ecommerce-frontend-user:latest  Up (healthy)              80/tcp
ecommerce-frontend-admin   ecommerce-frontend-admin:latest Up (healthy)              80/tcp
ecommerce-gateway          ecommerce-gateway:latest        Up (healthy)              0.0.0.0:8080->80/tcp
```

- 网络：`ecommerce-system_ecommerce-net`（bridge，容器互访用服务名）
- 数据卷：`ecommerce-system_mysql-data`
- 镜像体积：backend 430 MB / frontend-user 108 MB / frontend-admin 107 MB / gateway 93.6 MB / mysql 1.1 GB（构建只在服务器本地完成，无跨平台依赖问题）

### 2. 数据初始化（幂等脚本自动跑）

```
product   100     category   6     shop   3     user   7     coupon   6（4 平台 + 2 店铺）     orders   2
```

### 3. 端到端验收（`deploy/verify_deployment.py`，从 Windows 主机经 NAT 端口映射发起）

```
PASS  网关 /health                                   | HTTP 200 gateway ok
PASS  后端 /api/health                               | ecommerce-backend / 实例 608d3f4afba9
PASS  用户端首页 /                                    | HTTP 200，396 字节
PASS  管理端 /admin/                                  | HTTP 200，409 字节
PASS  /admin 重定向                                   | 最终地址 http://127.0.0.1:8080/admin/
PASS  商品列表                                        | 共 100 件，返回 5 条
PASS  分类列表                                        | 6 个分类
PASS  用户登录 alice                                  | token 长度 292
PASS  登录态 /api/auth/me                             | HTTP 200
PASS  购物车 /api/cart/list                           | HTTP 200
PASS  订单列表 /api/order/list                        | HTTP 200
PASS  越权拦截（普通用户访问管理端接口）                | HTTP 403
PASS  管理员登录 admin                                | token 长度 294
PASS  管理端用户分页                                   | HTTP 200
PASS  管理端订单分页                                   | HTTP 200
PASS  管理端看板 /api/admin/stats/overview            | HTTP 200
PASS  单设备登录互斥（旧 token 失效）                   | 旧 token HTTP 401
PASS  公开列表不返回下架商品                           | 返回 50 条，均上架=True

结果：18/18 通过
（脚本 18 项自动检查；另外从局域网另一台设备手工访问 HOST_IP:8080，确认可达）
```

复现命令：

```bash
python deploy/verify_deployment.py http://HOST_IP:8080
```

---

## 六、日常运维

```bash
bash deploy/deploy-server.sh status    # 容器状态 + 网关健康检查
bash deploy/deploy-server.sh logs      # 跟踪 gateway / backend 日志
bash deploy/deploy-server.sh rebuild   # 无缓存重建全部镜像
bash deploy/deploy-server.sh down      # 停止（保留数据卷，数据不丢）
docker compose down -v                 # 停止并清空数据库（重置种子数据）
```

- 改前端代码后：`docker compose build frontend-user && docker compose up -d frontend-user`（管理端同理）。
- 改后端代码后：`docker compose build backend && docker compose up -d backend`（网关会等后端 healthy 再启动）。
- 数据库直连：宿主机 `127.0.0.1:3307`，账号 `root / 123456`，库 `ecommerce`。

---

## 七、本次部署中发现并修复的问题

| # | 现象 | 根因 | 处理 |
|---|------|------|------|
| 1 | 宿主机完全连不上 `VM_IP`（`Test-NetConnection` 失败） | VirtualBox **NAT 网络**，虚拟机地址对宿主不可路由 | 增加 NAT 端口转发 `8080/2222/3307`，并把「运行时规则重启失效、需 `modifyvm` 持久化」写进文档 |
| 2 | 直接 `docker pull` 全部超时 | 国内网络无法直连 Docker Hub | 复用服务器已有 `daemon.json` 镜像加速；Dockerfile 内 Maven 走阿里云、npm 走 npmmirror（构建全程无外网直连失败） |
| 3 | `/admin` 301 跳到 `http://127.0.0.1/admin/`（**丢端口**），访问 8080 时直接连不上 | nginx `return 301 /admin/` 默认生成**绝对地址**，用 `$host` 拼 URL 不带端口 | 网关加 `absolute_redirect off;`，返回相对地址 `/admin/`，任意端口/反代场景均正确（已回归 18/18） |
| 4 | 端口、密码、JWT 写死在 compose 里，服务器上要改文件 | 配置未外置 | `GATEWAY_PORT` / `MYSQL_HOST_PORT` / `MYSQL_ROOT_PASSWORD` / `JWT_SECRET` / `JAVA_TOOL_OPTIONS` 全部参数化到 `.env`，仓库只留 `.env.example` |
| 5 | 网关比后端先启动，首屏请求 502 | `depends_on` 只保证容器启动顺序，不保证就绪 | 后端加 TCP 探针（`/dev/tcp`）、网关加 `/health` 探针，网关改为 `condition: service_healthy`，并新增后端 `/api/health` 接口 |
| 6 | 无法快速判断「服务到底好不好」 | 缺少统一探活与验收手段 | 新增 `GET /api/health`（免登录）+ `deploy/verify_deployment.py` 18 项自动验收 |

---

## 八、与「本地单机 docker compose」的区别

| 维度 | 本地单机 | 本次服务器部署 |
|------|----------|----------------|
| 运行位置 | 开发机 Docker Desktop | 独立 Ubuntu 服务器（另一台「机器」） |
| 访问方式 | `localhost:8080` | 通过 NAT 端口映射，**局域网其他设备**也能访问 |
| 构建环境 | Windows 上构建 | 服务器上从源码构建（排除 `node_modules`），验证了跨平台可移植 |
| 配置 | 默认值写死 | `.env` 外置 + 随机 JWT 密钥 |
| 存活性 | 手工观察 | 5 个服务全部带 healthcheck，网关等后端就绪再启动 |
| 验收 | 人工点几下 | 18 项自动验收脚本，结果能重复跑 |

> 「分布式」的严格定义包含多节点/多实例与负载均衡。本项目已在**独立服务器节点上完成多容器分布式部署**；后端仍是模块化单体，多实例水平扩展受 `SessionStore` 内存实现约束，改造路径见 `docs/architecture.md` 第 4 节「横向扩展路径」。
