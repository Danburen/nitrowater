# 上线清单（Production Go-Live Checklist）

> 面向"手动上生产"：一台 Linux 主机 + Docker。所有命令都在**仓库根目录**执行。
> 配套文档：`deploy/env-vars-reference.md`（变量含义）、`deploy/bin/README.md`（脚本）、
> `deploy/content/README.md`（内容挂载）。
>
> 判定标准：`deploy/bin/check-env.sh` 输出 **0 FAIL** 即可上线。

---

## 0. 机器与网络（前置）

| 项 | 要求 | 验证 |
|---|---|---|
| Docker | Docker Engine 24+ / Compose v2 | `docker compose version` |
| 目录 | 仓库放在持久盘（`/opt/nitrowater` 等），**不要**放临时目录 | `pwd` |
| 端口 | 仅放行 **80/443**；其余（8080/8081/8090/8093/3306/6379/6333/8333/8888/9333/2000）**不要**对公网开放 | 云安全组 |
| DNS | `<WEB_DOMAIN>` 与 `<AUTH_DOMAIN>` 的 A 记录指向本机 | `dig +short <domain>` |
| 内存 | 建议 ≥ 4 GB（mysql 512M + redis 256M + qdrant 384M + seaweedfs 512M + 三个 JVM） | `free -h` |
| 磁盘 | ≥ 20 GB（镜像 + 卷） | `df -h` |

> 证书用 HTTP-01，**80 端口必须公网可达**，否则 acme 签发失败。

---

## 1. 代码与密钥（在部署机上生成，不要从别处拷）

```bash
git clone <repo> /opt/nitrowater && cd /opt/nitrowater

# JWT RS256 密钥对（RS 验签用），生成到 deploy/keys/
bash deploy/bin/gen-keys.sh
chmod 600 deploy/keys/private.key
ls -l deploy/keys          # 期望 private.key / public.key

# 其它随机值（直接贴进 .env）
openssl rand -base64 32                  # NITROWATER_KEK（必须 32 字节 → 44 字符 base64）
openssl rand -base64 32                  # DEVICE_SALT
openssl rand -base64 24 | tr -d '/+='     # DB_PASSWORD
openssl rand -hex 32                     # BFF_CLIENT_SECRET
openssl rand -hex 24                     # S3_SECRET_KEY
```

**纪律**：
- `NITROWATER_KEK` 用于加密敏感列，**丢失 = 数据永久不可解**；生成后离线备份一份。
- `DEVICE_SALT` 决定设备指纹；上线后更换会让所有已绑定设备失效。
- `private.key` 轮换 = 所有 JWT 立即失效（全员重新登录），可接受但要选低峰。

---

## 2. 写 `.env`

```bash
cp deploy/docker/.env.example deploy/docker/.env
chmod 600 deploy/docker/.env
```

### 2.1 必填（缺失即 `check-env` FAIL）

| 变量 | 取值 | 说明 |
|---|---|---|
| `NITROWATER_KEK` | `openssl rand -base64 32` | 32 字节 AES KEK |
| `DEVICE_SALT` | 新随机值 | **不能**留 dev 默认 `8c4uWcFW...` |
| `JWT_ISSUER` | `https://<AUTH_DOMAIN>` | 必须是 URL，且与 account 一致 |
| `DB_PASSWORD` | 随机 | 不能用 `123456` |
| `BFF_CLIENT_SECRET` | `openssl rand -hex 32` | 不能用 `nitrowater-bff-secret` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | 自定义 | `RESOURCE_BACKEND=s3` 时必填 |
| `AI_CHAT_API_KEY` | DeepSeek key | 或 `DEEPSEEK_API_KEY` |
| `AI_EMBEDDING_API_KEY` | SiliconFlow key | 或 `SILICONFLOW_API_KEY` |
| `deploy/keys/{private,public}.key` | 第 1 步产物 | 文件存在即可 |

### 2.2 边缘（叠加 `-Edge` 时必填）

| 变量 | 示例 | 说明 |
|---|---|---|
| `WEB_DOMAIN` | `nitrowater.cn`（可逗号列表） | 业务域名，`www` 要写进列表才会一起签证书 |
| `AUTH_DOMAIN` | `auth.nitrowater.cn` | SSO 域名，必须与 `JWT_ISSUER` 一致 |
| `SSL_EMAIL` | `admin@nitrowater.cn` | Let's Encrypt 联系邮箱 |

### 2.3 SSO 回跳（强烈建议，否则登录后跳 localhost）

| 变量 | 值 |
|---|---|
| `SSO_DEFAULT_REDIRECT` | `https://<WEB_DOMAIN>` |
| `BFF_REDIRECT_URIS` | `https://<WEB_DOMAIN>/login/oauth2/code/nitrowater` |
| `BFF_POST_LOGOUT_REDIRECT_URIS` | `https://<WEB_DOMAIN>,https://<WEB_DOMAIN>/` |

### 2.4 生产姿态（保持这些值）

