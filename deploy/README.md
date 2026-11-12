# NitroWater 部署

> 单机自建：一台 Linux + Docker，仓库放持久盘（如 `/opt/nitrowater`）。所有命令都在**仓库根**执行。
> 变量清单以 **`deploy/docker/.env.example`** 为准（每个键都有注释，改完不要留 `YOUR_*_HERE`）。
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
| 宿主机工具 | `docker` + compose 插件、`bash`、`openssl`、`curl`。**不需要 git / JDK / Node**（部署包由 `kit` 镜像带上来，见 §12） |

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
openssl rand -hex 12      # RESOURCE_INTERNAL_TOKEN（/internal 发布通道）
date +%s                  # APP_SESSION_EPOCH（每次发布换新值 = 旧会话全部失效）
```

三个口令互不相同。`private.key` 轮换 = 全员重新登录。生成的值填进 §3 的 `.env`，
口令/密钥建议单独放 `deploy/docker/.env.prod.secrets`（见 §3.2）。

## 3. 变量（`.env`）

### 3.1 放在哪、谁维护

```bash
cd /opt/nitrowater                            # DEPLOY_ROOT
cp deploy/docker/.env.example deploy/docker/.env
chmod 600 deploy/docker/.env
```

- 模板是 `deploy/docker/.env.example`（唯一模板，全部是占位值，不含真实配置）。
  `deploy/docker/.env*` 整目录被 gitignore，**只有 `*.example` 进仓库**。
- **CI/CD 不生成、不修改它**：`build.yml` 只推镜像；`deploy.yml` 只用进程环境变量覆盖 4 个键（见 3.6）；
  部署时 kit 镜像只 `docker cp` 到 `deploy/`，宿主机没有 git 工作区，`.env` 完全是你的状态。
- 换机器 = 手工搬 `.env`（连同 `deploy/keys/`、`deploy/content/`）。

### 3.2 拆两个文件（可选，推荐）

`.env` 放普通配置，口令/密钥放 `deploy/docker/.env.prod.secrets`；两者都传给 compose 和门禁，
**后者覆盖前者**，进程环境变量优先级最高：

```bash
bash deploy/bin/check-env.sh deploy/docker/.env deploy/docker/.env.prod.secrets
docker compose --env-file deploy/docker/.env --env-file deploy/docker/.env.prod.secrets \
  -f deploy/docker/docker-compose.infra.yml up -d
```

不带参数时 `check-env.sh` 默认就读这一对；`deploy.yml` 也只在第二个文件存在时才追加 `--env-file`。

### 3.3 必填：空值直接 FAIL（没有它们起不来）

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 只给 mysql 初始化与人工运维 |
| `DB_USERNAME` / `DB_PASSWORD` | 业务连库账号，**不能是 `root`**；建库建号见 §6（手工一步） |
| `REDIS_PASSWORD` | 必填，Redis 存 BFF 会话与令牌（无空口令分支） |
| `NITROWATER_KEK` / `DEVICE_SALT` | 32 字节 Base64 / 随机串，禁用 dev 默认值 |
| `JWT_ISSUER` | 必须是 URL，且等于 account 对外地址 |
| `BFF_CLIENT_SECRET` | 不能用 `nitrowater-bff-secret` |
| `APP_SESSION_EPOCH` | 时间戳；每次发布换新值 = 旧会话全部失效 |
| `RESOURCE_INTERNAL_TOKEN` | 留空也能启动，但 `/internal` 发布通道返回 503 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | `RESOURCE_BACKEND=s3` 时必填 |
| `AI_CHAT_API_KEY` / `AI_EMBEDDING_API_KEY` | 也可用 `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` 回落 |

### 3.4 对外地址：一律写【浏览器看到的公网地址】

不要写 `localhost` / 内网 IP / 容器名，否则回调与跳转会拼出错误地址（典型症状：登录后 400、跳回 `localhost`）。

| 变量 | 例子 |
|---|---|
| `JWT_ISSUER` | `https://auth.example.com`（必须等于 account 对外地址） |
| `BFF_REDIRECT_URIS` | `https://example.com/login/oauth2/code/nitrowater` |
| `BFF_POST_LOGOUT_REDIRECT_URIS` | `https://example.com,https://example.com/` |
| `SSO_DEFAULT_REDIRECT` / `POST_LOGOUT_REDIRECT_URI` | `https://example.com`（前者必须落在后者列表里） |
| `SSO_FORM_ACTION_ORIGINS` | `https://example.com,https://auth.example.com` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 同上；**禁止 `*`**（带凭证的通配符等于放开所有来源） |
| `S3_PUBLIC_ENDPOINT` / `AVATAR_PICTURE_BASE_URL` | `https://fs.example.com`（浏览器可达，不是容器地址） |

