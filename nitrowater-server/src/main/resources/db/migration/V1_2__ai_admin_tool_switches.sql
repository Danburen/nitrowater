-- Per-tool switches: tool names listed here stay registered (Spring AI callback) but are not
-- advertised to the model for the next turn. Empty array = every built-in tool follows its feature
-- switch (documentSearch / skills / codeRunner / webSearch).
ALTER TABLE ai_admin_setting
  ADD COLUMN disabled_tools VARCHAR(500) NOT NULL DEFAULT '[]'
  COMMENT 'JSON array of tool names hidden from the model' AFTER skills_enabled;
