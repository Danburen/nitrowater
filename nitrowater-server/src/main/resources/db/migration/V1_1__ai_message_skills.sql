-- Skill trace of one assistant message.
--
-- The live step timeline is in-memory only, but the collapsed process row of a settled bubble
-- must survive a reload; storing the names next to sources_json / tools_json keeps the whole
-- trace in one row. Existing rows stay NULL and render as "no skills".

ALTER TABLE ai_message
  ADD COLUMN skills_json TEXT NULL COMMENT 'Skill trace snapshot' AFTER tools_json;
