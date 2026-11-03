# nitrowater 环境变量参考

> Phase 1：`nitrowater-account` 使用**扁平 `application.yml`**，不引入 shared config 导入。
> 本文件说明本地/生产运行需要关注的环境变量与配置项。

## 1. 本地开发必需

| # | 项 | 位置 | 默认/示例 |
|---|----|------|-----------|
| 1 | JWT 密钥 | `deploy/keys/{private,public}.key` | 复制 waterfun 的，或 `gen-keys.ps1` 生成 |
| 2 | `NITROWATER_KEK` | 环境变量 / `build.gradle` bootRun 默认 | 32 字节 Base64（见 `deploy/config/common-dev-secrets.yml`） |
| 3 | `device.salt` | `application.yml`（`${DEVICE_SALT:...}`） | dev 默认值；**生产必须用 `DEVICE_SALT` 覆盖，禁止复用 dev 盐值**（见第 2 节） |
| 4 | MySQL | `application.yml` datasource（`${DB_PASSWORD:123456}`） | SSO 库 `localhost:3306/nitrowater_account`（自动建库）；业务库 `nitrowater_biz`（server）；资源库 `nitrowater_resource`（resource） |
| 5 | Redis | `application.yml` data.redis（`${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}` / `${REDIS_PASSWORD:}`） | `localhost:6379` |
| 6 | `JWT_ISSUER` | 环境变量（SSO/OIDC 唯一签发者，Phase1 AT + AS 共用） | dev 默认 `http://localhost:8090`；生产 `https://auth.nitrowater.cn`。必须为 URL，且与前端 `VITE_OIDC_AUTHORITY` 一致 |

## 2. 生产必换清单（上线前逐项核对）

> 下列项在 `application.yml` / `docker-compose.*.yml` 中均带 **dev 弱默认**（`123456` / `change-me` /
> `nitrowater-bff-secret` 等），生产必须显式覆盖。依据：**OS 环境变量优先级高于 `application.yml`**，
> 因此只需在运行环境设置这些变量即可生效（无需改配置）。

| # | 变量 | 影响 | dev 默认（**禁止用于生产**） |
|---|------|------|------------------------------|
| 1 | `DB_PASSWORD` | account / server / resource 三库 datasource 口令 | `123456` |
| 2 | `DEVICE_SALT` | account 设备指纹盐（`device.salt`） | `8c4uWcFWgRFFJHoE/...`（**dev 盐值禁止上生产**；一旦复用即等于公开盐值） |
| 3 | `REDIS_PASSWORD` | 各服务 Redis 口令 | 空（无密码） |
| 4 | `BFF_CLIENT_SECRET` | BFF 的 OIDC 客户端密钥 | `nitrowater-bff-secret` |
| 5 | `S3_ACCESS_KEY` / `S3_SECRET_KEY` | S3/SeaweedFS 对象存储凭据 | `nitrowater` / `change-me` |
| 6 | `JWT_ISSUER` | SSO/OIDC 签发者（须为 URL） | `http://localhost:8090` |
| 7 | JWT 私钥 | `deploy/keys/*.key`（已 gitignore，不入库） | `gen-keys.ps1` 生成；生产改由 `JWT_PRIVATE_KEY` 指向挂载路径 |
| 8 | `NITROWATER_KEK` | account 密钥加密 KEK（32 字节 Base64） | 见 `deploy/config/common-dev-secrets.yml` |

## 3. 配置入口

| 用途 | 文件 |
|------|------|
| account 全部配置（扁平） | `nitrowater-account/src/main/resources/application.yml` |
| account 外部密钥层（`spring.config.import: optional:file:./deploy/config/common-dev-secrets.yml`，其 `KEY: value` 即 Spring 属性） | `deploy/config/common-dev-secrets.yml`（gitignored；依赖 workingDir=仓库根） |
| 前端 OIDC 配置（Vite env） | `nitrowater-web/.env.local`（模板 `.env.example`） |
| JWT 密钥对 | `deploy/keys/` |
| 启动脚本（本地 dev） | `bin/`（`start-*.bat`）；冒烟 `bin/test/` |
| 运维/部署脚本 | `deploy/bin/`（gen-keys / check-env / backup_mysql / reset-sso-db / deploy） |

