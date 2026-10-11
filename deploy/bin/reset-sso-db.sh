#!/usr/bin/env bash
# ============================================================
# reset-sso-db.sh — 清空 SSO 表，让 Flyway 下次启动按 V1 重建
# 用途：初期开发直接改 V1__sso_baseline.sql 后，清库重建
#
# 用法:
#   bash deploy/bin/reset-sso-db.sh
#   DB_PASS=yourpass bash deploy/bin/reset-sso-db.sh
# ============================================================
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_NAME="${DB_NAME:-nitrowater_account}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

SQL='SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS oauth2_authorization_consent, oauth2_authorization, oauth2_registered_client, user_role, `role`, sso_identity, user_data, user_data_archive, account_audit_log, encryption_data_key, user, flyway_schema_history;
SET FOREIGN_KEY_CHECKS=1;'

MYSQL_PWD="$DB_PASS" "$MYSQL_BIN" -h "$DB_HOST" -u "$DB_USER" "$DB_NAME" -e "$SQL"

echo "[OK] SSO tables dropped from '$DB_NAME'."
echo "     Restart account (bootRun / bin/start-account.bat) -> Flyway rebuilds from V1."
