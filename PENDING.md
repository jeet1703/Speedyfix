# SpeedyFix Auto Sales: what's left before and after launch

Last updated 3 November 2026. This replaces the "left to do" parts of [STATUS.md](STATUS.md), which describes the project as it was on 24 October.

**Where things stand.** Everything in the "Done since the last push" list at the end is built and tested on the development computer (111 backend tests pass, the frontend type-checks). The live site still runs the version from the last push until the next "commit and push". Checked against the code on 3 November 2026. Whole-site sanity test on 4 November: see [TEST-LOG.md](TEST-LOG.md).

Priorities: 🔴 needed before taking real orders · 🟠 should be done before launch · 🟢 after launch.

---

## 1. Emails 🔴

**Built (3 November): VAT invoices and order emails.**
- Every paid order (PayPal, or marked paid by staff) gets a **VAT invoice** numbered in sequence from INV-001001, dated when it was paid. It's a PDF in the look of the shop's sales invoice: prices before VAT, then delivery, VAT at 20%, the total paid and £0.00 balance due.
- The buyer can **view or download it** on the order page right after paying, and from My orders. Staff see it on the admin order page, with "Email it again".
- With `RESEND_API_KEY` set, the buyer is **emailed the invoice** (PDF attached), and `MAIL_STAFF_ORDERS` gets a copy with a link to the order. The emails wait in a queue (`email_outbox`) and are retried for about 8 hours if the mail service is down, so payment never waits on email and nothing is sent twice. The admin order page shows each email's status.
- **Resend:** the API key (sending only) arrived on 3 November and is in `render.env`. **The domain isn't verified yet:** speedyfixautosales.com's DNS is hosted by **Wix** (ns4/ns5.wixdns.net), and on 3 November none of Resend's records (`resend._domainkey` TXT, `send` MX and TXT, `_dmarc` TXT) were there. Until they're added in Wix (Domains → Manage DNS records) and Resend shows the domain as Verified, Resend refuses to send from @speedyfixautosales.com. Orders still go through; the emails wait and are retried, then show as "not sent" on the admin order page. The client guide: "Setting up order emails with Resend" (Claude doc).
- **On Render, once verified:** `RESEND_API_KEY`, and `MAIL_STAFF_ORDERS` (the inbox for order copies). `MAIL_FROM` is set in `render.yaml`; sign-up codes come from `MAIL_ACCOUNT_FROM` (default `account@speedyfixautosales.com`). Any address on the verified domain works, with no mailbox behind it; replies go to `MAIL_REPLY_TO` (eBay.speedyfix@gmail.com).
- **VAT: decided 4 November.** Standard rate: VAT is 1/6 (16.67%) of each price paid, which is 20% of the price before VAT. The invoice says "VAT 20% (16.67% of price)". *Earlier note, for reference:* standard 20% VAT or the second-hand margin scheme (global accounting is usual for breakers; the eBay listings show the VAT number but no VAT rate). With the margin scheme, the invoice must not show VAT and must say "global accounting invoice", and the basket, checkout, order page and emails must stop saying "includes VAT of £…". Not built yet. A refund after payment also needs a **credit note**, not built.
- Bank details from the paper invoice are left off on purpose: online orders are already paid, and they'd otherwise sit in the public code.

**Not needed (decided 4 November):** dispatched and part-request emails.

### Which emails exist

| Email | To | When | Status |
| --- | --- | --- | --- |
| Invoice and order confirmation | Customer | Order paid | **Built** (`OrderEmails`) |
| New order, invoice attached | Staff (`MAIL_STAFF_ORDERS`) | Order paid | **Built** |
| Sign-up code / "you already have an account" | Customer | Email sign-up | **Built** (`EmailVerificationMailer`); email sign-in is still switched off |
| Order dispatched (courier, tracking) | Customer | Staff mark it dispatched | Not built |
| Order cancelled | Customer | Unpaid order expires, or staff cancel | **Built**: says no money was taken, or, if it was paid, that it will be refunded (staff still refund in PayPal) |
| New part request | Staff | Request received | Not built (requests only show in the admin) |
| Part request received | Customer | Request received | Not built |
| Payment needs checking | Staff (`MAIL_STAFF_ORDERS`) | PayPal capture doesn't match the order | **Built**: once per order, with what PayPal reported and the PayPal order id |

The order page still says "check its status in the help chat"; once dispatch emails exist it can say they'll be emailed.

---

## 1a. FedEx delivery prices 🔴

