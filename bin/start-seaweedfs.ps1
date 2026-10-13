# ============================================================
# start-seaweedfs.ps1 - start self-hosted SeaweedFS (S3 :8333, single node, local disk).
#
# S3 endpoint: http://localhost:8333 (bucket: nitrowater). Data persists in the named
# volume nitrowater-seaweedfs-data. Behind a reverse proxy add
# "-s3.externalUrl=https://<public-host>" so presigned URLs use the public host.
#
# Usage:  pwsh -File bin\start-seaweedfs.ps1
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_common.ps1"

Ensure-DockerContainer 'nitrowater-seaweedfs' 8333 'NitroWater - SeaweedFS' {
    & docker run -d --name nitrowater-seaweedfs `
        -p 8333:8333 -p 8888:8888 -p 9333:9333 -p 23646:23646 `
        -v nitrowater-seaweedfs-data:/data `
        -e AWS_ACCESS_KEY_ID=nitrowater -e AWS_SECRET_ACCESS_KEY=change-me -e S3_BUCKET=nitrowater `
        chrislusf/seaweedfs | Out-Null
}
