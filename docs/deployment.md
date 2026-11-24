# 部署指南

---

## 一、环境要求

| 组件 | 版本 | 校验命令 |
|------|------|----------|
| JDK | 17+（已在 JDK 23 验证） | `java -version` |
| Node.js | 18+（推荐 20 LTS） | `node -v` |
| Maven | 3.8+ | `mvn -v`（或使用内置 `backend/maven`） |
| Docker + Compose | 20+ / v2（可选） | `docker -v`、`docker compose version` |

---

## 二、本地开发部署

### 0. 一键安装环境（可选但推荐）

```powershell
# 在项目根目录执行
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

`setup.ps1` 会自动检测并补齐 JDK 17+ 与 Node.js 18+，然后下载 Maven 与 npm 依赖。全部走国内镜像（清华 Adoptium、npmmirror、阿里云 Maven），无需科学上网。

| 步骤 | 内容 | 安装策略 | 镜像源 |
|------|------|----------|--------|
| 1/4 | JDK 17+ | winget → 便携 zip → Chocolatey | 清华 Adoptium / GitHub Releases |
| 2/4 | Node.js 18+ | winget → 便携 zip（v20.18.0）→ Chocolatey | npmmirror / 清华 nodejs-release |
| 3/4 | Maven 依赖 | `backend\maven\bin\mvn.cmd dependency:resolve` | 阿里云 Maven 公共仓库 |
| 4/4 | npm 依赖 | `frontend-user` + `frontend-admin` 的 `npm install` | `registry.npmmirror.com` |

注意事项：

- 便携安装：winget 不可用时，JDK / Node 解压到 `%USERPROFILE%\tools\` 并写入**用户级** `PATH` 与 `JAVA_HOME`，不需要管理员权限。
- 重新开终端：首次安装后必须关闭并重新打开 PowerShell，`PATH` 才会生效。
- 幂等：重复执行安全，已满足的步骤显示 `[SKIP]`。
- 执行策略：`-ExecutionPolicy Bypass` 只对本次进程生效，不会修改系统设置。

### 1. 启动后端

```powershell
cd backend

# Windows（使用项目内置 Maven，无需全局安装）
.\maven\bin\mvn.cmd spring-boot:run

# Linux / macOS（已安装 Maven 3.8+）
mvn spring-boot:run
```

启动成功后：

```
========================================
  电商系统启动成功
  API 地址: http://localhost:8080/api
========================================
```

- 首次启动后端会连接 MySQL，并自动跑 `db/schema-mysql.sql`（建表）+ `db/data-mysql.sql`（种子数据）
- 脚本均为**幂等**设计（`CREATE TABLE IF NOT EXISTS` + `INSERT ... WHERE NOT EXISTS`），重启不会丢数据、不会重复插入
- 需要完全重置：在 MySQL 中 `DROP DATABASE ecommerce` 后重建，再重启后端即可重新初始化

### 2. 启动用户端

```bash
cd frontend-user
npm install
npm run dev        # http://localhost:5173
```

`vite.config.js` 已配置代理：`/api` → `http://localhost:8080`。

### 3. 启动管理端

```bash
cd frontend-admin
npm install
npm run dev        # http://localhost:5174
```

### 4. 生产构建

```bash
cd frontend-user  && npm run build                    # 产物在 frontend-user/dist，base = /
cd frontend-admin && npm run build -- --base=/admin/  # 产物在 frontend-admin/dist，base = /admin/
```

> 管理端在网关下以 `/admin/` 前缀访问，所以**必须**带 `--base=/admin/` 构建（`Dockerfile` 中已内置该参数）；
> 路由已使用 `createWebHistory(import.meta.env.BASE_URL)` 同步 base，刷新子路由不会 404。
> 本地 `npm run dev` 仍是 `http://localhost:5174`，无需该参数。

---

## 三、Docker 分布式部署

项目采用「网关 + 两个前端 + 一个后端 + 一个数据库」的容器化部署形态：后端仍是**模块化单体**（16 个业务域分包：user / shop / product / category / cart / order / coupon / review / favorite / address / browse / stats / friend / message / report / setting，另含 `/health` 探活），前端为独立 Nginx 容器，通过统一网关对外只暴露 8080 一个端口。

```bash
# 构建并后台启动全部服务
docker compose up -d --build

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend

# 停止
docker compose down

# 停止并清除数据卷
docker compose down -v
```

### 服务编排

