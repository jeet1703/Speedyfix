import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, money } from '../api/client';
import type { ProductDetail, Vehicle } from '../api/types';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { ListingHtml } from '../components/ListingHtml';
import { Price } from '../components/Price';
import { PageTitle } from '../components/PageTitle';
import { PlateField } from '../components/PlateField';
import { ProductCard } from '../components/ProductCard';
import { SectionHeading } from '../components/SectionHeading';
import { ProductPageSkeleton } from '../components/Skeleton';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

type Tab = 'description' | 'fitment' | 'delivery';

export function ProductPage() {
  const { id = '' } = useParams();
  const { vehicle } = useStore();
  const { data } = useSite();
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<Tab>('description');

  useEffect(() => {
    let live = true;
    api.product(id, vehicle?.id)
      .then((d) => { if (live) { setDetail(d); setError(''); } })
      .catch((e: Error) => live && setError(e.message));
    return () => { live = false; };
  }, [id, vehicle?.id]);

  useEffect(() => setTab('description'), [id]);

  if (error && !detail) {
    return (
      <Layout title="Part not found" noindex>
        <div className="container state">
          <h1 className="h-display">We couldn’t find that part</h1>
          <p>{error}</p>
          <div className="empty__actions">
            <Link className="btn btn--primary" to="/products">Browse all parts</Link>
            <Link className="btn btn--outline" to="/request-a-part">Request a Part</Link>
          </div>
        </div>
      </Layout>
    );
  }
  if (!detail || detail.product.id !== id) {
    return <Layout><ProductPageSkeleton /></Layout>;
  }

  const p = detail.product;
  // Listings imported from eBay end their id with the eBay item number, which customers can quote on the phone.
  const itemNumber = p.id.match(/-(\d{9,})$/)?.[1];
  const samplePhoto = p.imageUrl.includes('images.unsplash.com');
  return (
    <Layout title={p.name} jsonLd={productLd(detail)} description={`${p.name}. ${p.condition}, ${p.salePercent ? `on sale (${p.salePercent}% off), ` : ''}£${p.price.toFixed(2)} inc. VAT${p.inStock ? ', in stock' : ''}. ${data?.site.warranty ?? ''} warranty, UK delivery from SpeedyFix Auto Sales.`.replace(/\s+/g, ' ')}>
      <PageTitle title={p.categoryName} heading={false} crumbs={[
        { label: 'Products', to: '/products' },
        { label: p.categoryName, to: `/products?category=${p.categoryId}` },
        { label: p.name }
      ]} />

      <div className="container product">
        <Gallery key={p.id} name={p.name} photos={detail.photos} samplePhoto={samplePhoto} />

        <div className="product__summary">
          <Link className="product__category" to={`/products?category=${p.categoryId}`}>{p.categoryName}</Link>
          <h1 className="h-display product__title">{p.name}</h1>
          <p className="product__price">
            <Price product={p} /> <span className="product__vat">inc. VAT ({money(detail.priceExVat)} ex. VAT)</span>
          </p>
          <p className={'product__stock' + (p.inStock ? '' : ' is-out')}>
            {p.inStock ? `In stock: ${p.stock} available` : 'Out of stock'}
          </p>
          <p className="product__lead">{detail.description}</p>

          <FitCheck detail={detail} vehicle={vehicle} />
          <BuyBox detail={detail} />

          <dl className="product__meta">
            {itemNumber && <div><dt>Item number:</dt><dd className="mono">{itemNumber}</dd></div>}
            <div><dt>Category:</dt><dd>{p.categoryName}</dd></div>
            {detail.partNumber && <div><dt>Part number:</dt><dd className="mono">{detail.partNumber}</dd></div>}
            {detail.brand && <div><dt>Brand:</dt><dd>{detail.brand}</dd></div>}
            {detail.subType && <div><dt>Sub type:</dt><dd>{detail.subType}</dd></div>}
            {detail.engineCode && <div><dt>Engine code:</dt><dd className="mono">{detail.engineCode}</dd></div>}
          </dl>
          <Perks shipping={p.shipping} />
        </div>
      </div>

      <div className="container tabs">
        <div className="tabs__list" role="tablist" aria-label="Part information">
          {([['description', 'Description'], ['fitment', 'Compatibility'], ['delivery', 'Delivery and returns']] as [Tab, string][]).map(([key, label]) => (
            <button key={key} type="button" role="tab" id={`tab-${key}`} aria-controls={`panel-${key}`}
              aria-selected={tab === key} tabIndex={tab === key ? 0 : -1} className="tabs__tab" onClick={() => setTab(key)}
              onKeyDown={(e) => {
                const order: Tab[] = ['description', 'fitment', 'delivery'];
                const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
                if (!step) return;
                const next = order[(order.indexOf(tab) + step + order.length) % order.length];
                setTab(next);
                document.getElementById(`tab-${next}`)?.focus();
              }}>
              {label}
            </button>
          ))}
        </div>
        <div className="tabs__panel" role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} tabIndex={0}>
          {tab === 'description' && <DescriptionTab detail={detail} />}
          {tab === 'fitment' && <FitmentTab detail={detail} vehicle={vehicle} />}
          {tab === 'delivery' && <DeliveryTab shipping={p.shipping} />}
        </div>
      </div>

      {detail.related.length > 0 && (
        <section className="section related" aria-labelledby="related-title">
          <div className="container">
            <SectionHeading id="related-title" title="Related products" />
            <ul className="product-grid product-grid--4">
              {detail.related.map((r) => <li key={r.id}><ProductCard product={r} /></li>)}
            </ul>
          </div>
        </section>
      )}
    </Layout>
  );
}

