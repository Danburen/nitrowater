-- NitroBot daily token usage and runtime controls.
-- Usage day is a pure date (UTC); all event timestamps use TIMESTAMP(3).

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
  ai_enabled              TINYINT(1)      NOT NULL DEFAULT 1,
  chat_enabled            TINYINT(1)      NOT NULL DEFAULT 1,
  agent_enabled           TINYINT(1)      NOT NULL DEFAULT 1,
  document_search_enabled TINYINT(1)      NOT NULL DEFAULT 1,
  web_search_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  code_runner_enabled     TINYINT(1)      NOT NULL DEFAULT 1,
  skills_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
  updated_by_uid          BIGINT UNSIGNED NULL COMMENT 'Logical ref account.user.uid',
  updated_at              TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Runtime NitroBot controls managed by administrators';

INSERT INTO ai_admin_setting
  (id, daily_token_limit, ai_enabled, chat_enabled, agent_enabled, document_search_enabled,
   web_search_enabled, code_runner_enabled, skills_enabled)
VALUES (1, 50000, 1, 1, 1, 1, 1, 1, 1);
