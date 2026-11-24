# ============================================================
# start-infra.ps1 - One-click start of ALL infra services (local dev).
#
#   MySQL      3306 : Windows service 'MySQL84'/'MySQL'      (no window; auto-detected)
#   Redis      6379 : docker container nitrowater-redis       -> docker logs -f window
#                     (redis:8.10.3-alpine - same major as production)
#   Piston     2000 : docker container nitrowater-piston      -> docker logs -f window
#
# Resource bytes need no infra: the resource service writes them to a local directory
# (RESOURCE_LOCAL_PATH, default ./bin/dev/resource-storage).
#
# Idempotent: anything already listening on its port is left as-is.
# Every non-service component keeps its own console window (cmd /k) so its log stays visible.
#
# Usage:  pwsh -File bin\start-infra.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

# Shared helpers: $RunDir, Write-Step/Ok/Warn/Err, Test-Port, Wait-Port, Test-Docker,
# Start-Window, Save-WindowPid, Ensure-DockerContainer.
. "$PSScriptRoot\_common.ps1"

# ---- config (dev defaults; override with env vars if needed) ----
$RedisDir     = if ($env:NITROWATER_REDIS_DIR) { $env:NITROWATER_REDIS_DIR } else { 'E:\Program Files\Redis' }
$RedisServer  = Join-Path $RedisDir 'redis-server.exe'
$RedisConf    = Join-Path $RedisDir 'redis.conf'
# Service name follows the installed server (MySQL84 after the 8.4 LTS upgrade).
$MysqlService = @('MySQL84', 'MySQL8.4', 'MySQL', 'MySQL80') |
    Where-Object { Get-Service -Name $_ -ErrorAction SilentlyContinue } |
    Select-Object -First 1
if (-not $MysqlService) { $MysqlService = 'MySQL' }

# ---- MySQL (Windows service; no window) ----
Write-Step 'MySQL (3306)'
if (Test-Port 3306) { Write-Ok 'already up' }
else {
    try { Start-Service $MysqlService -ErrorAction Stop; Write-Ok "service '$MysqlService' started" }
    catch { Write-Err "could not start service '$MysqlService': $($_.Exception.Message)" }
    if (Wait-Port 3306 40) { Write-Ok 'up (3306)' } else { Write-Err 'MySQL not reachable on 3306' }
}

# ---- Redis (docker: redis:8.10.3-alpine) ----
# Dev runs the same major as production, so the Windows port (stuck on 5.x) is retired.
# A redis-server.exe still holding 6379 is stopped first - otherwise the container cannot bind.
Write-Step 'Redis (6379, docker)'
$RedisImage = 'redis:8.10.3-alpine'
$holder = Get-NetTCPConnection -State Listen -LocalPort 6379 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($holder) {
    $proc = Get-Process -Id $holder.OwningProcess -ErrorAction SilentlyContinue
    if ($proc -and $proc.ProcessName -eq 'redis-server') {
        Write-Warn "stopping local redis-server.exe (pid $($proc.Id)) so the container can bind 6379"
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        $release = (Get-Date).AddSeconds(10)
        while ((Test-Port 6379) -and ((Get-Date) -lt $release)) { Start-Sleep -Milliseconds 300 }
    }
}
Ensure-DockerContainer 'nitrowater-redis' 6379 'NitroWater - Redis' {
    & docker rm -f nitrowater-redis *> $null
    & docker run -d --name nitrowater-redis `
        -p 6379:6379 `
        -v nitrowater-redis-dev:/data `
        $RedisImage redis-server --appendonly yes | Out-Null
} -Recreate

# ---- Docker containers (+ live log windows); Ensure-DockerContainer comes from _common.ps1 ----
Ensure-DockerContainer 'nitrowater-piston' 2000 'NitroWater - Piston' {
    & docker rm -f nitrowater-piston *> $null
    & docker run -d --name nitrowater-piston `
        --privileged -p 2000:2000 -v nitrowater-piston:/piston `
        -e PISTON_RUN_TIMEOUT=10000 -e PISTON_COMPILE_TIMEOUT=20000 `
        -e PISTON_RUN_MEMORY_LIMIT=268435456 -e PISTON_COMPILE_MEMORY_LIMIT=536870912 `
        -e PISTON_OUTPUT_MAX_SIZE=1048576 -e PISTON_MAX_CONCURRENT_JOBS=8 `
        -e LANG=en_US.UTF-8 -e LC_ALL=en_US.UTF-8 `
        ghcr.io/engineer-man/piston | Out-Null
}

# ---- Qdrant: third-party vector index for the documentation assistant (:6334 gRPC) ----
# If Docker Hub is unreachable, pull through a mirror first and tag it locally, e.g.
#   docker pull docker.m.daocloud.io/qdrant/qdrant:v1.20.1
#   docker tag  docker.m.daocloud.io/qdrant/qdrant:v1.20.1 qdrant/qdrant:v1.20.1
Ensure-DockerContainer 'nitrowater-qdrant' 6334 'NitroWater - Qdrant' {
    & docker run -d --name nitrowater-qdrant `
        -p 6333:6333 -p 6334:6334 `
        -v nitrowater-qdrant-data:/qdrant/storage `
        qdrant/qdrant:v1.20.1 | Out-Null
}

Write-Step 'Infra ready'
Write-Host '  MySQL 3306 | Redis 6379 | Qdrant 6334 | Piston 2000' -ForegroundColor Gray
Write-Host '  Resource bytes: local dir (RESOURCE_LOCAL_PATH), no extra service.' -ForegroundColor Gray
Write-Host '  Each component has its own console window showing logs.' -ForegroundColor Gray
