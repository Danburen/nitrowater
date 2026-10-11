#!/usr/bin/env bash
# ============================================================
# nitrowater MySQL 自动备份（Bash — Linux/macOS）
# 全量备份 nitrowater_account，gzip 压缩，保留最近 30 天。
#
# 用法:
#   手动:  bash deploy/bin/backup_mysql.sh
#   定时:  crontab -e  ->  0 3 * * * /path/nitrowater/deploy/bin/backup_mysql.sh >/dev/null 2>&1
# ============================================================
set -euo pipefail

# ----- 配置区（按需修改）-----
MYSQL_BIN="${MYSQL_BIN:-/usr/bin}"
BACKUP_DIR="${BACKUP_DIR:-/data/backup/nitrowater}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-nitrowater_account}"
RETAIN_DAYS="${RETAIN_DAYS:-30}"
# -----------------------------

LOG_FILE="${BACKUP_DIR}/backup_log.txt"
DATE_SUFFIX="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${DATE_SUFFIX}.sql"

mkdir -p "$BACKUP_DIR"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始备份 ${DB_NAME}..." >> "$LOG_FILE"

if "${MYSQL_BIN}/mysqldump" \
        -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
        --single-transaction --routines --triggers --default-character-set=utf8mb4 \
        "$DB_NAME" > "$BACKUP_FILE" 2>> "$LOG_FILE"; then
    gzip -f "$BACKUP_FILE"
    BACKUP_FILE="${BACKUP_FILE}.gz"
    find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -type f -mtime +"${RETAIN_DAYS}" -delete
    msg="[$(date '+%Y-%m-%d %H:%M:%S')] ✓ 备份完成：${BACKUP_FILE}"
    echo "$msg" | tee -a "$LOG_FILE"
else
    msg="[$(date '+%Y-%m-%d %H:%M:%S')] ✗ 备份失败（见 ${LOG_FILE}）"
    echo "$msg" | tee -a "$LOG_FILE"
    exit 1
fi
