# 首次部署

> 从空机器到能登录，**一次性、不可回滚**。按顺序做，每节末尾是验收点。
> 日常发版 / 回滚 / 备份见 [`../README.md`](../README.md)；变量以 `deploy/docker/.env.example` 为准。
> 报错先翻 **§11 常见坑**。

下文命令都在部署根 `/opt/nitrowater` 下执行，`<组>` 取 `infra` / `account` / `app`，均写成可直接复制的完整命令。

## 1. 前置

| 项 | 要求 |
|---|---|
| Docker | Engine 24+ / Compose v2；宿主机有 `bash`、`openssl`、`curl`。不需要 git / JDK / Node |
| 端口 | 只放行 80/443；3306/6379/6333/8080/8081/8090/8093/2000 **不要**对公网开 |
| DNS | 站点与 SSO 域名 A 记录指向本机（用 `edge` 时 80 必须公网可达） |
| 资源 | ≥ 4 GB 内存；磁盘按资源量估（≥ 20 GB 起步，`RESOURCE_DATA_DIR` 单独规划） |
| 镜像 | 4 个应用镜像已推到 GHCR（先手动跑一次 `build.yml`）。private 包要先在宿主机登录 |

## 2. 取部署文件

宿主机不需要 git。在**本地仓库根**执行（`deploy/` 里只有配置文件与脚本，状态文件不在 git 里）：

```bash
# 本地仓库根：git archive 只打 tracked 文件（.env / keys/ / content/ 不会进包）
git archive --format=tar.gz HEAD deploy > /tmp/nitrowater-deploy.tgz
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/

# 服务器上（$USER = 你的 SSH 账号；CI 也用这个账号写 deploy/）
sudo mkdir -p /opt/nitrowater
sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
```

Windows PowerShell 用这版（`-o` 让 git 自己写文件；`>` 是 `Out-File`，会把 gzip 当文本写坏）：

```powershell
git archive --format=tar.gz -o "$env:TEMP\nitrowater-deploy.tgz" HEAD deploy
scp "$env:TEMP\nitrowater-deploy.tgz" <host>:/tmp/
Remove-Item "$env:TEMP\nitrowater-deploy.tgz"
```

解包前自查（有输出就中止）：

```bash
tar -tzf /tmp/nitrowater-deploy.tgz | grep -E '\.key$|deploy/docker/\.env$|deploy/docker/\.env\.(prod|local|secrets|bak)'
```

别用 `tar -czf … -C . deploy` 打包：它会把本机被 gitignore 的 `.env`、`keys/*.key` 一起带上，解包直接覆盖生产配置。

之后每次迭代由 CI 同步，**服务器上不需要保留工作区**。

- [ ] `/opt/nitrowater/deploy/docker/` 里有 `infra` / `account` / `app` 三份 compose 和 `.env.example`
- [ ] `/opt/nitrowater/deploy/docs/` 也在（排障时不用回仓库翻）

GHCR 是 private 时，先在这台机器上登录一次：

```bash
printf '%s' '<PAT>' | docker login ghcr.io -u <github-user> --password-stdin
```

## 3. 密钥 + 环境变量

```bash
cp deploy/docker/.env.example deploy/docker/.env && chmod 600 deploy/docker/.env
bash deploy/bin/gen-keys.sh && chmod 600 deploy/keys/private.key

openssl rand -base64 32   # NITROWATER_KEK（丢了加密列永久不可解）
openssl rand -base64 32   # DEVICE_SALT
openssl rand -base64 24   # MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD（三个不同）
openssl rand -hex 32      # BFF_CLIENT_SECRET
openssl rand -hex 12      # RESOURCE_INTERNAL_TOKEN
$EDITOR deploy/docker/.env
```

> 已有 `.env`（升级部署）时先 `bash deploy/bin/sync-env.sh --dry-run` 看差异（保留已有值），确认后去掉 `--dry-run` 再跑。

必须改对：

