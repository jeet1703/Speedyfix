# SpeedyFix Auto Sales: third-party APIs and integrations

Researched 24 October 2026. This file covers every outside service the shop needs, with free and paid options. Prices come from provider pages where available and from recent third-party trackers otherwise (sources at the end). Most prices exclude VAT. Confirm them with each provider before signing up.

| # | Need | Free option | Recommended | Phase |
| --- | --- | --- | --- | --- |
| 1 | Plate lookup (registration → vehicle) | **DVSA MOT History API** | DVSA to start; a paid provider for engine codes | 3 |
| 2 | Parts fitment data | Our own fitment table (built into the admin) | Own data now; TecDoc later | Built / later |
| 3 | Payments | None | **Stripe** | 2 |
| 4 | Transactional email | Resend free (3,000 a month) | **Resend (decided)**; Postmark if delivery suffers | 2 |
| 5 | Business mailbox | Cloudflare Email Routing or Zoho Mail free | Either | Now |
| 6 | Address lookup at checkout | postcodes.io (postcode check only) | postcodes.io now; paid autocomplete later | 2 |
| 7 | Couriers and labels | Royal Mail Click & Drop | Click & Drop + manual pallet booking | 2 |
| 8 | Reviews | Trustpilot free (50 invites a month), Google reviews | Trustpilot free + Google | 4 |
| 9 | Analytics and consent | Cloudflare Web Analytics, GA4 | Both | 5–6 |
| 10 | Spam protection | Cloudflare Turnstile | Turnstile | 6 |
| 11 | Google Shopping and search | Merchant Center, Search Console, Business Profile | All free | 5, 8 |
| 12 | WhatsApp contact | `wa.me` click-to-chat link | Link (already in the site settings) | Now |

---

## 1. Plate lookup (VRM → vehicle)

The site needs **registration → make, model, engine size, fuel, year**, and ideally the **engine code** (for example `YD25DDTi`). The engine code is what makes fitment for engines and gearboxes reliable.

### Free government APIs

| API | What it returns | Limits | Status | Catch |
| --- | --- | --- | --- | --- |
| **DVSA MOT History API** | Vehicle details (make, **model**, fuel type, engine size, colour, first registration) plus the MOT test history | **500,000 requests a day**, 15 a second, bursts of 10 | **Registration open.** For organisations, businesses or individuals. Approval takes **up to 5 working days**. Uses OAuth 2.0 client credentials plus an API key. | **No engine code.** Vehicles too new for an MOT (under 3 years) may have little or no data. A key unused for 90 days is revoked. |
| DVLA Vehicle Enquiry Service (VES) | Make, colour, engine capacity, fuel type, year of manufacture, tax and MOT status, CO₂, euro status | Per-second limits by plan | **Registration is closed** "while we make some system upgrades" (official page, checked today) | **No model, no engine code.** One key per company. |

**Free approach (Phase 3, start here):**
1. Call the **DVSA MOT History API** with the plate to get the make, model, fuel, engine size and year.
2. Match that to our own `vehicles` table (make + model + engine size + fuel + year range → likely engine codes). Staff maintain the table through the admin.
3. Show "**Likely fits**: confirm your engine code" when more than one engine is possible. Engine codes for engines and gearboxes should be confirmed anyway, because the engine code isn't on the V5C (logbook).
4. Cache each lookup for 30 days.

This costs £0 and covers most of the UK car parc. Its weak spots are brand-new cars and telling apart engines with the same size.

### Paid providers (when the volume or accuracy justifies it)

| Provider | Price (ex VAT) | Engine code? | Notes |
| --- | --- | --- | --- |
| **UK Vehicle Data** (ukvehicledata.co.uk) | DVLA vehicle data from **£0.15 a lookup** (smallest credit pack) down to **£0.04** (a £995 pack). MOT, images and valuations cost extra. | Check the "Vehicle Data" package | Prepaid credits; well known among UK parts sellers |
| **Vehicle Smart** | Basic DVLA and MOT data from **2.5p** (up to 2,000 a month) down to about 0.5p at high volume. **Extended data with engine and trim from about 8p.** | Extended tier | Pricing page is over a year old, so get a quote |
| One Auto API | Per-use pricing (valuation from 20p; vehicle data on request) | Ask | Offers multiple data products |
| VehicleMatic, Autodata Solutions, DealerPricing | Advertised from 1p to 16p per lookup | Varies | Smaller resellers; compare quotes |
| **TecAlliance (TecDoc)**: plate → KType → parts | No public price; licensing on request | Yes (KType / NType) | The industry parts catalogue, mapping vehicles to OE and aftermarket parts. Worth it later for cross-referencing OE numbers at scale. |

