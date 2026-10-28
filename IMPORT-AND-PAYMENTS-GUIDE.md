# SpeedyFix Auto Sales: step-by-step guide to the database, the eBay import, image hosting and payments

Written 24 October 2026. This guide takes the shop from today's in-memory prototype to a live site with the business's real eBay stock, its own photo hosting, and card and PayPal payments. Each step says who does it, what to do, and how to know it's done.

**Order of work:** Part A (database) → Part B (image hosting) → Part C (eBay import: listings, specifics, photos) → Part D (keeping eBay and the website in step) → Part E (payments) → Part F (go live). Parts B and the owner's account set-up in C and E can start on day 1, while the database is built.

| Part | What | Who | Effort |
| --- | --- | --- | --- |
| A | PostgreSQL database | Developer | 1–2 days |
| B | Image hosting on Cloudflare R2 | Owner (account) + developer | Half a day |
| C | Import 1,408 eBay listings, their photos, specifics and compatibility | Owner (eBay developer account, category mapping) + developer | 2–3 days |
| D | Stop double-selling between eBay and the website | Developer | 1–2 days |
| E | Stripe payments, with PayPal through Stripe | Owner (Stripe account) + developer | 2–3 days |
| F | Go live | Both | 1 day |

Effort figures are planning estimates for one developer, not quotes.

---

## What the eBay CSV contains

File: `eBay-all-active-listings-report-2026-10-19-13335587752.csv` (Seller Hub "All active listings" report, 1,408 listings, UTF-8 with BOM, 30 columns).

| Column | Use in the import | Notes from the data |
| --- | --- | --- |
| Item number | **Key.** Links each listing to eBay for photos, specifics and stock sync | Unique, e.g. `365887388733` |
| Title | Listing title; part number, make, model, engine size and years are parsed from it as a fallback | 1,316 titles contain a part-number-like code; 1,186 contain a year range (e.g. `2014-2018`, `09-16`) |
| Available quantity | Stock | 1,290 in stock, **118 at 0 (all fixed-price parts)** |
| Format | Which listings to import | 1,380 `FIXED_PRICE` (parts), **22 `CLASSIFIED_AD` (whole used cars for sale, e.g. "2019 Jaguar E-Pace 2.0 D150", £13,995–£44,995)**, 6 `AUCTION` |
| Current price | Price (GBP) | £0.99 to £44,995; median £149.99 |
| Condition | Condition | 1,269 Used, 136 Remanufactured, 3 New. Maps straight onto the shop's three conditions. |
| eBay category 1 name / number | Mapped to the shop's categories (step C4) | **144 different eBay categories**; top: Engines 226, Doors & Door Skins 104, Turbos & Parts 96, ECUs & Computer Modules 69 |
| Sold quantity, Watchers | Seed "best selling" order | 212 sold in total |
| Start date | "Listed" date | `Sep-27-25 03:09:03 PDT` format |
| Custom label (SKU) | Kept if present | Only 26 listings have one |
| Card-grading columns, UPC, EAN, ISBN | Ignored | Empty or not relevant |

**Not in the CSV:** photos, description, item specifics (Brand, Manufacturer Part Number, Type…) and the compatibility (fitment) table. Those come from eBay's API in step C5, using the item number.

**Keep the CSV out of git.** It's business data. Move it to `data/imports/` (git-ignored, see step C1).

---

## Part A: Database (PostgreSQL)

**Progress (24 October 2026):** A1–A3 are done. Postgres 16 runs locally in Docker, the Flyway schema creates every table below, and backup/restore scripts are ready for the move to the live server. See [DATABASE.md](DATABASE.md). Next: A4 (move the stores onto the database).

### A1. Decide where it runs

| Environment | Database |
| --- | --- |
| Your computer | Postgres 16 in Docker: `docker run --name speedyfix-db -p 5432:5432 -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=speedyfix -d postgres:16` |
| Live | **Render Postgres Basic-256mb, Frankfurt** (~$6 a month + $0.30 per GB of disk), same region and private network as the backend. See HOSTING.md. |

### A2. Add the libraries (developer)

