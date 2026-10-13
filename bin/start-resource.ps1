# ============================================================
# start-resource.ps1 - start nitrowater-resource (unified resource service, :8093).
#
# Usage:  pwsh -File bin\start-resource.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$Root = Split-Path $PSScriptRoot -Parent

Write-Step 'resource (8093)'
if (Test-Port 8093) { Write-Ok 'already up (8093)' }
else {
    Save-WindowPid 'resource' (Start-Window 'NitroWater - resource' 'gradlew.bat :nitrowater-resource:bootRun' $Root)
    Write-Ok 'launched (own window)'
}
