import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { ShopResult } from '../api/types';
import { telHref, whatsAppHref } from '../components/contactLinks';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { useSite } from '../state/SiteContext';

/**
 * /about: who SpeedyFix is, what it sells (live counts from the shop), how it works and where to find it.
 * Only claims the business has given us or the shop can show; anything else waits for the client's copy.
 */
export function AboutPage() {
  const { data } = useSite();
  const [shop, setShop] = useState<ShopResult | null>(null);

  useEffect(() => {
    api.shop(new URLSearchParams({ pageSize: '1' })).then(setShop).catch(() => undefined);
  }, []);

  const site = data?.site;
  // Category photos come with the Products menu; counts with the shop.
  const photos = new Map((data?.menu ?? []).flatMap((g) => g.links).filter((l) => l.imageUrl).map((l) => [l.href, l.imageUrl!]));
  const categories = (shop?.categories ?? []).filter((c) => c.count > 0).sort((a, b) => b.count - a.count);

  return (
    <Layout title="About us" description={site ? `${site.companyName}: used, remanufactured and new car parts from ${site.shipsFrom}, with a ${site.warranty} warranty and UK delivery.` : undefined}>
      <PageTitle title="About us" crumbs={[{ label: 'About us' }]} />

      <section className="about-hero">
        <div className="container about-hero__inner">
          <div>
            <p className="eyebrow">{site?.tagline ?? 'Quality OEM and aftermarket car parts'}</p>
            <h2 className="about-hero__title">Quality car parts, checked, photographed and ready to ship</h2>
            <p className="about-hero__text">
              {site?.companyName ?? 'SpeedyFix Auto Sales'} supplies used, remanufactured and new car parts from our unit in
              {' '}{site?.shipsFrom.replace(/, UK$/, '') ?? 'Uxbridge'}: complete engines and gearboxes, turbos, injectors,
              ECUs, body panels and much more, for garages and drivers across the UK and abroad.
            </p>
            <div className="empty__actions">
              <Link className="btn btn--primary" to="/products">Browse parts</Link>
              <Link className="btn btn--outline-light" to="/request-a-part">Request a Part</Link>
            </div>
          </div>
          <dl className="about-stats">
            <div><dt>Parts in stock</dt><dd>{shop ? shop.total.toLocaleString('en-GB') : '…'}</dd></div>
            <div><dt>Categories</dt><dd>{shop ? categories.length : '…'}</dd></div>
            <div><dt>Warranty</dt><dd>{site?.warranty ?? '…'}</dd></div>
            <div><dt>UK delivery</dt><dd>7 days</dd></div>
          </dl>
        </div>
      </section>

      <div className="container page-body about">
        <section className="about__section" aria-labelledby="how-title">
          <h2 id="how-title" className="about__heading">How we work</h2>
          <ul className="about-steps">
            <li>
              <Icon name="search" size={26} />
              <h3>The actual part</h3>
              <p>Every listing shows photos of the exact part you’ll receive, not a stock image, with its part number so you can match it to your old one.</p>
            </li>
            <li>
              <Icon name="truck" size={26} />
              <h3>Checked to fit</h3>
              <p>Enter your reg to see what fits your vehicle. Not sure? Send us your reg or VIN and we’ll confirm before you buy.</p>
            </li>
            <li>
              <Icon name="shield" size={26} />
              <h3>Covered</h3>
              <p>Every part comes with a {site?.warranty ?? '28-day'} warranty from delivery, on top of your rights under UK consumer law.</p>
            </li>
            <li>
              <Icon name="wrench" size={26} />
              <h3>Shipped safely</h3>
              <p>Securely packed and tracked with {site?.couriers ?? 'our delivery partners'}. Engines and gearboxes travel by pallet.</p>
            </li>
          </ul>
        </section>

        {categories.length > 0 && (
          <section className="about__section" aria-labelledby="sell-title">
            <h2 id="sell-title" className="about__heading">What we sell</h2>
            <ul className="about-cats">
              {categories.map((c) => {
                const href = `/products?category=${c.id}`;
                return (
                  <li key={c.id}>
                    <Link to={href}>
                      {photos.get(href) ? <img src={photos.get(href)} alt="" width={120} height={120} loading="lazy" /> : <span className="about-cats__blank" />}
                      <span><strong>{c.name}</strong><span>{c.count.toLocaleString('en-GB')} in stock</span></span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        )}

        <section className="about__section about__split" aria-labelledby="ebay-title">
          <div>
            <h2 id="ebay-title" className="about__heading">Also on eBay</h2>
            <p>
              We’re a trusted eBay seller too, and our website and eBay shop share the same stock: new parts on eBay appear
              here within the hour. Buying here, you get the same parts, the same warranty and the same team.
            </p>
            <p>Can’t find what you need? Many parts aren’t listed yet. Tell us what you’re after and we’ll try to source it.</p>
            <Link className="btn btn--outline" to="/request-a-part">Request a Part</Link>
          </div>
          {site && (
            <div className="panel about__contact">
              <h3 className="panel__title">Find us</h3>
              <p>{site.companyName}<br />{site.address}</p>
              <p>
                <a href={telHref(site.phone)}>{site.phone}</a><br />
                <a href={whatsAppHref(site.whatsapp)} target="_blank" rel="noopener noreferrer">Message us on WhatsApp</a><br />
                <a href={`mailto:${site.email}`}>{site.email}</a>
              </p>
              <p className="page-body__note">Registered in England and Wales, company no. {site.companyNumber}. VAT no. {site.vatNumber}.</p>
            </div>
          )}
        </section>
      </div>
    </Layout>
  );
}
