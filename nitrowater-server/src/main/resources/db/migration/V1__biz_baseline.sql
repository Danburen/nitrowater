-- ============================================================
-- nitrowater_biz · V1 baseline（业务库：用户镜像 / 角色 / 插件中心）
-- 约定（见 AGENTS.md）：时间列一律 TIMESTAMP(3)；枚举一律 TINYINT UNSIGNED（应用层 @Converter 映射）；
--   库内 FK fk_*、唯一键 uk_*、索引 idx_*；表名单数；ENGINE=InnoDB CHARSET=utf8mb4。
-- 与 SSO 库 nitrowater_account 物理隔离：仅以 uid / *_resource_uuid 逻辑关联，不建跨库 FK。
-- 首次部署前可直接改本文件；部署后冻结，后续走 V2__...
-- 已折叠进本基线的迁移：V1_1 (ai_message.skills_json)、V1_2 (ai_admin_setting.disabled_tools)、
--   V1_3 (ai_skill)。折叠后必须 drop schema 让 Flyway 重放；只改文件不重置会 checksum mismatch。
-- ============================================================

-- ---------- 业务用户镜像（account 为身份权威，本表为投影 + 应用态）----------
CREATE TABLE biz_user (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  uid                   BIGINT UNSIGNED NOT NULL                                  COMMENT '逻辑关联 account.user.uid（无跨库 FK）',
  username              VARCHAR(64)     NULL                                      COMMENT '展示快照（绑定/登录时自 account 复制）',
  nickname              VARCHAR(64)     NULL,
  avatar_resource_uuid  CHAR(36)        NULL                                      COMMENT '头像资源 uuid（逻辑关联 resource.uuid；默认镜像 account 规范头像）',
  avatar_source         TINYINT UNSIGNED NOT NULL DEFAULT 0                       COMMENT '0=ACCOUNT(镜像 account) 1=LOCAL(本应用覆盖)',
  profile_synced_at     TIMESTAMP(3)    NULL                                      COMMENT '上次自 account 同步档案的时间',
  status                TINYINT UNSIGNED NOT NULL DEFAULT 0                       COMMENT '0=ACTIVE 1=SUSPENDED',
  created_at            TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_user_uid (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务用户镜像（uid 逻辑关联 account）';

-- ---------- 应用内角色（与 account 全局角色分开）----------
CREATE TABLE biz_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(64)     NOT NULL                       COMMENT 'e.g. PLUGIN_ADMIN / MEMBER',
  name        VARCHAR(64)     NOT NULL,
  description VARCHAR(255)    NULL,
  builtin     TINYINT(1)      NOT NULL DEFAULT 0,
  created_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用内角色词汇表';

CREATE TABLE biz_user_role (
  user_id    BIGINT UNSIGNED NOT NULL,
  role_id    BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_bur_user FOREIGN KEY (user_id) REFERENCES biz_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_bur_role FOREIGN KEY (role_id) REFERENCES biz_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-应用角色映射';

-- ============================================================
-- 插件中心元数据（plugin）— 原为独立迁移 V1_1__plugin_center.sql；项目未上线，dev 阶段按
--   AGENTS.md「改基线 + 重置库」约定折叠回 V1 baseline（折叠后必须 drop schema 让 Flyway 重放；
--   仅改文件不重置会 checksum mismatch。且不能用 flyway repair 绕过——repair 只改 checksum，
--   不会执行这里新增的建表语句，会留下「表缺失」的静默漂移）。
--   跨库不建物理 FK：plugin_key 逻辑关联 resource(app='nitrowater', group_key=plugin_key)
--   与文档目录 /plugin/<key>/（BFF 静态挂载）。`key` 是 MySQL 保留字，故列名取 plugin_key。
-- ============================================================

-- ---------- 插件中心元数据（单页说明 / 下载入口 / 外链）----------
CREATE TABLE plugin (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plugin_key   VARCHAR(64)     NOT NULL                       COMMENT '稳定键（同 resource.group_key 与文档路径 /plugin/<key>/）',
  name         VARCHAR(100)    NOT NULL                       COMMENT '展示名',
  tagline      VARCHAR(200)    NULL                           COMMENT '一句话副标题',
  summary      VARCHAR(1000)   NULL                           COMMENT '卡片简介',
  icon         VARCHAR(500)    NULL                           COMMENT '24x24 线性图标 SVG path 的 d 属性',
  tags         JSON            NULL                           COMMENT '标签数组（字符串列表）',
  docs_url     VARCHAR(500)    NULL                           COMMENT '完整文档站（默认 /plugin/<key>/docs/，同源整页跳转）',
  homepage_url VARCHAR(500)    NULL                           COMMENT '官网链接（可空；空则前端不展示）',
  repo_url     VARCHAR(500)    NULL                           COMMENT '开源仓库链接（可空；空则前端不展示）',
  sponsor_url  VARCHAR(500)    NULL                           COMMENT '赞助 / 打赏链接（可空；空则前端不展示）',
  readme       MEDIUMTEXT      NULL                           COMMENT '单页说明 markdown（详情页「介绍」tab）',
  sort_order   INT             NOT NULL DEFAULT 0             COMMENT '列表顺序（升序）',
  enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  created_at   TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_key (plugin_key),
  KEY idx_plugin_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件中心元数据（plugin_key 逻辑关联 resource / 文档目录）';

-- 首个插件 VeloChatX：内容迁移自 nitrowater-web 原先硬编码的 catalog.ts + descriptions/velochatx.md
INSERT INTO plugin (plugin_key, name, tagline, summary, icon, tags,
                    docs_url, homepage_url, repo_url, sponsor_url, readme, sort_order, enabled)
VALUES ('velochatx',
        'VeloChatX',
        'Velocity 跨服聊天插件',
        '为 Velocity 代理提供跨服聊天：自定义聊天格式、跨服 / 群组广播、定时广播、@提及、TabList 显示替换、违禁词过滤等；内置 i18n 与自动更新。',
        'M4 5h16v11H8l-4 4zM8 9h8M8 12h5',
        '["Velocity","跨服聊天","MiniMessage","LuckPerms"]',
        '/plugin/velochatx/docs/',
        NULL,
        'https://github.com/Danburen/VeloChatX',
        NULL,
        '# VeloChatX

> 面向 **Minecraft Velocity** 代理的高性能跨服聊天插件。

VeloChatX 让整个代理网络共用一套干净、快速、可定制的聊天体验——从玩家加入 / 离开提示，到跨服广播、
定时公告、@提及与 TabList 替换，全部开箱即用；配置集中、可增量启停，适合作为社区服的基础设施。

## 核心特性

- **跨服聊天** — 消息在代理网络内实时互通；玩家进入 / 离开时，提示会同时广播到**原服**与**当前服**。
- **高度可定制** — 频道 / 组别 / 前后缀等占位符自由组合，支持 MiniMessage 富文本（渐变、悬浮、点击）。
- **跨服与群组广播** — 把一条消息定向广播到指定服务器或服务器组。
- **定时广播** — 按间隔自动轮播公告（可配置顺序、按权限可见）。
- **@ 提及** — 在聊天中 @ 玩家并高亮其标题。
- **TabList 替换** — 自定义 Tab 列表的页眉 / 页脚与玩家排序。
- **违禁词过滤** — 内置敏感词过滤，命中可拦截或替换。
- **i18n** — 内置 `zh_CN` / `en_US`，按玩家客户端语言自动切换。
- **数据持久化** — 基于数据库保存玩家设置等数据。
- **自动更新** — 检测新版本并提示升级。

## 安装

1. 在下方 **下载** 区取最新的 `VeloChatX.jar`。
2. 放入 Velocity 的 `plugins/` 目录。
3. **启动** 服务器，插件会自动生成配置文件。
4. **停止** 服务器，按需修改 `config`。
5. **重启** 服务器即可生效。

> 依赖 **Java 17+**；建议配合 [LuckPerms](https://luckperms.net/) 读取玩家前后缀等数据。

## 命令与权限（概要）

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/vchat reload` | 重载配置文件 | `velochatx.command.reload` |
| `/vchat broadcast <消息>` | 发送一次跨服广播 | `velochatx.command.broadcast` |
| `/vchat` | 其余子命令 | 见完整文档 |

> 上表仅为概要。**完整命令树、全部权限节点与占位符**见
> [文档 · 命令与权限](/plugin/velochatx/docs/reference/cmdsAndPerms.html)。

## 兼容性

| 项 | 说明 |
| --- | --- |
| 平台 | Velocity 3.x |
| 依赖 | LuckPerms（可选）、Java 17+ |
| 语言 | 简体中文 / English |
| 更新 | 自动检查新版本 |

## 相关链接

- **完整文档**：见本页上方「查看完整文档」（[快速开始](/plugin/velochatx/docs/guide/intro/get-start.html)）
- **源码仓库**：<https://github.com/Danburen/VeloChatX>
- **问题反馈**：<https://github.com/Danburen/VeloChatX/issues>',
        0,
        1);

-- ============================================================
-- NitroBot (T11-T14): conversations, messages, profile, memory.
-- Conventions (AGENTS.md): TIMESTAMP(3), enums TINYINT UNSIGNED, booleans TINYINT(1),
-- intra-database FKs fk_*, unique keys uk_*, indexes idx_*. `uid` is a logical reference to
-- account.user.uid (no cross-database FK). uuids are the external identifiers.
-- ============================================================

CREATE TABLE ai_conversation (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  uuid              CHAR(36)        NOT NULL                         COMMENT 'External conversation id used by the SPA',
  uid               BIGINT UNSIGNED NOT NULL                         COMMENT 'Logical ref account.user.uid (JWT sub)',
  title             VARCHAR(120)    NULL                             COMMENT 'Derived from the first question, editable',
  message_count     INT UNSIGNED    NOT NULL DEFAULT 0               COMMENT 'Cached number of stored messages',
  tokens_estimate   INT UNSIGNED    NOT NULL DEFAULT 0               COMMENT 'Last context token estimate',
  summary           TEXT            NULL                             COMMENT 'Rolling summary of earlier turns',
  summary_upto_seq  INT UNSIGNED    NOT NULL DEFAULT 0               COMMENT 'Highest message seq covered by summary',
  compress_version  INT UNSIGNED    NOT NULL DEFAULT 0               COMMENT 'Optimistic lock for async compression',
  last_message_at   TIMESTAMP(3)    NULL                             COMMENT 'List ordering key',
  status            TINYINT UNSIGNED NOT NULL DEFAULT 0              COMMENT '0=ACTIVE,1=ARCHIVED,2=DELETED',
  created_at        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_conversation_uuid (uuid),
  KEY idx_ai_conversation_uid_status_last (uid, status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot conversations';

CREATE TABLE ai_message (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  uuid             CHAR(36)        NOT NULL                          COMMENT 'External message id',
  conversation_id  BIGINT UNSIGNED NOT NULL                          COMMENT 'FK ai_conversation.id',
  seq              INT UNSIGNED    NOT NULL                          COMMENT 'Monotonic position within the conversation, from 1',
  role             TINYINT UNSIGNED NOT NULL                         COMMENT '0=USER,1=ASSISTANT,2=ERROR',
  content          MEDIUMTEXT      NOT NULL                          COMMENT 'Original text, kept even after pruning',
  tokens           INT UNSIGNED    NOT NULL DEFAULT 0                COMMENT 'Estimated or reported usage',
  sources_json     TEXT            NULL                              COMMENT 'Citation metadata snapshot',
  tools_json       TEXT            NULL                              COMMENT 'Tool trace snapshot',
  skills_json      TEXT            NULL                              COMMENT 'Skill trace snapshot (folded from V1_1)',
  model            VARCHAR(64)     NULL                              COMMENT 'Model that produced an assistant message',
  is_pruned        TINYINT(1)      NOT NULL DEFAULT 0                COMMENT '1=long tool output folded into a placeholder in context',
  moderated        TINYINT(1)      NOT NULL DEFAULT 0                COMMENT '1=answer matched the violation dictionary and was masked',
  created_at       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_message_uuid (uuid),
  UNIQUE KEY uk_ai_message_conv_seq (conversation_id, seq),
  CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot messages';

CREATE TABLE ai_user_profile (
  uid               BIGINT UNSIGNED NOT NULL                         COMMENT 'Primary key, logical ref account.user.uid',
  language          VARCHAR(16)     NOT NULL DEFAULT 'zh-CN'         COMMENT 'Preferred answer language',
  reply_style       TINYINT UNSIGNED NOT NULL DEFAULT 1              COMMENT '0=CONCISE,1=BALANCED,2=DETAILED',
  expertise         TINYINT UNSIGNED NOT NULL DEFAULT 1              COMMENT '0=BEGINNER,1=INTERMEDIATE,2=EXPERT',
  timezone          VARCHAR(64)     NULL                             COMMENT 'IANA timezone',
  preferences_json  JSON            NULL                             COMMENT 'Free-form preferences (stack, format, bans)',
  inference_opt_in  TINYINT(1)      NOT NULL DEFAULT 0               COMMENT '1=allow automatic profile inference',
  version           INT UNSIGNED    NOT NULL DEFAULT 0               COMMENT 'Optimistic lock',
  created_at        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot per-user profile';

CREATE TABLE ai_profile_change (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  uid         BIGINT UNSIGNED NOT NULL                               COMMENT 'Logical ref account.user.uid',
  field       VARCHAR(64)     NOT NULL                               COMMENT 'Changed profile field',
  old_value   VARCHAR(500)    NULL,
  new_value   VARCHAR(500)    NULL,
  source      TINYINT UNSIGNED NOT NULL DEFAULT 0                    COMMENT '0=USER,1=INFERRED',
  confidence  TINYINT UNSIGNED NOT NULL DEFAULT 0                    COMMENT '0-100, meaningful for INFERRED',
  created_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ai_profile_change_uid (uid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot profile change audit';

CREATE TABLE ai_memory (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  uuid                      CHAR(36)        NOT NULL                 COMMENT 'External memory id',
  uid                       BIGINT UNSIGNED NOT NULL                 COMMENT 'Logical ref account.user.uid',
  kind                      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '0=FACT,1=PREFERENCE,2=DECISION,3=PROJECT',
  content                   VARCHAR(500)    NOT NULL                 COMMENT 'Memory text injected into prompts',
  source_conversation_uuid  CHAR(36)        NULL                     COMMENT 'Conversation that produced the memory',
  confidence                TINYINT UNSIGNED NOT NULL DEFAULT 50    COMMENT '0-100, below threshold is not injected',
  status                    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '0=ACTIVE,1=USER_DELETED,2=EXPIRED',
  hits                      INT UNSIGNED    NOT NULL DEFAULT 0       COMMENT 'Recall counter',
  last_used_at              TIMESTAMP(3)    NULL                     COMMENT 'LRU key',
  qdrant_point_id           CHAR(36)        NULL                     COMMENT 'Vector shadow id, NULL when not synced',
  created_at                TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_memory_uuid (uuid),
  KEY idx_ai_memory_uid_status (uid, status, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot long-term memory (MySQL is the source of truth)';

-- ============================================================
-- NitroBot daily token usage and runtime controls (folded from V1_1).
-- Usage day is a pure date (UTC); all event timestamps use TIMESTAMP(3).
-- ============================================================

CREATE TABLE ai_daily_usage (
  uid                 BIGINT UNSIGNED NOT NULL COMMENT 'Logical ref account.user.uid; no cross-database FK',
  usage_date          DATE            NOT NULL COMMENT 'UTC usage day',
  input_tokens        BIGINT UNSIGNED NOT NULL DEFAULT 0,
  output_tokens       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  reserved_tokens     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'In-flight request reservation for atomic quota enforcement',
  request_count       INT UNSIGNED    NOT NULL DEFAULT 0,
  estimated_requests  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Requests without provider-reported usage metadata',
  updated_at          TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (uid, usage_date),
  KEY idx_ai_daily_usage_date_total (usage_date, input_tokens, output_tokens)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user NitroBot token usage by UTC day';

CREATE TABLE ai_admin_setting (
  id                      TINYINT UNSIGNED NOT NULL COMMENT 'Singleton row; id=1',
  daily_token_limit       BIGINT UNSIGNED NOT NULL COMMENT 'Per-user daily token limit',
  global_daily_token_limit BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'All users combined daily limit; 0 = unlimited',
  global_quota_enabled    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Master switch for the global quota breaker',
  ai_enabled              TINYINT(1)      NOT NULL DEFAULT 1,
  chat_enabled            TINYINT(1)      NOT NULL DEFAULT 1,
  agent_enabled           TINYINT(1)      NOT NULL DEFAULT 1,
  document_search_enabled TINYINT(1)      NOT NULL DEFAULT 1,
  web_search_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  code_runner_enabled     TINYINT(1)      NOT NULL DEFAULT 1,
  skills_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
  disabled_tools          VARCHAR(500)    NOT NULL DEFAULT '[]' COMMENT 'JSON array of tool names hidden from the model (folded from V1_2)',
  moderation_mode         TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0=OFF,1=LOG(仅记录),2=ENFORCE(拦截+遮蔽)',
  updated_by_uid          BIGINT UNSIGNED NULL COMMENT 'Logical ref account.user.uid',
  updated_at              TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Runtime NitroBot controls managed by administrators';

INSERT INTO ai_admin_setting
  (id, daily_token_limit, ai_enabled, chat_enabled, agent_enabled, document_search_enabled,
   web_search_enabled, code_runner_enabled, skills_enabled, global_daily_token_limit,
   global_quota_enabled, moderation_mode)
VALUES (1, 50000, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1);

-- Content-moderation audit rows. The matched terms are kept; the surrounding text is not, so a
-- review row never becomes a second copy of the content the gate removed. `direction` records
-- whether the hit came from the user (INPUT, the only signal used for risk control), the model
-- answer (OUTPUT) or a memory write (MEMORY).
CREATE TABLE ai_moderation_hit (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  uid               BIGINT UNSIGNED  NOT NULL                    COMMENT 'Logical ref account.user.uid (JWT sub)',
  conversation_uuid CHAR(36)         NULL                        COMMENT 'Conversation that triggered the hit, when known',
  direction         TINYINT UNSIGNED NOT NULL                    COMMENT '0=INPUT,1=OUTPUT,2=MEMORY,3=PROFILE',
  action            TINYINT UNSIGNED NOT NULL                    COMMENT '0=LOGGED,1=BLOCKED,2=MASKED',
  words             VARCHAR(255)     NOT NULL                    COMMENT 'Distinct matched terms, comma separated',
  hit_count         INT UNSIGNED     NOT NULL DEFAULT 1          COMMENT 'Number of distinct matched terms',
  created_at        TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ai_moderation_hit_created (created_at),
  KEY idx_ai_moderation_hit_uid_created (uid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NitroBot content-moderation hits for administrator review';

-- ============================================================
-- Admin-managed agent skills (folded from V1_3). Built-in skills stay on the classpath (shipped
-- with the code); this table holds the ones created, imported or edited from the admin console.
-- A row whose name matches a built-in overrides it, and deleting the row restores the built-in.
-- ============================================================
CREATE TABLE ai_skill (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)     NOT NULL                         COMMENT 'stable identifier, lowercase kebab-case',
  description VARCHAR(255)    NOT NULL DEFAULT ''              COMMENT 'one line, shown in the catalog',
  when_to_use VARCHAR(255)    NOT NULL DEFAULT ''              COMMENT 'when the agent should load it',
  body        MEDIUMTEXT      NOT NULL                         COMMENT 'markdown instructions',
  origin      TINYINT UNSIGNED NOT NULL DEFAULT 0              COMMENT '0=FORM,1=IMPORT',
  updated_by  BIGINT UNSIGNED NULL                             COMMENT 'admin uid of the last writer',
  created_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_skill_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='admin-managed agent skills';