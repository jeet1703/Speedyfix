import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { gbpOrDash, OrderPill, when } from '../components';
import type { InvoiceStatus, Order, OrderStatus } from '../types';
import { invoiceUrl } from '../../api/client';
import { countryName, ukVat } from '../../api/countries';
import { CardsSkeleton } from '../../components/Skeleton';

const EMAIL_KINDS: Record<InvoiceStatus['emails'][number]['kind'], string> = {
  CUSTOMER_INVOICE: 'Invoice to the buyer',
  STAFF_NEW_ORDER: 'Staff copy',
  CUSTOMER_CANCELLED: 'Cancellation to the buyer',
  STAFF_PAYMENT_CHECK: 'Payment check to staff'
};

/** "sent 3 Nov, 22:19", "retrying (HTTP 503)". */
function emailState(e: InvoiceStatus['emails'][number]) {
  if (e.status === 'SENT') return `sent ${when(e.sentAt!)}`;
  if (e.status === 'FAILED') return `not sent${e.lastError ? ` (${e.lastError})` : ''}`;
  return e.attempts > 0 ? `retrying${e.lastError ? ` (${e.lastError})` : ''}` : 'sending…';
}

const COURIERS = ['FedEx', 'UPS', 'Royal Mail', 'DHL', 'DPD', 'Pallet network', 'Collected'];

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
  const [invoice, setInvoice] = useState<InvoiceStatus | null>(null);

  useEffect(() => {
    adminApi.order(reference).then(setOrder).catch((err) => setError(errorMessage(err, 'This order didn’t load. Try again.')));
  }, [reference]);

  // The invoice is numbered once the order is paid, so look again whenever its status changes.
  useEffect(() => {
    if (!order) return;
    adminApi.orderInvoice(order.reference).then(setInvoice).catch(() => setInvoice(null));
  }, [order?.reference, order?.status]);

  async function emailInvoice() {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const status = await adminApi.emailInvoice(reference);
      setInvoice(status);
      const sent = status.emails.find((e) => e.kind === 'CUSTOMER_INVOICE');
      if (sent?.status === 'SENT') setNotice(`Invoice emailed to ${sent.recipient}.`);
      else setError(`The invoice wasn’t sent${sent?.lastError ? ` (${sent.lastError})` : ''}. It will be tried again shortly.`);
    } catch (err) {
      setError(errorMessage(err, 'We couldn’t email the invoice. Try again.'));
    } finally {
      setBusy(false);
    }
  }

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
      {!order && !error && <CardsSkeleton cards={3} />}
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
                <li><strong>Total {ukVat(order.address.country) ? `(inc. ${gbpOrDash(order.vatIncluded)} VAT)` : '(no UK VAT: export)'}</strong><strong>{gbpOrDash(order.total)}</strong></li>
              </ul>
              {order.delivery.method === 'MANUAL' && (
                <p><strong>Manual delivery:</strong> the buyer didn’t want FedEx and hasn’t paid for delivery. They’ll contact you for a quote from another courier; agree it and take payment before sending.</p>
              )}
              {order.notes && <p><strong>Buyer’s note:</strong> {order.notes}</p>}
              {order.payment?.captureId && (
                <p className="admin-muted">
                  Paid with PayPal{order.payment.paidAt ? ` on ${new Date(order.payment.paidAt).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })}` : ''}.
                  PayPal transaction <span className="mono">{order.payment.captureId}</span> (find it in PayPal under Activity; refunds are made there).
                </p>
              )}
            </section>

            {order.status === 'AWAITING_PAYMENT' && (
              <section className="card" aria-labelledby="pay-title">
                <h2 id="pay-title" className="card__title">Take payment</h2>
                <p className="admin-muted">The buyer can pay online with PayPal or a card from their order page; the order moves to Awaiting dispatch by itself once PayPal confirms it. If they paid another way (by phone or bank transfer), mark it as paid.</p>
                {order.payment && !order.payment.captureId && <p className="admin-muted">The buyer has started a PayPal payment but hasn’t finished it.</p>}
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
                {order.address.postcode && <span className="mono">{order.address.postcode}</span>}
                {order.address.country !== 'GB' && <span><strong>{countryName(order.address.country)}</strong></span>}
              </address>
              {!ukVat(order.address.country) && <p className="admin-muted">Export: no UK VAT charged. Keep proof of export (the FedEx tracking) for HMRC, and book it with a commercial invoice for customs.</p>}
              <p className="admin-muted">Placed {when(order.placedAt)}</p>
            </section>

            {invoice?.invoiceNumber && (
              <section className="card" aria-labelledby="invoice-title">
                <h2 id="invoice-title" className="card__title">Invoice {invoice.invoiceNumber}</h2>
                <p className="admin-muted">VAT invoice, dated {when(invoice.issuedAt!)}.</p>
                <div className="request-actions">
                  <a className="btn btn--outline" href={invoiceUrl(order.reference)} target="_blank" rel="noopener">View PDF</a>
                  {invoice.emailOn && <button type="button" className="btn btn--outline" disabled={busy} onClick={() => void emailInvoice()}>Email it again</button>}
                </div>
                {!invoice.emailOn && <p className="admin-muted">Email isn’t set up yet, so it wasn’t emailed. Download it and send it yourself if the buyer asks.</p>}
              </section>
            )}

            {invoice && invoice.emails.length > 0 && (
              <section className="card" aria-labelledby="emails-title">
                <h2 id="emails-title" className="card__title">Emails</h2>
                <ul className="contact">
                  {invoice.emails.map((e) => (
                    <li key={e.id}>
                      <strong>{EMAIL_KINDS[e.kind]}:</strong> {emailState(e)}
                      {e.kind === 'STAFF_PAYMENT_CHECK' && e.details && <span className="admin-muted"> {e.details}</span>}
                    </li>
                  ))}
                </ul>
              </section>
            )}

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
