# 问题排查

按现象查，不要先改代码。

## 1. 登录与鉴权

| 现象 | 原因 | 处理 |
|---|---|---|
| 登录成功后又被踢回登录页 | issuer 不一致（account / bff / SPA 三处不同源） | 统一 `JWT_ISSUER`；account 的 `jwt.issuer` 与 BFF 的 `issuer-uri` 必须完全相同 |
| `invalid_redirect_uri` | 回调地址不在白名单 | 加入 `app.oidc.bff-redirect-uris` |
| BFF 启动报 `Unable to resolve Configuration with the provided Issuer` | account 未启动 / 域名不可达 | 先起 account；容器内确认能解析该 host |
| 接口 401，但刚登录 | 会话过期（默认 30 分钟）或 Redis 被清 | 重新登录；检查 Redis 连接与 `nitrowater:bff:session` |
| 403 且日志提到 `aud` | token audience 与 RS 配置不一致 | 对齐 `access-token-audiences` 与各 RS 的 `app.security.jwt.audiences` |
| 写请求 403 | CSRF token 缺失或过期 | 前端读取 `XSRF-TOKEN` 放 `X-XSRF-TOKEN`，失败后重取再重试一次 |
| 登录页白屏 | account 的 SPA 未构建 | `pnpm install` 后由 Gradle 触发构建，或用 `-PskipAdminUi -PskipAuthUi` 明确跳过 |

## 2. 数据库与 Flyway

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动失败 `Validate failed: checksum mismatch` | 改了已应用的迁移 | dev：`reset-db.ps1 -Force -Restart`；生产：新增迁移版本，不要改旧文件 |
| `flyway repair` 后表还是不存在 | repair 只修校验和，不补跑已记录的 DDL | 必须重置库让 V1 重放，或新增一个真正建表的迁移 |
| 建表报 `Invalid default value` | `TIMESTAMP` 默认值越界（1970–2038） | 用 `CURRENT_TIMESTAMP(3)`；超出范围的时间改用 `DATETIME(3)` 并先确认 |
| 写入时间偏差 8 小时 | 连接串缺少时区参数 | 加 `connectionTimeZone=UTC&preserveInstants=true`，Java 用 `Instant` |
| 枚举列插入失败 / 值错乱 | 用了 `@Enumerated` 或字符串存储 | 改为 `TINYINT UNSIGNED` + `@Converter(autoApply = true)` |
| 改了 SQL 但启动没生效 | `start-all` 用旧 jar，Flyway 读的是 classpath | 先重新 `bootJar` 再启动 |

## 3. 沙箱 / Piston

| 现象 | 原因 | 处理 |
|---|---|---|
| 所有语言都返回 `400 Bad Request`，响应体是 HTML | JDK HttpClient 默认 h2c Upgrade，Piston 不支持 | `PistonClient` 固定 HTTP/1.1 |
| `server_unavailable` | Piston 未启动 / `PISTON_BASE_URL` 不对 | 本机用 `http://localhost:2000`，容器用 `http://piston:2000` |
| 某种语言总是失败 | 语言运行时未安装 | `install-piston-langs.ps1` |
| `queue_timeout` / `queue_full` | 全局并发 2、队列 20 被打满 | 等待或上调 `global-concurrency`（需同步 Piston 容量） |
| 内存超限 | `memory-limit-bytes` > Piston 配置 | 下调到不超过 `PISTON_RUN_MEMORY_LIMIT` |
| 输出被截断 | 超过 `max-output-bytes`（64KB） | 属预期；提示用户减少输出 |

## 4. AI / NitroBot

