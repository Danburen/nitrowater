-- ============================================================
-- V1__sso_baseline.sql
-- nitrowater SSO 库基线（库名：nitrowater）
-- 对齐实体：cn.nitrowater.core.lib.entity.*
--    user / user_data / user_data_archive / account_audit_log / encryption_data_key
-- 说明：已剔除 waterfun 原表的业务列（avatar_resource_uuid 等）与业务 FK；
--       保留 user_data -> user 的级联 FK。
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
    uid                BIGINT UNSIGNED  NOT NULL,
    username           VARCHAR(32)      NOT NULL,
    password_hash      VARCHAR(255)     NULL,
    account_status     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=ACTIVE, 1=SUSPENDED, 2=DEACTIVATED',
    user_type          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=COMMON, 1=ADMIN, 2=...',
    level              TINYINT UNSIGNED NOT NULL DEFAULT 1,
    exp                INT UNSIGNED     NOT NULL DEFAULT 0,
    nickname           VARCHAR(12)      NULL,
    status_changed_at  TIMESTAMP(3)     NULL,
    last_active_at     TIMESTAMP(3)     NULL,
    created_at         TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3)     NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (uid),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 账号表（自 waterfun.user 拆出，凭据+基础资料）';

CREATE TABLE IF NOT EXISTS user_data (
    user_uid           BIGINT UNSIGNED  NOT NULL,
    email_encrypted    VARCHAR(128)     NULL,
    email_hash         VARCHAR(65)      NULL,
    email_verified     TINYINT(1)       NOT NULL DEFAULT 0,
    email_expire_at    TIMESTAMP(3)     NULL,
    phone_encrypted    VARCHAR(128)     NULL,
    phone_hash         VARCHAR(65)      NULL,
    phone_verified     TINYINT(1)       NOT NULL DEFAULT 0,
    encryption_key_id  VARCHAR(50)      NOT NULL,
    created_at         TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3)     NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_uid),
    CONSTRAINT fk_user_data_user FOREIGN KEY (user_uid) REFERENCES `user`(uid) ON DELETE CASCADE,
    KEY idx_verified_data (email_verified, phone_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户敏感数据（手机/邮箱 加密+哈希）';

CREATE TABLE IF NOT EXISTS user_data_archive (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_uid       BIGINT UNSIGNED NOT NULL,
    table_name     VARCHAR(50)     NOT NULL,
    original_data  JSON            NOT NULL,
    archived_at    TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    archived_by    VARCHAR(50)     NOT NULL,
    reason         VARCHAR(255)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_archive_user (user_uid),
    KEY idx_archive_time (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据归档';

CREATE TABLE IF NOT EXISTS account_audit_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_uid     BIGINT UNSIGNED NOT NULL,
    action       LONGTEXT        NOT NULL,
    action_time  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    performed_by VARCHAR(50)     NULL,
    ip_address   VARCHAR(45)     NULL,
    device_info  VARCHAR(255)    NULL,
    old_value    JSON            NULL,
    new_value    JSON            NULL,
    PRIMARY KEY (id),
    KEY idx_user_actions (user_uid, action_time),
    KEY idx_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号审计日志';

CREATE TABLE IF NOT EXISTS encryption_data_key (
    id            INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    key_id        VARCHAR(50)       NOT NULL,
    encrypted_key VARCHAR(512)      NOT NULL,
    algorithm     TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '0=AES, 1=SM4',
    key_length    SMALLINT UNSIGNED NOT NULL DEFAULT 256,
    key_status    TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '0=PENDING_ACTIVATION, 1=ACTIVE, 2=DECRYPT_ONLY, 3=SUSPENDED, 4=DEACTIVATED, 5=DESTROYED',
    key_purpose   VARCHAR(30)       NULL COMMENT 'AES_DATA / USER_HMAC / VERIFY_HMAC',
    created_at    TIMESTAMP(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    description   VARCHAR(255)      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_id (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段加密 DEK（KEK 加密存储）';
-- ============================================================
-- V2__sso_identity.sql
-- 登录身份绑定（预留第三方 OAuth2：QQ / 微信 / GitHub / Google …）
--
-- 设计：账号主体(user) 与 登录身份(sso_identity) 分离，
--       同一账号可绑定多种登录方式；未来接第三方只需往本表插行，
--       账号主表 user 无需改动。
--   - 本期仅建表预留，不接代码
--   - provider_user_id 存 openid / OAuth2 sub / 微信 unionid
-- ============================================================

CREATE TABLE IF NOT EXISTS sso_identity (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uid              BIGINT UNSIGNED NOT NULL COMMENT '关联 user.uid',
    provider         VARCHAR(30)     NOT NULL COMMENT 'PASSWORD/SMS/EMAIL/QQ/WECHAT/GITHUB/GOOGLE',
    provider_user_id VARCHAR(191)    NOT NULL COMMENT 'openid / OAuth2 sub / 微信 unionid',
    union_id         VARCHAR(191)    NULL COMMENT '微信 unionid（多端账号合一）',
    profile          JSON            NULL COMMENT '第三方返回的脱敏资料快照',
    created_at       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       TIMESTAMP(3)    NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_provider_user (provider, provider_user_id),
    KEY idx_identity_uid (uid),
    CONSTRAINT fk_identity_user FOREIGN KEY (uid) REFERENCES `user`(uid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录身份绑定（预留第三方 OAuth2）';