| 变量 | 要点 |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | 不能是 `root`；与 §5 建的账号一致 |
| `REDIS_PASSWORD` | 必填（存 BFF 会话与令牌） |
| `JWT_ISSUER` | 必须是 URL，等于 account 对外地址 |
| `BFF_REDIRECT_URIS` 等 7 个地址 | 一律写**浏览器看到的公网地址**，不要 localhost / 容器名 |
| `RESOURCE_DATA_DIR` | 资源字节落盘的宿主机目录（默认 `local` 后端）。`RESOURCE_BACKEND=local` 时必填，须先 `mkdir -p` 并纳入备份 |
| `AI_*` | AI key 可用 `DEEPSEEK_API_KEY` 等回落 |
| `AVATAR_PICTURE_BASE_URL` | **默认留空**：当前不开放头像修改，没有写入口，`picture` claim 不会下发。未来启用默认头像时填**站点 origin**（如 `https://<WEB_DOMAIN>`），account 会拼成 `<base>/api/public/resource/<uuid>`，走 BFF 匿名读，**不是**存储地址、也不是 :8093 |
| `RESEND_API_KEY` / `MAIL_FROM` | `COMMUNICATION_MODE=real` 时必填，缺失则 account **启动失败**（邮件无 SMTP 降级）；`MAIL_FROM` 必须是 Resend 已验证域下的地址 |

换域名要同步：`JWT_ISSUER`、`BFF_REDIRECT_URIS`、`BFF_POST_LOGOUT_REDIRECT_URIS`、
`SSO_DEFAULT_REDIRECT`、`SSO_FORM_ACTION_ORIGINS`、`POST_LOGOUT_REDIRECT_URI`（`AVATAR_PICTURE_BASE_URL`
默认空；启用头像后同样要同步）。

容易踩的：值后面不要写 `# 注释`（会变成口令的一部分）；Windows 编辑后 `sed -i 's/\r$//'`；
HTTPS 时 `SESSION_COOKIE_SECURE=true`；`CORS_ALLOWED_ORIGIN_PATTERNS` 禁止 `*`。

## 4. 门禁

```bash
bash deploy/bin/check-env.sh        # 必须 0 FAIL
```

缺值、dev 默认值、`YOUR_*_HERE` 占位都算 FAIL。

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

账号名 / 口令与 `.env` 的 `DB_USERNAME` / `DB_PASSWORD` 一致，重复执行无害；表结构由 Flyway 首次启动时建。

验收：`mysqladmin -uroot -p ping` → `mysqld is alive`；`SHOW DATABASES;` 能看到三个库。

> 敏感字段是应用层 AES-256-GCM 加密的，落库即密文；`NITROWATER_KEK` 丢了就永久解不开。

## 6. 身份服务 → 业务面

```bash
# 资源目录（app 里 resource 的 bind mount）：compose 缺它直接拒绝启动，check-env 还要求可写。
# 备份脚本按部署账号跑，所以属主给部署账号即可（容器以 root 写入）。
sudo mkdir -p /opt/nitrowater/data/resource
sudo chown -R "$USER": /opt/nitrowater/data

docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml pull
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-build
curl -fsS http://127.0.0.1:8090/.well-known/openid-configuration | head -1

docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml pull
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d --no-build
```

顺序不能反（bff 启动要拉 account 的 discovery）。`manifest unknown` = `.env` 的 `REGISTRY` / `GITHUB_REPOSITORY` / 镜像 tag 与实际推送不一致。

> 用 edge（§8）时真实顺序是 `account → edge → bff`：bff 启动要拉公网 `JWT_ISSUER` 的 discovery，
> 必须有已签发的证书。证书没签好就先别起 app（bff 会反复重启）。

验收：`nitrowater-bff` healthy；`curl http://127.0.0.1:8080/api/public/ping` 返回 pong。

## 7. 收尾：Piston 语言包 + 插件文档

piston 镜像里没有任何语言包。仓库脚本负责下载、补 `.ppman-installed` 标记并重启容器：

```bash
bash deploy/bin/install-piston-runtimes.sh
```

默认装 6 个包（`java` `gcc` `node` `python` `typescript` `lua`），经 `PISTON_MIRROR`（默认 `https://gh-proxy.org/`）下载。