In `backend/pom.xml`: `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core` and `flyway-database-postgresql`; for tests, `org.testcontainers:postgresql`. Settings come from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

### A3. Create the tables with Flyway (`V1__schema.sql`)

| Table | Holds | Key columns |
| --- | --- | --- |
| `categories` | Shop categories | `id`, `name`, `popular`, `sort_order`, `image_key` |
| `ebay_category_map` | eBay category number → shop category | `ebay_category_id`, `ebay_name`, `category_id` |
| `products` | Listings | `id`, `name`, `category_id`, `condition`, `grade`, `price`, `part_number`, `engine_code`, `brand`, `sub_type`, `stock`, `best_seller`, `sold_count`, `status` (DRAFT/LIVE/ENDED), `added_on`, **`ebay_item_id` (unique)**, `ebay_synced_at`, `description` |
| `product_refinements` | Featured refinements | `product_id`, `value` |
| `product_aspects` | All eBay item specifics, kept as-is | `product_id`, `name`, `value` |
| `product_photos` | Photos | `id`, `product_id`, `position`, `r2_key`, `width`, `height`, `source` (EBAY/UPLOAD), `source_url` |
| `vehicles` | Vehicles for fitment | `id`, `make`, `model`, `variant`, `year_from`, `year_to`, `engine`, `engine_code`, `fuel` |
| `product_fitment` | Which vehicles a part fits | `product_id`, `vehicle_id`, `source` (EBAY_COMPATIBILITY/PART_NUMBER/ENGINE_CODE/MANUAL), `confidence` |
| `baskets`, `basket_items` | Baskets | basket `id`, `updated_at`; items `product_id`, `quantity` |
| `orders`, `order_items` | Orders | `reference`, `status`, customer and address fields, totals, `dispatch_*`, **`stripe_payment_intent_id`**, `paid_at` |
| `customers`, `customer_searches` | Google sign-in shoppers and their searches | `id` (Google sub), `email`, `name`, `joined_at`, `last_sign_in_at`, `sign_ins` |
| `part_requests`, `part_request_photos` | Part requests | as today; photos as `r2_key` in a private bucket |
| `settings`, `lookup_log` | Admin settings, plate lookups | as today |
| `import_runs` | One row per CSV import | `id`, `file_name`, `started_at`, `created`, `updated`, `skipped`, `errors` (JSON) |

Indexes: `products(status, category_id)`, `products(lower(part_number))`, `products(ebay_item_id)`, a trigram index on `products.name` for search, `product_fitment(vehicle_id)`.

### A4. Move each in-memory store onto the database (developer)

Replace `CatalogRepository`, `BasketService`, `OrderService`, `PartRequestService`, `CustomerService`, `AdminSettings`, `LookupLog` and `ProductPhotoStore` with JPA repositories. The REST API stays the same, so the frontend doesn't change.

**Stock safety:** placing an order takes stock with `SELECT … FOR UPDATE` inside one transaction, so two buyers can't both get the last one-off part.

### A5. Tests and seed data

- Tests run against a real Postgres through Testcontainers; all 41 current tests must still pass.
- `V2__demo_seed.sql` (development only, behind a Spring profile) loads today's demo parts and vehicles.

**Done when:** the backend restarts and every listing, order, customer and setting is still there.

---

## Part B: Image hosting (Cloudflare R2)

**Where the images live:** a Cloudflare R2 bucket, served through Cloudflare's network from `images.<your-domain>`. Not on eBay, not on Render, not in the database.

**Why not keep using eBay's image links?** eBay's photo URLs belong to eBay's listing pages. They can change or disappear when a listing ends, and hot-linking them makes the shop depend on eBay. The photos are the business's own, so download them once and host them ourselves.

**Why R2:** no charge for people viewing images (no egress fees), S3-compatible, 10 GB stored free.

**Size and cost estimate:** 1,408 listings × about 8 photos = about 11,000 photos. Stored as WebP in 3 sizes (400, 800 and 1,600 px wide) at about 80 KB average → **about 2.5–3 GB**. That's inside R2's free 10 GB. About 35,000 uploads, inside the free 1 million a month.