| 变量 | 值 | 原因 |
|---|---|---|
| `PUBLISH_BIND` | `127.0.0.1` | 业务端口只给本机/隧道 |
| `OPS_BIND` | `127.0.0.1` | Qdrant / SeaweedFS UI 无认证 |
| `RESOURCE_USER_UPLOAD` | `false` | 图片等需审核的内容暂不开放（接口保留，返回 503） |
| `SESSION_COOKIE_SECURE` | `true` | edge 叠加层会自动注入；手工部署需自设 |
| `COMMUNICATION_MODE` | `real`（推荐） | `mock` 时验证码只写日志 |
| `RESOURCE_INTERNAL_TOKEN` | 随机串 | `bin/dev/sync-res.ps1` 发布插件 Jar 用；不设则该入口关闭 |

`RESOURCE_BACKEND=cos` 时另填 `COS_SECRET_ID/COS_SECRET_KEY/COS_REGION/COS_BUCKET`；
`COMMUNICATION_MODE=real` 时另填 SMTP 与阿里云短信一组变量。

### 2.5 可选

| 变量 | 用途 |
|---|---|
| `AI_DOCS_LOCATION` | 指向 `/etc/nitrowater/docs/nitrowater` 时改用外置语料，**目录必须含 `.md`** |
| `AI_SKILLS_DIR` | 外部技能目录（edge/content 叠加层已设为 `/etc/nitrowater/skills`） |
| `AI_MODERATION_DICT` / `AI_MODERATION_ALLOW` | 保持双层：`classpath:...,file:...`（叠加层已写好，不要改成单层） |
| `AI_COLLECTION` | 换 embedding 模型/维度时**换集合名**，不要原地重建 |
| `CONTENT_ROOT` | 内容目录（默认 `deploy/content`） |

---

## 3. 内容目录

```bash
bash deploy/bin/check-env.sh        # 先跑一次，它会指出缺什么
```

按需准备（都被 `.gitignore` 排除，只跟踪 `README.md` / `.gitkeep`）：

| 路径 | 内容 | 何时需要 |
|---|---|---|
| `deploy/content/moderation/words.txt` | 生产违禁词，**去注释后 ≥1 条** | 想用 ENFORCE / 有增量词 |
| `deploy/content/moderation/allow.txt` | 白名单（可选） | 需要放行误伤词 |
| `deploy/content/docs/nitrowater/*.md` | 外置语料 | 设了 `AI_DOCS_LOCATION` 时 |
| `deploy/content/skills/<name>/SKILL.md` | 外部技能 | 需要 |
| `deploy/plugin-docs/<plugin>/docs/` | 站点文档产物 | 插件文档站 |

---

## 4. 预检

```bash
bash deploy/bin/check-env.sh          # 生产模式
bash deploy/bin/check-env.sh --local  # 额外检查本机端口（在服务器上一般不需要）
```

必须 **0 FAIL**。WARN 逐条确认（常见：`.env` 未创建、`/admin/**` 未加白名单、上传开关未关）。

---

## 5. 起中间件 + Piston 语言包

```bash
bash deploy/bin/deploy.sh --infra-only

# 代码运行器需要语言包（一次即可；容器名固定）
docker exec nitrowater-piston cli/index.js ppman install java
docker exec nitrowater-piston cli/index.js ppman install c
docker exec nitrowater-piston cli/index.js ppman install c++
docker exec nitrowater-piston cli/index.js ppman list
```

---

## 6. 起应用 + 边缘

```bash
# 首次建议本地构建镜像；CI 出镜像时去掉 --build
bash deploy/bin/deploy.sh --edge --content --build

docker compose -f deploy/docker/docker-compose.app.yml -f deploy/docker/docker-compose.edge.yml \
  --env-file deploy/docker/.env ps
```

期望：`nginx-proxy` 监听 80/443；`bff/account/server/resource/docs` 只与容器网络相连。

---

## 7. 首个管理员（必做，否则进不了管理端）

角色由 Flyway 种入（`USER/ADMIN/SUPER_ADMIN`），但**没有任何用户绑定**。先注册一个账号，再授予：

```bash
docker exec -it nitrowater-mysql mysql -uroot -p"$DB_PASSWORD" nitrowater_account -e "
INSERT IGNORE INTO user_role (uid, role_id)
SELECT u.uid, r.id FROM \`user\` u JOIN \`role\` r ON r.code = 'SUPER_ADMIN'
WHERE u.username = '<你的用户名>';"
```

然后**重新登录**（角色在登录时载入）。验证：能打开 `https://<WEB_DOMAIN>/admin/ai/settings`。

---

## 8. 冒烟验收（逐条打勾）

