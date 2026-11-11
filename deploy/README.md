# NitroWater 部署

> 单机自建：一台 Linux + Docker，仓库放持久盘（如 `/opt/nitrowater`）。所有命令都在**仓库根**执行。
> 变量清单以 **`deploy/docker/.env.prod.example`** 为准（每个键都有注释，改完不要留 `YOUR_*_HERE`）。
> 本文只讲：顺序、必填项、验收、日常运维、故障对照。

## 拓扑与单元

```
公网 nginx（TLS 终止）
  ├── <WEB_DOMAIN>  → 127.0.0.1:8080  bff     SPA + /api/** + /plugin/**
  └── <AUTH_DOMAIN> → 127.0.0.1:8090  account 登录 / OIDC / 管理端
容器内不发布：server:8081  resource:8093  docs:80  mysql  redis  piston  qdrant  seaweedfs
```

| 单元 | 文件 | 内容 |
|---|---|---|
| infra | `docker-compose.infra.yml` | mysql / redis；`edge` profile = nginx-proxy + acme-companion（自带 80/443 与证书） |
| account | `docker-compose.account.yml` | SSO / OIDC，可独立发布 |
| app | `docker-compose.app.yml` | seaweedfs / qdrant / piston / docs / resource / server / bff |

三份共享外部网络 `nitrowater-net`。**启动顺序固定：infra → account → app**（后端启动即连库）。

## 1. 前置

| 项 | 要求 |
|---|---|
| Docker | Engine 24+ / Compose v2（`docker compose version`） |
| 端口 | 只放行 80/443；8080/8081/8090/8093/3306/6379/6333/8333/8888/9333/2000 **不要**对公网开放 |
| DNS | 站点与 SSO 域名 A 记录指向本机（用 `edge` 时 80 必须公网可达，HTTP-01 签发） |
| 资源 | ≥ 4 GB 内存（mysql 512M + redis 256M + qdrant 384M + seaweedfs 512M + 三个 JVM）、≥ 20 GB 磁盘 |

## 2. 密钥

```bash
bash deploy/bin/gen-keys.sh && chmod 600 deploy/keys/private.key

openssl rand -base64 32   # NITROWATER_KEK（32 字节 Base64，丢了加密列永久不可解 → 离线备份）
openssl rand -base64 32   # DEVICE_SALT
openssl rand -base64 24   # MYSQL_ROOT_PASSWORD
openssl rand -base64 24   # DB_PASSWORD
openssl rand -base64 24   # REDIS_PASSWORD
openssl rand -hex 32      # BFF_CLIENT_SECRET
openssl rand -hex 24      # S3_SECRET_KEY
```

三个口令互不相同。`private.key` 轮换 = 全员重新登录。

## 3. 变量

```bash
cp deploy/docker/.env.prod.example deploy/docker/.env
chmod 600 deploy/docker/.env
```

**必填**（缺任一，compose 或 `check-env` 直接阻断）：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 只给 mysql 初始化与人工运维 |
| `DB_USERNAME` / `DB_PASSWORD` | 业务连库账号，**不能是 `root`**；建库建号见 §6（手工一步） |
| `REDIS_PASSWORD` | 必填，Redis 存 BFF 会话与令牌（无空口令分支） |
| `NITROWATER_KEK` / `DEVICE_SALT` | 32 字节 Base64 / 随机串，禁用 dev 默认值 |
| `JWT_ISSUER` | 必须是 URL，且等于 account 对外地址 |
| `BFF_CLIENT_SECRET` | 不能用 `nitrowater-bff-secret` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | `RESOURCE_BACKEND=s3` 时必填 |
| `AI_CHAT_API_KEY` / `AI_EMBEDDING_API_KEY` | 也可用 `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` 回落 |

**拆两个文件**（可选，推荐）：`.env` 放普通配置，密钥写 `deploy/docker/.env.prod.secrets`
（已 gitignore）。两者都要传给 compose 和门禁，后者覆盖前者；**进程环境变量优先级最高**：

```bash
bash deploy/bin/check-env.sh deploy/docker/.env deploy/docker/.env.prod.secrets
docker compose --env-file deploy/docker/.env --env-file deploy/docker/.env.prod.secrets \
  -f deploy/docker/docker-compose.infra.yml up -d
```

其余键（AI 调优、短信、内容挂载、端口绑定、镜像 tag）见 `.env.prod.example` 注释。几条容易踩的：

- `PUBLISH_BIND` / `OPS_BIND` 保持 `127.0.0.1`（Qdrant/SeaweedFS 的 UI 无认证）。
- 换域名要同步 7 个变量：`JWT_ISSUER`、`BFF_REDIRECT_URIS`、`BFF_POST_LOGOUT_REDIRECT_URIS`、
  `SSO_DEFAULT_REDIRECT`、`SSO_FORM_ACTION_ORIGINS`、`POST_LOGOUT_REDIRECT_URI`、`AVATAR_PICTURE_BASE_URL`。
