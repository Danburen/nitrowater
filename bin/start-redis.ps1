# ============================================================
# start-redis.ps1 - start the local Redis (dev, 127.0.0.1:6379) in its own console window.
#
# Usage:  pwsh -File bin\start-redis.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$RedisDir    = if ($env:NITROWATER_REDIS_DIR) { $env:NITROWATER_REDIS_DIR } else { 'E:\Program Files\Redis' }
$RedisServer = Join-Path $RedisDir 'redis-server.exe'
$RedisConf   = Join-Path $RedisDir 'redis.conf'

Write-Step 'Redis (6379)'
if (Test-Port 6379) { Write-Ok 'already up' }
elseif (-not (Test-Path -LiteralPath $RedisServer)) { Write-Err "redis-server.exe not found: $RedisServer" }
else {
    $conf = if (Test-Path -LiteralPath $RedisConf) { Split-Path $RedisConf -Leaf } else { '' }
    Save-WindowPid 'redis' (Start-Window 'NitroWater - Redis' "redis-server.exe $conf" $RedisDir)
    if (Wait-Port 6379 20) { Write-Ok 'up (6379)' } else { Write-Warn 'not reachable on 6379 yet (see its window)' }
}
