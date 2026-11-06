import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, hasInvoice, invoiceUrl, money } from '../api/client';
import type { Order, OrderStatus } from '../api/types';
import { EmailSignIn } from '../components/EmailSignIn';
import { GoogleSignInButton } from '../components/GoogleSignInButton';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { Skeleton, SkeletonText } from '../components/Skeleton';
import { useAccount } from '../state/AccountContext';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

const joined = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' });
const placed = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

const STATUS: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: 'Awaiting payment',
  AWAITING_DISPATCH: 'Paid, preparing to dispatch',
  DISPATCHED: 'Dispatched',
  CANCELLED: 'Cancelled'
};

/** /account: sign in with Google (or email, once it's switched on), or the signed-in shopper's details and recent searches. */
export function AccountPage() {
  const { customer, token, signInWithGoogle, signOut, searches, clearSearches } = useAccount();
  const { data } = useSite();
  const { wishlist } = useStore();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  // ?next=/checkout: back to where the shopper was once they're signed in (only this site's own pages).
  const [params] = useSearchParams();
  const next = params.get('next');
  const backTo = next && next.startsWith('/') && !next.startsWith('//') ? next : null;
  useEffect(() => {
    if (customer && backTo) navigate(backTo, { replace: true });
  }, [customer, backTo, navigate]);

  async function onCredential(credential: string) {
    setError('');
    setBusy(true);
    try {
      await signInWithGoogle(credential);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'We couldn’t sign you in with Google. Try again.');
    } finally {
      setBusy(false);
    }
  }

  const clientId = data?.googleClientId ?? '';
  const emailSignIn = data?.emailSignIn ?? false;
  const orders = useMyOrders(token);

  return (
    <Layout title="My Account">
      <PageTitle title="My Account" crumbs={[{ label: 'My Account' }]} />
      <div className="container page-body account">
        {customer === undefined && (
          <div className="account__card" aria-busy="true" aria-label="Checking your account">
            <Skeleton width="50%" height={26} /><SkeletonText lines={2} /><Skeleton width={240} height={44} radius={4} />
          </div>
        )}

        {customer === null && (
          <section className="account__card" aria-labelledby="signin-title">
            <h2 id="signin-title" className="account__title">{backTo === '/checkout' ? 'Sign In to Check Out' : 'Sign In or Create an Account'}</h2>
            {backTo === '/checkout' && <p>You need an account to place an order, so you can follow it and find your invoice later. Your basket is kept.</p>}
            <p>{emailSignIn
              ? 'Use your Google account in one click, or your email address and a password.'
              : 'Use your Google account. It’s one click, and there’s no new password to remember.'}</p>
            {error && <p className="notice notice--error" role="alert">{error}</p>}
            {!data ? <Skeleton width={240} height={44} radius={4} /> : clientId ? (
              <div aria-busy={busy}>
                <GoogleSignInButton clientId={clientId} onCredential={(c) => void onCredential(c)} />
                {busy && <p className="page-body__note">Signing you in…</p>}
              </div>
            ) : !emailSignIn && (
              <p className="notice">Google sign-in isn’t switched on yet.{data.signInToBuy ? ` Call us on ${data.site.phone} to order.` : ' You can still shop and check out without an account.'}</p>
            )}
            {emailSignIn && (
              <>
                {clientId && <p className="account__or"><span>or</span></p>}
                <EmailSignIn />
              </>
            )}
            <p className="page-body__note">
              {clientId ? 'We only use your name, email and profile photo from Google. ' : ''}{data?.signInToBuy ? 'You need an account to check out.' : 'You can shop and check out without an account.'}
            </p>
          </section>
        )}

        {customer && (
          <div className="account__grid">
            <section className="account__card" aria-labelledby="profile-title">
              <div className="account__profile">
                {customer.pictureUrl
                  ? <img src={customer.pictureUrl} alt="" width={56} height={56} referrerPolicy="no-referrer" />
                  : <span className="account__avatar" aria-hidden="true">{customer.name.charAt(0).toUpperCase()}</span>}
                <div>
                  <h2 id="profile-title" className="account__title">{customer.name}</h2>
                  <p>{customer.email}</p>
                  <p className="page-body__note">Member since {joined.format(new Date(customer.joinedAt))} · Signed in with {customer.signInMethod === 'EMAIL' ? 'email' : 'Google'}</p>
                </div>
              </div>
              <div className="empty__actions">
                <Link className="btn btn--outline" to="/wishlist">Wishlist ({wishlist.length})</Link>
                <button type="button" className="btn btn--outline" onClick={() => void signOut()}>Sign Out</button>
              </div>
            </section>

            <section className="account__card account__card--wide" aria-labelledby="orders-title">
              <h2 id="orders-title" className="account__title">My Orders</h2>
              {orders === undefined && <SkeletonText lines={3} />}
              {orders === null && <p className="notice notice--error" role="alert">We couldn’t load your orders. Refresh the page to try again.</p>}
              {orders && orders.length === 0 && (
                <p className="page-body__note">No orders yet. Orders you place while signed in, or with {customer.email}, will appear here.</p>
              )}
              {orders && orders.length > 0 && (
                <ul className="my-orders">
                  {orders.map((o) => (
                    <li key={o.reference} className="my-orders__item">
                      <div className="my-orders__head">
                        <Link to={`/order-received/${o.reference}`} className="my-orders__ref">{o.reference}</Link>
                        <span className={`my-orders__status my-orders__status--${o.status.toLowerCase()}`}>{STATUS[o.status]}</span>
                      </div>
                      <p className="page-body__note">
                        {placed.format(new Date(o.placedAt))} · {o.lines.reduce((n, l) => n + l.quantity, 0)} item{o.lines.reduce((n, l) => n + l.quantity, 0) === 1 ? '' : 's'} · {money(o.total)}
                      </p>
                      <p className="my-orders__lines">{o.lines.map((l) => (l.quantity > 1 ? `${l.quantity} × ${l.name}` : l.name)).join(', ')}</p>
                      {hasInvoice(o) && (
                        <p className="page-body__note"><a href={invoiceUrl(o.reference)} target="_blank" rel="noopener">VAT Invoice (PDF)</a></p>
                      )}
                      {o.dispatch && (
                        <p className="page-body__note">
                          Sent with {o.dispatch.courier}{o.dispatch.trackingNumber ? `, tracking number ${o.dispatch.trackingNumber}` : ''}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="account__card" aria-labelledby="searches-title">
              <h2 id="searches-title" className="account__title">Recent Searches</h2>
              {searches.length === 0 ? (
                <p className="page-body__note">Your searches will appear here so you can run them again.</p>
              ) : (
                <>
                  <ul className="recent-searches">
                    {searches.map((q) => (
                      <li key={q}><Link to={`/products?q=${encodeURIComponent(q)}`}>{q}</Link></li>
                    ))}
                  </ul>
                  <button type="button" className="link-btn" onClick={clearSearches}>Clear Search History</button>
                </>
              )}
            </section>
          </div>
        )}
      </div>
    </Layout>
  );
}

/** The signed-in shopper's orders: undefined while loading, null if they couldn't be loaded. */
function useMyOrders(token: string | null) {
  const [orders, setOrders] = useState<Order[] | null | undefined>(undefined);
  useEffect(() => {
    if (!token) return;
    let live = true;
    setOrders(undefined);
    api.myOrders(token).then((list) => { if (live) setOrders(list); }).catch(() => { if (live) setOrders(null); });
    return () => { live = false; };
  }, [token]);
  return orders;
}
