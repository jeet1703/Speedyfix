import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

/** The orange band under the slider: enter a reg, see which parts fit. */
export function PlateFinder() {
  const { vehicle, setVehicle, notify } = useStore();
  const { data } = useSite();
  const [reg, setReg] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fitting, setFitting] = useState<number | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const result = await api.lookUpVehicle(reg);
      setVehicle(result.vehicle);
      setFitting(result.fittingParts);
      notify(`Vehicle saved: ${result.vehicle.make} ${result.vehicle.model}`);
      setReg('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Couldn’t look up that registration.');
    } finally {
      setBusy(false);
    }
  }

  function clear() {
    setVehicle(null);
    setFitting(null);
  }

  return (
    <section className="finder" aria-labelledby="finder-title">
      <div className="container finder__inner">
        <div className="finder__copy">
          <h2 id="finder-title">Find parts that fit your car</h2>
          {vehicle ? (
            <p>
              Checking fit for <strong>{vehicle.make} {vehicle.model}</strong>, {vehicle.engine}
              {fitting !== null && <>, {fitting} {fitting === 1 ? 'part fits' : 'parts fit'}</>}.{' '}
              <button type="button" className="link-btn" onClick={clear}>Remove vehicle</button>
            </p>
          ) : (
            <p>Enter your reg and we’ll show which parts match.</p>
          )}
        </div>
        <form className="finder__form" onSubmit={submit} noValidate>
          <label htmlFor="reg" className="sr-only">Your registration number</label>
          <div className="plate-input">
            <span className="plate-input__band" aria-hidden="true">UK</span>
            <input id="reg" value={reg} onChange={(e) => setReg(e.target.value)} placeholder="AB12 CDE"
              maxLength={9} autoComplete="off" spellCheck={false} aria-describedby={error ? 'reg-error' : data?.demoPlates ? 'reg-hint' : undefined}
              aria-invalid={!!error} />
          </div>
          <button className="btn btn--dark" type="submit" disabled={busy}>{busy ? 'Checking…' : 'Find parts'}</button>
        </form>
        {data?.demoPlates && <p id="reg-hint" className="finder__hint">Demo plates: AB12 CDE, VX16 ABC, JG17 XEE</p>}
        {error && <p id="reg-error" className="finder__error" role="alert">{error}</p>}
      </div>
    </section>
  );
}
