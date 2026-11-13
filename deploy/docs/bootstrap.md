# 首次部署

> 从空机器到能登录，**一次性、不可回滚**。按顺序做，每节末尾是验收点。
> 之后的上线 / 回滚 / 运维见 [`operations.md`](operations.md)。
> 变量以 `deploy/docker/.env.example` 为准（每项都有注释），本页只讲必填和最容易踩的。

下文的 `dc` 只在本页使用，先执行一次（bash）：

```bash
dc() { docker compose --env-file deploy/docker/.env -f "deploy/docker/docker-compose.$1.yml" "${@:2}"; }
```

## 1. 前置

| 项 | 要求 |
|---|---|
| Docker | Engine 24+ / Compose v2；宿主机有 `bash`、`openssl`、`curl`。不需要 git / JDK / Node |
| 端口 | 只放行 80/443；3306/6379/6333/8333/8080/8081/8090/8093/8888/9333/2000 **不要**对公网开 |
| DNS | 站点与 SSO 域名 A 记录指向本机（用 `edge` 时 80 必须公网可达） |
| 资源 | ≥ 4 GB 内存、≥ 20 GB 磁盘 |

## 2. 取部署文件

宿主机不需要 git。在**本地仓库根**执行（`deploy/` 里只有配置文件与脚本，状态文件不在 git 里）：

```bash
# 本地仓库根
tar -czf /tmp/nitrowater-deploy.tgz -C . deploy
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/

# 服务器上（$USER = 你的 SSH 账号；CI 也用这个账号写 deploy/）
sudo mkdir -p /opt/nitrowater
sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
```

也可以在服务器上 `git clone` 后把仓库的 `deploy/` 复制到 `/opt/nitrowater/`，效果一样；
之后每次迭代由 CI 同步，**不需要在服务器上保留工作区**。

- [ ] `/opt/nitrowater/deploy/docker/` 里有 `infra` / `account` / `app` 三份 compose 和 `.env.example`
- [ ] `/opt/nitrowater/deploy/docs/` 也在（排障时不用回仓库翻）

## 3. 密钥 + 环境变量

```bash
cp deploy/docker/.env.example deploy/docker/.env && chmod 600 deploy/docker/.env
bash deploy/bin/gen-keys.sh && chmod 600 deploy/keys/private.key

openssl rand -base64 32   # NITROWATER_KEK（丢了加密列永久不可解 → 离线备份）
openssl rand -base64 32   # DEVICE_SALT
openssl rand -base64 24   # MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD（三个不同）
openssl rand -hex 32      # BFF_CLIENT_SECRET
openssl rand -hex 12      # RESOURCE_INTERNAL_TOKEN
date +%s                  # APP_SESSION_EPOCH
$EDITOR deploy/docker/.env
```

必须改对：

| 变量 | 要点 |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | 不能是 `root`；与 §5 建的账号一致 |
| `REDIS_PASSWORD` | 必填（存 BFF 会话与令牌） |
| `JWT_ISSUER` | 必须是 URL，等于 account 对外地址 |
| `APP_SESSION_EPOCH` | 每次发布换新值 = 旧会话全部失效 |
| `BFF_REDIRECT_URIS` 等 7 个地址 | 一律写**浏览器看到的公网地址**，不要 localhost / 容器名 |
| `S3_*` / `AI_*` | 用 s3 时传 S3；AI key 可用 `DEEPSEEK_API_KEY` 等回落 |

换域名要同步：`JWT_ISSUER`、`BFF_REDIRECT_URIS`、`BFF_POST_LOGOUT_REDIRECT_URIS`、
`SSO_DEFAULT_REDIRECT`、`SSO_FORM_ACTION_ORIGINS`、`POST_LOGOUT_REDIRECT_URI`、`AVATAR_PICTURE_BASE_URL`。

容易踩的：值后面不要写 `# 注释`（会变成口令的一部分）；Windows 编辑后 `sed -i 's/\r$//'`；
HTTPS 时 `SESSION_COOKIE_SECURE=true`；`CORS_ALLOWED_ORIGIN_PATTERNS` 禁止 `*`。

## 4. 门禁

```bash
bash deploy/bin/check-env.sh        # 必须 0 FAIL
```

它按 compose 的 `environment:` 反推每个 `${VAR}`：缺值、回落成 `123456`/`root`/`change-me`、
仍是 `YOUR_*_HERE` 都算 FAIL；写了却没被 compose 引用的键报 WARN。

## 5. 网络 + 中间件 + 建库

