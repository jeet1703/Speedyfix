import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, money, price } from '../api/client';
import type { ProductDetail, Vehicle } from '../api/types';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { PlateField } from '../components/PlateField';
import { ProductCard } from '../components/ProductCard';
import { SectionHeading } from '../components/SectionHeading';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

type Tab = 'description' | 'fitment' | 'delivery';

export function ProductPage() {
  const { id = '' } = useParams();
  const { vehicle } = useStore();
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
      <Layout title="Part not found">
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
    return <Layout><div className="container state" aria-busy="true"><p>Loading the part…</p></div></Layout>;
  }

  const p = detail.product;
  return (
    <Layout title={p.name}>
      <PageTitle title={p.categoryName} heading={false} crumbs={[
        { label: 'Products', to: '/products' },
        { label: p.categoryName, to: `/products?category=${p.categoryId}` },
        { label: p.name }
      ]} />

      <div className="container product">
        <div className="product__gallery">
          <div className="photo-frame">
            <img src={p.imageUrl.replace('w=800&h=800', 'w=1200&h=1000')} alt={p.name} width={600} height={500} />
            <span className="photo-frame__note">Sample photo. The client’s own photo of this part replaces it before launch.</span>
          </div>
        </div>

        <div className="product__summary">
          <Link className="product__category" to={`/products?category=${p.categoryId}`}>{p.categoryName}</Link>
          <h1 className="h-display product__title">{p.name}</h1>
          <p className="product__price">
            {price(p.price)} <span>inc. VAT ({money(detail.priceExVat)} ex. VAT)</span>
          </p>
          <p className={'product__stock' + (p.inStock ? '' : ' is-out')}>
            {p.inStock ? `In stock: ${p.stock} available` : 'Out of stock'}
          </p>
          <p className="product__lead">{detail.description}</p>

          <FitCheck detail={detail} vehicle={vehicle} />
          <BuyBox detail={detail} />

          <dl className="product__meta">
            <div><dt>SKU:</dt><dd>{p.id.toUpperCase()}</dd></div>
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
          {([['description', 'Description'], ['fitment', 'Fitment'], ['delivery', 'Delivery and returns']] as [Tab, string][]).map(([key, label]) => (
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
        <p className={`fit-check__result fit-check__result--${fit === 'FITS' ? 'ok' : fit === 'DOES_NOT_FIT' ? 'no' : 'ask'}`} role="status">
          <Icon name={fit === 'FITS' ? 'check' : fit === 'DOES_NOT_FIT' ? 'x' : 'ask'} size={20} />
          <span>
            {fit === 'FITS' && <>Fits your {vehicle.make} {vehicle.model}, {vehicle.engine}.</>}
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
      <button type="button" className="btn btn--primary buy__add" onClick={add} disabled={adding || available === 0}>
        {!p.inStock ? 'Out of stock' : available === 0 ? 'All stock is in your basket' : adding ? 'Adding…' : 'Add to basket'}
      </button>
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
      <li><Icon name="truck" size={20} />{shipping === 'PALLET' ? 'Pallet delivery across the UK, £79.' : 'Tracked parcel delivery, £9.95 or free from £100.'}</li>
    </ul>
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
      <p className="tabs__text">{detail.description}</p>
      <dl className="specs">
        {rows.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
      </dl>
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
        <div><dt>This part</dt><dd>{shipping === 'PALLET' ? 'Ships by pallet' : 'Ships as a tracked parcel'}</dd></div>
        <div><dt>Pallet delivery</dt><dd>£79 per order, for engines and gearboxes</dd></div>
        <div><dt>Parcel delivery</dt><dd>£9.95, free on orders of £100 or more</dd></div>
        <div><dt>Ships from</dt><dd>{data?.site.shipsFrom}</dd></div>
        <div><dt>Warranty</dt><dd>{data?.site.warranty}</dd></div>
        <div><dt>Returns</dt><dd>[Returns policy from the client]</dd></div>
      </dl>
    </>
  );
}
