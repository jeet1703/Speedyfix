import { Link } from 'react-router-dom';
import type { Site } from '../api/types';

/*
 * Terms of sale and privacy notice. Written for this shop from UK consumer law (Consumer Contracts Regulations 2013,
 * Consumer Rights Act 2015), UK GDPR and the business's own eBay terms. DRAFTS: the business's legal adviser should
 * review them before launch (see PENDING.md). Facts still to confirm are marked [to confirm: …].
 */

const UPDATED = '1 November 2026';

/** fedex: parcels are priced by FedEx at checkout rather than the flat £9.95. */
export function TermsBody(site: Site, fedex = false) {
  return (
    <>
      <p className="info__note">Draft for review. Last updated {UPDATED}.</p>

      <h2 className="info__heading">1. Who we are</h2>
      <p>
        This website is run by {site.companyName}, a company registered in England and Wales (company no. {site.companyNumber},
        VAT no. {site.vatNumber}), of {site.address}. You can contact us on {site.phone} or at {site.email}.
      </p>
      <p>These terms apply when you buy from this website. Please read them before you order. If you buy as a business, section 12 applies too.</p>

      <h2 className="info__heading">2. Your order</h2>
      <ul className="info__list">
        <li>When you place an order, we reserve the parts and you pay through PayPal (with a PayPal account, or a debit or credit card).</li>
        <li>Our contract with you starts when your payment is confirmed. We’ll show the confirmation on screen, and you can check your order’s status at any time with its order number.</li>
        <li>An unpaid order is cancelled after 2 hours, and the parts go back on sale.</li>
        <li>Most of our parts are one-offs. If a part sells elsewhere before your order is confirmed, or a listing has an obvious error such as a wrong price, we’ll tell you and refund anything you’ve paid in full.</li>
      </ul>

      <h2 className="info__heading">3. Prices and payment</h2>
      <p>Prices are in pounds sterling and include VAT. Delivery is shown before you pay. Sale prices apply only while the listing shows them.</p>

      <h2 className="info__heading">4. Checking a part fits</h2>
      <p>
        Each listing shows the part number and the vehicles we know it fits. Our reg check is a guide based on the data we
        hold, so please compare the part number with the one on your old part. If you’re unsure, send us your registration
        or VIN before you buy and we’ll confirm compatibility. A part that doesn’t fit because the wrong part was ordered is
        covered by section 6, not by the warranty.
      </p>

      <h2 className="info__heading">5. Delivery</h2>
      <ul className="info__list">
        <li>UK delivery is usually within 7 working days (estimated) with {site.couriers}. Delivery times can vary with courier delays.</li>
        <li>{fedex ? 'Parcel delivery is priced by FedEx at checkout from your postcode, and the cheapest service is free on orders of £100 or more.' : 'Parcels cost £9.95, or are free on orders of £100 or more.'} {fedex ? 'Engines, gearboxes and other heavy parts go by FedEx Freight, priced at checkout; free delivery over £100 doesn’t apply to them. You can also choose manual delivery and pay a separate delivery quote.' : 'Engines, gearboxes and other heavy parts go by pallet at £79 per order.'} Someone needs to be available to receive a heavy delivery.</li>
        <li>We ship abroad by UK Pallets or FedEx at extra cost; allow 3 to 10 working days after dispatch. You are responsible for customs clearance and any import VAT or duties.</li>
        <li>The parts are your responsibility once they’re delivered. Please check them when they arrive and tell us within 48 hours if anything is damaged or missing. [to confirm: 48 hours]</li>
      </ul>

      <h2 className="info__heading">6. Changing your mind (your right to cancel)</h2>
      <p>
        If you’re buying as a consumer, you can cancel your order for any reason within <strong>14 days</strong> of the day
        you receive it, under the Consumer Contracts Regulations. As a goodwill extension, we accept returns within
        <strong> 30 days</strong> of delivery.
      </p>
      <ul className="info__list">
        <li>Tell us you want to cancel (by phone, WhatsApp or email, quoting your order number), then send the part back within 14 days of telling us.</li>
        <li>Unless the part is faulty or we sent the wrong one, you pay the cost of returning it, and the original delivery charge is deducted from your refund where it was a premium option. [to confirm]</li>
        <li>Parts must come back in the condition we supplied them. If a part has been fitted, used or tested beyond what’s needed to inspect it, we may reduce the refund to reflect its lower value, by up to 30%.</li>
        <li>We refund within 14 days of receiving the part back, to the way you paid.</li>
      </ul>

      <h2 className="info__heading">7. Faulty parts and our warranty</h2>
      <p>
        Your legal rights come first: under the Consumer Rights Act 2015, parts must be as described, fit for purpose and of
        satisfactory quality. Used parts will show wear consistent with their age and mileage. If a part is faulty, you can
        reject it for a full refund within 30 days of delivery, and after that you’re entitled to a repair or replacement,
        or in some cases a refund.
      </p>
      <p>
        On top of those rights, every part comes with our <strong>{site.warranty} warranty</strong> from the date of
        delivery. The warranty covers the part itself and doesn’t cover labour, fitting or other costs, or faults caused
        by incorrect fitting, accident or misuse. To claim, contact us with your order number before returning the part.
      </p>

      <h2 className="info__heading">8. Your account</h2>
      <p>You can shop without an account. If you sign in with Google, keep access to your Google account secure; you can ask us to delete your account at any time.</p>

      <h2 className="info__heading">9. Help chat</h2>
      <p>
        Our help chat answers from our stock and policies, and may use AI to word its replies. It can make mistakes, so
        please check the part’s page, or ask us, before you buy. These terms and the listing take priority over anything the chat says.
      </p>

      <h2 className="info__heading">10. Our responsibility to you</h2>
      <p>
        If we break these terms, we’re responsible for loss you suffer that is a foreseeable result. We don’t exclude
        liability where it would be unlawful to, such as for death or personal injury caused by our negligence, or for
        fraud. We aren’t responsible for losses that weren’t foreseeable, or for business losses where you buy as a consumer.
      </p>

      <h2 className="info__heading">11. Events outside our control</h2>
      <p>We aren’t responsible for delays caused by events outside our reasonable control, such as courier failures or severe weather. We’ll tell you, and you can cancel and get a refund for anything not yet delivered.</p>

      <h2 className="info__heading">12. Business customers</h2>
      <p>
        If you buy for a business (for example a garage), section 6 (the right to cancel) doesn’t apply, though our 30-day
        returns goodwill does. Our total liability to a business is limited to the price of the parts concerned, and we
        aren’t liable for loss of profit, business or downtime. [to confirm]
      </p>

      <h2 className="info__heading">13. Other things</h2>
      <p>
        These terms are governed by the law of England and Wales, and you can bring proceedings in the courts of England
        and Wales (or Scotland or Northern Ireland if you live there). We may update these terms; the version on this
        page when you order is the one that applies. See how we use your information in our <Link to="/privacy">privacy policy</Link>.
      </p>
    </>
  );
}

