# Loads a backup made by db-backup.ps1 into a Postgres 16 database: the live server, or the local one.
# Usage (from the project root):
#   .\scripts\db-restore.ps1 -File backups\speedyfix-2026-10-24-2200.dump -DatabaseUrl 'postgresql://user:password@host:5432/dbname?sslmode=require'
# For the live server, use the database's EXTERNAL connection string from the host's dashboard (Render: Connect → External).
# The password is passed straight to the restore tool and isn't stored anywhere.
param(
    [Parameter(Mandatory = $true)] [string] $File,
    [Parameter(Mandatory = $true)] [string] $DatabaseUrl,
    # Drops and recreates each table first. Use it to replace data that's already there.
    [switch] $Clean
)
$ErrorActionPreference = 'Stop'

$path = Resolve-Path $File
$dir = Split-Path $path
$name = Split-Path $path -Leaf
$cleanArgs = if ($Clean) { @('--clean', '--if-exists') } else { @() }

# Runs pg_restore from the same Postgres 16 image, so nothing needs installing on this computer.
docker run --rm -v "${dir}:/backup:ro" postgres:16-alpine `
    pg_restore --no-owner --no-privileges @cleanArgs --dbname "$DatabaseUrl" "/backup/$name"
if ($LASTEXITCODE -ne 0) { throw 'pg_restore reported errors (see above).' }

Write-Host "Restored $name"
