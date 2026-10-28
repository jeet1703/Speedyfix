# Backs up the local database (the speedyfix-db Docker container) to backups\speedyfix-<date>.dump.
# The dump is in Postgres's custom format, which db-restore.ps1 loads into any Postgres 16 server, including the live one.
# Usage (from the project root):  .\scripts\db-backup.ps1
$ErrorActionPreference = 'Stop'

$dir = Join-Path $PSScriptRoot '..\backups'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir ("speedyfix-{0}.dump" -f (Get-Date -Format 'yyyy-MM-dd-HHmm'))

docker exec speedyfix-db pg_dump -U speedyfix -d speedyfix --format=custom --no-owner --no-privileges -f /tmp/speedyfix.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed. Is the database running? (docker compose up -d db)' }
docker cp speedyfix-db:/tmp/speedyfix.dump $file
docker exec speedyfix-db rm /tmp/speedyfix.dump

Write-Host "Backed up to $file"
