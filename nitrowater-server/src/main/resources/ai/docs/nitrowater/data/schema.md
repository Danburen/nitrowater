# 数据模型与 Schema 约定

## 1. 三库分离

| 服务 | 数据库 | 迁移目录 | 说明 |
|---|---|---|---|
| account | `nitrowater_account` | `nitrowater-account/src/main/resources/db/migration` | 用户、角色、身份、OAuth2 授权 |
| server | `nitrowater_biz` | `nitrowater-server/src/main/resources/db/migration` | 业务用户投影、角色、插件、NitroBot |
| resource | `nitrowater_resource` | `nitrowater-resource/src/main/resources/db/migration` | 资源元数据 |

- 每个服务只读写自己的库，`ddl-auto=none`，schema 由 Flyway 管理。
- 跨库引用只用逻辑键：`uid`（数字主键）或资源 `uuid`（`CHAR(36)`），**不建物理外键**。
- 同库内的外键允许，命名 `fk_*`。

## 2. 强制约定

1. **时间列统一 `TIMESTAMP(3)`**（毫秒）。配合连接参数 `connectionTimeZone=UTC&preserveInstants=true`，Java 侧用 `java.time.Instant`。唯一例外是纯日期语义（如生日）用 `DATE`。
2. **枚举一律整数编码**：列类型 `TINYINT UNSIGNED`，Java 侧枚举带 `short value` 与 `static fromValue(int)`，用 `@Converter(autoApply = true)` 映射。禁止 `@Enumerated(STRING/ORDINAL)`。
3. **布尔列用 `TINYINT(1)`**（0/1），不要用 `TINYINT UNSIGNED` 表达布尔。
4. 命名：库/表 `snake_case`、表名单数；唯一键 `uk_*`、普通索引 `idx_*`、外键 `fk_*`。
5. 表引擎 `InnoDB`，字符集 `utf8mb4`。
6. 枚举编码**只追加、不改**；列的 `COMMENT` 写清每个编码。
7. 已应用的 Flyway 迁移冻结。dev 阶段允许「改基线 + drop schema 重放」，生产环境必须新增版本。

## 3. account 库核心表

| 表 | 说明 |
|---|---|
| `user` | 用户主键 `uid BIGINT UNSIGNED`，登录名、密码摘要、状态、头像资源 uuid |
| `user_data` / `user_data_archive` | 用户扩展资料与归档 |
| `sso_identity` | 第三方/SSO 身份绑定（`uid` 外键） |
| `oauth2_registered_client` | OIDC 客户端注册（`nitrowater-bff` 等） |
| `oauth2_authorization` / `oauth2_authorization_consent` | 授权码、Token 元数据、授权同意 |
| `role` / `user_role` | 全局角色与用户角色映射 |
| `account_audit_log` / `encryption_data_key` | 审计日志与数据密钥 |

> Spring Authorization Server 官方的 `oauth2_*` 表来自 `V1__sso_baseline.sql`，其中的裸 `timestamp` 列属于 vendor schema，**原样保留**，不套用本项目的 `TIMESTAMP(3)` 约定。

## 4. biz 库核心表

| 表 | 说明 |
|---|---|
| `biz_user` | account 用户的业务投影：`uid`、用户名、昵称、头像资源 uuid、头像来源、同步时间、状态 |
| `biz_role` / `biz_user_role` | 应用内角色与映射 |
| `plugin` | 插件中心元数据：`plugin_key`、名称、简介、可见性等 |

`biz_user.uid` 与 `biz_role` 的关系是**同库物理外键**（`fk_bur_user` / `fk_bur_role`，`ON DELETE CASCADE`）。

## 5. resource 库核心表

| 表 | 说明 |
|---|---|
| `resource` | 资源元数据：`uuid`（对外唯一键）、所有者 `uid`、`app`、`group_key`、文件名、大小、MIME、可见性、状态、存储后端与对象键 |

- `uuid` 是跨服务引用目标，唯一索引必建。
- 行状态区分 `PENDING`（预签名已发但未回调）、`ACTIVE`、`ORPHAN`（上传放弃后被清理）。
- 插件 JAR 通过 `app=nitrowater` + `group_key=plugin_key` 的逻辑约定挂在插件下。

## 6. NitroBot 表（`nitrowater_biz`）

### 6.1 `ai_conversation`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT UNSIGNED` | 内部主键 |
| `uuid` | `CHAR(36)` | 对外唯一键，前端只认它 |
| `uid` | `BIGINT UNSIGNED` | account 用户主键（JWT sub） |
| `title` | `VARCHAR(120)` | 首条问题截断，可 PATCH |
| `message_count` | `INT UNSIGNED` | 消息条数缓存 |
| `tokens_estimate` | `INT UNSIGNED` | 最近一次上下文估算 token |
| `summary` | `TEXT` | 滚动摘要 |
| `summary_upto_seq` | `INT UNSIGNED` | 摘要覆盖到的最大 `seq`（含） |
| `compress_version` | `INT UNSIGNED` | 压缩乐观锁 |
| `last_message_at` | `TIMESTAMP(3)` | 列表排序键 |
| `status` | `TINYINT UNSIGNED` | `0=ACTIVE 1=ARCHIVED 2=DELETED` |
| `created_at` / `updated_at` | `TIMESTAMP(3)` | 审计时间 |

