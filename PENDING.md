# SpeedyFix Auto Sales: what's left before and after launch

Last updated 1 November 2026. This replaces the "left to do" parts of [STATUS.md](STATUS.md), which describes the project as it was on 24 October.

**Where things stand.** Everything below the "Done" line in section 6 is built and tested on the development computer (78 backend tests pass, the frontend builds). The live site still runs the version from the last push until the next "commit and push".

Priorities: 🔴 needed before taking real orders · 🟠 should be done before launch · 🟢 after launch.

---

## 1. Emails 🔴

Nothing is emailed yet. New orders and part requests only appear in the admin, and customers get no confirmation by email.

### What to send

| Email | To | When | Contents |
| --- | --- | --- | --- |
| New order | Staff (orders inbox) | Order paid (PayPal capture) | Reference, items, total, customer and delivery details, link to the order in the admin |
| Order confirmation | Customer | Order paid | Reference, items, total, delivery address, what happens next, contact details |
| Order dispatched | Customer | Staff mark it dispatched | Courier and tracking number |
| Order cancelled | Customer | Unpaid order expires, or staff cancel | Why, and that no money was taken (or the refund) |
| New part request | Staff (buying inbox) | Request received | The part, vehicle, contact details and how they want a reply, photos as attachments or admin link |
| Part request received | Customer | Request received (only if they gave an email) | Reference and when to expect a reply |
| Payment needs checking | Staff | PayPal capture doesn't match the order (already logged as an error) | Order reference and PayPal order id |

### Plan

1. **Pick a sending service.** Recommended: **Resend** (free for 3,000 emails a month, 100 a day; simple HTTP API, no SMTP server to run). Alternatives: Postmark, Brevo, or the business's Google Workspace SMTP.
2. **Domain set-up (client):** add the DNS records the service gives (SPF, DKIM, and a DMARC policy) for `speedyfixautosales.com`, so emails come from `orders@speedyfixautosales.com` and don't land in spam. This needs the domain's DNS access.
3. **Keys:** `RESEND_API_KEY` in Render; `MAIL_FROM` (`SpeedyFix Auto Sales <orders@speedyfixautosales.com>`), `MAIL_STAFF_ORDERS` and `MAIL_STAFF_REQUESTS` (inboxes; can be the same, e.g. eBay.speedyfix@gmail.com).
4. **Build (about half a day):**
   - A `MailService` with plain, branded HTML templates (and text versions), written from the order and request data.
   - **An outbox table** (`email_outbox`: recipient, template, payload, status, attempts, last error). Each event writes a row in the same step as the change it reports (order paid, dispatched…), and a scheduled sender delivers and retries with back-off. So a mail service outage never loses an email or blocks an order, and a repeat (PayPal capture called twice) can't send twice: one row per order and event.
   - Staff see failed sends in the admin (order page shows "Confirmation email: sent / failed, resend").
   - Turned off when no key is set (development and tests), with a log line instead.
5. **Test:** sandbox order → staff and customer emails arrive (check inbox, not spam, on Gmail and Outlook); dispatch → tracking email; stop the mail service (bad key) → order still completes, email retried, admin shows the failure; capture twice → one email.
6. **Words to update once it's live:** the order page ("check its status in the help chat") can then say "we've emailed your confirmation".

---

## 2. Legal pages 🔴

Drafts are on the site at **/terms** and **/privacy** (`frontend/src/pages/legal.tsx`), written for this shop from UK consumer law, UK GDPR and the business's own eBay terms. They are **not legal advice**: the business's adviser should review them. Points to confirm (marked `[to confirm]` on the pages):

- **ICO registration number.** Businesses handling personal data normally pay the ICO data protection fee (ico.org.uk/fee). The privacy page needs the number.
- **Returns.** UK law gives consumers 14 days to cancel without a reason, and a restocking fee can't be charged for simply changing their mind, only a reduction where the part was used beyond inspection. The site's wording now follows that (30-day goodwill returns; up to 30% off the refund only if fitted, used or tested). The eBay listing template still says a 30% fee applies to parts "ordered by mistake", which should be brought into line.
- Who pays return postage, and whether the original delivery charge is refunded (by law, the standard delivery charge must be refunded on cancellation).
- The 48-hour window to report delivery damage.
- Business (trade) customers' liability cap.
- How long part requests are kept (then add a clean-up job to delete them, as the privacy notice will promise).

