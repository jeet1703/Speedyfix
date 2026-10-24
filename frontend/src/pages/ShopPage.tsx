import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Facet, ShopResult, ShopSort } from '../api/types';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { Pagination } from '../components/Pagination';
import { PlateFinder } from '../components/PlateFinder';
import { ProductCard } from '../components/ProductCard';
import { useAccount } from '../state/AccountContext';
import { useStore } from '../state/StoreContext';

const SORTS: { value: ShopSort; label: string }[] = [
  { value: 'best-selling', label: 'Best sellers first' },
  { value: 'newest', label: 'Newest first' },
  { value: 'price-low', label: 'Price: low to high' },
  { value: 'price-high', label: 'Price: high to low' }
];

const CONDITIONS = [
  { value: 'USED', label: 'Used' },
  { value: 'REMANUFACTURED', label: 'Remanufactured' },
  { value: 'NEW', label: 'New' }
];

/** Digits and at most one decimal point, so "12.5.0" can't reach the API as an invalid price. */
const priceInput = (value: string) => value.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');

/** The eBay-style filters: each takes several values, repeated in the URL (?brand=Nissan&brand=Toyota). */
const FACETS = [
  { param: 'refinement', title: 'Featured refinements', key: 'refinements' },
  { param: 'brand', title: 'Brand', key: 'brands' },
  { param: 'subType', title: 'Sub type', key: 'subTypes' }
] as const;
const FACET_PREVIEW = 6;

/**
 * /products, filtered by the query string (category, q, condition, minPrice, maxPrice, fitsOnly, brand, subType,
 * refinement, sort, page).
 */
