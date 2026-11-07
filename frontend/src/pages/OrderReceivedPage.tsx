import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, hasInvoice, invoiceUrl, money } from '../api/client';
import { countryName, ukVat } from '../api/countries';
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
  const pageTitle = !order ? 'Your order' : order.status === 'AWAITING_PAYMENT' ? (payOnline ? 'Finish paying' : 'Order placed')
    : order.status === 'CANCELLED' ? 'Order cancelled' : 'Order confirmed';

  return (
    <Layout title={pageTitle}>
      <PageTitle title={pageTitle} crumbs={[{ label: 'Checkout', to: '/checkout' }, { label: pageTitle }]} />
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
              <div className={'received__banner' + (order.status === 'AWAITING_PAYMENT' ? ' received__banner--unpaid' : order.status === 'CANCELLED' ? ' received__banner--cancelled' : '')} role="status">
                <span className="received__icon"><Icon name={order.status === 'AWAITING_PAYMENT' ? 'basket' : order.status === 'CANCELLED' ? 'x' : 'check'} size={28} /></span>
                <div>
                  {order.status === 'AWAITING_PAYMENT' ? (
                    <>
                      <h2>{payOnline ? 'Not paid yet: your order isn’t confirmed.' : `Thank you, ${order.customer.name.split(' ')[0]}. We’ll call you to take payment.`}</h2>
                      <p>We’ve reserved these parts for you{payOnline && data?.unpaidHoldMinutes ? ` for ${holdText(data.unpaidHoldMinutes)}` : ''}. {payOnline ? 'Pay below to confirm your order. If you don’t, the parts go back on sale.' : 'Your order is confirmed once you’ve paid.'}</p>
                    </>
                  ) : order.status === 'CANCELLED' ? (
                    <>
                      <h2>This order was cancelled.</h2>
                      <p>If it wasn’t paid in time, the parts went back on sale. Add them to your basket again, or call us.</p>
                    </>
                  ) : (
                    <>
                      <h2>Thank you, {order.customer.name.split(' ')[0]}. Your order is confirmed.</h2>
                      <p>{order.payment?.captureId ? 'Payment received. ' : ''}{data?.orderEmails ? `We’ve emailed your confirmation and invoice to ${order.customer.email}. ` : ''}Keep your order number: you can check its status any time with our help chat{data?.site ? `, or call ${data.site.phone}` : ''}.</p>
                    </>
                  )}
                </div>
              </div>

              {order.status === 'AWAITING_PAYMENT' && payOnline && (
                <section className="received__pay" aria-labelledby="pay-title">
                  <h3 id="pay-title" className="panel__title">Pay {money(order.total)}</h3>
                  <PayPalButtons clientId={data!.paypalClientId} reference={order.reference} onPaid={setOrder} />
                </section>
              )}

              <dl className="received__facts">
                <div><dt>Order number</dt><dd className="mono">{order.reference}</dd></div>
                <div><dt>Date</dt><dd>{dateFormat.format(new Date(order.placedAt))}</dd></div>
                <div><dt>Total</dt><dd>{money(order.total)}</dd></div>
                <div><dt>Payment</dt><dd>{paymentText(order)}</dd></div>
              </dl>

              {order.delivery.method === 'MANUAL' && order.status !== 'CANCELLED' && (
                <div className="notice" role="note">
                  <p><strong>You chose manual delivery.</strong></p>
                  <p>
                    Delivery isn’t included in your total. {order.status === 'AWAITING_PAYMENT' ? 'Once you’ve paid, call' : 'Call'} {data?.site.phone} or
                    email {data?.site.email}, quoting {order.reference}, and we’ll quote you from our other delivery partners
                    {data?.site.couriers ? ` (${data.site.couriers})` : ''}. We’ll send your parts once delivery is agreed.
                  </p>
                </div>
              )}

              {order.delivery.method === 'COLLECT' && order.status !== 'CANCELLED' && !order.dispatch && (
                <div className="notice" role="note">
                  <p><strong>You chose to collect from our store.</strong></p>
                  <p>
                    {data?.site.address && <>Collect from {data.site.address}. </>}
                    {order.status === 'AWAITING_PAYMENT' ? 'Once you’ve paid, we’ll' : 'We’ll'} check your parts and contact you when they’re
                    ready. Please bring your order number, {order.reference}{data?.site ? `, and call ${data.site.phone} if you’d like to check a time` : ''}.
                  </p>
                </div>
              )}

              {order.dispatch && (
                <section className="received__invoice" aria-labelledby="dispatch-title">
                  <span className="received__invoice-icon"><Icon name="truck" size={26} /></span>
                  <div>
                    <h3 id="dispatch-title" className="panel__title">{order.delivery.method === 'COLLECT' ? 'Collected' : 'On Its Way'}</h3>
                    {order.delivery.method === 'COLLECT' ? (
                      <p>Collected from our store on {dateFormat.format(new Date(order.dispatch.dispatchedAt))}.</p>
                    ) : (
                    <p>
                      Sent with <strong>{order.dispatch.courier}</strong> on {dateFormat.format(new Date(order.dispatch.dispatchedAt))}.
                      {order.dispatch.trackingNumber ? <> Tracking number: <strong className="mono">{order.dispatch.trackingNumber}</strong>.</> : ''}
                    </p>
                    )}
                  </div>
                </section>
              )}

              {hasInvoice(order) && (
                <section className="received__invoice" aria-labelledby="invoice-title">
                  <span className="received__invoice-icon"><Icon name="file" size={26} /></span>
                  <div>
                    <h3 id="invoice-title" className="panel__title">Your VAT Invoice</h3>
                    <p>{data?.orderEmails ? <>We’ve emailed it to <strong>{order.customer.email}</strong>. You can also open or save it here at any time.</> : 'Open or save it here at any time, from this page or My orders.'}</p>
                    <div className="received__invoice-actions">
                      <a className="btn btn--primary" href={invoiceUrl(order.reference)} target="_blank" rel="noopener">View Invoice</a>
                      <a className="btn btn--outline" href={invoiceUrl(order.reference, true)}><Icon name="download" size={18} /> Download PDF</a>
                    </div>
                  </div>
                </section>
              )}

              <div className="received__cols">
                <section>
                  <h3 className="panel__title">{order.delivery.method === 'COLLECT' ? 'Your Address' : 'Delivery Address'}</h3>
                  <address>
                    {order.customer.name}<br />
                    {order.address.line1}<br />
                    {order.address.line2 && <>{order.address.line2}<br /></>}
                    {order.address.town}<br />
                    {order.address.postcode && <>{order.address.postcode}<br /></>}
                    {order.address.country !== 'GB' && countryName(order.address.country)}
                  </address>
                  {order.notes && <p className="received__notes">Notes: {order.notes}</p>}
                </section>
                <section>
                  <h3 className="panel__title">Contact</h3>
                  <p>{order.customer.email}<br />{order.customer.phone}</p>
                </section>
              </div>

              <p>Questions about your order? Call {data?.site.phone} or email {data?.site.email}, quoting {order.reference}.</p>
              <Link className="btn btn--outline" to="/products">Continue Shopping</Link>
            </div>

            <aside className="panel" aria-labelledby="received-summary-title">
              <h2 id="received-summary-title" className="panel__title">Order Summary</h2>
              <LineList lines={order.lines} />
              <Totals totals={order} export={!ukVat(order.address.country)} />
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