/** Says whether the part fits the saved vehicle, and lets the shopper check another reg. */
function FitCheck({ detail, vehicle }: { detail: ProductDetail; vehicle: Vehicle | null }) {
  const { setVehicle, notify } = useStore();
  const { data } = useSite();
  const [reg, setReg] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(!vehicle);

  useEffect(() => setEditing(!vehicle), [vehicle]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const result = await api.lookUpVehicle(reg);
      setVehicle(result.vehicle);
      notify(`Vehicle saved: ${result.vehicle.make} ${result.vehicle.model}`);
      setReg('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Couldn’t look up that registration.');
    } finally {
      setBusy(false);
    }
  }

  const fit = detail.product.fit;
  return (
    <div className="fit-check">
      {vehicle && (
        <p className={`fit-check__result fit-check__result--${fit === 'FITS' || fit === 'LIKELY_FITS' ? 'ok' : fit === 'DOES_NOT_FIT' ? 'no' : 'ask'}`} role="status">
          <Icon name={fit === 'FITS' || fit === 'LIKELY_FITS' ? 'check' : fit === 'DOES_NOT_FIT' ? 'x' : 'ask'} size={20} />
          <span>
            {fit === 'FITS' && <>Fits your {vehicle.make} {vehicle.model}, {vehicle.engine}.</>}
            {fit === 'LIKELY_FITS' && <>Likely fits your {vehicle.make} {vehicle.model}, {vehicle.engine}: the listing matches its make, model, years and engine size. We check the part number before dispatch.</>}
            {fit === 'DOES_NOT_FIT' && <>Doesn’t fit your {vehicle.make} {vehicle.model}. <Link to="/products?fitsOnly=true">See parts that fit</Link>.</>}
            {fit === 'UNCONFIRMED' && <>Fit for your {vehicle.make} {vehicle.model} isn’t confirmed yet. Ask us and we’ll check it by part number before you order.</>}
            {' '}{!editing && <button type="button" className="link-btn" onClick={() => setEditing(true)}>Check another reg</button>}
          </span>
        </p>
      )}
      {editing && (
        <form onSubmit={submit} noValidate>
          <label htmlFor="fit-reg" className="fit-check__label">Check this part fits your car</label>
          <div className="fit-check__row">
            <PlateField id="fit-reg" value={reg} onChange={setReg} size="sm" invalid={!!error} describedBy={error ? 'fit-error' : 'fit-hint'} />
            <button type="submit" className="btn btn--dark" disabled={busy}>{busy ? 'Checking…' : 'Check fit'}</button>
          </div>
          {error && <p id="fit-error" className="field-error" role="alert">{error}</p>}
          <p id="fit-hint" className="fit-check__hint">Or call {data?.site.phone} with your reg and we’ll check for you.</p>
        </form>
      )}
    </div>
  );
}