| 服务 | 镜像 | 容器内端口 | 宿主端口 | 说明 |
|------|------|-----------|----------|------|
| `mysql` | mysql:8.0 | 3306 | 3307 | MySQL 8.0 数据库（utf8mb4，数据卷持久化；映射宿主 3307 别让与本机 MySQL 冲突） |
| `gateway` | ecommerce-gateway:latest | 80 | 8080 | Nginx 统一入口 |
| `backend` | ecommerce-backend:latest | 8080 | —（内网） | Spring Boot（连接 mysql） |
| `frontend-user` | ecommerce-frontend-user:latest | 80 | —（内网） | 用户端静态资源 |
| `frontend-admin` | ecommerce-frontend-admin:latest | 80 | —（内网） | 管理端静态资源 |

数据卷 `mysql-data` 持久化 MySQL 数据；后端等 MySQL 健康检查通过后启动并自动建表 + 灌种子，网关再等后端 healthy 才接管流量（5 个服务全部配 healthcheck）。

### 访问地址

| 应用 | 地址 |
|------|------|
| 用户端 | http://localhost:8080 |
| 管理端 | http://localhost:8080/admin/ （不带尾斜杠会自动 301） |
| 后端 API | http://localhost:8080/api |
| 数据库 | MySQL 8.0（宿主 3307 → 容器 3306；仅供本机排查，业务代码在容器网络内直连 `mysql:3306`） |

---

## 四、网关路由（gateway/nginx.conf）

| 路径 | 目标服务 |
|------|----------|
| `/api/` | `backend:8080`（保留 `/api` 前缀，与 `server.servlet.context-path` 一致） |
| `/admin/` | `frontend-admin:80`（管理端以 `--base=/admin/` 构建，路由 history base 同步） |
| `/admin` | 301 重定向到 `/admin/`，别让落到用户端首页 |
| `/health` | 返回 `gateway ok`，供外部探活 |
| `/` | `frontend-user:80` |

> 跨域说明：同域部署下浏览器不会发起跨域请求；如需跨域，由后端 `WebConfig` 统一下发 CORS 响应头（网关不再重复添加，别让出现两个 `Access-Control-Allow-Origin`）。

---

## 五、生产环境检查清单

| 项目 | 操作 |
|------|------|
| JWT 密钥 | 设置环境变量 `JWT_SECRET`（仓库中的占位值仅供本地起服务，生产必须替换） |
| 数据库 | 使用 MySQL 8.0（`utf8mb4`），密码等敏感信息通过环境变量注入，勿写死在代码 |
| SQL 打印 | `mybatis-plus.configuration.log-impl` 保持注释（已默认关闭） |
| 错误详情 | `server.error.include-*: never`（已配置） |
| CORS | 生产环境将 `allowedOriginPatterns("*")` 限定为具体域名 |
| SessionStore | 当前为内存实现，多实例部署需替换为 Redis（横向扩展章节见 `architecture.md`） |
| HTTPS | 在网关层配置证书与 80→443 跳转 |

---

## 六、常见问题

| 现象 | 原因与处理 |
|------|-----------|
| `setup.ps1` 提示「无法加载文件，禁止运行脚本」 | 加 `-ExecutionPolicy Bypass`：`powershell -ExecutionPolicy Bypass -File .\setup.ps1` |
| `setup.ps1` 装完 JDK/Node 后命令仍不可用 | 关闭并重新打开 PowerShell，让用户级 `PATH` 生效 |
| `setup.ps1` 所有下载源都失败 | 手动安装：JDK 见 https://adoptium.net ，Node 见 https://nodejs.org |
| 后端端口 8080 被占用 | `netstat -ano \| findstr :8080` 找到 PID 后结束进程，或修改 `server.port` |
| 前端接口 404 | 确认后端已启动；dev 模式依赖 vite proxy，不要直接访问 5173 的 `/api` |
| 管理端提示「该账号没有后台权限」 | 只有 `ADMIN`（超级管理员）与 `MERCHANT`（商家）能登录后台；普通用户需由超级管理员在「用户管理」里设为普通管理员 |
| 重启后数据丢失 | 确认 MySQL 数据卷 `mysql-data` 未被 `docker compose down -v` 清除；建表脚本已幂等化 |
| 登录后立刻提示「异地登录」 | 单设备登录机制：同一账号在另一处登录会挤掉旧 token，重新登录即可 |
| 数据库连接失败 | 确认 `mysql` 服务已 `healthy`；检查 `MYSQL_HOST/MYSQL_PORT` 环境变量与账号密码 |
| 订单自动消失 | 待支付订单超过 30 分钟由定时任务自动取消（可在 `order.timeout-minutes` 调整） |
| 访问 `/admin` 却看到商城首页 | 旧版网关只匹配 `/admin/`；现已加 `location = /admin` 301 跳转，重新 `docker compose up -d --build gateway` 生效 |
| 浏览器控制台报 CORS 头重复 | 旧版 Nginx 与后端同时下发 CORS 头，已删除网关侧配置，只保留后端 `WebConfig` |
| 前端改了代码，页面没变化 | `dist` 是构建产物：本地开发用 `npm run dev`（热更新）；Docker 部署需重新 `npm run build` 或 `docker compose build frontend-user frontend-admin` |
| 访问 `/admin` 跳到 `http://<host>/admin/` 后连不上 | 旧版网关用 nginx 默认的**绝对地址**重定向，会丢掉 `:8080` 端口；已在 `gateway/nginx.conf` 加 `absolute_redirect off;`，改后需 `docker compose build gateway && docker compose up -d gateway` |

