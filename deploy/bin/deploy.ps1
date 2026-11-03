<#
deploy.ps1 - single-entry deployment (aligned with waterfun deploy/bin/deploy.sh).

Steps:
  1) verify docker is available;
  2) require deploy/docker/.env (copy from .env.example);
  3) pre-flight: run check-env.ps1 (skip with -SkipCheck);
  4) `docker compose config` on infra + app (fails fast on missing mandatory vars / syntax);
  5) bring up infra, then app.

Content overlay: deploy/docker/docker-compose.content.yml mounts deploy/content
(moderation / docs / skills) into server at /etc/nitrowater/*. It turns on with
-Content, or automatically when deploy/content/moderation/words.txt exists (or
deploy/content/docs contains any .md).

Usage (from repo root):
  .\deploy\bin\deploy.ps1
  .\deploy\bin\deploy.ps1 -InfraOnly
  .\deploy\bin\deploy.ps1 -Build
  .\deploy\bin\deploy.ps1 -Content          # force the content overlay
  .\deploy\bin\deploy.ps1 -Edge             # add the TLS edge (nginx-proxy + acme-companion)
  .\deploy\bin\deploy.ps1 -SkipCheck        # skip the check-env gate
  .\deploy\bin\deploy.ps1 -EnvFile deploy\config\.env.prod.secrets   # use another env file

Env file: `deploy/docker/.env` by default. docker compose only auto-loads a file literally named
`.env`; anything else must be passed with --env-file, which -EnvFile does for every compose call
(and for check-env.ps1). Precedence is the usual one: shell env > env file.
#>
param(
  [switch]$InfraOnly,
  [switch]$Build,
  [switch]$Content,
  [switch]$Edge,
  [switch]$SkipCheck,
  [string]$EnvFile
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent          # deploy/
$repoRoot = Split-Path $root -Parent
$dockerDir = Join-Path $root 'docker'
# `-EnvFile` keeps prod secrets outside deploy/docker (e.g. deploy/config/.env.prod.secrets):
# docker compose does NOT auto-load any file other than `.env`, so every call below passes
# --env-file explicitly. A relative path resolves against the repo root, like the doc says.
$envFile = if ($EnvFile) {
  if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $repoRoot $EnvFile }
} else {
  Join-Path $dockerDir '.env'
}
$infra = Join-Path $dockerDir 'docker-compose.infra.yml'
$app = Join-Path $dockerDir 'docker-compose.app.yml'
$contentYml = Join-Path $dockerDir 'docker-compose.content.yml'
$edgeYml = Join-Path $dockerDir 'docker-compose.edge.yml'
$contentDir = Join-Path $root 'content'

if (-not (Test-Path -LiteralPath $envFile)) {
  Write-Host "[ERROR] missing $envFile  (copy deploy/docker/.env.example to it and fill values, or pass -EnvFile <path>)" -ForegroundColor Red
  exit 1
}
docker version *> $null
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] docker is not available" -ForegroundColor Red
  exit 1
}

if (-not $SkipCheck) {
  Write-Host "=== pre-flight (check-env) ===" -ForegroundColor Cyan
  & (Join-Path $PSScriptRoot 'check-env.ps1') -EnvFile $envFile
  if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] pre-flight failed; fix the [FAIL] items or re-run with -SkipCheck" -ForegroundColor Red
    exit 1
  }
} else {
  Write-Host "=== pre-flight skipped (-SkipCheck) ===" -ForegroundColor DarkGray
}

# Auto-detect provisioned content: a words.txt or at least one markdown file is
# the operator's signal that deploy/content is in use.
if (-not $Content) {
  $probeWord = Join-Path $contentDir 'moderation\words.txt'
  $probeDocs = Get-ChildItem -LiteralPath (Join-Path $contentDir 'docs') -Recurse -File -Filter '*.md' -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ((Test-Path -LiteralPath $probeWord) -or $probeDocs) { $Content = $true }
}

$appArgs = @('-f', $app)
if ($Content) {
  if (-not (Test-Path -LiteralPath $contentYml)) {
    Write-Host "[ERROR] missing $contentYml (content overlay requested)" -ForegroundColor Red
    exit 1
  }
  $appArgs += @('-f', $contentYml)
  Write-Host "content overlay: ON  ($contentDir -> /etc/nitrowater)" -ForegroundColor Cyan
} else {
  Write-Host "content overlay: off (content stays inside the image)" -ForegroundColor DarkGray
}

# Edge overlay: the only public entry point (80/443 + automatic TLS). Without it the stack is
# reachable only through the loopback bindings (browser or SSH tunnel).
if ($Edge) {
  if (-not (Test-Path -LiteralPath $edgeYml)) {
    Write-Host "[ERROR] missing $edgeYml (edge overlay requested)" -ForegroundColor Red
    exit 1
  }
  $appArgs += @('-f', $edgeYml)
  Write-Host "edge overlay: ON  (nginx-proxy 80/443, acme-companion auto TLS)" -ForegroundColor Cyan
} else {
  Write-Host "edge overlay: off (no public entry; ports bound to `$PUBLISH_BIND/`$OPS_BIND)" -ForegroundColor DarkGray
}

Write-Host "=== validate compose ===" -ForegroundColor Cyan
docker compose --env-file $envFile -f $infra config --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] infra compose config invalid" -ForegroundColor Red; exit 1 }
docker compose --env-file $envFile @appArgs config --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] app compose config invalid" -ForegroundColor Red; exit 1 }

Write-Host "=== up infra ===" -ForegroundColor Cyan
docker compose --env-file $envFile -f $infra up -d
if ($LASTEXITCODE -ne 0) { exit 1 }
if ($InfraOnly) { Write-Host "infra is up." -ForegroundColor Green; exit 0 }

Write-Host "=== up app ===" -ForegroundColor Cyan
if ($Build) {
  docker compose --env-file $envFile @appArgs up -d --build
} else {
  docker compose --env-file $envFile @appArgs up -d
}
if ($LASTEXITCODE -ne 0) { exit 1 }
$appFiles = ($appArgs | Where-Object { $_ -ne '-f' } | ForEach-Object { 'deploy/docker/' + (Split-Path -Leaf $_) }) -join ' -f '
Write-Host "deployed.  (logs: docker compose -f $appFiles logs -f)" -ForegroundColor Green