## 4. 端口

| 服务 | 端口 |
|------|------|
| nitrowater-account (SSO) | 8090 |
| nitrowater-web (Vite dev) | 5173 |
| MySQL | 3306 |
| Redis | 6379 |
| Qdrant (gRPC / REST) | 6334 / 6333 |

## 5. 文档助手（Spring AI RAG）

| 变量 | 作用 | 默认/说明 |
|------|------|-----------|
| `AI_ENABLED` | 总开关；false 时所有 AI 接口返回 503 | `true` |
| `AI_CHAT_API_KEY` | chat 模型 key；未设置时回退 `DEEPSEEK_API_KEY` | — |
| `AI_CHAT_BASE_URL` / `AI_CHAT_MODEL` | OpenAI 兼容 chat 端点 / 模型 | DeepSeek `https://api.deepseek.com` / `deepseek-chat` |
| `AI_EMBEDDING_API_KEY` | embedding key；未设置时回退 `SILICONFLOW_API_KEY` | — |
| `SILICONFLOW_API_KEY` | SiliconFlow key（embedding 默认使用） | — |
| `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_MODEL` | OpenAI 兼容 embedding 端点 / 模型 | SiliconFlow `https://api.siliconflow.cn/v1` / `Qwen/Qwen3-Embedding-0.6B` |
| `AI_EMBEDDING_DIMENSIONS` | 向量维度；Qwen3-Embedding-0.6B 为 1024；换模型/维度需换集合名或重建集合 | `1024` |
| `QDRANT_HOST` / `QDRANT_PORT` | Qdrant gRPC 地址 | `localhost` / `6334` |
| `AI_COLLECTION` | Qdrant 集合名 | `nitrowater_velochatx_docs` |
| `AI_INGEST_ON_STARTUP` | 启动时若集合为空则自动索引 | `true` |
| `AI_PER_USER_PER_MINUTE` | 每用户每分钟提问配额（Redis） | `20` |
| `AI_SKILLS_DIR` | 外部技能目录（`<name>/SKILL.md`，覆盖同名内置） | `deploy/ai-skills` |
| `AI_MEMORY_RETENTION_DAYS` | 长期记忆未使用/未更新的保留天数；超过后自动置为 `EXPIRED` | `180` |
| `AI_MEMORY_PURGE_DAYS` | `USER_DELETED` / `EXPIRED` 记忆的审计保留天数；超过后物理删除 | `30` |
| `AI_MEMORY_CLEANUP_CRON` | 记忆生命周期清理任务 cron（6 段） | `0 15 3 * * *` |
| `AI_AGENT_MAX_TOOL_CALLS` / `AI_AGENT_MAX_CALLS_PER_TOOL` | Agent 单轮工具调用总上限 / 单工具上限（`runCode` 固定放宽到 4：第 4 次用于返回 `retry_exhausted`） | `10` / `3` |
| `AI_AGENT_INTERRUPT_ON_DISCONNECT` | 客户端断开时是否立即取消模型流 | `false` |
| `AI_CHAT_MAX_TOKENS` / `AI_CHAT_TEMPERATURE` | chat 输出上限 / 温度 | `1200` / `0.2` |
| `AI_CHUNK_SIZE` / `AI_MIN_CHUNK_SIZE_CHARS` / `AI_MAX_CHUNKS_PER_DOC` | RAG 切块大小 / 最小块字符数 / 单文档最大块数 | `500` / `100` / `40` |
| `AI_TOP_K` / `AI_SIMILARITY_THRESHOLD` | 检索块数 / 最低相似度 | `6` / `0.35` |
| `AI_MAX_QUESTION_CHARS` / `AI_MAX_HISTORY_TURNS` | 问题最大长度 / 带入模型的历史轮数 | `2000` / `4` |
| `AI_WEB_SEARCH_AUTO_FALLBACK` | 本地文档无命中时是否自动联网一次（与显式搜索共用成功预算） | `true` |
| `AI_WEB_SEARCH_MAX_RESULTS` | 单次联网结果数（显式搜索与自动兜底共用） | `3` |
| `AI_WEB_SEARCH_MAX_SUCCESSES` | 单轮成功联网上限；失败/重试不计数 | `3` |
| `AI_WEB_SEARCH_MAX_ATTEMPTS` / `AI_WEB_SEARCH_RETRY_BACKOFF_MS` | 单次调用的 HTTP 尝试上限 / 可重试失败的间隔 | `2` / `300` |
| `AI_WEB_SEARCH_ENABLED` | 是否注册 `webSearch` 工具（用户还需单轮勾选「允许联网」） | `true`；未配置 key 时自动停用 |
| `AI_WEB_SEARCH_API_KEY` | 搜索 key；未设置时回退 `DEEPSEEK_API_KEY` | 空 |
| `AI_WEB_SEARCH_ENDPOINT` | DeepSeek 搜索地址 | `https://api.deepseek.com/v1/tools/search` |
| `AI_WEB_SEARCH_TIMEOUT_MS` | 搜索 HTTP 超时 | `20000` |
| `AI_MODERATION_DICT` | 违禁词表位置（逗号分隔，支持 `classpath:` / `file:`）；用于修正误判或补充词 | `classpath:ai/moderation/words.txt` |
| `AI_MODERATION_ALLOW` | 白名单位置，命中白名单的词不判定 | `classpath:ai/moderation/allow.txt` |
| `AI_MODERATION_STRIKE_LIMIT` | 触发冷却所需的输入命中次数（仅 INPUT 计分） | `5` |
| `AI_MODERATION_STRIKE_WINDOW_MINUTES` | 命中次数统计窗口（分钟） | `10` |
| `AI_MODERATION_COOLDOWN_MINUTES` | 达到阈值后暂停使用的时长（分钟） | `15` |