| Step | Who | What |
| --- | --- | --- |
| B1 | Owner | Create a Cloudflare account (free) and add the domain (moves DNS to Cloudflare). |
| B2 | Owner or developer | R2 → create bucket **`speedyfix-images`** (public product photos), location hint **Western Europe**. Create a second bucket **`speedyfix-private`** for part-request photos (never public). |
| B3 | Owner or developer | On `speedyfix-images` → Settings → **Custom domain** → `images.<your-domain>`. Before the domain is ready, turn on the bucket's `r2.dev` URL for testing only (it's rate-limited, so not for production). |
| B4 | Owner | R2 → Manage API tokens → **Create token** with *Object Read & Write* on those two buckets. Put the values straight into Render (below). **Don't send them in chat.** |
| B5 | Developer | Render backend variables: `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET=speedyfix-images`, `R2_PRIVATE_BUCKET=speedyfix-private`, `IMAGES_BASE_URL=https://images.<your-domain>` |
| B6 | Developer | Add the AWS S3 SDK (works with R2). One `PhotoStorage` service: `put(key, bytes, contentType)`, `delete(key)`, `url(key)`. |
| B7 | Developer | **Key layout:** `listings/{productId}/{position}-{400\|800\|1600}.webp`, plus the original kept as `listings/{productId}/{position}-orig.jpg`. Part-request photos: `requests/{reference}/{n}.{ext}` in the private bucket. |
| B8 | Developer | Resize and convert to WebP on upload (both admin uploads and the eBay import). Strip camera metadata (EXIF, including GPS). |
| B9 | Developer | Shop pages use `srcset` with the three sizes. Cloudflare caches images for a year (keys never change; a replaced photo gets a new key). |

**Done when:** a photo uploaded in the admin appears in the shop from `images.<domain>` (or the r2.dev test URL) and survives a redeploy.

---

## Part C: Import the eBay listings, photos, specifics and compatibility

### C1. Put the CSV in place (developer)

```
data/imports/eBay-all-active-listings-report-2026-10-19-13335587752.csv
```

Add `data/` to `.gitignore`. Each new eBay report goes in the same folder.

### C2. Create an eBay developer account and keys (owner, 15 minutes + approval)

