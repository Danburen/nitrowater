# ============================================================
# stop-all.ps1 - Stop everything started by start-all / start-infra.
#
#   - backend listeners  : 8090 account, 8080 bff, 8081 server, 8093 resource, 6379 redis
#   - web listener       : 5173 Vite dev server - ONLY with -WithWeb (you start the frontend
#                          yourself, so by default it is left alone)
#   - docker containers  : nitrowater-piston, nitrowater-seaweedfs, nitrowater-docs
#   - tracked PIDs       : the exact PIDs recorded by the start scripts in bin\.run\*.pid
#   - MySQL              : left running by default (system service); -StopMysql to stop it
#
# Usage:
#   pwsh -File bin\stop-all.ps1
#   pwsh -File bin\stop-all.ps1 -WithWeb     # also kill :5173
#   pwsh -File bin\stop-all.ps1 -StopMysql
#   (or double-click bin\stop-all.bat)
# ============================================================
[CmdletBinding()]
param(
    [switch]$StopMysql,
    [switch]$WithWeb
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

# NOTE: do NOT use [ordered]@{} here - OrderedDictionary does exact-type key matching, so
# $targets[8090] (int) silently misses the "8090" string key and every label prints empty.
$targets = @(
    [pscustomobject]@{ Port = 8090; Name = 'account'  },
    [pscustomobject]@{ Port = 8080; Name = 'bff'      },
    [pscustomobject]@{ Port = 8081; Name = 'server'   },
    [pscustomobject]@{ Port = 8093; Name = 'resource' },
    [pscustomobject]@{ Port = 6379; Name = 'redis'    }
)
if ($WithWeb) { $targets += [pscustomobject]@{ Port = 5173; Name = 'web' } }

Write-Step 'Stopping listeners (apps / redis)'
foreach ($t in $targets) {
    if (Test-Port $t.Port) {
        Stop-ByPort $t.Port
        Start-Sleep -Milliseconds 300
        if (Test-Port $t.Port) { Write-Warn "$($t.Name) ($($t.Port)) still listening" }
        else { Write-Ok "stopped $($t.Name) ($($t.Port))" }
    } else { Write-Host "  [ - ]  $($t.Name) ($($t.Port)) not listening" -ForegroundColor DarkGray }
}
if (-not $WithWeb -and (Test-Port 5173)) {
    Write-Host '  (web :5173 left running; use -WithWeb to stop it)' -ForegroundColor DarkGray
}

Write-Step 'Stopping docker containers'
try {
    & docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        foreach ($c in @('nitrowater-piston', 'nitrowater-seaweedfs', 'nitrowater-docs')) {
            $running = @(& docker ps --format '{{.Names}}')
            if ($running -contains $c) { & docker stop $c | Out-Null; Write-Ok "stopped container $c" }
            else {
                $all = @(& docker ps -a --format '{{.Names}}')
                if ($all -contains $c) { Write-Host "  [ - ]  $c already stopped" -ForegroundColor DarkGray }
                else { Write-Host "  [ - ]  $c not present" -ForegroundColor DarkGray }
            }
        }
    } else { Write-Warn 'Docker engine not running; skip' }
} catch { Write-Warn 'Docker not available; skip' }

Write-Step 'Closing tracked processes'
# PRIMARY: kill the exact PIDs recorded by the start scripts (bin\.run\*.pid).
# Two kinds are recorded: cmd.exe console wrappers (visible backend/infra windows) and
# java.exe (hidden backend jars). Verify the process name to guard against PID reuse.
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
            $proc = Get-Process -Id ([int]$val) -ErrorAction SilentlyContinue
            if ($proc -and ($proc.ProcessName -in @('cmd', 'java'))) {
                & taskkill /PID ([int]$val) /T /F *> $null
                if ($LASTEXITCODE -eq 0) { $closed++ }
            }
        }
        Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
    }
}
if ($closed) { Write-Ok "closed $closed tracked process(es) by recorded PID" }
else { Write-Host '  [ - ]  no tracked processes' -ForegroundColor DarkGray }

# FALLBACK: exact window titles only (no wildcard), for infra log windows whose PID file was
# lost. Match on MainWindowTitle (exact, case-insensitive equality) instead of `taskkill /FI
# WINDOWTITLE eq`, which reports success even when nothing matched - we need real counts.
$titlesClosed = 0
foreach ($t in @('NitroWater - docs', 'NitroWater - Redis', 'NitroWater - SeaweedFS', 'NitroWater - Piston')) {
    $procs = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq $t })
    foreach ($p in $procs) {
        & taskkill /PID $p.Id /T /F *> $null
        if ($LASTEXITCODE -eq 0) { $titlesClosed++ }
    }
}
if ($titlesClosed) { Write-Ok "closed $titlesClosed orphan log window(s) by title" }

if ($StopMysql) {
    Write-Step 'Stopping MySQL service'
    try { Stop-Service $MysqlService -ErrorAction Stop; Write-Ok "service '$MysqlService' stopped" }
    catch { Write-Warn "could not stop service '$MysqlService': $($_.Exception.Message)" }
} else {
    Write-Host '  (MySQL service left running; use -StopMysql to stop it)' -ForegroundColor DarkGray
}

Write-Step 'Done'

