import { useEffect, useState, type FormEvent } from 'react';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import type { SettingsView } from '../types';
import { CardsSkeleton } from '../../components/Skeleton';

export function SettingsPage() {
  const [view, setView] = useState<SettingsView | null>(null);
  const [loadError, setLoadError] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);

  function fill(v: SettingsView) {
    setView(v);
    setEmail(v.values.buyingTeamEmail);
  }

  useEffect(() => {
    adminApi.settings().then(fill).catch((err) => setLoadError(errorMessage(err, 'Settings didn’t load. Try again.')));
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSaved('');
    setBusy(true);
    try {
      fill(await adminApi.saveSettings({ buyingTeamEmail: email.trim() }));
      setSaved('Settings saved.');
    } catch (err) {
      setError(errorMessage(err, 'We couldn’t save the settings. Try again.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AdminLayout title="Settings">
      {loadError && <p className="notice notice--error" role="alert">{loadError}</p>}
      {!view && !loadError && <CardsSkeleton cards={2} />}
      {view && (
        <div className="settings">
          <form className="card" onSubmit={submit} noValidate aria-labelledby="buying-title">
            <h2 id="buying-title" className="card__title">Buying team</h2>
            {saved && <p className="notice notice--ok" role="status">{saved}</p>}
            {error && <p className="notice notice--error" role="alert">{error}</p>}
            <div className="field">
              <label htmlFor="buying-email">Buying team email<span className="field__optional"> (optional)</span></label>
              <input id="buying-email" className="input" type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={200} aria-describedby="buying-hint" />
              <p id="buying-hint" className="field__hint">“Send list to buying team” on the dashboard opens an email to this address.</p>
            </div>
            <div><button type="submit" className="btn btn--primary" disabled={busy}>{busy ? 'Saving…' : 'Save settings'}</button></div>
          </form>

          <section className="card" aria-labelledby="server-title">
            <h2 id="server-title" className="card__title">This server</h2>
            <dl className="facts facts--stack">
              <div><dt>Signed in as</dt><dd>{view.staffName} ({view.staffEmail})</dd></div>
              <div><dt>Sign-in</dt><dd>Email and password for the master account (the ADMIN_EMAIL and ADMIN_PASSWORD secrets). Sessions last {view.sessionHours} hours.</dd></div>
              <div><dt>Vehicle data</dt><dd>{view.demoPlates
                ? `Demo data, ${view.demoVehicles} sample vehicles. Add the DVSA MOT History API keys to look up real plates.`
                : 'Real UK plates from the DVSA MOT History API. Each plate is looked up once and remembered for 30 days.'}</dd></div>
              <div><dt>Storage</dt><dd>Listings, orders, invoices, customers, baskets, part requests and these settings are kept in the database. Photos are kept in Cloudflare R2.</dd></div>
            </dl>
          </section>
        </div>
      )}
    </AdminLayout>
  );
}
