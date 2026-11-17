#!/usr/bin/env bash
# ============================================================
# NitroWater 上线环境自检 (Linux / macOS)
#
# 检查项：.env 加载、必需变量与占位值（YOUR_*_HERE / 123456 / change-me / dev salt）、
# 条件变量、内容资产（词表双层 + 非空 / 文档语料）、密钥、文档站镜像 ref、端口。
#
# 用法：
#   bash deploy/bin/check-env.sh                       # Docker 上线
#   bash deploy/bin/check-env.sh --local               # 本地 dev（额外检查端口）
#   bash deploy/bin/check-env.sh a.env b.env           # 多个 env 文件，后者覆盖前者
#   ENV_FILE=deploy/docker/.env bash deploy/bin/check-env.sh
#
# 退出码：0 = 无 FAIL；1 = 存在 FAIL
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"

LOCAL=0
ENV_FILES=()
for arg in "$@"; do
    case "$arg" in
        --local) LOCAL=1 ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) ENV_FILES+=("$arg") ;;
    esac
done
# Default deploy layout: non-secret values in .env, secrets in .env.prod.secrets. compose gets
# both (--env-file a --env-file b), so this gate reads the same pair in the same order.
if [ ${#ENV_FILES[@]} -eq 0 ]; then
    if [ -n "${ENV_FILE:-}" ]; then
        ENV_FILES=("$ENV_FILE")
    else
        ENV_FILES=("$ROOT/docker/.env" "$ROOT/docker/.env.prod.secrets")
    fi
fi

PASS=0
WARN=0
FAIL=0

if [ -t 1 ]; then
    C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_GRY=$'\033[90m'; C_RST=$'\033[0m'
else
    C_RED=''; C_GRN=''; C_YEL=''; C_GRY=''; C_RST=''
fi

pass() { PASS=$((PASS + 1)); printf '  %s[PASS]%s %s -- %s\n' "$C_GRN" "$C_RST" "$1" "$2"; }
warn() { WARN=$((WARN + 1)); printf '  %s[WARN]%s %s -- %s\n' "$C_YEL" "$C_RST" "$1" "$2"; }
fail() { FAIL=$((FAIL + 1)); printf '  %s[FAIL]%s %s -- %s\n' "$C_RED" "$C_RST" "$1" "$2"; }
info() { printf '  %s[INFO]%s %s -- %s\n' "$C_GRY" "$C_RST" "$1" "$2"; }

# ---------- 1. .env ----------
# Keys this script exported itself: a variable from the real process environment must keep
# winning (compose behaves the same), while among files the last one wins.
declare -A DOTENV_KEYS=()
ENV_PATHS=()
for env_file in "${ENV_FILES[@]}"; do
    [ -f "$env_file" ] || continue
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"    # 容忍 CRLF：Windows 上编辑过的 .env 不能让值尾带 \r
        trimmed="${line#"${line%%[![:space:]]*}"}"
        case "$trimmed" in ''|'#'*) continue ;; esac
        key="${trimmed%%=*}"
        case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac
        value="${trimmed#*=}"
        value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
        if [ -z "${!key:-}" ] || [ -n "${DOTENV_KEYS[$key]:-}" ]; then
            export "$key=$value"
            DOTENV_KEYS[$key]=1
        fi
    done < "$env_file"
    ENV_PATHS+=("$env_file")
done
if [ ${#ENV_PATHS[@]} -gt 0 ]; then
    info '.env' "已加载 ${ENV_PATHS[*]}"
else
    warn '.env' "未找到 ${ENV_FILES[*]}（仅用进程环境变量；Docker 上线应存在）"
fi

CONTENT_ROOT="${CONTENT_ROOT:-$ROOT/content}"
# compose resolves a relative bind-mount source against the compose file's directory
# (deploy/docker), while check-env may be launched from anywhere: anchor it the same way.
case "$CONTENT_ROOT" in
    /*) ;;
    *) CONTENT_ROOT="$ROOT/docker/$CONTENT_ROOT" ;;
esac
[ -d "$CONTENT_ROOT" ] && CONTENT_ROOT="$(cd "$CONTENT_ROOT" && pwd -P)"

# 把容器内挂载路径映射回宿主机目录，确保校验的是真正会被挂载的那份文件。
resolve_host_path() {
    local path="${1#file:}"
    case "$path" in
        /etc/nitrowater/moderation*) echo "$CONTENT_ROOT/moderation${path#/etc/nitrowater/moderation}"; return ;;
        /etc/nitrowater/docs*) echo "$CONTENT_ROOT/docs${path#/etc/nitrowater/docs}"; return ;;
    esac
    # Local dev passes repo-relative paths (bootRun pins the working dir to the repo root).
    case "$path" in
        /*) echo "$path" ;;
        *) echo "$REPO/$path" ;;
    esac
}

count_entries() {
    [ -f "$1" ] || { echo -1; return; }
    grep -cv -e '^[[:space:]]*#' -e '^[[:space:]]*$' "$1"
}

# Template placeholders (YOUR_*_HERE) mean "not configured yet". Uppercase-only on purpose:
# a real value such as `your_name@corp.com` must not trip the check.
is_placeholder() {
    printf '%s' "$1" | grep -Eq 'YOUR_[A-Z0-9_]*|_HERE$'
}

echo '=== NitroWater 上线环境自检 ==='
if [ "$LOCAL" -eq 1 ]; then echo 'mode: local (dev)'; else echo 'mode: docker (prod)'; fi
echo ''

echo 'Result:'
echo '--------------------------------------------------------------------'

# ---------- 2. 必需变量 / 占位值 ----------
# 模板占位值（YOUR_*_HERE）必须阻断：把 .env.example 原样复制过来时，
# 以下逐项规则只认空值 / 已知弱默认，占位值会一路 PASS（评估 M3）。
PLACEHOLDERS=''
for env_file in "${ENV_PATHS[@]}"; do
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        trimmed="${line#"${line%%[![:space:]]*}"}"
        case "$trimmed" in ''|'#'*) continue ;; esac
        key="${trimmed%%=*}"
        case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac
        value="${!key:-}"
        if [ -n "$value" ] && is_placeholder "$value"; then
            case " $PLACEHOLDERS " in *" $key "*) ;; *) PLACEHOLDERS="$PLACEHOLDERS $key" ;; esac
        fi
    done < "$env_file"
done
if [ -n "$PLACEHOLDERS" ]; then
    fail 'placeholders' "仍是模板占位值，需换成真实值：$PLACEHOLDERS"
else
    pass 'placeholders' '未发现 YOUR_*_HERE 占位值'
fi

# ---------- 2.1 compose -> .env 覆盖 ----------
# 上面的规则只覆盖"有人记得加"的键；这里从 compose 反推：默认值是 URL/端口这类无害值就允许留空，
# 默认值是凭据或 dev 默认值就必须在 .env 里显式给值，否则就成了"复制模板改几个字就上线"。
# 两级严重度：`.env 没给值` = FAIL（部署不可能安全）；`给了但仍是 dev 值` = WARN（单机内部署可接受）。
DEV_DEFAULT_VALUES=' 123456 change-me device-salt-dev nitrowater-bff-secret root dev not-configured '
COMPOSE_DIR="$ROOT/docker"

# Windows line endings are invisible in a diff and break two things at once: a CRLF .env hands
# every container a value with a trailing \r (Redis AUTH / MySQL password mismatch), and a CRLF
# compose file makes the coverage parser below miss `${VAR:?…}` (it reports the container-side
# key, e.g. SPRING_DATASOURCE_USERNAME, instead of the .env key it came from).
CRLF_FILES=''
for crlf_file in "${ENV_PATHS[@]}" "$COMPOSE_DIR"/docker-compose.*.yml; do
    [ -f "$crlf_file" ] || continue
    crlf_chunk=''
    # Read with the bash builtin: GNU grep on some platforms (MSYS) opens files in text mode and
    # silently drops the CR, which would make this gate blind exactly where it matters.
    IFS= read -r -d '' crlf_chunk < "$crlf_file"
    case "$crlf_chunk" in
        *$'\r'*) CRLF_FILES="$CRLF_FILES ${crlf_file#"$ROOT/"}" ;;
    esac
done
if [ -n "$CRLF_FILES" ]; then
    fail 'crlf' "CRLF 换行：${CRLF_FILES# }（修：sed -i 's/\\r$//' 上述文件）"
else
    pass 'crlf' '.env / compose 均为 LF'
fi

declare -A COMPOSE_KEYS=()
declare -A REFERENCED=()
UNFILLED=''
DEV_DEFAULTED=''
FROM_ENV=0
FROM_DEFAULT=0
LITERAL=0

is_dev_default() {
    case " $DEV_DEFAULT_VALUES " in *" $1 "*) return 0 ;; esac
    return 1
}

for compose_file in "$COMPOSE_DIR"/docker-compose.*.yml; do
    [ -f "$compose_file" ] || continue
    while IFS= read -r ref; do
        [ -n "$ref" ] || continue
        REFERENCED[$ref]=1
    done <<EOF
$(grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*' "$compose_file" | sed 's/^\${//' | sort -u)
EOF
    while IFS= read -r entry; do
        [ -n "$entry" ] || continue
        key="${entry%%:*}"
        key="${key#"${key%%[![:space:]]*}"}"
        raw="${entry#*:}"
        raw="${raw#"${raw%%[![:space:]]*}"}"
        if [ -n "${COMPOSE_KEYS[$key]:-}" ]; then continue; fi
        COMPOSE_KEYS[$key]=1
        case "$raw" in
            *'${'*) ;;
            *) LITERAL=$((LITERAL + 1)); continue ;;
        esac

        # 单个 ${VAR...} 表达式：要看的是 VAR 的值，不是 compose 里的键名。
        if [[ "$raw" =~ ^\$\{([A-Za-z_][A-Za-z0-9_]*)(:[^}]*)?\}$ ]]; then
            var="${BASH_REMATCH[1]}"
            value="${!var:-}"
            if [ -n "$value" ]; then
                # 占位值交给上面的 placeholders 检查，这里只抓真实 dev 值。
                if ! is_placeholder "$value" && is_dev_default "$value"; then
                    # Keys only: this line is printed in CI logs, so never echo the value.
                    DEV_DEFAULTED="$DEV_DEFAULTED $var"
                else
                    FROM_ENV=$((FROM_ENV + 1))
                fi
            elif [[ "$raw" == *':?'* || "$raw" =~ ^\$\{[A-Za-z_][A-Za-z0-9_]*\}$ ]]; then
                UNFILLED="$UNFILLED $var"
            elif [[ "$raw" =~ 123456|change-me|device-salt-dev|bff-secret|not-configured|:-root ]]; then
                UNFILLED="$UNFILLED $var"
            else
                FROM_DEFAULT=$((FROM_DEFAULT + 1))
            fi
            continue
        fi

        # 混合表达式（URL / healthcheck）：只有显式必填标记才算阻塞。
        if [[ "$raw" == *':?'* ]] && [ -z "${!key:-}" ]; then
            UNFILLED="$UNFILLED $key"
        else
            FROM_DEFAULT=$((FROM_DEFAULT + 1))
        fi
    done <<EOF
$(grep -E '^      [A-Z][A-Z0-9_]*:' "$compose_file")
EOF
done

if [ -n "$UNFILLED" ]; then UNFILLED="$(printf '%s\n' $UNFILLED | sort -u | tr '\n' ' ')"; fi
if [ -n "$DEV_DEFAULTED" ]; then DEV_DEFAULTED="$(printf '%s\n' $DEV_DEFAULTED | sort -u | tr '\n' ' ')"; fi
# The compose inventory only describes the Docker path; local dev talks to the host MySQL/Redis
# with its own (root) credentials, so reporting it there would be pure noise.
if [ "$LOCAL" -eq 1 ]; then
    info 'compose-coverage' '本地 dev 跳过（仅 Docker 上线检查 compose 覆盖）'
elif [ -n "$UNFILLED" ]; then
    fail 'compose-coverage' ".env 缺这些值，compose 会回落到空值或 dev 默认值：$UNFILLED"
else
    pass 'compose-coverage' "compose 变量都已覆盖（.env=$FROM_ENV 安全默认=$FROM_DEFAULT 字面量=$LITERAL）"
fi
if [ "$LOCAL" -eq 1 ]; then
    info 'compose-defaults' '本地 dev 跳过'
elif [ -n "$DEV_DEFAULTED" ]; then
    warn 'compose-defaults' "仍是 dev 默认值（单机可接受，公网必须换）：$DEV_DEFAULTED"
else
    pass 'compose-defaults' '没有使用 dev 默认值'
fi

# 反向：.env 写了但没有任何 compose 引用 -> 不会进容器（"配了但没生效"比"没配"更糟）。
ENV_ONLY=''
for env_file in "${ENV_PATHS[@]}"; do
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        trimmed="${line#"${line%%[![:space:]]*}"}"
        case "$trimmed" in ''|'#'*) continue ;; esac
        key="${trimmed%%=*}"
        case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac
        if [ -z "${REFERENCED[$key]:-}" ]; then
            case " $ENV_ONLY " in *" $key "*) ;; *) ENV_ONLY="$ENV_ONLY $key" ;; esac
        fi
    done < "$env_file"
done
if [ "$LOCAL" -eq 1 ]; then
    info '.env-unused' '本地 dev 跳过'
elif [ -n "$ENV_ONLY" ]; then
    warn '.env-unused' "这些键没有任何 compose 引用，不会进容器：$ENV_ONLY"
else
    pass '.env-unused' '.env 的键都被 compose 引用'
fi

if [ -z "${NITROWATER_KEK:-}" ]; then
    fail 'NITROWATER_KEK' '未设置（account 启动失败）'
elif [ "${#NITROWATER_KEK}" -lt 40 ]; then
    warn 'NITROWATER_KEK' "长度 ${#NITROWATER_KEK}，32 字节 Base64 应为 44"
else
    pass 'NITROWATER_KEK' "len=${#NITROWATER_KEK}"
fi

DEV_SALT=''
ACCOUNT_YML="$REPO/nitrowater-account/src/main/resources/application.yml"
if [ -f "$ACCOUNT_YML" ]; then
    DEV_SALT="$(sed -n 's/.*DEVICE_SALT:\([^}]*\)}.*/\1/p' "$ACCOUNT_YML" | head -n 1)"
fi
if [ -z "${DEVICE_SALT:-}" ]; then
    fail 'DEVICE_SALT' '未设置（会用 dev 默认值，设备指纹可被伪造）'
elif [ -n "$DEV_SALT" ] && [ "$DEVICE_SALT" = "$DEV_SALT" ]; then
    fail 'DEVICE_SALT' '仍是 dev 默认值，生产必须换新随机值'
else
    pass 'DEVICE_SALT' '已覆盖 dev 默认值'
fi

ISSUER="${JWT_ISSUER:-}"
if [ -z "$ISSUER" ]; then
    fail 'JWT_ISSUER' '未设置（必须是 URL）'
elif ! printf '%s' "$ISSUER" | grep -Eq '^https?://'; then
    fail 'JWT_ISSUER' "不是 URL：$ISSUER"
elif [ "$LOCAL" -eq 0 ] && printf '%s' "$ISSUER" | grep -Eq 'localhost|127\.0\.0\.1'; then
    warn 'JWT_ISSUER' "生产仍指向本机：$ISSUER"
else
    pass 'JWT_ISSUER' "$ISSUER"
fi

DB_PASSWORD="${DB_PASSWORD:-}"
if [ -z "$DB_PASSWORD" ]; then
    fail 'DB_PASSWORD' '未设置'
elif [ "$LOCAL" -eq 0 ] && [ "$DB_PASSWORD" = '123456' ]; then
    fail 'DB_PASSWORD' '仍是 dev 默认值 123456'
else
    pass 'DB_PASSWORD' '已设置'
fi

# The app account is created by hand once (deploy/docs/bootstrap.md); root is bootstrap-only. A pool
# credential that owns every schema turns any SQL injection into a full-database compromise.
DB_USERNAME="${DB_USERNAME:-}"
if [ "$LOCAL" -eq 1 ]; then
    info 'DB_USERNAME' '本地 dev 用宿主机账号（Docker 上线必须是专用账号）'
elif [ -z "$DB_USERNAME" ]; then
    fail 'DB_USERNAME' '未设置（业务连库账号，见 deploy/docs/bootstrap.md）'
elif [ "$DB_USERNAME" = 'root' ]; then
    fail 'DB_USERNAME' 'root：改用 deploy/docs/bootstrap.md 建的专用账号'
elif is_placeholder "$DB_USERNAME"; then
    fail 'DB_USERNAME' '仍是模板占位值'
elif ! printf '%s' "$DB_USERNAME" | grep -Eq '^[A-Za-z0-9_]{1,32}$'; then
    fail 'DB_USERNAME' "只能是 [A-Za-z0-9_]：$DB_USERNAME"
else
    pass 'DB_USERNAME' "$DB_USERNAME"
fi

# MySQL root password is only used by the one-time manual bootstrap and by admin work.
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
if [ "$LOCAL" -eq 1 ]; then
    info 'MYSQL_ROOT_PASSWORD' '本地 dev 不跑 infra 容器，跳过'
elif [ -z "$MYSQL_ROOT_PASSWORD" ]; then
    fail 'MYSQL_ROOT_PASSWORD' '未设置（infra 初始化 MySQL 需要）'
elif is_placeholder "$MYSQL_ROOT_PASSWORD"; then
    fail 'MYSQL_ROOT_PASSWORD' '仍是模板占位值'
elif [ "$MYSQL_ROOT_PASSWORD" = '123456' ]; then
    fail 'MYSQL_ROOT_PASSWORD' '仍是 dev 默认值 123456'
elif [ "$MYSQL_ROOT_PASSWORD" = "$DB_PASSWORD" ]; then
    warn 'MYSQL_ROOT_PASSWORD' '与 DB_PASSWORD 相同（建议分成两个）'
else
    pass 'MYSQL_ROOT_PASSWORD' '已设置'
fi

BFF_SECRET="${BFF_CLIENT_SECRET:-}"
if [ -z "$BFF_SECRET" ]; then
    warn 'BFF_CLIENT_SECRET' '未设置（用 dev 默认值）'
elif [ "$LOCAL" -eq 0 ] && [ "$BFF_SECRET" = 'nitrowater-bff-secret' ]; then
    fail 'BFF_CLIENT_SECRET' '仍是 dev 默认值'
else
    pass 'BFF_CLIENT_SECRET' '已设置'
fi

# Redis holds BFF sessions (access + refresh tokens) and the account sessions, and it shares
# the compose network with Piston. Running it without AUTH in production is not acceptable.
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
if [ -z "$REDIS_PASSWORD" ]; then
    if [ "$LOCAL" -eq 1 ]; then
        info 'REDIS_PASSWORD' '未设置（本地 dev 允许无密码 Redis）'
    else
        fail 'REDIS_PASSWORD' '未设置：会话/令牌无认证，且与 Piston 同网段（评估 M4）'
    fi
elif is_placeholder "$REDIS_PASSWORD"; then
    fail 'REDIS_PASSWORD' '仍是模板占位值'
else
    pass 'REDIS_PASSWORD' '已设置'
fi

BACKEND="${RESOURCE_BACKEND:-local}"
if [ "$BACKEND" = 'local' ]; then
    # 字节落宿主机目录：既要在 .env 里声明，也要真实存在且可写（compose 用它做 bind mount）。
    if [ -z "${RESOURCE_DATA_DIR:-}" ]; then
        fail 'RESOURCE_DATA_DIR' 'local 后端必填（宿主机上的资源目录）'
    elif is_placeholder "$RESOURCE_DATA_DIR"; then
        fail 'RESOURCE_DATA_DIR' '仍是模板占位值'
    elif [ ! -d "$RESOURCE_DATA_DIR" ]; then
        fail 'RESOURCE_DATA_DIR' '目录不存在，先 mkdir -p 并确认备份策略'
    elif [ ! -w "$RESOURCE_DATA_DIR" ]; then
        fail 'RESOURCE_DATA_DIR' '目录不可写（容器以 root 写入，检查属主/挂载）'
    else
        pass 'RESOURCE_DATA_DIR' "$RESOURCE_DATA_DIR"
    fi
    if [ "${RESOURCE_PRESIGN_ENABLED:-false}" != 'false' ]; then
        warn 'RESOURCE_PRESIGN_ENABLED' 'local 后端不会签名，该开关对 local 无效'
    fi
elif [ "$BACKEND" = 'cos' ]; then
    # compose 无条件挂载这个目录，所以 cos 后端也必须声明（只是不再写入）。
    if [ -z "${RESOURCE_DATA_DIR:-}" ] || is_placeholder "$RESOURCE_DATA_DIR"; then
        fail 'RESOURCE_DATA_DIR' '必填（compose 的 bind mount，cos 后端同样要填）'
    else
        pass 'RESOURCE_DATA_DIR' "$RESOURCE_DATA_DIR（cos 不使用）"
    fi
    for name in COS_SECRET_ID COS_SECRET_KEY COS_BUCKET; do
        value="${!name:-}"
        if [ -z "$value" ] || is_placeholder "$value"; then
            fail "$name" 'COS 后端必填且不能是占位值'
        else
            pass "$name" "$value"
        fi
    done
    # 直连 COS 需要 bucket 域名浏览器可达且允许跨域 PUT/GET；默认 false 时全部经服务代理。
    if [ "${RESOURCE_PRESIGN_ENABLED:-false}" = 'true' ]; then
        warn 'RESOURCE_PRESIGN_ENABLED' 'COS 直连需确认 bucket CORS；否则保持 false（统一走 BFF 入口）'
    fi
else
    fail 'RESOURCE_BACKEND' "只支持 local / cos（当前：$BACKEND）"
fi

# ---------- 3. 条件变量 ----------
MODE="${COMMUNICATION_MODE:-mock}"
if [ "$MODE" = 'real' ]; then
    for name in RESEND_API_KEY MAIL_FROM \
        ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIYUN_SMS_SIGN_NAME ALIYUN_SMS_TEMPLATE_CODE; do
        if [ -z "${!name:-}" ]; then
            fail "$name" 'COMMUNICATION_MODE=real 时必填'
        else
            pass "$name" '已设置'
        fi
    done
else
    info 'COMMUNICATION_MODE' 'mock：验证码只写日志（生产建议 real）'
fi

if [ "$BACKEND" = 'cos' ]; then
    for name in COS_SECRET_ID COS_SECRET_KEY COS_REGION COS_BUCKET; do
        if [ -z "${!name:-}" ]; then
            fail "$name" 'RESOURCE_BACKEND=cos 时必填'
        else
            pass "$name" '已设置'
        fi
    done
fi

# ---------- 4. AI 凭据 ----------
if [ -n "${AI_CHAT_API_KEY:-}" ] || [ -n "${DEEPSEEK_API_KEY:-}" ]; then
    pass 'AI_CHAT_API_KEY' '已设置（或回落到 DEEPSEEK_API_KEY）'
else
    fail 'AI_CHAT_API_KEY' '未设置（也未见 DEEPSEEK_API_KEY），AI 接口不可用'
fi

if [ -n "${AI_EMBEDDING_API_KEY:-}" ] || [ -n "${SILICONFLOW_API_KEY:-}" ]; then
    pass 'AI_EMBEDDING_API_KEY' '已设置（或回落到 SILICONFLOW_API_KEY）'
else
    fail 'AI_EMBEDDING_API_KEY' '未设置（也未见 SILICONFLOW_API_KEY），索引无法重建'
fi

DIMS="${AI_EMBEDDING_DIMENSIONS:-}"
COLLECTION="${AI_COLLECTION:-nitrowater_velochatx_docs}"
if [ -n "$DIMS" ] && [ "$DIMS" != '1024' ] && [ "$COLLECTION" = 'nitrowater_velochatx_docs' ]; then
    warn 'AI_EMBEDDING_DIMENSIONS' "维度 $DIMS 与现有集合不匹配，请换 AI_COLLECTION 后重建"
elif [ -n "$DIMS" ]; then
    pass 'AI_EMBEDDING_DIMENSIONS' "$DIMS"
else
    pass 'AI_EMBEDDING_DIMENSIONS' '1024（默认）'
fi

# ---------- 5. 内容资产 ----------
DICT="${AI_MODERATION_DICT:-classpath:ai/moderation/words.txt}"
HAS_BASELINE=0
CHECKED_WORDS=0
IFS=',' read -r -a DICT_LOCATIONS <<< "$DICT"
for location in "${DICT_LOCATIONS[@]}"; do
    location="$(echo "$location" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [ -z "$location" ] && continue
    case "$location" in
        classpath:*) HAS_BASELINE=1; continue ;;
    esac
    host="$(resolve_host_path "$location")"
    if [ -z "$host" ]; then
        warn 'AI_MODERATION_DICT' "无法映射到宿主机路径：$location"
        continue
    fi
    count="$(count_entries "$host")"
    if [ "$count" -lt 0 ]; then
        fail 'words.txt' "文件不存在：$host"
    elif [ "$count" -eq 0 ]; then
        fail 'words.txt' "去注释后为空：$host（闸门等于关闭）"
    else
        pass 'words.txt' "$count 条（$host）"
    fi
    CHECKED_WORDS=1
done

if [ "$HAS_BASELINE" -eq 1 ]; then
    pass 'AI_MODERATION_DICT' '含 classpath 基线层'
else
    fail 'AI_MODERATION_DICT' '缺少 classpath 基线层：挂载丢失会让闸门静默失效（words=0）'
fi

if [ "$CHECKED_WORDS" -eq 0 ]; then
    BASELINE="$REPO/nitrowater-server/src/main/resources/ai/moderation/words.txt"
    count="$(count_entries "$BASELINE")"
    if [ "$count" -gt 0 ]; then
        pass 'words.txt' "classpath 基线 $count 条（未挂载增量词表）"
    elif [ -f "$BASELINE" ]; then
        fail 'words.txt' "classpath 基线为空：$BASELINE（闸门会默认失效）"
    elif [ "$LOCAL" -eq 1 ]; then
        fail 'words.txt' "classpath 基线缺失：$BASELINE"
    else
        # Deploy host keeps only deploy/: there is no source tree to count. The baseline ships
        # inside the server image as classpath:ai/moderation/words.txt, so its absence here says
        # nothing about the running service.
        info 'words.txt' '宿主机无源码树，classpath 基线由镜像内置；未挂载增量词表'
    fi
fi

ALLOW="${AI_MODERATION_ALLOW:-}"
if [ -n "$ALLOW" ]; then
    IFS=',' read -r -a ALLOW_LOCATIONS <<< "$ALLOW"
    for location in "${ALLOW_LOCATIONS[@]}"; do
        case "$location" in
            classpath:*|'') continue ;;
        esac
        host="$(resolve_host_path "$location")"
        [ -z "$host" ] && continue
        if [ -f "$host" ]; then
            pass 'allow.txt' "$host"
        else
            info 'allow.txt' "未提供（可选）：$host"
        fi
    done
fi

# 语料根：每个子目录 = 一个语料。宿主机侧固定 ${CONTENT_ROOT}/docs（compose 只挂这一份），
# 容器内路径由 AI_DOCS_ROOT 决定；bootRun 没有容器，它就是宿主机路径本身。
if [ "$LOCAL" -eq 1 ]; then
    case "${AI_DOCS_ROOT:-}" in
        ''|/etc/nitrowater/docs) DOCS_HOST="$REPO/deploy/content/docs"; DOCS_CONTAINER='' ;;
        *) DOCS_HOST="$(resolve_host_path "$AI_DOCS_ROOT")"; DOCS_CONTAINER="$AI_DOCS_ROOT" ;;
    esac
