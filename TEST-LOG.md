# Sanity test: whole website, shopper and admin

**4 November 2026**, on the development computer: the local backend (local database, FedEx production keys, PayPal sandbox, email sign-in on, emails off so test codes went to the server log) and the local frontend in Chrome. All test data is in the local database only.

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
