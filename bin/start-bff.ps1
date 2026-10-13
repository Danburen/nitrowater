# ============================================================
# start-bff.ps1 - start nitrowater-bff (BFF / Token Handler, :8080) in its own console window.
#
# Usage:  pwsh -File bin\start-bff.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$Root = Split-Path $PSScriptRoot -Parent

Write-Step 'bff (BFF :8080)'
if (Test-Port 8080) { Write-Ok 'already up (8080)' }
else {
    Save-WindowPid 'bff' (Start-Window 'NitroWater - bff' 'gradlew.bat :nitrowater-bff:bootRun' $Root)
    Write-Ok 'launched (own window)'
}