else
    DOCS_HOST="$CONTENT_ROOT/docs"
    DOCS_CONTAINER="${AI_DOCS_ROOT:-/etc/nitrowater/docs}"
fi
if [ ! -d "$DOCS_HOST" ]; then
    fail 'AI_DOCS_ROOT' "目录不存在：$DOCS_HOST（该目录下每个子目录是一个语料）"
else
    corpora=''; empty=''
    for dir in "$DOCS_HOST"/*; do
        [ -d "$dir" ] || continue
        name="$(basename "$dir")"
        case "$name" in .*|node_modules) continue ;; esac
        if [ -n "$(find "$dir" -type f -name '*.md' -print -quit)" ]; then
            corpora="$corpora $name"
        else
            empty="$empty $name"
        fi
    done
    if [ -z "$corpora" ]; then
        fail 'AI_DOCS_ROOT' "目录下没有任何含 .md 的语料：$DOCS_HOST"
    else
        target="$DOCS_HOST"
        [ -z "$DOCS_CONTAINER" ] || target="$DOCS_HOST → $DOCS_CONTAINER"
        pass 'AI_DOCS_ROOT' "语料：${corpora# }（$target）"
    fi
    [ -z "$empty" ] || warn 'AI_DOCS_ROOT' "没有 .md，已跳过：${empty# }"
fi

SKILLS_DIR="${AI_SKILLS_DIR:-}"
if [ -n "$SKILLS_DIR" ]; then
    info 'AI_SKILLS_DIR' '已废弃（服务不再读取），可从 .env 删除'
fi

# ---------- 6. 密钥 ----------
for file in private.key public.key; do
    if [ -f "$ROOT/keys/$file" ]; then
        pass "$file" "$ROOT/keys/$file"
    else
        fail "$file" "缺失：$ROOT/keys/$file（运行 deploy/bin/gen-keys.sh）"
    fi
done

# ---------- 7. 边缘 / 暴露面 ----------
BIND="${PUBLISH_BIND:-127.0.0.1}"
OPSB="${OPS_BIND:-127.0.0.1}"
if [ "$BIND" = '0.0.0.0' ] && [ "$LOCAL" -eq 0 ]; then
    warn 'PUBLISH_BIND' '0.0.0.0：业务端口对整个网段开放（生产应保持 127.0.0.1 + 边缘层）'
else
    pass 'PUBLISH_BIND' "$BIND"
fi
if [ "$OPSB" = '0.0.0.0' ] && [ "$LOCAL" -eq 0 ]; then
    fail 'OPS_BIND' '0.0.0.0：Qdrant 管理面无认证，不能对网络开放'
else
    pass 'OPS_BIND' "$OPSB"
fi

if [ "$LOCAL" -eq 0 ]; then
    WEB_DOMAIN="${WEB_DOMAIN:-}"; AUTH_DOMAIN="${AUTH_DOMAIN:-}"; SSL_EMAIL="${SSL_EMAIL:-}"
    if [ -z "$WEB_DOMAIN" ] && [ -z "$AUTH_DOMAIN" ] && [ -z "$SSL_EMAIL" ]; then
        warn 'edge' '未配置 WEB_DOMAIN / AUTH_DOMAIN / SSL_EMAIL：无公网入口（未叠加 edge）'
    else
        missing=''
        [ -z "$WEB_DOMAIN" ] && missing="$missing WEB_DOMAIN"
        [ -z "$AUTH_DOMAIN" ] && missing="$missing AUTH_DOMAIN"
        [ -z "$SSL_EMAIL" ] && missing="$missing SSL_EMAIL"
        if [ -n "$missing" ]; then
            fail 'edge' "缺：$missing（叠加 edge 时必须齐全）"
        else
            pass 'edge' "web=$WEB_DOMAIN auth=$AUTH_DOMAIN"
        fi
        if [ -n "$SSL_EMAIL" ] && ! printf '%s' "$SSL_EMAIL" | grep -Eq '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'; then
            fail 'SSL_EMAIL' "不是有效邮箱：$SSL_EMAIL"
        fi
        if [ "${SESSION_COOKIE_SECURE:-}" != 'true' ]; then
            fail 'SESSION_COOKIE_SECURE' 'HTTPS 部署必须为 true（否则会话 cookie 仍可走 http）'
        else
            pass 'SESSION_COOKIE_SECURE' 'true'
        fi
    fi
    if [ "${RESOURCE_UPLOAD_ENABLED:-true}" = 'false' ]; then
        warn 'RESOURCE_UPLOAD_ENABLED' '上传总闸已关（/api/resource/upload/** 一律 503）；仅插件发布可用'
    else
        pass 'RESOURCE_UPLOAD_ENABLED' 'true（仅 ADMIN / SUPER_ADMIN 可上传）'
    fi
    if [ -n "${WEB_DOMAIN:-}" ]; then
        EXPECTED="https://${WEB_DOMAIN}/login/oauth2/code/nitrowater"
        REDIRECTS="${BFF_REDIRECT_URIS:-}"
        if [ -z "$REDIRECTS" ]; then
            warn 'BFF_REDIRECT_URIS' "未设置：默认值只含 localhost，登录回调会失败（应含 $EXPECTED）"
        elif ! printf '%s' "$REDIRECTS" | grep -q 'https://'; then
            fail 'BFF_REDIRECT_URIS' '生产回调地址必须是 https'
        elif ! printf '%s' "$REDIRECTS" | grep -q "$WEB_DOMAIN"; then
            warn 'BFF_REDIRECT_URIS' "未包含生产域名 $WEB_DOMAIN"
        else
            pass 'BFF_REDIRECT_URIS' "含 $WEB_DOMAIN"
        fi
        if [ -z "${SSO_DEFAULT_REDIRECT:-}" ]; then
            warn 'SSO_DEFAULT_REDIRECT' '未设置：登录后默认跳 localhost:5173'
        else
            pass 'SSO_DEFAULT_REDIRECT' "$SSO_DEFAULT_REDIRECT"
        fi
    fi
    case "$ISSUER" in
        ''|http://localhost*) : ;;
        http://*) warn 'JWT_ISSUER' "生产建议使用 https：$ISSUER" ;;
    esac
fi

# ---------- 8. 文档站镜像 ----------
# 文档站内容（各插件站）来自独立仓库 nitrowater-plugin-docs 的镜像。它和 app 其余
# 服务在同一个 compose 单元里，所以 ref 写错会在 `docker compose pull` 时把整次发布拖垮 ——
# 在这里提前说话，别等到 pull 阶段。
DOCS_IMAGE_REPO="${DOCS_IMAGE_REPO:-}"
DOCS_IMAGE_TAG="${DOCS_IMAGE_TAG:-latest}"
case "$DOCS_IMAGE_REPO" in
    '')
        warn 'DOCS_IMAGE_REPO' '未配置：会回落到占位值 your-org/nitrowater-plugin-docs（app 单元 pull 会失败）'
        ;;
    */*)
        if is_placeholder "$DOCS_IMAGE_REPO"; then
            warn 'DOCS_IMAGE_REPO' "仍是模板占位值：$DOCS_IMAGE_REPO"
        else
            pass 'DOCS_IMAGE_REPO' "$DOCS_IMAGE_REPO"
        fi
        ;;
    *)
        fail 'DOCS_IMAGE_REPO' "需要 owner/repo 形式（如 danburen/nitrowater-plugin-docs）：$DOCS_IMAGE_REPO"
        ;;
