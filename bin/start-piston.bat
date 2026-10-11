@echo off
REM Start self-hosted Piston (code execution sandbox) for local dev.
REM Requires: Docker running Linux containers, cgroup v2 (privileged mode is mandatory -
REM           isolate needs it). The image is launched with host port 2000 mapped so a
REM           host-run nitrowater-server can reach http://localhost:2000.
REM NOTE: the official image bundles NO language runtimes; install them once with
REM       bin\install-piston-langs.ps1 (runtimes persist in the named volume).
REM       For the compose/prod path (no host port) use deploy\docker\docker-compose.piston.yml.
cd /d "%~dp0.."
REM PISTON_* limits MUST match/exceed what nitrowater-server requests, else Piston returns 400
REM (e.g. "compile_timeout cannot exceed the configured limit of ..."). Mirrors docker-compose.piston.yml.
REM Recreate (not just start) so these env vars always apply; runtimes persist in the named volume.
docker rm -f nitrowater-piston >nul 2>&1
docker run -d --name nitrowater-piston ^
  --privileged ^
  -p 2000:2000 ^
  -v nitrowater-piston:/piston ^
  -e PISTON_RUN_TIMEOUT=10000 ^
  -e PISTON_COMPILE_TIMEOUT=20000 ^
  -e PISTON_RUN_MEMORY_LIMIT=268435456 ^
  -e PISTON_COMPILE_MEMORY_LIMIT=536870912 ^
  -e PISTON_OUTPUT_MAX_SIZE=1048576 ^
  -e PISTON_MAX_CONCURRENT_JOBS=8 ^
  ghcr.io/engineer-man/piston
echo Piston started: http://localhost:2000  ^(list languages: GET /api/v2/runtimes^)
