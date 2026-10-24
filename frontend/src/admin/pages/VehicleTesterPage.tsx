import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { PlateField } from '../../components/PlateField';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { gbpOrDash, WorkPill } from '../components';
import type { PlateCheck, ProductRow } from '../types';

/** Checks a plate the way the shop does, without counting as a shopper search. */
export function VehicleTesterPage() {
  const [reg, setReg] = useState('');
  const [result, setResult] = useState<PlateCheck | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reg.trim()) {
      setError('Enter a registration number.');
      return;
    }
    setError('');
    setBusy(true);
    try {
      setResult(await adminApi.plateCheck(reg));
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, 'We couldn’t look up that plate. Try again.'));
    } finally {
      setBusy(false);
    }
  }

  const live = result?.confirmed.filter((r) => r.status === 'LIVE').length ?? 0;
  const v = result?.vehicle;

  return (
    <AdminLayout title="Vehicle lookup tester">
      <section className="card" aria-labelledby="tester-title">
        <h2 id="tester-title" className="card__title">Check a plate</h2>
        <p className="admin-muted">See which vehicle a plate identifies and which parts a shopper would be shown. Checks here aren’t counted as shopper searches.</p>
        <form className="inline-row tester-form" onSubmit={submit} noValidate>
          <label className="sr-only" htmlFor="tester-reg">Registration</label>
          <PlateField id="tester-reg" value={reg} onChange={setReg} invalid={!!error} describedBy={error ? 'tester-error' : undefined} />
          <button type="submit" className="btn btn--dark" disabled={busy}>{busy ? 'Checking…' : 'Check plate'}</button>
        </form>
        {error && <p id="tester-error" className="field-error" role="alert">{error}</p>}
        <p className="field__hint">The vehicle data is demo data for now: AB12 CDE, VX16 ABC and JG17 XEE resolve. Connect a licensed UK vehicle data provider before launch.</p>
      </section>

      {result && v && (
        <>
          <section className="card" aria-labelledby="vehicle-title">
            <h2 id="vehicle-title" className="card__title">{v.make} {v.model}</h2>
            <dl className="facts">
              <div><dt>Engine</dt><dd>{v.engine}</dd></div>
              <div><dt>Engine code</dt><dd className="mono">{v.engineCode ?? '—'}</dd></div>
              <div><dt>Year</dt><dd>{v.year ?? '—'}</dd></div>
              <div><dt>Shoppers see</dt><dd>{live} {live === 1 ? 'part' : 'parts'}</dd></div>
            </dl>
          </section>
          <PartsTable title="Parts confirmed for this vehicle" rows={result.confirmed}
            empty="No parts are confirmed for this vehicle. A shopper would see the request-a-part prompt." />
          {result.sameEngineCode.length > 0 && (
            <PartsTable title={`Same engine code (${v.engineCode}), not confirmed`} rows={result.sameEngineCode}
              note="These may fit. Check each one and confirm the vehicle on the part to show it to shoppers." />
          )}
        </>
      )}
    </AdminLayout>
  );
}

function PartsTable({ title, rows, empty, note }: { title: string; rows: ProductRow[]; empty?: string; note?: string }) {
  return (
    <section className="card" aria-label={title}>
      <h2 className="card__title">{title}</h2>
      {note && <p className="admin-muted">{note}</p>}
      {rows.length === 0 ? <p className="admin-muted">{empty}</p> : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th scope="col">Part</th><th scope="col">OE number</th><th scope="col" className="num">Price</th><th scope="col" className="num">Stock</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Action</span></th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td className="mono">{r.oeNumber ?? '—'}</td>
                  <td className="num">{gbpOrDash(r.price)}</td>
                  <td className="num">{r.stock}</td>
                  <td><WorkPill status={r.workStatus} /></td>
                  <td className="table__action"><Link to={`/admin/products/${encodeURIComponent(r.id)}`}>Edit<span className="sr-only"> {r.name}</span></Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