export function PrivacyBody(site: Site) {
  return (
    <>
      <p className="info__note">Draft for review. Last updated {UPDATED}.</p>

      <h2 className="info__heading">Who we are</h2>
      <p>
        {site.companyName} (company no. {site.companyNumber}), {site.address}, is responsible for the personal information
        this website collects (the “controller” under UK data protection law). Contact us about your information at
        {' '}{site.email} or {site.phone}. Our ICO registration number is [to confirm: ICO registration number].
      </p>

      <h2 className="info__heading">What we collect and why</h2>
      <ul className="info__list">
        <li><strong>Orders:</strong> your name, email, phone number, delivery address and any delivery notes, so we can process, deliver and support your order (to perform our contract with you). We keep order records for 6 years for tax and accounting (a legal obligation).</li>
        <li><strong>Payments:</strong> PayPal processes your payment. We never see or store your card details; we keep PayPal’s payment reference with your order.</li>
        <li><strong>Your account (optional):</strong> if you sign in with Google, we receive your name, email address, profile photo and Google account ID, and we keep your recent searches so you can repeat them. We keep these until you ask us to delete your account.</li>
        <li><strong>Part requests:</strong> your name, contact details, the part you need, any photos you send, and your registration in masked form (for example AB12 ***) with the vehicle it identifies, so we can find the part and reply. [to confirm: how long they’re kept]</li>
        <li><strong>Reg checks:</strong> when you enter a registration, we send it to the DVSA (the government’s vehicle records service) to identify the vehicle. We don’t store the registration; we keep only the vehicle model and whether we had matching parts, for up to 40 days, to understand demand.</li>
        <li><strong>Help chat:</strong> your messages are used to answer you. If AI is switched on, the wording of answers comes from Google’s Gemini service. We remove email addresses, order numbers and registrations before anything is sent to Google, and order tracking never goes to Google. We don’t keep chat conversations.</li>
        <li><strong>Baskets:</strong> what’s in your basket, kept for 7 days after it was last changed.</li>
        <li><strong>Technical information:</strong> our servers record basic request details (such as your IP address) for security and to prevent abuse, for a short time.</li>
      </ul>
      <p>We use the information we hold to run the shop, answer your questions and meet our legal duties. We don’t sell it, and we don’t send marketing emails.</p>

      <h2 className="info__heading">Who we share it with</h2>
      <p>We use trusted providers to run the shop. They act on our instructions and protect your information:</p>
      <ul className="info__list">
        <li><strong>Render</strong> (website and database hosting, servers in Frankfurt, Germany).</li>
        <li><strong>Cloudflare</strong> (storage for listing photos).</li>
        <li><strong>PayPal</strong> (payments).</li>
        <li><strong>Google</strong> (Sign in with Google, and AI wording in the help chat).</li>
        <li><strong>DVSA</strong> (vehicle lookups).</li>
        <li><strong>Couriers</strong> ({site.couriers}): your name, address and phone number, to deliver your order.</li>
      </ul>
      <p>Some of these providers are based in, or may access data from, the United States. Where information leaves the UK, it’s protected by the UK’s data adequacy rules or standard contractual safeguards.</p>

      <h2 className="info__heading">Cookies and browser storage</h2>
      <p>
        We don’t use advertising or tracking cookies. The shop keeps your basket, wishlist, recent searches and sign-in in
        your browser’s storage so they’re there when you come back. Google may set a cookie when you use Sign in with
        Google, and PayPal sets cookies while you pay; see their own privacy notices.
      </p>

      <h2 className="info__heading">Your rights</h2>
      <p>
        You can ask for a copy of the information we hold about you, ask us to correct or delete it, object to how we use
        it, or ask us to restrict its use or send it to you in a portable form. Contact us at {site.email}. We’ll reply within
        one month. Some records (such as orders) must be kept for tax purposes even if you ask us to delete them.
      </p>
      <p>
        If you’re unhappy with how we’ve used your information, please tell us first. You can also complain to the
        Information Commissioner’s Office (ICO) at ico.org.uk or on 0303 123 1113.
      </p>

      <h2 className="info__heading">Changes</h2>
      <p>We’ll update this notice when how we use information changes. See also our <Link to="/terms">terms of sale</Link>.</p>
    </>
  );
}
