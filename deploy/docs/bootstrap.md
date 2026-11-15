# 首次部署

> 从空机器到能登录，**一次性、不可回滚**。按顺序做，每节末尾是验收点。
> 之后的上线 / 回滚 / 运维见 [`operations.md`](operations.md)。
> 变量以 `deploy/docker/.env.example` 为准（每项都有注释），本页只讲必填和最容易踩的。
> 第一次跑报错先翻 **§11 常见坑**，按流水线顺序排的。

本页的 compose 命令一律写全 `docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.<组>.yml <子命令>`，
`<组>` 只取 `infra` / `account` / `app`；下文都给可直接复制的完整命令（在部署根 `/opt/nitrowater` 下执行）。

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
# 本地仓库根：git archive 只打 tracked 文件（.env / keys/ / plugin-docs 产物不会进包）
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

解包前自查（有输出就中止，说明包里混进了私钥或已填好的 env；PowerShell 把路径换成 `"$env:TEMP\nitrowater-deploy.tgz"`）：

```bash
tar -tzf /tmp/nitrowater-deploy.tgz | grep -E '\.key$|deploy/docker/\.env$|deploy/docker/\.env\.(prod|local|secrets|bak)'
```

不要用 `tar -czf /tmp/… -C . deploy`：`tar` 打包的是磁盘目录，会把**被 gitignore 忽略但本机存在**的文件一起带上——
`deploy/keys/private.key`、`deploy/docker/.env*`、`deploy/plugin-docs/*/` 一旦存在，解包就会覆盖服务器上的
生产签名密钥与配置（`keys/` 只应由服务器上的 `gen-keys.sh` 生成）。`git archive` 与 CI 的干净检出行为一致。

也可以在服务器上 `git clone` 后把仓库的 `deploy/` 复制到 `/opt/nitrowater/`，效果一样；
之后每次迭代由 CI 同步，**不需要在服务器上保留工作区**。

- [ ] `/opt/nitrowater/deploy/docker/` 里有 `infra` / `account` / `app` 三份 compose 和 `.env.example`
- [ ] `/opt/nitrowater/deploy/docs/` 也在（排障时不用回仓库翻）

GHCR 是 private 时，先在这台机器上登录一次（凭据会留在 `~/.docker/config.json`，之后 `compose pull` 直接可用）：

```bash
printf '%s' '<PAT>' | docker login ghcr.io -u <github-user> --password-stdin
```

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

它按 compose 的 `environment:` 反推每个 `${VAR}`：缺值、回落成 `123456`/`root`/`change-me`、
仍是 `YOUR_*_HERE` 都算 FAIL；写了却没被 compose 引用的键报 WARN。

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

账号名与 `.env` 的 `DB_USERNAME` 一致，口令与 `DB_PASSWORD` 一致。重复执行无害；
数据卷是旧的、服务能连上就跳过本节。表结构由 Flyway 首次启动时建，不用管。

验收：`mysqladmin -uroot -p ping` → `mysqld is alive`；`SHOW DATABASES;` 能看到三个库。

> MySQL 不需要 keyring：敏感字段是应用层 AES-256-GCM 加密（`NITROWATER_KEK` 包 DEK），
> 落库本来就是密文。**KEK 与备份分开保管**，否则备份解不开。

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

顺序不能反：bff 启动时要拉 account 的 OIDC discovery。`--no-build` 保证拉镜像而不是现场构建。
`pull` 的目标由 `.env` 的 `REGISTRY` / `GITHUB_REPOSITORY` / `ACCOUNT_IMAGE_TAG`·`APP_IMAGE_TAG` 拼出，
`manifest unknown` 基本就是这三处和实际推上去的不一致。

验收：`nitrowater-bff` healthy；`curl http://127.0.0.1:8080/api/public/ping` 返回 pong。

## 7. 收尾：Piston 语言包 + 插件文档

piston 镜像里**一个语言包都没有**（也没有 `cli/ppman`）。它启动时只注册 `/piston/packages/<包>/<版本>/`
里**带 `.ppman-installed` 标记**的目录，而官方 tarball 不带这个标记。仓库里的脚本把这几步都做了：

