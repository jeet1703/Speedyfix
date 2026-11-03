import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useSite } from '../state/SiteContext';
import { BasketDrawer } from './BasketDrawer';
import { Footer } from './Footer';
import { Header } from './Header';
import { HomeSkeleton, Skeleton } from './Skeleton';
import { Toast } from './Toast';
import { HelpChat } from './HelpChat';
import { TopBar } from './TopBar';

const DEFAULT_DESCRIPTION = 'Used, remanufactured and new engine parts, checked against your registration before you buy. UK delivery.';

/** Finds or adds a tag in the page head, so each page can set its own. */
function headTag<T extends HTMLElement>(selector: string, create: () => T): T {
  let tag = document.head.querySelector<T>(selector);
  if (!tag) {
    tag = create();
    document.head.appendChild(tag);
  }
  return tag;
}

/**
 * Header, footer, basket drawer and toast around every page. title sets the browser tab; description the page's
 * summary for search engines and link previews (the shop's own when left out). noindex keeps a page out of search
 * (a missing page, which the host still serves with the shop). jsonLd is the page's structured data for search
 * engines and AI assistants, such as a part's price and stock.
 */
export function Layout({ title, description, noindex = false, jsonLd, children }: {
  title?: string;
  description?: string;
  noindex?: boolean;
  jsonLd?: object;
  children: ReactNode;
}) {
  const { data, error, waking, retry } = useSite();
  const { pathname, search } = useLocation();

  useEffect(() => {
    document.title = title ? `${title} | SpeedyFix Auto Sales` : 'SpeedyFix Auto Sales | Engines and engine parts';
  }, [title]);

  useEffect(() => {
    let meta = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'description';
      document.head.appendChild(meta);
    }
    meta.content = description ?? DEFAULT_DESCRIPTION;
  }, [description]);

  // One address per page for search engines: no query string, except the category a parts list shows.
  useEffect(() => {
    const category = pathname === '/products' ? new URLSearchParams(search).get('category') : null;
    const base = import.meta.env.BASE_URL.replace(/\/$/, '');
    const link = headTag('link[rel="canonical"]', () => Object.assign(document.createElement('link'), { rel: 'canonical' }));
    link.href = `${window.location.origin}${base}${pathname}${category ? `?category=${encodeURIComponent(category)}` : ''}`;
  }, [pathname, search]);

  useEffect(() => {
    const robots = headTag<HTMLMetaElement>('meta[name="robots"]', () => Object.assign(document.createElement('meta'), { name: 'robots' }));
    robots.content = noindex ? 'noindex' : 'index, follow';
  }, [noindex]);

  // The shop's own details on every page, and the page's own data (a part) when it has some.
  const ld = JSON.stringify([
    data && {
      '@context': 'https://schema.org',
      '@type': 'AutoPartsStore',
      name: data.site.companyName,
      url: `${window.location.origin}${import.meta.env.BASE_URL}`,
      telephone: data.site.phone,
      email: data.site.email,
      address: data.site.address,
      vatID: data.site.vatNumber
    },
    jsonLd
  ].filter(Boolean));
  useEffect(() => {
    const script = headTag('script#page-ld', () => Object.assign(document.createElement('script'), { id: 'page-ld', type: 'application/ld+json' }));
    script.textContent = ld;
  }, [ld]);

  if (error && !data) {
    return (
      <main className="state">
        <h1 className="h-display">The store didn’t load</h1>
        <p>{error}</p>
        <button type="button" className="btn btn--primary" onClick={retry}>Try again</button>
      </main>
    );
  }
  if (!data) {
    // The header and footer need the site details, so until they arrive the whole page is a skeleton.
    return (
      <main aria-busy="true" aria-label="Loading the store">
        <div className="layout-skeleton__top"><div className="container"><Skeleton width={220} height={12} /></div></div>
        <div className="container layout-skeleton__header">
          <Skeleton width={180} height={40} />
          <Skeleton className="layout-skeleton__search" height={48} />
          <Skeleton width={120} height={40} />
        </div>
        {waking && (
          <p className="container layout-skeleton__waking" role="status">
            Waking up the store. This can take up to a minute the first time…
          </p>
        )}
        <HomeSkeleton />
      </main>
    );
  }

  return (
    <>
      <a href="#main" className="skip-link">Skip to content</a>
      <TopBar phone={data.site.phone} />
      <Header menu={data.menu} />
      <main id="main" tabIndex={-1}>{children}</main>
      <Footer site={data.site} />
      <BasketDrawer />
      <Toast />
      <HelpChat site={data.site} ai={data.chatAi} fedex={data.deliveryQuotes} />
    </>
  );
}
