import { useState } from 'react';
import { Link } from 'react-router-dom';
import { GoogleSignInButton } from '../components/GoogleSignInButton';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { useAccount } from '../state/AccountContext';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

const joined = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' });

/** /account: sign in with Google, or the signed-in shopper's details and recent searches. */
export function AccountPage() {
  const { customer, signInWithGoogle, signOut, searches, clearSearches } = useAccount();
  const { data } = useSite();
  const { wishlist } = useStore();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

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

  return (
    <Layout title="My account">
      <PageTitle title="My account" crumbs={[{ label: 'My account' }]} />
      <div className="container page-body account">
        {customer === undefined && <p aria-busy="true">Checking your account…</p>}

        {customer === null && (
          <section className="account__card" aria-labelledby="signin-title">
            <h2 id="signin-title" className="account__title">Sign in or create an account</h2>
            <p>Use your Google account. It’s one click, and there’s no new password to remember.</p>
            {error && <p className="notice notice--error" role="alert">{error}</p>}
            {!data ? <p aria-busy="true">Loading…</p> : clientId ? (
              <div aria-busy={busy}>
                <GoogleSignInButton clientId={clientId} onCredential={(c) => void onCredential(c)} />
                {busy && <p className="page-body__note">Signing you in…</p>}
              </div>
            ) : (
              <p className="notice">Google sign-in isn’t switched on yet. You can still shop and check out without an account.</p>
            )}
            <p className="page-body__note">We only use your name, email and profile photo from Google. You can shop and check out without an account.</p>
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
                  <p className="page-body__note">Member since {joined.format(new Date(customer.joinedAt))} · Signed in with Google</p>
                </div>
              </div>
              <div className="empty__actions">
                <Link className="btn btn--outline" to="/wishlist">Wishlist ({wishlist.length})</Link>
                <button type="button" className="btn btn--outline" onClick={() => void signOut()}>Sign out</button>
              </div>
            </section>

            <section className="account__card" aria-labelledby="searches-title">
              <h2 id="searches-title" className="account__title">Recent searches</h2>
              {searches.length === 0 ? (
                <p className="page-body__note">Your searches will appear here so you can run them again.</p>
              ) : (
                <>
                  <ul className="recent-searches">
                    {searches.map((q) => (
                      <li key={q}><Link to={`/products?q=${encodeURIComponent(q)}`}>{q}</Link></li>
                    ))}
                  </ul>
                  <button type="button" className="link-btn" onClick={clearSearches}>Clear search history</button>
                </>
              )}
            </section>
          </div>
        )}
      </div>
    </Layout>
  );
}
