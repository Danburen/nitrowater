# ============================================================
# _common.ps1 - shared helpers for bin/*.ps1 dev scripts.
#
# Dot-source it:  . "$PSScriptRoot\_common.ps1"
# Provides: $RunDir (bin\.run), Write-Step/Ok/Warn/Err, Test-Port, Wait-Port,
#           Test-Docker, Start-Window, Save-WindowPid, Ensure-DockerContainer.
#
# Window PIDs are recorded under bin\.run\*.pid so stop-all.ps1 can close the EXACT
# processes. NEVER kill by window title with a wildcard: a "WINDOWTITLE eq NitroWater*"
# filter is a PREFIX match (case-insensitive) that also hits the IDE, the browser, or a
# File Explorer window at the project root - with /T /F that kills explorer.exe and wipes
# the desktop/taskbar.
# ============================================================

$RunDir = Join-Path (Split-Path $PSCommandPath -Parent) '.run'
if (-not (Test-Path -LiteralPath $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }

function Save-WindowPid([string]$Name, $Process) {
    if ($Process) { Set-Content -LiteralPath (Join-Path $RunDir "$Name.pid") -Value $Process.Id -Encoding ascii }
}

function Write-Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Write-Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Write-Err($m)  { Write-Host "  [ERR]  $m" -ForegroundColor Red }

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}
function Wait-Port([int]$Port, [int]$TimeoutSec = 40) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) { if (Test-Port $Port) { return $true }; Start-Sleep -Milliseconds 600 }
    return $false
}
function Test-Docker {
    try { & docker info *> $null; return ($LASTEXITCODE -eq 0) } catch { return $false }
}

# Open a persistent console window (cmd /k) so the component's log stays visible.
# The command has NO embedded quotes, so it is safe across the Start-Process argv boundary.
# Returns the cmd.exe process so the caller can record its PID for stop-all.ps1.
function Start-Window([string]$Title, [string]$Command, [string]$WorkingDirectory = '') {
    $inner = "title $Title & $Command"
    if ($WorkingDirectory) {
        return Start-Process -FilePath 'cmd.exe' -ArgumentList @('/k', $inner) -WorkingDirectory $WorkingDirectory -PassThru
    }
    return Start-Process -FilePath 'cmd.exe' -ArgumentList @('/k', $inner) -PassThru
}

# Start (or create) a named docker container and open a `docker logs -f` window for it.
# -Recreate forces `docker rm -f` first so image/env changes apply (runtimes stay in the volume).
function Ensure-DockerContainer([string]$Name, [int]$Port, [string]$LogTitle, [scriptblock]$Run, [switch]$Recreate, [string]$WorkingDirectory = '') {
    Write-Step "$Name ($Port)"
    $exists = $false
    if (-not (Test-Docker)) { Write-Warn 'Docker engine not running; skip' }
    else {
        $names = @(& docker ps -a --format '{{.Names}}')
        if ($names -contains $Name) {
            if ($Recreate) { & docker rm -f $Name *> $null; & $Run; Write-Ok "recreated container $Name" }
            else { & docker start $Name | Out-Null; Write-Ok "started container $Name" }
            $exists = $true
        } else { & $Run; $exists = $true; Write-Ok "created container $Name" }
        if (Wait-Port $Port 60) { Write-Ok "up ($Port)" } else { Write-Warn "not reachable on $Port yet" }
    }
    if ($exists) { Save-WindowPid $Name (Start-Window $LogTitle "docker logs -f $Name" $WorkingDirectory) }
    return $exists
}
