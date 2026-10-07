# ============================================================
# nitrowater RSA Key Generation (PowerShell)
# 生成 RSA 2048 密钥对，供 JWT RS256 签名/验签。
#
# 用法:
#   .\deploy\bin\gen-keys.ps1
#
# 输出:
#   deploy/keys/private.key   Private key (签名)
#   deploy/keys/public.key    Public  key (验签)
# ============================================================

$DeployKeyDir = Resolve-Path "$PSScriptRoot\..\keys" -ErrorAction SilentlyContinue
if (-not $DeployKeyDir) {
    $DeployKeyDir = Join-Path $PSScriptRoot "..\keys"
}
if (-not (Test-Path -LiteralPath $DeployKeyDir)) {
    New-Item -ItemType Directory -Path $DeployKeyDir -Force | Out-Null
}

Write-Host "Generating RSA 2048-bit key pair..."
& openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DeployKeyDir\private.key" 2>&1 | Out-Null
& openssl rsa -pubout -in "$DeployKeyDir\private.key" -out "$DeployKeyDir\public.key" 2>&1 | Out-Null

Write-Host "Key pair generated successfully!"
Write-Host "  Private: $DeployKeyDir\private.key"
Write-Host "  Public : $DeployKeyDir\public.key"
Write-Host ""
Write-Host "application.yml 默认引用 file:./deploy/keys/private.key (bootRun workingDir=仓库根)"
