-- ============================================================
-- V1_1__oauth2_oidc_role.sql
-- 版本约定：主版本 1 下用子版本递进（1 / 1.1 / 1.2 …），不轻易升 V2（仅在破坏性 schema 重构时）。
-- 本迁移 = Phase 2 schema，含两部分：
--   (1) Phase 2.5：OIDC 客户端 / 授权 / 同意 持久化（方案 A：Spring Authorization Server 标准表）
--       来源：spring-security-oauth2-authorization-server 7.1.1 官方 DDL
--         - oauth2-registered-client-schema.sql
--         - oauth2-authorization-schema.sql
--         - oauth2-authorization-consent-schema.sql
--       说明：由 JdbcRegisteredClientRepository / JdbcOAuth2AuthorizationService /
--             JdbcOAuth2AuthorizationConsentService 直接读写；客户端由应用启动时幂等播种。
--   (2) RBAC：role / user_role（SSO 粗粒度角色词汇表 + 用户映射，见文末）
-- MySQL 时间精度：连接串需带 preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
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
-- RBAC：角色 / 用户-角色映射（SSO 粗粒度角色）
-- 设计：role 为角色词汇表；user_role 为用户映射。细粒度权限(permission)暂不建，
--       待出现"角色→权限"消费方时，以新子版本迁移追加。
-- 权威源：role/user_role 为角色权威源；user.userType 退为账号类别/展示
--        （后续 SsoUserPrincipal 由 user_role 派生 roles claim）。
-- 注：`role` 是 MySQL 8 保留字，必须反引号（与库内 `user` 一致）。
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

-- 内置角色播种（幂等）
INSERT IGNORE INTO `role`(code, name, builtin) VALUES
  ('USER',  '普通用户', 1),
  ('ADMIN', '管理员',   1);
