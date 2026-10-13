# ============================================================
# stop-all.ps1 - Stop everything started by start-infra / start-apps.
#
#   - app/tool listeners : 8090 account, 8080 bff, 8081 server, 8093 resource,
#                          5173 web, 6379 redis
#   - docker containers  : nitrowater-piston, nitrowater-seaweedfs
#   - console windows    : the exact PIDs recorded by start-*.ps1 in bin\.run\*.pid
#   - MySQL              : left running by default (system service); -StopMysql to stop it
#
# Usage:
#   pwsh -File bin\stop-all.ps1
#   pwsh -File bin\stop-all.ps1 -StopMysql
#   (or double-click bin\stop-all.bat)
# ============================================================
[CmdletBinding()]
param(
    [switch]$StopMysql
)

$MysqlService = 'MySQL'

# Shared helpers: Write-*, Test-Port, $RunDir. See _common.ps1 for why we must NEVER
# kill by window title with a wildcard (it wiped the desktop once).
. "$PSScriptRoot\_common.ps1"

function Stop-ByPort([int]$Port) {
    Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

$targets = [ordered]@{
    8090 = 'account'; 8080 = 'bff'; 8081 = 'server'; 8093 = 'resource'; 5173 = 'web'; 6379 = 'redis'
}

Write-Step 'Stopping listeners (apps / web / redis)'
foreach ($port in $targets.Keys) {
    if (Test-Port $port) {
        Stop-ByPort $port
        Start-Sleep -Milliseconds 300
        if (Test-Port $port) { Write-Warn "$($targets[$port]) ($port) still listening" }
        else { Write-Ok "stopped $($targets[$port]) ($port)" }
    } else { Write-Host "  [ - ]  $($targets[$port]) ($port) not listening" -ForegroundColor DarkGray }
}

Write-Step 'Stopping docker containers'
try {
    & docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        foreach ($c in @('nitrowater-piston', 'nitrowater-seaweedfs')) {
            $names = @(& docker ps -a --format '{{.Names}}')
            if ($names -contains $c) { & docker stop $c | Out-Null; Write-Ok "stopped container $c" }
            else { Write-Host "  [ - ]  $c not present" -ForegroundColor DarkGray }
        }
    } else { Write-Warn 'Docker engine not running; skip' }
} catch { Write-Warn 'Docker not available; skip' }

Write-Step 'Closing service windows'
# PRIMARY: kill the exact PIDs recorded by start-infra/start-apps (bin\.run\*.pid).
# NEVER kill by window title with a wildcard: a "WINDOWTITLE eq NitroWater*" filter is a
# PREFIX match (case-insensitive) that also matches any window starting with "nitrowater" -
# the IDE, the browser, a File Explorer window at the project root. With /T /F that killed
# explorer.exe and wiped the desktop/taskbar.
$closed = 0
if (Test-Path -LiteralPath $RunDir) {
    foreach ($f in Get-ChildItem -LiteralPath $RunDir -Filter '*.pid' -ErrorAction SilentlyContinue) {
        $val = (Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue)
        if ($null -ne $val) { $val = $val.Trim() }
        if ($val -match '^\d+$') {
            # We only ever record cmd.exe wrapper PIDs; verify the name to guard against PID reuse.
            $proc = Get-Process -Id ([int]$val) -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -eq 'cmd') {
                & taskkill /PID ([int]$val) /T /F *> $null
                if ($LASTEXITCODE -eq 0) { $closed++ }
            }
        }
        Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
    }
}
if ($closed) { Write-Ok "closed $closed service window(s) by recorded PID" }
else { Write-Host '  [ - ]  no tracked service windows' -ForegroundColor DarkGray }

# FALLBACK: exact window titles only (no wildcard). Covers windows opened by an older
# start script that did not record PIDs. Exact match cannot hit the IDE/browser/explorer.
foreach ($t in @(
    'NitroWater - account', 'NitroWater - bff', 'NitroWater - server', 'NitroWater - resource',
    'NitroWater - web', 'NitroWater - Redis', 'NitroWater - SeaweedFS', 'NitroWater - Piston'
)) {
    & taskkill /FI "WINDOWTITLE eq $t" /T /F *> $null
}
Write-Ok 'closed service windows'

if ($StopMysql) {
    Write-Step 'Stopping MySQL service'
    try { Stop-Service $MysqlService -ErrorAction Stop; Write-Ok "service '$MysqlService' stopped" }
    catch { Write-Warn "could not stop service '$MysqlService': $($_.Exception.Message)" }
} else {
    Write-Host '  (MySQL service left running; use -StopMysql to stop it)' -ForegroundColor DarkGray
}

Write-Step 'Done'
