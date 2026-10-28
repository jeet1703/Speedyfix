import { Link } from 'react-router-dom';
import type { Site } from '../api/types';
import { Logo } from './Logo';

const COLUMNS = [
  { title: 'Links', links: [['Home', '/'], ['About us', '/about'], ['My account', '/account'], ['Contact us', '/contact']] },
  { title: 'Information', links: [['Terms and conditions', '/terms'], ['Privacy policy', '/privacy'], ['Delivery partners', '/delivery'], ['FAQs', '/faq']] }
];

export function Footer({ site }: { site: Site }) {
  return (
    <footer className="footer">
      <div className="container footer__cols">
        <div className="footer__about">
          <Logo inverted />
          <p>{site.name} supplies used, remanufactured and genuine OEM engine parts to garages and drivers across the UK.</p>
          <Link to="/about" className="footer__more">Read more</Link>
        </div>
        {COLUMNS.map((c) => (
          <nav key={c.title} aria-label={c.title} className="footer__col">
            <h2>{c.title}</h2>
            <ul>{c.links.map(([label, href]) => <li key={href}><Link to={href}>{label}</Link></li>)}</ul>
          </nav>
        ))}
        <div className="footer__col">
          <h2>Contact us</h2>
          <ul>
            <li>{site.companyName}</li>
            <li>{site.address}</li>
            <li>Tel: {site.phone}</li>
            <li>Email: {site.email}</li>
          </ul>
        </div>
      </div>
      <div className="container footer__base">
        <p>© {new Date().getFullYear()} {site.name}. All prices in GBP and include VAT.</p>
      </div>
    </footer>
  );
}
