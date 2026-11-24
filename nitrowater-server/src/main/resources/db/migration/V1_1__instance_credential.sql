-- ============================================================
-- nitrowater_biz · V1_1 (VeloChatX remote monitoring: api-key credential / instance registry / command audit)
-- Conventions (AGENTS.md): timestamps TIMESTAMP(3); enums TINYINT UNSIGNED with encoded COMMENT;
--   unique keys uk_*, indexes idx_*; singular table names; ENGINE=InnoDB CHARSET=utf8mb4.
-- Cross-service references (owner_uid -> biz_user.uid / account.user.uid) are logical only, no FK.
-- This module stores identity / ownership / management metadata only: monitoring payloads and
--   status snapshots stay in memory (see docs/velochatx-remote-integration.md).
-- Note: the V1 baseline comment mentions folded-in V1_1..V1_4 (skills_json etc.); those files were
--   deleted and merged into V1, so version 1.1 is currently free. A dev database that still keeps
--   the pre-fold V1_1 history row must drop the schema and let Flyway replay.
-- ============================================================

-- User-level api-key. Only the SHA-256 hash is stored; the key is NOT bound to an instance id.
CREATE TABLE instance_credential (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  key_id       VARCHAR(64)  NOT NULL                          COMMENT 'kid, display id',
  key_hash     CHAR(64)     NOT NULL                          COMMENT 'SHA-256(api-key) hex, auth lookup key',
  key_prefix   VARCHAR(16)  NOT NULL                          COMMENT 'nw_live_ + first 4 plaintext chars, for display/debug',
  key_last4    VARCHAR(8)   NOT NULL                          COMMENT 'last 4 plaintext chars',
  scopes       VARCHAR(256) NOT NULL DEFAULT 'telemetry:write,rpc:execute' COMMENT 'comma separated scope codes',
  status       TINYINT UNSIGNED NOT NULL DEFAULT 0            COMMENT '0=ACTIVE 1=ROTATED 2=REVOKED',
  owner_uid    BIGINT UNSIGNED NULL                           COMMENT 'logical ref biz_user.uid; key fixes the owner, not an instance',
  remark       VARCHAR(255) NULL                              COMMENT 'issuance note (purpose / machine hint)',
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at   TIMESTAMP(3) NULL,
  last_used_at TIMESTAMP(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_credential_kid (key_id),
  UNIQUE KEY uk_instance_credential_hash (key_hash),
  KEY idx_instance_credential_owner (owner_uid, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VeloChatX user-level api-key (hash only; not bound to an instance)';

-- Instance registry: one key owns at most 3 live V instances (InstanceLimits.MAX_INSTANCES_PER_KEY).
-- Identity is the (key_id, instance_id) pair: the key fixes the user, the UUID fixes the machine.
CREATE TABLE instance_registry (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  key_id        VARCHAR(64)  NOT NULL                         COMMENT 'logical ref instance_credential.key_id',
  instance_id   VARCHAR(128) NOT NULL                         COMMENT 'V-generated UUID',
  display_name  VARCHAR(64)  NOT NULL                         COMMENT 'generated on first bind, user-editable; unique per owner',
  owner_uid     BIGINT UNSIGNED NULL                          COMMENT 'logical ref biz_user.uid',
  boot_id       VARCHAR(64)  NULL                             COMMENT 'last reported process UUID, display/debug only',
  first_seen_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  removed_at    TIMESTAMP(3) NULL                             COMMENT 'soft delete; non-NULL does not consume a slot',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_registry_key_instance (key_id, instance_id),
  KEY idx_instance_registry_owner_name (owner_uid, display_name),
  KEY idx_instance_registry_key_active (key_id, removed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VeloChatX instance registry (ownership / management metadata)';

-- Command audit: metadata only, never parameters or status payloads.
CREATE TABLE instance_command_audit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  command_id  VARCHAR(64)  NOT NULL,
  key_id      VARCHAR(64)  NULL                               COMMENT 'logical ref instance_credential.key_id',
  instance_id VARCHAR(128) NOT NULL,
  method      VARCHAR(64)  NOT NULL,
  issued_by   BIGINT UNSIGNED NULL                            COMMENT 'logical ref account.user.uid',
  result      TINYINT UNSIGNED NULL                           COMMENT '0=OK 1=ERROR 2=TIMEOUT',
  issued_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_command_audit_id (command_id),
  KEY idx_instance_command_audit_instance (instance_id, issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Command audit (metadata only, no params / status)';
