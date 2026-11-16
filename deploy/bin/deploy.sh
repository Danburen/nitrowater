#!/usr/bin/env bash
# ============================================================
# nitrowater 单入口部署（手动运维 与 CI 共用同一条代码路径）
#
# 流程：.env 存在性 → check-env 门禁 → compose 结构预检
#       → MySQL 备份（替换容器前，失败即停）→ 列出将要使用的镜像
#       → infra → account → 公网 issuer 门禁 → app（pull + up -d --no-build）
#       → 探活 → 清理 24h 前的旧镜像
#
# 为什么要有这条脚本：同一串命令以前内联在 .github/workflows/deploy.yml 的 SSH 段里，
# 于是「手动上线」和「CI 上线」是两份实现——改一处忘一处就会漂移（顺序、--no-build、
# 探活地址）。现在两边都调这里，CI 只负责 preflight + 把 deploy/ 同步到宿主机。
#
# 用法（宿主机 DEPLOY_ROOT 下执行；脚本自身会 cd 到仓库/deploy 根）：
#   bash deploy/bin/deploy.sh                            # 用 .env 里的 tag（ACCOUNT_IMAGE_TAG / APP_IMAGE_TAG）
#   bash deploy/bin/deploy.sh --tag v1.2.3               # 指定 tag（回滚就是传上一个提交的 sha）
#   bash deploy/bin/deploy.sh --profile edge             # 启用 infra 的 edge profile（自带 80/443 + 证书）
#   bash deploy/bin/deploy.sh --only account             # 只动 account（等价 README 的 --no-deps 场景）
#   bash deploy/bin/deploy.sh --no-prune                 # 不清理旧镜像
#   bash deploy/bin/deploy.sh --skip-backup              # 跳过发版前的 MySQL 备份（确认无 schema 变更时）
#   bash deploy/bin/deploy.sh --skip-issuer-gate         # 跳过公网 issuer 门禁（edge 暂时不可用但仍要发 app）
#
# 前提：宿主机有 docker + compose 插件、bash、curl。**不需要 git / JDK / Node**。
#       GHCR 包是 private 时先 `docker login ghcr.io`（或由 CI 的 GHCR_PAT 完成）。
# 注意：--tag 只作用于 account / app 的镜像；文档站镜像来自独立仓库（nitrowater-plugin-docs），
#       由 .env 的 DOCS_IMAGE_TAG 单独控制 —— 它跟着 app 单元一起 pull/up，但版本独立。
# 退出码：0 = 全部起来且探活通过；非 0 = 任一门禁/拉取/启动/探活失败（set -e）。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="deploy/docker/.env"
SECRETS_FILE="deploy/docker/.env.prod.secrets"
UNITS_DEFAULT=(infra account app)

TAG=""
COMPOSE_PROFILE_ARG="${COMPOSE_PROFILE:-}"
PRUNE=1
SKIP_BACKUP=0
SKIP_ISSUER_GATE=0
ONLY=""

usage() {
    cat <<'EOF'
Usage: bash deploy/bin/deploy.sh [options]

  --tag <tag>        image tag to deploy (v1.2.3 / latest / commit sha)
  --only <unit>      only one unit: infra | account | app
  --profile <name>   compose profile for the infra unit (e.g. edge)
  --skip-backup      skip the pre-deploy MySQL dump
  --skip-issuer-gate skip the public issuer probe (bff will then restart until it is up)
  --no-prune         keep images older than 24h
  -h, --help         show this help

On the host: docker + compose plugin, bash, curl. No git / JDK / Node needed.
Exit code 0 = every unit is up and the probes passed.
EOF
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --tag)      TAG="${2:?--tag needs a value}"; shift 2 ;;
        --profile)  COMPOSE_PROFILE_ARG="${2:?--profile needs a value}"; shift 2 ;;
        --only)     ONLY="${2:?--only needs infra|account|app}"; shift 2 ;;
        --no-prune) PRUNE=0; shift ;;
        --skip-backup) SKIP_BACKUP=1; shift ;;
        --skip-issuer-gate) SKIP_ISSUER_GATE=1; shift ;;
        -h|--help)  usage ;;
        *) echo "ERROR: unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
done

cd "$DEPLOY_ROOT"

# Host prerequisites (deploy/docs/bootstrap.md §1): docker + compose plugin, bash, curl.
command -v docker >/dev/null 2>&1 || { echo 'ERROR: docker not found' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'ERROR: docker compose plugin not found' >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo 'ERROR: curl not found (used by the health checks)' >&2; exit 1; }