1. Sign up at **developer.ebay.com** with the business's eBay account (the one that owns the listings).
2. **Application Keys → Create a keyset** for **Production**. You get an **App ID (Client ID)**, a **Cert ID (Client Secret)** and a **Dev ID**.
3. **User Tokens → Get a Token from eBay via Your Application**: sign in with the seller account and grant access. This lets the import read the shop's **own** listings in full, including photos, item specifics and the compatibility table.
4. Put the values straight into Render or your local environment: `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `EBAY_DEV_ID`, `EBAY_REFRESH_TOKEN`. **Never in chat or git.**
5. eBay may ask you to set up **Marketplace Account Deletion notifications** before production keys work. The developer adds a small endpoint for it (`/api/ebay/account-deletion`).

### C3. Decide what to import (owner, with the developer)

| Group | Count | Suggested rule |
| --- | --- | --- |
| Fixed price, in stock | 1,262 | **Import as LIVE** |
| Fixed price, quantity 0 | 118 | Import as **ENDED** (kept for "sold" pages and search), or skip |
| Classified ads | 22 | These are **whole used cars for sale** (£13,995 and up). Recommended: a separate **Cars for sale** section (photos, specs, price, "Enquire" / "Book a viewing" / WhatsApp buttons, no basket or online payment), imported into its own `vehicles_for_sale` table. **Owner decides** whether cars are in scope for launch. |
| Auctions | 6 | **Skip.** The shop sells at fixed prices |
| Prices | all | Confirm eBay prices **include VAT** (the shop shows VAT-inclusive prices) |

### C4. Map eBay's 144 categories onto the shop's categories (owner reviews)

The developer generates `data/category-map.csv` (eBay category number, eBay name, listing count, suggested shop category). The owner corrects the last column. Suggested shop categories from the data (most listings first):

| Shop category | eBay categories that feed it (examples) |
| --- | --- |
| Engines | Engines (226) |
| Gearboxes | Manual / automatic gearboxes, transfer boxes |
| Turbochargers | Turbos & Parts (96) |
| ECUs and modules | ECUs & Computer Modules (69), other control modules |
| Fuel injection | Fuel Injectors (45), fuel pumps, fuel rails |
| Starters and alternators | Alternators & Generators (44), Starter Motors (28) |
| Cooling and A/C | A/C Compressors & Clutches (33), radiators |
| Body panels and doors | Doors & Door Skins (104), Bumpers & Reinforcements (32), bonnets, wings |
| EGR and emissions | EGR Parts & Valves (28), DPFs |
| Ignition | Ignition Coils (23) |
| Accessories | Other Car Parts & Accessories (27) |

The homepage's popular categories stay Engines, Gearboxes, Accessories, Turbochargers. Loading the map fills `ebay_category_map`.

### C5. Build the importer (developer)

An admin-only job, **Listings → Import from eBay**, run on demand. It does each step below and can safely run again: it updates by `ebay_item_id` and never duplicates.

1. **Read the CSV:** strip the BOM, parse with a real CSV parser (titles contain commas), skip blank rows, apply the C3 rules.
2. **Upsert the listing:** title, price, quantity → stock, condition, shop category (via C4), listed date, `sold_count` from Sold quantity, `ebay_item_id`.
3. **Fetch full details from eBay** using the item number, with the **Trading API `GetItem`** (seller's own token, `IncludeItemCompatibilityList=true`, `DetailLevel=ReturnAll`):
   - **Photos:** `PictureDetails.PictureURL` (all of them, in order).
   - **Item specifics:** Brand, Manufacturer Part Number, OE/OEM Reference Number, Type, Engine size and others → `product_aspects`; mapped onto the shop's **brand**, **part number** and **sub type**.
   - **Compatibility table:** make, model, variant, years, engine → `vehicles` + `product_fitment` (source EBAY_COMPATIBILITY, high confidence). This fills fitment for most listings at once.
   - **Description:** cleaned of eBay HTML and scripts, stored as plain paragraphs.

   (Alternative with no seller sign-in: the **Browse API** `getItemByLegacyId` with an application token returns photos, specifics and the description, but **not** the compatibility table. Use Trading API `GetItem`.)
4. **Fall back to the title when specifics are missing:** part number = the code-like token (e.g. `237104ED9B`, `0445110546`), years from `2014-2018` or `09-16`, engine size from `1.6L`, make and model from the start of the title.
5. **Photos:** download each eBay picture at its largest size, resize to 400, 800 and 1,600 px WebP (Part B), upload to R2, and record them in `product_photos` (source EBAY, original URL kept for reference). Listings that already have photos are skipped unless "refresh photos" is ticked.
6. **Rate limits:** eBay's default allowance is **5,000 calls a day** per API ([eBay API call limits](https://developer.ebay.com/develop/api/buy/api_call_limits)). 1,408 `GetItem` calls fit in one day, but the import pauses and resumes the next day if it hits the limit. Run about 3 calls a second, and retry with back-off on errors.
7. **Report:** the import writes an `import_runs` row and shows a summary: created, updated, skipped (with reasons), photos uploaded, listings with no part number, and listings with no fitment. Problem rows can be downloaded as CSV.

### C6. Dry run, check, then import for real

| Step | Who | Check |
| --- | --- | --- |
| C6.1 | Developer | **Dry run** on the first 50 rows locally: nothing saved, report only |
| C6.2 | Owner | Look at 10 imported listings side by side with eBay: title, price, photos, part number, fitment |
| C6.3 | Developer | Full import on **staging** (Neon or a second Render database) |
| C6.4 | Owner | Spot-check 30 listings across categories; fix the category map if needed and re-run (it's safe to re-run) |
| C6.5 | Developer | Full import on **live**; listings go live, and the Listings tab's "Needs attention" shows what still needs fitment or photos |

**Done when:** about 1,262 live listings each have photos from `images.<domain>`, a category, and (for most) a part number and fitment. The demo sample parts are removed from live.

---

## Part D: Keep eBay and the website in step (prevent double-selling)

Most stock is one-off used parts, listed on eBay **and** the website. Selling on one must take it off the other.

| Step | What | How |
| --- | --- | --- |
| D1 | Website sale → eBay | When a website order is **paid** (Part E webhook), call Trading API **`ReviseInventoryStatus`** to lower the eBay quantity (ending the listing at 0). Failures retry, and an alert shows on the Overview. |
| D2 | eBay sale → website | Every 10 minutes, call **`GetSellerTransactions`** (or subscribe to eBay platform notifications) and reduce stock on the matching `ebay_item_id`. |
| D3 | Nightly check | A nightly job compares eBay quantity and price with the website for every linked listing and lists any differences on the Overview. |
| D4 | New listings | New stock is listed on eBay as now, then a quick **Import from eBay** brings it in, or a scheduled import runs nightly from the newest active-listings report. |
| D5 | Admin edits | Price and stock edits in the admin can also be pushed to eBay (ReviseInventoryStatus). Titles and photos are edited on eBay and pulled in. |

**Done when:** a test item bought on the website disappears from eBay within a minute, and one bought on eBay disappears from the website within 10 minutes.

---

## Part E: Payment gateway (Stripe, with PayPal through Stripe)

**Decision:** Stripe for cards, Apple Pay and Google Pay, **with PayPal switched on inside Stripe**. Stripe supports PayPal for UK businesses ([Stripe docs](https://docs.stripe.com/payments/paypal/activate)): one integration, one dashboard and one payout. Fallback, only if Stripe won't enable PayPal on the account: a separate PayPal Business account (2.9% + 30p per sale) and PayPal's own buttons.

### E1. Owner: accounts and settings (start on day 1; verification can take a few days)

1. Create the Stripe account at **dashboard.stripe.com/register** in the business's name: company number, registered address, a director's ID, and the **business bank account** for payouts.
2. **Settings → Payment methods:** cards on (default); **Apple Pay** and **Google Pay** on; **PayPal → Turn on**, settling into the Stripe balance.
3. **Settings → Public details:** statement descriptor, e.g. `SPEEDYFIX AUTO`, plus support email and phone.
4. **Developers → API keys:** copy the **test** keys into Render. Don't send them in chat.
5. Once the live domain is connected: **Payment methods → Domains → add `www.<your-domain>`** (needed for Apple Pay).

### E2. Developer: settings

| Variable | Value |
| --- | --- |
| `STRIPE_SECRET_KEY` | `sk_test_…` now, `sk_live_…` at launch (secret) |
| `STRIPE_PUBLISHABLE_KEY` | `pk_test_…` / `pk_live_…` (sent to the browser; not secret) |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` from E4 (secret) |