```bash
bash deploy/bin/install-piston-runtimes.sh
```

它按默认清单装 6 个包（`java` `gcc` `node` `python` `typescript` `lua`；`gcc` 提供 `c`/`c++`，
`node` 提供 `javascript`），版本取索引里最高版，包经 `PISTON_MIRROR`（默认 `https://gh-proxy.org/`）
下载——**服务器不需要直连 GitHub**。

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

以后加语言见 `operations.md` §8。

### 插件站点文档

```bash
# 约 1.8 MB，不在仓库里；不传则 /plugin/* 404
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

用 edge 时上传上限和 SSE 两条同样要加在 `nginx_vhost` 的 `default_location` 上
（上传上限已由 `deploy/docker/nginx-proxy-client-body.conf` 挂进 proxy 的 `conf.d`，SSE 仍需按 vhost 配）。

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
- [ ] Piston 6 个语言包已装（§7 的 `install-piston-runtimes.sh` 跑完，`/api/v2/runtimes` 能看到 `java` / `c` / `javascript` / `python` / `typescript` / `lua`）；`plugin-docs` 已上传
- [ ] https 生效；`/actuator/health` 非 200；上传 5 MB 不是 413
- [ ] 首个 `SUPER_ADMIN` 已授权并重新登录
- [ ] MySQL 基线备份完成；KEK 已离线保存

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
| 配置（§3） | `NITROWATER_KEK` 换掉或丢失 | 加密列**永久不可解**（备份也救不回来）；生成后立刻离线另存，且与数据库备份**分开**保管 |
| 建库（§5） | 应用启动报 `Unknown database` | Flyway 只建表不建库：先跑 §5 的三库 + 账号 SQL |
| 拉镜像（§6） | `manifest unknown` | 该 tag 没推 / 拼错 / 仓库路径不对；private 包先在宿主机 `docker login ghcr.io` |
| 发布（§6） | 第一次 `deploy.sh` 打印 `MySQL dump before replacing containers` | 正常：空库也会备份，`deploy/backups/` 多一个几 KB 的 `.sql.gz` |
| 入口（§8） | 登录后跳 `localhost:5173`，或 `invalid redirect_uri` | 7 个地址写浏览器看到的公网地址；反代必须发 `X-Forwarded-Proto` 与 `X-Forwarded-Host` |
| 入口（§8） | 上传 413，或 AI 回答整段刷出 | 反代 `client_max_body_size 100m`、`proxy_buffering off`、`proxy_read_timeout 300s` |
| 入口（§8） | `/actuator/health` 返回 200 | 管理面必须不可达：反代 `location ~ ^/(actuator\|internal)(/\|$){ return 404; }` |
| 运行期（§7） | 点「运行」才 500 | Piston 语言包没装：跑 §7 的 `install-piston-runtimes.sh`（**没有 `ppman`/`cli/`**，`docker exec … cli/index.js` 一定报 ENOENT） |
| 运行期（§7） | 装完 `/api/v2/runtimes` 里没有 | 少了 `.ppman-installed` 标记文件（piston 只注册带标记的目录）：重跑脚本会自动补；仍不行看 `docker logs nitrowater-piston` |
| 运行期（§7） | 装包时 `curl: (52) Empty reply` | 那是 `POST /api/v2/packages`（会去拉 GitHub 索引，拉不到就打挂 API）：`docker restart nitrowater-piston`，改用 §7 的脚本 |
| 运行期（§7） | 脚本报 `FAILED … (download)` | 代理不可用：换前缀重跑（`PISTON_MIRROR=https://ghproxy.net/ bash deploy/bin/install-piston-runtimes.sh`），已装的会 `skip` |
| 运行期（§7） | `/plugin/*` 404 | `plugin-docs` 被 gitignore，**不在**同步包里，必须手工上传 |
| 运行期（§4） | `check-env` 输出一堆 `[WARN]` | WARN 不阻断（写了但没被 compose 引用）；只清 `[FAIL]` |
