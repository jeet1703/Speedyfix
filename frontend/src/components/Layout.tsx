import { useEffect, type ReactNode } from 'react';
import { useSite } from '../state/SiteContext';
import { BasketDrawer } from './BasketDrawer';
import { Footer } from './Footer';
import { Header } from './Header';
import { HomeSkeleton, Skeleton } from './Skeleton';
import { Toast } from './Toast';
import { TopBar } from './TopBar';

/** Header, footer, basket drawer and toast around every page. title sets the browser tab. */
export function Layout({ title, children }: { title?: string; children: ReactNode }) {
  const { data, error, waking, retry } = useSite();

  useEffect(() => {
    document.title = title ? `${title} | SpeedyFix Auto Sales` : 'SpeedyFix Auto Sales | Engines and engine parts';
  }, [title]);

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
    </>
  );
}
