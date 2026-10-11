@echo off
REM Start self-hosted SeaweedFS (S3-compatible object storage, single node, local disk).
REM Default container command is `weed mini -dir=/data`; the named volume keeps data across restarts.
REM S3 endpoint: http://localhost:8333  (bucket: nitrowater)
REM NOTE: behind a reverse proxy, add "-s3.externalUrl=https://<public-host>" so presigned URLs
REM       use the public host (otherwise signature verification fails).
cd /d "%~dp0.."
docker run -d --name nitrowater-seaweedfs ^
  -p 8333:8333 -p 8888:8888 -p 9333:9333 -p 23646:23646 ^
  -v nitrowater-seaweedfs-data:/data ^
  -e AWS_ACCESS_KEY_ID=nitrowater ^
  -e AWS_SECRET_ACCESS_KEY=change-me ^
  -e S3_BUCKET=nitrowater ^
  chrislusf/seaweedfs
echo SeaweedFS started: S3 http://localhost:8333 , Filer UI http://localhost:8888 , Admin http://localhost:23646
