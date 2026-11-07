import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { siteImage, siteSrcSet } from '../api/images';
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
    <Layout title="About Us" description={site ? `${site.companyName}: used, remanufactured and new car parts from ${site.shipsFrom}, with a ${site.warranty} warranty and UK delivery.` : undefined}>
      <PageTitle title="About Us" crumbs={[{ label: 'About Us' }]} />

      <section className="about-hero">
        <div className="container about-hero__inner">
          <div>
            <p className="eyebrow">{site?.tagline ?? 'Quality OEM and aftermarket car parts'}</p>
            <h2 className="about-hero__title">Car Parts We’ve Checked and Photographed, Ready to Ship</h2>
            <p className="about-hero__text">
              {site?.companyName ?? 'SpeedyFix Auto Sales'} supplies used, remanufactured and new car parts from our unit in
              {' '}{site?.shipsFrom.replace(/, UK$/, '') ?? 'Uxbridge'}: complete engines and gearboxes, turbos, injectors,
              ECUs, body panels and more, for garages and drivers across the UK and abroad.
            </p>
            <div className="empty__actions">
              <Link className="btn btn--primary" to="/products">Browse Parts</Link>
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
          <h2 id="how-title" className="about__heading">How We Work</h2>
          <ul className="about-steps">
            <li>
              <Icon name="search" size={26} />
              <h3>The Actual Part</h3>
              <p>Every listing shows photos of the exact part you’ll receive and its part number, so you can match it to your old one.</p>
            </li>
            <li>
              <Icon name="truck" size={26} />
              <h3>Checked to Fit</h3>
              <p>Enter your reg to see what fits your vehicle. If you’re unsure, send us your reg or VIN and we’ll confirm before you buy.</p>
            </li>
            <li>
              <Icon name="shield" size={26} />
              <h3>Covered</h3>
              <p>Every part comes with a {site?.warranty ?? '28-day'} warranty from delivery, on top of your rights under UK consumer law.</p>
            </li>
            <li>
              <Icon name="wrench" size={26} />
              <h3>Shipped Safely</h3>
              <p>We pack each part to survive the trip and track it with {site?.couriers ?? 'our delivery partners'}. Engines and gearboxes are strapped down and sent by freight.</p>
            </li>
          </ul>
        </section>

        {categories.length > 0 && (
          <section className="about__section" aria-labelledby="sell-title">
            <h2 id="sell-title" className="about__heading">What We Sell</h2>
            <ul className="about-cats">
              {categories.map((c) => {
                const href = `/products?category=${c.id}`;
                return (
                  <li key={c.id}>
                    <Link to={href}>
                      {photos.get(href) ? <img src={siteImage(photos.get(href)!)} srcSet={siteSrcSet(photos.get(href)!)} sizes="120px" alt="" width={120} height={120} loading="lazy" /> : <span className="about-cats__blank" />}
                      <span><strong>{c.name}</strong><span>{c.count.toLocaleString('en-GB')} in stock</span></span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        )}

        <section className="about__section about__split" aria-labelledby="request-title">
          <div>
            <h2 id="request-title" className="about__heading">Can’t Find a Part?</h2>
            <p>Many parts aren’t listed yet. Tell us what you’re after and we’ll try to source it.</p>
            <Link className="btn btn--outline" to="/request-a-part">Request a Part</Link>
          </div>
          {site && (
            <div className="panel about__contact">
              <h3 className="panel__title">Find Us</h3>
              <p>{site.companyName}<br />{site.address}</p>
              <p>
                {site.landline && <><a href={telHref(site.landline)}>{site.landline}</a> (landline)<br /></>}
                <a href={telHref(site.phone)}>{site.phone}</a> (mobile)<br />
                <a href={whatsAppHref(site.whatsapp)} target="_blank" rel="noopener noreferrer">Message Us on WhatsApp</a><br />
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
