# ============================================================
# dev-seed.ps1 - one command to rebuild dev data: reset -> start -> users -> problems -> resources.
#
# The migrations are the source of truth for SCHEMA + real content; every piece of TEST data
# (users, demo problems, resource fixtures) is seeded by script, never baked into a migration, so
# changing test data never invalidates a Flyway checksum again.
#
# Usage:
#   .\dev-seed.ps1                       # drop biz, start services, seed everything
#   .\dev-seed.ps1 -Db biz,account       # also re-register the test users
#   .\dev-seed.ps1 -SkipReset            # seed only (services already up)
#   .\dev-seed.ps1 -SkipUsers -SkipProblems
#
# Steps are idempotent: re-running never duplicates users/problems (the seed scripts upsert).
# ============================================================
# NOTE: no [CmdletBinding()] on purpose - the "-Db" parameter would otherwise be ambiguous with
#       the automatic common parameter "-Debug" and fail to bind (same reason as reset-db.ps1).
param(
    [string[]]$Db = @('biz'),
    [switch]$SkipReset,
    [switch]$SkipStart,
    [switch]$SkipUsers,
    [switch]$SkipProblems,
    [switch]$SkipResources
)
. (Join-Path $PSScriptRoot '_common.ps1')

# `pwsh -File ... -Db a,b` arrives as ONE token; split it.
$targets = @($Db | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$doReset = (-not $SkipReset) -and ($targets.Count -gt 0) -and ($targets -notcontains 'none')

Write-Step 'dev-seed'
Write-Host ("  reset=[{0}] start={1} users={2} problems={3} resources={4}" -f `
        ($(if ($doReset) { $targets -join ',' } else { 'skip' })),
        (-not $SkipStart), (-not $SkipUsers), (-not $SkipProblems), (-not $SkipResources))

# ---------------- 1. reset (drop tables so Flyway replays from V1) ----------------
if ($doReset) {
    Write-Step "reset DBs: $($targets -join ', ')"
    & (Join-Path $PSScriptRoot 'reset-db.ps1') -Db $targets -Force
}

# ---------------- 2. start owning services (Flyway replays on boot) ----------------
if (-not $SkipStart) {
    Write-Step 'start services'
    # account first: bff/server fetch OIDC discovery during startup.
    Start-ModuleJar 'nitrowater-account' 8090 | Out-Null
    Start-ModuleJar 'nitrowater-server' 8081 | Out-Null
    if (-not $SkipResources) { Start-ModuleJar 'nitrowater-resource' 8093 | Out-Null }
}

# ---------------- 3. test users (testadmin / testuser1 / testuser) ----------------
if (-not $SkipUsers) {
    Write-Step 'seed test users'
    & (Join-Path $PSScriptRoot 'seed-test-users.ps1')
}

# ---------------- 4. demo problems (ACM + LeetCode function mode) ----------------
if (-not $SkipProblems) {
    Write-Step 'seed demo problems'
    & (Join-Path $PSScriptRoot 'seed-demo-problem.ps1')
    & (Join-Path $PSScriptRoot 'seed-func-problem.ps1')
}

# ---------------- 5. resource fixtures (SYSTEM/public artefacts) ----------------
if (-not $SkipResources) {
    Write-Step 'seed resource fixtures'
    & (Join-Path $PSScriptRoot 'seed-res.ps1')
}

Write-Step 'dev-seed done'
Write-Host '  test users : testadmin / testuser1 / testuser   (password Passw0rd123)'
Write-Host '  problems   : two-sum (ACM), two-sum-func (LeetCode)'
Write-Host '  plugin     : velochatx (seeded by the V1 baseline migration)'
Write-Host '  resources  : app=nitrowater group=smoke-plugin / samples'
