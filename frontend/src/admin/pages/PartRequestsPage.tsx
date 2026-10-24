import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, REQUEST_LABEL, RequestPill, when } from '../components';
import type { PartRequest, PartRequestStatus } from '../types';

type Filter = PartRequestStatus | 'ALL';

const REPLY_LABEL = { WHATSAPP: 'WhatsApp', PHONE: 'Phone call', EMAIL: 'Email' } as const;

/** /admin/part-requests?status=&ref= : the list, with the selected request beside it. */
export function PartRequestsPage() {
  const [params, setParams] = useSearchParams();
  const [requests, setRequests] = useState<PartRequest[] | null>(null);
  const [error, setError] = useState('');
  const filter = (params.get('status') ?? 'NEW') as Filter;
  const selectedRef = params.get('ref');

  useEffect(() => {
    adminApi.partRequests().then(setRequests).catch((err) => setError(errorMessage(err, 'Part requests didn’t load. Try again.')));
  }, []);

  function set(key: string, value: string | null) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  }

  const all = requests ?? [];
  const shown = all.filter((r) => filter === 'ALL' || r.status === filter);
  const selected = all.find((r) => r.reference === selectedRef) ?? null;
  const options: { value: Filter; label: string; count: number }[] = [
    ...(['NEW', 'QUOTED', 'CLOSED'] as const).map((s) => ({ value: s as Filter, label: REQUEST_LABEL[s], count: all.filter((r) => r.status === s).length })),
    { value: 'ALL', label: 'All', count: all.length }
  ];

  return (
    <AdminLayout title="Part requests">
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <div className="requests">
        <section className="card" aria-label="Requests">
          <div className="card__head card__head--wrap">
            <Chips label="Show" options={options} value={filter} onChange={(v) => set('status', v)} />
          </div>
          {!requests && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
          {requests && shown.length === 0 && <p className="admin-muted">No {filter === 'ALL' ? '' : REQUEST_LABEL[filter].toLowerCase() + ' '}requests.</p>}
          <ul className="request-list">
            {shown.map((r) => (
              <li key={r.reference}>
                <button type="button" className="request-item" aria-current={r.reference === selectedRef || undefined} onClick={() => set('ref', r.reference)}>
                  <span className="request-item__top">
                    <strong>{r.part}</strong>
                    <RequestPill status={r.status} />
                  </span>
                  <span className="admin-muted">{r.vehicle ?? 'Vehicle not identified'} · {r.reference} · {when(r.receivedAt)}</span>
                </button>
              </li>
            ))}
          </ul>
          <p className="card__foot admin-muted">Requests are held in memory on this prototype server, so a redeploy clears them.</p>
        </section>

        {selected ? (
          <RequestDetail key={selected.reference} request={selected}
            onChange={(updated) => setRequests((prev) => prev?.map((r) => (r.reference === updated.reference ? updated : r)) ?? null)} />
        ) : (
          <section className="card request-detail request-detail--empty"><p className="admin-muted">Choose a request to see the details.</p></section>
        )}
      </div>
    </AdminLayout>
  );
}

function RequestDetail({ request: r, onChange }: { request: PartRequest; onChange: (r: PartRequest) => void }) {
  const [photos, setPhotos] = useState<string[]>([]);
  const [photoError, setPhotoError] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    const urls: string[] = [];
    Promise.all(Array.from({ length: r.photoCount }, (_, i) => adminApi.requestPhoto(r.reference, i)))
      .then((loaded) => {
        if (cancelled) {
          loaded.forEach((u) => URL.revokeObjectURL(u));
          return;
        }
        urls.push(...loaded);
        setPhotos(loaded);
      })
      .catch(() => !cancelled && setPhotoError('The photos couldn’t be loaded.'));
    return () => {
      cancelled = true;
      urls.forEach((u) => URL.revokeObjectURL(u));
    };
  }, [r.reference, r.photoCount]);

  async function setStatus(status: PartRequestStatus) {
    setBusy(true);
    setError('');
    try {
      onChange(await adminApi.setRequestStatus(r.reference, status));
    } catch (err) {
      setError(errorMessage(err, 'We couldn’t update the request. Try again.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card request-detail" aria-labelledby="request-title">
      <div className="card__head">
        <h2 id="request-title" className="card__title">{r.part}</h2>
        <RequestPill status={r.status} />
      </div>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <dl className="facts">
        <div><dt>Reference</dt><dd className="mono">{r.reference}</dd></div>
        <div><dt>Received</dt><dd>{when(r.receivedAt)}</dd></div>
        <div><dt>Vehicle</dt><dd>{r.vehicle ?? 'Not identified from the plate'}</dd></div>
        <div><dt>Plate</dt><dd className="mono">{r.maskedRegistration}</dd></div>
      </dl>
      {r.notes && (
        <div>
          <h3 className="card__subtitle">Notes from the shopper</h3>
          <p className="request-notes">{r.notes}</p>
        </div>
      )}
      <div>
        <h3 className="card__subtitle">Reply by {REPLY_LABEL[r.replyBy]}</h3>
        <ul className="contact">
          <li>{r.name}</li>
          {r.phone && <li><a href={`tel:${r.phone.replace(/[^\d+]/g, '')}`}>{r.phone}</a></li>}
          {r.email && <li><a href={`mailto:${r.email}?subject=${encodeURIComponent(`Your part request ${r.reference}`)}`}>{r.email}</a></li>}
        </ul>
      </div>
      {r.photoCount > 0 && (
        <div>
          <h3 className="card__subtitle">Photos</h3>
          {photoError ? <p className="field-error">{photoError}</p> : (
            <div className="request-photos">
              {photos.map((url, i) => <a key={url} href={url} target="_blank" rel="noreferrer"><img src={url} alt={`Shopper’s photo ${i + 1}`} /></a>)}
            </div>
          )}
        </div>
      )}
      <div className="request-actions">
        {r.status !== 'QUOTED' && <button type="button" className="btn btn--primary btn--sm" disabled={busy} onClick={() => void setStatus('QUOTED')}>Mark as quoted</button>}
        {r.status !== 'CLOSED' && <button type="button" className="btn btn--outline btn--sm" disabled={busy} onClick={() => void setStatus('CLOSED')}>Close</button>}
        {r.status !== 'NEW' && <button type="button" className="btn btn--outline btn--sm" disabled={busy} onClick={() => void setStatus('NEW')}>Reopen</button>}
      </div>
    </section>
  );
}
