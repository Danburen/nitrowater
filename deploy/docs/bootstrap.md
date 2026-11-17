# 首次部署

> 从空机器到能登录，一次性完成。命令都在部署根 `/opt/nitrowater` 下执行。
> 日常发版 / 回滚 / 备份见 [`../README.md`](../README.md)；变量以 `deploy/docker/.env.example` 为准。

## 1. 前置

| 项 | 要求 |
|---|---|
| Docker | Engine 24+ / Compose v2；宿主机有 `bash`、`openssl`、`curl` |
| 端口 | 只放行 80/443；3306/6379/6333/8080/8081/8090/8093/2000 不对公网开放 |
| DNS | 站点与 SSO 域名指向本机；用 `edge` 时 80 必须公网可达 |
| 资源 | ≥ 4 GB 内存，磁盘 ≥ 20 GB（`RESOURCE_DATA_DIR` 单独规划） |
| 镜像 | 4 个应用镜像已推到 GHCR；private 包先在宿主机登录 |

Docker 必须开机自启：

```bash
sudo systemctl enable --now docker
```

所有容器都是 `restart: unless-stopped`，服务器重启后会自动拉起。

## 2. 上传部署文件

在**本地仓库根**执行：

```bash
git archive --format=tar.gz HEAD deploy > /tmp/nitrowater-deploy.tgz
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/

# 服务器上
sudo mkdir -p /opt/nitrowater
sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
```

Windows PowerShell：

```powershell
git archive --format=tar.gz -o "$env:TEMP\nitrowater-deploy.tgz" HEAD deploy
scp "$env:TEMP\nitrowater-deploy.tgz" <host>:/tmp/
Remove-Item "$env:TEMP\nitrowater-deploy.tgz"
```

只传 tracked 文件；不要用 `tar -czf` 打工作区，否则会把 `.env`、`keys/*.key` 带上生产。

private GHCR 先登录一次：

```bash
printf '%s' '<PAT>' | docker login ghcr.io -u <github-user> --password-stdin
```

## 3. 密钥 + 环境变量

```bash
cp deploy/docker/.env.example deploy/docker/.env && chmod 600 deploy/docker/.env
bash deploy/bin/gen-keys.sh && chmod 600 deploy/keys/private.key

openssl rand -base64 32   # NITROWATER_KEK（丢失后加密列不可解）
openssl rand -base64 32   # DEVICE_SALT
openssl rand -base64 24   # MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD（三个不同）
openssl rand -hex 32      # BFF_CLIENT_SECRET
openssl rand -hex 12      # RESOURCE_INTERNAL_TOKEN
$EDITOR deploy/docker/.env
```

必须改对：

| 变量 | 要点 |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | 不能是 `root`；与 §5 建的账号一致 |
| `REDIS_PASSWORD` | 必填 |
| `JWT_ISSUER` | 必须是 URL，等于 account 对外地址 |
| `BFF_REDIRECT_URIS` 等 7 个地址 | 写浏览器看到的公网地址，不要 localhost / 容器名 |
| `RESOURCE_DATA_DIR` | 资源落盘目录，先 `mkdir -p` 并纳入备份 |
| `RESEND_API_KEY` / `MAIL_FROM` | `COMMUNICATION_MODE=real` 时必填 |

换域名时同步这 7 个地址：`JWT_ISSUER`、`BFF_REDIRECT_URIS`、`BFF_POST_LOGOUT_REDIRECT_URIS`、
`SSO_DEFAULT_REDIRECT`、`SSO_FORM_ACTION_ORIGINS`、`POST_LOGOUT_REDIRECT_URI`、`AVATAR_PICTURE_BASE_URL`（默认空）。
值后面不要写 `# 注释`；Windows 编辑后 `sed -i 's/\r$//'`；HTTPS 时 `SESSION_COOKIE_SECURE=true`；CORS 禁止 `*`。

## 4. 门禁

```bash
bash deploy/bin/check-env.sh        # 必须 0 FAIL
```

## 5. 网络 + 中间件 + 建库

```bash
docker network create nitrowater-net      # 只需一次
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml ps   # 等 mysql healthy
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

表结构由 Flyway 首次启动时创建。

## 6. 启动服务

```bash
sudo mkdir -p /opt/nitrowater/data/resource
sudo chown -R "$USER": /opt/nitrowater/data

docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml pull
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-build
curl -fsS http://127.0.0.1:8090/.well-known/openid-configuration | head -1

docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml pull
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d --no-build
```

顺序不能反（bff 启动要拉 account 的 discovery）；用 edge 时是 `account → edge → app`。

验收：`nitrowater-bff` healthy；`curl http://127.0.0.1:8080/api/public/ping` 返回 `pong`。

## 7. Piston 语言包 + 文档站

```bash
bash deploy/bin/install-piston-runtimes.sh       # 默认装 6 个包
```

已装的语言会 `skip`，装完自动重启 piston。以后加语言 / 升级见 [`../README.md`](../README.md#piston-加语言)。

文档站镜像在 `deploy/docker/.env` 配置：

```bash
DOCS_IMAGE_REPO=<你的 GitHub owner>/nitrowater-plugin-docs
DOCS_IMAGE_TAG=latest
```

验收：

```bash
curl -fsS http://127.0.0.1:8085/healthz                                      # ok
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8085/plugin/velochatx/   # 200
```

## 8. 公网入口

```bash
docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.infra.yml --profile edge up -d
docker logs -f nitrowater-acme            # 等证书签发完成
```

SSE 需要在 `/etc/nginx/vhost.d/<WEB_DOMAIN>`（卷 `nginx_vhost`）写入：

```nginx
proxy_buffering off;
proxy_read_timeout 300s;
proxy_send_timeout 300s;
```

```bash
docker exec nitrowater-nginx-proxy nginx -s reload
```

首次部署时 edge 必须排在 app 之前；证书没签好不要起 app（bff 会反复重启）。

## 9. 验收 + 首个管理员 + 基线备份

```bash
curl -fsS https://<WEB_DOMAIN>/api/public/ping                  # pong
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1           # 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  --data-binary @<(head -c 5m /dev/zero) https://<WEB_DOMAIN>/api/public/ping   # 401/400，不能 413
```

首个管理员（先注册，再授权，然后重新登录）：

```bash
docker exec -it nitrowater-mysql mysql -uroot -p nitrowater_account -e "
INSERT IGNORE INTO user_role (uid, role_id)
SELECT u.uid, r.id FROM \`user\` u JOIN \`role\` r ON r.code = 'SUPER_ADMIN'
WHERE u.username = '<用户名>';"
```

最后导一份空库基线：

```bash
bash deploy/bin/backup_mysql.sh
```

## 10. 检查单

- [ ] `deploy/` 已上传；`.env` 无占位值，`check-env` 0 FAIL
- [ ] mysql / redis healthy；三个库 + 业务账号存在
- [ ] account discovery 可达；bff healthy；`/api/public/ping` 返回 pong
- [ ] Piston 语言包已装；文档站 `/healthz` 与 `/plugin/<id>/` 可访问
- [ ] HTTPS 生效；`/actuator/health` 非 200；上传 5 MB 不是 413
- [ ] 首个 `SUPER_ADMIN` 已授权并重新登录
- [ ] MySQL 基线备份完成；KEK 已记录

## 11. 常见问题

| 现象 | 处理 |
|---|---|
| 解包后多一层 `deploy/deploy/…` | `tar -C` 指到了 `deploy/`，删掉重来，`-C /opt/nitrowater` |
| 服务器 `keys/` 出现 `private.key` | 用了工作区的 `tar` / `scp -r`，改用 `git archive` |
| `check-env` 报 CRLF / 缺容器侧变量 | 转换 compose 与 `.env` 为 LF 后重跑 |
| 启动报 `Unknown database` | Flyway 只建表不建库，先执行 §5 的 SQL |
| `manifest unknown` / `denied` | 镜像 tag / 仓库路径不对；private 包先 `docker login ghcr.io` |
| 登录后跳 `localhost:5173` / `invalid redirect_uri` | 7 个地址改成浏览器看到的公网地址 |
| 点「运行」才 500 | Piston 语言包没装，跑 `install-piston-runtimes.sh` |
| `/plugin/*` 404 | `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 不对，或镜像不是用 `/plugin` 前缀构建 |