function BuyBox({ detail }: { detail: ProductDetail }) {
  const { addToBasket, basket, wishlist, toggleWishlist, setBasketOpen } = useStore();
  const p = detail.product;
  const inBasket = basket?.lines.find((l) => l.productId === p.id)?.quantity ?? 0;
  const available = Math.max(0, p.stock - inBasket);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const saved = wishlist.includes(p.id);

  useEffect(() => setQty((q) => Math.max(1, Math.min(q, available))), [available]);

  async function add() {
    setAdding(true);
    await addToBasket(p.id, p.name, qty);
    setAdding(false);
    setBasketOpen(true);
  }

  return (
    <div className="buy">
      <div className="qty qty--lg" role="group" aria-label="Quantity">
        <button type="button" aria-label="Decrease quantity" disabled={qty <= 1} onClick={() => setQty(qty - 1)}><Icon name="minus" size={16} /></button>
        <span aria-live="polite">{qty}</span>
        <button type="button" aria-label="Increase quantity" disabled={qty >= available} onClick={() => setQty(qty + 1)}><Icon name="plus" size={16} /></button>
      </div>
      {p.inStock && available === 0 ? (
        <button type="button" className="btn btn--outline buy__add" onClick={() => setBasketOpen(true)}>
          <Icon name="check" size={18} />In your basket
        </button>
      ) : (
        <button type="button" className="btn btn--primary buy__add" onClick={add} disabled={adding || !p.inStock}>
          {!p.inStock ? 'Out of stock' : adding ? 'Adding…' : inBasket > 0 ? 'Add another' : 'Add to basket'}
        </button>
      )}
      <button type="button" className={'buy__wish' + (saved ? ' is-saved' : '')} aria-pressed={saved}
        aria-label={saved ? 'Remove from wishlist' : 'Save to wishlist'} onClick={() => toggleWishlist(p.id)}>
        <Icon name="heart" size={20} />
      </button>
    </div>
  );
}

function Perks({ shipping }: { shipping: 'PARCEL' | 'PALLET' }) {
  const { data } = useSite();
  return (
    <ul className="perks">
      <li><Icon name="shield" size={20} />{data?.site.warranty} warranty.</li>
      <li><Icon name="truck" size={20} />{data?.deliveryQuotes
        ? shipping === 'PALLET' ? 'Tracked FedEx Freight delivery, priced at checkout, never more than £100 in the UK.' : 'Tracked FedEx delivery, priced at checkout. Free in the UK up to 5 kg.'
        : shipping === 'PALLET' ? 'Pallet delivery across the UK, £79.' : 'Tracked parcel delivery, £9.95 or free from £100.'}</li>
    </ul>
  );
}

/**
 * The listing's photos on a strip that slides, with a strip of thumbnails underneath; swipe on touch screens.
 * The photos either side of the current one load in advance, so a slide never waits on a download and the
 * counter always matches the photo showing.
 */
