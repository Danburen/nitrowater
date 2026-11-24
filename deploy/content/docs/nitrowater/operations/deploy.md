# 部署与运维

## 1. 部署单元

| 单元 | 镜像/进程 | 端口 | 依赖 |
|---|---|---|---|
| account | 容器 / jar | 8090 | MySQL(account)、Redis、`deploy/keys` |
| bff | 容器 / jar | 8080 | account(OIDC discovery)、Redis、server、resource |
| server | 容器 / jar | 8081 | MySQL(biz)、Redis、Qdrant、Piston、account(JWKS) |
| resource | 容器 / jar | 8093 | MySQL(resource)、Redis、对象存储 |
| web | BFF 托管 `/dist` | - | 构建产物 |

Compose 文件（三份纵向拆分的单元，共享外部网络 `nitrowater-net`）：

| 文件 | 内容 |
|---|---|
| `deploy/docker/docker-compose.infra.yml` | MySQL / Redis；`edge` profile = nginx-proxy + acme-companion |
| `deploy/docker/docker-compose.account.yml` | account（SSO / OIDC），可独立发布 |
| `deploy/docker/docker-compose.app.yml` | Qdrant / Piston / resource / server / bff |
| 根 `Dockerfile` | 多 target 构建（account / bff / server / resource），含 node stage 构建两个 SPA |

```bash
# 预检
bash deploy/bin/check-env.sh

# 逐个单元启动（仓库根）
docker network create nitrowater-net    # 只需一次
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d --build
```

`docker-compose.app.yml` / `docker-compose.account.yml` 使用外部网络 `nitrowater-net`，需要先创建。
Piston 只连 `nitrowater-runner-net`（app compose 内创建），server 同时连两个网络。

`mysql` 容器**不挂 initdb 脚本**：首次部署要在 infra 起来之后，按 `deploy/docs/bootstrap.md` §5
手工建 `nitrowater_{account,biz,resource}` 三个库和业务账号，再起 account / app（顺序反了会 `Access denied`）。
表结构由 Flyway 首次启动从基线重放。

## 2. 环境变量

| 变量 | 使用者 | 说明 |
|---|---|---|
| `JWT_ISSUER` | 全部 | **必须是所有服务与浏览器都可访问的 URL**；account 用它签发，server/resource 用它校验 |
| `NITROWATER_KEK` | account | 32 字节 AES KEK 的 Base64，缺失即启动失败 |
| `DEVICE_SALT` | account | 设备指纹盐 |
| `BFF_CLIENT_SECRET` | account / bff | OIDC 机密客户端密钥，yml 无默认值 |
| `DB_USERNAME` / `DB_PASSWORD` | 全部 | 业务连库账号，手工创建（`deploy/docs/bootstrap.md` §5）；compose 无默认值，缺失即启动失败 |
| `SPRING_DATASOURCE_URL` | 全部 | 含 `connectionTimeZone=UTC&preserveInstants=true` |
| `RESOURCE_INTERNAL_TOKEN` | resource / server | `/internal/**` 共享密钥 |
| `RESOURCE_DATA_DIR` | resource | 资源字节落盘的宿主机目录（bind mount 到 `/data/resource`），`local` 后端必填 |
| `RESOURCE_BACKEND` | resource | `local`（默认）/ `cos` |
| `RESOURCE_PRESIGN_ENABLED` | resource | 默认 `false`：读写都经服务自身端点，客户端不直连存储 |
| `RESOURCE_UPLOAD_ENABLED` | resource | 上传总闸；默认 `true`，角色仍限 `ROLE_ADMIN` / `ROLE_SUPER_ADMIN` |
| `PISTON_BASE_URL` | server | 容器内 `http://piston:2000`；本机 `http://localhost:2000` |
| `AI_*` | server | chat/embedding key、模型、`AI_TOP_K`、限流等 |
| `COS_*` | resource | 仅 `RESOURCE_BACKEND=cos` 时需要；默认整段注释。`RESOURCE_DATA_DIR` 仍必填（bind mount 不变） |

密钥不入库、不入仓；生产用环境变量或密钥管理服务注入。

## 3. issuer 与网络

`JWT_ISSUER` 必须是公网 URL，且浏览器与所有服务看到同一个字符串（生产建议 `https://auth.example.com`）。
BFF 启动时会拉 account 的 discovery，account 未就绪时 BFF 会启动失败。

## 4. 资源与容量

| 服务 | compose `mem_limit` | 说明 |
|---|---|---|
| account | 640m | 含两个 SPA 静态资源 |
| server | 768m | AI 与沙箱最吃内存 |
| resource | 512m | 大文件走流式，不占堆 |
| bff | 512m | 会话与静态托管 |
| piston | 1024m | 沙箱，privileged |
| MySQL / Redis / Qdrant | 见 compose | Qdrant 建议独立卷 |

沙箱 `app.sandbox.memory-limit-bytes`（默认 256MB）不得超过 Piston 的 `PISTON_RUN_MEMORY_LIMIT`；全局并发默认 2，需要按机器容量调整。

## 5. 数据备份

```powershell
.\deploy\bin\backup_mysql.ps1            # Windows
./deploy/bin/backup_mysql.sh             # Linux
```

- 三个库一起备份；Qdrant 的向量索引可以重建，不必备份（语料在仓库里）。
- 清库重建只在开发期做：`bin\dev\reset-db.ps1`（`deploy/bin/` 里不再放这类脚本）。

## 6. 发布流程

```bash
# 预检
bash deploy/bin/check-env.sh

# 全量
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-deps
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d

# 只更新 account（不碰 mysql/redis/nginx）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-deps
```

发布前检查：

1. `.\gradlew.bat build` 全绿。
2. 前端 `npm run build` 通过，产物是最新的。
3. 插件文档站已移出本站：更新数据库里的 `docsUrl` 即可；NitroWater 文档是纯 markdown 语料，无需构建。离线工具箱随 `nitrowater-tools` 独立镜像发布。
4. 新增 Flyway 版本而不是改已应用的迁移。
5. 环境变量齐全（尤其 `JWT_ISSUER`、`NITROWATER_KEK`、`BFF_CLIENT_SECRET`、`RESOURCE_DATA_DIR`）。

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
| 更新插件文档地址 | 在插件表单里修改 `docsUrl`（完整外部 URL） |
| 重新生成 JWT 密钥 | `deploy\bin\gen-keys.ps1`（会使现有 token 失效） |
| 环境自检 | `deploy\bin\check-env.ps1` |
