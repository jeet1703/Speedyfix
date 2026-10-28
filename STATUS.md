# SpeedyFix Auto Sales v2: project status and launch plan

Status as of 24 October 2026. Checked locally: all 50 backend tests pass (including the database migrations and the eBay import against a real Postgres), the frontend type-checks and builds, and the shop runs on the local database with the real eBay stock.

**In short:** on this computer, the shop now shows the business's **1,262 live eBay listings** from a PostgreSQL database, and the Seller Hub admin works end to end. It is **not ready for real customers yet**:

1. **Photos:** every listing shows "Photo coming soon" until the eBay developer keys are added (the photo import is built and tested).
2. **Orders, baskets and customers** are still held in memory, so a restart loses them (listings are already in the database).
3. **No payment is taken** (Stripe with PayPal is decided, not built).
4. **Plate lookup** only knows 3 demo vehicles.
5. **The live site** still runs an older version: none of the database, import or Seller Hub work is deployed.

---

## 0. Done and left, at a glance

Step-by-step detail is in **[IMPORT-AND-PAYMENTS-GUIDE.md](IMPORT-AND-PAYMENTS-GUIDE.md)**; database commands in **[DATABASE.md](DATABASE.md)**.

| # | Area | ✅ Done | ⏳ Left | Waiting on |
| --- | --- | --- | --- | --- |
| 1 | **Database** | Local Postgres 16 in Docker; Flyway schema (19 tables, 21 categories); backup and restore scripts tested; **listings read from the database** | Move baskets, orders, customers, searches, part requests, settings and admin photo uploads onto the database; create the live database on Render | Nothing for the local work. The live database waits on approving ~£6 a month. |
| 2 | **eBay import** | CSV importer: **1,380 listings imported** (1,262 live, 118 ended), 144 eBay categories mapped to 21 shop categories, part numbers read from 912 titles, safe to re-run. Photo and item-specifics step (Browse API, 20 per call) built and tested. | Run it with the eBay keys to bring in photos, brand, part number and type; review the category map; decide on the 22 **cars for sale**; later, the compatibility (fitment) table via the Trading API | **eBay developer keys** (`EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`) |
| 3 | **Keeping eBay and the website in step** | — | Website sale lowers eBay stock; eBay sale lowers website stock; nightly check (guide Part D) | Seller sign-in to eBay's Trading API |
| 4 | **Photo hosting** | Photos stored on disk (`storage/`), served at `/api/images`, 1,200 px + 400 px JPEGs, placeholder for listings without photos. **Stock photos for now** in a demo copy of the database (`speedyfix_demo`), so the local site looks complete | Move to **Cloudflare R2** behind `images.<domain>` (same paths) | Cloudflare account, R2 bucket and token |
| 5 | **Payments: Stripe + PayPal** | Admin order handling: mark paid, dispatch with courier and tracking, cancel (restocks) | Stripe Payment Element at checkout (cards, Apple Pay, Google Pay, PayPal), webhook marks orders paid, 30-minute stock hold, refunds from the admin | **Stripe account** (verified) and test keys |
| 6 | **Vehicle lookup** | **DVSA MOT History API connected in code** (switches on when its 4 credentials are set; 30-day cache keyed by a hash of the plate); **"Likely fits"** from each listing's title (make, model, years, engine size), used by fit badges, the "fits my car" filter and the fitting-parts count; demo plates until then | Live test with real credentials; manual make/model picker; paid engine-code data if the free route misses too often | **DVSA API registration** (up to 5 working days) |
| 7 | **Admin and sign-in** | Seller Hub admin (Overview with sales chart, Orders, Listings incl. Ended, Customers, Part requests, Vehicle lookup, Settings); email + password with JWT; Google sign-in works locally | Set the live admin password; add the live address to Google's allowed origins; more staff logins (later) | `ADMIN_PASSWORD` in Render; Google Cloud settings |
| 8 | **Shop** | Owner's changes (menu, categories, filters like eBay, no returns page or trade accounts); recent searches; Products menu fixed | About, Delivery, FAQ, Terms, Privacy pages; Cars for sale section if wanted | Owner's text and decision |
| 9 | **Hosting and deploy** | Live today: GitHub Pages + Render free (older version) | Commit and deploy the latest work; domain on Cloudflare; Cloudflare Pages + Render Starter + Render Postgres | Approval of ~£11–17 a month; the domain name |
| 10 | **Business details and legal** | — | Company name and number, VAT number, address, phone, warranty, couriers; terms, privacy and returns policies | Owner and legal adviser |
| 11 | **SEO basics** | — | Page titles and descriptions, sitemap, product structured data, robots.txt | Nothing |
| 12 | **Order emails** | — | Out of scope for launch (Resend when wanted) | Decision |

