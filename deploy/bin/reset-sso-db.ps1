# ============================================================
# reset-sso-db.ps1 - 清空 SSO 表，让 Flyway 下次启动按 V1 重建
# 用途：初期开发直接改 V1__sso_baseline.sql 后，清库重建
#
# 用法:
#   .\deploy\bin\reset-sso-db.ps1
#   .\deploy\bin\reset-sso-db.ps1 -DbPass "yourpass"
#   .\deploy\bin\reset-sso-db.ps1 -Force        # 跳过 YES 确认（危险！防止误跑）
# ============================================================
param(
    [string]$DbHost   = '127.0.0.1',
    [string]$DbName   = 'nitrowater_account',
    [string]$DbUser   = 'root',
    [string]$DbPass   = '123456',
    [string]$MysqlExe = 'E:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [switch]$Force
)

if (-not (Test-Path -LiteralPath $MysqlExe)) {
    Write-Host "[ERROR] mysql.exe not found: $MysqlExe"
    Write-Host "        pass -MysqlExe <path>"
    exit 1
}

if (-not $Force) {
    Write-Host "=== target ===" -ForegroundColor Cyan
    Write-Host ("  mysql : {0}" -f $MysqlExe)
    Write-Host ("  db    : {0}  @ {1}  user {2}" -f $DbName, $DbHost, $DbUser)
    $ans = Read-Host "This DROPS ALL tables in '$DbName' (data loss!). Type YES to continue"
    if ($ans -ne 'YES') { Write-Host '[ABORT] cancelled. (use -Force to skip this prompt)' -ForegroundColor Yellow; exit 1 }
    Write-Host ("  confirmed, dropping tables in '{0}' ..." -f $DbName)
}

$env:MYSQL_PWD = $DbPass
$sql = @'
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS oauth2_authorization_consent, oauth2_authorization, oauth2_registered_client, user_role, `role`, sso_identity, user_data, user_data_archive, account_audit_log, encryption_data_key, user, flyway_schema_history;
SET FOREIGN_KEY_CHECKS=1;
'@

& $MysqlExe -h $DbHost -u $DbUser $DbName -e $sql
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] drop failed"; exit 1 }

Write-Host "[OK] SSO tables dropped from '$DbName'."
Write-Host "     Restart account (IDEA / bin\start-all.ps1) -> Flyway rebuilds from V1."
