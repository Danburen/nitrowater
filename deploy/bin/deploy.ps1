<#
deploy.ps1 - single-entry deployment (aligned with waterfun deploy/bin/deploy.sh).

Steps:
  1) verify docker is available;
  2) require deploy/docker/.env (copy from .env.example);
  3) `docker compose config` on infra + app (fails fast on missing mandatory vars / syntax);
  4) bring up infra, then app.

Usage (from repo root):
  .\deploy\bin\deploy.ps1
  .\deploy\bin\deploy.ps1 -InfraOnly
  .\deploy\bin\deploy.ps1 -Build
#>
param(
  [switch]$InfraOnly,
  [switch]$Build
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent          # deploy/
$dockerDir = Join-Path $root 'docker'
$envFile = Join-Path $dockerDir '.env'
$infra = Join-Path $dockerDir 'docker-compose.infra.yml'
$app = Join-Path $dockerDir 'docker-compose.app.yml'

if (-not (Test-Path -LiteralPath $envFile)) {
  Write-Host "[ERROR] missing $envFile  (copy .env.example to .env and fill values)" -ForegroundColor Red
  exit 1
}
docker version *> $null
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] docker is not available" -ForegroundColor Red
  exit 1
}

Write-Host "=== validate compose ===" -ForegroundColor Cyan
docker compose --env-file $envFile -f $infra config --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] infra compose config invalid" -ForegroundColor Red; exit 1 }
docker compose --env-file $envFile -f $app config --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] app compose config invalid" -ForegroundColor Red; exit 1 }

Write-Host "=== up infra ===" -ForegroundColor Cyan
docker compose --env-file $envFile -f $infra up -d
if ($LASTEXITCODE -ne 0) { exit 1 }
if ($InfraOnly) { Write-Host "infra is up." -ForegroundColor Green; exit 0 }

Write-Host "=== up app ===" -ForegroundColor Cyan
if ($Build) {
  docker compose --env-file $envFile -f $app up -d --build
} else {
  docker compose --env-file $envFile -f $app up -d
}
if ($LASTEXITCODE -ne 0) { exit 1 }
Write-Host "deployed.  (logs: docker compose -f deploy/docker/docker-compose.app.yml logs -f)" -ForegroundColor Green
