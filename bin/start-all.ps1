# ============================================================
# start-all.ps1 - one-click start EVERYTHING (infra first, then business services).
#
#   = start-infra.ps1 + start-apps.ps1 (each component gets its own console window).
#   Pass -WithWeb to also start the web dev server (vite :5173).
#
# Usage:  pwsh -File bin\start-all.ps1 [-WithWeb]
#         (or double-click bin\start-all.bat)
# ============================================================
[CmdletBinding()]
param(
    [switch]$WithWeb
)

$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'start-infra.ps1')
& (Join-Path $PSScriptRoot 'start-apps.ps1') @PSBoundParameters