```bash
docker network create nitrowater-net      # 只需一次
dc infra up -d
dc infra ps                               # 等 mysql healthy
docker exec -it nitrowater-mysql mysql -uroot -p   # 粘下面的 SQL
```

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

账号名与 `.env` 的 `DB_USERNAME` 一致，口令与 `DB_PASSWORD` 一致。重复执行无害；
数据卷是旧的、服务能连上就跳过本节。表结构由 Flyway 首次启动时建，不用管。

验收：`mysqladmin -uroot -p ping` → `mysqld is alive`；`SHOW DATABASES;` 能看到三个库。

> MySQL 不需要 keyring：敏感字段是应用层 AES-256-GCM 加密（`NITROWATER_KEK` 包 DEK），
> 落库本来就是密文。**KEK 与备份分开保管**，否则备份解不开。

## 6. 身份服务 → 业务面

```bash
dc account pull && dc account up -d --no-build
curl -fsS http://127.0.0.1:8090/.well-known/openid-configuration | head -1

dc app pull && dc app up -d --no-build
```

顺序不能反：bff 启动时要拉 account 的 OIDC discovery。`--no-build` 保证拉镜像而不是现场构建。

验收：`nitrowater-bff` healthy；`curl http://127.0.0.1:8080/api/public/ping` 返回 pong。

## 7. 收尾

```bash
# Piston 语言包（只装一次；漏装 = 用户点"运行"才 500）
docker exec nitrowater-piston cli/index.js ppman install java
docker exec nitrowater-piston cli/index.js ppman install c
docker exec nitrowater-piston cli/index.js ppman install c++

# 插件站点文档（约 1.8 MB，不在仓库里；不传则 /plugin/* 404）
scp -r deploy/plugin-docs/* <host>:/opt/nitrowater/deploy/plugin-docs/
```

## 8. 公网入口

**二选一**：`edge` profile（nginx-proxy + 自动证书，`.env` 填 `WEB_DOMAIN` / `AUTH_DOMAIN` / `SSL_EMAIL`），
或自建反代到 `127.0.0.1:8080`（站点）与 `127.0.0.1:8090`（SSO）。自建反代必须做对四件事：

```nginx
proxy_set_header X-Forwarded-Proto $scheme;   # 缺了回调会拼成 http:// → invalid redirect_uri
proxy_set_header X-Forwarded-Host  $http_host;
client_max_body_size 100m;                    # 插件 jar 64MB；默认 1m → 413
proxy_buffering off;                          # SSE：AI 回答逐字下发
proxy_read_timeout 300s; proxy_send_timeout 300s;
location ~ ^/(actuator|internal)(/|$) { return 404; }   # 管理面双保险
```

用 edge 时上传上限和 SSE 两条同样要加在 `nginx_vhost` 的 `default_location` 上。

验收：`curl -I http://<WEB_DOMAIN>` → 301/308；`/actuator/health` → 404 或 401（不能 200）。

## 9. 验收 + 首个管理员 + 基线备份

```bash
curl -fsS https://<WEB_DOMAIN>/api/public/ping                  # pong
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1           # 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  --data-binary @<(head -c 5m /dev/zero) https://<WEB_DOMAIN>/api/public/ping   # 401/400，不能 413
```

浏览器里再点一遍：登录 / 退出、管理端 › AI › 词表 reload（词数 > 0）、问一个文档问题（带 `[n]` 引用）、
插件下载（200 + attachment）、长回答不断流。

首个管理员（先注册，再授权，然后**重新登录**）：

```bash
docker exec -it nitrowater-mysql mysql -uroot -p nitrowater_account -e "
INSERT IGNORE INTO user_role (uid, role_id)
SELECT u.uid, r.id FROM \`user\` u JOIN \`role\` r ON r.code = 'SUPER_ADMIN'
WHERE u.username = '<用户名>';"
```

最后：`bash deploy/bin/backup_mysql.sh` 导一份空库基线，并把 `NITROWATER_KEK` 存进密码管理器。

## 10. 检查单

- [ ] `deploy/` 已上传；`.env` 无占位值，`check-env` 0 FAIL
- [ ] mysql/redis healthy；三个库 + 业务账号存在
- [ ] account discovery 可达；bff healthy；`/api/public/ping` pong
- [ ] Piston 语言包装好；`plugin-docs` 已上传
- [ ] https 生效；`/actuator/health` 非 200；上传 5 MB 不是 413
- [ ] 首个 `SUPER_ADMIN` 已授权并重新登录
- [ ] MySQL 基线备份完成；KEK 已离线保存
