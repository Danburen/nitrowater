# ============================================================
# start-all.ps1 - ONE command for local preview: build the backend, start infra, run the services.
#
#   1. build    gradlew :<module>:bootJar for account / bff / server / resource   (-SkipBuild)
#   2. infra    bin\start-infra.ps1: MySQL, Redis, SeaweedFS, Piston, docs(:8085) (-SkipInfra)
#   3. services java -jar <module>\build\libs\app.jar  (:8090 -> :8080 / :8081 / :8093)
#
# Services run in their own persistent console windows by default (like infra), so the
# Spring log stays visible and closing a window stops that service.
# Add -Hidden to run them as background processes; logs land in bin\.run\logs\<module>.{out,err}.log.
# account starts first and is awaited, because bff/server fetch OIDC discovery during startup.
#
# THE FRONTEND IS NOT STARTED HERE - run it yourself:
#   dev  : cd nitrowater-web; npm run dev          -> http://localhost:5173 (proxies /api to :8080)
#   build: pwsh -File bin\start-all.ps1 -BuildWeb  -> npm run build; the BFF serves dist on :8080
#
# Usage:
#   pwsh -File bin\start-all.ps1                # build + infra + services (one console window each)
#   pwsh -File bin\start-all.ps1 -SkipBuild     # restart services from the existing jars
#   pwsh -File bin\start-all.ps1 -SkipInfra     # infra already running
#   pwsh -File bin\start-all.ps1 -BuildWeb      # also rebuild the SPA dist
#   pwsh -File bin\start-all.ps1 -NoRestart     # leave services that are already up untouched
#   pwsh -File bin\start-all.ps1 -Hidden        # run jars hidden; logs go to bin\.run\logs
#   (-Windowed is still accepted; visible console windows are now the default.)
#   (or double-click bin\start-all.bat)
#
# Stop everything with: pwsh -File bin\stop-all.ps1
# ============================================================
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$BuildWeb,
    [switch]$SkipInfra,
    [switch]$NoRestart,
    [switch]$Hidden,
    [switch]$Windowed   # legacy alias: visible console windows are the default
)

$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\_common.ps1"

$WebDir = Join-Path $RepoRoot 'nitrowater-web'

$useWindowed = -not $Hidden
if ($Windowed -and $Hidden) {
    Write-Err '-Windowed and -Hidden are mutually exclusive'
    exit 1
}

# Ordered: account first (bff/server pull OIDC discovery from it), then the rest in parallel.
$Services = @(
    [pscustomobject]@{ Name = 'account';  Module = 'nitrowater-account';  Port = 8090; Note = 'SSO / OIDC';   First = $true  },
    [pscustomobject]@{ Name = 'bff';      Module = 'nitrowater-bff';      Port = 8080; Note = 'portal / SPA'; First = $false },
    [pscustomobject]@{ Name = 'server';   Module = 'nitrowater-server';   Port = 8081; Note = 'business API'; First = $false },
    [pscustomobject]@{ Name = 'resource'; Module = 'nitrowater-resource'; Port = 8093; Note = 'resource API'; First = $false }
)

# ---------------- 1. optional web build (SPA served by the BFF on :8080) ----------------
if ($BuildWeb) {
    Write-Step 'web build (nitrowater-web -> dist)'
    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { Write-Err 'npm.cmd not found in PATH'; exit 1 }
    Push-Location $WebDir
    try { & $npm.Source run build } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Err "web build failed (exit $LASTEXITCODE)"; exit 1 }
    Write-Ok 'dist rebuilt (the BFF serves it on :8080)'
}

# ---------------- 2. backend build (bootJar -> <module>\build\libs\app.jar) ----------------
if ($SkipBuild) {
    Write-Step 'backend build skipped (-SkipBuild)'
    $missing = @($Services | Where-Object { -not (Test-Path -LiteralPath (Get-Jar $_.Module)) })
    if ($missing.Count -gt 0) {
        Write-Err ('jar missing: ' + (($missing | ForEach-Object { Get-Jar $_.Module }) -join ', '))
        Write-Warn 'drop -SkipBuild, or build first: gradlew :<module>:bootJar'
        exit 1
    }
}
else {
    Write-Step 'backend build (bootJar)'
    $tasks = @($Services | ForEach-Object { ":$($_.Module):bootJar" })
    Push-Location $RepoRoot
    try { & $Gradlew @tasks --console=plain } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Err "gradle bootJar failed (exit $LASTEXITCODE)"; exit 1 }
    Write-Ok 'jars rebuilt (build\libs\app.jar per module)'
}