- 浏览器跨域白名单 `CORS_ALLOWED_ORIGIN_PATTERNS` 必须显式列域名，禁止带凭证的通配符。
- 外置词表必须**双层**：`classpath:ai/moderation/words.txt,file:/etc/nitrowater/moderation/words.txt`；
  只用 `file:` 且路径写错会得到 `words=0`，闸门静默失效。
- `AI_DOCS_LOCATION` 指向的目录必须至少有 1 个 `.md`，否则该语料会被全量重建丢掉。
- 换 embedding 维度要换 `AI_COLLECTION`（别原地重建）。

## 4. 门禁（上线前必须 0 FAIL）

```bash
bash deploy/bin/check-env.sh          # 生产模式；Windows: .\deploy\bin\check-env.ps1
bash deploy/bin/check-env.sh --local  # 本地 dev：额外查端口，跳过 compose 相关检查
```

它按 compose 的 `environment:` 反推每个 `${VAR}`：**缺值、会回落到 `123456`/`root`/`change-me`、
仍是 `YOUR_*_HERE` 占位值**都算 FAIL；`.env` 写了却没有 compose 引用的键报 WARN（配了也不生效）。
另外校验：词表双层且非空、语料目录含 `.md`、`deploy/keys/*.key` 存在、`JWT_ISSUER` 是 URL、
`COMMUNICATION_MODE=real` 时 SMTP/短信齐全、`RESOURCE_BACKEND=cos` 时 COS 齐全。

CI/CD 里就是一行门禁（不需要封装脚本）：

```bash
bash deploy/bin/check-env.sh || exit 1
```

## 5. 启动

```bash
docker network create nitrowater-net                     # 只需一次

# 1) 中间件（自带公网入口时加 --profile edge）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d

# 1.5) 建库建号，只做一次：见 §6

# 2) 身份服务（只更新它时加 --no-deps，不碰 mysql/redis/nginx）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d

# 3) 业务面（首次或本地构建加 --build）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d

# Piston 语言包（一次装好落在 nitrowater-piston 卷）
docker exec nitrowater-piston cli/index.js ppman install java
docker exec nitrowater-piston cli/index.js ppman install c
docker exec nitrowater-piston cli/index.js ppman install c++
```

**公网入口二选一**：

- 用 `edge` profile（nginx-proxy + 自动证书）：infra 那条加 `--profile edge`，`.env` 里填
  `WEB_DOMAIN` / `AUTH_DOMAIN` / `SSL_EMAIL`。
- 用自建反代：不启用 edge，反代到本机 `127.0.0.1:8080` 与 `127.0.0.1:8090`；
  现成样例见 `deploy/docker/nginx-public-proxy.conf`（TLS、`X-Forwarded-*`、SSE 不缓冲、`/actuator|/internal` → 404）。

## 6. 建库建号（手工，一次性）

mysql 容器只负责把服务跑起来，**不挂任何 initdb 脚本**。三个库 + 一个专用账号就一段 SQL，
手工执行一次即可（官方镜像的 `MYSQL_USER`/`MYSQL_PASSWORD` 只能给单个库授权，套脚本反而多一层）。

infra 起好之后进容器（口令 = `.env` 里的 `MYSQL_ROOT_PASSWORD`）：

```bash
docker exec -it nitrowater-mysql mysql -uroot -p
```

把下面整段粘进去。两处要改：账号名与 `.env` 的 `DB_USERNAME` 一致，口令与 `DB_PASSWORD` 一致
（账号名只允许 `[A-Za-z0-9_]`）：

```sql
CREATE DATABASE IF NOT EXISTS `nitrowater_account`  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `nitrowater_biz`      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `nitrowater_resource` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'nitrowater'@'%' IDENTIFIED BY '<DB_PASSWORD>';
GRANT ALL PRIVILEGES ON `nitrowater_account`.*  TO 'nitrowater'@'%';
GRANT ALL PRIVILEGES ON `nitrowater_biz`.*      TO 'nitrowater'@'%';
GRANT ALL PRIVILEGES ON `nitrowater_resource`.* TO 'nitrowater'@'%';
FLUSH PRIVILEGES;
```

表结构不用管：account / server / resource 首次启动时由 Flyway 从基线（`V1__*.sql`）建表。

- 重复执行无害（`IF NOT EXISTS`）；改口令再跑一次 `ALTER USER` 即可。
- 数据卷是旧的、账号早就在库里 → 跳过本节，服务能连上就别动。
- 想彻底重来：infra compose `down -v` 删卷，再重跑本节（数据全丢）。

Redis 不用初始化：口令由 compose 从 `.env` 的 `REDIS_PASSWORD` 直接插值进 `redis-server` 命令行。

