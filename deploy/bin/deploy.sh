#!/usr/bin/env bash
# ============================================================
# deploy.sh — nitrowater 单入口部署（对齐 waterfun deploy/bin/deploy.sh）
#
# 流程：校验 .env 存在 → docker compose config 预检 → 起 infra → 起 app。
#   （compose 自带 mysql/redis healthcheck；变量缺失/语法错在 config 阶段即失败。）
#
# 用法（在仓库根）:
#   bash deploy/bin/deploy.sh
#   bash deploy/bin/deploy.sh --infra-only
#   bash deploy/bin/deploy.sh --build
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$DEPLOY_ROOT/deploy/docker"
ENV_FILE="$DOCKER_DIR/.env"
INFRA="$DOCKER_DIR/docker-compose.infra.yml"
APP="$DOCKER_DIR/docker-compose.app.yml"

cd "$DEPLOY_ROOT"

INFRA_ONLY=0
BUILD=0
for arg in "$@"; do
    case "$arg" in
        --infra-only) INFRA_ONLY=1 ;;
        --build) BUILD=1 ;;
        *) echo "unknown argument: $arg (use --infra-only / --build)"; exit 2 ;;
    esac
done

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found"
    echo "  cp deploy/docker/.env.example deploy/docker/.env  # 然后填写真实值"
    exit 1
fi
command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is not available"; exit 1; }

echo "=== [1/3] validate compose ==="
docker compose --env-file "$ENV_FILE" -f "$INFRA" config --quiet
docker compose --env-file "$ENV_FILE" -f "$APP" config --quiet

echo "=== [2/3] up infra ==="
docker compose --env-file "$ENV_FILE" -f "$INFRA" up -d
if [ "$INFRA_ONLY" -eq 1 ]; then echo "infra is up."; exit 0; fi

echo "=== [3/3] up app ==="
if [ "$BUILD" -eq 1 ]; then
    docker compose --env-file "$ENV_FILE" -f "$APP" up -d --build
else
    docker compose --env-file "$ENV_FILE" -f "$APP" up -d
fi
echo "deployed.  (logs: docker compose -f deploy/docker/docker-compose.app.yml logs -f)"
