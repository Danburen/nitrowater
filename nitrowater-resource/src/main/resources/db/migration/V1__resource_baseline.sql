-- ============================================================
-- nitrowater_resource · V1 baseline（统一资源元数据）
-- 枚举一律 tinyint UNSIGNED（应用层 Java 枚举 + @Converter 映射）
-- 库内仅此服务使用；跨库对外仅暴露 owner_uid（逻辑关联 account）。
-- 首次部署前可直接改本文件；部署后冻结，后续走 V2__...
-- ============================================================

CREATE TABLE resource (
  id            BIGINT           NOT NULL AUTO_INCREMENT,
  uuid          CHAR(36)         NOT NULL                                  COMMENT '对外不可猜标识',
  app           VARCHAR(32)      NOT NULL                                  COMMENT '应用/命名空间（对象键前缀），如 nitrowater',
  group_key     VARCHAR(64)      NULL                                      COMMENT '分组键（如插件 key），用于按组列举；与 app 联合',
  backend       TINYINT UNSIGNED NOT NULL DEFAULT 0                        COMMENT '0=S3, 1=COS, 2=LOCAL',
  bucket_key    VARCHAR(512)     NOT NULL                                  COMMENT '对象存储 key 或本地相对路径',
  visibility    TINYINT UNSIGNED NOT NULL DEFAULT 0                        COMMENT '0=PRIVATE, 1=PUBLIC',
  resource_type TINYINT UNSIGNED NOT NULL DEFAULT 0                        COMMENT '0=UNKNOWN,1=IMAGE,2=VIDEO,3=AUDIO,4=TEXT,5=DOCUMENT,6=ARCHIVE,7=EXECUTABLE,99=OTHER',
  mime_type     VARCHAR(128)     NULL,
  size_bytes    BIGINT           NULL,
  original_name VARCHAR(255)     NULL                                      COMMENT '原始文件名（展示名 / 下载名）',
  source_type   TINYINT UNSIGNED NOT NULL DEFAULT 2                        COMMENT '0=SYSTEM,1=CONTENT_ATTACHED,2=USER_UPLOADED',
  owner_uid     BIGINT           NULL                                      COMMENT '上传者 uid（逻辑关联 account，不建跨库 FK）',
  status        TINYINT UNSIGNED NOT NULL DEFAULT 0                        COMMENT '0=PENDING,1=ACTIVE,2=ORPHAN,3=DELETED',
  file_meta     JSON             NULL                                      COMMENT '文件信息 JSON，如 {"version":"2.0.8"}',
  created_at    TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_uuid (uuid),
  KEY idx_owner (owner_uid),
  KEY idx_app (app),
  KEY idx_app_group (app, group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