## 7. 验收

```bash
docker inspect -f '{{.State.Health.Status}}' nitrowater-bff        # healthy（唯一带健康检查的容器）
curl -fsS http://127.0.0.1:8090/.well-known/openid-configuration    # account 就绪
curl -fsS https://<WEB_DOMAIN>/api/public/ping                       # {"success":true,...,"pong":true}
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1                # 401：登录门禁生效且路由通
curl -I http://<WEB_DOMAIN>                                          # 301/308 → https
```

`/api/**` 除 `/api/public/**` 外都要登录，所以 curl 只验证「路由 + 门禁」，功能项在浏览器里点。
索引状态看管理端 `/admin/ai`（或在登录态查 `/api/ai/status`：`indexed=true`、`chunks>0`，首轮索引需几分钟）。

| # | 操作 | 期望 |
|---|---|---|
| 1 | 浏览器开站点 | 证书有效、无混合内容告警 |
| 2 | 登录 / 退出 | 跳 `auth.` 域并正确回门户 |
| 3 | 管理端 › AI › 词表 `reload` | 词数 > 0 |
| 4 | 问一个文档里有的问题 | 带 `[n]` 引用且链接可达 |
| 5 | 点一个插件下载 | 200 + `Content-Disposition: attachment` |
| 6 | 外网 `curl http://<host>:6333/collections` | 连接失败（端口未暴露） |
| 7 | `bash deploy/bin/ops-tunnel.sh --host <host>` | 本机 `127.0.0.1:6333/dashboard` 可用 |

## 8. 首个管理员（必做）

角色由 Flyway 种入，但没有用户绑定。先注册一个账号，再授权：

```bash
docker exec -it nitrowater-mysql mysql -uroot -p nitrowater_account -e "
INSERT IGNORE INTO user_role (uid, role_id)
SELECT u.uid, r.id FROM \`user\` u JOIN \`role\` r ON r.code = 'SUPER_ADMIN'
WHERE u.username = '<用户名>';"
```

然后**重新登录**（角色在登录时载入），`/admin/ai/settings` 应可打开。

## 9. 日常运维

**三类内容，三个入口**（都不需要重跑 jar / 重建镜像）：

| 内容 | 放哪 | 更新动作 |
|---|---|---|
| AI 词表 / 语料 | `deploy/content/**`（只读挂到 `/etc/nitrowater/*`） | 覆盖文件 → `POST /api/admin/ai/moderation/reload` / `reindex` |
| 插件站点文档 | `deploy/plugin-docs/<plugin>/docs/` | 覆盖产物（必要时 `docker restart nitrowater-docs`） |
| 插件 Jar | resource + SeaweedFS（不在 `deploy/`） | `pwsh -File bin\dev\sync-res.ps1` 或 CI 调 `POST /internal/resource/publish` |

`deploy/content` 只服务 AI 引用，站点文档与插件 Jar 是另外两条链路，别混用。契约见
`deploy/content/README.md`。用户上传默认关闭（`RESOURCE_USER_UPLOAD=false` → `503 uploads_disabled`），
插件发布与下载不受影响。

**运维 UI 不暴露**：Qdrant Dashboard / SeaweedFS filer 走 SSH 隧道
（`bash deploy/bin/ops-tunnel.sh --host <host>`，本地端口与远端一致），不要改 `OPS_BIND`。

**备份**（建议每天）：

```bash
docker run --rm -v nitrowater-mysql:/var/lib/mysql -v "$PWD":/backup alpine \
  tar czf /backup/mysql-$(date +%F).tgz -C /var/lib/mysql .
docker run --rm -v nitrowater-qdrant:/qdrant/storage -v "$PWD":/backup alpine \
  tar czf /backup/qdrant-$(date +%F).tgz -C /qdrant/storage .
```

**回滚**：

| 场景 | 动作 |
|---|---|
| 应用异常 | 换回上一 `APP_IMAGE_TAG` → app compose `up -d` |
| account 异常 | 换回上一 `ACCOUNT_IMAGE_TAG` → `up -d --no-deps account` |
| 关闭公网入口 | `docker compose -f deploy/docker/docker-compose.infra.yml --profile edge stop nginx-proxy acme-companion` |
| 拦截误伤 | 管理端模式切回 `LOG`，或改词表后 `moderation/reload` |
| 索引坏了 | `POST /api/admin/ai/reindex`（换维度则改 `AI_COLLECTION` 再重建） |
| 证书问题 | 保留 `nginx_certs` 卷；修 DNS/端口后重启 `acme-companion` |

**全站 AI 额度**（上线一周后再做）：管理端 › AI › 运行设置填每日 token 额度，**先只统计不熔断**，
观察 3–7 天用量曲线后按峰值 ×1.5 设限再打开；误判用「解除今日熔断」。熔断只拦调用模型的
`/api/ai/agent/**`，历史会话与 `/` 指令仍可用，UTC 0 点（北京时间 08:00）自动恢复。

