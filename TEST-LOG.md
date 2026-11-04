# Sanity test: whole website, shopper and admin

## Second run: 5 November 2026

On the development computer (local backend and database, FedEx production keys, PayPal sandbox, sign-in to buy on, emails on), plus read-only checks of the live site. Test orders, accounts and baskets are in the local database only.

| Area | What was checked | Result |
| --- | --- | --- |
| API touchpoints (63 checks) | Shop pages, search, part page, unknown part; reg lookup; basket add, change, over-stock, unknown basket; delivery to the UK, India, USA and Ireland; half postcodes and sanctioned countries refused; guest checkout blocked; order lookup; invoice PDF (not cached); help chat; account and password reset; part request; every admin page; bad staff token; CORS | ✅ 63 of 63 |
| Card payment, India | Indian sandbox card at checkout: paid, no UK VAT, invoice "VAT 0% (export)" | ✅ |
| Card payment, UK | UK sandbox card: paid, "Order confirmed", invoice numbered | ✅ |
| Unpaid order | Banner says it isn't confirmed; the card form on the order page pays it | ✅ |
| Admin orders | Cancel puts stock back; a cancelled order can't be paid; dispatch needs a courier and saves the tracking number | ✅ |
| Emails | Order confirmed (with invoice) and cancelled emails sent to real addresses; refused for example.com test addresses, as Resend should | ✅ |
| Phone width (390 px) | Home, category, part, basket, checkout, order, account, request, about, contact, delivery, terms, privacy, wishlist, 404, admin sign-in: nothing scrolls sideways | ✅ |
| Live site | Server up after each deploy; FedEx prices; worldwide wording in the help chat; card form and PayPal at checkout | ✅ |

### Problems found and fixed