# Read one KEY from .env/.env.prod.secrets without sourcing: values may contain '#', '$' or
# spaces, which a shell would misread. Among files the last one wins (compose behaves the same).
env_get() {
    local key="$1" file line value=""
    for file in "$ENV_FILE" "$SECRETS_FILE"; do
        [ -f "$file" ] || continue
        line="$(sed -n "s/^[[:space:]]*${key}=//p" "$file" | tail -n 1)"
        [ -n "$line" ] && value="${line%$'\r'}"
    done
    printf '%s' "$value"
}

if [ ! -f "$ENV_FILE" ]; then
    cat >&2 <<'EOF'
ERROR: deploy/docker/.env not found

  cp deploy/docker/.env.example deploy/docker/.env && chmod 600 deploy/docker/.env
  # then fill in the values (see deploy/docs/bootstrap.md section 3) and run this again
EOF
    exit 1
fi

# compose image refs are ${REGISTRY}/${GITHUB_REPOSITORY}/...; GHCR paths must be lowercase.
if [ -n "${GITHUB_REPOSITORY:-}" ]; then
    GITHUB_REPOSITORY="$(printf '%s' "$GITHUB_REPOSITORY" | tr '[:upper:]' '[:lower:]')"
    export GITHUB_REPOSITORY
fi

# A tag on the command line beats .env (compose gives process env priority over --env-file).
if [ -n "$TAG" ]; then
    export ACCOUNT_IMAGE_TAG="$TAG"
    export APP_IMAGE_TAG="$TAG"
fi
# One tag addresses all four images; app tag wins because `.env` may set only
# ACCOUNT_IMAGE_TAG (single-service pin) or neither.
EFFECTIVE_TAG="${APP_IMAGE_TAG:-${ACCOUNT_IMAGE_TAG:-}}"
[ -n "$EFFECTIVE_TAG" ] || EFFECTIVE_TAG="$(env_get APP_IMAGE_TAG)"
[ -n "$EFFECTIVE_TAG" ] || EFFECTIVE_TAG="$(env_get ACCOUNT_IMAGE_TAG)"
EFFECTIVE_TAG="${EFFECTIVE_TAG:-latest}"
REGISTRY_VAL="${REGISTRY:-$(env_get REGISTRY)}"
REGISTRY_VAL="${REGISTRY_VAL:-ghcr.io}"
REPO_VAL="${GITHUB_REPOSITORY:-$(env_get GITHUB_REPOSITORY)}"
REPO_VAL="${REPO_VAL:-your-org/nitrowater}"

echo "==> target: ${REPO_VAL} tag=${EFFECTIVE_TAG} registry=${REGISTRY_VAL}"

# One array that is never empty, so `"${COMPOSE_ARGS[@]}"` is safe under `set -u` on bash 4.2.
COMPOSE_ARGS=(--env-file "$ENV_FILE")
[ -f "$SECRETS_FILE" ] && COMPOSE_ARGS+=(--env-file "$SECRETS_FILE")
[ -n "$COMPOSE_PROFILE_ARG" ] && COMPOSE_ARGS+=(--profile "$COMPOSE_PROFILE_ARG")

UNITS=("${UNITS_DEFAULT[@]}")
if [ -n "$ONLY" ]; then
    case "$ONLY" in
        infra|account|app) UNITS=("$ONLY") ;;
        *) echo "ERROR: --only expects infra|account|app (got: $ONLY)" >&2; exit 2 ;;
    esac
fi

wait_http() {
    local url="$1" limit="${2:-60}" tries=0
    while [ "$tries" -lt "$limit" ]; do
        if curl -fsS -o /dev/null "$url"; then
            echo "    ready: $url"
            return 0
        fi
        tries=$((tries + 1))
        sleep 3
    done
    echo "    TIMEOUT: $url (after $((limit * 3))s)" >&2
    return 1
}

wait_healthy() {
    local name="$1" limit="${2:-40}" tries=0
    while [ "$tries" -lt "$limit" ]; do
        if [ "$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo missing)" = "healthy" ]; then
            echo "    healthy: $name"
            return 0
        fi
        tries=$((tries + 1))
        sleep 3
    done
    echo "    TIMEOUT: $name never became healthy" >&2
    docker logs --tail 50 "$name" || true
    return 1
}

