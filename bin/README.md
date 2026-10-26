# bin/ — 本地预览脚本（构建 → 启动 → 停止）

> 目标：**一条命令**完成「构建后端 → 起基础设施 → 起 4 个后端服务」，供人工预览或自动化（AI）验证。
> **前端不在这里启动**，由你自己 `npm run dev`（见下）。
> 全部脚本为 PowerShell，从**仓库根目录**执行；`*.bat` 只是双击启动器（无逻辑）。
> 编码：`.ps1` = UTF-8 with BOM，`.bat` = ASCII + CRLF。

## 顶层：只用这 4 个

| 脚本 | 用途 |
|------|------|
| `start-all.ps1`（或双击 `start-all.bat`） | **构建 + 启动**：`gradlew :<module>:bootJar` → infra → 4 个后端 jar |
| `start-infra.ps1` | 仅 infra：MySQL(3306, 系统服务) / Redis(6379) / SeaweedFS(8333) / Piston(2000) / docs(8085) |
| `stop-all.ps1`（或双击 `stop-all.bat`） | 停后端 + infra 容器 + 跟踪进程；**默认不动 :5173**（加 `-WithWeb` 才停） |
| `_common.ps1` | 共享 helper（被其余脚本点源，不直接运行） |

## start-all 做了什么

1. **build**：`gradlew :nitrowater-account:bootJar :nitrowater-bff:bootJar :nitrowater-server:bootJar :nitrowater-resource:bootJar`
   → 每模块产出 `build\libs\app.jar`（不含测试）。
2. **infra**：调用 `start-infra.ps1`（已在监听的组件跳过）。
3. **services**：`java -jar` 起 account(:8090) → **等 :8090 就绪** → 并行起 bff(:8080) / server(:8081) / resource(:8093)。
   - 默认每个后端一个**常驻 cmd 窗口**（标题 `NitroWater - <module>`），窗口里直接刷 Spring 日志；关掉窗口即停该服务。
   - 端口已被旧进程占用时，默认**先停旧窗口/旧进程再起新的**（避免跑到上一次的 jar）；`-NoRestart` 则完全不动。
   - account 起不来会**直接中止**（bff/server 启动时要拉 OIDC discovery）。
   - 加 `-Hidden` 才回到旧行为：进程隐藏，日志落 `bin\.run\logs\<module>.{out,err}.log`。

| 参数 | 作用 |
|------|------|
| `-SkipBuild` | 跳过构建，直接用现有 jar（缺失会报错退出） |
| `-SkipInfra` | infra 已就绪，跳过 |
| `-BuildWeb` | 额外 `npm run build`，让 :8080 直接托管最新 SPA |
| `-NoRestart` | 已在运行的服务原样保留（可能仍是旧 jar） |
| `-Hidden` | 后台隐藏运行，日志落盘（不是默认） |
| `-Windowed` | 旧参数；现在默认就是可见窗口，保留兼容 |

```powershell
.\bin\start-all.ps1              # 日常：构建 + infra + 4 个后端窗口（窗口常驻，日志直接看）
.\bin\start-all.ps1 -Hidden      # 后台隐藏运行，日志落盘
.\bin\start-all.ps1 -SkipBuild   # 只重启后端（jar 已是新的）
.\bin\start-all.ps1 -BuildWeb    # 顺带把前端 dist 构建进 :8080
```

> 默认模式下每个后端都会弹出一个标题为 `NitroWater - <module>` 的常驻 cmd 窗口；
> 窗口里就是 Spring 日志，服务退出后会停在 `[exited with code ...]` 并 `pause`，方便看错误。
> 关闭窗口会结束该服务；`stop-all.ps1` 也会按 PID 关闭这些窗口。
>
> `-Hidden` 的旧模式「没窗口 ≠ 没启动」：`Get-Process java` 能看到 4 个进程，端口 8090/8080/8081/8093 在监听。
>
> PID 记录：默认窗口模式记录的是 cmd 包装进程，`stop-all` 会连同 java 子进程一起结束；`-Hidden` 记录的是 java.exe。

## 前端（你自己启动）

```powershell
cd nitrowater-web
npm run dev        # http://localhost:5173 → /api /bff /oauth2 /login /logout /plugin 代理到 :8080
```

- 想直接在 **:8080** 看构建版：`.\bin\start-all.ps1 -BuildWeb`（= `cd nitrowater-web; npm run build`）。
  BFF 运行期读 `nitrowater-web/dist/`，所以**只改前端时重新 build 即可，无需重启 BFF**。
- `:5173` 的 Vite 进程不归本目录管：`stop-all.ps1` 默认不会杀它。

## 服务端口一览