# ---------------- 3. infra (MySQL / Redis / SeaweedFS / Piston / docs) ----------------
if ($SkipInfra) { Write-Step 'infra skipped (-SkipInfra)' }
else { & (Join-Path $PSScriptRoot 'start-infra.ps1') }

# ---------------- 4. services (jars must never be the PREVIOUS build) ----------------
Write-Step 'services'
$skip = @{}
foreach ($s in $Services) {
    if (-not (Test-Port $s.Port)) { continue }
    if ($NoRestart) {
        Write-Warn "$($s.Name) already up ($($s.Port)) - left as-is (-NoRestart: it may run an OLD jar)"
        $skip[$s.Name] = $true
        continue
    }
    Write-Host "  replacing stale $($s.Name) on $($s.Port)" -ForegroundColor Gray
    Stop-ModuleProcess $s.Module $s.Port | Out-Null
    Start-Sleep -Milliseconds 500
}

$failed = @()

# account first, awaited: bff/server resolve OIDC discovery from it at startup.
$acct = @($Services | Where-Object { $_.First })[0]
if (-not $skip.ContainsKey($acct.Name)) {
    if (-not (Start-ModuleJar $acct.Module $acct.Port -Windowed:$useWindowed)) { $failed += $acct.Name }
}
if ($failed.Count -gt 0) {
    Write-Err 'account did not come up; bff/server would fail OIDC discovery - aborting'
    $accountHint = if ($useWindowed) { 'check its console window' } else { 'see bin\.run\logs\nitrowater-account.out.log' }
    Write-Host "  $accountHint" -ForegroundColor Gray
    exit 1
}

# the rest in parallel: spawn all, then wait for each port.
$rest = @($Services | Where-Object { -not $_.First -and -not $skip.ContainsKey($_.Name) })
foreach ($s in $rest) { Start-ModuleJar $s.Module $s.Port -NoWait -Windowed:$useWindowed | Out-Null }
foreach ($s in $rest) {
    if (Wait-Port $s.Port 180) { Write-Ok "$($s.Name) up ($($s.Port))" }
    else {
        $serviceHint = if ($useWindowed) { 'see its console window' } else { "see logs\$($s.Module).out.log" }
        Write-Err "$($s.Name) not up after 180s ($serviceHint)"
        $failed += $s.Name
    }
}

# ---------------- 5. summary ----------------
Write-Step 'ready'
foreach ($s in $Services) {
    $state = if (Test-Port $s.Port) { 'UP  ' } else { 'DOWN' }
    $pidFile = Get-ModulePidFile $s.Module
    $procId = if (Test-Path -LiteralPath $pidFile) { (Get-Content -LiteralPath $pidFile -Raw).Trim() } else { '-' }
    Write-Host ("  [{0}] {1,-9} :{2}  pid={3,-7} {4}" -f $state, $s.Name, $s.Port, $procId, $s.Note)
}
Write-Host '  [UP  ] docs      :8085  plugin docs (proxied by the BFF at /plugin/**)' -ForegroundColor Gray
if ($useWindowed) {
    Write-Host '  each backend has its own persistent console window (Spring log); close it to stop that service' -ForegroundColor Gray
} else {
    Write-Host '  java runs hidden - check: Get-Process java | Format-Table Id,ProcessName,StartTime' -ForegroundColor Gray
    Write-Host '  logs : bin\.run\logs\<module>.{out,err}.log' -ForegroundColor Gray
    Write-Host '  tail : Get-Content bin\.run\logs\nitrowater-server.out.log -Wait -Tail 50' -ForegroundColor Gray
}
Write-Host '  stop : pwsh -File bin\stop-all.ps1 (also closes the backend windows)' -ForegroundColor Gray
if (-not (Test-Port 5173)) {
    Write-Host '  web  : cd nitrowater-web; npm run dev   -> http://localhost:5173' -ForegroundColor Gray
}

if ($failed.Count -gt 0) {
    Write-Err ('failed: ' + ($failed -join ', '))
    exit 1
}
