# 快速开始（Windows PowerShell）

> Docker 一键部署版见 [`docs/deployment.md`](deployment.md)。
> 接口文档见 [`docs/api.md`](api.md)，缺陷修复清单见 [`docs/defects-and-fixes.md`](defects-and-fixes.md)。

---

## 环境准备

### 🚀 一键安装（推荐，Windows）

如果不想手动装 JDK / Node，直接在项目根目录跑：

```powershell
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

脚本会自动完成 4 件事（全部走国内镜像）：

1. **检测 JDK 17+**，缺失时按 `winget → 便携 zip → Chocolatey` 三级兜底自动安装
2. **检测 Node.js 18+**，同上策略安装便携版 Node 20.18.0
3. **下载 Maven 依赖**（调用内置的 `backend\maven\bin\mvn.cmd`，已配置阿里云镜像）
4. **安装前端依赖**（`frontend-user` 与 `frontend-admin` 分别 `npm install`，走 npmmirror）

> - 便携版会解压到 `%USERPROFILE%\tools\`，自动写入**用户级** `PATH`，**不需要管理员权限**。
> - 首次装完 JDK / Node 后，请**关闭并重新打开 PowerShell** 再继续下面的启动步骤。
> - 脚本可重复执行，已满足的步骤会显示 `[SKIP]`；结尾会打印三个窗口的启动命令。

安装完成或环境本来就满足要求后，再按下面步骤启动。

### 手动环境要求

| 组件        | 版本   | 是否需要 PATH | 备注                                              |
| ----------- | ------ | ------------- | ------------------------------------------------- |
| **JDK**     | **17+**| ✅ 需要       | Spring Boot 3.2 强制要求，**JDK 8 不支持** `--release` 编译参数 |
| Node.js     | 18+    | ✅ 需要       | npm install 用                                    |
| **Maven**   | —      | ❌ 不需要     | **已内置到项目**（`backend\maven\`，Maven 3.9.6） |
| **MySQL**   | **8.0**| ✅ 需要       | 项目数据库（可用 Docker 一键启动，或本地安装） |

### 确认当前版本

打开 PowerShell 跑：

```powershell
java -version
node -v
npm -v
```

### ⚠️ JDK 版本不够？

Spring Boot 3.x 最低要求 **JDK 17**。如果 `java -version` 输出是 1.8.x，有三种快速安装方式：

**方式 A：Chocolatey（推荐，一条命令）**

```powershell
# 如果系统已装 chocolatey（检查：choco --version）
choco install temurin17 -y
# 或
choco install microsoft-openjdk17 -y
```

**方式 B：winget**

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

**方式 C：手动下载**

去 [Adoptium](https://adoptium.net/temurin/releases/?version=17) 下载 **Temurin 17 LTS** Windows x64 MSI 安装包，安装时勾选 **Set JAVA_HOME**。

**方式 D：一键脚本（最省事）**

```powershell
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

详见上方「🚀 一键安装」章节。

安装完成后**关闭当前 PowerShell，重新打开一个新的**，验证：

```powershell
java -version
# 预期输出：openjdk version "17.x.x" 或 Java(TM) SE 17
```

---

## 启动后端（窗口 1）

```powershell
cd .\backend
.\maven\bin\mvn.cmd spring-boot:run
```

首次运行会自动：
1. 下载 Maven 依赖（约 1-2 分钟，阿里云镜像加速）
2. 连接 MySQL（需提前准备好 MySQL 8.0 实例，或更换 `application-dev.yml` 中的连接信息）
3. 跑 `db/schema-mysql.sql` 建表（21 张：user_table / shop / category / product / cart / orders / order_item / favorite / review / review_append / review_hidden / browse_history / address / coupon / user_coupon / user_setting / friend / message / conversation_hide / report，外加 `seed_meta` 初始化标记表）
4. 跑 `db/data-mysql.sql` 插入初始数据（7 账号 / 3 店铺 / 6 分类 / 100 商品 / 2 历史订单 / 3 评价 / 1 收藏 / 3 地址 / 6 优惠券：4 张平台券 + 2 张店铺券）
5. 启动 Spring Boot，监听 `http://localhost:8080/api`

看到 `Started EcommerceApplication in x.x seconds` 即成功。

> 最省事的方式是用 Docker 启动 MySQL：`docker run -d --name ecommerce-mysql -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=ecommerce -p 3307:3306 mysql:8.0`，再启动后端即可自动初始化（容器映射到宿主机 3307，与默认配置一致）。

**后端 API 根路径**：`http://localhost:8080/api`

---

