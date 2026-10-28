import { Link, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { api, price } from '../api/client';
import type { Product } from '../api/types';
import { useAccount } from '../state/AccountContext';
import { useStore } from '../state/StoreContext';

export function SearchPanel({ onClose }: { onClose: () => void }) {
  const { vehicle } = useStore();
  const { searches, clearSearches } = useAccount();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Product[] | null>(null);
  const [error, setError] = useState('');
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);

  useEffect(() => {
    if (q.trim().length < 2) {
      setResults(null);
      return;
    }
    const t = window.setTimeout(() => {
      api.search(q.trim(), vehicle?.id)
        .then((r) => { setResults(r); setError(''); })
        .catch((e: Error) => setError(e.message));
    }, 250);
    return () => window.clearTimeout(t);
  }, [q, vehicle?.id]);

  return (
    <div className="search" role="search" onKeyDown={(e) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'Enter' && q.trim().length >= 2) { navigate(`/products?q=${encodeURIComponent(q.trim())}`); onClose(); }
    }}>
      <div className="container">
        <label htmlFor="site-search" className="sr-only">Search parts</label>
        <div className="search__field">
          <input id="site-search" ref={input} value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Part number, engine code or part name" autoComplete="off" />
          <button type="button" className="search__close" onClick={onClose}>Close</button>
        </div>
        <div aria-live="polite">
          {q.trim().length < 2 && searches.length > 0 && (
            <div className="search__recent">
              <p className="search__note">Recent searches <button type="button" className="link-btn" onClick={clearSearches}>Clear</button></p>
              <ul className="recent-searches">
                {searches.map((s) => (
                  <li key={s}><Link to={`/products?q=${encodeURIComponent(s)}`} onClick={onClose}>{s}</Link></li>
                ))}
              </ul>
            </div>
          )}
          {error && <p className="search__note">{error}</p>}
          {results && results.length === 0 && (
            <p className="search__note">No parts match “{q.trim()}”. Try a part number or engine code, or <Link to="/request-a-part" onClick={onClose}>request the part</Link>.</p>
          )}
          {results && results.length > 0 && (
            <ul className="search__results">
              {results.map((p) => (
                <li key={p.id}>
                  <Link to={`/products/${p.id}`} onClick={onClose}>
                    <img src={p.imageUrl.replace('w=800&h=800', 'w=120&h=120')} alt="" width={56} height={56} />
                    <span className="search__name">{p.name}<small>{p.reference ?? p.categoryName}</small></span>
                    <span className="search__price">{price(p.price)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {results && results.length > 0 && (
            <p className="search__note"><Link to={`/products?q=${encodeURIComponent(q.trim())}`} onClick={onClose}>See all results for “{q.trim()}”</Link></p>
          )}
        </div>
      </div>
    </div>
  );
}
