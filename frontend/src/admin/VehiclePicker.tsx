import { useEffect, useState } from 'react';
import { adminApi, errorMessage } from './adminApi';
import type { Fitment } from './types';

/**
 * Adds compatibility from eBay UK's vehicle catalogue: make, then model, the years, and one or more engines (none
 * ticked means every engine). Each engine becomes a row the listing fits; the shop matches shoppers' regs against them.
 */
export function VehiclePicker({ onAdd }: { onAdd: (rows: Fitment[]) => void }) {
  const [makes, setMakes] = useState<string[] | null>(null);
  const [make, setMake] = useState('');
  const [models, setModels] = useState<string[]>([]);
  const [model, setModel] = useState('');
  const [years, setYears] = useState<string[]>([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [engines, setEngines] = useState<string[] | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const fail = (e: unknown, fallback: string) => setError(errorMessage(e, fallback));

  useEffect(() => {
    adminApi.catalogueMakes().then(setMakes).catch((e) => { setMakes([]); fail(e, 'eBay’s vehicle list didn’t load.'); });
  }, []);

  const knownMake = makes?.find((m) => m.toLowerCase() === make.trim().toLowerCase()) ?? null;

  useEffect(() => {
    setModels([]); setModel(''); setYears([]); setFrom(''); setTo(''); setEngines(null); setChosen([]);
    if (!knownMake) return;
    adminApi.catalogueModels(knownMake).then(setModels).catch((e) => fail(e, 'Models didn’t load.'));
  }, [knownMake]);

  useEffect(() => {
    setYears([]); setFrom(''); setTo(''); setEngines(null); setChosen([]);
    if (!knownMake || !model) return;
    adminApi.catalogueYears(knownMake, model).then((y) => {
      setYears(y);
      if (y.length) { setFrom(y[y.length - 1]); setTo(y[0]); }
    }).catch((e) => fail(e, 'Years didn’t load.'));
  }, [knownMake, model]);

  useEffect(() => {
    setEngines(null); setChosen([]);
    if (!knownMake || !model || !from || !to) return;
    let live = true;
    adminApi.catalogueEngines(knownMake, model, Number(from), Number(to))
      .then((e) => { if (live) setEngines(e); })
      .catch((e) => fail(e, 'Engines didn’t load.'));
    return () => { live = false; };
  }, [knownMake, model, from, to]);

  async function add() {
    if (!knownMake || !model || !from || !to) return;
    setBusy(true);
    setError('');
    try {
      onAdd(await adminApi.addVehicleRange({ make: knownMake, model, yearFrom: Number(from), yearTo: Number(to), engines: chosen }));
      setChosen([]);
    } catch (e) {
      fail(e, 'The vehicles weren’t added. Try again.');
    } finally {
      setBusy(false);
    }
  }

  const ordered = [...years].sort();
  return (
    <div className="vpick" role="group" aria-labelledby="vpick-title">
      <p id="vpick-title" className="vpick__title">Add vehicles from eBay’s list</p>
      <div className="vpick__row">
        <div className="field">
          <label htmlFor="vp-make">Make</label>
          <input id="vp-make" className="input input--sm" list="vp-makes" value={make} onChange={(e) => setMake(e.target.value)}
            placeholder={makes === null ? 'Loading…' : 'Start typing, e.g. Nissan'} autoComplete="off" />
          <datalist id="vp-makes">{(makes ?? []).map((m) => <option key={m} value={m} />)}</datalist>
        </div>
        <div className="field">
          <label htmlFor="vp-model">Model</label>
          <select id="vp-model" className="input input--sm" value={model} disabled={!models.length} onChange={(e) => setModel(e.target.value)}>
            <option value="">{knownMake ? (models.length ? 'Choose…' : 'Loading…') : 'Choose a make first'}</option>
            {models.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="vp-from">From</label>
          <select id="vp-from" className="input input--sm" value={from} disabled={!years.length} onChange={(e) => { setFrom(e.target.value); if (to && e.target.value > to) setTo(e.target.value); }}>
            {!years.length && <option value="">—</option>}
            {ordered.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="vp-to">To</label>
          <select id="vp-to" className="input input--sm" value={to} disabled={!years.length} onChange={(e) => { setTo(e.target.value); if (from && e.target.value < from) setFrom(e.target.value); }}>
            {!years.length && <option value="">—</option>}
            {ordered.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
      </div>

      {model && from && to && (
        <fieldset className="vpick__engines">
          <legend>Engines <span className="field__optional">(tick the ones it fits; none ticked means all engines)</span></legend>
          {engines === null ? <p className="admin-muted">Loading engines…</p> : engines.length === 0 ? <p className="admin-muted">eBay lists no engines for these years.</p> : (
            <ul>
              {engines.map((e) => (
                <li key={e}>
                  <label>
                    <input type="checkbox" checked={chosen.includes(e)} onChange={(ev) => setChosen(ev.target.checked ? [...chosen, e] : chosen.filter((x) => x !== e))} />
                    <span className="mono">{e}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </fieldset>
      )}

      <div className="inline-row inline-row--sm">
        <button type="button" className="btn btn--dark" disabled={busy || !knownMake || !model || !from || !to} onClick={() => void add()}>
          {busy ? 'Adding…' : chosen.length > 1 ? `Add ${chosen.length} engines` : 'Add'}
        </button>
        {knownMake && model && from && to && (
          <span className="admin-muted">{knownMake} {model}, {from === to ? from : `${from} to ${to}`}, {chosen.length ? `${chosen.length} engine${chosen.length === 1 ? '' : 's'}` : 'all engines'}</span>
        )}
      </div>
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}