**换域名 = 一次性同步 7 个变量**：`JWT_ISSUER`、`BFF_REDIRECT_URIS`、`BFF_POST_LOGOUT_REDIRECT_URIS`、
`SSO_DEFAULT_REDIRECT`、`SSO_FORM_ACTION_ORIGINS`、`POST_LOGOUT_REDIRECT_URI`、`AVATAR_PICTURE_BASE_URL`。

反代还必须是「干净」的：发 `X-Forwarded-Proto` / `X-Forwarded-Host`、放开 `client_max_body_size`
（插件 jar 64 MB）与 `proxy_read_timeout`（AI 流式回答），并显式 403/404 掉
`/actuator/**` 与 `/internal/**`。片段见 §5。

### 3.5 条件必填

| 开关 | 条件 |
|---|---|
| `COMMUNICATION_MODE=real` | SMTP（`MAIL_*`）与阿里云短信（`ALIBABA_CLOUD_*`、`ALIYUN_SMS_*`）**两组都必填**，缺一项 FAIL；没有短信资质就先别切 `real` |
| `RESOURCE_BACKEND`（`s3` / `cos` / `local`） | `cos` 要 `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET`；用 `s3` 就把 `COS_*` 整段注释掉（**别留占位值**，会 FAIL） |
| `RESOURCE_USER_UPLOAD` | 审核流程上线前保持 `false`（接口返回 503） |
| `S3_ENDPOINT` / `PISTON_BASE_URL` | 默认指向 compose 自带的 seaweedfs / piston，改用外部服务时才需要写 |

### 3.6 镜像地址：写死 2 个键，tag 留给 CI

```ini
REGISTRY=ghcr.io
GITHUB_REPOSITORY=danburen/nitrowater      # 不填会回落到 your-org/nitrowater → manifest unknown
```

`ACCOUNT_IMAGE_TAG` / `APP_IMAGE_TAG` **不要写进 `.env`**，交给 CI 注入（进程环境变量优先级更高，
写了也会被覆盖，只会让手工排障和线上看到的不一致）。

| 键 | `.env` | CI 部署时 |
|---|---|---|
| `REGISTRY` / `GITHUB_REPOSITORY` | 写（手工排障要用） | 被 CI 的环境变量覆盖为同一个值 |
| `ACCOUNT_IMAGE_TAG` / `APP_IMAGE_TAG` | 留空 / 注释 | 注入成本次 run 的 tag |
| 其余所有键 | 唯一来源 | 只读不写 |

### 3.7 只有 compose 引用的键才会进容器

模板已覆盖三份 compose 里 `${VAR}` 的全部引用。`application.yml` 里另有几十个调优键
（`AI_AGENT_*`、`AI_CONTEXT_*`、`AI_TOP_K` 等）当前**没有**在 compose 透传，写进 `.env` 不生效；
要改就在 `docker-compose.app.yml` 的 `environment:` 加一行 `${KEY:-默认值}`。

`.env` 里写了却没被 compose 引用的键，`check-env` 会报 WARN（配了也不生效）。

### 3.8 几条容易踩的

- `PUBLISH_BIND` / `OPS_BIND` 保持 `127.0.0.1`（Qdrant/SeaweedFS 的 UI 无认证）。
- 外置词表必须**双层**：`classpath:ai/moderation/words.txt,file:/etc/nitrowater/moderation/words.txt`；
  只用 `file:` 且路径写错会得到 `words=0`，闸门静默失效。