export function ShopPage() {
  const [params, setParams] = useSearchParams();
  const { vehicle } = useStore();
  const { recordSearch } = useAccount();
  const [result, setResult] = useState<ShopResult | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [min, setMin] = useState(params.get('minPrice') ?? '');
  const [max, setMax] = useState(params.get('maxPrice') ?? '');
  const query = params.toString();

  useEffect(() => {
    let live = true;
    setLoading(true);
    api.shop(new URLSearchParams(query), vehicle?.id)
      .then((r) => { if (live) { setResult(r); setError(''); } })
      .catch((e: Error) => live && setError(e.message))
      .finally(() => live && setLoading(false));
    return () => { live = false; };
  }, [query, vehicle?.id]);

  // Searches made from any search box land here, so this is where they join the shopper's history.
  const searched = params.get('q');
  useEffect(() => {
    if (searched) recordSearch(searched);
  }, [searched, recordSearch]);

  // Keep the price boxes in step when the URL changes (e.g. "Clear filters").
  useEffect(() => {
    setMin(params.get('minPrice') ?? '');
    setMax(params.get('maxPrice') ?? '');
  }, [params]);

  /** Changes some filters; any filter change goes back to page 1. */
  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  }

  function goToPage(page: number) {
    update({ page: page === 1 ? null : String(page) });
    document.getElementById('main')?.scrollIntoView({ behavior: 'smooth' });
  }

  const category = params.get('category');
  const q = params.get('q');
  const conditions = (params.get('condition') ?? '').split(',').filter(Boolean);
  const fitsOnly = params.get('fitsOnly') === 'true';
  const categoryName = result?.categories.find((c) => c.id === category)?.name;
  const title = q ? `Results for “${q}”` : categoryName ?? 'Products';
  const hasFilters = conditions.length > 0 || params.has('minPrice') || params.has('maxPrice') || fitsOnly
    || FACETS.some((f) => params.has(f.param));
  const clearAll = { condition: null, minPrice: null, maxPrice: null, fitsOnly: null, brand: null, subType: null, refinement: null };

  /** Ticks or unticks one value of a multi-value filter, keeping its other values. */
  function toggleFacet(param: string, value: string) {
    const next = new URLSearchParams(params);
    const values = next.getAll(param);
    next.delete(param);
    const kept = values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
    kept.forEach((v) => next.append(param, v));
    next.delete('page');
    setParams(next);
  }

  function toggleCondition(value: string) {
    const next = conditions.includes(value) ? conditions.filter((c) => c !== value) : [...conditions, value];
    update({ condition: next.join(',') || null });
  }

  function applyPrice(e: FormEvent) {
    e.preventDefault();
    update({ minPrice: min.trim() || null, maxPrice: max.trim() || null });
  }

  const from = result && result.total > 0 ? (result.page - 1) * result.pageSize + 1 : 0;
  const to = result ? Math.min(result.page * result.pageSize, result.total) : 0;

  return (
    <Layout title={title}>
      <PageTitle title={title} crumbs={[{ label: 'Products', to: '/products' }, ...(categoryName ? [{ label: categoryName }] : []), ...(q ? [{ label: 'Search' }] : [])]} />
      <PlateFinder />
      <div className="container shop">
        <button type="button" className="btn btn--outline shop__filters-btn" aria-expanded={filtersOpen}
          aria-controls="shop-filters" onClick={() => setFiltersOpen((o) => !o)}>
          {filtersOpen ? 'Hide filters' : 'Show filters'}
        </button>
        <aside id="shop-filters" className={'shop__sidebar' + (filtersOpen ? ' is-open' : '')} aria-label="Filters">
          <section className="widget">
            <h2 className="widget__title">Product categories</h2>
            <ul className="widget__cats">
              <li>
                <Link to="/products" aria-current={!category && !q ? 'page' : undefined}>All parts</Link>
              </li>
              {result?.categories.map((c) => (
                <li key={c.id}>
                  <Link to={`/products?category=${c.id}`} aria-current={c.id === category ? 'page' : undefined}>
                    {c.name} <span>{c.count}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          {result && FACETS.map((f) => (
            <FacetWidget key={f.param} title={f.title} facets={result[f.key]} selected={params.getAll(f.param)}
              onToggle={(v) => toggleFacet(f.param, v)} />
          ))}

          {vehicle && (
            <section className="widget">
              <h2 className="widget__title">Your vehicle</h2>
              <label className="check">
                <input type="checkbox" checked={fitsOnly} onChange={() => update({ fitsOnly: fitsOnly ? null : 'true' })} />
                Only parts that fit my {vehicle.model}
              </label>
            </section>
          )}

          <fieldset className="widget">
            <legend className="widget__title">Condition</legend>
            {CONDITIONS.map((c) => (
              <label key={c.value} className="check">
                <input type="checkbox" checked={conditions.includes(c.value)} onChange={() => toggleCondition(c.value)} />
                {c.label}
              </label>
            ))}
          </fieldset>

          <form className="widget" onSubmit={applyPrice}>
            <h2 className="widget__title">Filter by price</h2>
            <div className="price-range">
              <label className="sr-only" htmlFor="min-price">Minimum price in pounds</label>
              <input id="min-price" className="input" inputMode="decimal" placeholder="£ Min" value={min} onChange={(e) => setMin(priceInput(e.target.value))} />
              <label className="sr-only" htmlFor="max-price">Maximum price in pounds</label>
              <input id="max-price" className="input" inputMode="decimal" placeholder="£ Max" value={max} onChange={(e) => setMax(priceInput(e.target.value))} />
            </div>
            <button type="submit" className="btn btn--dark btn--sm">Filter</button>
          </form>

          {hasFilters && (
            <button type="button" className="link-btn" onClick={() => update(clearAll)}>
              Clear filters
            </button>
          )}
        </aside>

        <section className="shop__results" aria-labelledby="results-count" aria-busy={loading}>
          <div className="shop__toolbar">
            <p id="results-count" aria-live="polite">
              {result ? (result.total === 0 ? 'No results' : `Showing ${from}–${to} of ${result.total} ${result.total === 1 ? 'result' : 'results'}`) : 'Loading…'}
            </p>
            <label className="sort">
              <span className="sr-only">Sort by</span>
              <select className="input" value={params.get('sort') ?? 'best-selling'} onChange={(e) => update({ sort: e.target.value === 'best-selling' ? null : e.target.value })}>
                {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
            </label>
          </div>

          {error && <p className="notice notice--error" role="alert">{error}</p>}

          {result && result.total === 0 && !error && (
            <div className="empty">
              <h2>No parts match{q ? ` “${q}”` : ' these filters'}</h2>
              <p>Try fewer filters, search by part number or engine code, or ask us to find it for you.</p>
              <div className="empty__actions">
                {hasFilters && <button type="button" className="btn btn--outline" onClick={() => update(clearAll)}>Clear filters</button>}
                <Link className="btn btn--primary" to="/request-a-part">Request a Part</Link>
              </div>
            </div>
          )}

          {result && result.items.length > 0 && (
            <ul className="product-grid">
              {result.items.map((p) => <li key={p.id}><ProductCard product={p} /></li>)}
            </ul>
          )}

          {result && <Pagination page={result.page} pageCount={result.pageCount} onPage={goToPage} />}
        </section>
      </div>
    </Layout>
  );
}

/** One eBay-style filter: a checkbox per value with its count, the first few shown until "Show more". */
function FacetWidget({ title, facets, selected, onToggle }: {
  title: string;
  facets: Facet[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  const [all, setAll] = useState(false);
  // A ticked value stays listed even when the other filters leave it with no parts.
  const listed = [...facets, ...selected.filter((v) => !facets.some((f) => f.value.toLowerCase() === v.toLowerCase())).map((value) => ({ value, count: 0 }))];
  if (listed.length === 0) return null;
  const shown = all ? listed : listed.slice(0, FACET_PREVIEW);
  return (
    <fieldset className="widget">
      <legend className="widget__title">{title}</legend>
      {shown.map((f) => (
        <label key={f.value} className="check">
          <input type="checkbox" checked={selected.some((v) => v.toLowerCase() === f.value.toLowerCase())} onChange={() => onToggle(f.value)} />
          {f.value} <span className="check__count">({f.count})</span>
        </label>
      ))}
      {listed.length > FACET_PREVIEW && (
        <button type="button" className="link-btn" aria-expanded={all} onClick={() => setAll((a) => !a)}>
          {all ? 'Show less' : `Show more (${listed.length - FACET_PREVIEW})`}
        </button>
      )}
    </fieldset>
  );
}
