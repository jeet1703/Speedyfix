# SpeedyFix Auto Sales — v2 storefront (React + Java)

The v2 "catalogue layout" storefront from the Figma design, built as:

- **frontend/**: React 18 + TypeScript + Vite
- **backend/**: Java 21 + Spring Boot 3.3 REST API

## What works

- Top bar, header with **Products mega menu**, search and mobile menu
- **Hero slider** (4 category slides, arrows, dots, autoplay that pauses on hover/focus and respects reduced motion)
- **Reg plate finder**: looks up the plate, saves the vehicle, and every product card shows whether the part fits
- Trust strip, workshop video section, popular category banners
- **Best selling** and **Latest products** carousels with wishlist and **Add to basket**
- **Basket drawer**: change quantities, remove lines, delivery and VAT worked out by the backend
- Live **search** by part name, OE number (with or without spaces/dashes) or engine code
- Reviews section (placeholders until a review platform is connected) and footer
- Vehicle, basket and wishlist are remembered in the browser
- **Shop** (`/products`): category sidebar with counts, condition and price filters, "only parts that fit my car", sort, pagination, search results (`?q=`)
- **Product page** (`/products/:id`): price inc. and ex. VAT, stock, fit check by reg, quantity, description / fitment / delivery tabs, related products
- **Basket page** (`/basket`), **checkout** (`/checkout`) with field validation, and **order received** (`/order-received/:reference`). Placing an order takes the parts out of stock and empties the basket. **No payment is taken** until a payment provider is chosen.
- **Request a part** (`/request-a-part`): reg, part, notes, up to 4 photos, contact details and reply preference
- **Wishlist** and **Contact** pages
- **Shop filters like eBay's**: Brand, Sub type and Featured refinements, each with counts that respect the other filters
- **My account** (`/account`): sign in with Google (needs `GOOGLE_CLIENT_ID`); signed-in shoppers keep their 10 most recent searches, guests keep them in the browser. Recent searches show in the search panel.

About, terms, privacy, delivery and FAQ pages show bracketed placeholders until the client supplies the copy.

## Staff admin: Seller Hub (`/admin`)

Signed-in staff manage the catalogue. Staff sign in with an email and password: the master account is `ADMIN_EMAIL` (default `prabhjeetsinghhh@gmail.com`) and `ADMIN_PASSWORD` (12+ characters); without a password, sign-in stays switched off. Sign-in returns a JWT (HS256) signed with `ADMIN_JWT_SECRET` (32+ characters; without it a random key is used and a restart signs everyone out). Signing out revokes the token. Sessions last 12 hours or until the tab closes, and five wrong passwords from one address lock it out for 15 minutes. The admin is a separate bundle, loaded only when someone opens `/admin`, and is marked `noindex`.

Laid out like eBay Seller Hub, with tabs across the top.

- **Overview**: like Seller Hub's: a Listings card (active, drafts, out of stock, needing fitment or photos), an Orders card (orders by status, new part requests, customers signed in) and a Sales card with a 31-day daily sales chart and today / 7 / 31 / 90-day totals with change on the period before; then listings to finish, shopper plate searches with no matching stock (vehicle only, never the plate), plate lookups against the spend cap, and an email of unmatched vehicles to the buying team
- **Orders**: every order with status tabs; an order page to mark it paid, dispatch it with courier and tracking number, or cancel it (stock goes back)
- **Listings**: Active, Out of stock, Drafts, Needs attention and All tabs, searchable
- **Customers**: every shopper who has signed in with Google: joined, last signed in, sign-in count and recent searches
- **Create listing / Edit listing**: part number, fitment suggested from other parts with the same part number (high confidence) or the same engine code (medium), part details including brand, sub type and up to 5 featured refinements, up to 4 photos (JPG, PNG or WebP, checked by file signature), a checklist and a shopper preview. Drafts can be saved without a price or photo; listing needs a part number or engine code, a price and a photo. Live listings can be ended.
- **Part requests**: shoppers' requests with contact details and photos, moved from New to Quoted to Closed
- **Vehicle lookup tester**: which vehicle a plate identifies, the parts confirmed for it, and unconfirmed parts with the same engine code. Staff checks aren't counted as shopper searches.
- **Settings**: cost per plate lookup, monthly cap and buying team email (starting values from `ADMIN_LOOKUP_COST`, `ADMIN_LOOKUP_BUDGET`, `ADMIN_BUYING_TEAM_EMAIL`)

Like the rest of the prototype, parts, photos, requests and settings are held in memory, so a redeploy resets them. Staff photo uploads share a 64 MB budget.

## Run it locally

Prerequisites: Java 21, Maven 3.9+, Node 20+.

```bash
# 1. Backend on http://localhost:8080 (ADMIN_PASSWORD switches on the staff admin; sign in with ADMIN_EMAIL)
cd backend
ADMIN_PASSWORD='choose-a-long-password' mvn spring-boot:run

# 2. Frontend on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

On Windows PowerShell, set the password first with `$env:ADMIN_PASSWORD='choose-a-long-password'`. The admin is at http://localhost:5173/admin; sign in with the master email and that password.

Demo plates: **AB12 CDE** (Nissan Navara D40), **VX16 ABC** (Vauxhall Astra K), **JG17 XEE** (Jaguar XE).

Run the backend tests with `mvn test`.

## GitHub Pages

The frontend is deployed to GitHub Pages by the workflow in `.github/workflows/deploy-pages.yml` whenever a commit is pushed to `main`. In the repository settings, set **Pages → Build and deployment → Source** to **GitHub Actions**. The site URL is `https://jeet1703.github.io/Speedyfix/`.

GitHub Pages only serves static files. The Java backend runs separately on Render (see below), and the Pages build points at it through `VITE_API_URL` (a repository Actions variable, falling back to `https://speedyfix-backend.onrender.com`).

## Render (backend)

`render.yaml` is a Render Blueprint for the backend: a Docker web service built from `backend/Dockerfile`, health-checked on `/api/site`, redeployed only when `backend/` changes. Set `ADMIN_PASSWORD` (and `ADMIN_EMAIL` if the master email changes) in the Render dashboard to switch on the admin; the Blueprint generates `ADMIN_JWT_SECRET`. On Render's free plan the service sleeps after 15 minutes without traffic; `KeepAlive` pings the service's own `RENDER_EXTERNAL_URL` every 14 minutes to prevent that (it does nothing when that variable isn't set, e.g. locally).

Everything (baskets, orders, part requests) is held in memory, so a restart or redeploy clears it. Idle baskets are dropped after 7 days, and stored part-request photos are capped at 64 MB, so the 512 MB free instance can't run out of memory.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/home?vehicleId=` | Everything the homepage needs: site details, menu, slides, features, categories, best sellers, latest, reviews, saved vehicle |
| GET | `/api/products?section=best-selling\|latest` or `?category=` | Product lists, with fit for `vehicleId` |
| GET | `/api/products/{id}` | Product page: the part, ex-VAT price, description, confirmed vehicles, related parts |
| GET | `/api/search?q=&limit=` | Search by name, OE number or engine code |
| POST | `/api/vehicles/lookup` `{ "registration": "AB12 CDE" }` | Vehicle plus how many parts fit |
| GET | `/api/vehicles` | All demo vehicles |
| POST | `/api/baskets` | New basket |
| GET | `/api/baskets/{id}` | Basket with totals |
| POST | `/api/baskets/{id}/items` `{ "productId", "quantity" }` | Add a part (stock-checked) |
| PATCH | `/api/baskets/{id}/items/{productId}` `{ "quantity" }` | Change quantity (0 removes) |
| DELETE | `/api/baskets/{id}/items/{productId}` | Remove a part |
| GET | `/api/site` | Business details and Products menu (header and footer on every page) |
| GET | `/api/shop?category=&q=&condition=USED,NEW&minPrice=&maxPrice=&fitsOnly=&vehicleId=&brand=&subType=&refinement=&sort=&page=&pageSize=&ids=` | Shop page: filtered, sorted, paged results, category counts, and Brand / Sub type / Featured refinement values with counts. `brand`, `subType` and `refinement` repeat for several values. `sort` is `best-selling`, `newest`, `price-low` or `price-high` |
| POST | `/api/orders` `{ basketId, name, email, phone, line1, line2, town, postcode, notes }` | Place an order (UK postcode checked; stock taken all-or-nothing) |
| GET | `/api/orders/{reference}` | Order received page |
| POST | `/api/part-requests` (multipart) | Request a part. The plate is stored masked (`AB12 ***`) with the vehicle it identified |
| GET | `/api/product-photos/{id}` | A photo staff uploaded for a part |
| POST | `/api/account/google` `{ "credential" }` | Sign in with a Google ID token; returns a shopper token |
| GET / DELETE | `/api/account`, `/api/account/session` | The signed-in shopper; sign out |
| GET / POST / DELETE | `/api/account/searches` `{ "query" }` | Recent searches (10, newest first); add one; clear them |

Admin routes need `Authorization: Bearer <JWT>` from `POST /api/admin/session`, except that sign-in call itself:

| Method | Path | Purpose |
| --- | --- | --- |
| POST / GET / DELETE | `/api/admin/session` | Sign in `{ "email", "password" }` (returns a JWT), check the session, sign out (revokes the JWT) |
| GET | `/api/admin/dashboard` | Overview: tasks, sales, listing counts, work queue and unmatched vehicle searches |
| GET | `/api/admin/products`, `/api/admin/products/{id}`, `/api/admin/categories` | Every part with its work status; one part for editing; categories |
| POST | `/api/admin/products`, `/api/admin/products/{id}` (multipart) | Create or update a part: `part` is the form as JSON, plus up to 4 `photos` |
| GET | `/api/admin/fitment?partNumber=&engineCode=&productId=` | Suggested vehicles with source and confidence |
| GET | `/api/admin/customers` | Shoppers who have signed in with Google, with their recent searches |
| GET / PATCH | `/api/admin/orders`, `/api/admin/orders/{ref}` `{ "status", "courier", "trackingNumber" }` | Orders; mark paid (`AWAITING_DISPATCH`), `DISPATCHED` or `CANCELLED` |
| POST | `/api/admin/plate-check` `{ "registration" }` | Vehicle, confirmed parts and same-engine parts for a plate |
| GET / PATCH | `/api/admin/part-requests`, `/api/admin/part-requests/{ref}` `{ "status" }` | Part requests; set New, Quoted or Closed |
| GET | `/api/admin/part-requests/{ref}/photos/{index}` | A shopper's photo |
| GET / PUT | `/api/admin/settings` | Lookup cost, monthly cap, buying team email |

Errors are RFC 7807 problem details; `detail` is written for shoppers and shown as-is in the UI.

Delivery rules: any pallet item (engines, gearboxes) costs £79; otherwise tracked parcel delivery is £9.95, free from £100. Prices include VAT; `vatIncluded` is total ÷ 6.

## Configuration

- `backend/src/main/resources/application.yml` → `site.*`: company name, phone, WhatsApp, email, address, warranty wording, dispatch location, the workshop **video URL** (YouTube embed) and allowed CORS origins.
- `frontend/.env` → `VITE_API_URL`: leave empty in development; set to the API's public URL in production.

## Prototype limits, and what to replace before launch

| Area | Now | For production |
| --- | --- | --- |
| Catalogue | In-memory sample data (`CatalogRepository`) | Spring Data JPA + PostgreSQL, or the client's product feed |
| Vehicle lookup | `DemoVehicleLookup` with 3 sample plates | Implement `VehicleLookup` with a licensed UK VRM data provider |
| Baskets | In memory, lost on restart | Database or Redis, with expiry |
| Photos | Unsplash stand-ins (Unsplash License) | The client's own photos of each part |
| Reviews | Empty list, placeholders shown | Feed from the chosen review platform |
| Orders and part requests | In memory, lost on restart | Database with access control and a retention policy (they hold personal data); email part requests to the sales team |
| Payments | Checkout places the order without taking payment | Connect the chosen provider |
| Product descriptions | Generated from condition and grade | The client's copy for each part |

Placeholders in square brackets (e.g. `[UK NUMBER]`, `[Courier names]`) need the client's real details.

## How this was checked

- Frontend: `tsc` type-check and `vite build` pass; the homepage was tested end to end in a headless browser (plate lookup, fit badges, add to basket with stock limit, basket drawer totals, search, mega menu, reload persistence, mobile at 390px).
- Backend: every source file was compiled with `javac` against the Spring and Jakarta APIs it uses, and the real services were run behind a test server to drive the frontend tests. The full Maven build (`mvn spring-boot:run`, `mvn test`) needs to be run on your machine, because the build sandbox could not download Maven dependencies.
