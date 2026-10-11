#!/usr/bin/env bash
# ============================================================
# nitrowater RSA Key Generation (Bash — Linux/macOS)
# 生成 RSA 2048 密钥对，供 JWT RS256 签名/验签。
#
# 用法:
#   bash deploy/bin/gen-keys.sh
#
# 输出:
#   deploy/keys/private.key   私钥 (签名)
#   deploy/keys/public.key    公钥 (验签)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEY_DIR="$SCRIPT_DIR/../keys"

mkdir -p "$KEY_DIR"

echo "Generating RSA 2048-bit key pair..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEY_DIR/private.key"
openssl rsa -pubout -in "$KEY_DIR/private.key" -out "$KEY_DIR/public.key"

echo "Key pair generated successfully!"
echo "  Private: $KEY_DIR/private.key"
echo "  Public : $KEY_DIR/public.key"
echo ""
echo "application.yml 默认引用 file:./deploy/keys/private.key (bootRun workingDir=仓库根)"