### 0.1 What to do next

**Developer, now (nothing blocking):**
1. Move baskets, orders, customers, searches, part requests and settings onto the database (about 1 day), so nothing is lost on a restart.
2. Commit the work and deploy the branch, keeping the live site on its current setup until the live database exists.
3. SEO basics and the content-page template.

**Owner, now (these unblock everything else; keys go into Render or `CREDENTIALS.local.md`, never chat):**
1. eBay developer account and Production keys → photos and item specifics.
2. Stripe account (start verification) → payments.
3. Cloudflare account, R2 bucket and token, domain on Cloudflare → photo hosting and the live domain.
4. DVSA MOT History API registration → real plate lookup.
5. `ADMIN_PASSWORD` in Render, and the live address in Google's allowed origins.
6. Business details, policies from the legal adviser, and the cars-for-sale decision.

### 0.2 Action plan (2 weeks from the day the owner's accounts are ready)

| When | Owner | Developer |
| --- | --- | --- |
| **Day 1** | eBay keys, Stripe account, Cloudflare and DVSA sign-ups; `ADMIN_PASSWORD` in Render | Remaining stores onto the database; commit and deploy |
| **Day 2** | Review the category map | eBay import with photos (test 50, then all); R2 photo hosting |
| **Days 3–5** | Stripe **test** keys in Render; PayPal on in Stripe | Stripe Payment Element, webhook, stock hold, refunds |
| **Day 6** | Try checkout and the admin on staging | eBay ⇄ website stock sync |
| **Day 7** | Policies and details due; DVSA key arrived | Real plate lookup; legal pages in |
| **Day 8** | — | SEO basics |
| **Day 9** | Approve hosting; domain on Cloudflare | Render Postgres, paid hosting, custom domain; backups and monitoring |
| **Day 10** | Stripe **live** keys; place and refund a real order | Go live; submit the sitemap to Google |

### 0.3 Payment gateway: what we need

