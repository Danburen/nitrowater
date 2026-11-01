#!/usr/bin/env bash
# ============================================================
# NitroWater 上线环境自检 (Linux / macOS)
#
# 与 check-env.ps1 对齐：.env 加载、必需变量与占位值、条件变量、
# 内容资产（词表双层 + 非空 / 文档语料 / 外部 skills）、密钥、端口。
#
# 用法：
#   bash deploy/bin/check-env.sh              # Docker 上线
#   bash deploy/bin/check-env.sh --local      # 本地 dev（额外检查端口）
#   ENV_FILE=deploy/docker/.env bash deploy/bin/check-env.sh
#
# 退出码：0 = 无 FAIL；1 = 存在 FAIL
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"

LOCAL=0
for arg in "$@"; do
    case "$arg" in
        --local) LOCAL=1 ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    esac
done

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
ENV_PATH="${ENV_FILE:-$ROOT/docker/.env}"
if [ -f "$ENV_PATH" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        trimmed="${line#"${line%%[![:space:]]*}"}"
        case "$trimmed" in ''|'#'*) continue ;; esac
        key="${trimmed%%=*}"
        case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac
        value="${trimmed#*=}"
        value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
        if [ -z "${!key:-}" ]; then
            export "$key=$value"
        fi
    done < "$ENV_PATH"
    info '.env' "已加载 $ENV_PATH"
else
    warn '.env' "未找到 $ENV_PATH（仅用进程环境变量；Docker 上线应存在）"
fi

CONTENT_ROOT="${CONTENT_ROOT:-$ROOT/content}"

# 把容器内挂载路径映射回宿主机目录，确保校验的是真正会被挂载的那份文件。
resolve_host_path() {
    local path="${1#file:}"
    case "$path" in
        /etc/nitrowater/moderation*) echo "$CONTENT_ROOT/moderation${path#/etc/nitrowater/moderation}"; return ;;
        /etc/nitrowater/docs*) echo "$CONTENT_ROOT/docs${path#/etc/nitrowater/docs}"; return ;;
        /etc/nitrowater/skills*) echo "$CONTENT_ROOT/skills${path#/etc/nitrowater/skills}"; return ;;
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

echo '=== NitroWater 上线环境自检 ==='
if [ "$LOCAL" -eq 1 ]; then echo 'mode: local (dev)'; else echo 'mode: docker (prod)'; fi
echo ''

echo 'Result:'
echo '--------------------------------------------------------------------'

# ---------- 2. 必需变量 / 占位值 ----------
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

BFF_SECRET="${BFF_CLIENT_SECRET:-}"
if [ -z "$BFF_SECRET" ]; then
    warn 'BFF_CLIENT_SECRET' '未设置（用 dev 默认值）'
elif [ "$LOCAL" -eq 0 ] && [ "$BFF_SECRET" = 'nitrowater-bff-secret' ]; then
    fail 'BFF_CLIENT_SECRET' '仍是 dev 默认值'
else
    pass 'BFF_CLIENT_SECRET' '已设置'
fi

BACKEND="${RESOURCE_BACKEND:-s3}"
if [ "$BACKEND" = 's3' ]; then
    for name in S3_ACCESS_KEY S3_SECRET_KEY S3_BUCKET; do
        value="${!name:-}"
        if [ -z "$value" ] || [ "$value" = 'change-me' ]; then
            fail "$name" 'S3 后端必填且不能是占位值'
        else
            pass "$name" "$value"
        fi
    done
fi

# ---------- 3. 条件变量 ----------
MODE="${COMMUNICATION_MODE:-mock}"
if [ "$MODE" = 'real' ]; then
    for name in MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_FROM \
        ALIYUN_SMS_ACCESS_KEY_ID ALIYUN_SMS_ACCESS_KEY_SECRET ALIYUN_SMS_SIGN_NAME ALIYUN_SMS_TEMPLATE_CODE; do
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
    count="$(count_entries "$REPO/nitrowater-server/src/main/resources/ai/moderation/words.txt")"
    if [ "$count" -gt 0 ]; then
        pass 'words.txt' "classpath 基线 $count 条（未挂载增量词表）"
    else
        fail 'words.txt' 'classpath 基线缺失或为空'
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

DOCS_LOCATION="${AI_DOCS_LOCATION:-}"
if [ -z "$DOCS_LOCATION" ]; then
    info 'AI_DOCS_LOCATION' '未设置：文档语料取镜像内 classpath'
else
    case "$DOCS_LOCATION" in
        classpath:*) info 'AI_DOCS_LOCATION' "classpath 语料：$DOCS_LOCATION" ;;
        *)
            host="$(resolve_host_path "$DOCS_LOCATION")"
            if [ -z "$host" ]; then
                warn 'AI_DOCS_LOCATION' "无法映射到宿主机路径：$DOCS_LOCATION"
            elif [ ! -d "$host" ]; then
                fail 'AI_DOCS_LOCATION' "目录不存在：$host"
            else
                md_count="$(find "$host" -type f -name '*.md' | wc -l | tr -d ' ')"
                if [ "$md_count" -eq 0 ]; then
                    fail 'AI_DOCS_LOCATION' "目录内没有 .md：$host（该 corpus 重建后会从索引消失）"
                else
                    pass 'AI_DOCS_LOCATION' "$md_count 个 md（$host）"
                fi
            fi
            ;;
    esac
fi

SKILLS_DIR="${AI_SKILLS_DIR:-}"
if [ -n "$SKILLS_DIR" ]; then
    host="$(resolve_host_path "$SKILLS_DIR")"
    if [ -n "$host" ] && [ -d "$host" ]; then
        skills="$(find "$host" -type f -name 'SKILL.md' | wc -l | tr -d ' ')"
        info 'AI_SKILLS_DIR' "$skills 个外部技能（$host）"
    elif [ -n "$host" ]; then
        warn 'AI_SKILLS_DIR' "目录不存在：$host（内置技能仍可用）"
    fi
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
    fail 'OPS_BIND' '0.0.0.0：Qdrant / SeaweedFS 管理面无认证，不能对网络开放'
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
            warn 'SESSION_COOKIE_SECURE' 'HTTPS 部署应为 true（否则会话 cookie 仍可走 http）'
        else
            pass 'SESSION_COOKIE_SECURE' 'true'
        fi
    fi
    if [ "${RESOURCE_USER_UPLOAD:-true}" != 'false' ]; then
        warn 'RESOURCE_USER_UPLOAD' '用户上传仍开启：需审核的内容尚未上线，生产建议 false（接口保留，返回 503）'
    else
        pass 'RESOURCE_USER_UPLOAD' 'false（仅保留接口）'
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

# ---------- 8. 本地端口 ----------
if [ "$LOCAL" -eq 1 ]; then
    if command -v nc >/dev/null 2>&1; then
        for entry in MySQL:3306 Redis:6379 SeaweedFS:8333 Qdrant:6333; do
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
    echo '  [FAIL] 存在阻断项，修好再上线。变量含义见 deploy/env-vars-reference.md'
    echo '         内容挂载见 deploy/content/README.md'
    exit 1
fi
echo '  [OK] 无阻断项。'
if [ "$WARN" -gt 0 ]; then
    echo '  [WARN] 有警告项，确认可接受后再上线。'
fi
exit 0
