# 部署与运维

## 1. 部署单元

| 单元 | 镜像/进程 | 端口 | 依赖 |
|---|---|---|---|
| account | 容器 / jar | 8090 | MySQL(account)、Redis、`deploy/keys` |
| bff | 容器 / jar | 8080 | account(OIDC discovery)、Redis、server、resource |
| server | 容器 / jar | 8081 | MySQL(biz)、Redis、Qdrant、Piston、account(JWKS) |
| resource | 容器 / jar | 8093 | MySQL(resource)、Redis、对象存储 |
| web | BFF 托管 `/dist` | - | 构建产物 |

Compose 文件：

| 文件 | 内容 |
|---|---|
| `deploy/docker/docker-compose.infra.yml` | MySQL / Redis / Qdrant / SeaweedFS |
| `deploy/docker/docker-compose.app.yml` | account / resource / server / bff |
| `deploy/docker/docker-compose.piston.yml` | Piston 沙箱 |
| 根 `Dockerfile` | 多 target 构建（account / bff / server / resource），含 node stage 构建两个 SPA |

```bash
docker compose -f deploy/docker/docker-compose.infra.yml up -d
docker compose -f deploy/docker/docker-compose.app.yml up -d --build
docker compose -f deploy/docker/docker-compose.piston.yml up -d
```

`docker-compose.app.yml` 使用外部网络 `nitrowater-net`，需要先由 infra 创建。

## 2. 环境变量

| 变量 | 使用者 | 说明 |
|---|---|---|
| `JWT_ISSUER` | 全部 | **必须是所有服务与浏览器都可访问的 URL**；account 用它签发，server/resource 用它校验 |
| `NITROWATER_KEK` | account | 32 字节 AES KEK 的 Base64，缺失即启动失败 |
| `DEVICE_SALT` | account | 设备指纹盐 |
| `BFF_CLIENT_SECRET` | account / bff | OIDC 机密客户端密钥，yml 无默认值 |
| `DB_PASSWORD` | 全部 | MySQL 密码（compose 默认 `123456`） |
| `SPRING_DATASOURCE_URL` | 全部 | 含 `connectionTimeZone=UTC&preserveInstants=true` |
| `RESOURCE_INTERNAL_TOKEN` | resource / server | `/internal/**` 共享密钥 |
| `S3_ENDPOINT` / `S3_PUBLIC_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | resource | S3 后端；`S3_PUBLIC_ENDPOINT` 是浏览器可达地址 |
| `RESOURCE_BACKEND` | resource | `s3` / `cos` / `local` |
| `PISTON_BASE_URL` | server | 容器内 `http://piston:2000`；本机 `http://localhost:2000` |
| `AI_*` | server | chat/embedding key、模型、`AI_TOP_K`、限流等 |
| `S3_SECRET_KEY` | resource | 必填，缺失启动失败 |

密钥不入库、不入仓；生产用环境变量或密钥管理服务注入。

## 3. issuer 与网络

`JWT_ISSUER` 是最容易配错的一项：

- 必须是一个 **URL**（OIDC Discovery 要求 issuer 与 `metadata.issuer` 完全一致）。
- 浏览器、BFF、server、resource 必须看到**同一个字符串**；容器内写 `http://account:8090` 而浏览器写 `https://auth.example.com` 会导致 issuer 不匹配。
- 生产建议统一为公网域名（如 `https://auth.nitrowater.cn`），并让容器能解析该域名（内部 DNS 或 hosts）。

BFF 启动时会拉 account 的 discovery；account 未就绪时 BFF 会启动失败，因此 compose 里需要依赖排序或重试。

## 4. 资源与容量

| 服务 | compose `mem_limit` | 说明 |
|---|---|---|
| account | 640m | 含两个 SPA 静态资源 |
| server | 见 compose | AI 与沙箱最吃内存 |
| resource | 见 compose | 大文件走流式，不占堆 |
| MySQL / Redis / Qdrant | 见 infra compose | Qdrant 建议独立卷 |

沙箱 `app.sandbox.memory-limit-bytes`（默认 256MB）不得超过 Piston 的 `PISTON_RUN_MEMORY_LIMIT`；全局并发默认 2，需要按机器容量调整。

## 5. 数据备份

```powershell
.\deploy\bin\backup_mysql.ps1            # Windows
./deploy/bin/backup_mysql.sh             # Linux
```

- 三个库一起备份；Qdrant 的向量索引可以重建，不必备份（语料在仓库里）。
- `deploy/bin/reset-sso-db.ps1` 只用于重置 SSO 库，生产慎用。

## 6. 发布流程

```powershell
.\deploy\bin\deploy.ps1      # 构建 + 推送/部署
./deploy/bin/deploy.sh
```

发布前检查：

1. `.\gradlew.bat build` 全绿。
2. 前端 `npm run build` 通过，产物是最新的。
3. 如需更新 VeloChatX 文档站，先运行 `bin\dev\sync-docs.ps1`（NitroWater 文档是纯 markdown 语料，无需构建）。
4. 新增 Flyway 版本而不是改已应用的迁移。
5. 环境变量齐全（尤其 `JWT_ISSUER`、`NITROWATER_KEK`、`BFF_CLIENT_SECRET`、`S3_SECRET_KEY`）。

## 7. 健康检查与日志

| 检查 | 方式 |
|---|---|
| 进程存活 | `docker ps` / `Get-Process java` |
| 端口 | `Get-NetTCPConnection -State Listen -LocalPort 8080` |
| 匿名探活 | `GET /api/public/ping` |
| AI 状态 | `GET /api/ai/status`（需登录） |
| 日志（容器） | `docker logs -f nitrowater-server` |
| 日志（本地） | `bin\.run\logs\<module>.out.log` |

`/actuator/**` 默认 `denyAll`，不要把它当外部健康检查端点；用 `/api/public/ping`。

## 8. 常见运维操作

| 操作 | 命令 |
|---|---|
| 重建 AI 索引 | `POST /api/admin/ai/reindex`（管理员） |
| 重置业务库 | `bin\dev\reset-db.ps1 -Db biz -Force -Restart` |
| 装 Piston 语言 | `bin\dev\install-piston-langs.ps1` |
| 同步 VeloChatX 文档 | `bin\dev\sync-docs.ps1` |
| 重新生成 JWT 密钥 | `deploy\bin\gen-keys.ps1`（会使现有 token 失效） |
| 环境自检 | `deploy\bin\check-env.ps1` |
