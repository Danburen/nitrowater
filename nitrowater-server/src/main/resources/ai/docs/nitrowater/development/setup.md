# 本地开发环境

## 1. 前置条件

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | Gradle toolchain 固定 25 |
| Node.js | 24 | 前端与 account 的两个 SPA |
| pnpm | 11 | `nitrowater-account` 是 pnpm workspace |
| Docker | 最新 | SeaweedFS / Piston / Qdrant 容器 |
| MySQL | 8.0 | Windows 服务名固定 `MySQL`，端口 3306 |
| Redis | 7.x | 端口 6379 |

首次需要生成 JWT 密钥与 dev 配置：

```powershell
.\deploy\bin\gen-keys.ps1       # 生成 deploy\keys\{private,public}.key（gitignored）
.\deploy\bin\check-env.ps1      # 环境自检
```

## 2. 一条命令启动

```powershell
.\bin\start-all.ps1              # 构建 bootJar → 起 infra → 起 4 个后端（可见窗口）
.\bin\start-all.ps1 -SkipBuild   # 只重启后端
.\bin\start-all.ps1 -BuildWeb    # 顺带构建前端 dist，:8080 直接托管
.\bin\stop-all.ps1               # 停后端 + infra 容器（默认不动 :5173）
```

启动顺序被脚本固定：account 先起并等待 `:8090` 就绪，然后并行起 bff / server / resource。account 起不来会直接中止。

前端单独启动：

```powershell
cd nitrowater-web
npm run dev      # http://localhost:5173，/api /bff /oauth2 /login /logout 代理到 :8080
```

## 3. 数据脚本

| 脚本 | 用途 |
|---|---|
| `bin\dev\reset-db.ps1 -Db biz -Force` | 破坏性：drop 库让 Flyway 从 V1 重放 |
| `bin\dev\reset-db.ps1 -Db biz -Force -Restart` | 重置后重启归属服务 |
| `bin\dev\dev-seed.ps1` | reset + 播种测试用户/资源（幂等） |
| `bin\dev\seed-test-users.ps1` | 测试账号 `testadmin` / `testuser1` / `testuser`（密码 `Passw0rd123`） |
| `bin\dev\install-piston-langs.ps1` | 一次性给 Piston 装语言运行时（走 `ghproxy.net`） |

`-Db` 取值：`account` / `biz` / `resource`。

> `reset-db.ps1 -Restart` 与 `dev-seed.ps1` 用 `java -jar build\libs\app.jar` 起服务，必须先产出 jar（跑一次 `start-all` 或 `gradlew :<module>:bootJar`）。Flyway 从 classpath 读迁移，改完 SQL 必须重新构建。

## 4. 构建与测试

```powershell
# 全量构建（含前端与 account 的 SPA）
.\gradlew.bat build

# 单模块
.\gradlew.bat :nitrowater-server:test
.\gradlew.bat :nitrowater-server:bootRun

# 跳过 account 的 pnpm 构建
.\gradlew.bat build -PskipAdminUi -PskipAuthUi
```

前端：

```powershell
cd nitrowater-web
npm run typecheck
npm run lint
npm run build
```

## 5. 文档（纯 markdown 语料）

NitroWater 文档**不是独立站点**，也没有构建步骤：直接编辑
`nitrowater-server/src/main/resources/ai/docs/nitrowater/**/*.md`，重启（或重建 jar）后由索引器切块写入 Qdrant。

```powershell
# 重建文档索引（管理员；服务已在运行）
Invoke-RestMethod -Method POST -Uri 'http://localhost:8081/api/admin/ai/reindex' -Headers @{ Authorization = 'Bearer <admin-token>' }
```

- 语料是 UTF-8 **无 BOM**，front matter 会被索引器剥掉；NitroWater 引用只带标题/章节/相对路径，不带 URL。
- VeloChatX 插件文档是唯一仍有静态站的语料：外部仓库构建 → `bin\dev\sync-docs.ps1` 发布到 `deploy/plugin-docs/velochatx/docs`（base `/plugin/velochatx/docs/`），由 docs 容器直接托管，**不进 jar**。

## 6. 开发约定

- 后端 Servlet / WebMVC，禁止 WebFlux。
- 注释英文；公开方法写 Javadoc。
- 数据库时间列 `TIMESTAMP(3)`；枚举 `TINYINT UNSIGNED` + `@Converter(autoApply = true)`；布尔 `TINYINT(1)`。
- Flyway 已应用迁移冻结；dev 阶段改基线必须同时重置库。
- Redis 键 `nitrowater:*`。
- 前端 Tailwind-first；Element Plus 按需引入。
- `.ps1` 为 UTF-8 with BOM；`.bat` 为 ASCII + CRLF；日志类 markdown 为 UTF-8 无 BOM。

## 7. 端口占用排查

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080
Get-Process java | Select-Object Id,StartTime
```

`bin\.run\` 下记录 PID 与（`-Hidden` 模式的）日志；默认窗口模式直接看各自 cmd 窗口。

## 8. 相关入口

| 入口 | 地址 |
|---|---|
| 门户（BFF） | `http://localhost:8080` |
| SSO | `http://localhost:8090` |
| Vite dev | `http://localhost:5173` |
| 插件文档 | `http://localhost:8080/plugin/velochatx/docs/` |
| AI 管理台 | `http://localhost:8080/admin/ai` |

更多脚本细节见 `bin/README.md`。
