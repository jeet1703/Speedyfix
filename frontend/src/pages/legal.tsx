import { Link } from 'react-router-dom';
import type { Site } from '../api/types';
import { reopenCookieNotice } from '../state/ads';

/*
 * Terms of sale and privacy notice. Written for this shop from UK consumer law (Consumer Contracts Regulations 2013,
 * Consumer Rights Act 2015), UK GDPR and the business's own eBay terms. DRAFTS: the business's legal adviser should
 * review them before launch (see PENDING.md). Facts still to confirm are marked [to confirm: …].
 */

const UPDATED = '5 November 2026';

/** fedex: parcels are priced by FedEx at checkout rather than the flat £9.95. */
export function TermsBody(site: Site, fedex = false) {
  return (
    <>
      <p className="info__note">Draft for review. Last updated {UPDATED}.</p>

      <h2 className="info__heading">1. Who We Are</h2>
      <p>
        This website is run by {site.companyName}, a company registered in England and Wales (company no. {site.companyNumber},
        VAT no. {site.vatNumber}), of {site.address}. You can contact us on {site.phone} or at {site.email}.
      </p>
      <p>These terms apply when you buy from this website. Please read them before you order. If you buy as a business, section 11 applies too.</p>

      <h2 className="info__heading">2. Your Order</h2>
      <ul className="info__list">
        <li>When you place an order, we reserve the parts and you pay through PayPal (with a PayPal account, or a debit or credit card).</li>
        <li>Our contract with you starts when your payment is confirmed. We’ll show the confirmation on screen, and you can check your order’s status at any time with its order number.</li>
        <li>An unpaid order is cancelled after 2 hours, and the parts go back on sale.</li>
        <li>Most of our parts are one-offs. If a part sells elsewhere before your order is confirmed, or a listing has an obvious error such as a wrong price, we’ll tell you and refund anything you’ve paid in full.</li>
      </ul>

      <h2 className="info__heading">3. Prices and Payment</h2>
      <p>Prices are in pounds sterling and include VAT. Delivery is shown before you pay. Sale prices apply only while the listing shows them.</p>

      <h2 className="info__heading">4. Checking a Part Fits</h2>
      <p>
        Each listing shows the part number and the vehicles we know it fits. Our reg check is a guide based on the data we
        hold, so please compare the part number with the one on your old part. If you’re unsure, send us your registration
        or VIN before you buy and we’ll confirm compatibility. A part that doesn’t fit because the wrong part was ordered is
        covered by section 6, not by the warranty.
      </p>
      <p>
        We strongly recommend having parts fitted by a professional. Some parts don’t come with fitting instructions. If
        you tell us a part doesn’t fit your vehicle, we’ll ask for evidence from the garage or fitter who checked it.
      </p>

      <h2 className="info__heading">5. Delivery</h2>
      <ul className="info__list">
        <li>UK delivery is usually within 7 working days (estimated) with {site.couriers}. Delivery times can vary with courier delays.</li>
        <li>{fedex ? 'Parcel delivery is priced by FedEx at checkout from your postcode, and in the UK the cheapest service is free when every part in the order weighs 5 kg or less.' : 'Parcels cost £9.95, or are free on orders of £100 or more.'} {fedex ? 'Engines, gearboxes and other heavy parts go by FedEx Freight, priced at checkout. UK delivery never costs more than £100, however heavy the order; delivery abroad is priced by FedEx at checkout. You can also choose manual delivery and pay a separate delivery quote.' : 'Engines, gearboxes and other heavy parts go by pallet at £79 per order.'} Someone needs to be available to receive a heavy delivery.</li>
        <li>We deliver worldwide. Orders delivered outside the UK are exports and are charged without UK VAT. You are responsible for customs clearance and any import VAT, duty or taxes in your country. Allow 3 to 10 working days after dispatch.</li>
        <li>The parts are your responsibility once they’re delivered. Please check them when they arrive and tell us within 24 hours if anything is damaged, missing or not what you ordered, with photos (see section 6).</li>
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
        If you buy for a business (for example a garage), the returns policy in section 6 applies. Our total liability to a
        business is limited to the price of the parts concerned, and we aren’t liable for loss of profit, business or
        downtime. [to confirm]
      </p>

      <h2 className="info__heading">12. Other Things</h2>
      <p>
        These terms are governed by the law of England and Wales, and you can bring proceedings in the courts of England
        and Wales (or Scotland or Northern Ireland if you live there). We may update these terms; the version on this
        page when you order is the one that applies. See how we use your information in our <Link to="/privacy">privacy policy</Link>.
      </p>
    </>
  );
}

/**
 * The business's own returns, warranty, refund and cancellation policy, word for word as supplied (5 November 2026):
 * the final source of truth. Shown in the terms (section 6) and on the Delivery and returns page. Only the returns
 * email is the shop's own address.
 */
