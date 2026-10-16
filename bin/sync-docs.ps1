# ============================================================
# sync-docs.ps1 - build a plugin-docs site and publish it into the BFF's deploy-owned dir.
#
# Plugin docs live OUTSIDE the web build (see deploy/plugin-docs/README.md). This builds the
# separate VitePress repo with the correct `base`, then mirrors the output into
# deploy/plugin-docs/<plugin>/. The BFF serves /plugin/<plugin>/** from there AT RUNTIME, so
# updating docs needs no web build and no BFF restart.
#
# Usage:
#   pwsh -File bin\sync-docs.ps1                       # build velochatx-docs + publish
#   pwsh -File bin\sync-docs.ps1 -SkipBuild            # publish the existing dist only
#   pwsh -File bin\sync-docs.ps1 -DocsRepo D:\x -Plugin foo
# ============================================================
[CmdletBinding()]
param(
    [string]$DocsRepo = 'D:\Project\webs\velochatx-docs',
    [string]$Plugin = 'velochatx',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$Root = Split-Path $PSScriptRoot -Parent
$Dist = Join-Path $DocsRepo 'docs\.vitepress\dist'
# Deploy-owned layout: <plugin>/docs/ = the VitePress site (only). Downloadable artifacts (jars) are
# no longer stored here - they go to the resource service (see bin\sync-res.ps1). The BFF serves the
# plugin-docs dir at /plugin/<plugin>/**.
$Out = Join-Path $Root "deploy\plugin-docs\$Plugin\docs"
# The docs site is mounted one level deeper than the plugin's own namespace.
$Base = "/plugin/$Plugin/docs/"

Write-Step "plugin docs: $Plugin  (base=$Base)"

if (-not (Test-Path -LiteralPath $DocsRepo)) { Write-Err "docs repo not found: $DocsRepo"; exit 1 }

if (-not $SkipBuild) {
    # A stale committed .vitepress cache makes `docs:build` mis-report dead links - clear it.
    $cache = Join-Path $DocsRepo 'docs\.vitepress\cache'
    if (Test-Path -LiteralPath $cache) { Remove-Item -Recurse -Force $cache }
    Write-Ok 'cleared stale .vitepress/cache'

    $vp = Join-Path $DocsRepo 'node_modules\vitepress\bin\vitepress.js'
    if (-not (Test-Path -LiteralPath $vp)) { Write-Err "vitepress not installed in $DocsRepo (run npm install there)"; exit 1 }
    $env:VITEPRESS_BASE = $Base
    Push-Location $DocsRepo
    try { & node $vp build docs } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Err "vitepress build failed (exit $LASTEXITCODE)"; exit 1 }
    Write-Ok "built with base $Base"
}

if (-not (Test-Path -LiteralPath (Join-Path $Dist 'index.html'))) { Write-Err "no build output at $Dist"; exit 1 }

New-Item -ItemType Directory -Force -Path $Out | Out-Null
# /MIR so the published dir always matches the source exactly (removes stale files).
robocopy $Dist $Out /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Err "robocopy failed (exit $LASTEXITCODE)"; exit 1 }

$files = @(Get-ChildItem -Recurse -File $Out).Count
$mb = [math]::Round(((Get-ChildItem -Recurse -File $Out | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Ok "published -> deploy\plugin-docs\$Plugin\docs  ($files files, $mb MB)"
Write-Ok "URL: http://localhost:8080$Base"
