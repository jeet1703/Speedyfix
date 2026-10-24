import { useEffect, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { Crumb } from '../components/PageTitle';
import { useAdminAuth } from './AdminAuth';

/** Seller Hub-style tabs. Marketing, payments and the like aren't in scope for this shop. */
const TABS = [
  { label: 'Overview', to: '/admin', match: (p: string) => p === '/admin' },
  { label: 'Orders', to: '/admin/orders', match: (p: string) => p.startsWith('/admin/orders') },
  { label: 'Listings', to: '/admin/listings', match: (p: string) => p.startsWith('/admin/listings') || p.startsWith('/admin/products') },
  { label: 'Customers', to: '/admin/customers', match: (p: string) => p.startsWith('/admin/customers') },
  { label: 'Part requests', to: '/admin/part-requests', match: (p: string) => p.startsWith('/admin/part-requests') },
  { label: 'Vehicle lookup', to: '/admin/vehicle-lookup', match: (p: string) => p.startsWith('/admin/vehicle-lookup') },
  { label: 'Settings', to: '/admin/settings', match: (p: string) => p.startsWith('/admin/settings') }
];

/** Seller Hub-style frame around every signed-in admin page: top bar, tab row, then the page. title sets the tab and the h1. */
export function AdminLayout({ title, crumbs, actions, children }: {
  title: string;
  crumbs?: Crumb[];
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const { staff, signOut } = useAdminAuth();

  useEffect(() => {
    document.title = `${title} | SpeedyFix Auto Sales Seller Hub`;
  }, [title]);

  return (
    <div className="admin">
      <a href="#admin-main" className="skip-link">Skip to content</a>
      <header className="hub-bar">
        <div className="hub-bar__inner">
          <Link to="/admin" className="admin-logo" aria-label="SpeedyFix Auto Sales Seller Hub, overview">
            <span className="admin-logo__mark">SPEEDY<span>FIX</span></span>
            <span className="admin-logo__sub">Seller Hub</span>
          </Link>
          <div className="hub-bar__user">
            <span>{staff?.email}<span className="hub-bar__role"> · {staff?.role === 'MASTER' ? 'Master admin' : 'Staff'}</span></span>
            <a href={import.meta.env.BASE_URL} target="_blank" rel="noreferrer">View shop</a>
            <button type="button" className="hub-bar__signout" onClick={() => void signOut()}>Sign out</button>
          </div>
        </div>
        <nav className="hub-tabs" aria-label="Seller Hub">
          <ul>
            {TABS.map((t) => (
              <li key={t.to}><Link to={t.to} aria-current={t.match(pathname) ? 'page' : undefined}>{t.label}</Link></li>
            ))}
          </ul>
        </nav>
      </header>

      <main id="admin-main" className="admin-main" tabIndex={-1}>
        <header className="admin-head">
          <div>
            {crumbs && crumbs.length > 0 && (
              <nav aria-label="Breadcrumb">
                <ol className="admin-crumbs">
                  {crumbs.map((c) => (
                    <li key={c.label}>{c.to ? <Link to={c.to}>{c.label}</Link> : <span aria-current="page">{c.label}</span>}</li>
                  ))}
                </ol>
              </nav>
            )}
            <h1 className="admin-title">{title}</h1>
          </div>
          {actions && <div className="admin-head__actions">{actions}</div>}
        </header>
        {children}
      </main>
    </div>
  );
}