- 包先落 `/var/tmp/piston-dl`，有进度条；**Ctrl-C 中止后重跑会断点续传**，已装的打印 `skip`。
- **已安装的会自动跳过**（`skip java (installed: 15.0.2)`），不会重复下载；装了一半的目录会 `warn … reinstalling` 就地重装。
- 要升到索引里的最新版用 `--upgrade`（旧版本目录保留在卷里，确认没问题再手动删）。
- 装完自动 `docker restart nitrowater-piston`，再打印 `/api/v2/runtimes`。
- 退出码：`0` 请求的包都在位并已注册；`1` 有包失败（其余照装）；`2` 容器/参数有问题。

常用变体：

```bash
bash deploy/bin/install-piston-runtimes.sh --dry-run                     # 只看会装什么（含已装的跳过）
bash deploy/bin/install-piston-runtimes.sh --list                        # 索引里有哪些包/版本
bash deploy/bin/install-piston-runtimes.sh --upgrade                     # 有新版才装
bash deploy/bin/install-piston-runtimes.sh rust:1.72.0                   # 只装某个语言的指定版本
PISTON_MIRROR=https://ghproxy.net/ bash deploy/bin/install-piston-runtimes.sh   # 换代理
```

### 验收

脚本末尾会打印 runtimes，应看到 **9 条**：`c` / `c++` / `d` / `fortran`（都来自 `gcc`）、`java`、`lua`、
`javascript`（来自 `node`）、`python`、`typescript`。手工再查一次：

```bash
docker exec nitrowater-piston node -e "require('http').get('http://127.0.0.1:2000/api/v2/runtimes',r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>console.log(d))})"
```

