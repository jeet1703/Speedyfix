# SpeedyFix Auto Sales: hosting and database research

Researched 24 October 2026. Prices come from the providers' own pricing pages where they loaded, and from recent third-party price trackers otherwise (sources at the end). Most providers bill in **US dollars**; GBP figures are rough conversions. VAT may be added on top. Check each price on the provider's site before signing up, because these pages change often.

## Recommendation in one paragraph

Host everything on **Render (Frankfurt)**, with **Render Postgres** for the database, **Cloudflare** (free) in front for DNS, SSL and caching, and **Cloudflare R2** for part photos. The core hosting comes to about **$21 a month (about £16–17)**. Emails and monitoring stay free at the start. The larger variable costs are **card fees** (Stripe, per sale) and **plate lookups** (per search), not hosting. Everything is managed: no servers to patch, automatic deploys from GitHub, and one dashboard.

---

## 1. What the site needs

| Part | What it is | Needs |
| --- | --- | --- |
| Frontend | React shop and admin. Static files today; server-rendered (Node) after Phase 5 for SEO. | A host serving `www.<your-domain>` with HTTPS |
| Backend | Spring Boot (Java 21) in Docker | An always-on container with at least 512 MB RAM (1–2 GB is comfortable for Java) |
| Database | PostgreSQL (Phase 1 of the plan) | Managed, with automatic backups, close to the backend |
| Photos | Part and part-request photos | S3-compatible storage with cheap delivery |
| Email | Order and dispatch emails | A transactional email API |
| Payments | Cards, Apple Pay, Google Pay | Stripe |

**Region:** no major platform among these has a UK region except DigitalOcean (London). **Frankfurt** adds roughly 15–25 ms to each request from the UK, which shoppers won't notice, and Cloudflare serves static files and images from UK locations anyway. Under UK GDPR, the EU counts as "adequate", so hosting customer data in Germany is allowed. Say so in the privacy policy.

---

## 2. Hosting the app (frontend and backend)

| Provider | Smallest always-on plan | Monthly fee for the account | UK or EU region | Notes |
| --- | --- | --- | --- | --- |
| **Render** (recommended) | **Starter $7**: 512 MB, 0.5 CPU. **Standard $25**: 2 GB, 1 CPU. | Hobby workspace **$0** (1 member). The new Pro plan is a flat fee with unlimited members. | Frankfurt (no London) | Already set up via `render.yaml`. Deploys from GitHub automatically. The free plan sleeps after 15 minutes and takes about 1 minute to wake, so it's not suitable for a shop. |
| Railway | Pay per use: about $20 per vCPU and $10 per GB of RAM a month | Hobby $5 (includes $5 of usage); Pro $20 | Has EU regions (check the location) | Good developer experience, but usage-based, so the bill varies each month. |
| Fly.io | shared-cpu-1x, 512 MB: about $3.19 in its cheapest US region; more in Europe | None | London (`lhr`) | Cheap compute, but managed Postgres is about $38 a month or more, and it needs more setup. |
| DigitalOcean App Platform | From $5 a month | None | **London** | The UK region is the main reason to pick it. Managed Postgres from about $15. |
| Hetzner VPS (do it yourself) | CX23 about €5.49 a month (after the 2026 price rises) | None | Germany or Finland | Cheapest, but **you** run the server: security updates, backups, SSL, monitoring. Not recommended without someone to look after it. |
| Cloudflare Workers (frontend only) | Free plan, or $5 a month for the paid plan | None | Global edge | Can host the server-rendered React Router v7 frontend cheaply. The Java backend can't run there. |
| Vercel (frontend only) | Pro $20 per member | None | Global edge | The free Hobby plan is for non-commercial use only, so a shop would need Pro. |

### Java memory note

Spring Boot with a database layer **fits in Render Starter's 512 MB** if the Java memory is capped (for example `JAVA_TOOL_OPTIONS=-Xmx320m -XX:MaxMetaspaceSize=128m`). Start on Starter at $7. If Render's metrics show memory pressure or restarts, move to Standard at $25. That's one click, with no code change.

### Frontend while the shop is a single-page app (now until Phase 5)

Move the frontend from GitHub Pages to **Cloudflare Pages (free)** on your domain. When a project has no `404.html`, Cloudflare Pages serves the app with status **200** for every route. GitHub Pages returns **404** for every product page, which is bad for Google. This is a quick fix you can make now. Once server-side rendering lands (Phase 5), the frontend moves to a Render Starter service at $7, or to Cloudflare Workers.

---

## 3. Database