---

## 3. Hosting and domain 🟠

The plan: the shop moves from GitHub Pages (development only) to **Render**, with the backend, on `speedyfixautosales.com`.

1. **Render Static Site** for the frontend: build `cd frontend && npm ci && npm run build`, publish `frontend/dist`, and a rewrite rule `/*` → `/index.html`. That gives every page (products, categories) a normal 200 response, which fixes search engines not indexing product pages on GitHub Pages. Set `VITE_API_URL` to the backend address. Static sites are free on Render.
2. **Domain:** `www.speedyfixautosales.com` → the static site, `api.speedyfixautosales.com` → the backend (custom domains on Render, free TLS). Add both to the backend's CORS list (`site.cors-allowed-origins`) and Google sign-in's authorised origins.
3. **After the move:** set `SITE_URL=https://www.speedyfixautosales.com` on the backend (sitemap addresses) and change the `Sitemap:` line in `frontend/public/robots.txt` to `https://api.speedyfixautosales.com/sitemap.xml`. Submit the sitemap in Google Search Console.
4. **Cloudflare is optional.** It isn't needed for the move: Render does the hosting, TLS and page routing, and R2 (photos) works without the domain being on Cloudflare. It's worth adding later for a CDN for photos on `images.speedyfixautosales.com` (instead of the backend serving them from R2), bot and DDoS protection, and the rate-limit backstop described in the security review.
5. **Render free tier sleeps** after 15 minutes idle (first visitor waits 30 to 50 seconds, and the eBay sync and unpaid-order clean-up only run while awake). Either a free uptime ping (UptimeRobot, every 10 minutes, on `/api/site`) or the paid Starter plan (about $7 a month).
6. **Database:** the free Render Postgres **expires around 27 November 2026** and has no backups. Upgrade to a paid plan (Basic, about $6 to $7 a month, daily backups) before then.

---

## 4. Keys and accounts 🟠

| What | Status | To do |
| --- | --- | --- |
| Render environment | Not updated since the last push | Add `R2_*`, `EBAY_CLIENT_ID/SECRET`, `PAYPAL_*` (sandbox first, then live), `GEMINI_API_KEY`, `SITE_URL` (values in `render.env` and `CREDENTIALS.local.md`) |
| PayPal live secret | Sent over WhatsApp | Regenerate after launch (developer.paypal.com → live app → Secret) and update Render |
| Gemini key | The developer's own, temporary | Client creates one at aistudio.google.com; check the free tier is offered in the UK, otherwise it's paid (small cost per chat) |
| Google sign-in | Origins not registered | Client adds the site addresses in Google Cloud and publishes the consent screen |
| eBay seller sign-in | Not started | Needed for compatibility tables and two-way stock sync (section 5) |
| After the push | | Copy the photo records and eBay descriptions from the local database to the live one (no re-download), then check the live site |

---

## 5. Features after launch 🟢

- **eBay compatibility tables** (the "fits 15 vehicles" list on each eBay listing): needs the client's one-time eBay sign-in; then import into the Compatibility tab and use for the reg check. About half a day.
- **Two-way eBay stock sync:** when a part sells on the website, end or reduce the eBay listing (same sign-in). Until then staff do it by hand.
- **Refund button** in the admin (PayPal refunds API), recorded on the order. Refunds are done in PayPal for now.
- **Choose a category photo** in the admin (the cut-outs are set by a database update for now).
- **Homepage slides:** they use stock photos (Unsplash licence, fine to use) with claims like "tested and graded before they're listed"; the client should confirm the wording, or the slides can use the shop's own cut-outs.
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

**Done since the last push** (for reference): database storage for customers, orders, baskets and part requests; My orders; eBay photos and descriptions; eBay sync (automatic and manual); PayPal Checkout; unpaid-order expiry with PayPal reconciliation; sale prices; HTML descriptions; photo gallery; category cut-outs and menu previews; help chat with Gemini; business details; About, Delivery, FAQ, Terms and Privacy pages; rate limits; robots.txt and sitemap; response compression.
