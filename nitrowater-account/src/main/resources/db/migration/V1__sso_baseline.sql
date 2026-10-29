-- ============================================================
-- V1__sso_baseline.sql
-- nitrowater SSO 库基线（库名：nitrowater_account）
--
-- 单一基线：本文件已折叠原 V1_1（OIDC + RBAC）、V1_2（SUPER_ADMIN）、
-- V1_3（手机/邮箱 hash 全局唯一 + user_type 注释）。dev 阶段允许改基线 + 重置库；
-- 已上线后如需变更，必须新增 V1_1__... 子版本，不能再改本文件。
--
-- 对齐实体：cn.nitrowater.core.lib.entity.*
--    user / user_data / user_data_archive / account_audit_log / encryption_data_key
-- 说明：已剔除 waterfun 原表的业务关联列（post_resource 等）与业务 FK；
--       avatar_resource_uuid 按新设计保留（引用 nitrowater-resource 的 resource.uuid，OIDC picture 由此派生）；
--       保留 user_data -> user 的级联 FK。
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
    uid                BIGINT UNSIGNED  NOT NULL,
    username           VARCHAR(32)      NOT NULL,
    password_hash      VARCHAR(255)     NULL,
    account_status     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=ACTIVE, 1=SUSPENDED, 2=DEACTIVATED',
    user_type          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=COMMON,1=ADMIN,2=BOT,3=MODERATOR,4=VIP,5=SYSTEM,6=TEST',
    level              TINYINT UNSIGNED NOT NULL DEFAULT 1,
    exp                INT UNSIGNED     NOT NULL DEFAULT 0,
    nickname           VARCHAR(12)      NULL,
    avatar_resource_uuid CHAR(36)       NULL COMMENT '头像资源uuid(资源服务resource.uuid；OIDC picture 由此派生)',
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
    KEY idx_verified_data (email_verified, phone_verified),
    -- One account per phone/email. MySQL unique indexes permit multiple NULLs, so a
    -- password-only SYSTEM/TEST account without either target remains valid.
    UNIQUE KEY uk_user_data_phone_hash (phone_hash),
    UNIQUE KEY uk_user_data_email_hash (email_hash)
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
-- sso_identity —— 登录身份绑定（预留第三方 OAuth2：QQ / 微信 / GitHub / Google …）
-- （原独立迁移 V2__sso_identity.sql，已并入本基线）
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

-- ============================================================
-- OIDC 持久化（原 V1_1）
-- Spring Authorization Server 7.1.1 官方 DDL，由 JdbcRegisteredClientRepository /
-- JdbcOAuth2AuthorizationService / JdbcOAuth2AuthorizationConsentService 直接读写。
-- 注意：这三张表是 vendor schema，保留上游裸 timestamp 列，不套项目 TIMESTAMP(3) 约定。
-- ============================================================

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            varchar(100)  NOT NULL,
    client_id                     varchar(100)  NOT NULL,
    client_id_issued_at           timestamp     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 varchar(200)  DEFAULT NULL,
    client_secret_expires_at      timestamp     DEFAULT NULL,
    client_name                   varchar(200)  NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types     varchar(1000) NOT NULL,
    redirect_uris                 varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris     varchar(1000) DEFAULT NULL,
    scopes                        varchar(1000) NOT NULL,
    client_settings               varchar(2000) NOT NULL,
    token_settings                varchar(2000) NOT NULL,
    PRIMARY KEY (id),
    KEY oauth2_registered_client_client_id_idx (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OIDC 接入客户端（Spring AS 标准表）';

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            varchar(100)  NOT NULL,
    registered_client_id          varchar(100)  NOT NULL,
    principal_name                varchar(200)  NOT NULL,
    authorization_grant_type      varchar(100)  NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    blob          DEFAULT NULL,
    state                         varchar(500)  DEFAULT NULL,
    authorization_code_value      blob          DEFAULT NULL,
    authorization_code_issued_at  timestamp     DEFAULT NULL,
    authorization_code_expires_at timestamp     DEFAULT NULL,
    authorization_code_metadata   blob          DEFAULT NULL,
    access_token_value            blob          DEFAULT NULL,
    access_token_issued_at        timestamp     DEFAULT NULL,
    access_token_expires_at       timestamp     DEFAULT NULL,
    access_token_metadata         blob          DEFAULT NULL,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,
    oidc_id_token_value           blob          DEFAULT NULL,
    oidc_id_token_issued_at       timestamp     DEFAULT NULL,
    oidc_id_token_expires_at      timestamp     DEFAULT NULL,
    oidc_id_token_metadata        blob          DEFAULT NULL,
    refresh_token_value           blob          DEFAULT NULL,
    refresh_token_issued_at       timestamp     DEFAULT NULL,
    refresh_token_expires_at      timestamp     DEFAULT NULL,
    refresh_token_metadata        blob          DEFAULT NULL,
    user_code_value               blob          DEFAULT NULL,
    user_code_issued_at           timestamp     DEFAULT NULL,
    user_code_expires_at          timestamp     DEFAULT NULL,
    user_code_metadata            blob          DEFAULT NULL,
    device_code_value             blob          DEFAULT NULL,
    device_code_issued_at         timestamp     DEFAULT NULL,
    device_code_expires_at        timestamp     DEFAULT NULL,
    device_code_metadata          blob          DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OIDC 授权状态（Spring AS 标准表）';

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OIDC 授权同意（Spring AS 标准表）';

-- ============================================================
-- RBAC（原 V1_1 + V1_2）
-- role 是 SSO 粗粒度角色词汇表，user_role 是用户映射；user.user_type 仅表达账号类别。
-- `role` 是 MySQL 8 保留字，必须反引号。
-- ============================================================

CREATE TABLE IF NOT EXISTS `role` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL COMMENT '角色码：ADMIN / USER / ...',
    name        VARCHAR(64)     NOT NULL COMMENT '显示名',
    description VARCHAR(255)    NULL,
    builtin     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '内置角色不可删',
    created_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  TIMESTAMP(3)    NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色（SSO 粗粒度）';

CREATE TABLE IF NOT EXISTS `user_role` (
    uid        BIGINT UNSIGNED NOT NULL COMMENT '关联 user.uid',
    role_id    BIGINT UNSIGNED NOT NULL COMMENT '关联 role.id',
    granted_at TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (uid, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (uid)     REFERENCES `user`(uid) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES `role`(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色映射';

-- 内置角色（幂等）
INSERT IGNORE INTO `role`(code, name, description, builtin) VALUES
  ('USER',        '普通用户',   '默认角色',                 1),
  ('ADMIN',       '管理员',     '后台管理员',               1),
  ('SUPER_ADMIN', '超级管理员', '站长：管理全部管理员与资源', 1);