**Built (2 November):**
- **Checkout.** Once a full postcode is typed, checkout asks FedEx's Rate API for prices and lists each service with its price and arrival date. The shopper picks one, and the order re-prices that choice on the server.
- **Pricing.** FedEx's charge before VAT, plus `FEDEX_MARKUP_PERCENT` and `FEDEX_HANDLING_FEE` (both 0 by default), plus 20% VAT. The cheapest service is free on orders of £100 or more.
- **What doesn't use FedEx.** Pallets keep the flat £79. If FedEx isn't set up or doesn't answer, the flat prices apply, so checkout never stops.
- **Box sizes.** Each category has a typical packed box (`ParcelSizes.java`). Staff can set a part's own packed weight on its listing.

**Test (sandbox) keys** are in `render.env` and `CREDENTIALS.local.md`, and work.

**Decided 4 November:** FedEx prices at checkout, alongside the "Manual delivery" option (pay for the parts, get a delivery quote from another courier afterwards).

**Was to decide (the client):**
1. **The prices: real figures (production keys, 3 November).** The sandbox's £40 "intracountry" fee and £25 home-delivery charge were test data only: the shop's real account pays neither, and home and business addresses cost the same. From UB8 2JP to Manchester, before VAT, then what the shopper pays with VAT (no markup):

   | Parcel | Billed as | Economy | Priority | Priority Express | First |
   | --- | --- | --- | --- | --- | --- |
   | Sensor, glow plug, coil (1 to 1.5 kg, 30×20×15) | 1.8 kg | £24.49 → **£29.39** | £29.25 → £35.10 | £41.45 → £49.74 | £64.88 → £77.86 |
   | Starter, alternator (7 kg, 40×30×30) | 7.2 kg | £24.49 → **£29.39** | £29.25 → £35.10 | £41.45 → £49.74 | £64.88 → £77.86 |
   | Turbo (12 kg, 50×40×40) | 15.9 kg | £30.07 → **£36.08** | £36.95 → £44.34 | £50.32 → £60.38 | £75.15 → £90.18 |
   | Door (25 kg, 130×100×25) | 64.7 kg | £95.09 → **£114.11** | £119.35 → £143.22 | £142.30 → £170.76 | £178.51 → £214.21 |

   - About £24.49 is the account's **minimum**: everything up to about 7 kg costs the same. That includes a fuel surcharge (about 18%) and a £0.75 demand surcharge.
   - Big, light parts are charged **by size** (length × width × height ÷ 5,000). A door box is billed as 64.7 kg, plus a £15.95 additional-handling fee. The category box sizes in `ParcelSizes.java` matter for these.
   - **Compared with the flat £9.95:** the cheapest FedEx service is about £29 for a small part. The client decides whether to show FedEx prices at checkout, keep a flat price (the shop pays the difference), or set a markup or cap. The cheapest service is still free on orders of £100 or more.

2. **Free delivery over £100.** With FedEx, the shop pays the cheapest service on those orders. Keep it, raise the threshold, or drop it?
3. **Markup or handling fee** for packing, if any.
4. **Box sizes and weights** in `ParcelSizes.java` are estimates. They matter, because FedEx charges by box size when it's bigger than the weight. Check a few real parcels (doors, body panels, turbos) against them.
5. **Home vs business addresses:** no difference on the real account, so no tick box is needed.

**To go live:**
1. ~~Production keys~~: done on 3 November. The project's production key is linked to the shop's **production shipping account**, which is different from the sandbox account. Both are in `CREDENTIALS.local.md`. Using the sandbox account number with the production keys fails with "ACCOUNT.NUMBER.MISMATCH".
2. Put them on Render: `FEDEX_API_KEY`, `FEDEX_SECRET_KEY`, `FEDEX_ACCOUNT_NUMBER` (in `CREDENTIALS.local.md`), `FEDEX_ENVIRONMENT=live`, once the client has chosen how to price delivery (above). **Never put the sandbox keys on the live server**: they show FedEx's test prices (£100+ for a small part). Without any keys, the flat prices apply.
3. Update `frontend/public/llms.txt`, which states the flat £9.95. The Delivery page, terms, product pages, basket, checkout and help chat switch to FedEx wording by themselves (`deliveryQuotes`); the `<noscript>` text in `index.html` only says "free on orders of £100 or more", which stays true.

## 2. Legal pages (later, decided 4 November)

Drafts are on the site at **/terms** and **/privacy** (`frontend/src/pages/legal.tsx`), written for this shop from UK consumer law, UK GDPR and the business's own eBay terms. They are **not legal advice**: the business's adviser should review them. Points to confirm (marked `[to confirm]` on the pages):

