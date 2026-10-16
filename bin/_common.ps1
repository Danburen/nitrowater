# ============================================================
# _common.ps1 - shared helpers for bin/*.ps1 dev scripts.
#
# Dot-source it:  . "$PSScriptRoot\_common.ps1"
# Provides: $RunDir (bin\.run), Write-Step/Ok/Warn/Err, Test-Port, Wait-Port,
#           Test-Docker, Start-Window, Save-WindowPid, Ensure-DockerContainer.
#
# Window PIDs are recorded under bin\.run\*.pid so stop-all.ps1 can close the EXACT
# processes. NEVER kill by window title with a wildcard: a "WINDOWTITLE eq NitroWater*"
# filter is a PREFIX match (case-insensitive) that also hits the IDE, the browser, or a
# File Explorer window at the project root - with /T /F that kills explorer.exe and wipes
# the desktop/taskbar.
# ============================================================

$RunDir = Join-Path (Split-Path $PSCommandPath -Parent) '.run'
if (-not (Test-Path -LiteralPath $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }

function Save-WindowPid([string]$Name, $Process) {
    if ($Process) { Set-Content -LiteralPath (Join-Path $RunDir "$Name.pid") -Value $Process.Id -Encoding ascii }
}

function Write-Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Write-Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Write-Err($m)  { Write-Host "  [ERR]  $m" -ForegroundColor Red }

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}
function Wait-Port([int]$Port, [int]$TimeoutSec = 40) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) { if (Test-Port $Port) { return $true }; Start-Sleep -Milliseconds 600 }
    return $false
}
function Test-Docker {
    try { & docker info *> $null; return ($LASTEXITCODE -eq 0) } catch { return $false }
}

# Open a persistent console window (cmd /k) so the component's log stays visible.
# The command has NO embedded quotes, so it is safe across the Start-Process argv boundary.
# Returns the cmd.exe process so the caller can record its PID for stop-all.ps1.
function Start-Window([string]$Title, [string]$Command, [string]$WorkingDirectory = '') {
    $inner = "title $Title & $Command"
    if ($WorkingDirectory) {
        return Start-Process -FilePath 'cmd.exe' -ArgumentList @('/k', $inner) -WorkingDirectory $WorkingDirectory -PassThru
    }
    return Start-Process -FilePath 'cmd.exe' -ArgumentList @('/k', $inner) -PassThru
}

# Start (or create) a named docker container and open a `docker logs -f` window for it.
# -Recreate forces `docker rm -f` first so image/env changes apply (runtimes stay in the volume).
function Ensure-DockerContainer([string]$Name, [int]$Port, [string]$LogTitle, [scriptblock]$Run, [switch]$Recreate, [string]$WorkingDirectory = '') {
    Write-Step "$Name ($Port)"
    $exists = $false
    if (-not (Test-Docker)) { Write-Warn 'Docker engine not running; skip' }
    else {
        $names = @(& docker ps -a --format '{{.Names}}')
        if ($names -contains $Name) {
            if ($Recreate) { & docker rm -f $Name *> $null; & $Run; Write-Ok "recreated container $Name" }
            else { & docker start $Name | Out-Null; Write-Ok "started container $Name" }
            $exists = $true
        } else { & $Run; $exists = $true; Write-Ok "created container $Name" }
        if (Wait-Port $Port 60) { Write-Ok "up ($Port)" } else { Write-Warn "not reachable on $Port yet" }
    }
    if ($exists) { Save-WindowPid $Name (Start-Window $LogTitle "docker logs -f $Name" $WorkingDirectory) }
    return $exists
}

# ============================================================
# --- dev data tooling (used by reset-db.ps1 / seed-*.ps1) ---
#
# These helpers lived in the git-ignored .ai/testkit tree. The dev data scripts were promoted
# into bin/ (tracked) so a fresh clone can rebuild + reseed without that local-only tree, so the
# helpers they depend on moved here with them.
# ============================================================

