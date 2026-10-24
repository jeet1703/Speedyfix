import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, gbpOrDash, OrderPill, when } from '../components';
import type { Order, OrderStatus } from '../types';

type Filter = 'all' | 'awaiting-payment' | 'awaiting-dispatch' | 'dispatched' | 'cancelled';

const FILTERS: { value: Filter; label: string; status?: OrderStatus }[] = [
  { value: 'all', label: 'All' },
  { value: 'awaiting-payment', label: 'Awaiting payment', status: 'AWAITING_PAYMENT' },
  { value: 'awaiting-dispatch', label: 'Awaiting dispatch', status: 'AWAITING_DISPATCH' },
  { value: 'dispatched', label: 'Dispatched', status: 'DISPATCHED' },
  { value: 'cancelled', label: 'Cancelled', status: 'CANCELLED' }
];

/** /admin/orders?status=&q= : every order, newest first. */
export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState('');
  const filter = (FILTERS.find((f) => f.value === params.get('status'))?.value ?? 'all') as Filter;
  const q = params.get('q') ?? '';

  useEffect(() => {
    adminApi.orders().then(setOrders).catch((err) => setError(errorMessage(err, 'The orders didn’t load. Try again.')));
  }, []);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value && value !== 'all') next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  }

  const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const searched = (orders ?? []).filter((o) => {
    const text = [o.reference, o.customer.name, o.customer.email, o.address.postcode, ...o.lines.map((l) => l.name)].join(' ').toLowerCase();
    return words.every((w) => text.includes(w));
  });
  const status = FILTERS.find((f) => f.value === filter)?.status;
  const shown = searched.filter((o) => !status || o.status === status);
  const options = FILTERS.map((f) => ({ value: f.value, label: f.label, count: searched.filter((o) => !f.status || o.status === f.status).length }));

  return (
    <AdminLayout title="Orders">
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <section className="card" aria-label="Orders">
        <div className="card__head card__head--wrap">
          <Chips label="Orders" options={options} value={filter} onChange={(v) => update('status', v)} />
          <div className="admin-search">
            <label className="sr-only" htmlFor="order-search">Search orders</label>
            <input id="order-search" className="input" type="search" placeholder="Search by order number, buyer, postcode or part"
              value={q} onChange={(e) => update('q', e.target.value)} />
          </div>
        </div>
        {!orders && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
        {orders && shown.length === 0 && <p className="admin-muted">{orders.length === 0 ? 'No orders yet.' : 'No orders match.'}</p>}
        {shown.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Order</th>
                  <th scope="col">Buyer</th>
                  <th scope="col">Items</th>
                  <th scope="col" className="num">Total</th>
                  <th scope="col">Status</th>
                  <th scope="col"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {shown.map((o) => (
                  <tr key={o.reference}>
                    <td className="mono">{o.reference}<span className="table__sub">{when(o.placedAt)}</span></td>
                    <td>{o.customer.name}<span className="table__sub">{o.address.town}, {o.address.postcode}</span></td>
                    <td>
                      {o.lines[0]?.name}
                      {o.lines.length > 1 && <span className="table__sub">and {o.lines.length - 1} more</span>}
                    </td>
                    <td className="num">{gbpOrDash(o.total)}</td>
                    <td><OrderPill status={o.status} /></td>
                    <td className="table__action"><Link to={`/admin/orders/${encodeURIComponent(o.reference)}`}>View<span className="sr-only"> order {o.reference}</span></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </AdminLayout>
  );
}