| Problem | Fix |
| --- | --- |
| FedEx arrival dates assumed the parcel left right now, so a 9:37 pm order showed next-day arrival | The site tells FedEx the real dispatch day (today before 12:00 UK on a weekday, else the next weekday; `FEDEX_DISPATCH_CUTOFF`) |
| An engine and a small part in one basket got no FedEx price (FedEx won't mix freight and parcels) | The small parts go on the engine's pallet |
| A refused card at checkout left an unpaid order without telling the shopper | The message says the order is saved and reserved, so trying again won't order twice |

### Still open

| Gap | Why it matters | What fixes it |
| --- | --- | --- |
| Every page except the home page answers "404" on GitHub Pages (people still see it) | Google won't index part pages | Move to speedyfixautosales.com on a host with proper page routing (Cloudflare Pages, Netlify) |
| Render: email sign-in and sign-in to buy are off; `MAIL_STAFF_ORDERS` not confirmed | Staff don't get new-order emails; anyone can check out as a guest | Set `ACCOUNT_EMAIL_SIGN_IN`, `ORDERS_REQUIRE_SIGN_IN`, `MAIL_STAFF_ORDERS` |
| eBay compatibility import needs the seller's token | Parts show "Likely fits" rather than "Fits" | `EBAY_DEV_ID` and `EBAY_USER_TOKEN` in Render, then press the import button |
| Live PayPal | Real payments | Switch on Advanced Credit and Debit Card Payments for the live PayPal app, then live keys |
| FedEx shipments are booked by hand | Staff time; export paperwork | FedEx Ship API (labels, commercial invoice) |
| No invoices list, VAT export or credit notes in the admin | VAT returns and refunds | Build the Invoices page and credit notes |
| Home page's main heading is a slide title ("Turbochargers") | Search engines read it as the page's subject | One heading with the shop's name |

## First run: 4 November 2026

On the development computer: the local backend (local database, FedEx production keys, PayPal sandbox, email sign-in on, emails off so test codes went to the server log) and the local frontend in Chrome. All test data is in the local database only.

## Results

| Area | What was checked | Result |
| --- | --- | --- |
| All shop pages | Home, products, a category, search, a part, a missing part, About, Contact, Delivery, FAQs, Terms, Privacy, Request a part, Wishlist, Basket, Checkout, Account, an unknown order, an unknown page: each loads with the right title and no script errors | ✅ |
| Phone width (390 px) | Home, category, part, basket, order, account and request pages: nothing scrolls sideways | ✅ |
| Part page | Price with and without VAT; FedEx delivery wording; add to basket opens the basket drawer | ✅ |
| Basket | Remove a line; delivery shows "At checkout"; a mixed engine and parcel basket goes as one £79 pallet | ✅ |
| Sign-in to buy | Checkout asks a signed-out shopper to sign in, keeps the basket, and comes back to checkout afterwards | ✅ |
| Email sign-up | Code emailed (to the log), account made only with the code | ✅ |
| Forgot password | Code emailed, new password set, signed straight in; a second request the same day sends nothing (automated test) | ✅ |
| Checkout | Name and email filled from the account; real FedEx prices and arrival dates for the postcode; Manual delivery option; totals and VAT (1/6 of the total) change with the choice | ✅ |
| Manual delivery order | Placed with no delivery charge; both the shopper's and the staff's order pages explain it | ✅ |
| PayPal (sandbox) | The site creates the PayPal payment and PayPal's card form opens | ✅ |
| PayPal card payment | PayPal's sandbox declined the generic test card 4111 1111 1111 1111 | ⚠️ Blocked: needs a sandbox card from developer.paypal.com (Testing tools → Credit card generator) or a sandbox buyer login |
| Mark paid (admin) | Order moves to Awaiting dispatch; invoice INV-001005 numbered; invoice card shows; says email is off | ✅ |
| Dispatch (admin) | Courier and tracking saved; shown in My orders | ✅ |
| Invoice PDF | Downloads with the right file name; has the invoice and order numbers, "VAT 20% (16.67% of price)", totals, buyer and delivery | ✅ |
| Cancel (admin) | Parts back in stock; shopper's page says cancelled, payment not taken | ✅ |
| Help chat | Delivery question answered with checkout pricing; finds the ignition coil by vehicle | ✅ |
| Order lookup | Works with the order's email (any case); a wrong email gets "couldn't find" | ✅ |
| Reg lookup | Demo plate AB12 CDE found | ✅ |
| Part-number search | 273012B100 finds the coil | ✅ |
| Request a part | Sent (PR-1003), vehicle identified from the reg, shows in the admin | ✅ |
| All admin pages | Overview, Orders, an order, Listings, Edit listing, Create listing, Customers, Part requests, Vehicle lookup, Settings | ✅ |
| Edit listing (admin) | Packed weight saved on an eBay-imported listing; its 5 eBay photos still show in the shop | ✅ after fix 2 |
| Order emails | Invoice email sent through Resend to three addresses on 3 November (tested separately) | ✅ |

## Problems found and fixed

1. **The order page didn't show the courier and tracking number after dispatch**, although the invoice email says it will. My orders showed them. Fixed: the order page now has an "On its way" box with the courier, date and tracking number.
2. **Staff couldn't save changes to eBay-imported listings.** The listing editor only counted photos uploaded in the admin, so listings with only their eBay photos showed "Save changes (add a photo first)", greyed out. Fixed: the eBay photos count, and the editor shows one with a note.
3. **The PayPal box on the order page was squeezed to about 190 px**, so the card button had no label and the card form was cramped. Fixed: it uses the column's width (up to 480 px) and shows "Pay with PayPal" and "Debit or Credit Card".
4. **Two admin notes said data is "held in memory" and lost on a redeploy** (Part requests and Settings). It's kept in the database. Fixed.
5. **The basket said FedEx delivery is free over £100**; only the cheapest service is. Fixed.

## Still open

| # | Problem | Priority | Note |
| --- | --- | --- | --- |
| 1 | A full PayPal card payment hasn't been tested | 🔴 before launch | Use a PayPal sandbox card or sandbox buyer login, then a small live payment and refund after launch |
| 2 | Google sign-in isn't registered for the live address | 🔴 before launch | Without it nobody can buy (checkout needs sign-in), unless email sign-in is switched on or `ORDERS_REQUIRE_SIGN_IN=false` |
| 3 | Email sign-ups waiting for their code are kept in memory | 🟠 before switching email sign-in on | A restart loses them; the shopper signs up again. Forgot password is stored in the database |
| 4 | The missing-part page says "No part with id not-a-real-part." | 🟢 | Wording only |
| 5 | Shopper pages show times in the visitor's own time zone, the admin in UK time | 🟢 | Only noticeable from abroad |

Browser autofill put a saved address into the sign-up and reset forms during testing; that's the browser, not the site.