**Recommendation:** register for the **DVSA MOT History API now** (free, takes up to 5 days). Build Phase 3 with a `VehicleLookup` implementation for DVSA, plus our engine-code matching. Get quotes from **UK Vehicle Data** and **Vehicle Smart (extended)**, and add one as a second `VehicleLookup` implementation when the free approach misses too often. The admin's **lookup cost and monthly cap** settings are already built for this.

## 2. Parts fitment data

| Option | Cost | Notes |
| --- | --- | --- |
| **Own fitment data** (already built) | Free | The admin records OE numbers, cross-references, engine codes and confirmed vehicles, and suggests fitment from parts sharing an OE number or engine code. Accurate for a used-parts stock where each item is checked by hand. |
| TecDoc (TecAlliance) | Licence on request | Needed for a large catalogue of new aftermarket parts; overkill for one-off used parts now |

## 3. Payments

There's no free card processor; each charges per sale. UK online rates:

| Provider | Standard UK card | Other | Monthly fee | Notes |
| --- | --- | --- | --- | --- |
| **Stripe** (recommended) | **1.5% + 20p** | Premium UK cards 2.8% + 20p; EEA 2.5% + 20p; international 3.15% + 20p; Klarna from 4.99% + 35p; disputes £20 | None | Best developer API: Payment Element, Apple Pay, Google Pay, webhooks, refunds by API. Works with the plan in Phase 2. |
| Square | **1.4% + 25p** | Non-UK cards 2.5% + 25p | None | Slightly cheaper on large orders; less flexible API for a custom checkout |
| SumUp | 2.5% (online) | — | None on pay-as-you-go | More expensive online |
| PayPal | Not confirmed in this research | — | — | Can be added as an extra method later (Stripe supports PayPal in some markets) |

Worked example: a £300 engine costs **£4.70** on Stripe or **£4.45** on Square. A £40 sensor costs £0.80 on Stripe or £0.81 on Square. Stripe's tooling is worth the small difference.

## 4. Transactional email (order confirmations and so on)

### Decision: **Resend**, sent over SMTP so the provider can be swapped

**Which emails the shop sends:**

