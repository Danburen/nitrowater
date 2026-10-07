# ============================================================
# nitrowater 环境自检 (PowerShell)
# 检查：MySQL/Redis 端口、JWT 密钥、WATERFUN_KEK、DEVICE_SALT
#
# 用法:
#   .\deploy\bin\check-env.ps1
# ============================================================

$ErrorActionPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent            # deploy/
$repo = Split-Path $root -Parent                    # 仓库根

function Test-Port([string]$name, [int]$port) {
    $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
    $tag = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [{0}] {1} 127.0.0.1:{2}" -f $tag, $name, $port)
}

Write-Host "=== ports ===" -ForegroundColor White
Test-Port 'MySQL' 3306
Test-Port 'Redis' 6379

Write-Host "=== JWT keys ===" -ForegroundColor White
foreach ($f in 'private.key', 'public.key') {
    $p = Join-Path $root "keys\$f"
    $tag = if (Test-Path -LiteralPath $p) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [{0}] {1}" -f $tag, $p)
}

Write-Host "=== env ===" -ForegroundColor White
if ([string]::IsNullOrEmpty($env:WATERFUN_KEK)) {
    Write-Host "  [WARN] WATERFUN_KEK not set (bootRun 会回退到 build.gradle 开发默认值)" -ForegroundColor Yellow
} else {
    Write-Host ("  [PASS] WATERFUN_KEK set (len={0})" -f $env:WATERFUN_KEK.Length) -ForegroundColor Green
}

$deviceSaltVal = $env:DEVICE_SALT
$yml = Join-Path $repo 'nitrowater-account\src\main\resources\application.yml'
if (Test-Path -LiteralPath $yml) {
    $hasSalt = Select-String -LiteralPath $yml -Pattern '^\s*salt:' -Quiet
    $tag = if ($hasSalt) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [{0}] device.salt 已在 application.yml 配置" -f $tag)
}

Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