索引：`uk_ai_conversation_uuid (uuid)`、`idx_ai_conversation_uid_status_last (uid, status, last_message_at)`。

### 6.2 `ai_message`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT UNSIGNED` | 内部主键 |
| `uuid` | `CHAR(36)` | 消息唯一键 |
| `conversation_id` | `BIGINT UNSIGNED` | 外键 → `ai_conversation.id`，`ON DELETE CASCADE` |
| `seq` | `INT UNSIGNED` | 会话内单调序号，从 1 起 |
| `role` | `TINYINT UNSIGNED` | `0=USER 1=ASSISTANT 2=ERROR` |
| `content` | `MEDIUMTEXT` | 原文永久保留 |
| `tokens` | `INT UNSIGNED` | 估算或实际 usage |
| `sources_json` | `TEXT` | 引用元数据 |
| `tools_json` | `TEXT` | 工具轨迹 |
| `model` | `VARCHAR(64)` | 生成该条回答的模型 |
| `is_pruned` | `TINYINT(1)` | `1`=长工具结果已折叠为占位符 |
| `created_at` | `TIMESTAMP(3)` | 创建时间 |

约束：`uk_ai_message_uuid (uuid)`、`uk_ai_message_conv_seq (conversation_id, seq)`。

### 6.3 `ai_user_profile`

| 列 | 类型 | 说明 |
|---|---|---|
| `uid` | `BIGINT UNSIGNED` | 主键 |
| `language` | `VARCHAR(16)` | 回答语言偏好，默认 `zh-CN` |
| `reply_style` | `TINYINT UNSIGNED` | `0=CONCISE 1=BALANCED 2=DETAILED` |
| `expertise` | `TINYINT UNSIGNED` | `0=BEGINNER 1=INTERMEDIATE 2=EXPERT` |
| `timezone` | `VARCHAR(64)` | 时区 |
| `preferences_json` | `JSON` | 技术栈、输出格式、禁用词等自由扩展 |
| `inference_opt_in` | `TINYINT(1)` | 自动推断开关，默认关闭 |
| `version` | `INT UNSIGNED` | 乐观锁 |
| `created_at` / `updated_at` | `TIMESTAMP(3)` | 审计时间 |

### 6.4 `ai_profile_change`

画像变更留痕：`uid`、`field`、`old_value`、`new_value`、`source`（`0=USER 1=INFERRED`）、`confidence`（0-100）、`created_at`。用于撤销与审计。

### 6.5 `ai_memory`

跨会话长期记忆，MySQL 是唯一事实来源，Qdrant 只是检索影子。

| 列 | 类型 | 说明 |
|---|---|---|
| `uuid` | `CHAR(36)` | 记忆唯一键 |
| `uid` | `BIGINT UNSIGNED` | 所属用户 |
| `kind` | `TINYINT UNSIGNED` | `0=FACT 1=PREFERENCE 2=DECISION 3=PROJECT` |
| `content` | `VARCHAR(500)` | 记忆正文 |
| `source_conversation_uuid` | `CHAR(36)` | 来源会话，便于级联清理 |
| `confidence` | `TINYINT UNSIGNED` | 0-100，低于阈值不注入 |
| `status` | `TINYINT UNSIGNED` | `0=ACTIVE 1=USER_DELETED 2=EXPIRED` |
| `hits` / `last_used_at` | `INT UNSIGNED` / `TIMESTAMP(3)` | 召回统计与 LRU |
| `qdrant_point_id` | `CHAR(36)` | 已同步的 Qdrant 点；`NULL` 表示未同步 |

## 7. 数据生命周期

| 数据 | 策略 |
|---|---|
| 会话 | 90 天软删（`status=2`），可按需硬删 |
| 消息 | 随会话级联；原文保留以支持摘要重算 |
| 画像 | 长期保存，用户可编辑/删除 |
| 记忆 | 长期保存直到用户删除；每用户 ACTIVE 上限 200 条 |
| 资源 | PENDING 超时转 ORPHAN 并删除对象 |

## 8. 迁移操作

```powershell
# 改了 baseline 后，drop 该库所有表并重启，让 Flyway 从 V1 重放
.\bin\dev\reset-db.ps1 -Db biz -Force -Restart

# 只删表不重启
.\bin\dev\reset-db.ps1 -Db biz -Force

# 补种子数据
.\bin\dev\dev-seed.ps1
```

改基线而不重置库会导致启动时 checksum mismatch；`flyway repair` 只改记录的校验和，**不会补跑新加的 DDL**，因此不要用 repair 代替重置。
