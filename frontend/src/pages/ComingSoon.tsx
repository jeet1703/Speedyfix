import { Link, useLocation } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';

/** Pages that need the client's own copy, and pages planned for the next build phase. */
const PAGES: Record<string, { title: string; text: string }> = {
  '/about': { title: 'About us', text: '[About SpeedyFix Auto Sales copy from the client]' },
  '/terms': { title: 'Terms and conditions', text: '[Terms and conditions from the client’s legal adviser]' },
  '/privacy': { title: 'Privacy policy', text: '[Privacy policy from the client’s legal adviser]' },
  '/delivery': { title: 'Delivery partners', text: '[Courier names] and delivery details from the client.' },
  '/faq': { title: 'FAQs', text: '[Frequently asked questions from the client]' }
};

export function ComingSoon() {
  const { pathname } = useLocation();
  const page = PAGES[pathname.replace(/\/$/, '')];
  const title = page?.title ?? 'Page not found';
  return (
    <Layout title={title}>
      <PageTitle title={title} crumbs={[{ label: title }]} />
      <div className="container page-body">
        <p className="page-body__text">{page?.text ?? 'We couldn’t find that page. It may have moved.'}</p>
        <div className="empty__actions">
          <Link className="btn btn--primary" to="/products">Browse parts</Link>
          <Link className="btn btn--outline" to="/">Back to the homepage</Link>
        </div>
      </div>
    </Layout>
  );
}
