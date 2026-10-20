# SpeedyFix Engine Solutions — v2 storefront (React + Java)

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

About, returns, terms, privacy, delivery and FAQ pages show bracketed placeholders until the client supplies the copy. Account and trade pages are the next build phase.

## Run it locally

Prerequisites: Java 21, Maven 3.9+, Node 20+.

```bash
# 1. Backend on http://localhost:8080
cd backend
mvn spring-boot:run

# 2. Frontend on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

Demo plates: **AB12 CDE** (Nissan Navara D40), **VX16 ABC** (Vauxhall Astra K), **JG17 XEE** (Jaguar XE).

Run the backend tests with `mvn test`.

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
| GET | `/api/shop?category=&q=&condition=USED,NEW&minPrice=&maxPrice=&fitsOnly=&vehicleId=&sort=&page=&pageSize=&ids=` | Shop page: filtered, sorted, paged results and category counts. `sort` is `best-selling`, `newest`, `price-low` or `price-high` |
| POST | `/api/orders` `{ basketId, name, email, phone, line1, line2, town, postcode, notes }` | Place an order (UK postcode checked; stock taken all-or-nothing) |
| GET | `/api/orders/{reference}` | Order received page |
| POST | `/api/part-requests` (multipart) | Request a part. The plate is stored masked (`AB12 ***`) with the vehicle it identified |

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
| Accounts, trade accounts | Not built | Next phase |

Placeholders in square brackets (e.g. `[UK NUMBER]`, `[Courier names]`) need the client's real details.

## How this was checked

- Frontend: `tsc` type-check and `vite build` pass; the homepage was tested end to end in a headless browser (plate lookup, fit badges, add to basket with stock limit, basket drawer totals, search, mega menu, reload persistence, mobile at 390px).
- Backend: every source file was compiled with `javac` against the Spring and Jakarta APIs it uses, and the real services were run behind a test server to drive the frontend tests. The full Maven build (`mvn spring-boot:run`, `mvn test`) needs to be run on your machine, because the build sandbox could not download Maven dependencies.
