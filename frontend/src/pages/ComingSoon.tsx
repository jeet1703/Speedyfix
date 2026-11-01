import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { Site } from '../api/types';
import { telHref } from '../components/contactLinks';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { useSite } from '../state/SiteContext';
import { PrivacyBody, TermsBody } from './legal';

/** Information pages. Terms and privacy are drafts for the client's legal adviser to review (see PENDING.md). */
const PAGES: Record<string, { title: string; body: (site: Site) => ReactNode }> = {
  '/delivery': {
    title: 'Delivery and returns',
    body: (site) => (
      <>
        <h2 className="info__heading">UK delivery</h2>
        <ul className="info__list">
          <li>Tracked parcel delivery is £9.95, and free on orders of £100 or more.</li>
          <li>Engines, gearboxes and other heavy parts go by pallet: £79 per order.</li>
          <li>UK delivery is usually within 7 working days (estimated). Courier delays can happen.</li>
          <li>Everything is securely packed and you get tracking once it’s dispatched from {site.shipsFrom}.</li>
        </ul>
        <h2 className="info__heading">Our delivery partners</h2>
        <p>We send parts with {site.couriers}, choosing the right service for the size and destination of your order.</p>
        <h2 className="info__heading">International delivery</h2>
        <p>We ship by UK Pallets or FedEx, Standard Economy or Express Priority, at extra cost: contact us for a quote. Allow 3 to 10 working days after dispatch. Buyers are responsible for customs clearance and any import VAT or duties.</p>
        <h2 className="info__heading">Warranty</h2>
        <p>All parts come with a {site.warranty} warranty from the date of delivery. It covers the part only, not labour or fitting costs.</p>
        <h2 className="info__heading">Returns</h2>
        <p>{site.returns}</p>
      </>
    )
  },
  '/faq': {
    title: 'FAQs',
    body: (site) => (
      <dl className="info__faq">
        <dt>How do I know a part fits my car?</dt>
        <dd>Enter your reg on the part’s page to check it against our compatibility list, and compare the part number with the one on your old part. If you’re unsure, message us your reg or VIN and we’ll confirm compatibility before you order.</dd>
        <dt>Is the part in the photos the one I’ll get?</dt>
        <dd>Yes. Our photos are of the actual unit for sale, not stock images.</dd>
        <dt>What warranty do I get?</dt>
        <dd>A {site.warranty} warranty from the date of delivery, covering the part (not labour or fitting).</dd>
        <dt>How long does delivery take?</dt>
        <dd>UK orders usually arrive within 7 working days, with {site.couriers}. Engines and gearboxes travel by pallet. See <Link to="/delivery">Delivery and returns</Link>.</dd>
        <dt>Which couriers do you use?</dt>
        <dd>{site.couriers}. You get a tracking number once your order is dispatched.</dd>
        <dt>Can I return a part?</dt>
        <dd>{site.returns}</dd>
        <dt>Do your prices include VAT?</dt>
        <dd>Yes, every price includes VAT. Our VAT number is {site.vatNumber}.</dd>
        <dt>Can’t find the part you need?</dt>
        <dd>Use <Link to="/request-a-part">Request a Part</Link>, or call us on <a href={telHref(site.phone)}>{site.phone}</a>, and we’ll try to source it.</dd>
      </dl>
    )
  },
  '/terms': { title: 'Terms and conditions', body: TermsBody },
  '/privacy': { title: 'Privacy policy', body: PrivacyBody }
};

export function ComingSoon() {
  const { pathname } = useLocation();
  const { data } = useSite();
  const page = PAGES[pathname.replace(/\/$/, '')];
  const title = page?.title ?? 'Page not found';
  return (
    <Layout title={title}>
      <PageTitle title={title} crumbs={[{ label: title }]} />
      <div className="container page-body">
        <div className="page-body__text info">
          {page ? (data ? page.body(data.site) : null) : <p>We couldn’t find that page. It may have moved.</p>}
        </div>
        <div className="empty__actions">
          <Link className="btn btn--primary" to="/products">Browse parts</Link>
          <Link className="btn btn--outline" to="/contact">Contact us</Link>
        </div>
      </div>
    </Layout>
  );
}
