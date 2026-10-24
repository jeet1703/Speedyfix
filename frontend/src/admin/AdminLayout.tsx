import { useEffect, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { Crumb } from '../components/PageTitle';
import { useAdminAuth } from './AdminAuth';

const NAV = [
  { label: 'Dashboard', to: '/admin', match: (p: string) => p === '/admin' },
  { label: 'Products', to: '/admin/products', match: (p: string) => p.startsWith('/admin/products') && p !== '/admin/products/new' },
  { label: 'Add part', to: '/admin/products/new', match: (p: string) => p === '/admin/products/new' },
  { label: 'Part requests', to: '/admin/part-requests', match: (p: string) => p.startsWith('/admin/part-requests') },
  { label: 'Vehicle lookup tester', to: '/admin/vehicle-lookup', match: (p: string) => p.startsWith('/admin/vehicle-lookup') },
  { label: 'Settings', to: '/admin/settings', match: (p: string) => p.startsWith('/admin/settings') }
];

/** Dark sidebar and page header around every signed-in admin page. title sets the browser tab and the h1. */
export function AdminLayout({ title, crumbs, actions, children }: {
  title: string;
  crumbs?: Crumb[];
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const { staff, signOut } = useAdminAuth();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    document.title = `${title} | SpeedyFix Auto Sales admin`;
  }, [title]);

  useEffect(() => setMenuOpen(false), [pathname]);

  return (
    <div className="admin">
      <a href="#admin-main" className="skip-link">Skip to content</a>
      <aside className="admin-side">
        <div className="admin-side__top">
          <Link to="/admin" className="admin-logo" aria-label="SpeedyFix Auto Sales admin, dashboard">
            <span className="admin-logo__mark">SPEEDY<span>FIX</span></span>
            <span className="admin-logo__sub">Admin</span>
          </Link>
          <button type="button" className="admin-side__toggle" aria-expanded={menuOpen} aria-controls="admin-nav"
            onClick={() => setMenuOpen((o) => !o)}>
            Menu
          </button>
        </div>
        <nav id="admin-nav" className={'admin-nav' + (menuOpen ? ' admin-nav--open' : '')} aria-label="Admin">
          <ul>
            {NAV.map((item) => (
              <li key={item.to}>
                <Link to={item.to} aria-current={item.match(pathname) ? 'page' : undefined}>{item.label}</Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className={'admin-side__foot' + (menuOpen ? ' admin-side__foot--open' : '')}>
          <p>Signed in as {staff?.name}</p>
          <p className="admin-side__role">{staff?.role === 'MASTER' ? 'Master admin' : 'Catalogue staff'}</p>
          <button type="button" className="admin-side__signout" onClick={() => void signOut()}>Sign out</button>
        </div>
      </aside>

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
