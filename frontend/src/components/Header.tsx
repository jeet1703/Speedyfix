import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { money } from '../api/client';
import type { MenuGroup } from '../api/types';
import { useStore } from '../state/StoreContext';
import { Icon } from './Icons';
import { Logo } from './Logo';
import { MegaMenu } from './MegaMenu';
import { SearchPanel } from './SearchPanel';

const NAV = [
  { label: 'Home', href: '/' },
  { label: 'About us', href: '/about' },
  { label: 'Request a part', href: '/request-a-part' },
  { label: 'Returns and warranty', href: '/returns' },
  { label: 'Contact', href: '/contact' }
];

export function Header({ menu }: { menu: MenuGroup[] }) {
  const { basket, setBasketOpen } = useStore();
  const { pathname, search } = useLocation();
  const [productsOpen, setProductsOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const productsRef = useRef<HTMLLIElement>(null);
  // Hover opens the menu; a click that follows a hover keeps it open instead of toggling it shut.
  const openedByHover = useRef(false);

  useEffect(() => {
    if (!productsOpen) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !productsRef.current?.contains(e.target as Node)) setProductsOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [productsOpen]);

  // Close the menus whenever the page changes, including a new category on the shop page.
  useEffect(() => {
    setProductsOpen(false);
    setMobileOpen(false);
  }, [pathname, search]);

  const count = basket?.itemCount ?? 0;
  const current = (href: string) => (href === '/' ? pathname === '/' : pathname.startsWith(href));
  const navLink = (n: { label: string; href: string }) => (
    <li key={n.href}><Link to={n.href} aria-current={current(n.href) ? 'page' : undefined}>{n.label}</Link></li>
  );

  return (
    <header className="header">
      <div className="container header__inner">
        <Logo />
        <nav aria-label="Main" className={'nav' + (mobileOpen ? ' nav--open' : '')} id="main-nav">
          <ul>
            {NAV.slice(0, 2).map(navLink)}
            <li ref={productsRef} className="nav__products"
              onMouseLeave={() => { openedByHover.current = false; setProductsOpen(false); }}>
              <button type="button" aria-expanded={productsOpen} aria-controls="products-menu"
                className={current('/products') ? 'is-current' : undefined}
                onMouseEnter={() => { if (!productsOpen) openedByHover.current = true; setProductsOpen(true); }}
                onClick={() => {
                  if (openedByHover.current) { openedByHover.current = false; setProductsOpen(true); }
                  else setProductsOpen((o) => !o);
                }}>
                Products <Icon name="chevronDown" size={16} />
              </button>
              {productsOpen && <MegaMenu id="products-menu" groups={menu} onNavigate={() => setProductsOpen(false)} />}
            </li>
            {NAV.slice(2).map(navLink)}
          </ul>
        </nav>
        <div className="header__actions">
          <button type="button" className="icon-btn" aria-label="Search parts" aria-expanded={searchOpen}
            onClick={() => setSearchOpen((o) => !o)}>
            <Icon name="search" />
          </button>
          <Link className="icon-btn icon-btn--hide-sm" to="/account" aria-label="My account"><Icon name="user" /></Link>
          <button type="button" className="basket-btn" onClick={() => setBasketOpen(true)}
            aria-label={`Basket, ${count} ${count === 1 ? 'item' : 'items'}`}>
            <Icon name="basket" />
            <span className="basket-btn__count">{count}</span>
            <span className="basket-btn__total">{money(basket?.subtotal ?? 0)}</span>
          </button>
          <button type="button" className="icon-btn menu-btn" aria-label="Menu" aria-expanded={mobileOpen}
            aria-controls="main-nav" onClick={() => setMobileOpen((o) => !o)}>
            <Icon name={mobileOpen ? 'x' : 'menu'} />
          </button>
        </div>
      </div>
      {searchOpen && <SearchPanel onClose={() => setSearchOpen(false)} />}
    </header>
  );
}
