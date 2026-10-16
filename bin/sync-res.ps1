# ============================================================
# sync-res.ps1 - publish a plugin's downloadable artifacts to the resource service.
#
# The plugin center's "download" button is bound to the unified resource service
# (nitrowater-resource, :8093) instead of a static dir: each jar is uploaded once through the
# internal publish endpoint and afterwards served from object storage (SeaweedFS) behind a stable
# public download URL. Republishing the same plugin + file name overwrites in place and keeps the
# URL stable, so the SPA never needs a code change when a new jar is built.
#
# Usage:
#   pwsh -File bin\sync-res.ps1                                   # velochatx, default source
#   pwsh -File bin\sync-res.ps1 -Plugin foo -SourceDir D:\x\out\artifacts\foo
#   pwsh -File bin\sync-res.ps1 -BaseUrl http://localhost:8093 -Token <secret>
#
# The service must be running with a non-empty app.resource.internal.token (bootRun injects a dev
# default; override with the RESOURCE_INTERNAL_TOKEN env var). This script talks to the service
# port DIRECTLY (never through the BFF, which denies /internal/**).
# ============================================================
[CmdletBinding()]
param(
    [string]$Plugin = 'velochatx',
    [string]$App = 'nitrowater',
    [string[]]$SourceDir = @('D:\Project\plugin project\pro\VeloChatX\out\artifacts\VeloChatX'),
    [string]$BaseUrl = 'http://localhost:8093',
    [string]$Token = $env:RESOURCE_INTERNAL_TOKEN
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

if ([string]::IsNullOrWhiteSpace($Token)) { $Token = 'nitrowater-internal-dev' }

Write-Step "plugin resources -> resource service: $Plugin"

$src = @($SourceDir | Where-Object { Test-Path -LiteralPath $_ })
if ($src.Count -eq 0) { Write-Err "no source dir found: $($SourceDir -join ', ')"; exit 1 }

$jars = @(foreach ($dir in $src) { Get-ChildItem -LiteralPath $dir -File -Filter '*.jar' })
if ($jars.Count -eq 0) { Write-Err "no *.jar under: $($src -join ', ')"; exit 1 }

# Publish OLDEST-FIRST (mtime ascending). The public list endpoint returns createdAt DESC
# ("newest first"), and Resource.createdAt is set only on INSERT (re-publishing keeps it), so the
# LAST published jar wins the highest createdAt and therefore leads the download list. Within a
# bulk seed that must be the newest build - e.g. the unversioned "VeloChatX.jar" (fresh IDE
# artifact) is published last so it appears first for the SPA's "latest" badge (files[0]).
$ordered = @($jars | Sort-Object LastWriteTime)
$endpoint = "$BaseUrl/internal/resource/publish"
$headers = @{ 'X-Internal-Token' = $Token }

$ok = 0
foreach ($j in $ordered) {
    $ver = $null
    $m = [regex]::Match($j.Name, '-(\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z.]+)?)\.jar$')
    if ($m.Success) { $ver = $m.Groups[1].Value }

    $q = "app=$([uri]::EscapeDataString($App))&group=$([uri]::EscapeDataString($Plugin))&name=$([uri]::EscapeDataString($j.Name))"
    if ($ver) { $q += "&version=$([uri]::EscapeDataString($ver))" }

    $mb = [math]::Round($j.Length / 1MB, 1)
    try {
        Invoke-RestMethod -Method Post -Uri "${endpoint}?$q" -Headers $headers `
            -ContentType 'application/java-archive' -InFile $j.FullName | Out-Null
        Write-Ok "published $($j.Name)  ($mb MB)"
        $ok++
    } catch {
        Write-Err "failed $($j.Name): $($_.Exception.Message)"
    }
}

if ($ok -ne $ordered.Count) { exit 1 }
Write-Ok "done: $ok jars -> $App/$Plugin"
Write-Ok "list: $BaseUrl/api/public/resources?app=$App&group=$Plugin"