| # | 操作 | 期望 |
|---|---|---|
| 1 | `curl -I http://<WEB_DOMAIN>` | 301/308 → https |
| 2 | 浏览器打开站点 | 证书有效、无混合内容告警 |
| 3 | 登录 / 退出 | 跳 `auth.` 域并正确回到门户 |
| 4 | `curl https://<WEB_DOMAIN>/api/ai/status` | `indexed=true`、`chunks>0`（首轮索引可能需要几分钟） |
| 5 | 管理端 › AI › 词表 `reload` | 返回加载词数 > 0 |
| 6 | 问一个文档里有的问题 | 回答带 `[n]` 引用且链接可达 |
| 7 | 随便点一个插件下载 | 200 + `Content-Disposition: attachment` |
| 8 | `POST /api/resource/upload/policy`（带登录） | `503 uploads_disabled` |
| 9 | 外网 `curl http://<host>:6333/collections` | 连接失败（端口未暴露） |
| 10 | `bash deploy/bin/ops-tunnel.sh --host <host>` | 本机 `127.0.0.1:6333/dashboard` 可用 |
| 11 | `docker logs nitrowater-acme` | 无错误；证书已签发 |

---

## 9. 上线后

**灰度**：`ai_admin_setting.moderation_mode` 默认 `LOG(1)`。观察 `ai_moderation_hit` 7 天 → 调词表 → 管理端切 `ENFORCE(2)`（热生效）。

**备份**（建议每天）：

```bash
docker run --rm -v nitrowater-mysql:/var/lib/mysql -v "$PWD":/backup alpine \
  tar czf /backup/mysql-$(date +%F).tgz -C /var/lib/mysql .
docker run --rm -v nitrowater-qdrant:/qdrant/storage -v "$PWD":/backup alpine \
  tar czf /backup/qdrant-$(date +%F).tgz -C /qdrant/storage .
```

**三类内容更新（都不需要重跑 jar/镜像）**

| 内容 | 放哪 | 更新动作 |
|---|---|---|
| AI 词表/语料/技能 | `deploy/content/**` | 覆盖文件 → `POST /api/admin/ai/moderation/reload` / `reindex` |
| 插件站点文档 | `deploy/plugin-docs/<plugin>/docs/` | 覆盖产物（必要时 `docker restart nitrowater-docs`） |
| 插件 Jar | resource + SeaweedFS | `RESOURCE_INTERNAL_TOKEN=...` 后 `pwsh bin/dev/sync-res.ps1` 或 CI 调 `/internal/resource/publish` |

**运维 UI**：不暴露，用隧道 `bash deploy/bin/ops-tunnel.sh --host <host>`。

---

## 10. 回滚

| 场景 | 动作 |
|---|---|
| 应用异常 | 换回上一镜像 tag 后 `up -d`；或去掉 `-f docker-compose.edge.yml` 回到"仅回环" |
| 拦截误伤 | 管理端把模式切回 `LOG`；或 `POST /api/admin/ai/moderation/reload` 后修词表 |
| 索引坏了 | `POST /api/admin/ai/reindex`；换 embedding 维度则改 `AI_COLLECTION` 再重建 |
| 证书问题 | 保留 `nginx_certs` 卷；修 DNS/端口后重启 `acme-companion` |

---

## 11. 常见故障对照

| 症状 | 原因 | 处理 |
|---|---|---|
| 登录后跳 `localhost:5173` | `SSO_DEFAULT_REDIRECT` / `BFF_REDIRECT_URIS` 未改 | 改 `.env` 重启 bff/account |
| 登录报 `invalid redirect_uri` | `BFF_REDIRECT_URIS` 与浏览器实际回调不一致 | 同上，注意 https 与结尾斜杠 |
| 证书一直不签发 | 80 端口不通 / DNS 未生效 | 检查安全组与 `dig`；看 `docker logs nitrowater-acme` |
| 进入管理端 403 | 未绑定角色，或绑定后未重新登录 | 执行第 7 步 SQL，重新登录 |
| AI 回答无引用 | 索引为空 / embedding key 错 | 看 `/api/ai/status` 的 `error`，修 key 后 `reindex` |
| 上传 503 | `RESOURCE_USER_UPLOAD=false`（预期） | 需要开放时改 true 并重启 resource |
| 大 Jar 发布 413 | `RESOURCE_MAX_SIZE` 太小 | `.jar` 已用 `RESOURCE_PLUGIN_MAX_SIZE`；非 jar 才受 10MB 限制 |
| 图片裂图（将来开放上传时） | S3 只在回环，presigned 直连失败 | 设 `RESOURCE_PRESIGN=false`（同源流式）或给 S3 开边缘 vhost |

---

## 12. 全站 AI 额度（上线后第一周再做）

1. 管理端 › AI › 运行设置：填「全站 token 额度（每日）」，**先不要打开熔断开关**（只统计不拦截）。
2. 观察 3–7 天：管理端 › AI › 用量 顶部的「全站用量」卡片（今日用量 / 上限 / 请求数 + 近 31 天曲线）。
3. 按实测峰值 × 1.5 设定上限，再打开「全站额度熔断」。
4. 误判时点「解除今日熔断」：清零全站当日计数、立即恢复提问（不影响个人额度）。
5. 熔断后：提问入口关闭，**历史会话与 `/` 指令仍可用**，北京时间 08:00 自动恢复。
6. 保留策略：个人日明细 31 天（`AI_USAGE_RETENTION_DAYS`），全站日行 366 天（`AI_USAGE_GLOBAL_RETENTION_DAYS`）。