- `AI_DOCS_LOCATION` 指向的目录必须至少有 1 个 `.md`，否则该语料会被全量重建丢掉。
- 换 embedding 维度要换 `AI_COLLECTION`（别原地重建）。
- 不要在值后面写行内注释：`DB_PASSWORD=abc123  # 密码` 会把 `#` 和后面整段当成口令。
- 不要在 Windows 上编辑后直接上传：CRLF 会让值尾带 `\r`（`check-env` 容错，compose 不一定）；
  已经带上了就 `sed -i 's/\r$//' deploy/docker/.env`。
- `NITROWATER_KEK` 一旦定下来永久不能改（改了所有加密列都解不开），生成后立刻离线备份。
- 数据卷已存在时改 `DB_PASSWORD` 不会改库里的口令，需要手工 `ALTER USER`。
- 走 HTTPS 时 `SESSION_COOKIE_SECURE=true`。

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

> 宿主机上还没有 `deploy/` 目录？先看 §12 的「部署包」：解一次 kit（`docker create ... unused` +
> `docker cp`）就有了 compose 和脚本，再回来填 `.env`。
> 另外 `deploy/plugin-docs/`（约 1.8 MB 静态文档，**不在 kit 镜像里**）要单独 scp 上去，
> 否则 `/plugin/*` 会 404。

**之后不要手敲上面三条了**，用唯一发布入口（门禁 → pull → up -d --no-build → 探活）：

```bash
bash deploy/bin/deploy.sh --tag <tag>            # 上线（手动与 CI 同一条命令）
bash deploy/bin/deploy.sh --only account         # 只动身份服务
bash deploy/bin/deploy.sh --help                 # 全部参数
```

**公网入口二选一**：

- 用 `edge` profile（nginx-proxy + 自动证书）：infra 那条加 `--profile edge`，`.env` 里填
  `WEB_DOMAIN` / `AUTH_DOMAIN` / `SSL_EMAIL`。
- 用自建反代：不启用 edge，反代到本机 `127.0.0.1:8080`（站点）与 `127.0.0.1:8090`（SSO）。
  **这份配置不在仓库里**——它属于你那台 nginx，由你手动维护。但有四件事必须做对，
  漏了都会伪装成应用 bug（症状见 §10）：

  ```nginx
  # 1) 转发头：缺了 account 会把回调拼成 http://，BFF 报 invalid redirect_uri
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header X-Forwarded-Host  $http_host;
  proxy_set_header X-Real-IP         $remote_addr;

  # 2) 上传上限：插件 jar 是 64 MB，nginx 默认 1m → 413
  client_max_body_size 100m;

  # 3) SSE 长连接：AI 回答逐字下发，默认 proxy_read_timeout 60s 会把它切断（后端 180s）
  proxy_buffering off;          # 兜底；BFF 自己已回 X-Accel-Buffering: no
  proxy_read_timeout 300s;
  proxy_send_timeout 300s;

  # 4) 管理面：容器侧本来就不发布管理端口，这里做双保险
  location ~ ^/(actuator|internal)(/|$) { return 404; }
  ```

  用 edge profile 时第 2、3 条同样需要，加在 `nginx_vhost` 卷里的 `default_location` 上。

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

**MySQL 需要 keyring 吗？不需要。** 敏感字段（`user_data.email_encrypted` / `phone_encrypted`、AI 供应商 key）
是**应用层加密**：`NITROWATER_KEK`（AES-256-GCM）包住每条记录的 DEK（存 `encryption_data_key` 表），
落库的本来就是密文。没有用 InnoDB 表空间加密（`ENCRYPTION='Y'`），所以不需要 `keyring_file` /
`early-plugin-load` / 额外的 keyring 卷——那套是"数据库自己加密数据文件"才需要的。

推论：**KEK 必须和数据库备份分开保管**。没有 KEK，`mysqldump` 出来的密文解不开；
反之 KEK 泄露 = 密文全裸。换 KEK 前先确认是否需要重新加密存量记录。

