import { Link } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { useSite } from '../state/SiteContext';

export function ContactPage() {
  const { data } = useSite();
  const site = data?.site;
  return (
    <Layout title="Contact">
      <PageTitle title="Contact us" crumbs={[{ label: 'Contact' }]} />
      <div className="container two-col two-col--wide page-body">
        <section className="panel" aria-labelledby="contact-title">
          <h2 id="contact-title" className="panel__title">Get in touch</h2>
          <dl className="specs">
            <div><dt>Phone</dt><dd>{site?.phone}</dd></div>
            <div><dt>WhatsApp</dt><dd>{site?.whatsapp}</dd></div>
            <div><dt>Email</dt><dd>{site?.email}</dd></div>
            <div><dt>Address</dt><dd>{site?.address}</dd></div>
            <div><dt>Company</dt><dd>{site?.companyName}</dd></div>
          </dl>
        </section>
        <aside className="panel panel--dark" aria-labelledby="contact-part-title">
          <h2 id="contact-part-title" className="panel__title">Looking for a part?</h2>
          <p>Send your reg and the part you need, and we’ll reply with a price and lead time.</p>
          <Link className="btn btn--primary" to="/request-a-part">Request a part</Link>
        </aside>
      </div>
    </Layout>
  );
}
