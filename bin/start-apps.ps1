# ============================================================
# start-apps.ps1 - One-click start of ALL business services (local dev).
#
#   account  :8090  nitrowater-account   (SSO / OIDC)          [started first, awaited]
#   bff      :8080  nitrowater-bff       (BFF / Token Handler)
#   server   :8081  nitrowater-server    (business API + judge)
#   resource :8093  nitrowater-resource  (resource service)
#   docs     :8085  nitrowater-docs      (nginx container: ALL plugin docs, 127.0.0.1:8085)
#   web      :5173  nitrowater-web       (vite dev)            [only with -WithWeb]
#
# Each gradle service runs `gradlew :<module>:bootRun` in its OWN console window (logs stay visible).
# docs is a docker container (nginx:alpine) started/created via deploy/docker/docker-compose.app.yml;
# the BFF only forwards /plugin/** to it (app.plugin-docs.upstream = http://localhost:8085 in dev).
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

Write-Step 'bff / server / resource / docs'
Start-App 'NitroWater - bff'      8080 ':nitrowater-bff:bootRun'      'bff'      | Out-Null
Start-App 'NitroWater - server'   8081 ':nitrowater-server:bootRun'   'server'   | Out-Null
Start-App 'NitroWater - resource' 8093 ':nitrowater-resource:bootRun' 'resource' | Out-Null
# docs (nginx container hosting ALL plugin docs; no host port, internal-only -> no port wait).
Write-Step 'docs (plugin docs container)'
if (-not (Test-Docker)) {
    Write-Warn 'Docker engine not running; skip docs'
} else {
    $names = @(& docker ps -a --format '{{.Names}}')
    if ($names -contains 'nitrowater-docs') {
        & docker start nitrowater-docs *> $null
        if ($LASTEXITCODE -ne 0) { Write-Warn 'docker start nitrowater-docs failed; may need recreate from compose' }
        else { Write-Ok 'started container nitrowater-docs' }
    } else {
        $composeFile = Join-Path $Root 'deploy/docker/docker-compose.app.yml'
        & docker compose -f $composeFile up -d docs *> $null
        if ($LASTEXITCODE -ne 0) { Write-Warn 'docker compose up -d docs failed (see output above)' }
        else { Write-Ok 'created container nitrowater-docs' }
    }
    Save-WindowPid 'nitrowater-docs' (Start-Window 'NitroWater - docs' 'docker logs -f nitrowater-docs' $Root) | Out-Null
}

if ($WithWeb) {
    Write-Step 'web (vite :5173)'
    if (Test-Port 5173) { Write-Ok 'web already up (5173)' }
    else { Save-WindowPid 'web' (Start-Window 'NitroWater - web' 'npm run dev' $WebDir); Write-Ok 'web launched (own window)' }
}

Write-Step 'Apps started'
Write-Host '  account 8090 | bff 8080 | server 8081 | resource 8093 | docs 8085 (container)' -ForegroundColor Gray
Write-Host '  Each service has its own console window showing live logs.' -ForegroundColor Gray