| Email | To | When |
| --- | --- | --- |
| Order confirmation (with VAT receipt) | Customer | Payment confirmed |
| Dispatched (courier and tracking link) | Customer | Staff mark the order dispatched |
| Refund or cancellation | Customer | Staff refund |
| Part request received | Customer | Request submitted |
| Quote for a part request | Customer | Staff send a quote |
| New order or new part request alert | Staff (`orders@`, `sales@`) | Each order or request |
| Unmatched vehicle searches | Buying team | Weekly summary (replaces today's `mailto:` link) |
| Staff password reset | Staff | Phase 6 |

**How many:** about **4–5 emails per order** and 2–3 per part request. At **100 orders and 50 requests a month**, that's about **600–650 emails a month**, roughly 20–30 a day.

**Comparison:**

| Provider | Free | Paid | Delivery | Developer fit | Verdict |
| --- | --- | --- | --- | --- | --- |
| **Resend** | **3,000 a month, max 100 a day**; sending stops at the limit, with no surprise charges | Pro **$20 a month** for 50,000; $35 for 100,000 | Good; you verify your own domain (SPF and DKIM) | REST API and SMTP; Java works over SMTP with Spring Mail | **Chosen.** The free plan covers the shop about 5× over at the volume above. |
| Postmark | 100 a month (testing only) | Basic **$15 a month** for 10,000; Pro $16.50 | **Best in class** for transactional email (separate message streams, fast inbox delivery) | REST API and SMTP | **The upgrade path** if emails land in spam or volume grows |
| Brevo | 300 a day, **Brevo branding** on emails | Paid plans | Good | REST API and SMTP | Branding on order emails looks unprofessional |
| Amazon SES | No meaningful free tier for an app hosted outside AWS (sources conflict) | About **$0.10 per 1,000** | Good once out of the "sandbox" (needs an AWS approval request) | SMTP and SDK | Cheapest at large volume, but more setup and an AWS account |
| SendGrid | Free plan removed | Paid | Good | REST API and SMTP | No reason to choose it now |

**Why Resend:**
1. **£0 to start, with room to grow.** 3,000 a month covers about 500 orders a month. The 100-a-day cap covers about 20 orders a day.
2. **No surprise bills.** The free plan stops sending instead of charging, so watch the dashboard and upgrade to Pro ($20) before the limit.
3. **Easy to set up.** Verify the domain in Cloudflare (SPF, DKIM, DMARC records), then send.
4. **No lock-in.** The backend sends through **SMTP with `spring-boot-starter-mail`** (`spring.mail.host`, `username`, `password` as environment variables). Switching to Postmark or SES later is a settings change, not a code change.

**When to switch to Postmark ($15 a month):** if order emails start landing in spam, or the volume regularly passes about 80 a day.

**Setup checklist:**
- Domain: send from `orders@<your-domain>`, with replies to `sales@<your-domain>` (forwarded through Cloudflare Email Routing or a Zoho inbox).
- DNS in Cloudflare: Resend's SPF and DKIM records, plus `DMARC: v=DMARC1; p=quarantine; rua=mailto:dmarc@<your-domain>`.
- Render environment variables: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (the API key), `MAIL_FROM`.
- Emails are HTML with a plain-text version, the SpeedyFix Auto Sales logo, the company name, number and registered address in the footer, and no tracking pixels (that avoids a consent issue).
- Every send goes through a retry queue, so a short outage at the provider doesn't lose an order confirmation.

## 5. Business mailbox

| Option | Cost | Notes |
| --- | --- | --- |
| **Cloudflare Email Routing** | Free | Forwards `sales@`, `orders@` and so on to an existing inbox such as Gmail |
| **Zoho Mail free** | Free for up to 5 users, 5 GB each | A real inbox on your domain |
| Google Workspace or Microsoft 365 | Paid per user | If the business already uses them |

## 6. Address and postcode lookup at checkout

| Option | Cost | What you get |
| --- | --- | --- |
| **postcodes.io** | **Free**, no key | Checks a postcode exists and returns its town and area (ONS data). **No street addresses.** |
| Postio | 100 free lookups, then pay as you go | Address autocomplete (Royal Mail PAF) |
| EasyPostcodes | From about **2.3p** a request | PAF address lookup |
| AddressBrain | From £49 a month for 1,000 | PAF |
| findAddress.io | From £5 a month | Needs your own PAF licence for full data |
| getAddress.io, Ideal Postcodes | Paid (not verified here) | Popular choices; check their pricing |

**Recommendation:** use **postcodes.io** now to check the postcode and fill in the town, which costs nothing. The checkout already validates UK postcodes. Add paid address autocomplete only if typing errors cause delivery problems.

## 7. Couriers and shipping labels

| Option | Access | Cost | Notes |
| --- | --- | --- | --- |
| **Royal Mail Click & Drop** | Self-serve **API key** | Free software; you pay postage | Easiest to integrate; for up to about 1,000 parcels a day. Good for sensors and small parts. |
| DPD UK | API credentials **through your DPD account manager** (a few days to two weeks) | Contract rates | Premium tracked delivery; strict address validation |
| ShipStation API (ShipEngine) | Self-serve | Paid plans (not verified here) | One API for Royal Mail, Parcelforce, DPD, Evri and InPost, without separate carrier accounts |
| Parcel2Go | Booking website (no public API found) | Discounted rates | Good for manual booking at the start |
| **Pallet networks** (Palletways, Pall-Ex, Fortec) | Booked through a local depot or a broker | Quote per pallet | No public API found. Book manually and enter the tracking number in the admin's Orders screen. |

**Recommendation:** at launch, **book shipments manually** (Click & Drop for parcels, a pallet depot or broker for engines and gearboxes) and store the courier and tracking number on the order. The dispatch email sends the tracking link. Automate label printing through the Click & Drop API once there are several orders a day.

## 8. Reviews

| Option | Free | Paid | Notes |
| --- | --- | --- | --- |
| **Trustpilot** | **50 review invitations a month**, 1 widget, 1 user | Plus from about $259–299 a month (sources disagree) | API access needs a paid plan. Free is enough to start: send invitations after delivery. |
| **Google reviews** (via Google Business Profile) | Free to collect | Showing them on the site through the Places API uses Google's monthly free usage, then a per-request charge | Most visible in local search |
| Reviews.io | Free trial | From about $29–89 a month (sources vary) | Product reviews plus Google seller ratings |

**Recommendation:** use the **Trustpilot free plan plus Google Business Profile**. Show a Trustpilot widget, and add `AggregateRating` structured data only once there are real reviews.

## 9. Analytics and cookie consent

| Tool | Cost | Cookies? |
| --- | --- | --- |
| **Cloudflare Web Analytics** | Free | No, so no consent banner is needed for it |
| Google Analytics 4 | Free | Yes; load it only after consent (Consent Mode v2) |
| Cookie banner | Free tiers exist from several consent providers | Required under PECR before any non-essential cookies |

## 10. Spam and bot protection

**Cloudflare Turnstile** is free and replaces CAPTCHAs. Use it on the part-request, contact and checkout forms, and on the plate lookup (to protect paid lookups).

## 11. Google channels (all free)

| Tool | Use |
| --- | --- |
| Google Search Console | Indexing, sitemap, search queries |
| Bing Webmaster Tools | Same for Bing, which also feeds some AI search tools |
| **Google Merchant Center** | **Free product listings** in Google Shopping from a product feed (`/feeds/google.xml`) |
| Google Business Profile | Local search and the map listing, plus Google reviews |

## 12. WhatsApp

The WhatsApp click-to-chat link (`https://wa.me/44XXXXXXXXXX`) is free and needs no API. The site already has a WhatsApp setting (`site.whatsapp`). The paid WhatsApp Business Platform (Meta) is only needed for automated messages, and isn't needed at launch.

---

## Accounts to create now (all free)

1. **DVSA MOT History API**: register at https://documentation.history.mot.api.gov.uk/mot-history-api/register/ (up to 5 working days).
2. **Stripe**: create the account and complete business verification (test mode works immediately).
3. **Cloudflare**: add the domain, then turn on Email Routing, Turnstile and Web Analytics; create an R2 bucket.
4. **Resend**: verify the domain.
5. **Royal Mail Click & Drop**: create a business account.
6. **Trustpilot** (free business account), **Google Business Profile**, **Google Search Console** and **Merchant Center**.
7. **Sentry** and **Better Stack**.
8. Ask for quotes: **UK Vehicle Data** and **Vehicle Smart** (extended data with engine codes), plus a pallet network or broker.

Put every API key in Render's environment variables, never in the repo or in chat.

---

## Sources

- DVLA Vehicle Enquiry Service (official): https://developer-portal.driver-vehicle-licensing.api.gov.uk/apis/vehicle-enquiry-service/vehicle-enquiry-service-description.html
- DVSA MOT History API, documentation, rate limits and registration (official): https://documentation.history.mot.api.gov.uk/, https://documentation.history.mot.api.gov.uk/mot-history-api/rate-limits/, https://documentation.history.mot.api.gov.uk/mot-history-api/register/
- DVSA MOT API authentication (official): https://documentation.history.mot.api.gov.uk/mot-history-api/authentication/
- DVSA MOT API fields (third-party): https://paul-walsh.co.uk/dvsa-mot-history-api-what-it-returns/
- UK Vehicle Data pricing: https://ukvehicledata.co.uk/dvla-data-api
- Vehicle Smart API pricing: https://vehiclesmart.com/vehicle-smart-api.html
- One Auto API: https://www.oneautoapi.com/service/uk-vehicle-data-valuation/
- TecAlliance TecDoc: https://www.tecalliance.net/solutions/tecdoc
- Stripe UK pricing: https://stripe.com/gb/pricing
- Square UK fees (third-party): https://www.businessexpert.co.uk/payment-processing/square-review
- SumUp online fees: https://www.sumup.com/en-gb/compare/best-online-payment-systems/
- Postmark pricing (third-party): https://frontdeskreview.com/software/transactional-email/postmark/
- Resend pricing (third-party): https://automationatlas.io/answers/resend-pricing-explained-2026/
- Brevo free plan (third-party): https://dreamlit.ai/blog/brevo-review
- Amazon SES pricing: https://aws.amazon.com/ses/pricing/
- Zoho Mail free plan: https://blog.zoho.com/workplace/honouring-the-commitment-we-made-ten-years-ago.html
- Address lookup comparison: https://autopostcode.com/uk-address-api-comparison, https://postio.co.uk/products/address, https://easypostcodes.com/, https://www.addressbrain.com/pricing
- Royal Mail API shipping: https://www.royalmail.com/business/parcels/shipping-solutions/api-shipping
- UK shipping APIs (third-party): https://paul-walsh.co.uk/best-shipping-apis-uk-ecommerce/
- ShipStation API UK carriers: https://help.shipengine.com/hc/en-us/articles/19372351551899--ShipStation-API-Carriers-UK
- Trustpilot plans: https://no.business.trustpilot.com/plans, https://www.costbench.com/software/review-management/trustpilot/free-plan/
- Reviews.io pricing: https://www.capterra.com/p/201733/REVIEWS-io/
- Google Maps Platform pricing: https://developers.google.com/maps/billing-and-pricing/pricing
- Free hosting roundups (2026): https://snapdeploy.dev/state-of-free-hosting, https://docs.bswen.com/blog/2026-02-28-springboot-free-hosting/
- Free Postgres options: https://swyftstack.com/blog/free-postgresql-hosting, https://agentdeals.dev/vendor/aiven