| Option | Price | Backups | Notes |
| --- | --- | --- | --- |
| **Render Postgres Basic-256mb** (recommended to start) | **$6 a month**, plus disk at **$0.30 per GB a month** (5 GB ≈ $1.50) | Included on paid databases. Check the restore window for your plan. | Same region and private network as the backend, so it's fast and never exposed to the internet. Flat price. Upgrade to Basic-1gb ($19) or Basic-4gb ($75) as you grow. Disk can grow but not shrink. |
| Render Postgres (free) | $0 | **None** | **Expires after 30 days** (then deleted 14 days later). Testing only. |
| Neon (Launch plan) | Pay per use: **$0.106 per compute-hour** (CU-hour) and **$0.35 per GB a month**. A small 0.25 CU database running all month ≈ $19; one that sleeps overnight ≈ $10–13. | Restore any point in the last 7 days | Excellent free and dev branches. Scales to zero when idle (a short wake-up delay). Less predictable cost. |
| Neon (Free) | $0 | Only 6 hours of history | 100 compute-hours a month and 1 GB. Fine for development, too thin for a live shop. |
| Supabase Pro | **$25 a month** (includes a Micro database, 8 GB disk, daily backups kept 7 days) | Daily, 7 days | Free projects **pause after 1 week of inactivity**, which rules them out for a shop. Pro is good if you want built-in file storage and auth. |
| DigitalOcean Managed Postgres | From about **$15 a month** (single node) | Daily | The London region is available. |

**Why Render Postgres over Neon:** the flat $6–7 is cheaper than Neon's usage pricing for a database that is awake all day. It sits on Render's private network next to the backend, and it's one bill and one dashboard. Use a **free Neon project for development** if you want a cloud dev database.

**How much data?** A few thousand parts with fitment, plus a few years of orders, is well under 1 GB. Photos don't go in the database.

---

## 4. Photos: Cloudflare R2

| Item | Price |
| --- | --- |
| Storage | **10 GB a month free**, then $0.015 per GB a month |
| Uploads (Class A) | 1 million a month free, then $4.50 per million |
| Views (Class B) | 10 million a month free, then $0.36 per million |
| Download bandwidth (egress) | **Free** |

Around 2,000 parts × 4 photos × 300 KB ≈ 2.4 GB, so it stays **free**. Serve the photos from `images.<your-domain>` through Cloudflare's cache. Part-request photos go in a private bucket, seen only through the admin.

---

## 5. Other services