### E3. Developer: checkout flow

1. Add the Stripe Java SDK (`com.stripe:stripe-java`) and `@stripe/stripe-js` + `@stripe/react-stripe-js` on the frontend.
2. **`POST /api/checkout`**: validates the delivery details (as today), works out the total **on the server** from the basket, creates the order as **AWAITING_PAYMENT**, **holds the stock for 30 minutes**, and creates a Stripe **PaymentIntent** (amount in pence, GBP, `automatic_payment_methods` on, order reference in metadata, an idempotency key per order). It returns the `client_secret`.
3. **Checkout page:** the **Payment Element** shows cards, Apple Pay, Google Pay and PayPal. On submit: `stripe.confirmPayment(...)` with `return_url` set to the order-received page.
4. **Order-received page:** reads the PaymentIntent status. Paid → "Order confirmed"; processing → "We're confirming your payment"; failed → "Payment didn't go through" with **Try again**.
5. The footer payment logos show only once payment is live.

### E4. Developer: webhook (the source of truth)

1. Endpoint **`POST /api/payments/stripe/webhook`**, outside the admin sign-in; the **signature is checked** with `STRIPE_WEBHOOK_SECRET`.
2. In the Stripe Dashboard → Developers → Webhooks, add that URL with these events: `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `charge.dispute.created`.
3. `payment_intent.succeeded` → order **AWAITING_DISPATCH** (paid); record `paid_at`; push the stock change to eBay (D1). Processed once only, even if Stripe sends it twice.
4. `payment_intent.payment_failed` → the order stays unpaid; the shopper can retry.
5. A scheduled job releases the stock of orders still unpaid after 30 minutes and marks them **EXPIRED**.
6. `charge.dispute.created` → shows on the Overview for staff.

### E5. Developer: refunds in the admin

The order page gets **Refund** (full or partial, with a reason). It calls Stripe's Refund API; the order shows the refund; stock goes back on a full refund if the part is returned. The existing **Mark as paid** stays for phone and bank-transfer orders.

### E6. Testing (test mode)

| Test | Expected |
| --- | --- |
| Card `4242 4242 4242 4242` | Paid; order awaiting dispatch; eBay quantity reduced |
| Card `4000 0000 0000 9995` (declined) | "Payment didn't go through"; stock still held, then released after 30 min |
| Card `4000 0025 0000 3155` (3-D Secure) | Bank check pop-up, then paid |
| PayPal (test mode) | Redirect to PayPal sandbox and back; paid |
| Apple Pay / Google Pay | Shown on supported devices once the domain is registered |
| Refund from admin | Refund in Stripe; order shows refunded |
| Webhook sent twice | Order updated once |

### E7. Go live with payments

1. Owner switches Stripe to **live mode**; the developer swaps in the **live** keys and creates the **live** webhook.
2. Place **one real order** with a real card and one with PayPal, then refund both from the admin.
3. Check the money and refunds in the Stripe dashboard, and the first payout to the bank.

**Card security:** card and PayPal details only ever go to Stripe, never to our server, which keeps the shop on Stripe's simplest compliance level (SAQ A).

---

## Part F: Go live checklist

- [ ] Database live on Render Postgres, with backups on and one restore tested
- [ ] Photos served from `images.<domain>` (R2)
- [ ] eBay import run on live; demo sample parts removed
- [ ] eBay ⇄ website stock sync tested both ways
- [ ] Stripe live keys, webhook live, real test orders refunded
- [ ] `ADMIN_PASSWORD` set in Render; Google sign-in origins include the live domain
- [ ] Business details, terms, privacy and returns policy on the site
- [ ] Domain on Cloudflare; frontend on Cloudflare Pages; backend on a paid Render plan
- [ ] Sitemap submitted to Google Search Console

---

## Two-week schedule

| Day | Owner | Developer |
| --- | --- | --- |
| 1 | Stripe account + verification; eBay developer account + keys (C2); Cloudflare account; set `ADMIN_PASSWORD` | Database schema and Flyway (A1–A3) |
| 2 | Approve hosting; R2 buckets and token (B1–B4) | Move all stores to the database (A4–A5) |
| 3 | Decide classified ads, auctions and VAT (C3) | R2 photo storage, resizing, admin uploads (B5–B9) |
| 4 | Review the category map (C4) | eBay importer: CSV, GetItem, specifics, compatibility (C5) |
| 5 | Check 10 listings from the dry run (C6.2) | Photo import to R2; import report; staging import |
| 6 | Spot-check 30 listings on staging (C6.4) | eBay stock sync (Part D) |
| 7 | Stripe test keys in Render; PayPal on in Stripe | Checkout with Payment Element (E3) |
| 8 | — | Webhook, stock holds, refunds (E4–E5) |
| 9 | Try test payments (E6) | Fixes; full import on live (C6.5) |
| 10 | Live keys; real orders and refunds (E7) | Go live (Part F) |

---

## Sources

- eBay API call limits: https://developer.ebay.com/develop/api/buy/api_call_limits
- eBay Browse API overview: https://developer.ebay.com/api-docs/buy/browse/overview.html
- eBay Trading API, retrieving items: https://developer.ebay.com/api-docs/user-guides/static/trading-user-guide/retrieve-items.html
- Stripe, PayPal for UK businesses: https://docs.stripe.com/payments/paypal/activate
- Stripe UK pricing: https://stripe.com/gb/pricing
- Cloudflare R2 pricing: https://developers.cloudflare.com/r2/pricing/
