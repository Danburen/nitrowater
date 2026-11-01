#!/usr/bin/env bash
# ============================================================
# deploy.sh — nitrowater 单入口部署（对齐 waterfun deploy/bin/deploy.sh）
#
# 流程：校验 .env 存在 → 预检 check-env.sh → docker compose config 预检
#       → 起 infra → 起 app。
#   （compose 自带 mysql/redis healthcheck；变量缺失/语法错在 config 阶段即失败。）
#
# 内容叠加层：docker-compose.content.yml 把 deploy/content（moderation / docs /
# skills）只读挂进 server 的 /etc/nitrowater/*。加 --content 强制开启；默认在
# deploy/content/moderation/words.txt 存在（或 docs 下有 .md）时自动开启。
#
# 用法（在仓库根）:
#   bash deploy/bin/deploy.sh
#   bash deploy/bin/deploy.sh --infra-only
#   bash deploy/bin/deploy.sh --build
#   bash deploy/bin/deploy.sh --content
#   bash deploy/bin/deploy.sh --edge       # 加 TLS 边缘（nginx-proxy + acme-companion）
#   bash deploy/bin/deploy.sh --skip-check
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$DEPLOY_ROOT/deploy/docker"
ENV_FILE="$DOCKER_DIR/.env"
INFRA="$DOCKER_DIR/docker-compose.infra.yml"
APP="$DOCKER_DIR/docker-compose.app.yml"
CONTENT_YML="$DOCKER_DIR/docker-compose.content.yml"
EDGE_YML="$DOCKER_DIR/docker-compose.edge.yml"
CONTENT_DIR="$DEPLOY_ROOT/deploy/content"

cd "$DEPLOY_ROOT"

INFRA_ONLY=0
BUILD=0
CONTENT=0
EDGE=0
SKIP_CHECK=0
for arg in "$@"; do
    case "$arg" in
        --infra-only) INFRA_ONLY=1 ;;
        --build) BUILD=1 ;;
        --content) CONTENT=1 ;;
        --edge) EDGE=1 ;;
        --skip-check) SKIP_CHECK=1 ;;
        *) echo "unknown argument: $arg (use --infra-only / --build / --content / --edge / --skip-check)"; exit 2 ;;
    esac
done

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found"
    echo "  cp deploy/docker/.env.example deploy/docker/.env  # 然后填写真实值"
    exit 1
fi
command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is not available"; exit 1; }

if [ "$SKIP_CHECK" -eq 0 ]; then
    echo "=== [1/4] pre-flight (check-env) ==="
    if ! ENV_FILE="$ENV_FILE" bash "$SCRIPT_DIR/check-env.sh"; then
        echo "ERROR: pre-flight failed; 修好 [FAIL] 项，或改用 --skip-check"
        exit 1
    fi
else
    echo "=== [1/4] pre-flight skipped (--skip-check) ==="
fi

# 自动识别"已铺设内容"：存在 words.txt，或 docs 下有任意 .md。
if [ "$CONTENT" -eq 0 ]; then
    if [ -f "$CONTENT_DIR/moderation/words.txt" ] || [ -n "$(find "$CONTENT_DIR/docs" -type f -name '*.md' -print -quit 2>/dev/null)" ]; then
        CONTENT=1
    fi
fi

APP_FILES=(-f "$APP")
if [ "$CONTENT" -eq 1 ]; then
    if [ ! -f "$CONTENT_YML" ]; then
        echo "ERROR: $CONTENT_YML missing (content overlay requested)"
        exit 1
    fi
    APP_FILES+=(-f "$CONTENT_YML")
    echo "content overlay: ON  (deploy/content -> /etc/nitrowater)"
else
    echo "content overlay: off (内容仍来自镜像)"
fi

# 边缘叠加层：唯一公网入口（80/443 + 自动证书）。不开时整套服务只能经回环端口
# （浏览器 / SSH 隧道）访问。
if [ "$EDGE" -eq 1 ]; then
    if [ ! -f "$EDGE_YML" ]; then
        echo "ERROR: $EDGE_YML missing (edge overlay requested)"
        exit 1
    fi
    APP_FILES+=(-f "$EDGE_YML")
    echo "edge overlay: ON  (nginx-proxy 80/443, acme-companion auto TLS)"
else
    echo "edge overlay: off (无公网入口；端口仅绑 127.0.0.1)"
fi

echo "=== [2/4] validate compose ==="
docker compose --env-file "$ENV_FILE" -f "$INFRA" config --quiet
docker compose --env-file "$ENV_FILE" "${APP_FILES[@]}" config --quiet

echo "=== [3/4] up infra ==="
docker compose --env-file "$ENV_FILE" -f "$INFRA" up -d
if [ "$INFRA_ONLY" -eq 1 ]; then echo "infra is up."; exit 0; fi

echo "=== [4/4] up app ==="
if [ "$BUILD" -eq 1 ]; then
    docker compose --env-file "$ENV_FILE" "${APP_FILES[@]}" up -d --build
else
    docker compose --env-file "$ENV_FILE" "${APP_FILES[@]}" up -d
fi
echo "deployed.  (logs: docker compose -f deploy/docker/docker-compose.app.yml$([ "$CONTENT" -eq 1 ] && echo ' -f deploy/docker/docker-compose.content.yml') logs -f)"
