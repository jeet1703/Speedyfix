# Database: local PostgreSQL now, the live server later

The shop uses **PostgreSQL 16**. On your computer it runs in Docker; when the live server is bought (Render Postgres, see HOSTING.md) the same schema and data move across with one restore.

## What's set up

| Piece | Where |
| --- | --- |
| Local database | Docker container `speedyfix-db` (Postgres 16), defined in [compose.yaml](compose.yaml); data kept in the Docker volume `speedyfix-db-data` |
| Schema | Flyway migrations in [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration): `V1__schema.sql` (all 19 tables), `V2__categories.sql`, `V3__ebay_categories.sql` (shop categories for the eBay stock) |
| Connection | `postgres` Spring profile, [application-postgres.yml](backend/src/main/resources/application-postgres.yml). Without the profile the backend runs without a database (as the live site does today). |
| Backup and restore | [scripts/db-backup.ps1](scripts/db-backup.ps1), [scripts/db-restore.ps1](scripts/db-restore.ps1); backups go to `backups/` (git-ignored) |
| Test | `DatabaseMigrationTest` runs the migrations on a throwaway Postgres 16 whenever Docker is running |

**Listings now come from the database** when the `postgres` profile is on (1,380 imported from the eBay report: 1,262 live, 118 ended). **Still in memory:** baskets, orders, customers, part requests and settings. Moving those across is the rest of Part A4 in [IMPORT-AND-PAYMENTS-GUIDE.md](IMPORT-AND-PAYMENTS-GUIDE.md).

## Everyday use (PowerShell, from the project root)

| Task | Command |
| --- | --- |
| Start the database (Docker Desktop must be running) | `docker compose up -d db` |
| Stop it (data is kept) | `docker compose stop db` |
| Run the backend on it | `cd backend; $env:SPRING_PROFILES_ACTIVE='postgres'; $env:PORT='8081'; $env:ADMIN_PASSWORD='<your local password>'; mvn spring-boot:run` |
| Open a SQL prompt | `docker exec -it speedyfix-db psql -U speedyfix -d speedyfix` |
| Back up | `.\scripts\db-backup.ps1` |
| Wipe and start again (**deletes all local data**) | `docker compose down -v` then `docker compose up -d db` |

Local connection details, for a database app such as DBeaver or pgAdmin: host `localhost`, port `5432`, database `speedyfix`, user `speedyfix`, password `speedyfix-local-db`. These are for this computer only.

## The demo copy with stock photos

Until the real eBay photos are imported, the local site runs on **`speedyfix_demo`**: a copy of `speedyfix` in which every listing has a stock photo (Unsplash samples chosen by category, marked `SAMPLE`). The real `speedyfix` database is never changed, so stock photos can't reach the live site.

| Task | Command |
| --- | --- |
| Make or remake the copy (after each import, say) | `.\scripts\db-clone-demo.ps1` |
| Run the site on the copy | add `$env:DATABASE_URL='jdbc:postgresql://localhost:5432/speedyfix_demo'` before starting the backend |
| Run the site on the real database | leave `DATABASE_URL` unset |

When the eBay photos are imported, they replace a listing's stock photo, whichever database they go into.

## Import the eBay listings

The report goes in `data/imports/` (git-ignored). The import is safe to run again: listings are matched on their eBay item number and updated, never duplicated. It starts no web server, so the shop can keep running; the shop picks up the changes within a minute.

```powershell
cd backend
$env:EBAY_CLIENT_ID='…'; $env:EBAY_CLIENT_SECRET='…'   # optional: without them, photos and item specifics are skipped
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres" "-Dspring-boot.run.arguments=--import.ebay.csv=../data/imports/<report>.csv --spring.main.web-application-type=none"
```

| Option (add to the arguments) | Effect |
| --- | --- |
| `--import.ebay.dry-run=true` | Report only; nothing saved |
| `--import.ebay.limit=50` | First 50 rows only (good for a first test with the eBay keys) |
| `--import.ebay.details=false` | Skip eBay's API (no photos or item specifics) |
| `--import.ebay.refresh-photos=true` | Download photos again even for listings that already have them |

**What it does:** fixed-price listings in stock go **live**; quantity 0 becomes **ended**; auctions and classified ads (the cars for sale) are skipped. Categories come from `ebay_category_map` (filled with suggestions on the first run; change a row to re-point an eBay category, then re-run). With the eBay keys set, it calls eBay's Browse API in batches of 20 for each listing's photos and item specifics (brand, part number, type), downloads the photos once, and saves a 1,200 px and a 400 px JPEG per photo under `storage/listings/{listing}/` (git-ignored), with the paths in `product_photos`. Listings without photos show a "Photo coming soon" image.

**Results** of each run are in the `import_runs` table: `SELECT * FROM import_runs ORDER BY id DESC LIMIT 1;`

## Changing the schema

Never edit a migration that has already run. Add a new file with the next number, for example `V4__add_cars_for_sale.sql`. Flyway applies it on the next start-up, locally and on the live server.

## Moving to the live server (when it's bought)

1. **Create the database** in Render: New → PostgreSQL, **Frankfurt**, PostgreSQL **16**, plan Basic-256mb. Use the same region as the backend.
2. **Point the backend at it.** In Render → `speedyfix-backend` → Environment, add:

   | Variable | Value |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `postgres` |
   | `DATABASE_URL` | `jdbc:postgresql://<host>:5432/<database>`, from the database's **Internal** connection details (host, port, database) |
   | `DATABASE_USERNAME` | the database's username |
   | `DATABASE_PASSWORD` | the database's password |

3. **Deploy.** On start-up Flyway creates every table on the empty live database (check the log for "Successfully applied").
4. **Copy local data across** (optional: only if listings were imported locally first). Back up locally, then restore into the live database using its **External** connection string:

   ```powershell
   .\scripts\db-backup.ps1
   .\scripts\db-restore.ps1 -File backups\speedyfix-<date>.dump -DatabaseUrl '<External connection string>' -Clean
   ```

   `-Clean` replaces the empty tables Flyway made. Keep the connection string out of chat and git.
5. **Check:** open the shop and the admin; the listings and categories are there.
6. **Backups:** Render backs up paid databases; also run `db-backup.ps1` against the live database weekly (or set up the nightly copy to R2 described in STATUS.md, Phase 6).