## 启动用户端（窗口 2）

```powershell
cd .\frontend-user
npm install
npm run dev
```

打开 `http://localhost:5173`

开发模式下通过 Vite 代理把 `/api` 请求转发到后端 8080，无需改任何配置。

---

## 启动管理后台（窗口 3）

```powershell
cd .\frontend-admin
npm install
npm run dev
```

打开 `http://localhost:5174`

---

## 局域网其他设备访问（并发登录测试）

为支持局域网多设备并发验证，本机开发服务已配置为监听所有网卡：

1. 主机跑 `ipconfig`，取以太网 IPv4（本机为 `HOST_IP`）；
2. `vite.config.js` 中 `server.host = '0.0.0.0'`，所以另一台设备可直接访问：

   | 应用 | 地址 |
   |------|------|
   | 用户端 | `http://HOST_IP:5173` |
   | 管理端 | `http://HOST_IP:5174` |

3. 后端仍只需在主机运行；其他设备的 `/api` 由 Vite 代理转发到主机的 8080，**无需额外放行 8080**；
4. 两台设备必须**同网段**（`HOST_IP` 所在网段）。若另一台是 VMware / VirtualBox 虚拟机，请确认网络模式：
   - NAT：虚拟机可直接访问主机 IP；
   - Host-Only：主机需用对应虚拟网卡地址（如 VirtualBox 的 Host-Only 网卡地址）；
5. 若访问被拒，检查 Windows 防火墙是否放行 `node.exe` 入站（本机专用/公用防火墙已关闭）；
6. **Docker 版不必开 5173/5174**：虚拟机（`VM_IP`）里 5 个容器由网关 `8080` 统一对外，主机与局域网设备直接访问 `http://HOST_IP:8080`（管理端 `/admin/`），详见 `docs/docker-deploy-server.md`。

---

## 测试账号

| 用户名    | 密码   | 角色     | 说明 |
| --------- | ------ | -------- | ---- |
| admin     | 123456 | ADMIN    | 平台管理员，可登录后台看全站；同时是 1 号店「星选官方旗舰店」店主 |
| merchant1 | 123456 | MERCHANT | 2 号店「极客数码专营店」店主，后台只看本店 |
| merchant2 | 123456 | MERCHANT | 3 号店「生活优选馆」店主，后台只看本店 |
| alice     | 123456 | USER     | 普通用户 |
| bob       | 123456 | USER     | 普通用户 |
| charlie   | 123456 | USER     | 普通用户 |
| david     | 123456 | USER     | 普通用户 |

> 多商家验证：用 `alice` 分别加购 2 号店与 3 号店的商品 → 结算会按店铺拆成 2 笔订单；再用 `merchant1` 登录后台，只看到 2 号店那笔。

密码统一为 BCrypt 哈希值：`$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq`

---

## 数据库管理

本项目使用 **MySQL 8.0**（utf8mb4）。建表与种子脚本位于 `backend/src/main/resources/db/`：

| 文件 | 作用 |
|------|------|
| `db/schema-mysql.sql` | 幂等建表（`CREATE TABLE IF NOT EXISTS`，内联索引，InnoDB / utf8mb4） |
| `db/data-mysql.sql` | 幂等种子数据（`INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS`）；用户业务示例数据仅首次初始化写入（`seed_meta` 标记），重置后重启不会还原 |

### 本地连接 MySQL

本地开发默认连接 `127.0.0.1:3307/ecommerce`（账号 `root/123456`）。若你的 MySQL 在 3306，启动前设置环境变量 `MYSQL_PORT=3306` 即可，无需改代码：

```powershell
$env:MYSQL_PORT="3306"; .\maven\bin\mvn.cmd spring-boot:run
```

数据库不存在时会被自动创建（`createDatabaseIfNotExist=true`）；也可手动创建：

```sql
CREATE DATABASE ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

启动后端时会自动跑建表与种子脚本，无需手动导入。

### 快速启动一个 MySQL（Docker / 裸机）

```powershell
# Docker
docker run -d --name ecommerce-mysql -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=ecommerce -p 3307:3306 mysql:8.0
```

### 重置数据库（清空所有数据）

1. 停止后端（`Ctrl+C`）
2. 在 MySQL 中跑 `DROP DATABASE ecommerce; CREATE DATABASE ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. 重新启动后端：`.\maven\bin\mvn.cmd spring-boot:run`
4. 启动时会自动重建表 + 填充初始数据

### 数据持久化

数据保存在 MySQL 中，**后端重启数据不丢失**。只有手动 `DROP DATABASE` 时数据才会清空。