以后加语言 / 升级见 [`../README.md`](../README.md#piston-加语言)。

### 插件站点文档（镜像，不需要上传）

文档站（每个插件一个 VitePress 站点）在**独立仓库** `nitrowater-plugin-docs` 里构建；那边 CI 每次
push 到 `main` 就推镜像（`:latest` + `:<短 sha>`）。服务器这边**只拉不传**：`.env` 里给两行，
部署时它作为 app 单元的一个服务一起 `pull` + `up -d`。

```bash
# deploy/docker/.env
DOCS_IMAGE_REPO=<你的 GitHub owner>/nitrowater-plugin-docs
DOCS_IMAGE_TAG=latest        # 或某个短 sha（那个仓库每次 main push 都推 :latest 和 :<sha>）
```

首次部署前确认：

```bash
docker login ghcr.io                       # 私有包才需要（read:packages）；public 可跳过
curl -fsS http://127.0.0.1:8085/healthz    # ok
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8085/plugin/velochatx/   # 200
```

URL 只有 `/plugin/<id>/`。**没有门户页**：`/plugin/` 本身 404，入口由 SPA 的插件中心提供。镜像必须
用 `DOCS_BASE_PREFIX=/plugin` 构建（VitePress 的 `base` 是构建期固定的），否则站点会是 `/<id>/`、
经 BFF 全部 404。

> 以前 `deploy/plugin-docs/` 靠 `scp`/推目录同步，内容不在版本里；镜像化后回滚 = 换 `DOCS_IMAGE_TAG`。

本地开发不用镜像（两个仓库同级时开箱可用）：

```bash
# 在 nitrowater-plugin-docs 里
DOCS_BASE_PREFIX=/plugin npm run build      # 产物 dist/plugin/<id>/
```

`bin\start-infra.ps1` 把 `../nitrowater-plugin-docs/dist` 只读挂进 docs 容器
（`deploy/docker/docker-compose.docs-local.yml`），URL 与线上一致；仓库不在同级就用
`DOCS_SITE_DIR` 指过去。它找不到 `dist/plugin/` 时会打 WARN，提醒你先按上面那条命令构建。

## 8. 公网入口（edge profile + ACME）

`edge` profile 起 nginx-proxy + acme-companion：按容器的 `VIRTUAL_HOST` 自动生成 vhost，并用 HTTP-01
自动签发 / 续期证书。`.env` 只需 `WEB_DOMAIN` / `AUTH_DOMAIN` / `SSL_EMAIL`，且 **80 端口必须从公网可达**。

```bash
docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.infra.yml --profile edge up -d
docker logs -f nitrowater-acme            # 等 "Cert success"（首次签完约 1 分钟）
```

vhost 上还需要两件事：

- **上传上限**：已由 `deploy/docker/nginx-proxy-client-body.conf` 挂进 proxy 的 `conf.d`（100m），不用管。
- **SSE**：AI 回答要逐字下发，必须关掉缓冲与短超时 —— 写 `/etc/nginx/vhost.d/<WEB_DOMAIN>`（卷 `nginx_vhost`）：

```nginx
proxy_buffering off;
proxy_read_timeout 300s;
proxy_send_timeout 300s;
```

```bash
docker exec nitrowater-nginx-proxy nginx -s reload
```

> 没签下证书时不会生成 HTTPS 块，443 落到自签证书（浏览器报不安全）。看 `docker logs nitrowater-acme`，别改 vhost。

**首次部署时这一步要排在 §6 的 `app` 之前**：`VIRTUAL_HOST` / `LETSENCRYPT_HOST` 在容器创建时读取。
标签改动后要重建容器才生效：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-build --force-recreate
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d --no-build --force-recreate bff
```

验收：`curl -I http://<WEB_DOMAIN>` → 301/308；`/actuator/health` → 404 或 401（不能 200，bff 自己就会拒）。

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

最后：`bash deploy/bin/backup_mysql.sh` 导一份空库基线。

## 10. 检查单

- [ ] `deploy/` 已上传；`.env` 无占位值，`check-env` 0 FAIL
- [ ] mysql/redis healthy；三个库 + 业务账号存在
- [ ] account discovery 可达；**公网 `JWT_ISSUER` 的 discovery 也通**（edge 已起、证书已签发）后再起 app；bff healthy；`/api/public/ping` pong
- [ ] Piston 6 个语言包已装（§7 的 `install-piston-runtimes.sh` 跑完，`/api/v2/runtimes` 能看到 `java` / `c` / `javascript` / `python` / `typescript` / `lua`）；`.env` 的 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 已填，`/plugin/<id>/` 能打开、`/healthz` 返回 `ok`
- [ ] https 生效；`/actuator/health` 非 200；上传 5 MB 不是 413
- [ ] 首个 `SUPER_ADMIN` 已授权并重新登录
- [ ] MySQL 基线备份完成；KEK 已记录

## 11. 常见坑

按流水线顺序排；括号里是上文对应的小节。示例值一律用 `<host>` / `<user>` / `<org>` 这类占位符。

| 阶段 | 现象 | 原因 / 处理 |
|---|---|---|
| 传文件（§2） | 在服务器上 `ssh <host> …`、`scp …` 报 `Could not resolve hostname` | 这类命令只在**本地**执行；服务器上直接跑 `tar`、`docker`、`bash deploy/bin/…` |
| 传文件（§2） | `ls -1 deploy/docker` 看不到 `.env.example` | 点开头是隐藏文件，用 `ls -la` |
| 传文件（§2） | 解包后多一层 `deploy/deploy/…` | `-C` 指到了 `deploy/` 而不是部署根；删掉重来，`-C /opt/nitrowater` |
| 传文件（§2） | 服务器 `keys/` 里出现了 `private.key` | 用了工作区的 `tar` 或 `scp -r`：开发密钥不能上生产。改用 `git archive`；实在要用 `tar` 就加 `--exclude 'deploy/keys/*.key' --exclude 'deploy/docker/.env' --exclude 'deploy/docker/.env.prod*'` |
| 权限（§1） | CI 同步时 `tar: Cannot open: Permission denied` | `deploy/` 属主必须是执行部署的账号：`sudo chown -R <user>: /opt/nitrowater` |
| 权限（§1） | `docker ps` 报 `permission denied` | `docker` 组没生效：`sudo usermod -aG docker <user>` 后**退出重登** |
| 配置（§3） | 口令/地址里混进了奇怪后缀 | `KEY=value  # 注释` 会把 `#` 之后算进值里；注释单独成行 |
| 配置（§3） | Windows 编辑后变量带不可见字符 | 行尾是 `\r`：`sed -i 's/\r$//' deploy/docker/.env deploy/docker/docker-compose.*.yml`（`check-env` 的 `crlf` 项也会报） |
| 配置（§3） | `check-env` 的 `compose-coverage` 缺的是 `SPRING_DATASOURCE_USERNAME` 这类**容器侧**名字 | 同上：compose 文件是 CRLF，脚本没解析到 `${DB_USERNAME:?…}`。转成 LF 后重跑，缺的会变回 `DB_USERNAME` |
| 配置（§3） | `check-env` 判 `GITHUB_REPOSITORY` FAIL | 必须**全小写**的 `<org>/nitrowater`，且与 GHCR 里的实际路径一致 |
| 配置（§3） | `DB_USERNAME` 被判 FAIL | 不能用 `root`；必须与 §5 建库 SQL 里的账号同名 |
| 配置（§3） | `NITROWATER_KEK` 换掉或丢失 | 加密列**永久不可解**（备份也救不回来） |
| 建库（§5） | 应用启动报 `Unknown database` | Flyway 只建表不建库：先跑 §5 的三库 + 账号 SQL |
| 拉镜像（§6） | `manifest unknown` | 该 tag 没推 / 拼错 / 仓库路径不对；private 包先在宿主机 `docker login ghcr.io` |
| 发布（§6） | 第一次部署打印 `backup: MySQL dump before replacing containers` | 正常：空库也会备份，`deploy/backups/` 多一个几 KB 的 `.sql.gz` |
| 入口（§8） | 登录后跳 `localhost:5173`，或 `invalid redirect_uri` | 7 个地址写浏览器看到的公网地址（`WEB_DOMAIN` / `AUTH_DOMAIN` 必须与它们一致） |
| 入口（§8） | 上传 413，或 AI 回答整段刷出 | vhost 没吃到 100m 上限 / 没关缓冲：见 §8 的两条 vhost 配置 |
| 入口（§8） | `/actuator/health` 返回 200 | 不应该：bff 对 `/actuator/**`、`/internal/**` 是 denyAll，先查是不是连到了别的服务 |
| 运行期（§7） | 点「运行」才 500 | Piston 语言包没装：跑 §7 的 `install-piston-runtimes.sh`（**没有 `ppman`/`cli/`**，`docker exec … cli/index.js` 一定报 ENOENT） |
| 运行期（§7） | 装完 `/api/v2/runtimes` 里没有 | 少了 `.ppman-installed` 标记文件（piston 只注册带标记的目录）：重跑脚本会自动补；仍不行看 `docker logs nitrowater-piston` |
| 运行期（§7） | 装包时 `curl: (52) Empty reply` | 那是 `POST /api/v2/packages`（会去拉 GitHub 索引，拉不到就打挂 API）：`docker restart nitrowater-piston`，改用 §7 的脚本 |
| 运行期（§7） | 脚本报 `FAILED … (download)` | 代理不可用：换前缀重跑（`PISTON_MIRROR=https://ghproxy.net/ bash deploy/bin/install-piston-runtimes.sh`），已装的会 `skip` |
| 运行期（§7） | `/plugin/*` 404 或 `/plugin/` 是 nginx 默认页 | docs 镜像没换成功：`.env` 的 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 不对，或 `docker compose … pull` 拉到的还是旧 tag；`docker inspect nitrowater-docs --format '{{.Config.Image}}'` 看实际镜像 |
| 运行期（§7） | 部署在 `pull` 阶段就失败（`manifest unknown` / `denied`） | 文档站镜像在**独立仓库**：`DOCS_IMAGE_REPO` 要写 `<owner>/nitrowater-plugin-docs`（不是 `nitrowater/docs`）；private 包先 `docker login ghcr.io` |
| 运行期（§7） | 本地改了文档但容器没变 | dev 用的是**挂载**不是镜像：在文档仓库 `DOCS_BASE_PREFIX=/plugin npm run build` 后刷新；换了 `docs-nginx.conf` 要 `docker rm -f nitrowater-docs` 重建 |
| 运行期（§7） | `/plugin/<id>/` 打开后资源全 404 | 文档仓库是用默认前缀构建的（`/<id>/`）：VitePress 的 `base` 是构建期固定的，必须 `DOCS_BASE_PREFIX=/plugin npm run build`，镜像同理（CI 要带 `--build-arg`，或 Dockerfile 默认就是它） |
| 运行期（§4） | `check-env` 输出一堆 `[WARN]` | WARN 不阻断（写了但没被 compose 引用）；只清 `[FAIL]` |