- **ICO registration number.** Businesses handling personal data normally pay the ICO data protection fee (ico.org.uk/fee). The privacy page needs the number.
- **Returns.** UK law gives consumers 14 days to cancel without a reason, and a restocking fee can't be charged for simply changing their mind, only a reduction where the part was used beyond inspection. The site's wording now follows that (30-day goodwill returns; up to 30% off the refund only if fitted, used or tested). The eBay listing template still says a 30% fee applies to parts "ordered by mistake", which should be brought into line.
- Who pays return postage, and whether the original delivery charge is refunded (by law, the standard delivery charge must be refunded on cancellation).
- The 48-hour window to report delivery damage.
- Business (trade) customers' liability cap.
- How long part requests are kept (then add a clean-up job to delete them, as the privacy notice will promise).

---

## 3. Hosting and domain 🟠

The plan: the shop moves from GitHub Pages (development only) to **Render**, with the backend, on `speedyfixautosales.com`. Today the frontend is on GitHub Pages (`.github/workflows/deploy-pages.yml`, API at `https://speedyfix-backend.onrender.com`), `render.yaml` only has the backend, and the domain's DNS is at Wix, pointing at a Wix site. Moving the domain means changing those records in Wix.

1. **Render Static Site** for the frontend: build `cd frontend && npm ci && npm run build`, publish `frontend/dist`, and a rewrite rule `/*` → `/index.html`. That gives every page (products, categories) a normal 200 response, which fixes search engines not indexing product pages on GitHub Pages. Set `VITE_API_URL` to the backend address. Static sites are free on Render.
2. **Domain:** `www.speedyfixautosales.com` → the static site, `api.speedyfixautosales.com` → the backend (custom domains on Render, free TLS). Add both to the backend's CORS list (`site.cors-allowed-origins`) and Google sign-in's authorised origins.
3. **After the move:** set `SITE_URL=https://www.speedyfixautosales.com` on the backend (sitemap addresses) and change the `Sitemap:` line in `frontend/public/robots.txt` to `https://api.speedyfixautosales.com/sitemap.xml`. Submit the sitemap in Google Search Console.
4. **Cloudflare is optional.** It isn't needed for the move: Render does the hosting, TLS and page routing, and R2 (photos) works without the domain being on Cloudflare. It's worth adding later for a CDN for photos on `images.speedyfixautosales.com` (instead of the backend serving them from R2), bot and DDoS protection, and the rate-limit backstop described in the security review.
5. **Render free tier sleeps** after 15 minutes idle. The backend pings itself every 14 minutes on Render (`KeepAlive.java`, using `RENDER_EXTERNAL_URL`), so it stays awake. That uses about 730 of the free plan's 750 instance hours a month, so there's no room for a second free service. After a redeploy or crash, the first visitor still waits 30 to 50 seconds. The paid Starter plan (about $7 a month) avoids both.
6. **Database:** the free Render Postgres **expires around 27 November 2026** and has no backups. Upgrade to a paid plan (Basic, about $6 to $7 a month, daily backups) before then.
7. **Pages for crawlers that don't run JavaScript** (most AI assistants' crawlers: GPTBot, ClaudeBot, PerplexityBot). They read only the `<noscript>` summary in `index.html` and `llms.txt` (`frontend/public`); Google renders the pages and sees everything, including each part's price data. Worth doing after the move: prerender the information pages (about, delivery, FAQ, terms, privacy) at build time, and have the backend serve part pages with their name, price and description already in the HTML. Keep the `<noscript>` details in step with `application.yml` if the address or phone changes.

---

## 4. Keys and accounts 🟠

