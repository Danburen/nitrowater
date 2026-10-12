#requires -Version 5.1
<#
.SYNOPSIS
    Install language runtimes into the local Piston sandbox (dev).

.DESCRIPTION
    The ghcr.io/engineer-man/piston image bundles NO language runtimes. This script
    makes the Piston API fetch its package index AND package tarballs through a GitHub
    proxy (default https://ghproxy.net), because direct GitHub access is unreliable on
    CN networks. It serves a proxy-rewritten index from a tiny local HTTP server and
    points the container's PISTON_REPO_URL at it, then installs the requested packages.

    Installed runtimes persist in the named volume `nitrowater-piston`, so this only
    needs to run once (or when adding a language). The API's `POST /api/v2/packages`
    crashes on an index-fetch failure, so pointing PISTON_REPO_URL at a reachable
    server (not raw GitHub) also removes that failure mode.

.PARAMETER Packages
    Piston package names to install. Defaults cover the nitrowater-server languages:
    java, gcc (-> c, c++), node (-> javascript), python, typescript, lua.

.EXAMPLE
    .\bin\install-piston-langs.ps1
#>
[CmdletBinding()]
param(
    [string] $Proxy = 'https://ghproxy.net/',
    [string] $Container = 'nitrowater-piston',
    [int]    $IndexPort = 8899,
    [string] $PistonApi = 'http://localhost:2000',
    [string[]] $Packages = @('gcc', 'node', 'python', 'typescript', 'lua', 'java')
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) { Write-Host "[piston-langs] $msg" -ForegroundColor Cyan }

# Known-good versions (latest in the index at time of writing).
$Versions = @{
    'gcc' = '10.2.0'; 'node' = '20.11.1'; 'python' = '3.12.0';
    'typescript' = '5.0.3'; 'lua' = '5.4.4'; 'java' = '15.0.2'
}

Write-Step "Fetching package index via proxy $Proxy ..."
$indexUrl = "${Proxy}https://github.com/engineer-man/piston/releases/download/pkgs/index"
$raw = (Invoke-WebRequest -Uri $indexUrl -UseBasicParsing -TimeoutSec 60).Content
$lines = ($raw -split "`n") | ForEach-Object { $_.TrimEnd("`r") } | Where-Object { $_ -ne '' }
if ($lines.Count -eq 0) { throw "Empty index from $indexUrl" }

# Rewrite each entry's download URL to go through the proxy.
$rewritten = foreach ($line in $lines) {
    $p = $line -split ',', 4
    if ($p.Count -eq 4) { "$($p[0]),$($p[1]),$($p[2]),$Proxy$($p[3])" } else { $line }
}

$idxPath = Join-Path $env:TEMP 'piston-index'
[IO.File]::WriteAllText($idxPath, (($rewritten -join "`n") + "`n"))
Write-Step "Wrote rewritten index ($($rewritten.Count) lines) to $idxPath"

# Tiny static server for the rewritten index.
$serverJs = Join-Path $env:TEMP 'piston-index-server.js'
@'
const http = require('http'), fs = require('fs');
http.createServer((req, res) => fs.createReadStream(process.argv[2]).pipe(res))
    .listen(Number(process.argv[3]), () => console.log('index-server up'));
'@ | Set-Content -Path $serverJs -Encoding ascii
$srv = Start-Process node -ArgumentList @($serverJs, $idxPath, $IndexPort) -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 2
Write-Step "Index server started (pid $($srv.Id)) on port $IndexPort"

try {
    $repoUrl = "http://host.docker.internal:$IndexPort/index"

    # (Re)create the container so PISTON_REPO_URL is set; the named volume keeps runtimes.
    $exists = (docker ps -a --filter "name=^/$Container$" --format '{{.Names}}')
    if ($exists) {
        $hasEnv = docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $Container |
                  Select-String -SimpleMatch "PISTON_REPO_URL=$repoUrl"
        if ($hasEnv) { docker start $Container | Out-Null }
        else {
            Write-Step "Recreating $Container with PISTON_REPO_URL=$repoUrl"
            docker rm -f $Container | Out-Null
            docker run -d --name $Container --privileged -p 2000:2000 `
                -v nitrowater-piston:/piston -e "PISTON_REPO_URL=$repoUrl" `
                -e PISTON_RUN_TIMEOUT=10000 -e PISTON_COMPILE_TIMEOUT=20000 `
                -e PISTON_RUN_MEMORY_LIMIT=268435456 -e PISTON_COMPILE_MEMORY_LIMIT=536870912 `
                -e PISTON_OUTPUT_MAX_SIZE=1048576 -e PISTON_MAX_CONCURRENT_JOBS=8 `
                ghcr.io/engineer-man/piston | Out-Null
        }
    } else {
        docker run -d --name $Container --privileged -p 2000:2000 `
            -v nitrowater-piston:/piston -e "PISTON_REPO_URL=$repoUrl" `
            -e PISTON_RUN_TIMEOUT=10000 -e PISTON_COMPILE_TIMEOUT=20000 `
            -e PISTON_RUN_MEMORY_LIMIT=268435456 -e PISTON_COMPILE_MEMORY_LIMIT=536870912 `
            -e PISTON_OUTPUT_MAX_SIZE=1048576 -e PISTON_MAX_CONCURRENT_JOBS=8 `
            ghcr.io/engineer-man/piston | Out-Null
    }

    Write-Step "Waiting for Piston API at $PistonApi ..."
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        try { if ((Invoke-WebRequest "$PistonApi/api/v2/runtimes" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { $ready = $true; break } } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "Piston API not reachable at $PistonApi" }

    foreach ($pkg in $Packages) {
        $ver = $Versions[$pkg]
        if (-not $ver) { throw "No known version for package '$pkg' (add it to `$Versions)" }
        $installed = (Invoke-RestMethod "$PistonApi/api/v2/packages" -TimeoutSec 60) |
                     Where-Object { $_.language -eq $pkg -and $_.language_version -eq $ver -and $_.installed }
        if ($installed) { Write-Step "already installed: $pkg $ver"; continue }
        Write-Step "installing $pkg $ver (large downloads can take several minutes) ..."
        $body = @{ language = $pkg; version = $ver } | ConvertTo-Json
        Invoke-RestMethod "$PistonApi/api/v2/packages" -Method Post -ContentType 'application/json' `
            -Body $body -TimeoutSec 1800 | Out-Null
        Write-Step "installed: $pkg $ver"
    }

    # TypeScript: Piston's package ships no Node typings, so require/process/Buffer fail with TS2580.
    # Install @types/node (persists in the volume) and make the compile step expose it to tsc.
    if ($Packages -contains 'typescript') {
        $tsVer = $Versions['typescript']
        Write-Step "Wiring @types/node into typescript $tsVer ..."
        docker exec $Container sh -lc "cd /piston/packages/typescript/$tsVer && [ -d types/node_modules/@types/node ] || bin/npm install --prefix /piston/packages/typescript/$tsVer/types @types/node@18 --registry https://registry.npmmirror.com --no-audit --no-fund" | Out-Null
        $compileScript = @'
#!/usr/bin/env bash
rename 's/$/\.ts/' "$@"

# Piston's TypeScript package ships no Node typings, so require/process/Buffer/__dirname fail
# with TS2580. Make the pre-installed @types/node visible to tsc in the job directory.
mkdir -p node_modules/@types
ln -sf __TS_TYPES__/node_modules/@types/node node_modules/@types/node

tsc *.ts
'@
        $compileScript = $compileScript -replace '__TS_TYPES__', "/piston/packages/typescript/$tsVer/types"
        $compilePath = Join-Path $env:TEMP 'piston-ts-compile.sh'
        [IO.File]::WriteAllText($compilePath, $compileScript)
        docker cp $compilePath "${Container}:/piston/packages/typescript/$tsVer/compile" | Out-Null
        docker exec $Container chmod +x "/piston/packages/typescript/$tsVer/compile" | Out-Null
        Write-Step "typescript @types/node wired in."
    }

    Write-Step "Runtimes now available:"
    (Invoke-RestMethod "$PistonApi/api/v2/runtimes" -TimeoutSec 30) |
        ForEach-Object { Write-Host ("  {0} {1}" -f $_.language, $_.version) }
} finally {
    if ($srv -and -not $srv.HasExited) { Stop-Process -Id $srv.Id -Force }
    Write-Step "Index server stopped."
}