---

## 七、Ubuntu 服务器部署（分布式节点，已经跑通）

本节记录「**Docker + 分布式部署**」的实际落地过程，完整拓扑、验收明细与踩坑见
📄 [`docker-deploy-server.md`](docker-deploy-server.md)。

### 7.1 环境

| 项目 | 值 |
|------|-----|
| 服务器 | Ubuntu 24.04.3 LTS，主机名 `ubuntu-server`，内网 IP `VM_IP`（VirtualBox NAT） |
| 规格 | 10 vCPU / 19 GiB 内存 / 98 GB 磁盘 |
| 容器 | Docker 29.3.0，Compose v5.1.1，已配置 registry 镜像加速 |
| 项目目录 | `/home/deployer/ecommerce-system` |

### 7.2 部署

```bash
# 1) 上传源码包（打包时排除 node_modules / dist / target / 内置 maven）
scp -P 2222 ec-deploy-src.tar.gz deployer@127.0.0.1:/home/deployer/
ssh -p 2222 deployer@127.0.0.1
mkdir -p ~/ecommerce-system && tar -xzf ~/ec-deploy-src.tar.gz -C ~/ecommerce-system
cd ~/ecommerce-system

# 2) 生成配置（.env 含密钥，不入库）
cp .env.example .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env
docker compose config -q

# 3) 一键构建 + 启动（脚本会等网关 /health 就绪后打印访问地址）
bash deploy/deploy-server.sh up

# 4) 验收（18 项）
python deploy/verify_deployment.py http://HOST_IP:8080
```

### 7.3 配置外置（根目录 `.env`）

| 变量 | 默认 | 说明 |
|------|------|------|
| `GATEWAY_PORT` | 8080 | 网关对外端口 |
| `MYSQL_HOST_PORT` | 3307 | MySQL 映射宿主端口（容器内 3306） |
| `MYSQL_ROOT_PASSWORD` | 123456 | 数据库密码，后端容器复用 |
| `JWT_SECRET` | 占位密钥 | **生产必须替换**：`openssl rand -base64 48` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70` | 后端 JVM 参数 |

### 7.4 访问与端口

| 位置 | 映射 | 用途 |
|------|------|------|
| 宿主 8080 → VM 8080 → `gateway:80` | 8080:80 | 用户端 `/`、管理端 `/admin/`、API `/api/` |
| 宿主 2222 → VM 22 | 2222:22 | SSH |
| 宿主 3307 → VM 3307 → `mysql:3306` | 3307:3306 | 数据库直连 |

```powershell
# VirtualBox NAT 端口转发（运行中即时生效；重启 VM 后失效）
VBoxManage controlvm ubuntu-24.04.3-gp-master natpf1 "web8080,tcp,,8080,,8080"
# 永久生效（需先关机）
VBoxManage modifyvm ubuntu-24.04.3-gp-master --natpf1 "web8080,tcp,,8080,,8080"
```

| 应用 | 地址 |
|------|------|
| 用户端 | http://HOST_IP:8080/ |
| 管理端 | http://HOST_IP:8080/admin/ |
| 探活 | http://HOST_IP:8080/health 、 http://HOST_IP:8080/api/health |

### 7.5 运维

```bash
bash deploy/deploy-server.sh status   # 容器状态 + 健康检查
bash deploy/deploy-server.sh logs     # gateway / backend 日志
bash deploy/deploy-server.sh rebuild  # 无缓存重建
bash deploy/deploy-server.sh down     # 停止（保留数据卷）
```

### 7.6 健康检查与启动顺序

| 服务 | 探针 | 说明 |
|------|------|------|
| `mysql` | `mysqladmin ping` | 通过后后端才开始启动 |
| `backend` | TCP 探测 `127.0.0.1:8080`（JRE 镜像无 curl，用 bash `/dev/tcp`） | 通过后网关才启动 |
| `frontend-user` / `frontend-admin` | `wget` 首页 | 静态站点就绪 |
| `gateway` | `wget http://127.0.0.1/health` | 网关自身探活 |
| 应用层 | `GET /api/health`（免登录） | 返回服务名 / 实例 / 时间，便于确认请求落在哪个实例 |
