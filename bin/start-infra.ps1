# ============================================================
# start-infra.ps1 - One-click start of ALL infra services (local dev).
#
#   MySQL      3306 : Windows service 'MySQL'                 (no window)
#   Redis      6379 : redis-server.exe                        -> own console window
#   SeaweedFS  8333 : docker container nitrowater-seaweedfs   -> docker logs -f window
#   Piston     2000 : docker container nitrowater-piston      -> docker logs -f window
#   docs       8085 : docker container nitrowater-docs        -> docker logs -f window
#                     (nginx hosting ALL plugin docs; the BFF forwards /plugin/** here)
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
$MysqlService = 'MySQL'

# ---- MySQL (Windows service; no window) ----
Write-Step 'MySQL (3306)'
if (Test-Port 3306) { Write-Ok 'already up' }
else {
    try { Start-Service $MysqlService -ErrorAction Stop; Write-Ok "service '$MysqlService' started" }
    catch { Write-Err "could not start service '$MysqlService': $($_.Exception.Message)" }
    if (Wait-Port 3306 40) { Write-Ok 'up (3306)' } else { Write-Err 'MySQL not reachable on 3306' }
}

# ---- Redis (own window; launched from its install dir so no path quoting is needed) ----
Write-Step 'Redis (6379)'
if (Test-Port 6379) { Write-Ok 'already up' }
elseif (-not (Test-Path -LiteralPath $RedisServer)) { Write-Err "redis-server.exe not found: $RedisServer" }
else {
    $conf = if (Test-Path -LiteralPath $RedisConf) { Split-Path $RedisConf -Leaf } else { '' }
    Save-WindowPid 'redis' (Start-Window 'NitroWater - Redis' "redis-server.exe $conf" $RedisDir)
    if (Wait-Port 6379 20) { Write-Ok 'up (6379)' } else { Write-Warn 'Redis not reachable on 6379 yet (see its window)' }
}

# ---- Docker containers (+ live log windows); Ensure-DockerContainer comes from _common.ps1 ----
Ensure-DockerContainer 'nitrowater-seaweedfs' 8333 'NitroWater - SeaweedFS' {
    & docker run -d --name nitrowater-seaweedfs `
        -p 8333:8333 -p 8888:8888 -p 9333:9333 -p 23646:23646 `
        -v nitrowater-seaweedfs-data:/data `
        -e AWS_ACCESS_KEY_ID=nitrowater -e AWS_SECRET_ACCESS_KEY=change-me -e S3_BUCKET=nitrowater `
        chrislusf/seaweedfs | Out-Null
}

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
#   docker pull docker.m.daocloud.io/qdrant/qdrant:v1.18.3
#   docker tag  docker.m.daocloud.io/qdrant/qdrant:v1.18.3 qdrant/qdrant:v1.18.3
Ensure-DockerContainer 'nitrowater-qdrant' 6334 'NitroWater - Qdrant' {
    & docker run -d --name nitrowater-qdrant `
        -p 6333:6333 -p 6334:6334 `
        -v nitrowater-qdrant-data:/qdrant/storage `
        qdrant/qdrant:v1.18.3 | Out-Null
}

# ---- docs: ONE nginx container hosting every plugin's documentation (127.0.0.1:8085) ----
# The BFF only proxies /plugin/** here (app.plugin-docs.upstream default = http://localhost:8085).
# Created from compose (mounts + nginx config live there); started by name when it already exists.
$composeFile = Join-Path $RepoRoot 'deploy/docker/docker-compose.app.yml'
Ensure-DockerContainer 'nitrowater-docs' 8085 'NitroWater - docs' {
    # compose declares `nitrowater-net` as external, so it must exist before `up`.
    & docker network inspect nitrowater-net *> $null
    if ($LASTEXITCODE -ne 0) { & docker network create nitrowater-net *> $null }
    & docker compose -f $composeFile up -d docs *> $null
}

Write-Step 'Infra ready'
Write-Host '  MySQL 3306 | Redis 6379 | Qdrant 6334 | SeaweedFS 8333 | Piston 2000 | docs 8085' -ForegroundColor Gray
Write-Host '  Each component has its own console window showing logs.' -ForegroundColor Gray
