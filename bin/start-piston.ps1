# ============================================================
# start-piston.ps1 - start the self-hosted Piston code sandbox (dev, :2000).
#
# Requires Docker on Linux containers + cgroup v2 (privileged mode is mandatory - isolate
# needs it). Host port 2000 is mapped so a host-run nitrowater-server can reach
# http://localhost:2000. For the compose/prod path (no host port) use
# deploy\docker\docker-compose.piston.yml.
#
# NOTE: the official image bundles NO language runtimes; install them once with
# bin\install-piston-langs.ps1 (they persist in the named volume nitrowater-piston).
#
# PISTON_* limits MUST match/exceed what nitrowater-server requests, else Piston returns 400
# (e.g. "compile_timeout cannot exceed the configured limit of ..."). Mirrors
# docker-compose.piston.yml. Recreate (not just start) so these env vars always apply;
# runtimes persist in the named volume.
#
# Usage:  pwsh -File bin\start-piston.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

Ensure-DockerContainer 'nitrowater-piston' 2000 'NitroWater - Piston' -Recreate {
    & docker run -d --name nitrowater-piston `
        --privileged -p 2000:2000 -v nitrowater-piston:/piston `
        -e PISTON_RUN_TIMEOUT=10000 -e PISTON_COMPILE_TIMEOUT=20000 `
        -e PISTON_RUN_MEMORY_LIMIT=268435456 -e PISTON_COMPILE_MEMORY_LIMIT=536870912 `
        -e PISTON_OUTPUT_MAX_SIZE=1048576 -e PISTON_MAX_CONCURRENT_JOBS=8 `
        -e LANG=en_US.UTF-8 -e LC_ALL=en_US.UTF-8 `
        ghcr.io/engineer-man/piston | Out-Null
    # NOTE: LANG/LC_ALL above do NOT reach the JVM (Piston's isolate sandbox clears the env); the
    # actual UTF-8 (CJK) fix is the java package `run` patch applied by bin\install-piston-langs.ps1.
}
