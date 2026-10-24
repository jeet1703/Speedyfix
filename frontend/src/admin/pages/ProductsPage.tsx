import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, gbpOrDash, inQueue, QUEUE_FILTERS, WorkPill, type QueueFilter } from '../components';
import type { ProductRow } from '../types';

/** /admin/products?filter=&q= : every part, drafts included. */
export function ProductsPage() {
  const [params, setParams] = useSearchParams();
  const [rows, setRows] = useState<ProductRow[] | null>(null);
  const [error, setError] = useState('');
  const filter = (QUEUE_FILTERS.some((f) => f.value === params.get('filter')) ? params.get('filter') : 'all') as QueueFilter;
  const q = params.get('q') ?? '';

  useEffect(() => {
    adminApi.products().then(setRows).catch((err) => setError(errorMessage(err, 'The products didn’t load. Try again.')));
  }, []);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value && value !== 'all') next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  }

  const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const matches = (r: ProductRow) => {
    const text = [r.name, r.oeNumber, r.engineCode, r.categoryName].filter(Boolean).join(' ').toLowerCase();
    return words.every((w) => text.includes(w) || text.replace(/[\s-]/g, '').includes(w.replace(/-/g, '')));
  };
  const searched = rows?.filter(matches) ?? [];
  const shown = searched.filter((r) => inQueue(r, filter));
  const options = QUEUE_FILTERS.map((f) => ({ ...f, count: searched.filter((r) => inQueue(r, f.value)).length }));

  return (
    <AdminLayout title="Products" actions={<Link className="btn btn--primary" to="/admin/products/new">Add part</Link>}>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <section className="card" aria-label="Products">
        <div className="card__head card__head--wrap">
          <div className="admin-search">
            <label className="sr-only" htmlFor="product-search">Search products</label>
            <input id="product-search" className="input" type="search" placeholder="Search by name, OE number or engine code"
              value={q} onChange={(e) => update('q', e.target.value)} />
          </div>
          <Chips label="Show" options={options} value={filter} onChange={(v) => update('filter', v)} />
        </div>
        {!rows && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
        {rows && shown.length === 0 && <p className="admin-muted">No parts match.</p>}
        {shown.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col"><span className="sr-only">Photo</span></th>
                  <th scope="col">Part</th>
                  <th scope="col">OE number</th>
                  <th scope="col" className="num">Price</th>
                  <th scope="col" className="num">Stock</th>
                  <th scope="col">Status</th>
                  <th scope="col" className="num">Vehicles</th>
                  <th scope="col"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.id}>
                    <td className="table__thumb">{r.imageUrl ? <img src={r.imageUrl} alt="" loading="lazy" /> : <span className="thumb-empty" />}</td>
                    <td>
                      {r.name}
                      <span className="table__sub">{r.categoryName}{!r.hasPhoto && r.imageUrl ? ' · sample photo' : ''}</span>
                    </td>
                    <td className="mono">{r.oeNumber ?? (r.engineCode ? `Engine ${r.engineCode}` : '—')}</td>
                    <td className="num">{gbpOrDash(r.price)}</td>
                    <td className={'num' + (r.stock === 0 ? ' text-bad' : '')}>{r.stock}</td>
                    <td><WorkPill status={r.workStatus} /></td>
                    <td className="num">{r.vehicles}</td>
                    <td className="table__action"><Link to={`/admin/products/${encodeURIComponent(r.id)}`}>Edit<span className="sr-only"> {r.name}</span></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </AdminLayout>
  );
}
