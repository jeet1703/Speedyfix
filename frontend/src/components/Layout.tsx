import { useEffect, type ReactNode } from 'react';
import { useSite } from '../state/SiteContext';
import { BasketDrawer } from './BasketDrawer';
import { Footer } from './Footer';
import { Header } from './Header';
import { Toast } from './Toast';
import { TopBar } from './TopBar';

/** Header, footer, basket drawer and toast around every page. title sets the browser tab. */
export function Layout({ title, children }: { title?: string; children: ReactNode }) {
  const { data, error, retry } = useSite();

  useEffect(() => {
    document.title = title ? `${title} | SpeedyFix Engine Solutions` : 'SpeedyFix Engine Solutions | Engines and engine parts';
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
    return <main className="state" aria-busy="true"><p>Loading the store…</p></main>;
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
