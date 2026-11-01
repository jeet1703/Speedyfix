import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, money } from '../api/client';
import type { Order } from '../api/types';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { LineList, Totals } from '../components/OrderSummary';
import { PageTitle } from '../components/PageTitle';
import { PayPalButtons } from '../components/PayPalButtons';
import { holdText } from './CheckoutPage';
import { Skeleton, SkeletonText } from '../components/Skeleton';
import { useSite } from '../state/SiteContext';

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export function OrderReceivedPage() {
  const { reference = '' } = useParams();
  const { data } = useSite();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.getOrder(reference).then(setOrder).catch((e: Error) => setError(e.message));
  }, [reference]);

  const payOnline = !!data?.paypalClientId;

  return (
    <Layout title="Order received">
      <PageTitle title="Order received" crumbs={[{ label: 'Checkout', to: '/checkout' }, { label: 'Order received' }]} />
      <div className="container">
        {error && (
          <div className="empty empty--page">
            <h2>{error}</h2>
            <p>If you placed an order, call {data?.site.phone} and we’ll find it for you.</p>
          </div>
        )}
        {!order && !error && (
          <div className="two-col page-body" aria-busy="true" aria-label="Loading your order">
            <div className="skeleton-text"><Skeleton height={96} radius={6} /><Skeleton height={80} radius={6} /><SkeletonText lines={4} /></div>
            <Skeleton height={320} radius={6} />
          </div>
        )}
        {order && (
          <div className="two-col">
            <div className="received">
              <div className="received__banner" role="status">
                <span className="received__icon"><Icon name="check" size={28} /></span>
                <div>
                  {order.status === 'AWAITING_PAYMENT' ? (
                    <>
                      <h2>Thank you, {order.customer.name.split(' ')[0]}. One last step: payment.</h2>
                      <p>We’ve reserved these parts for you{payOnline && data?.unpaidHoldMinutes ? ` for ${holdText(data.unpaidHoldMinutes)}` : ''}. {payOnline ? 'Pay below to confirm your order.' : 'We’ll call you to take payment.'}</p>
                    </>
                  ) : order.status === 'CANCELLED' ? (
                    <>
                      <h2>This order was cancelled.</h2>
                      <p>If it wasn’t paid in time, the parts went back on sale. Add them to your basket again, or call us.</p>
                    </>
                  ) : (
                    <>
                      <h2>Thank you, {order.customer.name.split(' ')[0]}. Your order is confirmed.</h2>
                      <p>{order.payment?.captureId ? 'Payment received. ' : ''}Keep your order number: you can check its status any time with our help chat{data?.site ? `, or call ${data.site.phone}` : ''}.</p>
                    </>
                  )}
                </div>
              </div>

              {order.status === 'AWAITING_PAYMENT' && payOnline && (
                <section className="received__pay" aria-labelledby="pay-title">
                  <h3 id="pay-title" className="panel__title">Pay {money(order.total)}</h3>
                  <PayPalButtons clientId={data!.paypalClientId} order={order} onPaid={setOrder} />
                </section>
              )}

              <dl className="received__facts">
                <div><dt>Order number</dt><dd className="mono">{order.reference}</dd></div>
                <div><dt>Date</dt><dd>{dateFormat.format(new Date(order.placedAt))}</dd></div>
                <div><dt>Total</dt><dd>{money(order.total)}</dd></div>
                <div><dt>Payment</dt><dd>{paymentText(order)}</dd></div>
              </dl>

              <div className="received__cols">
                <section>
                  <h3 className="panel__title">Delivery address</h3>
                  <address>
                    {order.customer.name}<br />
                    {order.address.line1}<br />
                    {order.address.line2 && <>{order.address.line2}<br /></>}
                    {order.address.town}<br />
                    {order.address.postcode}
                  </address>
                  {order.notes && <p className="received__notes">Notes: {order.notes}</p>}
                </section>
                <section>
                  <h3 className="panel__title">Contact</h3>
                  <p>{order.customer.email}<br />{order.customer.phone}</p>
                </section>
              </div>

              <p>Questions about your order? Call {data?.site.phone} or email {data?.site.email}, quoting {order.reference}.</p>
              <Link className="btn btn--outline" to="/products">Continue shopping</Link>
            </div>

            <aside className="panel" aria-labelledby="received-summary-title">
              <h2 id="received-summary-title" className="panel__title">Order summary</h2>
              <LineList lines={order.lines} />
              <Totals totals={order} />
            </aside>
          </div>
        )}
      </div>
    </Layout>
  );
}

function paymentText(order: Order) {
  if (order.payment?.captureId) return 'Paid with PayPal';
  if (order.status === 'AWAITING_PAYMENT') return 'Awaiting payment';
  if (order.status === 'CANCELLED') return 'Not taken';
  return 'Paid';
}
