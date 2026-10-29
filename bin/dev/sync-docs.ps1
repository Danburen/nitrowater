# ============================================================
# sync-docs.ps1 - build the VeloChatX docs site and publish it into the docs mount.
#
# The VeloChatX documentation project (sources + VitePress config) lives OUTSIDE this repository
# and evolves on its own schedule. This repo only accepts the built site, and only at the single
# place the runtime reads it from:
#
#   deploy/plugin-docs/velochatx/docs  ->  /plugin/velochatx/docs/
#       - mounted read-only by the docs nginx container (:8085)
#       - forwarded by the BFF as the single edge for /plugin/**
#       - also the target of the AI citations for the `velochatx` corpus
#
# There is deliberately NO copy under nitrowater-server/src/main/resources/static: docs deploy
# dynamically (rsync/scp + optional container restart), never through a jar rebuild.
#
# The AI corpus is curated by hand at
# nitrowater-server/src/main/resources/ai/docs/velochatx and must keep the same relative paths as
# the built site so citations resolve.
#
# NitroWater documentation has no site at all: it is a markdown-only corpus under
# ai/docs/nitrowater (public-base-url is empty, citations carry no URL).
#
# Usage:
#   pwsh -File bin\dev\sync-docs.ps1              # build + publish
#   pwsh -File bin\dev\sync-docs.ps1 -SkipBuild   # publish the existing dist only
#   pwsh -File bin\dev\sync-docs.ps1 -DocsRepo D:\x
# ============================================================
[CmdletBinding()]
param(
    [string]$DocsRepo = 'D:\Project\webs\velochatx-docs',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\..\_common.ps1"

$Root = RepoRoot
$Dist = Join-Path $DocsRepo 'docs\.vitepress\dist'
$Base = '/plugin/velochatx/docs/'
$Out = Join-Path $Root 'deploy\plugin-docs\velochatx\docs'

Write-Step "velochatx docs  (base=$Base)"

if (-not $SkipBuild) {
    if (-not (Test-Path -LiteralPath $DocsRepo)) { Write-Err "docs repo not found: $DocsRepo"; exit 1 }

    # A stale committed .vitepress cache makes `docs:build` mis-report dead links - clear it.
    $cache = Join-Path $DocsRepo 'docs\.vitepress\cache'
    if (Test-Path -LiteralPath $cache) { Remove-Item -Recurse -Force $cache }

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
Write-Ok "published -> deploy\plugin-docs\velochatx\docs  ($files files, $mb MB)"
Write-Ok "URL: http://localhost:8080$Base"
Write-Step 'no jar rebuild needed: the docs container serves this directory directly'
