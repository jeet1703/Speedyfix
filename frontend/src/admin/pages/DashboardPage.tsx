import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, inQueue, QUEUE_FILTERS, WorkPill, type QueueFilter } from '../components';
import type { Dashboard } from '../types';

const QUEUE_SIZE = 8;
const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<QueueFilter>('all');

  useEffect(() => {
    adminApi.dashboard().then(setData).catch((err) => setError(errorMessage(err, 'The dashboard didn’t load. Try again.')));
  }, []);

  const rows = data ? data.workQueue.filter((r) => inQueue(r, filter)) : [];
  const overBudget = data && data.lookupBudget > 0 && data.lookupSpend > data.lookupBudget;

  return (
    <AdminLayout title="Catalogue" actions={<Link className="btn btn--primary" to="/admin/products/new">Add part</Link>}>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      {!data && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
      {data && (
        <>
          <div className="stats">
            <div className="card stat">
              <h2 className="stat__label">Need fitment</h2>
              <p className="stat__value">{data.needFitment}</p>
              <Link to="/admin/products?filter=needs-fitment">Review queue</Link>
            </div>
            <div className="card stat">
              <h2 className="stat__label">Missing photos</h2>
              <p className="stat__value">{data.missingPhotos}</p>
              <Link to="/admin/products?filter=missing-photo">Review queue</Link>
            </div>
            <div className="card stat">
              <h2 className="stat__label">Searches with no match (7 days)</h2>
              <p className="stat__value">{data.noMatchSearches}</p>
              <a href="#no-match">See what shoppers wanted</a>
            </div>
            <div className="card stat">
              <h2 className="stat__label">Plate lookups this month</h2>
              <p className="stat__value">{data.lookupsThisMonth}</p>
              <p className={'stat__note' + (overBudget ? ' stat__note--bad' : '')}>
                {data.lookupBudget > 0
                  ? <>{gbp.format(data.lookupSpend)} of {gbp.format(data.lookupBudget)} cap</>
                  : <>{gbp.format(data.lookupSpend)} spent. <Link to="/admin/settings">Set a cap</Link></>}
              </p>
            </div>
          </div>

          <div className="dash-cols">
            <section className="card" aria-labelledby="queue-title">
              <div className="card__head">
                <h2 id="queue-title" className="card__title">Work queue</h2>
                <Chips label="Show" options={QUEUE_FILTERS.filter((f) => f.value !== 'live')} value={filter} onChange={setFilter} />
              </div>
              {rows.length === 0 ? (
                <p className="admin-muted">Nothing in this queue.</p>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr><th scope="col">Part</th><th scope="col">OE number</th><th scope="col">Status</th><th scope="col">Vehicles</th><th scope="col"><span className="sr-only">Action</span></th></tr>
                    </thead>
                    <tbody>
                      {rows.slice(0, QUEUE_SIZE).map((r) => (
                        <tr key={r.id}>
                          <td>{r.name}</td>
                          <td className="mono">{r.oeNumber ?? '—'}</td>
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
                <p className="card__foot">
                  Showing {QUEUE_SIZE} of {rows.length}. <Link to={`/admin/products${filter === 'all' ? '' : `?filter=${filter}`}`}>See them all</Link>
                </p>
              )}
            </section>

            <section id="no-match" className="card" aria-labelledby="nomatch-title" tabIndex={-1}>
              <h2 id="nomatch-title" className="card__title">Shoppers found nothing for</h2>
              <p className="admin-muted">Vehicles searched this week with no matching stock. Plates are hidden.</p>
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
