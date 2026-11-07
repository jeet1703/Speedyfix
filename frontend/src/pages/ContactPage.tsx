import { Link } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { telHref, whatsAppHref } from '../components/contactLinks';
import { useSite } from '../state/SiteContext';

export function ContactPage() {
  const { data } = useSite();
  const site = data?.site;
  return (
    <Layout title="Contact">
      <PageTitle title="Contact Us" crumbs={[{ label: 'Contact' }]} />
      <div className="container two-col two-col--wide page-body">
        <section className="panel" aria-labelledby="contact-title">
          <h2 id="contact-title" className="panel__title">Get in Touch</h2>
          <dl className="specs">
            {site?.landline && <div><dt>Landline</dt><dd><a href={telHref(site.landline)}>{site.landline}</a></dd></div>}
            <div><dt>Mobile</dt><dd>{site && <a href={telHref(site.phone)}>{site.phone}</a>}</dd></div>
            <div><dt>WhatsApp</dt><dd>{site && <a href={whatsAppHref(site.whatsapp)} target="_blank" rel="noopener noreferrer">Message Us on WhatsApp</a>}</dd></div>
            <div><dt>Email</dt><dd>{site && <a href={`mailto:${site.email}`}>{site.email}</a>}</dd></div>
            <div><dt>Address</dt><dd>{site?.address}</dd></div>
            <div><dt>Company</dt><dd>{site?.companyName}, company no. {site?.companyNumber}</dd></div>
            <div><dt>VAT number</dt><dd>{site?.vatNumber}</dd></div>
          </dl>
        </section>
        <aside className="panel panel--dark" aria-labelledby="contact-part-title">
          <h2 id="contact-part-title" className="panel__title">Looking for a Part?</h2>
          <p>Send your reg and the part you need, and we’ll reply with a price and lead time.</p>
          <Link className="btn btn--primary" to="/request-a-part">Request a Part</Link>
        </aside>
      </div>
    </Layout>
  );
}
