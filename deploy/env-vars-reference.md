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
| 5 | Redis | `application.yml` data.redis（`${REDIS_PASSWORD:}`） | `localhost:6379` |
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
| 前端 OIDC 配置（Vite env） | `nitrowater-web/.env.local`（模板 `.env.example`） |
| 开发密钥登记（模板） | `deploy/config/common-dev-secrets.yml` |
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
| `AI_CHAT_API_KEY` | chat 模型 key；未设置时回退 `GLM_API_KEY` | — |
| `AI_CHAT_BASE_URL` / `AI_CHAT_MODEL` | OpenAI 兼容 chat 端点 / 模型 | 智谱 `https://open.bigmodel.cn/api/paas/v4` / `glm-5.3-flash` |
| `AI_EMBEDDING_API_KEY` | embedding key；未设置时回退 `GLM_API_KEY`（与 chat 同一把 key） | — |
| `GLM_API_KEY` | 智谱开放平台 key（chat / embedding 默认共用） | — |
| `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_MODEL` | OpenAI 兼容 embedding 端点 / 模型 | 智谱 `https://open.bigmodel.cn/api/paas/v4` / `embedding-3` |
| `AI_EMBEDDING_DIMENSIONS` | 向量维度；embedding-3 支持 256/512/1024/2048（默认 2048）；换模型/维度需换集合名或重建集合 | `1024` |
| `QDRANT_HOST` / `QDRANT_PORT` | Qdrant gRPC 地址 | `localhost` / `6334` |
| `AI_COLLECTION` | Qdrant 集合名 | `nitrowater_velochatx_docs` |
| `AI_INGEST_ON_STARTUP` | 启动时若集合为空则自动索引 | `true` |
| `AI_PER_USER_PER_MINUTE` / `AI_PER_USER_PER_DAY` | 每用户提问配额（Redis） | `20` / `300` |
| `AI_SKILLS_DIR` | 外部技能目录（`<name>/SKILL.md`，覆盖同名内置） | `deploy/ai-skills` |
| `AI_AGENT_MAX_TOOL_CALLS` / `AI_AGENT_MAX_CALLS_PER_TOOL` | Agent 单轮工具调用总上限 / 单工具上限（`runCode` 固定放宽到 4：第 4 次用于返回 `retry_exhausted`） | `10` / `3` |
| `AI_WEB_SEARCH_ENABLED` | 是否注册 `webSearch` 工具（用户还需单轮勾选「允许联网」） | `true`；未配置 key 时自动停用 |
| `AI_WEB_SEARCH_API_KEY` | 搜索 key；未设置时回退 `DEEPSEEK_API_KEY` | 空 |
| `AI_WEB_SEARCH_ENDPOINT` | DeepSeek 搜索地址 | `https://api.deepseek.com/v1/tools/search` |
| `AI_WEB_SEARCH_TIMEOUT_MS` | 搜索 HTTP 超时 | `20000` |

> 索引是**整库重建**：管理员调用 `POST /api/admin/ai/reindex`（或聊天页右上角「重建索引」）会删除
> `corpus=velochatx` 的点并重新 embedding。换 embedding 供应商（维度不变，如 Qwen3 → embedding-3）
> 重建索引即可；换维度需先删除 Qdrant 集合，或改 `AI_COLLECTION`。
> 当前默认：**智谱 `embedding-3`**（OpenAI 兼容、1024 维、与 chat 共用 `GLM_API_KEY`）。
> 注意：Spring AI 2.x 的请求 URL = base-url + 固定路径（`/chat/completions`、`/embeddings`），
> 因此 base-url 必须带版本段（`https://open.bigmodel.cn/api/paas/v4`）。
> Skills 内置在 classpath；工具目录在管理台 `/admin/ai` 只读展示，用户侧只能选 skill 和「允许联网」。
> 联网默认开启，但需要 `DEEPSEEK_API_KEY`（或 `AI_WEB_SEARCH_API_KEY`）才真正启用；未配置时
> `webSearchEnabled=false`，前端不会显示「允许联网」。搜索走 DeepSeek 的
> `POST /v1/tools/search`，返回 `title/url/snippet/summary`；用户单轮勾选「允许联网」才注册工具。

## 6. 生产（后续）

- `JWT_ISSUER` 设为 `https://auth.nitrowater.cn`（须与 AS 实际对外地址、前端 `VITE_OIDC_AUTHORITY` 三方一致）；
- JWT 私钥改由环境变量 `JWT_PRIVATE_KEY` 指向挂载路径（`file:/etc/nitrowater/keys/private.key`）；
- `NITROWATER_KEK` 由密钥管理系统注入；
- JPA `ddl-auto` 由 `update` 改为 `validate` + Flyway 基线；
- docker 部署：`deploy/docker/`（`docker-compose.{infra,app,piston}.yml` + 模板 `.env.example`；根单文件多目标 `Dockerfile`），已对齐 waterfun 约定（infra=中间件、external `nitrowater-net`、服务=app）。
