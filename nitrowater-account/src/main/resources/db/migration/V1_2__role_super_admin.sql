-- ============================================================
-- V1_2__role_super_admin.sql
-- 版本约束：本迁移依赖版本 1 的基线演进（1 / 1.1）追加，不修改已应用的 V1_1。
-- 变更内容：
--   追加内置角色 SUPER_ADMIN（站长），可管理全部 ADMIN 与全部资源。
--   角色词表仍为扁平结构（code/name/builtin），不引入 permission 表、不引入 level 列；
--   “谁管谁”由安全链（/api/admin/** 仅 ROLE_SUPER_ADMIN 可访问）表达。
-- 说明：SUPER_ADMIN 在 SsoUserPrincipal#getRoles() 中自动蕴含 ROLE_ADMIN，
--       因此持 SUPER_ADMIN 的账号同时具备现有所有 ROLE_ADMIN 权限，无需逐处改动。
-- 幂等：INSERT IGNORE（code 唯一键），重复启动/重放均安全。
-- ============================================================

INSERT IGNORE INTO `role`(code, name, description, builtin) VALUES
  ('SUPER_ADMIN', '超级管理员', '站长：管理全部管理员与资源', 1);
