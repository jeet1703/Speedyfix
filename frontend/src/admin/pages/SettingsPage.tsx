import { useEffect, useState, type FormEvent } from 'react';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import type { SettingsView } from '../types';
import { CardsSkeleton } from '../../components/Skeleton';

const moneyInput = (value: string) => value.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');

export function SettingsPage() {
  const [view, setView] = useState<SettingsView | null>(null);
  const [loadError, setLoadError] = useState('');
  const [cost, setCost] = useState('');
  const [budget, setBudget] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);

  function fill(v: SettingsView) {
    setView(v);
    setCost(String(v.values.lookupCost));
    setBudget(String(v.values.lookupBudget));
    setEmail(v.values.buyingTeamEmail);
  }

  useEffect(() => {
    adminApi.settings().then(fill).catch((err) => setLoadError(errorMessage(err, 'Settings didn’t load. Try again.')));
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSaved('');
    if (cost === '' || budget === '') {
      setError('Enter the cost per lookup and the monthly budget (0 for no cap).');
      return;
    }
    setBusy(true);
    try {
      fill(await adminApi.saveSettings({ lookupCost: Number(cost), lookupBudget: Number(budget), buyingTeamEmail: email.trim() }));
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
          <form className="card" onSubmit={submit} noValidate aria-labelledby="lookups-title">
            <h2 id="lookups-title" className="card__title">Plate lookups</h2>
            <p className="admin-muted">Used for the spend figure on the dashboard. Get the price per lookup from your vehicle data provider’s contract.</p>
            {saved && <p className="notice notice--ok" role="status">{saved}</p>}
            {error && <p className="notice notice--error" role="alert">{error}</p>}
            <div className="field-row">
              <div className="field">
                <label htmlFor="lookup-cost">Cost per lookup (£)</label>
                <input id="lookup-cost" className="input" inputMode="decimal" value={cost} onChange={(e) => setCost(moneyInput(e.target.value))} />
              </div>
              <div className="field">
                <label htmlFor="lookup-budget">Monthly cap (£)</label>
                <input id="lookup-budget" className="input" inputMode="decimal" value={budget} onChange={(e) => setBudget(moneyInput(e.target.value))} aria-describedby="budget-hint" />
                <p id="budget-hint" className="field__hint">0 means no cap is set. The dashboard turns red when spend passes it.</p>
              </div>
            </div>
            <h2 className="card__title">Buying team</h2>
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
              <div><dt>Storage</dt><dd>Orders, customers, baskets, part requests and these settings are held in memory for now, so a redeploy resets them.</dd></div>
            </dl>
          </section>
        </div>
      )}
    </AdminLayout>
  );
}
