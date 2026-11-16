import { Link } from 'react-router-dom';
import type { Site } from '../api/types';
import { reopenCookieNotice } from '../state/ads';

/*
 * Terms of sale and privacy notice. Written for this shop from UK consumer law (Consumer Contracts Regulations 2013,
 * Consumer Rights Act 2015), UK GDPR and the business's own eBay terms. They are in force on the site but haven't been
 * checked by a lawyer: the business's legal adviser should still review them (see PENDING.md).
 */

const UPDATED = '16 November 2026';

/** Shown at checkout and saved with what the shopper agreed to; change it whenever the terms change. */
export const TERMS_VERSION = UPDATED;

/** fedex: parcels are priced by FedEx at checkout rather than the flat £9.95. */
export function TermsBody(site: Site, fedex = false) {
  return (
    <>
      <p className="info__note">Last updated {UPDATED}.</p>

      <h2 className="info__heading">1. Who We Are</h2>
      <p>
        This website is run by {site.companyName}, a company registered in England and Wales (company no. {site.companyNumber},
        VAT no. {site.vatNumber}). Our registered office is {site.address}. You can contact us on {site.phone} or at {site.email}.
      </p>
      <p>These terms apply when you buy from this website. Please read them before you order. You buy as a consumer when you buy for yourself and not for a trade or business. If you buy for a business, section 11 applies too.</p>

      <h2 className="info__heading">2. Your Order</h2>
      <ul className="info__list">
        <li>When you place an order, we reserve the parts and you pay through PayPal (with a PayPal account, or a debit or credit card).</li>
        <li>Before you pay, you tick a box to accept these terms. Our contract with you starts when your payment is confirmed. We’ll show the confirmation on screen and email it to you with your VAT invoice, and you can check your order’s status at any time with its order number.</li>
        <li>An unpaid order is cancelled after 2 hours, and the parts go back on sale.</li>
        <li>Most of our parts are one-offs. If a part sells elsewhere before your order is confirmed, or a listing has an obvious error such as a wrong price, we’ll tell you and refund anything you’ve paid in full.</li>
      </ul>

      <h2 className="info__heading">3. Prices and Payment</h2>
      <p>Prices are in pounds sterling and include VAT. Delivery is shown before you pay. Sale prices apply only while the listing shows them.</p>

      <h2 className="info__heading">4. Checking a Part Fits</h2>
      <p>
        Each listing shows the part number and the vehicles we know it fits. Our reg check is a guide based on the data we
        hold, so please compare the part number with the one on your old part. If you’re unsure, send us your registration
        or VIN before you buy and we’ll confirm compatibility. If you order the wrong part, you can still send it
        back under section 6 (changing your mind); that isn’t a warranty claim.
      </p>
      <p>
        We strongly recommend having parts fitted by a professional. Some parts don’t come with fitting instructions. If
        you tell us a part doesn’t fit your vehicle, a note from the garage or fitter who checked it helps us sort it out quickly.
      </p>

      <h2 className="info__heading">5. Delivery</h2>
      <ul className="info__list">
        <li>UK delivery is usually within 7 working days (estimated) with {site.couriers}. Delivery times can vary with courier delays.</li>
        <li>{fedex ? 'Parcel delivery is priced by FedEx at checkout from your postcode, and in the UK the cheapest service is free when every part in the order weighs 5 kg or less.' : 'Parcels cost £9.95, or are free on orders of £100 or more.'} {fedex ? 'Engines, gearboxes and other heavy parts go by FedEx Freight, priced at checkout. UK delivery never costs more than £100, however heavy the order; delivery abroad is priced by FedEx at checkout. You can also choose manual delivery and pay a separate delivery quote.' : 'Engines, gearboxes and other heavy parts go by pallet at £79 per order.'} Someone needs to be available to receive a heavy delivery.</li>
        <li>We deliver worldwide. Orders delivered outside the UK are exports and are charged without UK VAT. You are responsible for customs clearance and any import VAT, duty or taxes in your country. Allow 3 to 10 working days after dispatch.</li>
        <li>The parts are your responsibility once they’re delivered. Please check them when they arrive and tell us as soon as you can, ideally within 24 hours and with photos, if anything is damaged, missing or not what you ordered: it helps us claim from the courier. Telling us later doesn’t take away your rights in section 6.</li>
      </ul>

      <h2 className="info__heading">6. Returns, Warranty, Refunds and Cancellation</h2>
      {ReturnPolicyBody(site)}

      <h2 className="info__heading">7. Your Account</h2>
      <p>You can shop without an account. If you sign in with Google, keep access to your Google account secure; you can ask us to delete your account at any time.</p>

      <h2 className="info__heading">8. Help Chat</h2>
      <p>
        Our help chat answers from our stock and policies, and may use AI to word its replies. It can make mistakes, so
        please check the part’s page, or ask us, before you buy. These terms and the listing take priority over anything the chat says.
      </p>

      <h2 className="info__heading">9. Our Responsibility to You</h2>
      <p>
        If we break these terms, we’re responsible for loss you suffer that is a foreseeable result. We don’t exclude
        liability where it would be unlawful to, such as for death or personal injury caused by our negligence, or for
        fraud. We aren’t responsible for losses that weren’t foreseeable, or for business losses where you buy as a consumer.
      </p>

      <h2 className="info__heading">10. Events Outside Our Control</h2>
      <p>We aren’t responsible for delays caused by events outside our reasonable control, such as courier failures or severe weather. We’ll tell you, and you can cancel and get a refund for anything not yet delivered.</p>

      <h2 className="info__heading">11. Business Customers</h2>

      <p>
        If you buy for a business (for example a garage), the trade returns policy in section 6 applies in place of the
        consumer rights there. Our total liability to a business is limited to the price of the parts concerned, and we
        aren’t liable for loss of profit, business or downtime.
      </p>

      <h2 className="info__heading">12. Complaints</h2>
      <p>
        If something has gone wrong, tell us at {site.email} or on {site.phone}, with your order number. We’ll reply within
        5 working days and try to put it right. If we can’t agree, you can get free advice from Citizens Advice
        (citizensadvice.org.uk, 0808 223 1133), and you keep your right to go to court.
      </p>

      <h2 className="info__heading">13. Other Things</h2>
      <p>
        These terms are governed by the law of England and Wales, and you can bring proceedings in the courts of England
        and Wales (or Scotland or Northern Ireland if you live there). We may update these terms; the version on this
        page when you order is the one that applies. See how we use your information in our <Link to="/privacy">privacy policy</Link>.
      </p>
    </>
  );
}