| 现象 | 原因 | 处理 |
|---|---|---|
| 所有 AI 接口 503 | `app.ai.enabled=false` 或缺 chat key | 配置 key 后重启 |
| 回答总是「文档中没有找到」 | 索引为空 / corpus 不匹配 | `POST /api/admin/ai/reindex`；检查 `app.ai.collections` 的 `corpus` |
| 启动日志有 embedding 失败但服务正常 | 这是设计：索引失败不阻塞启动 | 补 key 或修 base-url 后重建索引 |
| 引用没有链接 | 该 corpus 的 `public-base-url` 为空（NitroWater 文档就是这样） | 属预期：纯 markdown 语料只给标题/章节/路径 |
| VeloChatX 引用链接 404 | docs 容器未起 / `public-base-url` 与站点路径不一致 | 引用前缀是 `/plugin/velochatx/docs/`；先 `sync-docs.ps1` 发布并确认 :8085 在服务 |
| 文档首段内容异常 / 标题丢失 | markdown 带 UTF-8 BOM 或 front matter 未剥离 | 语料写 UTF-8 **无 BOM**；`DocChunkBuilder` 会剥 front matter |
| SSE 一次性出现，不是逐字 | 中间层缓冲了 `text/event-stream` | 检查 BFF `AiStreamProxyController` 与反向代理的 buffering |
| 工具调用被提前截断 | `runCode` 限额被误设为 3 | 必须 ≥4，让工具能返回 `retry_exhausted`（`ToolLimitBindingTest` 守着） |
| 429 | 用户分钟/天配额耗尽 | 等待或调整 `per-user-per-minute` / `per-user-per-day` |

## 5. 资源服务

| 现象 | 原因 | 处理 |
|---|---|---|
| 上传成功但读取 403 | 预签名地址用了容器内 endpoint | `S3_PUBLIC_ENDPOINT` 必须是浏览器可达地址 |
| 浏览器直传 CORS 失败 | SeaweedFS 未配置 CORS | 在对象存储侧允许前端来源的 `PUT` |
| 资源一直 PENDING | 客户端未回调 `/upload/callback` | 客户端补回调；超时后由清理任务转 ORPHAN |
| 头像不显示 | 头像资源 uuid 失效或不可见 | 检查 `resource.uuid` 与可见性 |
| 插件 JAR 下载 404 | 未发布或 `plugin_key` 不匹配 | 用管理台重新上传；确认 `app`/`group_key` 约定 |

## 6. 前端与构建

| 现象 | 原因 | 处理 |
|---|---|---|
| `:5173` 接口 404 | Vite 代理目标不是 8080 | 检查 `vite.config.ts` 的 proxy |
| 刷新深链接 404 | BFF 未做 history 回退 | 确认 SPA fallback 配置 |
| 构建报 Tailwind 相关错误 | 版本/插件不匹配 | 使用 `@tailwindcss/vite`，Tailwind v4 |
| `pnpm` 相关构建失败 | Node/pnpm 版本不符 | Node 24 + pnpm 11 |
| 改动前端后 :8080 没变化 | BFF 读 `dist/`，未重新构建 | `npm run build` 或 `start-all -BuildWeb`（无需重启 BFF） |

## 7. 脚本与编码

| 现象 | 原因 | 处理 |
|---|---|---|
| `.ps1` 报语法错误/中文乱码 | 保存成了无 BOM 或 GBK | `.ps1` 必须 UTF-8 **with BOM** |
| `.bat` 窗口乱码 | 非 ASCII 内容 | `.bat` 只写 ASCII + CRLF |
| 日志/文档出现 `?` 或乱码 | 历史 GBK 文件被当 UTF-8 读 | 先按原编码读出再以 UTF-8 无 BOM 重写 |
| 脚本报 `jar missing` | 未先构建 | 跑 `start-all` 或 `gradlew :<module>:bootJar` |

## 8. 端口与环境

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,8081,8090,8093
Get-Process java | Select-Object Id,Path,StartTime
```

| 端口 | 服务 |
|---|---|
| 8080 | BFF |
| 8081 | server |
| 8090 | account |
| 8093 | resource |
| 5173 | Vite dev |
| 6333 / 6334 | Qdrant HTTP / gRPC |
| 8333 | SeaweedFS S3 |
| 2000 | Piston |

脚本默认 Java 路径由 `NITROWATER_JAVA` 指定（**不读 `JAVA_HOME`**）；Redis 目录 `NITROWATER_REDIS_DIR`；MySQL 客户端 `NITROWATER_MYSQL`。Docker 不可用时 SeaweedFS / Piston 会被跳过并给出 WARN。

## 9. 排查顺序

1. 看服务日志（窗口或 `bin\.run\logs`）。
2. 确认端口在监听、依赖组件可达。
3. 确认配置一致（issuer、audience、回调地址、endpoint）。
4. 确认数据层（Flyway 版本、表是否存在、时区参数）。
5. 最后才怀疑代码，并用最小复现定位。
