import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { Chips, gbpOrDash, WorkPill } from '../components';
import type { ProductRow } from '../types';
import { Pagination } from '../../components/Pagination';
import { TableSkeleton } from '../../components/Skeleton';

type Tab = 'active' | 'out-of-stock' | 'drafts' | 'ended' | 'needs-attention' | 'needs-fitment' | 'missing-photo' | 'all';

/** Seller Hub-style listing tabs. needs-fitment and missing-photo are reached from Overview's tasks. */
const TABS: { value: Tab; label: string; test: (r: ProductRow) => boolean; hidden?: boolean }[] = [
  { value: 'active', label: 'Active', test: (r) => r.status === 'LIVE' && r.stock > 0 },
  { value: 'out-of-stock', label: 'Out of stock', test: (r) => r.status === 'LIVE' && r.stock === 0 },
  { value: 'drafts', label: 'Drafts', test: (r) => r.status === 'DRAFT' },
  { value: 'ended', label: 'Ended', test: (r) => r.status === 'ENDED' },
  { value: 'needs-attention', label: 'Needs attention', test: (r) => r.status !== 'ENDED' && (r.vehicles === 0 || !r.hasPhoto) },
  { value: 'needs-fitment', label: 'Needs fitment', test: (r) => r.vehicles === 0, hidden: true },
  { value: 'missing-photo', label: 'Needs photos', test: (r) => !r.hasPhoto, hidden: true },
  { value: 'all', label: 'All', test: () => true }
];

const PAGE_SIZE = 50;

const listed = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

/** /admin/listings?tab=&q= : every listing, drafts included. */
export function ListingsPage() {
  const [params, setParams] = useSearchParams();
  const [rows, setRows] = useState<ProductRow[] | null>(null);
  const [error, setError] = useState('');
  const tab = (TABS.find((t) => t.value === params.get('tab'))?.value ?? 'active') as Tab;
  const q = params.get('q') ?? '';

  useEffect(() => {
    adminApi.products().then(setRows).catch((err) => setError(errorMessage(err, 'The listings didn’t load. Try again.')));
  }, []);

  /** Changes the tab, search or page; a new tab or search starts again from page 1. */
  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: key !== 'page' });
  }

  function goToPage(p: number) {
    update('page', p === 1 ? '' : String(p));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const matches = (r: ProductRow) => {
    const text = [r.name, r.partNumber, r.engineCode, r.brand, r.subType, r.categoryName].filter(Boolean).join(' ').toLowerCase();
    return words.every((w) => text.includes(w) || text.replace(/[\s-]/g, '').includes(w.replace(/-/g, '')));
  };
  const searched = rows?.filter(matches) ?? [];
  const current = TABS.find((t) => t.value === tab)!;
  const inTab = searched.filter(current.test);
  const pageCount = Math.max(1, Math.ceil(inTab.length / PAGE_SIZE));
  const page = Math.min(Math.max(1, Number(params.get('page')) || 1), pageCount);
  const shown = inTab.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  // A hidden tab (opened from an Overview task) shows as a chip while it's the one in use.
  const options = TABS.filter((t) => !t.hidden || t.value === tab).map((t) => ({ value: t.value, label: t.label, count: searched.filter(t.test).length }));

  return (
    <AdminLayout title="Listings" actions={<Link className="btn btn--primary" to="/admin/products/new">Create listing</Link>}>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <section className="card" aria-label="Listings">
        <div className="card__head card__head--wrap">
          <Chips label="Listings" options={options} value={tab} onChange={(v) => update('tab', v === 'active' ? '' : v)} />
          <div className="admin-search">
            <label className="sr-only" htmlFor="listing-search">Search listings</label>
            <input id="listing-search" className="input" type="search" placeholder="Search by title, part number, brand or engine code"
              value={q} onChange={(e) => update('q', e.target.value)} />
          </div>
        </div>
        {!rows && !error && <TableSkeleton columns={7} />}
        {rows && shown.length === 0 && <p className="admin-muted">No listings here.</p>}
        {shown.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col"><span className="sr-only">Photo</span></th>
                  <th scope="col">Listing</th>
                  <th scope="col">Part number</th>
                  <th scope="col" className="num">Price</th>
                  <th scope="col" className="num">Available</th>
                  <th scope="col">Status</th>
                  <th scope="col">Listed</th>
                  <th scope="col"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.id}>
                    <td className="table__thumb">{r.imageUrl ? <img src={r.imageUrl} alt="" loading="lazy" /> : <span className="thumb-empty" />}</td>
                    <td>
                      {r.name}
                      <span className="table__sub">{[r.categoryName, r.brand, r.subType].filter(Boolean).join(' · ')}{r.hasPhoto ? '' : r.imageUrl?.endsWith('placeholder.svg') ? ' · no photo yet' : r.imageUrl ? ' · sample photo' : ''}</span>
                    </td>
                    <td className="mono">{r.partNumber ?? (r.engineCode ? `Engine ${r.engineCode}` : '—')}</td>
                    <td className="num">{gbpOrDash(r.price)}</td>
                    <td className={'num' + (r.stock === 0 ? ' text-bad' : '')}>{r.stock}</td>
                    <td><WorkPill status={r.workStatus} /></td>
                    <td>{listed.format(new Date(r.addedOn))}</td>
                    <td className="table__action"><Link to={`/admin/products/${encodeURIComponent(r.id)}`}>Edit<span className="sr-only"> {r.name}</span></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {inTab.length > PAGE_SIZE && (
          <div className="admin-pager">
            <p className="admin-muted">
              Showing {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, inTab.length)} of {inTab.length.toLocaleString('en-GB')}
            </p>
            <Pagination page={page} pageCount={pageCount} onPage={goToPage} />
          </div>
        )}
      </section>
    </AdminLayout>
  );
}