esac
case "$DOCS_IMAGE_TAG" in
    '')     fail 'DOCS_IMAGE_TAG' '为空（留空会拼出 …/repo: 这种非法 ref）' ;;
    latest) info 'DOCS_IMAGE_TAG' 'latest：能跑，但没有回滚点；建议写死版本号（v1.2.3）' ;;
    *[!A-Za-z0-9._-]*) fail 'DOCS_IMAGE_TAG' "含非法字符：$DOCS_IMAGE_TAG" ;;
    *)      pass 'DOCS_IMAGE_TAG' "$DOCS_IMAGE_TAG" ;;
esac

# ---------- 9. 本地端口 ----------
if [ "$LOCAL" -eq 1 ]; then
    if command -v nc >/dev/null 2>&1; then
        for entry in MySQL:3306 Redis:6379 Qdrant:6333; do
            name="${entry%%:*}"; port="${entry##*:}"
            if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
                pass "$name" "127.0.0.1:$port"
            else
                fail "$name" "127.0.0.1:$port 不可达"
            fi
        done
    else
        info 'ports' '未找到 nc，跳过端口检查'
    fi
fi

echo '--------------------------------------------------------------------'
echo ''
echo "Pass: $PASS | Warn: $WARN | Fail: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo ''
    echo '  [FAIL] 存在阻断项，修好再上线。变量与拓扑见 deploy/README.md 与 deploy/docs/'
    echo '         内容挂载见 deploy/content/README.md'
    exit 1
fi
echo '  [OK] 无阻断项。'
if [ "$WARN" -gt 0 ]; then
    echo '  [WARN] 有警告项，确认可接受后再上线。'
fi
exit 0
