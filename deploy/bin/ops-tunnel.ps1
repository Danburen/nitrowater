<#
ops-tunnel.ps1 - open SSH tunnels to the ops UIs (Qdrant dashboard, SeaweedFS filer/master).

Why: in production those ports are bound to 127.0.0.1 (OPS_BIND) because none of those UIs has
authentication of its own, and they must not be reachable from the network. A tunnel gives you the
normal browser experience without exposing anything:

    http://127.0.0.1:6333/dashboard    Qdrant (collections, points, payload, filtering)
    http://127.0.0.1:8888              SeaweedFS filer (browse / download / delete objects)
    http://127.0.0.1:9333              SeaweedFS master (topology, volumes)

Usage (from anywhere):
  .\deploy\bin\ops-tunnel.ps1 -HostName ops.example.com
  .\deploy\bin\ops-tunnel.ps1 -HostName 1.2.3.4 -User ubuntu -SshPort 2222
  .\deploy\bin\ops-tunnel.ps1 -HostName ops.example.com -IdentityFile C:\Users\me\.ssh\id_ed25519
  .\deploy\bin\ops-tunnel.ps1 -HostName ops.example.com -NoFiler -NoMaster   # Qdrant only

Notes:
  - The local ports are the same numbers as the remote ones, so bookmarks stay valid.
  - Tunnels stop when you close the window (Ctrl+C / Ctrl+D).
  - Always target 127.0.0.1 on the remote side; `localhost` may resolve to ::1 and fail.
#>
param(
  [Parameter(Mandatory = $true)][string]$HostName,
  [string]$User = 'root',
  [int]$SshPort = 22,
  [string]$IdentityFile,
  [int]$QdrantPort = 6333,
  [int]$FilerPort = 8888,
  [int]$MasterPort = 9333,
  [switch]$NoQdrant,
  [switch]$NoFiler,
  [switch]$NoMaster
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
  Write-Host '[ERROR] ssh not found in PATH (install the OpenSSH client)' -ForegroundColor Red
  exit 1
}

$forwards = New-Object System.Collections.Generic.List[string]
if (-not $NoQdrant) { $forwards.Add(('{0}:127.0.0.1:{0}' -f $QdrantPort)) }
if (-not $NoFiler) { $forwards.Add(('{0}:127.0.0.1:{0}' -f $FilerPort)) }
if (-not $NoMaster) { $forwards.Add(('{0}:127.0.0.1:{0}' -f $MasterPort)) }
if ($forwards.Count -eq 0) {
  Write-Host '[ERROR] every tunnel was disabled (-NoQdrant -NoFiler -NoMaster)' -ForegroundColor Red
  exit 1
}

$sshArgs = @(
  '-N',
  '-o', 'ServerAliveInterval=30',
  '-o', 'ServerAliveCountMax=3',
  '-o', 'ExitOnForwardFailure=yes',
  '-p', $SshPort
)
if ($IdentityFile) { $sshArgs += @('-i', $IdentityFile) }
foreach ($f in $forwards) { $sshArgs += @('-L', $f) }
$sshArgs += ('{0}@{1}' -f $User, $HostName)

Write-Host '=== ops tunnels ===' -ForegroundColor Cyan
if (-not $NoQdrant) { Write-Host ("  Qdrant     http://127.0.0.1:{0}/dashboard" -f $QdrantPort) }
if (-not $NoFiler) { Write-Host ("  Filer      http://127.0.0.1:{0}" -f $FilerPort) }
if (-not $NoMaster) { Write-Host ("  Master     http://127.0.0.1:{0}" -f $MasterPort) }
Write-Host ("  ssh target {0}@{1}:{2}" -f $User, $HostName, $SshPort) -ForegroundColor DarkGray
Write-Host '  Ctrl+C to close.' -ForegroundColor DarkGray
Write-Host ''

& ssh @sshArgs
exit $LASTEXITCODE
