# ============================================================
# start-account.ps1 - start nitrowater-account (SSO / OIDC, :8090) in its own console window.
#
# Usage:  pwsh -File bin\start-account.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$Root = Split-Path $PSScriptRoot -Parent

Write-Step 'account (SSO :8090)'
if (Test-Port 8090) { Write-Ok 'already up (8090)' }
else {
    Save-WindowPid 'account' (Start-Window 'NitroWater - account' 'gradlew.bat :nitrowater-account:bootRun' $Root)
    Write-Ok 'launched (own window)'
}
