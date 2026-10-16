#!/usr/bin/env bash
# ============================================================
# nitrowater 环境自检 (Bash — Linux/macOS)
# 检查：MySQL/Redis 端口、JWT 密钥、NITROWATER_KEK、DEVICE_SALT
# 若存在 deploy/docker/.env 则先加载（与 compose 的 --env-file 一致）。
#
# 用法:
#   bash deploy/bin/check-env.sh
# ============================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$DEPLOY_ROOT/deploy/docker/.env"

# ---- 加载 .env（若有；逐行解析，不 source，避免 # 注释/变量展开被误判）----
if [ -f "$ENV_FILE" ]; then
    while IFS='=' read -r key value; do
        key="$(echo "$key" | tr -d '[:space:]')"
        value="$(echo "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^["'\'']//' -e 's/["'\'']$//')"
        if echo "$key" | grep -qE '^[A-Z][A-Z_0-9]+$'; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
    echo -e "${CYAN}[OK]${NC} Loaded $ENV_FILE"
fi

check_port() {
    local name="$1" port="$2"
    if timeout 2 bash -c "echo > /dev/tcp/127.0.0.1/$port" 2>/dev/null; then
        echo -e "  [${GREEN}PASS${NC}] $name 127.0.0.1:$port"
    else
        echo -e "  [${RED}FAIL${NC}] $name 127.0.0.1:$port"
    fi
}

echo "=== ports ==="
check_port MySQL 3306
check_port Redis 6379

echo "=== JWT keys ==="
for f in private.key public.key; do
    p="$SCRIPT_DIR/../keys/$f"
    if [ -f "$p" ]; then echo -e "  [${GREEN}PASS${NC}] $p"
    else echo -e "  [${RED}FAIL${NC}] $p"; fi
done

echo "=== env ==="
if [ -n "${NITROWATER_KEK:-}" ]; then
    echo -e "  [${GREEN}PASS${NC}] NITROWATER_KEK set (len=${#NITROWATER_KEK})"
else
    echo -e "  [${YELLOW}WARN${NC}] NITROWATER_KEK not set (bootRun 回退 build.gradle 开发默认；部署 account 必填)"
fi
if [ -n "${DEVICE_SALT:-}" ]; then
    echo -e "  [${GREEN}PASS${NC}] DEVICE_SALT set"
else
    echo -e "  [${YELLOW}WARN${NC}] DEVICE_SALT not set (application.yml 有开发默认)"
fi

echo ""
echo "Done."
