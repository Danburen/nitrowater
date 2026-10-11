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

-- ---------- 题库 ----------
CREATE TABLE problem (
  id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  slug            VARCHAR(64)      NOT NULL                       COMMENT '稳定可读 ID',
  title           VARCHAR(200)     NOT NULL,
  difficulty      TINYINT UNSIGNED NOT NULL DEFAULT 0             COMMENT '0=EASY 1=MEDIUM 2=HARD',
  category        VARCHAR(64)      NULL,
  tags            JSON             NULL,
  description     MEDIUMTEXT       NOT NULL                       COMMENT 'markdown 题面',
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
  KEY idx_problem_difficulty (difficulty),
  KEY idx_problem_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

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