function Gallery({ name, photos, samplePhoto }: { name: string; photos: ProductDetail['photos']; samplePhoto: boolean }) {
  const [index, setIndex] = useState(0);
  const [touchX, setTouchX] = useState<number | null>(null);
  const many = photos.length > 1;
  const step = (by: number) => setIndex((i) => (i + by + photos.length) % photos.length);
  const near = (i: number) => {
    const d = Math.abs(i - index);
    return Math.min(d, photos.length - d) <= 1;
  };
  return (
    <div className="product__gallery">
      <div className="photo-frame" aria-roledescription={many ? 'carousel' : undefined}
        onTouchStart={(e) => setTouchX(e.touches[0].clientX)}
        onTouchEnd={(e) => {
          if (touchX === null) return;
          const dx = e.changedTouches[0].clientX - touchX;
          if (many && Math.abs(dx) > 40) step(dx < 0 ? 1 : -1);
          setTouchX(null);
        }}>
        <div className="gallery__track" style={{ transform: `translateX(-${index * 100}%)` }}>
          {photos.map((ph, i) => (
            <img key={ph.url} src={ph.url.replace('w=800&h=800', 'w=1200&h=1000')} width={600} height={500}
              alt={many ? `${name}, photo ${i + 1} of ${photos.length}` : name} aria-hidden={i !== index}
              loading={i === 0 || near(i) ? 'eager' : 'lazy'} decoding="async" draggable={false} />
          ))}
        </div>
        {samplePhoto && <span className="photo-frame__note">Sample photo. The part you receive is the one in the title.</span>}
        {many && (
          <>
            <button type="button" className="gallery__arrow gallery__arrow--prev" aria-label="Previous photo" onClick={() => step(-1)}>‹</button>
            <button type="button" className="gallery__arrow gallery__arrow--next" aria-label="Next photo" onClick={() => step(1)}>›</button>
            <span className="gallery__count" aria-live="polite">{index + 1} / {photos.length}</span>
          </>
        )}
      </div>
      {many && (
        <ul className="gallery__thumbs" aria-label="Photos">
          {photos.map((ph, i) => (
            <li key={ph.url}>
              <button type="button" className="gallery__thumb" aria-current={i === index} aria-label={`Show photo ${i + 1}`} onClick={() => setIndex(i)}>
                <img src={ph.thumbnailUrl} alt="" width={80} height={80} loading="lazy" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function DescriptionTab({ detail }: { detail: ProductDetail }) {
  const { data } = useSite();
  const p = detail.product;
  const rows: [string, string][] = [
    ['Condition', p.condition],
    ...(p.grade ? [['Grade', p.grade] as [string, string]] : []),
    ['Warranty', data?.site.warranty ?? ''],
    ['Ships from', data?.site.shipsFrom ?? ''],
    ['Fits', detail.fitsVehicles.length ? `${detail.fitsVehicles.join('; ')} (check your reg above)` : 'Not confirmed yet. Check by part number or ask us.']
  ];
  return (
    <>
      {detail.descriptionHtml ? <ListingHtml html={detail.descriptionHtml} /> : <p className="tabs__text">{detail.description}</p>}
      <dl className="specs">
        {rows.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
      </dl>
      {detail.specifics.length > 0 && (
        <>
          <h3 className="tabs__subhead">Item specifics</h3>
          <dl className="specs">
            {detail.specifics.map((s) => <div key={s.name}><dt>{s.name}</dt><dd>{s.value}</dd></div>)}
          </dl>
        </>
      )}
    </>
  );
}

function FitmentTab({ detail, vehicle }: { detail: ProductDetail; vehicle: Vehicle | null }) {
  return (
    <>
      {detail.fitsVehicles.length > 0 ? (
        <>
          <p className="tabs__text">Our team has confirmed this part fits:</p>
          <ul className="fit-list">{detail.fitsVehicles.map((v) => <li key={v}><Icon name="check" size={18} />{v}</li>)}</ul>
        </>
      ) : (
        <p className="tabs__text">We haven’t confirmed which vehicles this part fits yet. Match it by part number, or <Link to="/request-a-part">send us your reg</Link> and we’ll check.</p>
      )}
      {detail.partNumber && <p className="tabs__text">Part number: <span className="mono">{detail.partNumber}</span></p>}
      {detail.engineCode && <p className="tabs__text">Engine code: <span className="mono">{detail.engineCode}</span></p>}
      {!vehicle && <p className="tabs__text">Add your reg above to see whether it fits your car.</p>}
    </>
  );
}

function DeliveryTab({ shipping }: { shipping: 'PARCEL' | 'PALLET' }) {
  const { data } = useSite();
  return (
    <>
      <dl className="specs">
        <div><dt>This part</dt><dd>{shipping === 'PALLET' ? (data?.deliveryQuotes ? 'Ships by FedEx Freight' : 'Ships by pallet') : 'Ships as a tracked parcel'}</dd></div>
        {data?.deliveryQuotes
          ? <div><dt>Heavy parts</dt><dd>Engines and gearboxes go by FedEx Freight, priced at checkout from your postcode</dd></div>
          : <div><dt>Pallet delivery</dt><dd>£79 per order, for engines and gearboxes</dd></div>}
        <div><dt>Parcel delivery</dt><dd>{data?.deliveryQuotes ? 'FedEx, priced at checkout from your postcode; in the UK the cheapest service is free up to 5 kg, and delivery is never more than £100' : '£9.95, free on orders of £100 or more'}</dd></div>
        <div><dt>Ships from</dt><dd>{data?.site.shipsFrom}</dd></div>
        <div><dt>Warranty</dt><dd>{data?.site.warranty}</dd></div>
        <div><dt>Returns</dt><dd>{data?.site.returns}</dd></div>
      </dl>
    </>
  );
}

/** schema.org data for a part, so search engines and AI assistants can read its price, condition and stock. */
function productLd(detail: ProductDetail) {
  const p = detail.product;
  const condition = /new/i.test(p.condition) ? 'NewCondition' : /reman|recon/i.test(p.condition) ? 'RefurbishedCondition' : 'UsedCondition';
  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: p.name,
    description: detail.description,
    image: detail.photos.length ? detail.photos.map((ph) => ph.url) : [p.imageUrl],
    category: p.categoryName,
    ...(detail.partNumber ? { mpn: detail.partNumber } : {}),
    ...(detail.brand ? { brand: { '@type': 'Brand', name: detail.brand } } : {}),
    offers: {
      '@type': 'Offer',
      url: window.location.href.split('?')[0],
      price: p.price.toFixed(2),
      priceCurrency: 'GBP',
      itemCondition: `https://schema.org/${condition}`,
      availability: `https://schema.org/${p.inStock ? 'InStock' : 'OutOfStock'}`
    }
  };
}
