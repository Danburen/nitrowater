#!/usr/bin/env bash
# ============================================================
# nitrowater MySQL backup (Bash - Linux/macOS)
#
# Dumps all three schemas (account / biz / resource) from the running mysql
# container, gzips the result into ${BACKUP_DIR} and deletes dumps older than
# RETAIN_DAYS. The script runs on the host, so no mysql client is required:
# credentials are read from deploy/docker/.env (+ optional .env.prod.secrets)
# and passed to the container, never typed on a command line.
#
# Usage:
#   bash deploy/bin/backup_mysql.sh
#   BACKUP_DIR=/mnt/backup bash deploy/bin/backup_mysql.sh
#   crontab -e  ->  0 3 * * * /opt/nitrowater/deploy/bin/backup_mysql.sh >/dev/null 2>&1
#
# deploy.sh calls this before replacing containers; a non-zero exit aborts the deploy.
# Exit codes: 0 = dump written and verified; 1 = no fresh backup was produced.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$DEPLOY_ROOT/deploy/docker/.env"
SECRETS_FILE="$DEPLOY_ROOT/deploy/docker/.env.prod.secrets"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-nitrowater-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_ROOT/backups}"
RETAIN_DAYS="${RETAIN_DAYS:-30}"
DATABASES=(nitrowater_account nitrowater_biz nitrowater_resource)

# Read one KEY without sourcing .env: values may contain '#', '$' or spaces.
# Later files win, matching `docker compose --env-file` precedence.
env_get() {
    local key="$1" file line value=""
    for file in "$ENV_FILE" "$SECRETS_FILE"; do
        [ -f "$file" ] || continue
        line="$(sed -n "s/^[[:space:]]*${key}=//p" "$file" | tail -n 1)"
        [ -n "$line" ] && value="${line%$'\r'}"
    done
    printf '%s' "$value"
}

# Business account first (least privilege); root only when .env has no DB user.
DB_USER="${MYSQL_BACKUP_USER:-${DB_USERNAME:-$(env_get DB_USERNAME)}}"
DB_PASS="${MYSQL_BACKUP_PASSWORD:-${DB_PASSWORD:-$(env_get DB_PASSWORD)}}"
if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ]; then
    DB_USER="${MYSQL_BACKUP_USER:-root}"
    DB_PASS="${MYSQL_BACKUP_PASSWORD:-$(env_get MYSQL_ROOT_PASSWORD)}"
fi
if [ -z "$DB_PASS" ]; then
    echo "ERROR: no MySQL password in $ENV_FILE (DB_PASSWORD or MYSQL_ROOT_PASSWORD)" >&2
    exit 1
fi

# The container must exist and be running: a stopped mysql means the data files are
# not safely consistent, and a missing one means this is not a deployed host yet.
if ! docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" >/dev/null 2>&1; then
    echo "ERROR: container '$MYSQL_CONTAINER' not found (docker compose ... infra up -d)" >&2
    exit 1
fi
if [ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER")" != "true" ]; then
    echo "ERROR: container '$MYSQL_CONTAINER' is not running (its files are not consistent)" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

LOG_FILE="$BACKUP_DIR/backup.log"
STAMP="$(date +%Y%m%d_%H%M%S)"
FINAL_FILE="$BACKUP_DIR/nitrowater_all_${STAMP}.sql.gz"
TMP_FILE="$BACKUP_DIR/.nitrowater_all_${STAMP}.sql.gz.part"
trap 'rm -f "$TMP_FILE"' EXIT

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"; }

log "start: ${DATABASES[*]} from ${MYSQL_CONTAINER}"

# MYSQL_PWD keeps the password out of the container's process list (host docker
# access is already root-equivalent). --no-tablespaces avoids needing PROCESS.
if ! docker exec -e MYSQL_PWD="$DB_PASS" "$MYSQL_CONTAINER" \
        mysqldump -u "$DB_USER" -h 127.0.0.1 \
        --single-transaction --routines --triggers --no-tablespaces \
        --default-character-set=utf8mb4 \
        --databases "${DATABASES[@]}" \
        2>> "$LOG_FILE" | gzip > "$TMP_FILE"; then
    rm -f "$TMP_FILE"
    log "FAILED: mysqldump returned non-zero (log: ${LOG_FILE})"
    exit 1
fi

# A truncated pipe or an unknown schema still writes a "valid looking" file, so
# check gzip integrity and that every expected schema header made it in.
if ! gzip -t "$TMP_FILE" 2>/dev/null; then
    rm -f "$TMP_FILE"
    log 'FAILED: gzip integrity check'
    exit 1
fi

size="$(wc -c < "$TMP_FILE" | tr -d '[:space:]')"
schemas="$(gzip -dc "$TMP_FILE" | grep -c '^CREATE DATABASE' || true)"
if [ "$size" -lt 4096 ] || [ "${schemas:-0}" -lt "${#DATABASES[@]}" ]; then
    rm -f "$TMP_FILE"
    log "FAILED: incomplete dump (${size} bytes, ${schemas:-0}/${#DATABASES[@]} schemas)"
    exit 1
fi

mv "$TMP_FILE" "$FINAL_FILE"
chmod 600 "$FINAL_FILE"
log "done: ${FINAL_FILE} (${size} bytes, ${schemas} schemas)"

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'nitrowater_all_*.sql.gz' -mtime +"$RETAIN_DAYS" -delete