/**
 * Returns, warranty, refunds and cancellation. Shown in the terms (section 6) and on the Delivery and returns page.
 * Consumers get what UK law gives them (14 days to change their mind, and their rights when a part is faulty), which
 * a shop can't take away. The business's own stricter policy, as supplied on 5 November 2026, applies to trade buyers.
 */
export function ReturnPolicyBody(site: Site) {
  return (
    <>
      <p>
        If you buy for yourself (as a consumer), the first five parts below apply to you. If you buy for a business, such
        as a garage, the trade policy further down applies.
      </p>

      <h3 className="info__heading">Changed Your Mind? You Have 14 Days</h3>
      <ul className="info__list">
        <li>You can cancel your order for any reason, from the moment you place it until 14 days after the day you (or someone you name) receive the last part in it. You don’t have to say why.</li>
        <li>To cancel, tell us clearly before the 14 days end: email {site.email}, call {site.phone}, or send us the cancellation form below. Give your order number if you can.</li>
        <li>Send the parts back, or hand them over at our store, within 14 days of telling us. You pay the cost of sending them back. For an engine, gearbox or other part that came on a pallet, that is usually about what delivery cost you (up to about £100 within the UK).</li>
        <li>We refund the price of the parts and the delivery charge you paid (at the price of our cheapest delivery, if you chose a dearer one), with no restocking fee. We pay it the way you paid, within 14 days of getting the parts back or of you showing us you’ve sent them.</li>
        <li>You can open the packaging and look at a part as you would in a shop. If it has been fitted, used or damaged while you had it, we may reduce the refund by the value it has lost.</li>
        <li>If you cancel before we’ve sent your order, we refund everything you paid.</li>
      </ul>

      <h3 className="info__heading">Cancellation Form</h3>
      <p>You can copy this into an email or a letter. You don’t have to use it.</p>
      <p className="info__form">
        To {site.companyName}, {site.address}, {site.email}:<br />
        I/We [*] hereby give notice that I/We [*] cancel my/our [*] contract of sale of the following goods [*]:<br />
        Order number:<br />
        Ordered on [*] / received on [*]:<br />
        Name of consumer(s):<br />
        Address of consumer(s):<br />
        Signature of consumer(s) (only if this form is sent on paper):<br />
        Date:<br />
        [*] Delete as appropriate
      </p>

      <h3 className="info__heading">If a Part Is Faulty or Not as Described</h3>
      <p>
        Under the Consumer Rights Act 2015, every part we sell must be as described, fit for its purpose and of
        satisfactory quality, taking into account its age and price where it’s used. This covers every part, including
        electrical parts and new genuine parts.
      </p>
      <ul className="info__list">
        <li><strong>Within 30 days of delivery:</strong> you can reject a faulty or wrongly described part and get a full refund.</li>
        <li><strong>After 30 days:</strong> we’ll repair or replace the part. If that isn’t possible or doesn’t work, you can have a refund or keep the part at a reduced price. In the first 6 months a fault is taken to have been there when the part arrived, unless we can show otherwise.</li>
        <li>We pay the cost of getting a faulty or wrongly described part back to us, and we refund the delivery charge you paid.</li>
        <li>Contact us first at {site.email} or on {site.phone} with your order number and, if you can, photos or a note from your garage, so we can arrange the return. Please don’t send a part back before we’ve agreed how.</li>
      </ul>
      <p>Nothing on this website takes away these legal rights. For free advice about them, contact Citizens Advice (citizensadvice.org.uk, 0808 223 1133).</p>

      <h3 className="info__heading">Warranty</h3>
      <ul className="info__list">
        <li>Aftermarket and refurbished parts come with a 3 months replacement warranty from delivery. For more information see the part’s description.</li>
        <li>The warranty covers the part only. It doesn’t cover labour, towing or rental costs.</li>
        <li>The warranty is on top of your legal rights above, which apply to every part whether or not it has a warranty.</li>
      </ul>

      <h3 className="info__heading">Refunds</h3>
      <p>
        We refund to the payment method you used, within 14 days as set out above. Your bank or card provider may take a
        few more working days to show it. If a refund hasn’t arrived when you expected it, email {site.email} and we’ll
        look into it straight away.
      </p>

      <h3 className="info__heading">Trade Buyers (Business Purchases)</h3>
      <p>
        If you buy for a business, the consumer rights above don’t apply and this policy applies instead. We have a 30
        days return policy and accept returns only in the following conditions.
      </p>
      <ul className="info__list">
        <li>If you have received an item different from the ordered part number, or not matching the pictures on the listing.</li>
        <li>If the item has quality problems or is damaged: send us photos and contact us within 24 hours of receiving it.</li>
        <li>If the item was bought by mistake and is still in good condition for reselling (unopened, in its original packaging). In this case the buyer is responsible for all related shipping costs.</li>
        <li>A non-refundable restocking fee of 20% is charged for each returned order or item.</li>
        <li>All presumed defective or wrong items need an authorisation before returning. Any package returned without authorisation will be refused.</li>
        <li>No returns or guarantees unless the item is non-functional in relation to its description.</li>
        <li>No returns or exchanges on opened packages or installed parts.</li>
        <li>We highly recommend professional installation for all products; installation instructions are not included with some. Professional proof is required if an item doesn’t fit your vehicle.</li>
        <li>No returns or warranty for new genuine parts.</li>
        <li>
          Electrical parts have no warranty and are not returnable. Electrical and electronic parts are easily damaged by
          improper installation or testing, so please give accurate vehicle information when you buy and be sure the part
          you’re replacing is the cause of the fault. This covers switches (all types), sensors (all types), regulators,
          amplifiers, temperature sensors, motor resistors, EGR sensors and valves, ignition modules, computers, cruise
          control relays, distributor parts, door lock actuators, lighting connectors, solenoids, relays and power window switches.
        </li>
        <li>Refunds for returns or cores are normally credited within 10 days of the return reaching our warehouse, less the original shipping fee and the restocking fee. The buyer pays return shipping.</li>
        <li>Orders go to the warehouse as soon as payment is confirmed, so a trade order can’t be cancelled once it has been paid.</li>
      </ul>
    </>
  );
}

