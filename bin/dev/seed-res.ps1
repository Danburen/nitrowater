# ============================================================
# seed-res.ps1 - publish a small set of SYSTEM resource fixtures to the resource service.
#
# Why a script and not SQL: resource BYTES live in the storage backend (SeaweedFS / local dir),
# the `resource` table only holds metadata. Fixtures are therefore published through the same
# internal endpoint bin/dev/sync-res.ps1 uses (POST /internal/resource/publish, X-Internal-Token),
# which is idempotent per app+group+name and keeps download URLs stable.
#
# Scope: this endpoint only creates SYSTEM/public artefacts (it takes no owner/visibility), so it
# is good for plugin-download-like fixtures and for giving the admin console rows to look at.
# Owned / PRIVATE rows must come from the authenticated upload path (see .ai/testkit/smoke-*.ps1).
#
# Usage:
#   .\bin\dev\seed-res.ps1
#   .\bin\dev\seed-res.ps1 -BaseUrl http://localhost:8093 -Token <secret>
#
# Requires: resource service up (:8093) with app.resource.internal.token set.
# ============================================================
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8093',
    [string]$Token = $env:RESOURCE_INTERNAL_TOKEN,
    [string]$App = 'nitrowater',
    [string]$Group = 'smoke-plugin'
)
. (Join-Path $PSScriptRoot '..\_common.ps1')

if ([string]::IsNullOrWhiteSpace($Token)) { $Token = 'nitrowater-internal-dev' }

if (-not (Test-TcpPort 8093)) { Write-Err 'resource service not up (:8093) - start it first'; exit 1 }

$endpoint = "$BaseUrl/internal/resource/publish"
$headers = @{ 'X-Internal-Token' = $Token }
$tmp = Join-Path $env:TEMP 'nitrowater-seed-res'
if (-not (Test-Path -LiteralPath $tmp)) { New-Item -ItemType Directory -Path $tmp -Force | Out-Null }

# OLDEST FIRST: the public list returns createdAt DESC and createdAt is only set on INSERT, so the
# LAST published file leads the list ("latest" badge, files[0]). Hence 0.9.0 before 1.0.0.
$fixtures = @(
    [pscustomobject]@{ group = $Group;        name = "$Group-0.9.0.jar"; version = '0.9.0'; body = "nitrowater seed fixture $Group 0.9.0" }
    [pscustomobject]@{ group = $Group;        name = "$Group-1.0.0.jar"; version = '1.0.0'; body = "nitrowater seed fixture $Group 1.0.0" }
    [pscustomobject]@{ group = 'samples';     name = 'sample.txt';      version = $null;     body = "plain text fixture for the admin resource console" }
    [pscustomobject]@{ group = 'samples';     name = 'sample.json';     version = '1';       body = '{"fixture":true,"from":"bin/dev/seed-res.ps1"}' }
)

Write-Step "seed resources -> $App"
$ok = 0
foreach ($f in $fixtures) {
    $file = Join-Path $tmp $f.name
    [System.IO.File]::WriteAllText($file, $f.body, (New-Object System.Text.UTF8Encoding($false)))
    $q = "app=$([uri]::EscapeDataString($App))&group=$([uri]::EscapeDataString($f.group))&name=$([uri]::EscapeDataString($f.name))"
    if ($f.version) { $q += "&version=$([uri]::EscapeDataString($f.version))" }
    try {
        Invoke-RestMethod -Method Post -Uri "${endpoint}?$q" -Headers $headers -ContentType 'application/octet-stream' -InFile $file | Out-Null
        Write-Ok "$($f.group)/$($f.name)"
        $ok++
    } catch {
        Write-Err "$($f.group)/$($f.name): $($_.Exception.Message)"
    }
}
Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue

Write-Step "verify (public list, $App/$Group)"
try {
    $list = Invoke-RestMethod -Uri "$BaseUrl/api/public/resources?app=$([uri]::EscapeDataString($App))&group=$([uri]::EscapeDataString($Group))"
    Write-Host "  $($list.Count) file(s); first = $($list[0].name) v$($list[0].version)"
} catch {
    Write-Warn "could not read the public list: $($_.Exception.Message)"
}

if ($ok -ne $fixtures.Count) { Write-Err "$ok/$($fixtures.Count) published"; exit 1 }
Write-Ok "done: $ok fixtures"