| What | Status | To do |
| --- | --- | --- |
| Render environment | Not updated since the last push | Add `R2_*`, `EBAY_CLIENT_ID/SECRET`, `PAYPAL_CLIENT_ID/SECRET` and `PAYPAL_ENVIRONMENT=live` (the code defaults to sandbox), `GEMINI_API_KEY`, `SITE_URL`, and once the domain is verified `RESEND_API_KEY` and `MAIL_STAFF_ORDERS` (values in `render.env` and `CREDENTIALS.local.md`). Check `SPRING_PROFILES_ACTIVE=postgres` and `DATABASE_*` are set, and `ADMIN_PASSWORD` (12+ characters; without it no one can sign in to the admin). `ADMIN_JWT_SECRET` and `ACCOUNT_JWT_SECRET` are generated by Render. Leave the FedEx keys off until production keys exist. |
| PayPal live secret | Sent over WhatsApp | Regenerate after launch (developer.paypal.com → live app → Secret) and update Render |
| Gemini key | The developer's own, temporary | Client creates one at aistudio.google.com; check the free tier is offered in the UK, otherwise it's paid (small cost per chat) |
| Google sign-in 🔴 | Origins not registered | **Needed to take orders:** checkout requires sign-in (`ORDERS_REQUIRE_SIGN_IN`, on by default), and Google is the only sign-in until email sign-in is switched on. Client adds the live site address (https://jeet1703.github.io now, the own domain later) under Authorised JavaScript origins of the OAuth client in Google Cloud, and publishes the consent screen. If it isn't working at launch, set `ORDERS_REQUIRE_SIGN_IN=false` on Render so guests can order. |
| eBay seller sign-in | Not started | Needed for compatibility tables and two-way stock sync (section 5) |
| After the push | | Copy the photo records and eBay descriptions from the local database to the live one (no re-download), then check the live site |

---

## 5. Features after launch 🟢

- **eBay compatibility tables** (the "fits 15 vehicles" list on each eBay listing): needs the client's one-time eBay sign-in; then import into the Compatibility tab and use for the reg check. About half a day.
- **Two-way eBay stock sync:** when a part sells on the website, end or reduce the eBay listing (same sign-in). Until then staff do it by hand.
- **Refund button** in the admin (PayPal refunds API), recorded on the order. Refunds are done in PayPal for now.
- **Choose a category photo** in the admin (the cut-outs are set by a database update for now).
- **Homepage slides:** they use stock photos (free Unsplash License; checked on 2 November 2026 that none of the live photos shows a brand logo or a recognisable person, which that licence doesn't cover, so keep any new ones free of both) with claims like "tested and graded before they're listed"; the client should confirm the wording, or the slides can use the shop's own cut-outs.
- **Email sign-in** (built, switched off): shoppers sign up with name, email and password and confirm with a 6-digit code. Codes are sent through Resend from `account@speedyfixautosales.com` (the same key and domain as the order emails; no separate mailbox or registration). **Forgot password: built 4 November**, one reset code per account per day (kept in the database). **Before switching on:** keep sign-ups waiting for their code in the database rather than memory (a restart loses them). Then set up Resend (section 1) and set `ACCOUNT_EMAIL_SIGN_IN=true`. Without the Resend key codes only go to the server log, so never switch it on live before that.
- **Real reviews** (e.g. eBay feedback or Trustpilot); the section stays hidden until there are some.
- **Help chat:** review real questions after launch and tune the instructions; consider keeping anonymous chat logs (with the privacy notice updated) to learn what customers ask.

---

## 6. Checks to do by hand 🟠

The automated checks can't see the screen. Before launch, on the live site:

- **Phone, on mobile data:** open the menu and follow every link; search; open a part and swipe its photos; add to basket, change the quantity; check out with the keyboard open (nothing covered, the chat bubble not over "Place order"); pay with sandbox PayPal and a test card; check the order page and My orders.
- **Keyboard only:** tab through the header, Products menu, basket drawer, chat and checkout. You can always see where you are, Enter works, Esc closes menus, the drawer and the chat.
- **Zoom to 200%** on a laptop: nothing cut off or overlapping.
- **Admin:** create a listing with photos, a sale and an HTML description; mark an order paid and dispatched; run "Sync with eBay".
- Record anything that fails with the page, browser, screen width and exact step.

---

**Done since the last push** (for reference): sign-in required to buy (`ORDERS_REQUIRE_SIGN_IN`); forgot password (once a day); cancellation and payment-check emails; the fixes from the 4 November sanity test (TEST-LOG.md); VAT invoices (PDF) on the order page, My orders and the admin, with invoice and staff emails through Resend; sign-up code emails through Resend; FedEx delivery prices at checkout; packed weights per part; category photos; the short About section; mobile menu scrolling; SEO fixes (canonical links, structured data, noscript summary, llms.txt); database storage for customers, orders, baskets and part requests; My orders; eBay photos and descriptions; eBay sync (automatic and manual); PayPal Checkout; unpaid-order expiry with PayPal reconciliation; sale prices; HTML descriptions; photo gallery; category cut-outs and menu previews; help chat with Gemini; business details; About, Delivery, FAQ, Terms and Privacy pages; rate limits; robots.txt and sitemap; response compression.
