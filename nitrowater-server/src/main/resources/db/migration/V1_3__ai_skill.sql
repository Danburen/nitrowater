-- Admin-managed agent skills. Built-in skills stay on the classpath (shipped with the code);
-- this table holds the ones created, imported or edited from the admin console. A row whose name
-- matches a built-in overrides it, and deleting the row restores the built-in.
CREATE TABLE ai_skill (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)     NOT NULL COMMENT 'stable identifier, lowercase kebab-case',
    description VARCHAR(255)    NOT NULL DEFAULT '' COMMENT 'one line, shown in the catalog',
    when_to_use VARCHAR(255)    NOT NULL DEFAULT '' COMMENT 'when the agent should load it',
    body        MEDIUMTEXT      NOT NULL COMMENT 'markdown instructions',
    origin      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=FORM,1=IMPORT',
    updated_by  BIGINT UNSIGNED NULL COMMENT 'admin uid of the last writer',
    created_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_skill_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'admin-managed agent skills';
