#!/usr/bin/env bash
# ============================================================
# ops-tunnel.sh — 把运维 UI 通过 SSH 隧道映射到本机
#
# 生产里 Qdrant / SeaweedFS 的端口只绑 127.0.0.1（OPS_BIND），因为这些 UI 自身没有认证。
# 隧道让你用普通浏览器访问，而不用把任何端口暴露到网络：
#
#   http://127.0.0.1:6333/dashboard   Qdrant（集合 / 点数 / payload / 过滤查询）
#   http://127.0.0.1:8888             SeaweedFS filer（浏览 / 下载 / 删除文件）
#   http://127.0.0.1:9333             SeaweedFS master（拓扑 / 卷）
#
# 用法：
#   bash deploy/bin/ops-tunnel.sh --host ops.example.com
#   bash deploy/bin/ops-tunnel.sh --host 1.2.3.4 --user ubuntu --ssh-port 2222
#   bash deploy/bin/ops-tunnel.sh --host ops.example.com -i ~/.ssh/id_ed25519
#   bash deploy/bin/ops-tunnel.sh --host ops.example.com --no-filer --no-master
#
# 注意：
#   - 本地端口与远端一致，书签不用改；
#   - 关闭窗口（Ctrl+C）即断开；
#   - 远端固定用 127.0.0.1（localhost 可能解析成 ::1 而失败）。
# ============================================================
set -uo pipefail

HOST=''; USER_NAME='root'; SSH_PORT=22; IDENTITY=''
QD_PORT=6333; FILER_PORT=8888; MASTER_PORT=9333
USE_QD=1; USE_FILER=1; USE_MASTER=1

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST="${2:-}"; shift 2 ;;
        --user) USER_NAME="${2:-}"; shift 2 ;;
        --ssh-port) SSH_PORT="${2:-}"; shift 2 ;;
        -i|--identity) IDENTITY="${2:-}"; shift 2 ;;
        --no-qdrant) USE_QD=0; shift ;;
        --no-filer) USE_FILER=0; shift ;;
        --no-master) USE_MASTER=0; shift ;;
        -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1"; exit 2 ;;
    esac
done

if [ -z "$HOST" ]; then
    echo 'ERROR: --host is required (e.g. --host ops.example.com)'
    exit 2
fi
command -v ssh >/dev/null 2>&1 || { echo 'ERROR: ssh not found'; exit 1; }

FORWARDS=()
[ "$USE_QD" -eq 1 ] && FORWARDS+=(-L "${QD_PORT}:127.0.0.1:${QD_PORT}")
[ "$USE_FILER" -eq 1 ] && FORWARDS+=(-L "${FILER_PORT}:127.0.0.1:${FILER_PORT}")
[ "$USE_MASTER" -eq 1 ] && FORWARDS+=(-L "${MASTER_PORT}:127.0.0.1:${MASTER_PORT}")
if [ "${#FORWARDS[@]}" -eq 0 ]; then
    echo 'ERROR: every tunnel was disabled'
    exit 1
fi

echo '=== ops tunnels ==='
[ "$USE_QD" -eq 1 ] && echo "  Qdrant     http://127.0.0.1:${QD_PORT}/dashboard"
[ "$USE_FILER" -eq 1 ] && echo "  Filer      http://127.0.0.1:${FILER_PORT}"
[ "$USE_MASTER" -eq 1 ] && echo "  Master     http://127.0.0.1:${MASTER_PORT}"
echo "  ssh target ${USER_NAME}@${HOST}:${SSH_PORT}"
echo '  Ctrl+C to close.'
echo ''

IDENTITY_ARGS=()
[ -n "$IDENTITY" ] && IDENTITY_ARGS=(-i "$IDENTITY")

exec ssh -N \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -p "$SSH_PORT" \
    "${IDENTITY_ARGS[@]}" \
    "${FORWARDS[@]}" \
    "${USER_NAME}@${HOST}"
