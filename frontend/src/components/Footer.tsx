import { Link } from 'react-router-dom';
import { telHref } from './contactLinks';
import type { Site } from '../api/types';
import { Logo } from './Logo';

const COLUMNS = [
  { title: 'Links', links: [['Home', '/'], ['About Us', '/about'], ['My Account', '/account'], ['Contact Us', '/contact']] },
  { title: 'Information', links: [['Terms and Conditions', '/terms'], ['Privacy Policy', '/privacy'], ['Delivery and Returns', '/delivery'], ['FAQs', '/faq']] }
];

export function Footer({ site }: { site: Site }) {
  return (
    <footer className="footer">
      <div className="container footer__cols">
        <div className="footer__about">
          <Logo inverted />
          <p>{site.name} supplies used, remanufactured and genuine OEM engine parts to garages and drivers across the UK.</p>
          <Link to="/about" className="footer__more">Read More</Link>
        </div>
        {COLUMNS.map((c) => (
          <nav key={c.title} aria-label={c.title} className="footer__col">
            <h2>{c.title}</h2>
            <ul>{c.links.map(([label, href]) => <li key={href}><Link to={href}>{label}</Link></li>)}</ul>
          </nav>
        ))}
        <div className="footer__col">
          <h2>Contact Us</h2>
          <ul>
            <li>{site.companyName}</li>
            <li>{site.address}</li>
            <li>Tel: <a href={telHref(site.phone)}>{site.phone}</a></li>
            <li>Email: <a href={`mailto:${site.email}`}>{site.email}</a></li>
          </ul>
        </div>
      </div>
      <div className="container footer__base">
        <p>© {new Date().getFullYear()} {site.companyName}. All prices in GBP and include VAT.</p>
        <p>Registered in England and Wales, company no. {site.companyNumber}. VAT no. {site.vatNumber}.</p>
      </div>
    </footer>
  );
}