| 服务 | 端口 | 入口 |
|---|---|---|
| account（SSO/OIDC） | 8090 | http://localhost:8090 |
| bff（门户 / SPA / 代理） | 8080 | http://localhost:8080 |
| server（业务 API + 判题） | 8081 | `/api/**`（经 BFF 访问） |
| resource（资源/插件 jar） | 8093 | `/api/resource/**`（经 BFF 访问） |
| docs（插件文档 nginx） | 8085 | `/plugin/**`（经 BFF 访问） |
| Vite dev（可选） | 5173 | http://localhost:5173 |

## dev/ — 数据与同步（不参与日常启动）

| 脚本 | 用途 |
|------|------|
| `bin\dev\dev-seed.ps1` | 一键：reset → 播种用户 / 题目 / 资源（`-Skip*` 分段；幂等） |
| `bin\dev\reset-db.ps1` | **破坏性**：drop 库让 Flyway 从 V1 重放（`-Restart` 顺带重启归属服务） |
| `bin\dev\seed-test-users.ps1` | 测试用户 `testadmin` / `testuser1` / `testuser`（密码 `Passw0rd123`） |
| `bin\dev\seed-demo-problem.ps1` | 「两数之和」ACM 模式 + 2 用例（铸造管理员 JWT 后建题） |
| `bin\dev\seed-func-problem.ps1` | 「两数之和」LeetCode 函数模式（`two-sum-func`，走 harness） |
| `bin\dev\seed-res.ps1` | 经 `/internal/resource/publish` 发资源夹具（`smoke-plugin`、`samples`） |
| `bin\dev\sync-docs.ps1` | 构建插件文档（VitePress）并镜像到 `deploy\plugin-docs\<plugin>\docs` |
| `bin\dev\sync-res.ps1` | 把插件 jar 发布到资源服务（`:8093` 直连，不经 BFF） |
| `bin\dev\install-piston-langs.ps1` | 给 Piston **一次性**装语言运行时（经 `ghproxy.net`） |

> ⚠ `dev-seed.ps1` / `reset-db.ps1 -Restart` 用 `java -jar build\libs\app.jar` 起服务，
> 所以**先跑一次 `bin\start-all.ps1`**（或 `gradlew :<module>:bootJar`）把 jar 产出，否则报 `jar missing`。
> 另：Flyway 从 classpath 读迁移 —— `start-all` 走 jar，改完 `db/migration/*.sql` 必须先重新构建再启动。

## 运行态与排查

- 运行态目录 `bin\.run\`（gitignored）：`*.pid` = 进程号；`logs\` = `-Hidden` 模式的 stdout/stderr。
- 默认窗口模式直接看各自 cmd 窗口；`-Hidden` 模式实时看日志：
  ```powershell
  Get-Content .\bin\.run\logs\nitrowater-server.out.log -Wait -Tail 50
  ```
- 起不来先看 `logs\<module>.out.log` / `.err.log`；端口占用用 `Get-NetTCPConnection -State Listen -LocalPort 8080`。
- 依赖的本地路径（可用环境变量覆盖）：
  - Java 25：`NITROWATER_JAVA`（默认 `D:\Program Files\Java\openkdk-hotspot-25.0.1.1\bin\java.exe`；**不读 JAVA_HOME**）
  - Redis：`NITROWATER_REDIS_DIR`（默认 `E:\Program Files\Redis`）
  - MySQL：`NITROWATER_MYSQL`（默认 `E:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`）；Windows 服务名固定 `MySQL`
- Docker 不可用时 SeaweedFS / Piston / docs 会被跳过（脚本给 WARN，不报错）。

## 前置配置（首次）

1. JWT 密钥：`.\deploy\bin\gen-keys.ps1` → `deploy\keys\{private,public}.key`（gitignored）。
2. `NITROWATER_KEK`：32 字节 AES KEK 的 Base64（`start-all` 起 account 时注入 dev 默认）。
3. `RESOURCE_INTERNAL_TOKEN`：`/internal/**` 的共享密钥（`start-all` 起 resource 时注入 dev 默认
   `nitrowater-internal-dev`，与 `bin\dev\sync-res.ps1` 一致）；该前缀只接受**本机/内网**来源。
4. `BFF_CLIENT_SECRET` / `S3_SECRET_KEY`：**application.yml 里已无内置默认值**，缺失即启动失败；
   `start-all`（account/bff）与 `gradlew bootRun` 会注入 dev 值，手工 `java -jar` 需自行 export。
5. 环境自检：`.\deploy\bin\check-env.ps1`。

## 相关目录

- `deploy\bin\` — 运维/部署脚本（gen-keys / check-env / backup_mysql / reset-sso-db / deploy）。
- `deploy\docker\` — Docker Compose（infra / app / piston）与根 `Dockerfile`。
