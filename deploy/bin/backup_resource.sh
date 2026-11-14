#!/usr/bin/env bash
# ============================================================
# nitrowater resource backup (Bash - Linux/macOS)
#
# Mirrors the resource data directory (RESOURCE_DATA_DIR, the bind mount of
# /data/resource) into ${BACKUP_DIR}/resource/<timestamp>/ and drops snapshots
# older than RETAIN_DAYS. The resource table only holds metadata, so losing this
# directory turns every download into a 404 - it must be backed up alongside MySQL.
#
# The copy is hardlink-based: each snapshot looks complete, but unchanged files
# share the previous snapshot's inodes, so daily snapshots cost only what changed.
# Run it while the service is up: LocalFileStorage stages uploads in a temp file
# and renames atomically, so a reader never sees a half-written object.
#
# Usage:
#   bash deploy/bin/backup_resource.sh
#   BACKUP_DIR=/mnt/backup bash deploy/bin/backup_resource.sh
#   crontab -e  ->  30 3 * * * /opt/nitrowater/deploy/bin/backup_resource.sh >/dev/null 2>&1
#
# Restore (service stopped, so nothing writes while the tree is replaced):
#   docker stop nitrowater-resource
#   rsync -a --delete deploy/backups/resource/<timestamp>/ "$RESOURCE_DATA_DIR"/
#   docker start nitrowater-resource
#
# Exit codes: 0 = snapshot written and verified; 1 = no fresh snapshot was produced.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$DEPLOY_ROOT/deploy/docker/.env"
SECRETS_FILE="$DEPLOY_ROOT/deploy/docker/.env.prod.secrets"

BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_ROOT/deploy/backups}"
RETAIN_DAYS="${RETAIN_DAYS:-30}"
RESOURCE_CONTAINER="${RESOURCE_CONTAINER:-nitrowater-resource}"

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

if ! command -v rsync >/dev/null 2>&1; then
    echo 'ERROR: rsync not found (apt install rsync / dnf install rsync)' >&2
    exit 1
fi

RESOURCE_DIR="${RESOURCE_DATA_DIR:-$(env_get RESOURCE_DATA_DIR)}"
if [ -z "$RESOURCE_DIR" ]; then
    echo "ERROR: RESOURCE_DATA_DIR is not set (env or $ENV_FILE)" >&2
    exit 1
fi
if [ ! -d "$RESOURCE_DIR" ]; then
    echo "ERROR: resource directory '$RESOURCE_DIR' does not exist" >&2
    exit 1
fi
if [ ! -r "$RESOURCE_DIR" ]; then
    echo "ERROR: resource directory '$RESOURCE_DIR' is not readable" >&2
    exit 1
fi

# The bind mount is the source of truth. If the container is up, confirm it sees
# the same host path - a mis-pointed mount means we would back up an empty tree.
if docker inspect -f '{{.State.Running}}' "$RESOURCE_CONTAINER" >/dev/null 2>&1; then
    MOUNT_SOURCE="$(docker inspect -f \
        '{{range .Mounts}}{{if eq .Destination "/data/resource"}}{{.Source}}{{end}}{{end}}' \
        "$RESOURCE_CONTAINER" 2>/dev/null || true)"
    if [ -n "$MOUNT_SOURCE" ] && [ "$(cd "$MOUNT_SOURCE" && pwd -P)" != "$(cd "$RESOURCE_DIR" && pwd -P)" ]; then
        echo "ERROR: $RESOURCE_CONTAINER mounts '$MOUNT_SOURCE' at /data/resource," >&2
        echo "       but RESOURCE_DATA_DIR is '$RESOURCE_DIR'" >&2
        exit 1
    fi
fi

SNAP_ROOT="$BACKUP_DIR/resource"
mkdir -p "$SNAP_ROOT"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

LOG_FILE="$BACKUP_DIR/resource-backup.log"
STAMP="$(date +%Y%m%d_%H%M%S)"
SNAP_DIR="$SNAP_ROOT/$STAMP"
trap 'if [ "${OK:-0}" -ne 1 ]; then rm -rf "$SNAP_DIR"; fi' EXIT

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"; }

# Hardlink against the newest existing snapshot so unchanged objects are shared.
# Snapshot names are fixed-width timestamps, so lexicographic order is chronological
# (globbing instead of `find -printf`, which BSD find on macOS does not have).
PREVIOUS=''
for dir in "$SNAP_ROOT"/*/; do
    [ -d "$dir" ] || continue
    name="$(basename "$dir")"
    if [ -z "$PREVIOUS" ] || [[ "$name" > "$PREVIOUS" ]]; then
        PREVIOUS="$name"
    fi
done
LINK_ARGS=()
[ -n "$PREVIOUS" ] && LINK_ARGS=(--link-dest="$SNAP_ROOT/$PREVIOUS")

log "start: $RESOURCE_DIR -> $SNAP_DIR (previous: ${PREVIOUS:-none})"

# --delete makes the snapshot an exact mirror (deleted objects must disappear from
# future snapshots too). Staging files (.upload-*.part) are transient by design and
# are left out; they are never referenced by a resource row.
if ! rsync -a --delete --numeric-ids --exclude='.upload-*.part' \
        "${LINK_ARGS[@]}" "$RESOURCE_DIR"/ "$SNAP_DIR"/ >>"$LOG_FILE" 2>&1; then
    log 'FAILED: rsync returned non-zero (see the log above)'
    exit 1
fi

# A truncated transfer still leaves a plausible-looking tree, so compare the file
# count of the live directory with the snapshot before declaring success.
source_files="$(find "$RESOURCE_DIR" -type f ! -name '.upload-*.part' | wc -l | tr -d '[:space:]')"
snap_files="$(find "$SNAP_DIR" -type f | wc -l | tr -d '[:space:]')"
if [ "$source_files" != "$snap_files" ]; then
    log "FAILED: file count mismatch (source $source_files, snapshot $snap_files)"
    exit 1
fi

OK=1
log "done: $SNAP_DIR ($snap_files files)"

find "$SNAP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETAIN_DAYS" -exec rm -rf {} + 2>/dev/null || true
