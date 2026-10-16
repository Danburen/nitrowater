-- ============================================================
-- nitrowater_biz · V1 baseline（业务库：用户镜像 / 角色 / 题库 / 提交 / 积分）
-- 约定（见 AGENTS.md）：时间列一律 TIMESTAMP(3)；枚举一律 TINYINT UNSIGNED（应用层 @Converter 映射）；
--   库内 FK fk_*、唯一键 uk_*、索引 idx_*；表名单数；ENGINE=InnoDB CHARSET=utf8mb4。
-- 与 SSO 库 nitrowater_account 物理隔离：仅以 uid / *_resource_uuid 逻辑关联，不建跨库 FK。
-- 首次部署前可直接改本文件；部署后冻结，后续走 V2__...
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
  points                INT             NOT NULL DEFAULT 0                        COMMENT '积分余额缓存（权威在 user_point_log）',
  status                TINYINT UNSIGNED NOT NULL DEFAULT 0                       COMMENT '0=ACTIVE 1=SUSPENDED',
  created_at            TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_user_uid (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务用户镜像（uid 逻辑关联 account）';

-- ---------- 应用内角色（与 account 全局角色分开）----------
CREATE TABLE biz_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(64)     NOT NULL                       COMMENT 'e.g. PROBLEM_ADMIN / CONTESTANT',
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

-- ---------- 题库（题面双语：直列 _en 列，缺省回退中文）----------
CREATE TABLE problem (
  id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  slug            VARCHAR(64)      NOT NULL                       COMMENT '稳定可读 ID',
  title           VARCHAR(200)     NOT NULL                       COMMENT '中文标题',
  title_en        VARCHAR(200)     NULL                           COMMENT '英文标题（缺省回退中文）',
  difficulty      TINYINT UNSIGNED NOT NULL DEFAULT 0             COMMENT '0=EASY 1=MEDIUM 2=HARD',
  description     MEDIUMTEXT       NOT NULL                       COMMENT '中文题面 markdown',
  description_en  MEDIUMTEXT       NULL                           COMMENT '英文题面 markdown',
  io_mode         TINYINT UNSIGNED NOT NULL DEFAULT 0             COMMENT '0=ACM 1=LEETCODE',
  judge_config    JSON             NULL                           COMMENT '比较策略/方法签名(LC)',
  templates       JSON             NULL                           COMMENT '各语言起始模板',
  time_limit_ms   INT              NOT NULL DEFAULT 5000,
  memory_limit_kb INT              NOT NULL DEFAULT 262144,
  reward_points   INT              NOT NULL DEFAULT 0             COMMENT '首次 AC 奖励积分',
  enabled         TINYINT(1)       NOT NULL DEFAULT 1,
  created_by      BIGINT UNSIGNED  NULL                           COMMENT '出题人 uid（逻辑）',
  created_at      TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_problem_slug (slug),
  KEY idx_problem_enabled (enabled),
  KEY idx_problem_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库（题面双语直列 _en）';

-- ---------- 标签（双语直列；kind 分组：主题/公司/分类）----------
CREATE TABLE tag (
  id         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  slug       VARCHAR(64)      NOT NULL                       COMMENT '稳定键，如 array / dynamic-programming',
  name       VARCHAR(64)      NOT NULL                       COMMENT '中文名',
  name_en    VARCHAR(64)      NULL                           COMMENT '英文名（缺省回退中文）',
  kind       TINYINT UNSIGNED NOT NULL DEFAULT 0             COMMENT '0=TOPIC 1=COMPANY',
  color      VARCHAR(16)      NULL,
  enabled    TINYINT(1)       NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_slug (slug),
  KEY idx_tag_kind (kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库标签（双语直列，kind: 0=TOPIC 1=COMPANY）';

CREATE TABLE problem_tag (
  problem_id BIGINT UNSIGNED NOT NULL,
  tag_id     BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (problem_id, tag_id),
  CONSTRAINT fk_pt_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE,
  CONSTRAINT fk_pt_tag     FOREIGN KEY (tag_id)     REFERENCES tag(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-标签 映射';

-- ---------- 题单（problem set：官方 curated + 用户自建“我的题单”）----------
-- 归属：owner_uid NULL = 官方/curated；非空 = 用户 uid（跨库逻辑键，不建 FK）。
-- 可见性 visibility：0=PRIVATE 1=PUBLIC（仅对用户题单有意义）。
-- 官方题单需稳定 slug（唯一）；用户题单 slug 为 NULL（唯一索引允许多个 NULL）。
CREATE TABLE problem_set (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug           VARCHAR(64)  NULL                           COMMENT '稳定键，如 hot-100 / interview-150（仅官方题单；用户题单为 NULL）',
  owner_uid      BIGINT UNSIGNED NULL                        COMMENT '题单归属 uid（NULL=官方；跨库逻辑键，无 FK）',
  visibility     TINYINT UNSIGNED NOT NULL DEFAULT 0         COMMENT '可见性：0=PRIVATE 1=PUBLIC（仅对用户题单有意义）',
  name           VARCHAR(100) NOT NULL                       COMMENT '中文名，如 热门题目',
  name_en        VARCHAR(100) NULL                           COMMENT '英文名（缺省回退中文）',
  description    VARCHAR(500) NULL                           COMMENT '中文简介',
  description_en VARCHAR(500) NULL                           COMMENT '英文简介（缺省回退中文）',
  color          VARCHAR(16)  NULL,
  sort_order     INT          NOT NULL DEFAULT 0             COMMENT '题单展示顺序（升序）',
  enabled        TINYINT(1)   NOT NULL DEFAULT 1,
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_problem_set_slug (slug),
  KEY idx_problem_set_enabled (enabled),
  KEY idx_problem_set_owner (owner_uid, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题单/problem set（owner_uid NULL=官方；非空=用户题单；一道题可属于多个题单）';

CREATE TABLE problem_set_item (
  problem_id BIGINT UNSIGNED NOT NULL,
  set_id     BIGINT UNSIGNED NOT NULL,
  ord        INT NOT NULL DEFAULT 0 COMMENT '题单内顺序（升序）',
  PRIMARY KEY (problem_id, set_id),
  KEY idx_psi_set (set_id, ord),
  CONSTRAINT fk_psi_problem FOREIGN KEY (problem_id) REFERENCES problem(id)     ON DELETE CASCADE,
  CONSTRAINT fk_psi_set     FOREIGN KEY (set_id)     REFERENCES problem_set(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-题单 映射';

CREATE TABLE problem_test_case (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  problem_id      BIGINT UNSIGNED NOT NULL,
  idx             INT             NOT NULL                        COMMENT '用例序号（0 基）',
  stdin           MEDIUMTEXT      NULL,
  expected_output MEDIUMTEXT      NULL,
  is_sample       TINYINT(1)      NOT NULL DEFAULT 0              COMMENT '1=展示给用户的样例',
  score           INT             NOT NULL DEFAULT 0              COMMENT '部分分（可选）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_problem_idx (problem_id, idx),
  CONSTRAINT fk_case_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库用例';

-- ---------- 题解（1:N，仅中文；全部可见；官方置顶；预留用户发布）----------
CREATE TABLE problem_editorial (
  id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  problem_id   BIGINT UNSIGNED  NOT NULL,
  author_uid   BIGINT UNSIGNED  NULL                       COMMENT '逻辑关联 account.uid（官方=出题 admin uid）',
  author_type  TINYINT UNSIGNED NOT NULL DEFAULT 0         COMMENT '0=OFFICIAL 1=USER',
  title        VARCHAR(200)     NOT NULL,
  content      MEDIUMTEXT       NOT NULL                   COMMENT 'markdown 正文（中文）',
  language     TINYINT UNSIGNED NULL                       COMMENT '题解代码语言，同 submission.language（0=JAVA…6=LUA）',
  status       TINYINT UNSIGNED NOT NULL DEFAULT 0         COMMENT '0=DRAFT 1=PUBLISHED 2=HIDDEN',
  pinned       TINYINT(1)       NOT NULL DEFAULT 0         COMMENT '置顶（官方）',
  like_count   INT              NOT NULL DEFAULT 0         COMMENT '点赞数（预留）',
  view_count   INT              NOT NULL DEFAULT 0         COMMENT '浏览数（预留）',
  published_at TIMESTAMP(3)     NULL,
  created_at   TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pe_problem (problem_id, status, pinned, published_at),
  KEY idx_pe_author (author_uid),
  CONSTRAINT fk_pe_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题解（author_type:0=OFFICIAL 1=USER；status:0=DRAFT 1=PUBLISHED 2=HIDDEN）';

-- ---------- 提交/判题 ----------
CREATE TABLE submission (
  id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED  NOT NULL                       COMMENT 'FK biz_user.id',
  uid             BIGINT UNSIGNED  NOT NULL                       COMMENT '冗余：便于无 join 审计/查询',
  problem_id      BIGINT UNSIGNED  NOT NULL,
  language        TINYINT UNSIGNED NOT NULL                       COMMENT '0=JAVA 1=C 2=CPP 3=PYTHON 4=JAVASCRIPT 5=TYPESCRIPT 6=LUA（追加式）',
  io_mode         TINYINT UNSIGNED NOT NULL                       COMMENT '0=ACM 1=LEETCODE',
  code            MEDIUMTEXT       NOT NULL,
  status          TINYINT UNSIGNED NOT NULL DEFAULT 0             COMMENT '0=QUEUED 1=RUNNING 2=DONE 3=SE',
  verdict         TINYINT UNSIGNED NULL                           COMMENT '0=AC 1=WA 2=TLE 3=MLE 4=RE 5=CE 6=OLE 7=SE（NULL=未判）',
  passed          INT              NOT NULL DEFAULT 0,
  total           INT              NOT NULL DEFAULT 0,
  score           INT              NOT NULL DEFAULT 0,
  time_ms         INT              NULL,
  memory_kb       INT              NULL,
  case_results    JSON             NULL                           COMMENT '每用例 {index,verdict,timeMs,memoryKb,message}',
  compile_message TEXT             NULL                           COMMENT 'CE 摘要（截断/转义）',
  created_at      TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at     TIMESTAMP(3)     NULL,
  PRIMARY KEY (id),
  KEY idx_sub_user_created (user_id, created_at),
  KEY idx_sub_problem_created (problem_id, created_at),
  KEY idx_sub_problem_user_verdict (problem_id, user_id, verdict),
  CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES biz_user(id),
  CONSTRAINT fk_sub_problem FOREIGN KEY (problem_id) REFERENCES problem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题提交与判题结果';

-- ---------- 用户×题目 状态（聚合）----------
CREATE TABLE user_problem_status (
  user_id            BIGINT UNSIGNED  NOT NULL,
  problem_id         BIGINT UNSIGNED  NOT NULL,
  status             TINYINT UNSIGNED NOT NULL DEFAULT 0           COMMENT '0=ATTEMPTED 1=SOLVED',
  best_verdict       TINYINT UNSIGNED NULL                         COMMENT '同 submission.verdict',
  best_score         INT              NOT NULL DEFAULT 0,
  attempts           INT              NOT NULL DEFAULT 0,
  first_solved_at    TIMESTAMP(3)     NULL,
  last_submission_at TIMESTAMP(3)     NULL,
  PRIMARY KEY (user_id, problem_id),
  CONSTRAINT fk_ups_user FOREIGN KEY (user_id) REFERENCES biz_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_ups_problem FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户×题目聚合状态（已解决/进度）';

-- ---------- 积分流水 ----------
CREATE TABLE user_point_log (
  id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED  NOT NULL,
  delta         INT              NOT NULL                          COMMENT '正/负',
  reason        TINYINT UNSIGNED NOT NULL                          COMMENT '0=SOLVE 1=CONTEST 2=SIGN_IN 3=ADMIN',
  ref_type      TINYINT UNSIGNED NULL                              COMMENT '0=PROBLEM 1=SUBMISSION 2=CONTEST',
  ref_id        BIGINT UNSIGNED  NULL,
  balance_after INT              NOT NULL                          COMMENT '冗余快照，便于对账',
  remark        VARCHAR(255)     NULL,
  created_at    TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_point_idempotent (user_id, reason, ref_type, ref_id),
  KEY idx_point_user_created (user_id, created_at),
  CONSTRAINT fk_point_user FOREIGN KEY (user_id) REFERENCES biz_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水（幂等：首次 AC 只发一次）';

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