> 建表与初始化脚本均为幂等写法（`CREATE TABLE IF NOT EXISTS` / `INSERT ... WHERE NOT EXISTS`），
> 所以**每次重启都会安全执行，既不会丢数据，也不会重复插入**。

---

## 快速验证 API

后端启动后直接用 curl 或 PowerShell 验证：

### PowerShell（Windows 自带）

```powershell
# 商品列表（免登录）
Invoke-RestMethod -Uri "http://localhost:8080/api/product/list"

# 登录获取 Token
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method Post -ContentType "application/json" `
  -Body '{"username":"alice","password":"123456"}'
$token = $login.data.token

# 我的购物车（需要登录）
Invoke-RestMethod -Uri "http://localhost:8080/api/cart/list" `
  -Headers @{ Authorization = "Bearer $token" }
```

### curl / Git Bash

```bash
# 商品列表
curl http://localhost:8080/api/product/list

# 登录 + 获取 Token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | jq .
```

---

## 常见问题 FAQ

### Q1: 启动报 `无效的标记: --release`

**原因**：JDK 版本太低，JDK 8 不认识 `--release` 编译参数。

**解决**：安装 JDK 17（见上方「JDK 版本不够？」章节）。

### Q2: 报 `mvn 不是内部或外部命令`

Maven **已内置**在 `backend\maven\` 目录下，不需要配置 PATH。

正确用法：
```powershell
cd backend
.\maven\bin\mvn.cmd spring-boot:run
```

不要用 `mvn`，要用**完整相对路径** `.\maven\bin\mvn.cmd`。

### Q3: 端口被占用

修改 `backend\src\main\resources\application.yml`：
```yaml
server:
  port: 8081
```

前端开发端口在 `vite.config.js` 的 `server.port` 字段。

查看端口占用：
```powershell
netstat -ano | findstr :8080
# 然后用 taskkill /PID <PID> /F 结束进程
```

### Q4: 前端报跨域错误（CORS）

**本项目已处理好**：
- 开发模式：Vite 的 `proxy` 会把 `/api` 代理到 `http://localhost:8080`，浏览器视为同源
- Docker 部署：用户端与管理端同域挂在网关下（`/`、`/admin/`），同样不触发跨域
- 如需跨域：由后端 `WebConfig` 统一下发 CORS 响应头，网关**不再**重复添加（否则会出现两个 `Access-Control-Allow-Origin`）

如有问题，确认后端 `application.yml` 的 `server.servlet.context-path` 是 `/api`，并检查访问的是否是网关端口 8080。

### Q5: 启动慢（下载依赖卡很久）

首次运行会下载 Maven 依赖（约 300MB），后续运行会直接用本地缓存。

如果下载特别慢，可以在 `backend\` 下创建 `.mvn\settings.xml`：
```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### Q6: 数据库在哪里？

- 开发模式：本机 / Docker 中的 MySQL 8.0 实例（数据库名 `ecommerce`）
- Docker 模式：named volume `mysql-data`（通过 `docker volume ls` 查看）

### Q7: 多个后端实例能共享数据吗？

**数据可以共享**：数据库为独立的 MySQL 服务，多个后端实例连接同一个 MySQL 即可共享商品 / 订单等数据。唯一注意登录态（SessionStore 当前为内存实现）需替换为 Redis 才能跨实例共享，详见 `docs/architecture.md`。

### Q8: Docker 和本地开发可以切换吗？

完全可以。本地开发直接启动 3 个窗口 + 直接访问各前端的 dev server；Docker 部署后只有 Gateway 8080 一个入口。前端 `baseURL: '/api'` 在两种模式下都能正常工作（本地靠 Vite proxy，Docker 靠 Nginx Gateway）。

### Q9: 用户端为什么找不到「商家后台」入口？

这是**有意的设计**：用户端只服务购物场景，顶栏与页脚都不放后台链接，别让普通用户误入运营系统。运营人员直接访问：

- 本地开发：`http://localhost:5174`
- 网关部署：`http://localhost:8080/admin/`（不带尾斜杠会自动 301）

### Q10: 改了前端代码，页面没变化？

- 开发态：用 `npm run dev`（热更新），不要直接打开 `dist/index.html`
- 仓库**不保留** `dist` 与 `backend/target`（构建产物不入库）：需要静态产物时自行 `npm run build`，Docker 部署则 `docker compose build frontend-user frontend-admin && docker compose up -d`
- 看到旧界面（例如旧的页脚）说明浏览器缓存或跑的是旧进程，可强制刷新（Ctrl+F5）并重启 dev server
