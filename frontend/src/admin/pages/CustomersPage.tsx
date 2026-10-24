import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { when } from '../components';
import type { CustomerRow } from '../types';

/** /admin/customers : every shopper who has signed in with Google, most recent first, with their recent searches. */
export function CustomersPage() {
  const [rows, setRows] = useState<CustomerRow[] | null>(null);
  const [error, setError] = useState('');
  const [q, setQ] = useState('');

  useEffect(() => {
    adminApi.customers().then(setRows).catch((err) => setError(errorMessage(err, 'The customers didn’t load. Try again.')));
  }, []);

  const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const shown = (rows ?? []).filter((c) => {
    const text = [c.name, c.email, ...c.recentSearches].join(' ').toLowerCase();
    return words.every((w) => text.includes(w));
  });

  return (
    <AdminLayout title="Customers">
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      <section className="card" aria-label="Customers">
        <div className="card__head card__head--wrap">
          <p className="admin-muted">
            {rows ? `${rows.length} ${rows.length === 1 ? 'shopper has' : 'shoppers have'} signed in with Google.` : ''} Recent searches show what each one has been looking for.
          </p>
          <div className="admin-search">
            <label className="sr-only" htmlFor="customer-search">Search customers</label>
            <input id="customer-search" className="input" type="search" placeholder="Search by name, email or search"
              value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
        </div>
        {!rows && !error && <p className="admin-muted" aria-busy="true">Loading…</p>}
        {rows && shown.length === 0 && <p className="admin-muted">{rows.length === 0 ? 'No one has signed in yet.' : 'No customers match.'}</p>}
        {shown.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col"><span className="sr-only">Photo</span></th>
                  <th scope="col">Customer</th>
                  <th scope="col">Joined</th>
                  <th scope="col">Last signed in</th>
                  <th scope="col" className="num">Sign-ins</th>
                  <th scope="col">Recent searches</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((c) => (
                  <tr key={c.id}>
                    <td className="table__thumb">
                      {c.pictureUrl
                        ? <img src={c.pictureUrl} alt="" referrerPolicy="no-referrer" className="avatar" />
                        : <span className="avatar avatar--letter" aria-hidden="true">{c.name.charAt(0).toUpperCase()}</span>}
                    </td>
                    <td>{c.name}<span className="table__sub"><a href={`mailto:${c.email}`}>{c.email}</a></span></td>
                    <td>{when(c.joinedAt)}</td>
                    <td>{when(c.lastSignInAt)}</td>
                    <td className="num">{c.signIns}</td>
                    <td>
                      {c.recentSearches.length === 0 ? <span className="admin-muted">None yet</span> : (
                        <ul className="search-chips">
                          {c.recentSearches.slice(0, 5).map((s) => (
                            <li key={s}><Link to={`/admin/listings?tab=all&q=${encodeURIComponent(s)}`}>{s}</Link></li>
                          ))}
                          {c.recentSearches.length > 5 && <li className="admin-muted">+{c.recentSearches.length - 5} more</li>}
                        </ul>
                      )}
                    </td>
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
