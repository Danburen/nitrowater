# ============================================================
# _common.ps1 - shared helpers for bin/*.ps1 dev scripts.
#
# Dot-source it:  . "$PSScriptRoot\_common.ps1"
# Provides: $RunDir (bin\.run), Write-Step/Ok/Warn/Err, Test-Port, Wait-Port,
#           Test-Docker, Start-Window, Save-WindowPid, Ensure-DockerContainer,
#           Stop-ListeningPort, Stop-ModuleProcess, Get-Jar, Start-ModuleJar.
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
# Java 25 is mandatory: the modules compile to class-file major 69. Resolve in this order:
#   $env:NITROWATER_JAVA -> the known JDK 25 install -> java.exe on PATH.
# Deliberately NOT $env:JAVA_HOME - on this machine it points at an older JDK (22) that cannot
# load the boot jars ("Unsupported class file major version 69").
function Resolve-JavaExe {
    if ($env:NITROWATER_JAVA) { return $env:NITROWATER_JAVA }
    $known = 'D:\Program Files\Java\openkdk-hotspot-25.0.1.1\bin\java.exe'
    if (Test-Path -LiteralPath $known) { return $known }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'java.exe not found; set NITROWATER_JAVA to a JDK 25 java.exe'
}
$JavaExe     = Resolve-JavaExe
$MysqlExe    = if ($env:NITROWATER_MYSQL) { $env:NITROWATER_MYSQL } else { 'E:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
$RedisDir    = if ($env:NITROWATER_REDIS_DIR) { $env:NITROWATER_REDIS_DIR } else { 'E:\Program Files\Redis' }
$RedisCli    = Join-Path $RedisDir 'redis-cli.exe'
$Gradlew     = Join-Path $RepoRoot 'gradlew.bat'

# ---- dev credentials / keys (dev only; see AGENTS.md) ----
$DbHost        = '127.0.0.1'
$DbUser        = 'root'
$DbPass        = '123456'
$NitrowaterKek = 'kElE+g/NUUkCAG0azg9ZcYYXbevGg9mYuUtJvICut98='
$ResourceInternalToken = if ($env:RESOURCE_INTERNAL_TOKEN) { $env:RESOURCE_INTERNAL_TOKEN } else { 'nitrowater-internal-dev' }
# No defaults live in application.yml any more (a missing prod secret must fail loudly); the dev
# values below are injected here so a local start needs no setup.
$BffClientSecret = if ($env:BFF_CLIENT_SECRET) { $env:BFF_CLIENT_SECRET } else { 'nitrowater-bff-secret' }
$S3SecretKey     = if ($env:S3_SECRET_KEY) { $env:S3_SECRET_KEY } else { 'change-me' }
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

# Same idea, but reports whether anything was actually stopped. start-all.ps1 uses it to replace
# a listener that is still running the PREVIOUS jar after a fresh build.
function Stop-ListeningPort([int]$Port) {
    $stopped = $false
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        try { Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop; $stopped = $true } catch { }
    }
    return $stopped
}

