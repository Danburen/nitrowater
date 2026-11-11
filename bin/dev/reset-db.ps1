# ============================================================
# reset-db.ps1 - Drop ALL tables of a database so Flyway replays from V1 on next start.
#
# DESTRUCTIVE. Default target = all (account, biz, resource).
#
# Requires the migration checksums to match the working tree: after ANY edit to a
# V1__*_baseline.sql (dev-stage baseline fold, per AGENTS.md), the affected DB MUST be dropped +
# replayed here. Editing a baseline without resetting fails startup on a checksum mismatch, and
# lyway repair is NOT a substitute - it only rewrites the recorded checksum; the DDL added to
# the baseline never runs, leaving silent schema drift (missing tables).
#
# Usage:
#   .\bin\dev\reset-db.ps1 -DryRun                 # show what would be dropped
#   .\bin\dev\reset-db.ps1 -Db biz -Force          # drop the business DB, no prompt
#   .\bin\dev\reset-db.ps1 -Db account,biz         # subset (prompts)
#   .\bin\dev\reset-db.ps1 -Restart                # drop then restart owning services (Flyway replays)
#
# Owning services: account -> account(:8090) | biz -> server(:8081) | resource -> resource(:8093)
# After a biz reset run: .\bin\dev\seed-test-users.ps1 (or just: .\bin\dev\dev-seed.ps1)
#
# NOTE: no [CmdletBinding()] here on purpose - the "-Db" parameter would otherwise be
#       ambiguous with the automatic common parameter "-Debug" and fail to bind.
# ============================================================
param(
    [string[]]$Db,
    [switch]$DryRun,
    [switch]$Force,
    [switch]$Restart
)
. (Join-Path $PSScriptRoot '..\_common.ps1')

# When invoked via `pwsh -File ... -Db a,b` the comma list arrives as ONE token; split it.
if ($Db) { $Db = @($Db | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

$defs = @{
    account  = [pscustomobject]@{ database='nitrowater_account';  module='nitrowater-account';  port=8090; name='account' }
    biz      = [pscustomobject]@{ database='nitrowater_biz';      module='nitrowater-server';   port=8081; name='server' }
    resource = [pscustomobject]@{ database='nitrowater_resource'; module='nitrowater-resource'; port=8093; name='resource' }
}

$targets = if (-not $Db -or ($Db -contains 'all')) { @('account', 'biz', 'resource') } else { @($Db | Where-Object { $defs.ContainsKey($_) }) }
if (-not $targets) { Write-Err 'no valid -Db (use account|biz|resource|all)'; exit 1 }

Write-Step "Targets: $($targets -join ', ')"
foreach ($t in $targets) {
    $d = $defs[$t]
    Write-Host ("  {0,-8} db={1,-22} owner={2} (:{3})" -f $t, $d.database, $d.module, $d.port)
}

if ($DryRun) { Write-Warn 'DryRun - nothing dropped.'; exit 0 }

if (-not $Force) {
    $ans = Read-Host "This DROPS ALL TABLES in: $($targets -join ', '). Type YES to continue"
    if ($ans -ne 'YES') { Write-Warn 'Aborted.'; exit 1 }
}

# Drops every base table of the current schema, generated from information_schema.
$dropSql = @'
SET FOREIGN_KEY_CHECKS=0;
SET @tables = NULL;
SELECT GROUP_CONCAT(CONCAT('`', table_name, '`') SEPARATOR ',') INTO @tables
  FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SET @tables = IFNULL(CONCAT('DROP TABLE IF EXISTS ', @tables), 'SELECT 1');
PREPARE stmt FROM @tables;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET FOREIGN_KEY_CHECKS=1;
'@

foreach ($t in $targets) {
    $d = $defs[$t]
    Write-Step "Reset $($d.database)"

    if (Test-TcpPort $d.port) {
        Write-Host "  stopping $($d.module)"
        # Tracked pid first (it also closes the wrapper console), port as the fallback for jars
        # started by hand. Dropping tables under a LIVE process leaves it serving 500s until the
        # next restart, so this refuses to continue while the port is still bound.
        Stop-ModuleProcess $d.module $d.port | Out-Null
        $deadline = (Get-Date).AddSeconds(20)
        while ((Test-TcpPort $d.port) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 300 }
        if (Test-TcpPort $d.port) {
            Write-Err "$($d.module) is still listening on $($d.port) - refusing to drop $($d.database)"
            Write-Err 'stop it manually (pwsh -File bin\stop-all.ps1) and re-run this script'
            exit 1
        }
        Write-Ok "stopped $($d.module) (port $($d.port) free)"
    }

    Invoke-Mysql $dropSql $d.database | Out-Null
    Write-Ok "dropped all tables in $($d.database)"
}

if ($Restart) {
    Write-Step 'Restarting owning services (Flyway will replay from V1)'
    foreach ($t in $targets) {
        $d = $defs[$t]
        Start-ModuleJar $d.module $d.port | Out-Null
    }
} else {
    Write-Warn 'Services stopped. Start them again (bin\start-all.ps1, or re-run with -Restart) to let Flyway rebuild.'
    Write-Warn 'Then re-seed: bin\dev\seed-test-users.ps1, or run bin\dev\dev-seed.ps1.'
}
