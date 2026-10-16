# bin/ — nitrowater 启动脚本（本地 dev）

> 目录分工（均从**仓库根目录**执行）：
> - `bin/` — 本地**启动/关闭**（全部 **PowerShell**：`start-*.ps1` / `stop-all.ps1`；`*.bat` 仅作**双击启动器**，不含逻辑）
> - `bin/test/` — **端到端冒烟**（smoke-*）
> - `deploy/bin/` — **运维/部署**脚本（gen-keys / check-env / backup_mysql / reset-sso-db / deploy；每组 `.ps1` + `.sh` 成对）
> - `deploy/docker/` — Docker Compose（infra / app / piston）

## 一键启动 / 关闭（推荐）

> 每个组件/服务都会**独占一个命令行窗口**并实时输出日志；窗口标题统一为 `NitroWater - <name>`。
> 均**幂等**：端口已在监听则跳过，不会重复启动。

| 脚本 | 用途 |
|------|------|
| `start-all.ps1`（或双击 `start-all.bat`） | **一键全部**：先起 infra，再起全部业务服务（= `start-infra.ps1` + `start-apps.ps1`） |
| `start-infra.ps1` | **一键 infra**：MySQL(3306, 系统服务·无窗口) / Redis(6379) / SeaweedFS(8333) / Piston(2000) |
| `start-apps.ps1`  | **一键业务**：account(8090) → bff(8080) / server(8081) / resource(8093) + **docs 容器**(`nitrowater-docs`, nginx, 127.0.0.1:8085)；加 `-WithWeb` 再起 web(5173) |
| `stop-all.ps1`（或双击 `stop-all.bat`） | **一键关闭**：停所有监听端口 + Docker 容器 + 服务窗口（MySQL 保留，加 `-StopMysql` 一并停） |

- 启动顺序：MySQL → Redis → SeaweedFS/Piston → account →（等 :8090 就绪）→ bff/server/resource → docs 容器 →（可选）web。
- `start-apps.ps1` **先等 account(:8090) 就绪**，因为 bff/server 启动时要取 OIDC discovery。
- `stop-all.ps1` 按 **端口 / Docker / 窗口 PID**（`bin/.run/*.pid`，由 start 脚本记录）三种方式回收；残留进程可再执行一次。**不再按窗口标题通配杀进程**（曾误杀 IDE/浏览器/资源管理器 → 桌面消失）。
- 共用 helper 在 `bin/_common.ps1`（各 `start-*.ps1` / `stop-all.ps1` 点源复用）。`*.bat` 只是 `pwsh/powershell -File ...` 的**双击启动器**，无逻辑。

## 启动（本地 dev·手动单起）

| 脚本 | 用途 |
|------|------|
| `start-redis.ps1`     | 本地 Redis（127.0.0.1:6379） |
| `start-account.ps1`   | `nitrowater-account`（SSO，:8090） |
| `start-bff.ps1`       | `nitrowater-bff`（BFF / Token Handler，:8080） |
| `start-seaweedfs.ps1` | 自托管 SeaweedFS（S3，:8333；本地磁盘） |
| `start-resource.ps1`  | `nitrowater-resource`（统一资源服务，:8093） |
| `start-piston.ps1`    | Piston 代码沙箱（判题/运行，:2000；需 Docker + cgroup v2 + privileged） |
| `install-piston-langs.ps1` | 给 Piston 装语言运行时（经 `ghproxy.net` 代理，见下） |

> **Piston 语言运行时**：官方镜像 `ghcr.io/engineer-man/piston` **不含任何语言**。首次用
> `.\bin\install-piston-langs.ps1` 安装（经 GitHub 代理 `ghproxy.net` 拉包；装 java/gcc/node/python/typescript/lua，
> 覆盖 server 的 7 语言）。运行时持久化在命名卷 `nitrowater-piston`，只需装一次。生产/compose 路径见
> `deploy/docker/docker-compose.piston.yml`（不映射宿主端口）。
> **TypeScript** 额外补 `@types/node`：脚本会自动安装并在 `compile` 步骤里暴露给 `tsc`，
> 使 `require` / `process` 等不再报 `TS2580`。

## 快速开始（本地 dev）

