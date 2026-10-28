# Makes (or remakes) speedyfix_demo: a copy of the local speedyfix database with stock photos on every listing that
# has none. The real database is only read, never changed, so stock photos can't reach the live site by accident.
# Usage (from the project root, Docker running):  .\scripts\db-clone-demo.ps1
# Then run the backend with  $env:DATABASE_URL='jdbc:postgresql://localhost:5432/speedyfix_demo'
$ErrorActionPreference = 'Stop'

docker exec speedyfix-db psql -U speedyfix -d postgres -v ON_ERROR_STOP=1 -q `
    -c "DROP DATABASE IF EXISTS speedyfix_demo WITH (FORCE);" -c "CREATE DATABASE speedyfix_demo;"
if ($LASTEXITCODE -ne 0) { throw 'Could not create speedyfix_demo. Is the database running? (docker compose up -d db)' }

# Copy everything (tables, data, migration history) from the real database into the demo one.
docker exec speedyfix-db sh -c "pg_dump -U speedyfix -d speedyfix --no-owner | psql -U speedyfix -d speedyfix_demo -q -v ON_ERROR_STOP=1"
if ($LASTEXITCODE -ne 0) { throw 'Copying the database failed.' }

docker cp (Join-Path $PSScriptRoot 'stock-photos.sql') speedyfix-db:/tmp/stock-photos.sql
docker exec speedyfix-db psql -U speedyfix -d speedyfix_demo -v ON_ERROR_STOP=1 -f /tmp/stock-photos.sql
if ($LASTEXITCODE -ne 0) { throw 'Adding stock photos failed.' }

Write-Host 'speedyfix_demo is ready: a copy of speedyfix with stock photos.'