| Service | Choice | Price | Notes |
| --- | --- | --- | --- |
| DNS, SSL, CDN, firewall | **Cloudflare free** | $0 | Point the domain's nameservers at Cloudflare |
| Transactional email | **Resend (decided)** | Free: 3,000 emails a month, **max 100 a day**. Pro **$20 a month** for 50,000. | The shop sends about 4–5 emails per order, so the free plan covers about 500 orders a month. Sent over SMTP, so it can be swapped for Postmark ($15 a month, best delivery) if emails land in spam. Full comparison and setup: [INTEGRATIONS.md §4](INTEGRATIONS.md#4-transactional-email-order-confirmations-and-so-on). |
| Payments | **Stripe** | **No monthly fee.** Standard UK cards **1.5% + 20p**; premium UK cards **2.8% + 20p**; EEA cards 2.5% + 20p; international cards 3.15% + 20p; Klarna from 4.99% + 35p; disputes **£20** each | Apple Pay and Google Pay are charged at the card rates. A £300 engine on a standard UK card costs £4.70 in fees. |
| Plate lookup (VRM data) | **DVSA MOT History API (free)** to start; a paid provider later | DVSA: free, 500,000 a day. Paid (for engine codes): UK Vehicle Data about **£0.04–£0.15 per lookup**, Vehicle Smart extended data from about 8p (ex VAT). | DVSA returns the make, model, fuel and engine size but **no engine code**. The DVLA VES API returns no model, and its registration is currently closed. Details: [INTEGRATIONS.md §1](INTEGRATIONS.md#1-plate-lookup-vrm--vehicle). |
| Error tracking | Sentry Developer plan | Free (about 5,000 errors a month, 1 user) | |
| Uptime alerts | Better Stack free | Free (10 monitors, checked every 3 minutes) | UptimeRobot is a fine alternative |
| ICO data protection fee | Required for UK businesses handling personal data | **£52 a year** (Tier 1, small business); £47 by direct debit | Check your tier with the ICO fee checker |
| Domain | Already bought | Renewal only | Move DNS to Cloudflare; the registrar can stay the same |

---

## 6. Monthly cost scenarios

### A. Recommended start (low traffic, the first months)

| Item | $ per month |
| --- | --- |
| Render Hobby workspace | 0 |
| Backend: Render Starter | 7 |
| Frontend: Cloudflare Pages now; Render Starter after SSR | 0 → 7 |
| Render Postgres Basic-256mb + 5 GB disk | 7.50 |
| Cloudflare (DNS, CDN) + R2 photos | 0 |
| Resend free, Sentry free, Better Stack free | 0 |
| **Hosting total** | **≈ $14.50 now, ≈ $21.50 after SSR (about £11–17)** |

### B. Growing shop (steady orders, 2–3 staff)

| Item | $ per month |
| --- | --- |
| Backend: Render Standard (2 GB) | 25 |
| Frontend SSR: Render Starter | 7 |
| Render Postgres Basic-1gb + 10 GB disk | 22 |
| Email: Postmark Basic or Resend Pro | 15–20 |
| R2 (still mostly free) | 0–1 |
| Render Pro workspace (if you need team members, logs, previews) | flat fee; check the pricing page |
| **Hosting total** | **≈ $70–75 a month (about £55–60), plus the Pro workspace if used** |

### Variable costs (not hosting)

| Item | Example |
| --- | --- |
| Stripe | 100 orders a month averaging £150 on standard UK cards ≈ **£245 a month** in fees (1.5% + 20p each) |
| Plate lookups | 1,000 uncached lookups a month at £0.05–£0.10 ≈ **£50–100 a month** ex VAT. Set the monthly cap in the admin Settings. |
| ICO | £52 a year |

### Options I don't recommend for now

| Option | Why not |
| --- | --- |
| Render free plan (current) | Sleeps after 15 minutes (about a 1-minute wake-up), the free database expires after 30 days, no backups |
| Hetzner or another self-managed VPS | Cheapest (about €5–20), but someone has to patch, secure, back up and monitor the server |
| Supabase or Neon free plans for the live database | Pausing or thin backups |
| AWS or Azure | Far more setup and billing complexity than this shop needs |

---

## 7. Free alternatives (£0 a month)

Every part of the stack has a free option. The catch is always the same: sleeping, pausing, small limits or thin backups. A free setup is fine for a **preview, a soft launch or the first few orders**. Move the backend and database to paid plans before relying on it for income.

| Part | Free option | Limits and catch | Verdict |
| --- | --- | --- | --- |
| Frontend (static, now) | **Cloudflare Pages** | Free plan allows commercial use; generous build and bandwidth limits | **Use it.** Better than GitHub Pages and $0. |
| Frontend (alternative) | Netlify free | Bandwidth and build-minute caps | Fine |
| Frontend (SSR, Phase 5) | **Cloudflare Workers free** | About 100,000 requests a day free; $5 a month paid plan after that | Good fit for React Router v7 |
| Frontend | Vercel Hobby | **Not allowed for commercial use** | Avoid for the shop |
| Backend | **Render free** (current) | 512 MB; sleeps after 15 minutes (about a 1-minute wake-up); 750 free hours a month, which covers one service running all month. The `KeepAlive` self-ping keeps it awake. About 5 GB of bandwidth on the Hobby workspace. | OK for a preview. A cold start mid-checkout loses sales, and Render can change free-plan rules. |
| Backend | Oracle Cloud Always Free (Arm VM) | Needs a card and phone. Capacity is often unavailable, idle VMs can be reclaimed, and recent reports say the free allowance was cut. **You manage the server.** | Only if someone will maintain it |
| Backend | Google Cloud Run free allowance | Card required; scales to zero, so Spring Boot cold starts take several seconds | Possible, but more setup |
| Backend | Koyeb free | Now needs a card ($29 hold), and the free instance sleeps | Not worth it |
| Database | **Neon Free** | 0.5–1 GB per project, 100 compute-hours a month, sleeps after 5 minutes idle (wakes in under a second), **only 6 hours of restore history** | **Best free database.** Take your own nightly backups. |
| Database | Aiven free Postgres | About 1 GB, no time limit, but **you can't choose the region**, which may not be the UK or EU | Check the region for GDPR first |
| Database | Supabase Free | 500 MB; **pauses after 7 days without activity**; no backups | Risky for a shop |
| Database | Render free Postgres | **Deleted after 30 days** | Testing only |
| Photos | **Cloudflare R2** | 10 GB, 1 million uploads and 10 million views a month free, free egress | Use it (free at this size) |
| Transactional email | **Resend free** | 3,000 a month, **100 a day** | Use it to start |
| Transactional email | Brevo free | 300 a day, Brevo branding | Alternative |
| Transactional email | Amazon SES | Not free, but about **$0.10 per 1,000** emails | Cheapest at volume |
| Business mailbox (`sales@your-domain`) | **Cloudflare Email Routing** | Forwarding only (to Gmail, for example) | Free and instant |
| Business mailbox | Zoho Mail free | Up to 5 users, 5 GB each, custom domain | A real inbox for free |
| Errors and uptime | Sentry Developer, Better Stack, UptimeRobot | Small quotas, 1 user | Use them |
| Analytics | **Cloudflare Web Analytics** | Cookieless, so no consent needed for it | Use it from day one |
| Analytics | Google Analytics 4 | Free; needs cookie consent | Add it with the cookie banner |
| Spam protection | Cloudflare Turnstile | Free | Use it on forms |
| SEO tools | Google Search Console, Bing Webmaster, Google Merchant Center, Google Business Profile | Free | Use them all |
| Payments | No free option | Every provider charges per sale (see INTEGRATIONS.md) | Stripe |

### A £0 setup that works for a preview or soft launch

| Piece | Free choice |
| --- | --- |
| Frontend | Cloudflare Pages |
| Backend | Render free with `KeepAlive` (as now) |
| Database | Neon Free (Frankfurt) plus a nightly `pg_dump` to R2 |
| Photos | Cloudflare R2 |
| Email | Resend free plus Cloudflare Email Routing |
| Monitoring | Sentry, Better Stack, Cloudflare Web Analytics |
| Plate lookup | DVSA MOT History API (free; see INTEGRATIONS.md) |

**When to upgrade:** once orders arrive every week, move the backend to Render Starter ($7) and the database to Render Postgres Basic ($6–7) or Neon Launch. That's scenario A, at about $15–22 a month.

---

## 8. How to host it now, step by step

> **Important:** until Phase 1 (database) and Phase 2 (payments) are done, the live site loses data on every deploy and takes no payment. Host it now as a **preview or catalogue**: switch off checkout, or label it clearly, and don't take real orders yet.

1. **Cloudflare:** create a free account, add your domain, and change the nameservers at your registrar to the two Cloudflare gives you. This takes from a few minutes to 24 hours.
2. **Frontend → Cloudflare Pages:** connect the GitHub repo, set the build command to `npm ci && npm run build`, the root directory to `frontend`, the output directory to `dist`, and the environment variable `VITE_API_URL=https://api.<your-domain>`. Add the custom domain `www.<your-domain>`, and redirect the bare domain to `www`. Remove the `cp dist/index.html dist/404.html` step, and turn off GitHub Pages afterwards.
3. **Backend → Render:** upgrade `speedyfix-backend` from free to **Starter** and add the custom domain `api.<your-domain>`. Render shows a DNS target; add it in Cloudflare as a CNAME. Set `ADMIN_PASSWORD`, `ADMIN_EMAIL` and `ADMIN_JWT_SECRET` in the environment settings.
4. **CORS:** add `https://www.<your-domain>` to `site.cors-allowed-origins` in `application.yml` and redeploy.
5. **Database (once Phase 1 code is ready):** in Render, create Postgres **Basic-256mb** in **Frankfurt** and copy its *internal* connection string into the backend as `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
6. **R2:** create a bucket, connect `images.<your-domain>`, and create an API token. The variables go in the backend environment (Phase 1.5).
7. **Email DNS:** when email is added, put the provider's SPF, DKIM and DMARC records in Cloudflare.
8. **Monitoring:** Better Stack checks on `https://www.<your-domain>` and `https://api.<your-domain>/api/site`, and a Sentry project for each app.

Once the backend is on a paid plan, the `KeepAlive` self-ping is no longer needed.

---

## Sources

- Neon pricing: https://neon.com/pricing
- Supabase pricing: https://supabase.com/pricing
- Cloudflare R2 pricing: https://developers.cloudflare.com/r2/pricing/
- Cloudflare Workers pricing: https://developers.cloudflare.com/workers/platform/pricing/
- Railway pricing: https://railway.com/pricing
- Stripe UK pricing: https://stripe.com/gb/pricing
- Render free plan limits: https://render.com/docs/free
- Render regions: https://render.com/docs/regions
- Render workspace plans: https://render.com/docs/new-workspace-plans and https://render.com/changelog/updated-plans-for-render-workspaces
- Render compute and Postgres prices (third-party, checked 2026): https://frontdeskreview.com/software/managed-postgres/render/, https://www.budgetforge.dev/tools/render-pricing-2026, https://kuberns.com/blogs/render-postgres-pricing-setup-limits/
- Fly.io pricing: https://fly.io/docs/about/pricing/
- DigitalOcean pricing (third-party): https://kuberns.com/blogs/digitalocean-pricing/
- Hetzner 2026 price changes: https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/
- Postmark pricing (third-party, 2026): https://frontdeskreview.com/software/transactional-email/postmark/
- Resend pricing (third-party, 2026): https://automationatlas.io/answers/resend-pricing-explained-2026/
- UK vehicle data pricing: https://ukvehicledata.co.uk/dvla-data-api, https://vehiclesmart.com/vehicle-smart-api.html
- Sentry free plan (third-party): https://costbench.com/software/developer-tools/sentry/free-plan/
- ICO fee: https://www.lewissilkin.com/insights/2025/02/06/fee-payable-to-the-ico-to-increase-in-february-102jzb9
