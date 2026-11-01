# Live site sanity check: 1 November 2026

| | |
| --- | --- |
| **When** | 1 November 2026, 06:55 to 06:56 IST (01:25 UTC); follow-up checks to 07:05 IST |
| **Shop** | https://jeet1703.github.io/Speedyfix/ (GitHub Pages, development hosting) |
| **Backend** | https://speedyfix-backend.onrender.com (Render, Frankfurt) |
| **Version** | Backend `59a7e9d` (photo fix) on top of `23127bd` (database, eBay sync, PayPal, help chat) |
| **Settings** | All 22 Render settings in place; PayPal on **sandbox** keys (test money only) |
| **How** | Automated checks from outside, as a shopper (pages, API, photos, search, basket, chat, security, SEO). Nothing ordered or submitted; one empty test basket created (expires after 7 days). Visual, phone and keyboard checks not included (see the end). |

## Results

| Area | Check | Result | |
| --- | --- | --- | --- |
| Shop | Homepage loads | HTTP 200 | ✅ |
| Shop | Latest frontend deployed (help chat, sales, PayPal) | Yes | ✅ |
| API | Responds | 0.3 s (awake) | ✅ |
| API | Business details | +44 7707 704977, eBay.speedyfix@gmail.com, couriers listed | ✅ |
| API | Real DVSA plate lookups | On (not demo plates) | ✅ |
| API | AI chat | On | ✅ |
| API | PayPal | On, **sandbox** | ✅ (test money) |
| API | Compression | gzip | ✅ |
| Photos | Homepage category images | 4 of 4 load | ✅ |
| Photos | Products menu previews | 6 of 6 sampled load | ✅ |
| Catalogue | Live parts | 1,275 parts, 21 categories | ✅ |
| Photos | Product card photos | **0 of 96 sampled; all "photo coming soon"** | ❌ Issue 1 |
| Product page | Gallery, eBay description, item specifics | **1 placeholder photo, no description, no specifics** | ❌ Issue 1 |
| Search | "turbo" | 6 results | ✅ |
| Reg check | Unknown plate | Handled (404, "not found" message) | ✅ |
| Basket | Add to basket from the shop's address | 1 item, £34.94 with delivery | ✅ |
| Help chat | AI answer ("gearbox for a Nissan Juke?") | 1.8 s, named a part, 2 part cards | ✅ |
| Help chat | Order lookup with wrong details | Refused (404), reveals nothing | ✅ |
| Security | Admin without login | 401 (orders, eBay sync) | ✅ |
| Security | Requests from another website | 403 | ✅ |
| SEO | Sitemap | 1,304 addresses | ✅ |
| SEO | Product pages' status on GitHub Pages | 404 (page shows, search engines don't index) | ⚠️ Issue 4 |
| SEO | robots.txt | Only at /Speedyfix/robots.txt, not the domain root | ⚠️ Issue 4 |
| eBay sync | Ran automatically | Yes: 37 listings added, live parts 1,262 → 1,275 | ✅ (but Issue 2) |

## Issues

### 1. No product photos or eBay descriptions on the live site ❌
- **Seen:** every product card shows "photo coming soon"; product pages have no gallery, eBay description or item specifics.
- **Cause:** the photo records and descriptions were imported into the development database; copying them to the live database is a separate step. It was prepared and tested (dry run on live: 1,379 parts get photos, 1,380 descriptions, nothing else changed) but the write to the live database needs the owner's approval, which hasn't been given yet.
- **Fix:** run `copy-to-live.ps1 -Apply` (approve the prompt). The photos themselves are already in R2.
- **Status:** waiting for approval.

### 2. The 37 listings the eBay sync added have no photos ❌ → fixed in code
- **Seen:** the import history on live shows "eBay sync: 37 created, 0 photos", each with "Couldn't save photo …".
- **Cause:** the first sync ran a few minutes after deploy `23127bd`, before the photo-storage fix `59a7e9d`. Saving a photo also wrote a local copy, and on Render the app can't write that folder, so every photo failed. The sync only handles a new listing once, so these would never get photos.
- **Fix:** (a) `59a7e9d` (deployed): photos go to and come from R2 only on the live server. (b) New, not yet deployed: each sync also retries photos for up to 20 live eBay parts that have none, so the 37 fill in over two syncs and any future failure heals itself. The fix also stops a photos-only run from recording eBay category guesses.
- **Status:** fix (b) tested (78 tests pass), waiting to be pushed.

### 3. The orange loading bar runs across the whole site while the chat thinks ⚠️ → fixed in code
- **Seen:** reported by the developer: the top-of-page loading bar moves while the help chat is answering.
- **Cause:** the chat's requests used the same request helper as page loads, which drives the bar.
- **Fix:** the chat's requests (AI, search, reg check, order lookup) no longer move the bar; the chat shows its own thinking dots.
- **Status:** built, waiting to be pushed.

### 4. Search engines can't index product pages on GitHub Pages ⚠️ (known)
- **Seen:** product and category addresses return HTTP 404 (the page still shows, from GitHub Pages' fallback); robots.txt isn't at the domain root.
- **Cause:** GitHub Pages hosting, which is for development only.
- **Fix:** move the shop to a Render Static Site with a rewrite rule, on speedyfixautosales.com (PENDING.md, section 3). No action before then.

### 5. "Sync with eBay" disabled on the development computer ⚠️ → fixed
- **Seen:** reported by the developer: the admin button said "Not set up on this server".
- **Cause:** the local start-up script didn't load the eBay keys (on purpose, while the photo import ran).
- **Fix:** the local script now loads them; the button works locally. Not a live-site issue.

## Not covered by this check
- Visual layout, phone and tablet, zoom to 200%, keyboard-only use (needs a browser; see PENDING.md, section 6).
- A complete purchase with PayPal (sandbox): needs a sandbox buyer login in the browser.
- Google sign-in (waiting for the client to register the site's addresses).
- Emails (not built yet; PENDING.md, section 1).

## Next check
After issues 1 to 3 are closed: repeat this check, then a sandbox PayPal purchase on a phone.
