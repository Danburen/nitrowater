#!/usr/bin/env bash
# ============================================================
# reset-sso-db.sh — 清空 SSO 表，让 Flyway 下次启动按 V1 重建
# 用途：初期开发直接改 V1__sso_baseline.sql 后，清库重建
#
# 用法:
#   bash deploy/bin/reset-sso-db.sh
#   DB_PASS=yourpass bash deploy/bin/reset-sso-db.sh
#   bash deploy/bin/reset-sso-db.sh --force    # 跳过 YES 确认（危险！防止误跑）
# ============================================================
set -euo pipefail

FORCE=0
for arg in "$@"; do
    case "$arg" in
        --force|-f) FORCE=1 ;;
        *) echo "unknown argument: $arg (use --force)"; exit 2 ;;
    esac
done

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_NAME="${DB_NAME:-nitrowater_account}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

echo "=== target ==="
echo "  db: $DB_NAME @ $DB_HOST user $DB_USER"
if [ "$FORCE" -ne 1 ]; then
    read -r -p "This DROPS ALL tables in '$DB_NAME' (data loss!). Type YES to continue: " ans
    if [ "$ans" != "YES" ]; then echo "[ABORT] cancelled. (use --force to skip this prompt)"; exit 1; fi
    echo "  confirmed, dropping tables in '$DB_NAME' ..."
fi

SQL='SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS oauth2_authorization_consent, oauth2_authorization, oauth2_registered_client, user_role, `role`, sso_identity, user_data, user_data_archive, account_audit_log, encryption_data_key, user, flyway_schema_history;
SET FOREIGN_KEY_CHECKS=1;'

MYSQL_PWD="$DB_PASS" "$MYSQL_BIN" -h "$DB_HOST" -u "$DB_USER" "$DB_NAME" -e "$SQL"

echo "[OK] SSO tables dropped from '$DB_NAME'."
echo "     Restart account (bootRun / bin/start-all.ps1) -> Flyway rebuilds from V1."
