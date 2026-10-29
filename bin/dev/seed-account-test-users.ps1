# ============================================================
# seed-account-test-users.ps1 - run the password-only SSO test-user SQL.
#
# Requires: account has started once (V1 baseline applied + active AES_DATA key exists).
# The SQL is idempotent; rerunning updates the same four deterministic accounts.
#
# Usage:
#   .\bin\dev\seed-account-test-users.ps1
#   .\bin\dev\seed-account-test-users.ps1 -Database nitrowater_account
# ============================================================
[CmdletBinding()]
param(
    [string]$Database = 'nitrowater_account'
)
. (Join-Path $PSScriptRoot '..\_common.ps1')

$sqlFile = Join-Path $PSScriptRoot 'seed-account-test-users.sql'
if (-not (Test-Path -LiteralPath $sqlFile)) { throw "seed SQL not found: $sqlFile" }

$key = (Invoke-Mysql "SELECT key_id FROM encryption_data_key WHERE key_purpose='AES_DATA' AND key_status=1 ORDER BY created_at DESC, id DESC LIMIT 1" $Database |
    Where-Object { $_ -match '^dek-' } |
    Select-Object -First 1)
if (-not $key) {
    throw "No active AES_DATA key in $Database. Start nitrowater-account once, then rerun this seed."
}

Write-Step "seed password-only accounts in $Database (AES key: $key)"
$sql = Get-Content -LiteralPath $sqlFile -Raw -Encoding utf8
$env:MYSQL_PWD = $DbPass
$sql | & $MysqlExe -h $DbHost -u $DbUser --default-character-set=utf8mb4 $Database
if ($LASTEXITCODE -ne 0) { throw "seed failed with mysql exit code $LASTEXITCODE" }

Write-Host ''
Write-Host '  password : Passw0rd123' -ForegroundColor Gray
Write-Host '  testops  : SYSTEM, SUPER_ADMIN, password-only' -ForegroundColor Gray
Write-Host '  testlab  : TEST, USER, password-only' -ForegroundColor Gray
Write-Host '  testadminpw : ADMIN, ADMIN role, password-only' -ForegroundColor Gray
Write-Host '  testdisabled : COMMON, SUSPENDED' -ForegroundColor Gray
Write-Host '  testnopwd    : COMMON, NULL password (must fail password login)' -ForegroundColor Gray