# Flyway runs inside the app on boot, so the only recovery point for a release that
# changes the schema is a dump taken before the containers are replaced. A missing
# container means "not a deployed host yet" (skip); anything else must succeed, and
# backup_mysql.sh itself refuses to dump from a stopped mysql.
pre_deploy_backup() {
    if [ "$SKIP_BACKUP" -eq 1 ]; then
        echo '==> backup: skipped (--skip-backup)'
        return 0
    fi
    if ! docker inspect -f '{{.State.Running}}' nitrowater-mysql >/dev/null 2>&1; then
        echo '==> backup: skipped (nitrowater-mysql not found)'
        return 0
    fi
    echo '==> backup: MySQL dump before replacing containers'
    bash deploy/bin/backup_mysql.sh
}

# bff 启动时要从 JWT_ISSUER 拉 OIDC discovery（application.yml 的 provider.issuer-uri），拉不到
# 就崩溃重启。这个地址走公网，需要 edge 证书已签发 + frp/反代链路通，所以在拉起 app 之前探测：
# 一次明确的失败，好过 bff 崩十几次再回来翻日志。
require_public_issuer() {
    local issuer
    issuer="$(env_get JWT_ISSUER)"
    issuer="${issuer%/}"
    if [ -z "$issuer" ]; then
        echo '    SKIP: JWT_ISSUER is empty in deploy/docker/.env' >&2
        return 0
    fi
    wait_http "$issuer/.well-known/openid-configuration" 60 && return 0
    cat >&2 <<EOF
ERROR: public issuer is unreachable: $issuer

  bff 启动时要拉这个地址的 OIDC discovery，拉不到会反复重启，所以在拉起 app 之前停下。
  排查顺序：
    1) edge 是否在跑（--profile edge 下的 nginx-proxy / acme）
    2) 证书是否已签发：docker logs nitrowater-acme
    3) 80/443 是否真能从公网回来：frp / 反代 / 安全组
    4) JWT_ISSUER 是否等于 AUTH_DOMAIN（必须是 AS 的域名，不是站点域名）
  确认要跳过本门禁时加 --skip-issuer-gate（bff 会自己重试）。
EOF
    return 1
}

echo '==> gate 1/2: vars and content (deploy/bin/check-env.sh)'
bash deploy/bin/check-env.sh

echo '==> gate 2/2: compose structure'
for unit in "${UNITS[@]}"; do
    docker compose "${COMPOSE_ARGS[@]}" -f "deploy/docker/docker-compose.${unit}.yml" config -q
done

# The only place a `your-org/nitrowater` fallback (REGISTRY / GITHUB_REPOSITORY missing from
# .env) becomes visible before the pull.
echo '==> resolved images'
for unit in "${UNITS[@]}"; do
    docker compose "${COMPOSE_ARGS[@]}" -f "deploy/docker/docker-compose.${unit}.yml" config --images
done

# --only infra is how a stopped mysql gets started again, so it must not need a dump.
case " ${UNITS[*]} " in
    *" account "*|*" app "*) pre_deploy_backup ;;
    *) echo '==> backup: skipped (--only infra)' ;;
esac

# Fixed order: infra -> account (bff fetches discovery at boot) -> app.
# No --remove-orphans: with a profile mismatch it would delete nginx-proxy/acme.
for unit in "${UNITS[@]}"; do
    if [ "$unit" = "app" ] && [ "$SKIP_ISSUER_GATE" -eq 0 ]; then
        echo '==> gate: public AS discovery (bff boot dependency)'
        require_public_issuer
    fi
    echo "==> ${unit}: pull"
    docker compose "${COMPOSE_ARGS[@]}" -f "deploy/docker/docker-compose.${unit}.yml" pull
    echo "==> ${unit}: up -d"
    docker compose "${COMPOSE_ARGS[@]}" -f "deploy/docker/docker-compose.${unit}.yml" up -d --no-build
    if [ "$unit" = "account" ]; then
        wait_http http://127.0.0.1:8090/.well-known/openid-configuration 60
    fi
done

case " ${UNITS[*]} " in
    *" app "*)
        echo '==> verify'
        # docs first: it is a static site, so a failure here is either a bad image tag or a
        # broken nginx conf - both worth reporting before the business services.
        wait_healthy nitrowater-docs
        wait_http http://127.0.0.1:8080/api/public/ping 100
        wait_healthy nitrowater-bff 60
        ;;
esac

if [ "$PRUNE" -eq 1 ]; then
    echo '==> prune images older than 24h'
    docker image prune -af --filter 'until=24h'
fi

echo "==> done: ${EFFECTIVE_TAG}"
