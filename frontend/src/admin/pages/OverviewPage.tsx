import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '../../components/Icons';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, inQueue, QUEUE_FILTERS, WorkPill, type QueueFilter } from '../components';
import { SalesChart } from '../SalesChart';
import type { Dashboard } from '../types';
import { CardsSkeleton } from '../../components/Skeleton';

const QUEUE_SIZE = 6;
const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

/**
 * /admin: the Seller Hub-style overview. Listings, Orders and Sales cards across the top (as on eBay), then the
 * listings to finish and what shoppers couldn't find.
 */
export function OverviewPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<QueueFilter>('all');

  useEffect(() => {
    adminApi.dashboard().then(setData).catch((err) => setError(errorMessage(err, 'The overview didn’t load. Try again.')));
  }, []);

  const rows = data ? data.workQueue.filter((r) => inQueue(r, filter) && r.workStatus !== 'LIVE') : [];
  const overBudget = data && data.lookupBudget > 0 && data.lookupSpend > data.lookupBudget;

  const needsAttention = data ? data.workQueue.filter((r) => r.vehicles === 0 || !r.hasPhoto).length : 0;
  const tiles = data ? [
    { label: 'Active listings', count: data.listings.active, to: '/admin/listings?tab=active' },
    { label: 'Drafts', count: data.listings.drafts, to: '/admin/listings?tab=drafts' },
    { label: 'Out of stock', count: data.listings.outOfStock, to: '/admin/listings?tab=out-of-stock' },
    { label: 'Needs fitment or photos', count: needsAttention, to: '/admin/listings?tab=needs-attention' }
  ] : [];
  const orderLinks = data ? [
    { label: 'Awaiting dispatch', count: data.orders.awaitingDispatch, to: '/admin/orders?status=awaiting-dispatch' },
    { label: 'Awaiting payment', count: data.orders.awaitingPayment, to: '/admin/orders?status=awaiting-payment' },
    { label: 'Dispatched', count: data.orders.dispatched, to: '/admin/orders?status=dispatched' },
    { label: 'Cancelled', count: data.orders.cancelled, to: '/admin/orders?status=cancelled' },
    { label: 'New part requests', count: data.tasks.newPartRequests, to: '/admin/part-requests' },
    { label: `Customers signed in (${data.customers.newThisWeek} new this week)`, count: data.customers.total, to: '/admin/customers' }
  ] : [];

  return (
    <AdminLayout title="Overview" actions={<Link className="btn btn--primary" to="/admin/products/new">Create listing</Link>}>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      {!data && !error && <CardsSkeleton cards={4} />}
      {data && (
        <>
          <div className="hub-grid">
            <section className="card hub-card" aria-labelledby="listings-title">
              <HubCardHead id="listings-title" title="Listings" to="/admin/listings" />
              <div className="tiles">
                {tiles.map((t) => (
                  <Link key={t.label} className="tile" to={t.to}>
                    <span className="tile__label">{t.label}</span>
                    <span className="tile__count">{t.count.toLocaleString('en-GB')}</span>
                    <Icon name="chevronRight" size={22} />
                  </Link>
                ))}
              </div>
              <div><Link className="btn btn--primary hub-card__cta" to="/admin/products/new">Create listing</Link></div>
            </section>

            <section className="card hub-card" aria-labelledby="orders-title">
              <HubCardHead id="orders-title" title="Orders" to="/admin/orders" />
              <ul className="hub-links">
                <li><Link to="/admin/orders">See all orders</Link><span>{data.orders.all}</span></li>
                {orderLinks.map((o) => (
                  <li key={o.to}><Link to={o.to}>{o.label}</Link><span className={o.count === 0 ? 'is-zero' : undefined}>{o.count}</span></li>
                ))}
              </ul>
            </section>

            <section className="card hub-card" aria-labelledby="sales-title">
              <HubCardHead id="sales-title" title="Sales" to="/admin/orders" />
              <SalesChart days={data.dailySales} />
              <ul className="hub-links hub-links--money">
                {data.sales.map((s) => (
                  <li key={s.period}>
                    <span>{s.period}</span>
                    <span>
                      {s.change !== null && (
                        <span className={'change ' + (s.change >= 0 ? 'change--up' : 'change--down')}>
                          <span aria-hidden="true">{s.change >= 0 ? '▲' : '▼'}</span>
                          <span className="sr-only">{s.change >= 0 ? 'up' : 'down'}</span> {Math.abs(s.change).toFixed(1)}%
                        </span>
                      )}
                      <strong>{gbp.format(s.total)}</strong>
                    </span>
                  </li>
                ))}
              </ul>
              <p className="admin-muted">
                Includes delivery and VAT; cancelled orders aren’t counted. Payment isn’t connected yet, so totals include
                orders still awaiting payment. Change is against the period just before.
              </p>
            </section>
          </div>

          <div className="dash-cols">
            <section className="card" aria-labelledby="queue-title">
              <div className="card__head">
                <h2 id="queue-title" className="card__title">Listings to finish</h2>
                <Chips label="Show" options={QUEUE_FILTERS.filter((f) => f.value !== 'live')} value={filter} onChange={setFilter} />
              </div>
              {rows.length === 0 ? (
                <p className="admin-muted">Nothing to finish here.</p>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr><th scope="col">Listing</th><th scope="col">Part number</th><th scope="col">Status</th><th scope="col">Vehicles</th><th scope="col"><span className="sr-only">Action</span></th></tr>
                    </thead>
                    <tbody>
                      {rows.slice(0, QUEUE_SIZE).map((r) => (
                        <tr key={r.id}>
                          <td>{r.name}</td>
                          <td className="mono">{r.partNumber ?? '—'}</td>
                          <td><WorkPill status={r.workStatus} /></td>
                          <td>{r.vehicles}</td>
                          <td className="table__action"><Link to={`/admin/products/${encodeURIComponent(r.id)}`}>Edit<span className="sr-only"> {r.name}</span></Link></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {rows.length > QUEUE_SIZE && (
                <p className="card__foot">Showing {QUEUE_SIZE} of {rows.length}. <Link to="/admin/listings?tab=needs-attention">See them all</Link></p>
              )}
            </section>

            <section id="no-match" className="card" aria-labelledby="nomatch-title" tabIndex={-1}>
              <h2 id="nomatch-title" className="card__title">Shoppers found nothing for</h2>
              <p className="admin-muted">Vehicles searched this week with no matching stock ({data.noMatchSearches} {data.noMatchSearches === 1 ? 'search' : 'searches'}). Plates are hidden.</p>
              {data.noMatch.length === 0 ? (
                <p className="admin-muted nomatch__empty">No unmatched searches in the last 7 days.</p>
              ) : (
                <ul className="nomatch">
                  {data.noMatch.map((n) => (
                    <li key={n.vehicle ?? 'unknown'}>
                      <span>{n.vehicle ?? 'Plates we couldn’t identify'}</span>
                      <span className="admin-muted">{n.searches} {n.searches === 1 ? 'search' : 'searches'}</span>
                    </li>
                  ))}
                </ul>
              )}
              {data.noMatch.length > 0 && (data.buyingTeamEmail ? (
                <a className="card__link" href={buyingTeamMail(data)}>Send list to buying team</a>
              ) : (
                <p className="admin-muted">Add the buying team’s email in <Link to="/admin/settings">Settings</Link> to send them this list.</p>
              ))}
              <p className={'stat__note' + (overBudget ? ' stat__note--bad' : '')}>
                Plate lookups this month: {data.lookupsThisMonth}.{' '}
                {data.lookupBudget > 0
                  ? <>{gbp.format(data.lookupSpend)} of {gbp.format(data.lookupBudget)} cap.</>
                  : <>{gbp.format(data.lookupSpend)} spent. <Link to="/admin/settings">Set a cap</Link></>}
              </p>
            </section>
          </div>
        </>
      )}
    </AdminLayout>
  );
}

/** Opens the staff member's email app with the list filled in. No plates or shopper details are included. */
function buyingTeamMail(data: Dashboard) {
  const lines = data.noMatch.map((n) => `- ${n.vehicle ?? 'Plates we couldn’t identify'}: ${n.searches} ${n.searches === 1 ? 'search' : 'searches'}`);
  const body = `Vehicles shoppers searched in the last 7 days with no matching stock:\n\n${lines.join('\n')}\n`;
  return `mailto:${encodeURIComponent(data.buyingTeamEmail)}?subject=${encodeURIComponent('Stock wanted: unmatched vehicle searches')}&body=${encodeURIComponent(body)}`;
}

/** A card heading that links to its full page, with the chevron eBay uses. */
function HubCardHead({ id, title, to }: { id: string; title: string; to: string }) {
  return (
    <div className="card__head">
      <h2 id={id} className="card__title"><Link to={to} className="hub-card__title">{title}</Link></h2>
      <Link to={to} className="hub-card__more" aria-label={`Open ${title}`}><Icon name="chevronRight" size={20} /></Link>
    </div>
  );
}