export function ReturnPolicyBody(site: Site) {
  return (
    <>
      <h3 className="info__heading">Return Policy</h3>
      <p>WE HAVE A 30 DAYS RETURN POLICY AND ACCEPT THE RETURNS ONLY IN SUCH FOLLOWING CONDITIONS.</p>
      <ul className="info__list">
        <li>If you have received the item different than the ordered part number or not matching with the pictures given on the listing.</li>
        <li>If you receive the items have quality problems or damaged, please provide us the photos immediately and contact us within 24 hours after receiving item.</li>
        <li>If buyer purchased “item by mistake” and the item is still in a good condition(unopened with original packaging) for reselling, in this case the buyer is responsible for all related shipping costs.</li>
        <li>A non-refundable restocking fee of 20% will be charged for each returned order/item.</li>
        <li>All presumed defective or wrong items must obtain an authorization before returning. Any returned package without authorization will be refused.</li>
        <li>No returns or guarantees, unless item is non-functional in relation to the description.</li>
        <li>No returns or exchanges offered on opened packages or installed parts.</li>
        <li>We highly recommend professional installation for all products, installation instructions are not included in some products. Professional proof is required, if item is not fits to your vehicle.</li>
      </ul>

      <h3 className="info__heading">Warranty</h3>
      <ul className="info__list">
        <li>No Returns or Warranty for New Genuine parts.</li>
        <li>There is a 3 Months replacement warranty on Aftermarket &amp; Refurbished parts. For more information refer to our item description.</li>
        <li>Warranty will applicable on parts only, we will not refund any labor costs, towing &amp; rentals in incurred by you or the installer of products at this website.</li>
      </ul>

      <h3 className="info__heading">NO WARRANTY &amp; NO RETURNS ON ELECTRICAL PARTS</h3>
      <ul className="info__list">
        <li>Electrical parts do not have any warranty and are also not returnable under any circumstances.</li>
        <li>Please ensure you provide accurate vehicle information at the time of purchase and are sure the electrical item you are replacing is the cause of your issue.</li>
        <li>Electrical/electronic parts are easily damaged by improper installation or testing. Because electronics are very sensitive to voltage overloads, it does not take much of a voltage spike to ruin a component.</li>
        <li>
          The parts list mentioned below provides a high-level list of merchandise that is classified as non-returnable as
          per our Return Policy. “Switches (all types), Sensors (all types), Regulators, Amplifiers, Temperature Sensors,
          Motor Resistors, EGR Sensors and Valves, Ignition Modules, Computers, Cruise Control Relays, Distributor Parts,
          Door Lock Actuators, Lighting Connectors, Solenoids, Relays &amp; Power Window Switches”.
        </li>
      </ul>

      <h3 className="info__heading">Refunds</h3>
      <p>
        Refunds for returns or cores normally process and credit within 10 days from the date we receive your return at the
        warehouse. Once the item has been returned, a refund will be made to the buyer minus original shipping fee and the
        restocking fee, buyers are also responsible for return shipping charges. The refund will be credited to the credit
        card you used to buy the merchandise. You will receive a “credit approved” e-mail the day you are credited. Please
        be aware that your bank may take 4-7 business days to post the credit to your account or credit card. If you do not
        receive a credit after 30 days, please e-mail our returns department at {site.email} and we will respond to you
        immediately.
      </p>

      <h3 className="info__heading">Final Reminders for Returns</h3>
      <p>You may return products within 30 business days of shipment for a refund or replacement.</p>

      <h3 className="info__heading">Cancellation</h3>
      <p>
        All orders are electronically processed and forwarded to the warehouse immediately upon confirmation of the order.
        Therefore, there is a very short time for order cancellations. If you wish to cancel an order, you must do so BEFORE
        you receive the “Invoice – Payment” e-mail. No orders can be canceled after the payment has been confirmed, as the
        order will be immediately processed for shipment.
      </p>
      <p>An order is considered confirmed once payment is done. Due to the strict policy, one cannot able to cancel already confirmed orders.</p>
    </>
  );
}

export function PrivacyBody(site: Site) {
  return (
    <>
      <p className="info__note">Draft for review. Last updated {UPDATED}.</p>

      <h2 className="info__heading">Who We Are</h2>
      <p>
        {site.companyName} (company no. {site.companyNumber}), {site.address}, is responsible for the personal information
        this website collects (the “controller” under UK data protection law). Contact us about your information at
        {' '}{site.email} or {site.phone}. Our ICO registration number is [to confirm: ICO registration number].
      </p>

      <h2 className="info__heading">What We Collect and Why</h2>
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

      <h2 className="info__heading">Who We Share It With</h2>
      <p>We use trusted providers to run the shop. They act on our instructions and protect your information:</p>
      <ul className="info__list">
        <li><strong>Render</strong> (website and database hosting, servers in Frankfurt, Germany).</li>
        <li><strong>Cloudflare</strong> (storage for listing photos).</li>
        <li><strong>PayPal</strong> (payments).</li>
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