**Recommendation: take PayPal through Stripe.** Stripe can accept PayPal for UK businesses ([Stripe docs](https://docs.stripe.com/payments/paypal/activate)). That gives one integration, one dashboard, one webhook and one payout for cards, Apple Pay, Google Pay and PayPal. The alternative, a separate PayPal Business account with PayPal's own buttons (2.9% + 30p per sale), is a second integration and a second reconciliation, so only choose it if Stripe's PayPal option isn't available on the account.

**From the owner:**

| Item | Where | Notes |
| --- | --- | --- |
| Stripe account in the business's name | [dashboard.stripe.com](https://dashboard.stripe.com/register) | Company details, a director's ID, the business bank account for payouts. Verification can take a few days, so start on day 1. |
| PayPal switched on | Stripe Dashboard → Settings → Payment methods → PayPal → Turn on | Choose to settle into the Stripe balance so all money arrives in one payout. |
| Apple Pay / Google Pay | Stripe Dashboard → Payment methods → register the shop's domain | Done once the live domain is connected. |
| Test keys, then live keys | Stripe Dashboard → Developers → API keys | Put them straight into Render (below). **Never send keys in chat or email.** |
| Statement descriptor | Stripe settings | What buyers see on their bank statement, e.g. `SPEEDYFIX AUTO`. |
| Refund and returns policy | Legal adviser | Needed on the site before taking money. |

**Settings in Render (backend):**

| Variable | What it is |
| --- | --- |
| `STRIPE_SECRET_KEY` | `sk_test_…` while testing, `sk_live_…` at launch |
| `STRIPE_PUBLISHABLE_KEY` | `pk_test_…` / `pk_live_…` (sent to the browser; not secret) |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…`, from the webhook endpoint created in Stripe for `https://<api domain>/api/payments/stripe/webhook` |

**Developer work:**

1. Add the Stripe Java SDK. When checkout starts, create a **PaymentIntent** for the basket total (worked out on the server, never trusted from the browser).
2. Checkout page: show the **Stripe Payment Element** (cards, Apple Pay, Google Pay, PayPal) and confirm the payment.
3. **Webhook** `payment_intent.succeeded` → order becomes *Awaiting dispatch*; `payment_failed` → shopper can retry. Stock is held for 30 minutes and released if payment never completes. Webhook signatures are checked.
4. Order-received page shows "Paid"; the admin's **Mark as paid** stays for phone and bank-transfer orders.
5. **Refunds** from the admin order page through the Stripe API; partial refunds allowed.
6. Show the payment logos in the footer only once payment is live.
7. Test with Stripe's test cards (success, declined, 3-D Secure) and PayPal sandbox, then one real order refunded in live mode.

**Card security:** card and PayPal details only ever go to Stripe, never to our server, so the shop stays on Stripe's simplest compliance level (SAQ A).

### 0.4 Logins

| Where | Email | Password |
| --- | --- | --- |
| Local copy (your computer) | `prabhjeetsinghhh@gmail.com` | the one you set in `$env:ADMIN_PASSWORD`, see `CREDENTIALS.local.md` (local only, never use it live) |
| Live site | `prabhjeetsinghhh@gmail.com` (or `ADMIN_EMAIL` if set) | Whatever you set as `ADMIN_PASSWORD` in Render. **Not set yet**, so live admin sign-in is off. |

---

## 1. What's built

### Shop (customer side)

| Area | Status |
| --- | --- |
| Homepage: hero slider, mega menu, reg-plate finder, carousels, category banners | Done |
| Shop `/products`: filters, sort, pagination, "fits my car" | Done; on the database it lists the 1,262 live eBay listings |
| Product page `/products/:id`: fit check, VAT split, tabs, related parts | Done |
| Basket drawer and basket page, with delivery and VAT worked out by the backend | Done |
| Checkout `/checkout` and order received page | Done, **without payment** |
| Request a part `/request-a-part`, with up to 4 photos | Done |
| Wishlist and Contact pages | Done |
| Shop filters like eBay: Brand, Sub type, Featured refinements | Done |
| My account: sign in with Google, recent search history | Done; works locally (the client ID is set) |
| About, Delivery, FAQ, Terms, Privacy | Placeholder page only (`ComingSoon`). The Returns page was removed at the owner's request. |

### Staff admin: Seller Hub (`/admin`)

The admin is **built** and laid out like eBay Seller Hub (Overview, Orders, Listings, Customers, Part requests, Vehicle lookup, Settings).

| Screen | What it does |
| --- | --- |
| Sign-in | Email and password. The master account is `ADMIN_EMAIL` (default `prabhjeetsinghhh@gmail.com`) and `ADMIN_PASSWORD` (12+ characters). Sign-in returns a JWT (HS256, 12 hours) signed with `ADMIN_JWT_SECRET`, and signing out revokes it. Five failed attempts lock that address out for 15 minutes. |
| Overview | Listings, Orders and Sales cards as on eBay (31-day sales chart; today, 7, 31 and 90-day totals with change), listings to finish, plate searches with no matching stock, lookup spend |
| Customers | Everyone who has signed in with Google, with last sign-in and recent searches |
| Orders | Status tabs; mark paid, dispatch with courier and tracking, cancel (restocks) |
| Listings | Active, Out of stock, Drafts, Ended, Needs attention, All |
| Create / Edit listing | Part number, fitment suggestions (same part number or engine code), brand, sub type, featured refinements, up to 4 photos, checklist, shopper preview, end a listing |
| Part requests | Customers' requests with contact details and photos. Status moves New → Quoted → Closed. |
| Vehicle lookup tester | Which vehicle a plate identifies, plus its confirmed and possible parts |
| Settings | Lookup cost, monthly cap, buying team email |

**Missing from the admin:** accounts for staff other than the master (see Phase 6). Marketing and advertising tools from eBay aren't in scope.

---

## 2. Run it locally

You need Java 21, Node 20 or newer, and Maven 3.9 or newer. Maven isn't on your PATH, but a copy is cached at `~/.m2/wrapper/dists/apache-maven-3.9.14-bin/.../bin/mvn`. Either add it to PATH or open the backend in IntelliJ.

> **Port note:** NVIDIA Broadcast listens on `127.0.0.1:8080`, which is Spring Boot's default port. Either quit Broadcast from the system tray, or run the backend on 8081 as shown below.

**Terminal 1: backend (PowerShell)**

```powershell
cd backend
$env:PORT = '8081'
$env:ADMIN_PASSWORD = '<your local password>'   # any value of 12+ characters
# Optional: $env:ADMIN_EMAIL (default prabhjeetsinghhh@gmail.com) and $env:ADMIN_JWT_SECRET (32+ characters)
mvn spring-boot:run
```

**Terminal 2: frontend (PowerShell)**

```powershell
cd frontend
npm install                                   # first time only
$env:API_PROXY_TARGET = 'http://localhost:8081'
npm run dev
```

If the backend runs on 8080, leave out both `PORT` and `API_PROXY_TARGET`.

| Open this | For |
| --- | --- |
| http://localhost:5173 | The shop |
| http://localhost:5173/admin | Staff admin. Sign in with `prabhjeetsinghhh@gmail.com` and your `ADMIN_PASSWORD`. |

Demo plates: **AB12 CDE** (Nissan Navara D40), **VX16 ABC** (Vauxhall Astra K), **JG17 XEE** (Jaguar XE).

Run the backend tests with `mvn test` from inside `backend/`.

### A quick tour of the admin

1. **Sign in** at `/admin/login` with the master email and password.
2. **Overview**: Listings, Orders and Sales cards, then the listings still needing fitment or photos. Click one to fix it.
3. **Listings → Create listing**: enter the part number (or engine code), then **Find vehicles** for fitment suggestions, or add vehicles by hand. Add brand, sub type and featured refinements.
4. Add a price and at least one photo, then tick through the checklist and press **List it**. You can save a draft without a price or photo.
5. Open the shop in another tab. The part shows up, and a plate lookup marks it as fitting.
6. **Part requests**: submit one from `/request-a-part` in the shop, then move it New → Quoted → Closed here.
7. **Vehicle lookup tester**: check what a plate finds without it counting as a customer search.
8. **Settings**: set the lookup cost, the monthly cap and the buying team email. The dashboard's email link uses that address.

Without the database (`postgres` profile) everything you add is lost on restart. With it, listings are kept; baskets, orders, customers and requests still aren't until the rest of Part A4 is done.

---
## 3. Launch plan, phase by phase

The site goes live at the end of **Phase 8**. Phases 4 (content) and 5 (SEO) can run alongside Phases 1–3, because the client supplies the content while the developer builds.

| Phase | Goal | Who | Rough effort (1 developer) |
| --- | --- | --- | --- |
| 0 | Housekeeping: commit the work, add CI | Dev | 1 day |
| 1 | Database and photo storage, so data survives a deploy | Dev | 1–1.5 weeks |
| 2 | Taking money: payments, orders admin, emails | Dev | 1.5–2 weeks |
| 3 | Real vehicle lookup | Dev + client (contract) | 3–5 days |
| 4 | Content, legal pages, real photos | **Client** + dev | Depends on the client |
| 5 | SEO foundation | Dev | 1.5–2 weeks |
| 6 | Production hosting, security and compliance | Dev + client (accounts) | 1 week |
| 7 | Testing and soft launch | Dev + client | 1 week |
| 8 | Go live | Dev + client | 1 day, then 2 weeks of monitoring |
| 9 | After launch: growth | Dev + client | Ongoing |

The effort figures are estimates for planning, not quotes.

### Target hosting setup

Full research, with prices, free alternatives and sources, is in **[HOSTING.md](HOSTING.md)**. Every outside API (plate lookup, payments, email, address lookup, couriers, reviews) is compared in **[INTEGRATIONS.md](INTEGRATIONS.md)**.

```
                 Customers / Google
                        │
              Cloudflare (DNS, CDN, SSL, WAF)          ← free plan
               │                       │                     │
   www.<domain>                    api.<domain>          images.<domain>
   Cloudflare Pages now;           Render Starter        Cloudflare R2
   Render (Node SSR) after Ph. 5   (Spring Boot, Docker)  (part photos)
                                       │
                                       ├── Render Postgres Basic-256mb (Frankfurt, private network)
                                       ├── Stripe (payments)
                                       ├── Resend or Postmark (email)
                                       ├── UK VRM data provider
                                       └── Sentry + Better Stack (errors, uptime)
```

| Piece | Choice | Monthly cost |
| --- | --- | --- |
| Domain | Already bought; DNS moves to Cloudflare | Renewal only |
| DNS, CDN, SSL | Cloudflare free | $0 |
| Frontend | Cloudflare Pages now (free); Render Starter after SSR | $0 → $7 |
| Backend | Render Starter (512 MB); Standard (2 GB) if memory gets tight | $7 → $25 |
| Database | Render Postgres Basic-256mb + 5 GB disk | about $7.50 |
| Photos | Cloudflare R2 (10 GB free, free egress) | $0 |
| Email | **Resend** (decided) over SMTP: free for 3,000 a month → Pro $20; Postmark $15 if delivery suffers | $0 → $15–20 |
| Monitoring | Sentry free + Better Stack free | $0 |
| Payments | Stripe: no monthly fee, 1.5% + 20p per standard UK card | Per sale |
| Plate lookups | DVSA MOT History API (free) first; paid engine-code data (about 4p–15p each) later | $0 → per lookup |

Expect **about $15–22 a month (about £11–17)** for hosting at the start, and about $70–75 for a busier shop. Card fees and plate lookups are the real variable costs. See HOSTING.md for worked examples.

---

### Phase 0: Housekeeping

| # | Task | Done when |
| --- | --- | --- |
| 0.1 | ~~Commit the admin work~~ Done (feature branches); the database and import work still to commit | `git status` is clean |
| 0.2 | Work on feature branches from now on, merging to `main` through pull requests | Branch protection is on for `main` |
| 0.3 | GitHub Actions: run `mvn test` and `npm run build` on every pull request | A failing test blocks the merge |
| 0.4 | Add a `.env.example` listing every environment variable the app reads | New developers can set up from it |

### Phase 1: Database and photo storage

**Goal:** nothing is lost on a restart or redeploy.

| # | Task | Done when |
| --- | --- | --- |
| 1.1 | ~~Add the PostgreSQL driver and Flyway~~ **Done** (JDBC + Flyway, `postgres` profile) | The app starts against Postgres and creates the tables itself |
| 1.2 | Tables for the catalogue: **done**, and the shop reads listings from them. Admin photo uploads still go to memory. | Admin product edits survive a restart |
| 1.3 | Tables: `baskets`, `basket_items`, `orders`, `order_items`. Stock is taken in one transaction with a row lock, so two buyers can't get the last part. | Orders survive a restart, and the stock test passes |
| 1.4 | Tables: `part_requests`, `part_request_photos` | Requests survive a restart |
| 1.5 | Move product and request photos to Cloudflare R2 (request photos in a private bucket, served through the admin only) | Photos survive a redeploy; the 64 MB memory cap is removed |
| 1.6 | ~~Seed data~~ Replaced by the real eBay import (1,380 listings) | A fresh local database has real data |
| 1.7 | ~~Local Postgres in Docker~~ **Done** (`compose.yaml`, DATABASE.md) | Instructions are in DATABASE.md |
| 1.8 | ~~Tests against Postgres through Testcontainers~~ **Done** (migrations and import) | `mvn test` passes |
| 1.9 | Create Render Postgres Basic-256mb in Frankfurt for production (optionally a free Neon project for a cloud dev database) | The backend uses the internal `SPRING_DATASOURCE_*` connection |

### Phase 2: Taking money

**Goal:** a customer can pay, staff can fulfil the order, and everyone gets the right emails.

| # | Task | Done when |
| --- | --- | --- |
| 2.1 | **Stripe** Payment Element (cards, Apple Pay, Google Pay) **and PayPal** (decided by the owner) on the checkout page | Test-mode payments complete with both |
| 2.2 | Stripe webhook (`payment_intent.succeeded` or `payment_failed`). An order only becomes **Paid**, and stock is only taken, when the webhook confirms it. Unpaid orders release their stock after 30 minutes. | A declined card leaves stock untouched |
| 2.3 | Payment failure, retry and declined states in the checkout | Each state has a clear message |
| 2.4 | ~~Admin Orders screen~~ **Done**: status tabs, order page, mark paid, dispatch with courier and tracking, cancel. Payment webhooks will mark orders paid automatically. | Staff can fulfil an order without touching the database |
| 2.5 | Refunds from the admin, through the Stripe API | A refund appears in Stripe and on the order |
| 2.6 | Invoice or VAT receipt as a PDF for each order, with the company name, address and VAT number | The customer can download it |
| 2.7 | **Transactional email** (Postmark or Resend): order confirmation, dispatch with tracking, refund, part-request acknowledgement, and staff alerts for new orders and requests | All emails arrive and pass SPF, DKIM and DMARC |
| 2.8 | Stock adjustment and a "sold" state for one-off used parts | A sold part can't be bought twice |
| 2.9 | Show the payment logos in the footer only once payment is live | They match what is accepted |

### Phase 3: Real vehicle lookup

| # | Task | Done when |
| --- | --- | --- |
| 3.1 | **Client:** choose and contract a UK VRM data provider (for example UK Vehicle Data, CarWeb or One Auto API). It must return the engine code, not just make and model. | API key received |
| 3.2 | Implement `VehicleLookup` against the provider. Keep `DemoVehicleLookup` for local development. | Real plates resolve |
| 3.3 | Cache results in the database for 30 days, so repeat searches for a plate aren't charged twice | The lookup log shows cache hits |
| 3.4 | Enforce the monthly spend cap from Settings, rate-limit lookups per visitor, and fall back to "choose your vehicle manually" when the cap is reached | Spend can't run away |
| 3.5 | Manual make → model → engine selector for customers who don't want to enter a plate | It works without a plate |

### Phase 4: Content, legal pages and real photos (mostly client)

| # | Item | Who | Notes |
| --- | --- | --- | --- |
| 4.1 | Business details: phone, WhatsApp, email, registered company name, **company number**, registered office, **VAT number**, trading address | Client | The Companies Act requires the company name, number and registered office on the website. Goes in `application.yml` `site.*`. |
| 4.2 | Warranty period, returns window and dispatch location | Client | Shown on every product page |
| 4.3 | Courier names, cut-off times, pallet and parcel prices (currently £79 pallet; £9.95 parcel, free from £100) | Client | Delivery page and checkout |
| 4.4 | **Terms and conditions**, **privacy policy**, **returns and warranty policy**, cookie policy | Client's legal adviser | Required under the Consumer Contracts Regulations (14-day cancellation), the Consumer Rights Act and UK GDPR |
| 4.5 | About us copy and workshop photos, FAQ answers, workshop video (YouTube) | Client | |
| 4.6 | Real photos of each part, shot the same way each time (same background, angle and lighting) | Client | Uploaded through the admin |
| 4.7 | Product descriptions, condition notes and mileage | Client | Entered in the admin. Unique text per part also helps SEO. |
| 4.8 | Content-page template (title, side navigation between policy pages, readable text column), then build each page | Dev | Replaces `ComingSoon` |
| 4.9 | Dedicated 404 page, with search and the reg finder | Dev | |
| 4.10 | Review platform (Trustpilot, Reviews.io or Google reviews) and connect its feed | Client chooses, dev connects | Replaces the 3 placeholder reviews |

### Phase 5: SEO foundation

**Why this needs real work:** the site is a client-side React app hosted on GitHub Pages. Two problems follow.

1. **Every deep link returns HTTP 404.** GitHub Pages serves product pages through the `404.html` fallback, so Google sees a "not found" status on every product and category page, even though the page then appears in the browser. Those pages won't be indexed reliably.
2. **The HTML Google, Bing and social apps receive is empty** until JavaScript runs. Google renders JavaScript eventually, but more slowly and less reliably. Facebook, WhatsApp and most other crawlers don't render it at all, so shared links show no title or image.

**Fix:** move the frontend to **server-side rendering (SSR)**. The smallest change is upgrading React Router from v6 to v7 and switching on its framework mode. The routes and components stay as they are, each route gains a `loader` (fetches data on the server) and a `meta` export, and the frontend runs as a small Node service on Render behind Cloudflare. Each page then returns status 200 with full HTML, its own title and its own description.

#### 5A. Technical SEO

| # | Task | Done when |
| --- | --- | --- |
| 5.1 | SSR with React Router v7 framework mode, deployed to Render; retire GitHub Pages | `curl` on a product URL returns 200 with the product name in the HTML |
| 5.2 | Real 404 and 410 status codes for missing pages; 301 redirects for changed URLs | Search Console shows no soft 404s |
| 5.3 | **SEO-friendly URLs**: `/parts/{category}` for categories (instead of `?category=`), and `/parts/{category}/{descriptive-slug}` for products, for example `/parts/engines/nissan-navara-d40-2-5-dci-yd25ddti-engine` | Old URLs 301 to the new ones |
| 5.4 | `<title>` and meta description per page. Products: `{Part} for {Make Model} ({Engine code}) – {Condition} | SpeedyFix Auto Sales`. Categories: `{Category} for UK cars | SpeedyFix Auto Sales`. | Every indexable page has a unique title and description |
| 5.5 | Canonical tags. Filter, sort and page parameters point to the clean URL. Search results (`?q=`) are `noindex, follow`. | No duplicate pages in Search Console |
| 5.6 | `noindex` on `/basket`, `/checkout`, `/order-received/*`, `/wishlist` and `/admin/*` (the admin already has it) | Checked with a crawler |
| 5.7 | `robots.txt`: allow the shop, disallow `/admin`, `/api`, `/basket`, `/checkout`, and link the sitemap | It's live at `/robots.txt` |
| 5.8 | **`sitemap.xml` generated from the database**: home, categories, every live product, vehicle and engine-code pages (5.13), and content pages, each with `lastmod` | Submitted in Search Console and Bing Webmaster Tools |
| 5.9 | **Sold and out-of-stock used parts**: keep the page live marked "Sold" with similar parts for 90 days, then 301 to the category. Don't let sold parts turn into 404s. | Sold parts keep their search traffic |
| 5.10 | **Structured data (JSON-LD)**: `Product` and `Offer` (GBP price, availability, `itemCondition` Used, Refurbished or New, `shippingDetails`, `hasMerchantReturnPolicy`), `BreadcrumbList`, `Organization` / `AutoPartsStore` with address and phone, and `WebSite` with `SearchAction`. Add `AggregateRating` only once real reviews exist. | Google's Rich Results Test passes for each template |
| 5.11 | Open Graph and Twitter Card tags with the product photo; a default share image for other pages | WhatsApp and Facebook links show a preview |
| 5.12 | Image SEO: alt text from the part name and vehicle; WebP or AVIF, resized through Cloudflare; `width` and `height` set; lazy-load below the fold | No layout shift from images |

#### 5B. Pages that bring search traffic

Car-part searches are mostly "{part} {make model}", "{engine code} engine" or an OE number. Build pages that match those searches:

| # | Task | Done when |
| --- | --- | --- |
| 5.13 | **Vehicle landing pages**, `/vehicles/{make}/{model}`, for example `/vehicles/nissan/navara-d40`: the vehicle, its engines and every part that fits, built automatically from fitment data | One page per vehicle with stock, in the sitemap |
| 5.14 | **Engine-code pages**, `/engine-codes/{code}`, for example `/engine-codes/yd25ddti`: which vehicles use the engine and the parts in stock | One page per engine code |
| 5.15 | OE and part numbers in the product page's visible text, plus OE search at `/search?q=` | Searching an OE number on Google can find the page |
| 5.16 | Category pages with 150–300 words of helpful copy (what to check, fitment advice) above or below the grid | Each category has unique text |
| 5.17 | Internal links: breadcrumbs, "fits these vehicles" links on products, related parts, links from vehicles to parts | No orphan pages in a crawl |

#### 5C. Performance (Core Web Vitals)

| # | Task | Done when |
| --- | --- | --- |
| 5.18 | Largest Contentful Paint under 2.5 s on mobile: preload the hero image, self-host the fonts (Chakra Petch, Sora, JetBrains Mono) with `font-display: swap`, serve critical CSS with SSR | PageSpeed Insights mobile score of 90 or more |
| 5.19 | Cloudflare caching for static assets and images, with long cache lifetimes and hashed filenames | Repeat visits load from cache |
| 5.20 | Keep the admin bundle split out of shopper pages (already the case) | Shopper JavaScript bundle under about 200 KB gzipped |

#### 5D. Google and Bing accounts (at go-live, Phase 8)

| # | Task |
| --- | --- |
| 5.21 | **Google Search Console** and **Bing Webmaster Tools**: verify the domain through DNS and submit the sitemap |
| 5.22 | **Google Business Profile** for the workshop or warehouse address. Keep the name, address and phone identical everywhere. |
| 5.23 | **Google Merchant Center**: a product feed from the backend (`/feeds/google.xml`) for free listings in Google Shopping, and paid Shopping ads later if wanted |
| 5.24 | GA4 with Consent Mode v2, set up through the cookie banner (Phase 6) |

### Phase 6: Production hosting, security and compliance

| # | Task | Done when |
| --- | --- | --- |
| 6.1 | Buy the domain and move DNS to Cloudflare | DNS resolves |
| 6.2 | Render: frontend (Node SSR) and backend (Docker) on paid plans, custom domains `www.<domain>` and `api.<domain>`; remove the `KeepAlive` self-ping | No cold starts |
| 6.3 | Separate environments: **staging** (its own small database, Stripe test mode) and **production** | Changes are checked on staging first |
| 6.4 | Secrets only in Render environment variables: `ADMIN_PASSWORD`, `ADMIN_JWT_SECRET`, `SPRING_DATASOURCE_*`, `R2_*`, `STRIPE_*`, `MAIL_*`, `VRM_API_KEY`, `SENTRY_DSN` | Nothing secret in git |
| 6.5 | CORS limited to the production domain; HTTPS only, with HSTS; security headers (CSP, `X-Frame-Options`, `Referrer-Policy`) | securityheaders.com grade A |
| 6.6 | **Staff accounts**: a `staff_users` table, a Staff page where the master adds and removes staff, BCrypt-hashed passwords, roles (Master, Staff), password reset by email, optional 2FA, and an audit log of who changed what | More than one person can work safely |
| 6.7 | Rate limits on public forms (part request, checkout, plate lookup) and Cloudflare Turnstile on forms | Spam bots are blocked |
| 6.8 | **Backups**: Render Postgres backups, plus a nightly `pg_dump` to R2 kept for 30 days; test a restore once | A restore has been tested |
| 6.9 | **Monitoring**: Sentry on the frontend and backend, uptime checks on `/` and `/api/site`, and alerts to email or WhatsApp | Alerts reach a person |
| 6.10 | **UK GDPR**: pay the ICO data protection fee (£52 a year, Tier 1), set retention periods (for example, delete part requests after 12 months), handle data export and deletion requests, keep a list of processors (Stripe, Render, Cloudflare, the email provider and so on) | Documented in the privacy policy |
| 6.11 | **Cookie banner** (PECR) before any analytics or marketing cookies, wired into GA4 Consent Mode | Nothing non-essential runs before consent |
| 6.12 | Payments stay PCI-light: card details only ever go to Stripe Elements, never to our server | Stripe self-assessment form SAQ A |

### Phase 7: Testing and soft launch

| # | Task | Done when |
| --- | --- | --- |
| 7.1 | End-to-end tests (Playwright): plate lookup → add to basket → checkout → Stripe test payment → order email → admin dispatch | Runs in CI |
| 7.2 | Cross-device check: iPhone Safari, Android Chrome, desktop Chrome, Edge and Firefox; mobile at 390 px | No blocking issues |
| 7.3 | Accessibility check (WCAG 2.2 AA): keyboard, screen reader, contrast | axe shows no serious issues |
| 7.4 | SEO pre-flight: crawl staging with Screaming Frog (free up to 500 URLs); check titles, canonicals, status codes, structured data and the sitemap. **Staging must be `noindex`.** | No errors |
| 7.5 | Load the real catalogue: at least the first 50–100 parts, with photos, fitment and descriptions | The shop looks full |
| 7.6 | **Soft launch**: Stripe live mode, staff and friends place real orders with real cards, then refund them | Money arrives in the bank and refunds work |

### Phase 8: Go live

1. Point `www.<domain>` and the bare domain at production. The bare domain 301s to `www`.
2. If an old SpeedyFix website (for example the former Engine Solutions site) exists, **301-redirect every old URL** to its new equivalent so its search rankings carry over.
3. Remove `noindex` from production, check `robots.txt`, and submit the sitemap in Search Console and Bing.
4. Turn on Merchant Center free listings and publish the Google Business Profile.
5. Watch Sentry, uptime, Stripe and Search Console daily for 2 weeks.

### Phase 9: After launch

- **Customer accounts**: sign-in, order history, saved vehicles and addresses, reorder.
- **Content marketing for SEO**: guides such as "YD25 engine common faults" or "how to check a used turbo", linked to the matching parts. One or two a month.
- **Reviews**: ask for a review automatically after delivery, and show `AggregateRating` once reviews are real.
- **Marketplaces** (optional): list stock on eBay Motors from the same database.
- Abandoned-basket emails (with consent), and more Google Shopping campaigns.
- Monthly SEO review in Search Console: queries, click-through rate, pages not indexed.

---

## 4. Decisions needed from the client

| Decision | Recommendation | Needed by |
| --- | --- | --- |
| Domain name | A `.co.uk`, plus the `.com` if available | Phase 6 (buy early) |
| Payment provider | **Decided: Stripe and PayPal** | Phase 2 |
| Vehicle data provider and monthly budget | One that returns engine codes; compare 2–3 quotes | Phase 3 |
| Email sender address | `orders@<domain>` | Phase 2 |
| Couriers and delivery prices (is £79 pallet / £9.95 parcel / free over £100 right?) | | Phase 4 |
| Warranty period and returns window | | Phase 4 |
| Review platform | Trustpilot or Reviews.io | Phase 4 |
| How many staff need admin logins, and their roles | | Phase 6 |
| Does an old website exist whose URLs need redirecting? | | Phase 8 |

## 5. Other placeholders still in the code

| Placeholder | Where |
| --- | --- |
| `[UK NUMBER]`, `[WhatsApp number]`, `[email address]`, `[Registered company name]`, `[UK trading address]`, `[Warranty period]`, `[UK warehouse]` | `backend/src/main/resources/application.yml` `site.*` |
| `[Courier names]` | `CatalogRepository.java:92` |
| `[Returns policy from the client]` | `ProductPage.tsx:280` |
| Workshop video (empty) | `site.video-url` |
| About, Returns, Delivery, FAQ, Terms and Privacy copy | `ComingSoon.tsx` |
| 3 placeholder reviews | `Reviews.tsx` |
| Unsplash sample photos | `Images.java`, replaced through the admin |

See `DESIGN-TODO.md` for the design-side list.
