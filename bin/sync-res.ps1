# ============================================================
# sync-res.ps1 - publish a plugin's downloadable artifacts into the BFF's deploy-owned dir.
#
# The plugin center's "download" button is bound to a LOCAL resource (no GitHub/CDN hop): the
# jars are mirrored into deploy\plugin-docs\<plugin>\res\, which the BFF serves at
# /plugin/<plugin>/res/<file> AT RUNTIME (see nitrowater-bff WebConfig) - so republishing needs
# no web build and no BFF restart.
#
# Also writes res\manifest.json (name / version / bytes / modified, newest first); the SPA's
# plugin detail page fetches it to build the version list, so adding a jar needs no code change.
#
# Usage:
#   pwsh -File bin\sync-res.ps1                         # velochatx, default source
#   pwsh -File bin\sync-res.ps1 -Clean                  # wipe res\ first (drops stale jars)
#   pwsh -File bin\sync-res.ps1 -Plugin foo -SourceDir D:\x\out\artifacts\foo
# ============================================================
[CmdletBinding()]
param(
    [string]$Plugin = 'velochatx',
    [string[]]$SourceDir = @('D:\Project\plugin project\pro\VeloChatX\out\artifacts\VeloChatX'),
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

$Root = Split-Path $PSScriptRoot -Parent
$Out = Join-Path $Root "deploy\plugin-docs\$Plugin\res"

Write-Step "plugin resources: $Plugin"

$src = @($SourceDir | Where-Object { Test-Path -LiteralPath $_ })
if ($src.Count -eq 0) { Write-Err "no source dir found: $($SourceDir -join ', ')"; exit 1 }

# Stale jars would otherwise linger forever (unlike docs, res/ is not /MIR-mirrored).
if ($Clean -and (Test-Path -LiteralPath $Out)) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$jars = @(foreach ($dir in $src) { Get-ChildItem -LiteralPath $dir -File -Filter '*.jar' })
if ($jars.Count -eq 0) { Write-Err "no *.jar under: $($src -join ', ')"; exit 1 }

$total = ($jars | Measure-Object Length -Sum).Sum

# Newest first: the unversioned "VeloChatX.jar" is a fresh IDE artifact, so order by mtime.
$files = @()
foreach ($j in ($jars | Sort-Object LastWriteTime -Descending)) {
    Copy-Item -LiteralPath $j.FullName -Destination $Out -Force
    $ver = $null
    $m = [regex]::Match($j.Name, '-(\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z.]+)?)\.jar$')
    if ($m.Success) { $ver = $m.Groups[1].Value }
    $files += [ordered]@{
        name     = $j.Name
        version  = $ver
        bytes    = $j.Length
        modified = $j.LastWriteTime.ToUniversalTime().ToString('o')
    }
}

$manifest = [ordered]@{
    plugin      = $Plugin
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    files       = $files
}
$json = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $Out 'manifest.json'), $json, (New-Object System.Text.UTF8Encoding($false)))

$mb = [math]::Round($total / 1MB, 1)
Write-Ok "published -> deploy\plugin-docs\$Plugin\res  ($($files.Count) jars, $mb MB)"
Write-Ok "manifest: http://localhost:8080/plugin/$Plugin/res/manifest.json"