export function PrivacyBody(site: Site) {
  return (
    <>
      <p className="info__note">Last updated {UPDATED}.</p>

      <h2 className="info__heading">Who We Are</h2>
      <p>
        {site.companyName} (company no. {site.companyNumber}), {site.address}, is responsible for the personal information
        this website collects (the “controller” under UK data protection law). Contact us about your information at
        {' '}{site.email} or {site.phone}. We’re registered with the Information Commissioner’s Office (ICO){site.icoNumber ? `, registration number ${site.icoNumber}` : ''}.
      </p>

      <h2 className="info__heading">What We Collect and Why</h2>
      <ul className="info__list">
        <li><strong>Orders:</strong> your name, email, phone number, delivery address and any delivery notes, so we can process, deliver and support your order (to perform our contract with you). We keep order records for 6 years for tax and accounting (a legal obligation).</li>
        <li><strong>Payments:</strong> PayPal processes your payment. We never see or store your card details; we keep PayPal’s payment reference with your order.</li>
        <li><strong>Your account (optional):</strong> if you sign in with Google, we receive your name, email address, profile photo and Google account ID. If you sign up with an email address and password, we keep your name and email address, and your password in a scrambled form that we can’t read. We keep your recent searches so you can repeat them. We keep all of this until you ask us to delete your account (you agree to this when you create it).</li>
        <li><strong>Part requests:</strong> your name, contact details, the part you need, any photos you send, and your registration in masked form (for example AB12 ***) with the vehicle it identifies, so we can find the part and reply (you ask us to do this when you send the request). We delete part requests after 12 months.</li>
        <li><strong>Reg checks:</strong> when you enter a registration, we send it to the DVSA (the government’s vehicle records service) to identify the vehicle, because you’ve asked us to check what fits. We don’t store the registration; we keep only the vehicle model and whether we had matching parts, for up to 40 days, to understand demand.</li>
        <li><strong>Help chat:</strong> your messages are used to answer you. If AI is switched on, the wording of answers comes from Google’s Gemini service. We remove email addresses, order numbers and registrations before anything is sent to Google, and order tracking never goes to Google. We don’t keep chat conversations.</li>
        <li><strong>Emails we send you:</strong> your order confirmation, invoice and any sign-in codes, sent to the email address you gave us.</li>
        <li><strong>Advertising:</strong> only if you accept advertising cookies, Google records that you visited and whether you contacted us (your consent; see Cookies below).</li>
        <li><strong>Baskets:</strong> what’s in your basket, kept for 7 days after it was last changed.</li>
        <li><strong>Technical information:</strong> our servers record basic request details (such as your IP address) for security and to prevent abuse, for a short time (our legitimate interest in keeping the shop safe).</li>
      </ul>
      <p>We use the information we hold to run the shop, answer your questions and meet our legal duties. We don’t sell it, and we don’t send marketing emails.</p>

      <h2 className="info__heading">Who We Share It With</h2>
      <p>We use trusted providers to run the shop. They act on our instructions and protect your information:</p>
      <ul className="info__list">
        <li><strong>Render</strong> (website and database hosting, servers in Frankfurt, Germany).</li>
        <li><strong>Cloudflare</strong> (storage for listing photos).</li>
        <li><strong>PayPal</strong> (payments).</li>
        <li><strong>Resend</strong> (sends our order and account emails).</li>
        <li><strong>Google</strong> (Sign in with Google, AI wording in the help chat, and measuring our Google Ads adverts).</li>
        <li><strong>DVSA</strong> (vehicle lookups).</li>
        <li><strong>Couriers</strong> ({site.couriers}): your name, address and phone number, to deliver your order.</li>
      </ul>
      <p>Some of these providers are based in, or may access data from, the United States. Where information leaves the UK, it’s protected by the UK’s data adequacy rules or standard contractual safeguards.</p>

      <h2 className="info__heading">Cookies and Browser Storage</h2>
      <p>
        The shop keeps your basket, wishlist, recent searches and sign-in in your browser’s storage so they’re there when
        you come back. Google may set a cookie when you use Sign in with Google, and PayPal sets cookies while you pay;
        see their own privacy notices.
      </p>
      <p>
        We advertise with Google Ads. If you accept advertising cookies, Google sets cookies that tell us which adverts
        brought visitors to the shop and whether they got in touch. If you reject them, no advertising cookies are set and
        Google receives only anonymous counts with no cookies. The shop works the same either way, and you can change your
        choice at any time.
      </p>
      <p><button type="button" className="btn btn--outline" onClick={reopenCookieNotice}>Change Cookie Choice</button></p>

      <h2 className="info__heading">Your Rights</h2>
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