```powershell
.\deploy\bin\gen-keys.ps1        # 1) 生成/放置 JWT 密钥
.\bin\start-redis.ps1            # 2) 本地 Redis（MySQL 假定已在运行）
.\deploy\bin\check-env.ps1       # 3) 自检
.\bin\start-account.ps1          # 4) SSO
.\bin\start-bff.ps1              # 5) BFF
.\bin\start-seaweedfs.ps1        # 6) 存储
.\bin\start-resource.ps1         # 7) 资源服务
```

## 开发数据（重置 + 播种）

> **未上线期约定**：迁移只含 schema + 真实内容；**测试数据一律由脚本播种**，不进迁移。
> 因此改测试数据不会动 Flyway checksum，也不必再改迁移。

| 脚本 | 用途 |
|------|------|
| `dev-seed.ps1` | **一键**：reset → start → users → problems → resources（`-Skip*` 可分段跑；幂等） |
| `reset-db.ps1` | **破坏性**：drop 某库全部表，让 Flyway 下次启动从 V1 重放（`-Restart` 顺带重启归属服务） |
| `seed-test-users.ps1` | 幂等造测试用户 `testadmin`/ADMIN、`testuser1`/USER、`testuser`/USER 并授角色 |
| `seed-demo-problem.ps1` | 铸造管理员 JWT → 建「两数之和」(ACM 模式) + 2 用例 |
| `seed-func-problem.ps1` | 同上，**LeetCode 函数模式**版（`two-sum-func`，走 harness） |
| `seed-res.ps1` | 经 `/internal/resource/publish` 发 SYSTEM 资源夹具（`smoke-plugin` jar、`samples` 文本） |

```powershell
.\bin\dev-seed.ps1                 # 最常用：重建 biz 并播种全部测试数据
.\bin\dev-seed.ps1 -SkipReset      # 服务已在跑，只补播种
.\bin\reset-db.ps1 -Db biz -Force -Restart
```

> **⚠ 改迁移后必须先重建 jar**：Flyway 从 **jar 的 classpath** 读迁移（不是工作树），
> 只改 `db/migration/*.sql` 而不 `bootJar` 会让旧迁移继续生效。正解：停服 → `gradlew :<module>:bootJar` → `reset-db` 重放。
> 另：`flyway repair` **不能**替代重放——它只改 checksum，不会补执行基线里新增的 DDL（表缺失的静默漂移）。
## 冒烟测试
`bin/test/`：`smoke-auth` / `smoke-oidc` / `smoke-logout` / `smoke-resource`。

## 运维 / 部署 / Docker
- 运维/部署脚本：`deploy/bin/`（详见 `deploy/bin/README.md`）
- Docker Compose：`deploy/docker/`（infra + app + piston）；单入口 `deploy/bin/deploy.ps1`

### 插件文档容器（nitrowater-docs）
插件文档由**一个** nginx 容器（`nitrowater-docs`）统一托管，BFF 只转发 `/plugin/**`（不再自己读盘）。

```powershell
# 首次创建 / 更新后启动（推荐；用 compose，挂载 + nginx 配置都正确）
docker compose -f deploy/docker/docker-compose.app.yml up -d docs

# 按容器名单独操作（容器已存在时）
docker start   nitrowater-docs      # 启动
docker stop    nitrowater-docs      # 停止
docker restart nitrowater-docs      # 重启（发布新文档后立即生效）
docker logs -f nitrowater-docs      # 实时日志
docker inspect nitrowater-docs      # 详情

# 重建（改动挂载/nginx 配置后）
docker compose -f deploy/docker/docker-compose.app.yml up -d --force-recreate docs
```
- dev 端口 `127.0.0.1:8085`（= BFF 的 `app.plugin-docs.upstream` 默认）；prod 中 BFF 走容器网络名 `http://docs:80`。
- `bin/start-apps.ps1` 已内置：容器存在则 `docker start`，不存在则用 compose 创建。

## 前置配置

1. **JWT 密钥**：`deploy/keys/{private,public}.key`（`deploy/bin/gen-keys.ps1` 生成；已 gitignore）。
2. **NITROWATER_KEK**：32 字节 AES KEK 的 Base64（或 `nitrowater-account/build.gradle` bootRun 开发默认）。
3. **DEVICE_SALT**：已在 `application.yml` 复用 waterfun 开发值。

详见 `deploy/env-vars-reference.md`。脚本编码：`.bat`=ASCII+CRLF；`.ps1`=UTF-8 with BOM。