> 索引是**整库重建**：管理员调用 `POST /api/admin/ai/reindex`（或聊天页右上角「重建索引」）会删除
> `corpus=velochatx` 的点并重新 embedding。换 embedding 供应商（维度不变）重建索引即可；
> 换维度需先删除 Qdrant 集合，或改 `AI_COLLECTION`。
> 当前默认：**SiliconFlow `Qwen/Qwen3-Embedding-0.6B`**（OpenAI 兼容、1024 维、
> 使用 `SILICONFLOW_API_KEY`）。
> 注意：Spring AI 2.x 的请求 URL = base-url + 固定路径（`/chat/completions`、`/embeddings`），
> 因此 base-url 必须带版本段（`https://api.siliconflow.cn/v1`）。
> Skills 内置在 classpath；工具目录在管理台 `/admin/ai` 只读展示，用户侧只能选 skill 和「允许联网」。
> 联网默认开启，但需要 `DEEPSEEK_API_KEY`（或 `AI_WEB_SEARCH_API_KEY`）才真正启用；未配置时
> `webSearchEnabled=false`，前端不会显示「允许联网」。搜索走 DeepSeek 的
> `POST /v1/tools/search`，返回 `title/url/snippet/summary`；用户单轮勾选「允许联网」才注册工具。

## 6. 代码沙箱（Piston）

| 变量 | 作用 | 默认 |
|------|------|------|
| `PISTON_BASE_URL` | 后端直连 Piston 的地址（仅登录用户可用） | `http://localhost:2000` |

## 7. 生产（后续）