# Stop a service by its tracked wrapper PID first (this also closes the persistent console
# window and its java child), then by port as a fallback for hidden or manually started jars.
function Stop-ModuleProcess([string]$Module, [int]$Port) {
    $stopped = $false
    $pidFile = Get-ModulePidFile $Module
    if (Test-Path -LiteralPath $pidFile) {
        $raw = Get-Content -LiteralPath $pidFile -Raw -ErrorAction SilentlyContinue
        if ($null -ne $raw) { $raw = $raw.Trim() }
        if ($raw -match '^\d+$') {
            $proc = Get-Process -Id ([int]$raw) -ErrorAction SilentlyContinue
            if ($proc -and ($proc.ProcessName -in @('cmd', 'java'))) {
                & taskkill /PID ([int]$raw) /T /F *> $null
                if ($LASTEXITCODE -eq 0) { $stopped = $true }
            }
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
    if (Stop-ListeningPort $Port) { $stopped = $true }
    return $stopped
}

# ---- app jar bookkeeping ----
function Get-Jar([string]$Module) { return (Join-Path $RepoRoot "$Module\build\libs\app.jar") }
function Get-ModulePidFile([string]$Module) { return (Join-Path $RunDir "$Module.pid") }

# Launch a boot jar. WorkingDirectory MUST be the repo root (account resolves
# jwt.private-key=file:./deploy/keys/... relative to it).
#   default  : hidden process; stdout/stderr split into .run\logs\<module>.{out,err}.log
#   -Windowed: own visible console window showing the Spring log live (no file redirect)
# -NoWait returns as soon as the process is spawned (callers that start several services in
# parallel wait for the ports themselves).
function Start-ModuleJar([string]$Module, [int]$Port, [switch]$NoWait, [switch]$Windowed) {
    if (Test-TcpPort $Port) { Write-Ok "$Module already up ($Port)"; return $true }
    $jar = Get-Jar $Module
    if (-not (Test-Path -LiteralPath $jar)) { throw "jar missing: $jar (run: gradlew :${Module}:bootJar)" }
    if ($Module -eq 'nitrowater-account') { $env:NITROWATER_KEK = $NitrowaterKek }
    # Dev default so bin\dev\seed-res.ps1 / sync-res.ps1 work out of the box. The endpoint itself
    # is token-gated and restricted to loopback/private networks (see InternalEndpointFilter).
    if ($Module -eq 'nitrowater-resource') { $env:RESOURCE_INTERNAL_TOKEN = $ResourceInternalToken }
    # Secrets have no built-in defaults in application.yml: inject the dev values here (account and
    # the BFF must share BFF_CLIENT_SECRET; resource authenticates to SeaweedFS with S3_SECRET_KEY).
    if ($Module -eq 'nitrowater-account' -or $Module -eq 'nitrowater-bff') { $env:BFF_CLIENT_SECRET = $BffClientSecret }
    if ($Module -eq 'nitrowater-resource') { $env:S3_SECRET_KEY = $S3SecretKey }
    if ($Windowed) {
        # java.exe launched straight from a console-less parent inherits the (hidden) parent
        # console and shows nothing. Go through `cmd /k` like the infra windows do: the cmd
        # wrapper owns a fresh visible console, and the jar runs inside it.
        # The wrapper lives under .run\ (no spaces in its path), so Start-Window's
        # "no embedded quotes" contract still holds.
        $launcher = Join-Path $RunDir "run-$Module.cmd"
        $lines = @(
            '@echo off',
            ('"{0}" -jar "{1}"' -f $JavaExe, $jar),
            'echo.',
            'echo [exited with code %ERRORLEVEL%]',
            'pause'
        )
        Set-Content -LiteralPath $launcher -Value $lines -Encoding ascii
        $p = Start-Window "NitroWater - $Module" $launcher $RepoRoot
        Set-Content -LiteralPath (Get-ModulePidFile $Module) -Value $p.Id -Encoding ascii
        Write-Host "  launched $Module pid=$($p.Id) (visible console window)" -ForegroundColor Gray
    }
    else {
        $out = Join-Path $LogDir "$Module.out.log"
        $err = Join-Path $LogDir "$Module.err.log"
        $p = Start-Process -FilePath $JavaExe -ArgumentList @('-jar', $jar) -WorkingDirectory $RepoRoot `
            -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
        Set-Content -LiteralPath (Get-ModulePidFile $Module) -Value $p.Id -Encoding ascii
        Write-Host "  launched $Module pid=$($p.Id) (logs\$Module.out.log)" -ForegroundColor Gray
    }
    if ($NoWait) { return $true }
    if (Wait-TcpPort $Port 120) { Write-Ok "$Module up ($Port)"; return $true }
    Write-Warn "$Module not up after 120s (see the window, or logs\$Module.out.log)"
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
