import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { gbpOrDash, OrderPill, when } from '../components';
import type { Order, OrderStatus } from '../types';

const COURIERS = ['Royal Mail', 'Parcelforce', 'DPD', 'Evri', 'Pallet network', 'Collected'];

/** /admin/orders/:reference : the buyer, what they ordered, and the next step (paid, dispatched or cancelled). */
export function OrderDetailPage() {
  const { reference = '' } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [courier, setCourier] = useState('');
  const [tracking, setTracking] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);

  useEffect(() => {
    adminApi.order(reference).then(setOrder).catch((err) => setError(errorMessage(err, 'This order didn’t load. Try again.')));
  }, [reference]);

  async function change(status: OrderStatus, message: string) {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      setOrder(await adminApi.setOrderStatus(reference, status, courier.trim(), tracking.trim()));
      setNotice(message);
      setConfirmCancel(false);
    } catch (err) {
      setError(errorMessage(err, 'We couldn’t update the order. Try again.'));
    } finally {
      setBusy(false);
    }
  }

  function dispatch(e: FormEvent) {
    e.preventDefault();
    if (!courier.trim()) {
      setError('Choose or enter the courier.');
      return;
    }
    void change('DISPATCHED', 'Marked as dispatched.');
  }

  const open = order && (order.status === 'AWAITING_PAYMENT' || order.status === 'AWAITING_DISPATCH');

  return (
    <AdminLayout title={order ? `Order ${order.reference}` : 'Order'} crumbs={[{ label: 'Orders', to: '/admin/orders' }, { label: reference }]}>
      {notice && <p className="notice notice--ok" role="status">{notice}</p>}
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      {!order && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
      {order && (
        <div className="edit-cols">
          <div className="edit-main">
            <section className="card" aria-labelledby="items-title">
              <div className="card__head">
                <h2 id="items-title" className="card__title">Items</h2>
                <OrderPill status={order.status} />
              </div>
              <ul className="order-lines">
                {order.lines.map((l) => (
                  <li key={l.productId}>
                    <span>{l.quantity} × {l.name}{l.reference && <span className="table__sub mono">{l.reference}</span>}</span>
                    <span>{gbpOrDash(l.lineTotal)}</span>
                  </li>
                ))}
                <li><span>Delivery{order.delivery.label ? ` (${order.delivery.label})` : ''}</span><span>{gbpOrDash(order.delivery.price)}</span></li>
                <li><strong>Total (inc. {gbpOrDash(order.vatIncluded)} VAT)</strong><strong>{gbpOrDash(order.total)}</strong></li>
              </ul>
              {order.notes && <p><strong>Buyer’s note:</strong> {order.notes}</p>}
            </section>

            {order.status === 'AWAITING_PAYMENT' && (
              <section className="card" aria-labelledby="pay-title">
                <h2 id="pay-title" className="card__title">Take payment</h2>
                <p className="admin-muted">Online payment isn’t connected yet. Once the buyer has paid (by phone or bank transfer), mark the order as paid to move it to Awaiting dispatch.</p>
                <div><button type="button" className="btn btn--primary" disabled={busy} onClick={() => void change('AWAITING_DISPATCH', 'Marked as paid. The order is now awaiting dispatch.')}>Mark as paid</button></div>
              </section>
            )}

            {order.status === 'AWAITING_DISPATCH' && (
              <form className="card" aria-labelledby="dispatch-title" onSubmit={dispatch}>
                <h2 id="dispatch-title" className="card__title">Dispatch</h2>
                <div className="field-row">
                  <div className="field">
                    <label htmlFor="courier">Courier</label>
                    <input id="courier" className="input" list="couriers" value={courier} onChange={(e) => setCourier(e.target.value)} maxLength={60} />
                    <datalist id="couriers">{COURIERS.map((c) => <option key={c} value={c} />)}</datalist>
                  </div>
                  <div className="field">
                    <label htmlFor="tracking">Tracking number<span className="field__optional"> (optional)</span></label>
                    <input id="tracking" className="input mono" value={tracking} onChange={(e) => setTracking(e.target.value)} maxLength={60} spellCheck={false} />
                  </div>
                </div>
                <div><button type="submit" className="btn btn--primary" disabled={busy}>Mark as dispatched</button></div>
              </form>
            )}

            {order.dispatch && (
              <section className="card" aria-labelledby="sent-title">
                <h2 id="sent-title" className="card__title">Dispatched</h2>
                <dl className="facts">
                  <div><dt>Courier</dt><dd>{order.dispatch.courier}</dd></div>
                  <div><dt>Tracking</dt><dd className="mono">{order.dispatch.trackingNumber ?? '—'}</dd></div>
                  <div><dt>Sent</dt><dd>{when(order.dispatch.dispatchedAt)}</dd></div>
                </dl>
              </section>
            )}

            {order.status === 'CANCELLED' && <p className="notice">This order was cancelled and its parts were put back in stock.</p>}
          </div>

          <aside className="edit-side">
            <section className="card" aria-labelledby="buyer-title">
              <h2 id="buyer-title" className="card__title">Buyer</h2>
              <ul className="contact">
                <li><strong>{order.customer.name}</strong></li>
                <li><a href={`mailto:${order.customer.email}?subject=${encodeURIComponent(`Your order ${order.reference}`)}`}>{order.customer.email}</a></li>
                <li><a href={`tel:${order.customer.phone.replace(/[^+\d]/g, '')}`}>{order.customer.phone}</a></li>
              </ul>
              <h3 className="card__subtitle">Deliver to</h3>
              <address className="contact">
                <span>{order.address.line1}</span>
                {order.address.line2 && <span>{order.address.line2}</span>}
                <span>{order.address.town}</span>
                <span className="mono">{order.address.postcode}</span>
              </address>
              <p className="admin-muted">Placed {when(order.placedAt)}</p>
            </section>

            {open && (
              <section className="card" aria-labelledby="cancel-title">
                <h2 id="cancel-title" className="card__title">Cancel order</h2>
                <p className="admin-muted">Puts the parts back in stock. This can’t be undone.</p>
                {confirmCancel ? (
                  <div className="request-actions">
                    <button type="button" className="btn btn--dark" disabled={busy} onClick={() => void change('CANCELLED', 'Order cancelled. The parts are back in stock.')}>Yes, cancel the order</button>
                    <button type="button" className="btn btn--outline" onClick={() => setConfirmCancel(false)}>Keep it</button>
                  </div>
                ) : (
                  <div><button type="button" className="btn btn--outline" onClick={() => setConfirmCancel(true)}>Cancel order</button></div>
                )}
              </section>
            )}
          </aside>
        </div>
      )}
    </AdminLayout>
  );
}