$RepoRoot   = Split-Path $PSScriptRoot -Parent          # bin -> repo root
$LogDir     = Join-Path $RunDir 'logs'
if (-not (Test-Path -LiteralPath $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

# ---- toolchain paths (override with env vars when needed) ----
$JavaExe     = if ($env:NITROWATER_JAVA)  { $env:NITROWATER_JAVA }  else { 'D:\Program Files\Java\openkdk-hotspot-25.0.1.1\bin\java.exe' }
$MysqlExe    = if ($env:NITROWATER_MYSQL) { $env:NITROWATER_MYSQL } else { 'E:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
$RedisDir    = 'E:\Program Files\Redis'
$RedisCli    = Join-Path $RedisDir 'redis-cli.exe'
$Gradlew     = Join-Path $RepoRoot 'gradlew.bat'

# ---- dev credentials / keys (dev only; see AGENTS.md) ----
$DbHost        = '127.0.0.1'
$DbUser        = 'root'
$DbPass        = '123456'
$NitrowaterKek = 'kElE+g/NUUkCAG0azg9ZcYYXbevGg9mYuUtJvICut98='
$JwtKeyFile    = Join-Path $RepoRoot 'deploy\keys\private.key'
$JwtKid        = 'nitrowater-key-1'
$Issuer        = 'http://localhost:8090'
$DeviceFp      = 'smoketestdevice0001'

# ---- network helpers (testkit-style names, kept so the promoted scripts stay unchanged) ----
function Test-TcpPort([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}
function Wait-TcpPort([int]$Port, [int]$TimeoutSec = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) { if (Test-TcpPort $Port) { return $true }; Start-Sleep -Milliseconds 700 }
    return $false
}
function Get-HttpStatus([string]$Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 6 -MaximumRedirection 5
        return [int]$r.StatusCode
    } catch {
        try { return [int]$_.Exception.Response.StatusCode } catch { return $null }
    }
}
function Stop-ByPort([int]$Port) {
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        try { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue } catch { }
    }
}

# ---- app jar bookkeeping ----
function Get-Jar([string]$Module) { return (Join-Path $RepoRoot "$Module\build\libs\app.jar") }
function Get-ModulePidFile([string]$Module) { return (Join-Path $RunDir "$Module.pid") }

# Launch a boot jar the way start-test.ps1 did: hidden window, stdout/stderr split into
# .run\logs\<module>.{out,err}.log, PID recorded for stop-all.ps1. WorkingDirectory MUST be the
# repo root (account resolves jwt.private-key=file:./deploy/keys/... relative to it).
function Start-ModuleJar([string]$Module, [int]$Port) {
    if (Test-TcpPort $Port) { Write-Ok "$Module already up ($Port)"; return $true }
    $jar = Get-Jar $Module
    if (-not (Test-Path -LiteralPath $jar)) { throw "jar missing: $jar (run: gradlew :${Module}:bootJar)" }
    if ($Module -eq 'nitrowater-account') { $env:NITROWATER_KEK = $NitrowaterKek }
    $out = Join-Path $LogDir "$Module.out.log"
    $err = Join-Path $LogDir "$Module.err.log"
    $p = Start-Process -FilePath $JavaExe -ArgumentList @('-jar', $jar) -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
    Set-Content -LiteralPath (Get-ModulePidFile $Module) -Value $p.Id -Encoding ascii
    Write-Host "  launched $Module pid=$($p.Id) (logs\$Module.out.log)"
    if (Wait-TcpPort $Port 120) { Write-Ok "$Module up ($Port)"; return $true }
    Write-Warn "$Module not up after 120s (see logs\$Module.out.log)"
    return $false
}

# ---- Redis helper (returns $null for a missing key) ----
function Get-RedisValue([string]$Key) {
    $v = & $RedisCli -h 127.0.0.1 -p 6379 GET $Key 2>$null
    if ($null -eq $v -or $v -eq '(nil)') { return $null }
    return $v
}

# ---- MySQL helper (raw tab-separated, no column headers) ----
function Invoke-Mysql([string]$Sql, [string]$Database = '') {
    $env:MYSQL_PWD = $DbPass
    $margs = @('-h', $DbHost, '-u', $DbUser, '-N', '-B')
    if ($Database) { $margs += $Database }
    $margs += @('-e', $Sql)
    return (& $MysqlExe @margs 2>$null)
}

# ---- JWT minting (RS256, kid = nitrowater-key-1); aud = nitrowater-server ----
function New-ServiceJwt([hashtable]$Claims) {
    $rsa = [System.Security.Cryptography.RSA]::Create()
    $rsa.ImportFromPem((Get-Content -LiteralPath $JwtKeyFile -Raw))
    $b64 = { param([byte[]]$b) [Convert]::ToBase64String($b).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = @{} + $Claims
    $payload.iss = $Issuer
    $payload.aud = @('nitrowater-server')
    $payload.iat = $now
    $payload.nbf = $now - 5
    $payload.exp = $now + 3600
    $header = @{ alg = 'RS256'; typ = 'JWT'; kid = $JwtKid }
    $h = & $b64 ([Text.Encoding]::UTF8.GetBytes(($header | ConvertTo-Json -Compress)))
    $p = & $b64 ([Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json -Compress -Depth 8)))
    $signingInput = "$h.$p"
    $sig = $rsa.SignData([Text.Encoding]::UTF8.GetBytes($signingInput),
        [Security.Cryptography.HashAlgorithmName]::SHA256,
        [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    return "$signingInput.$(& $b64 $sig)"
}
