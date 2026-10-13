# ============================================================
# start-apps.ps1 - One-click start of ALL business services (local dev).
#
#   account  :8090  nitrowater-account   (SSO / OIDC)          [started first, awaited]
#   bff      :8080  nitrowater-bff       (BFF / Token Handler)
#   server   :8081  nitrowater-server    (business API + judge)
#   resource :8093  nitrowater-resource  (resource service)
#   web      :5173  nitrowater-web       (vite dev)            [only with -WithWeb]
#
# Each service runs `gradlew :<module>:bootRun` in its OWN console window (logs stay visible).
# account is brought up first and awaited, because bff/server pull OIDC discovery at startup.
# Idempotent: a service already listening on its port is left as-is.
#
# Usage:
#   pwsh -File bin\start-apps.ps1
#   pwsh -File bin\start-apps.ps1 -WithWeb
# ============================================================
[CmdletBinding()]
param(
    [switch]$WithWeb
)

$ErrorActionPreference = 'Stop'

# Shared helpers: Start-Window, Save-WindowPid, Test-Port, Wait-Port, Write-*.
. "$PSScriptRoot\_common.ps1"

$Root   = Split-Path $PSScriptRoot -Parent
$WebDir = Join-Path $Root 'nitrowater-web'

function Start-App([string]$Title, [int]$Port, [string]$GradleTask, [string]$PidName) {
    if (Test-Port $Port) { Write-Ok "$Title already up ($Port)"; return $true }
    Save-WindowPid $PidName (Start-Window $Title "gradlew.bat $GradleTask" $Root)
    Write-Host "  launched $Title -> $GradleTask (own window)" -ForegroundColor Gray
    return $false
}

# account first, await :8090 so bff/server can fetch OIDC discovery.
Write-Step 'account (SSO :8090)'
$acctUp = Start-App 'NitroWater - account' 8090 ':nitrowater-account:bootRun' 'account'
if (-not $acctUp) {
    if (Wait-Port 8090 180) { Write-Ok 'account up (8090)' }
    else { Write-Warn 'account not up after 180s (check its window)' }
}

Write-Step 'bff / server / resource'
Start-App 'NitroWater - bff'      8080 ':nitrowater-bff:bootRun'      'bff'      | Out-Null
Start-App 'NitroWater - server'   8081 ':nitrowater-server:bootRun'   'server'   | Out-Null
Start-App 'NitroWater - resource' 8093 ':nitrowater-resource:bootRun' 'resource' | Out-Null

if ($WithWeb) {
    Write-Step 'web (vite :5173)'
    if (Test-Port 5173) { Write-Ok 'web already up (5173)' }
    else { Save-WindowPid 'web' (Start-Window 'NitroWater - web' 'npm run dev' $WebDir); Write-Ok 'web launched (own window)' }
}

Write-Step 'Apps started'
Write-Host '  account 8090 | bff 8080 | server 8081 | resource 8093' -ForegroundColor Gray
Write-Host '  Each service has its own console window showing live logs.' -ForegroundColor Gray
