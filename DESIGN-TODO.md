# SpeedyFix Auto Sales v2: what is left to design

Status as of 22 October 2026, based on the routes in `frontend/src/App.tsx`, the placeholder text in the code, and the links in the header, top bar and footer.

## 1. Pages with no design yet

These links already appear in the header, top bar or footer, but they all open the shared `ComingSoon` page (`frontend/src/pages/ComingSoon.tsx`). It shows only a title, one placeholder sentence and two buttons.

| Page | Route | Linked from | What is needed |
| --- | --- | --- | --- |
| My account | `/account` | Top bar (twice), header icon, footer | Sign in, create account, forgotten password, order history, saved vehicles, addresses. Planned for the next build phase. |
| Trade accounts | `/trade` | Top bar, footer | Trade application form, trade pricing explanation, trade sign-in. Next build phase. |
| About us | `/about` | Header, footer ("Read more") | Layout for company story, workshop photos, team or facilities. Needs client copy. |
| Returns and warranty | `/returns` | Header, footer | Long-form policy layout, plus a returns request flow if the client wants one. |
| Delivery partners | `/delivery` | Footer | Courier names, pallet vs parcel rules (£79 pallet; £9.95 parcel, free from £100), dispatch times. |
| FAQs | `/faq` | Footer | Accordion layout grouped by topic (fitment, delivery, returns, payment). |
| Terms and conditions | `/terms` | Footer | Legal text layout. Copy must come from the client's legal adviser. |
| Privacy policy | `/privacy` | Footer | Legal text layout. Copy must come from the client's legal adviser. |
| Page not found | any unknown URL | — | Currently reuses the `ComingSoon` page with "Page not found". A dedicated 404 design would help, for example with the search box and reg finder. |

The long-form pages (About, Returns, Delivery, FAQ, Terms, Privacy) could share one content-page template: page title, side navigation between policy pages, readable text column.

## 2. Designed, but waiting on real content or assets

| Area | Where | What is missing |
| --- | --- | --- |
| Product and category photos | `backend/.../catalog/Images.java` | All images are Unsplash samples. They need the client's own photos of each part, shot consistently (same background, angle and crop). |
| Workshop video | `site.video-url` in `application.yml`, `VideoSection.tsx` | Empty. Needs a YouTube embed URL from the client. |
| Reviews | `Reviews.tsx` | Shows three marked placeholders. A review platform needs to be chosen and connected. |
| Business details | `site.*` in `application.yml` | Phone, WhatsApp, email, company name, address, warranty period and dispatch location are bracketed placeholders. |
| Delivery feature tile | `CatalogRepository.java` | "[Courier names]" placeholder. |
| Product page returns line | `ProductPage.tsx` | "[Returns policy from the client]" placeholder. |

## 3. Flows and states not yet designed

**Payment.** Checkout has a "Payment" section that says payment isn't connected, and the order-received page says no payment was taken. Once a provider is chosen, the card / Apple Pay / Google Pay step needs designing, along with payment failure, retry and declined states.

**Payment logos in the footer.** The footer lists Visa, Mastercard, Apple Pay and Google Pay, but no payment is taken yet. Remove or hide them until payment is live, so the site does not suggest methods it does not accept.

**Order confirmation email.** Customers get no emails yet. Order confirmation, dispatch notice and part-request acknowledgement emails all need a design.

**Account-dependent states.** Once accounts exist, the header, checkout (signed-in vs guest), wishlist (saved to an account vs browser only) and saved vehicle all need signed-in variants.

**Loading and cold-start states.** On the Render free plan the API can take 30–60 seconds to wake up. The homepage and shop need a clear loading state (skeleton cards, hero placeholder) so the page does not look broken during that wait.

## 4. Site-wide items

- **Favicon and app icons**: none in `frontend/index.html`.
- **Social sharing previews**: no Open Graph or Twitter card tags or share image.
- **Cookie notice**: not needed while the site uses only browser storage for basket, wishlist and vehicle. It will be needed once analytics, ads or a review widget that sets cookies is added. Flag for the client's legal review.
- **Brand guidelines**: the colours (orange `#fe7232`, ink `#14171a`, plate yellow `#f5c518`, euro blue `#1f4fb5`) and fonts (Chakra Petch, Sora, JetBrains Mono) are only defined in `frontend/src/styles/base.css`. A one-page style reference would help keep new pages consistent.
- **Dark mode**: not designed. The site is light-only (`color-scheme: light`). This is optional.

## Suggested order

1. Content-page template, then About, Returns, Delivery, FAQ, Terms and Privacy as the client supplies copy.
2. Dedicated 404 page and loading states.
3. Favicon, share image, and the decision on the footer payment logos.
4. Payment step, once the provider is chosen.
5. Accounts and trade accounts (next build phase).
6. Transactional emails.