## 10. 故障对照

| 症状 | 原因 | 处理 |
|---|---|---|
| 登录后跳 `localhost:5173` | `SSO_DEFAULT_REDIRECT` / `BFF_REDIRECT_URIS` 没改 | 改 `.env` 重启 bff/account |
| `invalid redirect_uri` | `BFF_REDIRECT_URIS` 与浏览器实际回调不一致 | 同上，注意 https 与结尾斜杠 |
| account/server 起不来，报 `Access denied for user` | 没建号 / 口令与 `.env` 不符 | 见 §6 |
| 证书一直不签发 | 80 端口不通 / DNS 未生效 | 安全组与 `dig`；`docker logs nitrowater-acme` |
| 管理端 403 | 未绑定角色，或绑定后未重新登录 | §8 SQL + 重新登录 |
| AI 回答无引用 | 索引为空 / embedding key 错 | 看 `/api/ai/status` 的 `error`，修 key 后重建索引 |
| 大 Jar 发布 413 | 内部发布走 `RESOURCE_MAX_SIZE`（10MB） | 临时调高该值；`.jar` 的 admin 上传走 64MB 限制 |
| 图片裂图（开放上传后） | S3 只在回环，presigned 直连失败 | `RESOURCE_PRESIGN=false` 同源流式，或给 S3 开边缘 vhost |

## 11. 脚本（`deploy/bin/`）

`.ps1` = Windows，`.sh` = Linux/macOS（首次 `chmod +x deploy/bin/*.sh`）。

| 用途 | 脚本 |
|---|---|
| 生成 JWT 密钥 | `gen-keys.{ps1,sh}` |
| **上线环境自检 / 部署门禁** | `check-env.{ps1,sh}` |
| 备份 MySQL | `backup_mysql.{ps1,sh}`（读 `MYSQL_ROOT_PASSWORD`，兼容旧 `DB_PASS`） |
| 运维 UI 隧道（Qdrant / SeaweedFS） | `ops-tunnel.{ps1,sh}` |

这里只放**上线/运维**要用的。开发期的东西（清库、种子数据、同步、本地存储、开发密钥）
都在 `bin/dev/`，别混进来。

## 12. CI/CD（GitHub Actions）

两个工作流**都只手动触发**（自动触发已注释掉，需要时再开）：仓库页 Actions → 选工作流 → `Run workflow`。

| 文件 | 手工跑法 | 干什么 |
|---|---|---|
| `.github/workflows/ci.yml` | Actions → CI | 后端 `gradlew build`（含单测+JaCoCo）、两个前端 lint+build、compose 结构校验、`deploy/bin`·`bin/dev` 脚本语法、Dockerfile 试构建 `target=bff` |
| `.github/workflows/deploy.yml` | Actions → Deploy（填 `tag`） | 矩阵构建 4 个镜像推 GHCR → SSH 到服务器：同步部署包 → `check-env.sh` → 按 infra → account → app `pull` + `up -d` → 探活 |

命令行等价物（装了 `gh` 时）：

```bash
gh workflow run ci.yml
gh workflow run deploy.yml -f tag=v1.2.3
```

**一次性准备**

1. 服务器上 `DEPLOY_ROOT`（默认 `/opt/nitrowater`）是本仓库的 git clone，且 §1~§6 已完成（含手工建库建号）；
   `deploy/docker/.env`（+ 可选 `.env.prod.secrets`）、`deploy/keys/`、`deploy/content/` 已就位。
2. 仓库 Secrets：`SSH_HOST` / `SSH_USER` / `SSH_KEY`；GHCR 包是 private 时再加 `GHCR_USER` + `GHCR_PAT`。
3. 仓库 Variables（可选）：`DEPLOY_ROOT`、`DEPLOY_BRANCH`（默认 `main`）、
   `COMPOSE_PROFILE`（用自带 edge 就填 `edge`，自建反代留空）。

**发布与回滚**

`Run workflow` 时 `tag` 填什么就构建+部署什么：`latest`、版本号（`v1.2.3`）、或某个提交的 sha
（`gh workflow run deploy.yml -f tag=<sha>`）。镜像同时打 `<tag>` 与 `<sha>` 两个标签，所以
**回滚 = 把 `tag` 填成上一个提交的 sha 再跑一次**。

服务器侧行为：`git reset --hard origin/<DEPLOY_BRANCH>` 同步部署包（compose / 脚本），
`.env`、`keys`、`content` 是 gitignored 不受影响；镜像 tag 由工作流注入。
所以**部署包跟 `main`，镜像跟 tag**——回滚镜像不会把 compose 一起退回去。