## 7. 验收

```bash
docker inspect -f '{{.State.Health.Status}}' nitrowater-bff        # healthy（唯一带健康检查的容器）
curl -fsS http://127.0.0.1:8090/.well-known/openid-configuration    # account 就绪
curl -fsS https://<WEB_DOMAIN>/api/public/ping                       # {"success":true,...,"pong":true}
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1                # 401：登录门禁生效且路由通
curl -I http://<WEB_DOMAIN>                                          # 301/308 → https
```

反代层再验两条（**自建反代和 edge 都要**，失败症状见 §10）：

```bash
# 1) 管理面必须打不到：期望 404 或 401，出现 200 就是双保险没做
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health

# 2) 上传上限：5 MB 请求应该是 401/400（说明透到了应用），绝不能是 413（nginx 默认 1m）
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  --data-binary @<(head -c 5m /dev/zero) https://<WEB_DOMAIN>/api/public/ping
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
| 8 | 登录后问一个长问题 | 首字 1~3 秒内出现、逐字增长、中途不断流（断流 = 反代 `proxy_read_timeout`） |

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
| **上传报 413，容器日志里没有这条请求** | 反代 `client_max_body_size` 还是 nginx 默认的 1m | 反代加 `client_max_body_size 100m;`（§5） |
| **AI 回答说一半就断 / 整段一次性刷出** | 反代 `proxy_read_timeout` 60s 或 buffering | `proxy_buffering off; proxy_read_timeout 300s;`（§5） |
| 图片裂图（开放上传后） | S3 只在回环，presigned 直连失败 | `RESOURCE_PRESIGN=false` 同源流式，或给 S3 开边缘 vhost |

## 11. 脚本（`deploy/bin/`）

`.ps1` = Windows，`.sh` = Linux/macOS（首次 `chmod +x deploy/bin/*.sh`）。

| 用途 | 脚本 |
|---|---|
| **上线 / 回滚（唯一发布入口，CI 与手动同一条路）** | `deploy.sh`（`--tag` / `--only` / `--with-kit` / `--help`） |
| 生成 JWT 密钥 | `gen-keys.{ps1,sh}` |
| **上线环境自检 / 部署门禁** | `check-env.{ps1,sh}` |
| 备份 MySQL | `backup_mysql.{ps1,sh}`（读 `MYSQL_ROOT_PASSWORD`，兼容旧 `DB_PASS`） |
| 运维 UI 隧道（Qdrant / SeaweedFS） | `ops-tunnel.{ps1,sh}` |

这里只放**上线/运维**要用的。开发期的东西（清库、种子数据、同步、本地存储、开发密钥）
都在 `bin/dev/`，别混进来。

## 12. CI/CD（GitHub Actions）

三个工作流**都只手动触发**（自动触发已注释掉，需要时再开）：仓库页 Actions → 选工作流 → `Run workflow`。
**构建与部署是两件事**：`build.yml` 先跑检查再产镜像，`deploy.yml` 只搬已构建好的镜像，`release.yml` 串起来一次做完。

| 文件 | 手工跑法 | 干什么 | 碰服务器 |
|---|---|---|---|
| `.github/workflows/build.yml` | Actions → Build（填 `tag`） | 先 `verify`（后端 build+单测+JaCoCo、两个前端 lint+build、compose/脚本语法守卫），全绿后 `docker buildx bake` 一次推 **4 个应用镜像 + `kit`** 到 GHCR：`<tag>` + `<sha>` 双标签。**不部署** | 否 |
| `.github/workflows/deploy.yml` | Actions → Deploy（填 `tag`） | preflight 确认 **5 个镜像**存在 → SSH：`docker pull` + `docker cp` 解出 kit → `bash deploy/bin/deploy.sh`（门禁 → infra/account/app `pull` + `up -d --no-build` → 探活）。**不构建** | 是 |
| `.github/workflows/release.yml` | Actions → Release（填 `tag`） | `build.yml` → 成功后 `deploy.yml`（`needs` 链，构建失败不动线上） | 是 |

`docker-bake.hcl`（仓库根）是全部镜像的构建清单：4 个应用 target 共享 Dockerfile 的 `build` 阶段（全量 gradle），
`bake` 单次调用把它**只跑一次**；分开 `docker build --target` 会跑四次、并重复下载依赖。
第 5 个 target `kit` 与之无关：`FROM scratch` + 二十来个文本文件，构建只要 1~2 秒。

> **三份前端的构建位置**：都在**镜像里**，workflow 不再单独跑前端 job——
> `bff` 镜像的 `web-ui` stage（`nitrowater-web`：`npm ci` + vue-tsc + vite build → `dist` → jar 的 `classpath:/static/`），
> `account` 镜像的 `admin-ui` / `auth-ui` stage（pnpm workspace → `nitrowater-account/src/main/resources/static/{admin,auth}`）。
> 所以服务器只吃镜像，不需要 dist/；`build.yml` 的 `ui-web` / `ui-account` 两个 job 是 lint + 类型检查 + build 的**质量门禁**，
> 产物不参与部署。Dockerfile 里 gradle 用 `-PskipAdminUi -PskipAuthUi` 跑，避免 stage 内二次调 node。

命令行等价物（装了 `gh` 时）：

```bash
gh workflow run build.yml   -f tag=v1.2.3   # 先攒镜像，挑时间再上
gh workflow run deploy.yml  -f tag=v1.2.3   # 只上线（回滚也用它）
gh workflow run release.yml -f tag=v1.2.3   # 构建 + 上线一条龙
```

**选哪个**：日常发布用 `release`；想"白天构建、夜里切换"或先构建攒着，就 `build` 再 `deploy`。
只改 compose / 脚本也一样要走 `build`（kit 会重打，1~2 秒），然后 `deploy` —— 这是因为 compose 文件现在也随镜像走。

> `deploy` 的 preflight 用 `docker manifest inspect` 逐个查 GHCR；GHCR 包没链接到本仓库
> （或包是 private 但 GITHUB_TOKEN 无权限）时会误报缺失——此时勾 `skip_preflight` 跳过，
> 真正缺失会在 `compose pull` 阶段报错，不会静默上线旧镜像。

**一次性准备**

1. 服务器上 `DEPLOY_ROOT`（默认 `/opt/nitrowater`）**不需要 git clone**：部署包由 `kit` 镜像带上来
   （见下面的「部署包」），§1~§6 已完成（含手工建库建号）；
   `deploy/docker/.env`（+ 可选 `.env.prod.secrets`）、`deploy/keys/`、`deploy/content/` 已就位。
2. 仓库 Secrets：`SSH_HOST` / `SSH_USER` / `SSH_KEY`；GHCR 包是 private 时再加 `GHCR_USER` + `GHCR_PAT`。
3. 仓库 Variables（可选）：`DEPLOY_ROOT`、`COMPOSE_PROFILE`（用自带 edge 就填 `edge`，自建反代留空）。

**给 CI 配一个专用 SSH 账号**（推荐，别直接用 root）：

```bash
# 服务器上：建账号 + 进 docker 组（注意：docker 组 ≈ root 等价权限）
sudo useradd -m -s /bin/bash deployer && sudo usermod -aG docker deployer

# 本地：生成专用密钥，只把公钥装上去
ssh-keygen -t ed25519 -C "gh-actions-nitrowater" -f ~/.ssh/nitrowater_deploy
ssh-copy-id -i ~/.ssh/nitrowater_deploy.pub deployer@<host>

# 验证：能不动 sudo 就列出容器
ssh -i ~/.ssh/nitrowater_deploy deployer@<host> 'docker ps >/dev/null && bash --version | head -1'
```

`SSH_KEY` 填**私钥全文**（`gh-actions-nitrowater`），`SSH_USER=deployer`。
宿主机还需要 `bash` + `curl`（`deploy.sh` 用它做探活）；`DEPLOY_ROOT` 目录要归 `deployer` 或可写。
可选：给 `appleboy/ssh-action` 加 `fingerprint`（`ssh-keyscan -t ed25519 <host>`）防中间人。

**发布与回滚**

`tag` 就是**镜像选择器**：`latest`、版本号（`v1.2.3`）、或某个提交的 sha。每次 build 都给同一批镜像
同时打 `<tag>` 与 `<sha>` 两个标签，所以：

```bash
gh workflow run deploy.yml -f tag=<上一个提交的 sha>   # 回滚，不重建
```

**回滚别用 `release`**：它会用这个 tag 重新构建一遍并覆盖同名镜像，镜像内容变成"现在"而不是"当时"。
`deploy` 是唯一会去动服务器的入口，也是唯一该用于回滚的入口（部署全局串行，`concurrency: deploy`）。

服务器侧行为：`docker cp` 只覆盖 `deploy/` 里的 kit 文件；`.env`、`keys/`、`content/` 是宿主机状态，不受影响。
**部署包跟 tag**（kit 与应用镜像同一个 tag/sha），所以回滚镜像是连同 compose 一起回退的；
反过来说，只改 compose / 脚本也必须 `build` 一次（重打 kit），不能只跑 `deploy`。

**部署包（`kit` 镜像）**

宿主机上没有任何 git 工作区。compose 文件、`deploy/bin/*` 脚本、`.env.example` 都由 `kit` 镜像带上来——
它只是借用 registry 当传输通道（同一个 tag、同一套凭据），宿主机用四行 docker 解包：

```bash
KIT=ghcr.io/<owner>/nitrowater/kit:<tag>
docker pull   "$KIT"
docker create --name nitrowater-kit "$KIT" unused     # unused = 占位命令，见下
docker cp     nitrowater-kit:/deploy/. /opt/nitrowater/deploy/   # 合并，不删 .env / keys / content
docker rm -f  nitrowater-kit
```

> **为什么要给个 `unused`**：镜像是 `FROM scratch`，没有 `CMD`/`ENTRYPOINT` 时 `docker create` 会直接报
> `no command specified`——不是等到 `run` 才校验。这个容器**从不启动**，命令只是占位符
> （`Dockerfile.kit` 里已写 `CMD ["unused"]`，用新版本镜像时这个参数可以省略）。
> 镜像里没有任何可执行文件，所以它永远不会变成运行时。

**发布产物规则（重要）**：`deploy/docker/*.yml` 和 `deploy/bin/*.sh` 是**发布产物**，不是"装一次的工具"。
改它们 = 发一版（`build` 重打 kit，1~2 秒），**不要在服务器上手改**——下次解包会被覆盖，
而且线上那份和仓库差在哪没人说得清。宿主机只保留状态：`.env`、`keys/`、`content/`、数据卷。

`docker cp` 是**合并**不是同步：仓库里删掉的旧文件不会自动从宿主机消失（极少见，手工删掉即可）。

**首次引导**（宿主机还没有 `deploy/`）：先解 kit（上面四行），然后：

```bash
cd /opt/nitrowater
cp deploy/docker/.env.example deploy/docker/.env && chmod 600 deploy/docker/.env
bash deploy/bin/gen-keys.sh && chmod 600 deploy/keys/private.key
# 按 §3 填 .env
docker network create nitrowater-net
```

之后每次上线 / 回滚，手动与 CI 是同一条命令：

```bash
bash deploy/bin/deploy.sh --tag <tag> --skip-kit     # 门禁 → pull → up -d --no-build → 探活
bash deploy/bin/deploy.sh --tag <tag> --with-kit     # 先把部署包切到该 tag，再上线
```

> `compose pull` 是**整份文件**拉取，`app.yml` 里还有 `chrislusf/seaweedfs`、`ghcr.io/engineer-man/piston`
> 等第三方镜像（未固定 tag = latest）。某个镜像源抽风就会让整个 `deploy` 中止（`set -e`）。
> 第三方镜像建议先在服务器上 `docker compose ... pull` 预拉一次、或给它们固定版本，
> 避免"业务镜像没问题却因为 nginx 拉不下来而上不了线"。