- `JWT_ISSUER` 设为 `https://auth.nitrowater.cn`（须与 AS 实际对外地址、前端 `VITE_OIDC_AUTHORITY` 三方一致）；
- JWT 私钥改由环境变量 `JWT_PRIVATE_KEY` 指向挂载路径（`file:/etc/nitrowater/keys/private.key`）；
- `NITROWATER_KEK` 由密钥管理系统注入；
- JPA `ddl-auto` 由 `update` 改为 `validate` + Flyway 基线；
- docker 部署：`deploy/docker/`（`docker-compose.{infra,app,piston}.yml` + 模板 `.env.example`；根单文件多目标 `Dockerfile`），已对齐 waterfun 约定（infra=中间件、external `nitrowater-net`、服务=app）。

## 8. 账号验证码发送（account）

| 变量 | 作用 | 默认 |
|------|------|------|
| `COMMUNICATION_MODE` | `mock` 只写日志不发；`real` 走 SMTP + 阿里云短信 | `mock` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 地址 / 端口（real 必填） | 空 / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 账号 / 密码 | 空 |
| `MAIL_FROM` | 发件地址（real 必填） | 空 |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | SMTP 认证 / STARTTLS | `true` / `true` |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET` | 阿里云短信凭据（real 必填；阿里云标准环境变量，SDK 同名读取，**不进配置文件**） | 空 |
| `ALIYUN_SMS_SIGN_NAME` / `ALIYUN_SMS_TEMPLATE_CODE` | 短信签名 / 模板（real 必填） | 空 |
| `ALIYUN_SMS_ENDPOINT` | 阿里云短信 endpoint | `dysmsapi.aliyuncs.com` |

> 本地默认 `COMMUNICATION_MODE=mock`：验证码只写入 account 日志，不会真实发送。
>
> `COMMUNICATION_MODE=real` 但某个通道没填凭据时，该发送器会打印一条 WARN（列出缺哪几项）并降级为写日志，服务照常启动；补齐变量后该 WARN 消失、自动走真实发送。
>
> 生产必须设置 `COMMUNICATION_MODE=real` 并补齐 SMTP / 阿里云短信配置（`deploy/bin/check-env.*` 会校验）；降级/mock 模式会泄露验证码到日志，禁止用于生产。

## 9. 内容解耦与挂载（词表 / 文档语料 / 外部 skills）

代码走镜像、策略走数据库、内容走卷：三者发布节奏不同，混在一起就会出现"改一个词要发一次版"。

| 变量 | 用途 | 默认 | 说明 |
|------|------|------|------|
| `CONTENT_ROOT` | 宿主机内容根目录 | `deploy/content`（叠加层内为 `../content`） | 其下 `moderation/` `docs/` `skills/` 三个子目录 |
| `AI_MODERATION_DICT` | 违禁词位置，**逗号分隔多层** | `classpath:ai/moderation/words.txt` | 生产写法：`classpath:...,file:/etc/nitrowater/moderation/words.txt` |
| `AI_MODERATION_ALLOW` | 白名单位置，多层语义同上 | `classpath:ai/moderation/allow.txt` | 命中 allow 即判为不违规 |
| `AI_DOCS_LOCATION` | 覆盖 `nitrowater` 语料位置 | `classpath:ai/docs/nitrowater` | 纯路径（`/etc/nitrowater/docs/nitrowater`）或 `file:` 前缀均可；**目录必须含 `.md`** |
| `AI_SKILLS_DIR` | 外部技能目录（`<name>/SKILL.md`） | `deploy/ai-skills`（容器内不存在） | 外部同名技能覆盖内置 |
| `AI_COLLECTION` | Qdrant 集合名 | `nitrowater_velochatx_docs` | 换 embedding 模型/维度时**换集合**，别原地重建 |

### 9.1 启用方式

推荐走单入口（自动带 `check-env` 预检）：

```powershell
.\deploy\bin\deploy.ps1 -Content          # Windows；-SkipCheck 可跳过预检
```
```bash
bash deploy/bin/deploy.sh --content       # Linux/macOS；--skip-check 可跳过预检
```

`deploy.{ps1,sh}` 在 `deploy/content/moderation/words.txt` 存在、或 `deploy/content/docs`
下有任意 `.md` 时会**自动开启**叠加层；不加 `--content` 也可显式强制。

等价的手工方式：

```bash
docker compose -f docker-compose.app.yml -f docker-compose.content.yml up -d
```

不叠加该文件时，行为与之前完全一致（词表与文档仍取镜像 `classpath`）。内容目录契约见
`deploy/content/README.md`；该目录被 `.gitignore` 排除，仅保留 `README.md` / `.gitkeep`。

### 9.2 三条硬规则

1. **词表必须双层，禁止只用 `file:` 单层。** 挂载路径写错时单层会得到 `words=0`——闸门静默失效，
   日志有 WARN、用户无感。双层保留 classpath 基线后，最坏情况只是"少了增量词"。
2. **`AI_DOCS_LOCATION` 指向的目录必须至少有 1 个 `.md`。** 该 corpus 为空时全量重建不会保留它，
   这段文档会从检索结果里消失（`reindex` 对全空语料是安全的：不动旧索引；但对"单 corpus 为空"不是）。
3. **语料与站点同版本发布。** 引用链接 = `public-base-url` + 相对路径；两者不同步时 `[n]` 会指到另一版页面。

### 9.3 生效方式（都不需要重新发版）

| 改动 | 生效动作 | 是否重启 |
|------|----------|----------|
| 词表 `words.txt` / `allow.txt` | `POST /api/admin/ai/moderation/reload` | 否 |
| 文档语料 | `POST /api/admin/ai/reindex`（全量重建，建议低峰） | 否 |
| 外部 skills | 下次 `loadSkill` 读取 | 否（技能注册在启动时，改动后如未生效则重启） |
| 模式 off/log/enforce | 管理端 › AI › 运行设置 | 否 |

### 9.4 上线前核对（`deploy/bin/check-env.ps1` / `check-env.sh` 覆盖）

- 词表文件存在且去注释后非空；`AI_MODERATION_DICT` 含 classpath 基线；
- `AI_DOCS_LOCATION` 指向路径（纯路径或 `file:`）时目录存在且含 `.md`；
- 必需变量非占位值（`123456` / `change-me` / `nitrowater-bff-secret` / dev 默认 salt）；
- `COMMUNICATION_MODE=real` 时 SMTP + 阿里云短信齐全；`RESOURCE_BACKEND=cos` 时 COS 齐全；
- chat / embedding key 至少一条可用来源；`JWT_ISSUER` 是 URL；`deploy/keys/*.key` 存在。

### 9.5 本地开发（同一套 `deploy/` 目录）

`nitrowater-server/build.gradle` 把 `bootRun.workingDir` 固定为**仓库根**，所以本地可以直接用
仓库相对路径指向 `deploy/content`（与容器内挂载的是同一份文件，只是路径不同）：

```powershell
$env:AI_MODERATION_DICT='classpath:ai/moderation/words.txt,file:deploy/content/moderation/words.txt'
$env:AI_DOCS_LOCATION='deploy/content/docs/nitrowater'   # 纯路径即可，不需要 file:
$env:AI_SKILLS_DIR='deploy/content/skills'
.\gradlew.bat :nitrowater-server:bootRun
```

| 产物 | 本地位置 | 生产位置 | 差异 |
|------|----------|----------|------|
| 词表 / 语料 / 外部技能 | `deploy/content/*`（相对路径） | 同目录 → 挂进 `/etc/nitrowater/*:ro` | 路径前缀与只读挂载 |
| 站点文档（HTML） | `deploy/plugin-docs/` | 同一 compose 的 `docs` 服务 | 本地 `sync-docs.ps1` 填、prod 由 CI 发布 |
| 统一资源二进制（插件 Jar / 上传） | `deploy/resource-storage/`（`RESOURCE_BACKEND=local`） | SeaweedFS / COS | 本地才用文件系统后端 |
| JWT 密钥 | `deploy/keys/` | 同目录挂进容器 | 无 |

两点容易混：

1. **站点文档 ≠ RAG 语料**。`deploy/plugin-docs` 只服务于 nginx（`/plugin/**`）；AI 检索读的是
   `classpath:ai/docs/*` 或 `AI_DOCS_LOCATION`。改了 md 想让 AI 立刻用上，必须 `reindex`，且要保证
   站点与语料同版本。
2. **资源二进制不要塞进 `deploy/content`**。`deploy/content` 在容器里是**只读**挂载，而 resource
   服务要写入（PENDING 上传、孤儿回收）；两者生命周期也不同（前者进内容仓、后者要备份）。


## 10. 边缘层与暴露面（生产）

原则一句话：**只有边缘对外，管理面一律内网。** 除业务 origin 外，其余容器都不发布端口，
或只绑 `127.0.0.1`。

| 变量 | 默认 | 作用 |
|------|------|------|
| `PUBLISH_BIND` | `127.0.0.1` | `account/resource/server/bff` 的宿主端口绑定 |
| `OPS_BIND` | `127.0.0.1` | `qdrant:6333`、`seaweedfs:8333/8888/9333` 的宿主端口绑定 |
| `WEB_DOMAIN` | edge 必填 | 业务站点域名，可写逗号列表（`a.cn,www.a.cn`） |
| `AUTH_DOMAIN` | edge 必填 | SSO 子域（`auth.a.cn`），`JWT_ISSUER` 必须与之一致 |
| `SSL_EMAIL` | edge 必填 | Let's Encrypt 联系邮箱 |
| `SESSION_COOKIE_SECURE` | `false` | HTTPS 下必须 `true`；edge 叠加层自动注入 |

### 10.1 拓扑

```
公网 80/443 → nginx-proxy（+ acme-companion 自动证书）
  ├── WEB_DOMAIN  → bff:8080        SPA + /api/** + /plugin/**
  └── AUTH_DOMAIN → account:8090    登录 / OIDC / SSO admin（SUPER_ADMIN）
容器内 / 回环，不对外：
  server:8081  resource:8093  docs:80  mysql  redis  piston
  qdrant:6333  seaweedfs:8888/9333   ← 仅 SSH 隧道
```

### 10.2 启用

```powershell
.\deploy\bin\deploy.ps1 -Edge      # Windows
```
```bash
bash deploy/bin/deploy.sh --edge   # Linux/macOS
```

等价手工：`docker compose -f docker-compose.app.yml -f docker-compose.edge.yml up -d`
（可再叠加 `-f docker-compose.content.yml`）。不叠加 edge 时行为不变，只是没有公网入口。

回滚：去掉 `-f docker-compose.edge.yml` 重新 `up -d`，旧容器会被移除；证书卷保留，
下次启用即复用，不会重复申请。

### 10.3 证书

- `acme-companion` 按容器的 `LETSENCRYPT_HOST` **自动签发 + 续期**（HTTP-01）。
- 前提：80 端口公网可达、域名 A 记录指向本机、邮箱有效。
- 内网-only 域名拿不到 HTTP-01 证书 → 改 DNS-01（`ACME_CHALLENGE=DNS` + provider 凭据）
  或内网自签；这类域名不该走公网边缘，直接走 VPN。
- `nginx-proxy` 需要挂载 `docker.sock`（等价 root）。单机自建可接受；多租户机器应加
  `docker-socket-proxy`，或改用 Caddy（无常驻 socket）。

### 10.4 管理面入口（Qdrant / SeaweedFS）

```powershell
.\deploy\bin\ops-tunnel.ps1 -HostName ops.example.com
```
```bash
bash deploy/bin/ops-tunnel.sh --host ops.example.com
```

然后浏览器直接开 `http://127.0.0.1:6333/dashboard`、`:8888`、`:9333`。
本地端口与远端一致，书签固定；关窗口即断。Qdrant 若启用了 API key，Dashboard 首次会提示输入。

**不要**把 `OPS_BIND` 设成 `0.0.0.0`：那两个 UI 没有认证，SeaweedFS filer 还能删文件。

### 10.5 一个已知副作用：presigned 图片

`resource` 服务对"可内联渲染"的类型（头像、图片、PDF）返回 **presigned 重定向**，浏览器会
直接去访问 S3 端点。S3 只在 `127.0.0.1` 时，这类资源在生产打不开（插件 Jar 不受影响，它们
走 `resource` 流式下载）。三选一：

1. 接受降级（只影响内联图片）；
2. `RESOURCE_BACKEND=local`：全部走 `resource` 流式，代价是流量过服务；
3. 在 `docker-compose.edge.yml` 里打开注释掉的 `s3` vhost，并设
   `S3_PUBLIC_ENDPOINT=https://<S3_DOMAIN>`（presigned URL 由签名+有效期保护）。

## 11. 资源（对象存储）与三个更新入口

### 11.1 用户上传开关

| 变量 | 默认 | 说明 |
|------|------|------|
| `RESOURCE_USER_UPLOAD` | `true`（edge 叠加层默认 `false`） | `false` 时 `/api/resource/upload/**` 仍映射，但返回 `503 uploads_disabled` |

"保留接口、不暴露"：改环境变量即可，代码/前端都不用动。

- **受影响**：用户上传（头像、图片、附件）。目前前端没有任何上传入口，等于无感。
- **不受影响**：插件 Jar 的发布（`/api/admin/plugin-resources/**`、`/internal/resource/publish`）与公开读取/下载（`/api/public/**`）。

将来要开放上传，还得同时决定 presigned 方案（§10.5）：`RESOURCE_PRESIGN=false` 走同源流式，或给 S3 开一个边缘 vhost。

### 11.2 三个更新入口（都不需要重跑 jar / 重建镜像）

| 内容 | 放在哪 | 更新动作 | 生效方式 |
|------|--------|----------|----------|
| AI 词表 / 语料 / 技能 | `deploy/content/**`（挂到 `/etc/nitrowater/*`） | 覆盖文件 | `POST /api/admin/ai/moderation/reload`（词表）/ `reindex`（语料） |
| 插件站点文档（HTML） | `deploy/plugin-docs/<plugin>/docs/` | 覆盖构建产物 | nginx 每请求读盘（必要时 `docker restart nitrowater-docs`） |
| 插件 Jar（下载用） | **不在 `deploy/`**：resource 服务 + SeaweedFS | `bin\dev\sync-res.ps1` → `POST /internal/resource/publish` | 同名重发原地覆盖，下载 URL 不变 |

`deploy/content` **只**服务 AI 问答引用；站点 HTML 与插件 Jar 是另外两条链路，别混用。

### 11.3 插件 Jar 同步流程（`bin/dev/sync-res.ps1`）

1. 扫描 `-SourceDir` 下的 `*.jar`（默认 VeloChatX 构建产物目录）；
2. 按 mtime **从旧到新**发布 —— 列表按 `createdAt DESC`，而 `createdAt` 只在 INSERT 时写入，所以最后发布的那个排第一（SPA 的 "latest" 徽标取 `files[0]`）；
3. 逐个 `POST {BaseUrl}/internal/resource/publish?app&group&name[&version]`，头 `X-Internal-Token`，body 为原始字节；
4. 服务端：`requireToken`（常量时间比较；未配置 token 直接 503，fail-closed）→ 读 body → `publishSystem` 原地覆盖同 `app+group+name` 的对象，**保留 uuid 与下载 URL**；
5. 字节落在 SeaweedFS 命名卷 `nitrowater-seaweedfs`（宿主 `/var/lib/docker/volumes/nitrowater-seaweedfs/_data`），对象键 `app/group/owner|system/yyyy/MM/dd/<uuid>.jar`。

> ⚠️ 已知限制：内部发布通道的尺寸上限用的是 `RESOURCE_MAX_SIZE`（默认 10MB），**不是** `RESOURCE_PLUGIN_MAX_SIZE`（64MB），大 jar 会被 413。临时办法是调高 `RESOURCE_MAX_SIZE`；正确做法是让内部发布对 `.jar` 用 plugin 上限，并改成流式读取（现在整包读进堆内存）。

### 11.4 业务管理入口

| 入口 | 路径 | 能做什么 |
|------|------|----------|
| SPA 管理页 | `/admin/resources`（`AdminResources.vue`） | 列表/筛选/详情/下载/删除，对应 `/api/admin/resources` |
| 插件 Jar 管理 | `/api/admin/plugin-resources/{key}` | 列出 / 上传 / 删除某插件的 jar；上传走 `PluginArtifactPolicy`（64MB + JAR magic 校验） |
| 内部发布 | `/internal/resource/publish` | 运维脚本 / CI 用，token 鉴权，不经 BFF |

`/api/admin/**` 需要 `ROLE_ADMIN` 或 `ROLE_SUPER_ADMIN`（`ResourceSecurityConfig`）。

## 12. 用量统计与全站熔断

### 12.1 数据模型：全局用量就是 `uid = 0` 的那一行

`ai_daily_usage` 的主键是 `(uid, usage_date)`。全站用量复用同一张表、同一条 reserve→settle 路径，
只是把 `uid` 固定为哨兵值 **`0`**（`AiUsageService.GLOBAL_UID`）。因此：

- 并发安全性与个人额度一致（行锁 + 在途预留 `reserved_tokens`）；
- 结算、管理员清零、保留策略都是同一套代码；
- 个人合计 = `SUM()` 或直接读该行，不存在第二套账。

| 变量 | 默认 | 说明 |
|------|------|------|
| `AI_USAGE_RETENTION_DAYS` | `31` | 个人日明细保留天数（低于 7 会被抬到 7） |
| `AI_USAGE_GLOBAL_RETENTION_DAYS` | `366` | 全局哨兵行保留天数（全站长期曲线靠它） |
| `AI_USAGE_CLEANUP_CRON` | `0 45 4 * * *` | 每日清理时间（UTC） |

### 12.2 管理端开关

| 位置 | 字段 / 动作 | 说明 |
|------|-------------|------|
| 管理端 › AI › 运行设置 | `全站 token 额度（每日）` | `0` = 不限制；单位 token |
| 管理端 › AI › 运行设置 | `全站额度熔断` | 关闭时只统计不拦截 |
| 管理端 › AI › 用量 | `全站用量` 卡片 | 今日用量 / 上限 / 请求数 / 占比 + 近 31 天曲线 |
| 管理端 › AI › 用量 | `解除今日熔断` | 清零全局行，立即恢复提问（误判兜底） |

接口：`GET /api/admin/ai/usage/summary?days=31`、`POST /api/admin/ai/usage/reset-global`；
公开状态：`GET /api/ai/status` 的 `globalQuota {enabled, limit, used, tripped, resetAt}`。

### 12.3 熔断行为

- 触发条件：`enabled && limit > 0 && (input + output + reserved) >= limit`。
- 只拦**调用模型**的入口：`/api/ai/agent/**`，返回
  `429 {"error":"global_quota_exceeded","limit":...,"used":...}`。
- **不受影响**：`/api/ai/status`、`/api/ai/usage`、`/api/ai/slash/**`（确定性查询，不花 token）、
  历史会话（前端本地保存）。
- 重置时间：UTC 0 点（北京时间 08:00），前端横幅会写明。
- 建议：先只观察（开关关、上限设一个值）跑 3–7 天，再打开熔断。

### 12.4 已知限制

- **embedding 未计入**：`reindex` 走 `vectorStore.add()`，不经过 `AiUsageService`，所以全站额度
  不覆盖索引重建成本。大语料重建前请人工评估（后续可加"每日重建次数/总量上限"）。
- 管理员不豁免熔断（避免上限失真）；需要时可点"解除今日熔断"。
- 日界是 UTC，与本地面向用户的"今天"可能错开 8 小时。
