import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, money } from '../api/client';
import type { Order } from '../api/types';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { LineList, Totals } from '../components/OrderSummary';
import { PageTitle } from '../components/PageTitle';
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
                  <h2>Thank you, {order.customer.name.split(' ')[0]}. Your order has been received.</h2>
                  <p>We’ve reserved these parts for you. Keep your order number for any questions.</p>
                </div>
              </div>

              <dl className="received__facts">
                <div><dt>Order number</dt><dd className="mono">{order.reference}</dd></div>
                <div><dt>Date</dt><dd>{dateFormat.format(new Date(order.placedAt))}</dd></div>
                <div><dt>Total</dt><dd>{money(order.total)}</dd></div>
                <div><dt>Payment</dt><dd>Not taken (payment not connected yet)</dd></div>
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
