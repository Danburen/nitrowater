#!/usr/bin/env bash
# ============================================================
# .env 同步：以 deploy/docker/.env.example 为骨架刷新 deploy/docker/.env
#
# 规则（定死，别改语义）：
#   1. 注释、分节标题、空行、顺序 —— 一律以 .env.example 为准。
#   2. 生效的键：例子有 + .env 有 -> 保留 .env 的原值（不会被模板占位值覆盖）。
#   3. 生效的键：例子有 + .env 没有 -> 用例子那行（占位值 YOUR_*_HERE，提醒你补）。
#   4. .env 有 + 例子没有（生效行和注释行都没有）-> 从 .env 删除。
#   5. 例子中注释掉的键（如 "# APP_IMAGE_TAG=latest"）：.env 里若已启用，则保留启用状态和原值，
#      不会退回注释 —— 否则会把线上正在用的 tag 悄悄关掉。
#      反过来：在 .env 里把某个键注释掉不表示"禁用"，模板里它是生效行就会被补回来（带占位值）。
#      真要禁用，就把模板里那行改成注释 —— 注释与启停状态都以模板为准。
#
# 用法（在仓库根或任意目录都行）：
#   bash deploy/bin/sync-env.sh                  # 同步（先备份成 .env.bak.<时间戳>）
#   bash deploy/bin/sync-env.sh --dry-run        # 只打印摘要与 diff，不落盘
#   bash deploy/bin/sync-env.sh -f path/to/.env  # 同步指定文件（默认 deploy/docker/.env）
#
# 退出码：0 = 已同步（或本来就没差异）；1 = 输入缺失；2 = 参数问题。
# 文件权限保持原样（.env 通常是 600）；目标不存在时按 600 新建。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$SCRIPT_DIR/../docker"
DRY=0
TARGET=""

usage() { sed -n '2,22p' "$0"; exit 0; }

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY=1; shift ;;
        -f|--file) TARGET="${2:?--file needs a path}"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "ERROR: unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
done

EXAMPLE="$DOCKER_DIR/.env.example"
[ -n "$TARGET" ] || TARGET="$DOCKER_DIR/.env"

[ -f "$EXAMPLE" ] || { echo "ERROR: template not found: $EXAMPLE" >&2; exit 1; }
TARGET="$(mkdir -p "$(dirname "$TARGET")" && cd "$(dirname "$TARGET")" && pwd)/$(basename "$TARGET")"

# Keep the target's line endings (a Windows-edited .env is CRLF); new files get LF.
EOL=$'\n'
if [ -f "$TARGET" ] && grep -q $'\r' "$TARGET" 2>/dev/null; then
    EOL=$'\r\n'
fi

# ---- current .env: KEY=VALUE for active lines only (last occurrence wins, like compose) ----
declare -A CUR=()
if [ -f "$TARGET" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        case "$line" in ''|'#'*) continue ;; esac
        if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]]; then
            CUR["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
        fi
    done < "$TARGET"
fi

# ---- rebuild: skeleton from the template, values from .env ----
declare -A USED=()
declare -A EMITTED=()
out=""
kept=0 added=0 activated=0
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"

    # Active key line: "KEY=value"
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        USED["$key"]=1
        EMITTED["$key"]=1
        if [ -n "${CUR[$key]+x}" ]; then
            out+="$key=${CUR[$key]}$EOL"; kept=$((kept + 1))
        else
            out+="$line$EOL"; added=$((added + 1))
        fi
        continue
    fi

    # Commented-out key line: "# KEY=value". Re-enable it only when .env already has it active
    # AND the template has not already emitted that key as an active line (the template uses
    # commented lines both for "optional" keys and for alternative snippets of an active key,
    # e.g. AI_MODERATION_DICT - those snippets must stay comments).
    if [[ "$line" =~ ^#[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)= ]]; then
        key="${BASH_REMATCH[1]}"
        USED["$key"]=1
        if [ -n "${CUR[$key]+x}" ] && [ -z "${EMITTED[$key]+x}" ]; then
            out+="$key=${CUR[$key]}$EOL"; activated=$((activated + 1)); EMITTED["$key"]=1
        else
            out+="$line$EOL"
        fi
        continue
    fi

    out+="$line$EOL"
done < "$EXAMPLE"

# ---- keys that only exist in .env -> dropped ----
dropped=()
for key in "${!CUR[@]}"; do
    [ -n "${USED[$key]+x}" ] || dropped+=("$key")
done

existed=0
[ -f "$TARGET" ] && existed=1

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
printf '%s' "$out" > "$tmp"

echo "==> template: $EXAMPLE"
echo "==> target  : $TARGET $([ "$existed" -eq 1 ] && echo "(exists)" || echo "(new)")"
echo "==> kept=$kept added=$added re-enabled=$activated dropped=${#dropped[@]}"

if [ "$DRY" -eq 1 ]; then
    if [ "$existed" -eq 1 ]; then
        echo '==> diff (current -> new)'
        diff -u --label '.env (current)' --label '.env (new)' "$TARGET" "$tmp" || true
    else
        echo '==> content (new file)'
        cat "$tmp"
    fi
    echo '==> dry-run: nothing written'
    exit 0
fi

if [ "$existed" -eq 1 ]; then
    if cmp -s "$TARGET" "$tmp"; then
        echo '==> already in sync, nothing to do'
        exit 0
    fi
    backup="$TARGET.bak.$(date +%Y%m%d-%H%M%S)"
    # Two runs inside the same second must not overwrite the first backup.
    n=1
    while [ -e "$backup" ]; do
        backup="$TARGET.bak.$(date +%Y%m%d-%H%M%S)-$n"; n=$((n + 1))
    done
    cp -p "$TARGET" "$backup"
    echo "==> backup  : $backup"
    # Truncate in place: keeps the inode, so mode/owner (usually 600) survive.
    cat "$tmp" > "$TARGET"
else
    umask 077
    cat "$tmp" > "$TARGET"
    chmod 600 "$TARGET"
fi

if [ "${#dropped[@]}" -gt 0 ]; then
    printf '==> dropped keys (not in template): %s\n' "$(printf '%s\n' "${dropped[@]}" | sort | tr '\n' ' ')"
fi
echo "==> done; review with: diff -u $TARGET.bak.* $TARGET  (or run --dry-run next time)"
