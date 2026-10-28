import { Icon } from './Icons';

export function Pagination({ page, pageCount, onPage }: { page: number; pageCount: number; onPage: (page: number) => void }) {
  if (pageCount <= 1) return null;
  const pages = visiblePages(page, pageCount);
  return (
    <nav className="pagination" aria-label="Pages">
      <button type="button" className="pagination__btn" aria-label="Previous page" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        <Icon name="chevronLeft" size={18} />
      </button>
      {pages.map((p, i) => p === null ? (
        <span key={`gap-${i}`} className="pagination__gap" aria-hidden="true">…</span>
      ) : (
        <button key={p} type="button" className={'pagination__btn' + (p === page ? ' is-current' : '')}
          aria-current={p === page ? 'page' : undefined} aria-label={`Page ${p}`} onClick={() => onPage(p)}>
          {p}
        </button>
      ))}
      <button type="button" className="pagination__btn" aria-label="Next page" disabled={page >= pageCount} onClick={() => onPage(page + 1)}>
        <Icon name="chevronRight" size={18} />
      </button>
    </nav>
  );
}

/** First, last and two either side of the current page, with null for each gap: 1 … 4 5 [6] 7 8 … 141. */
function visiblePages(page: number, pageCount: number): (number | null)[] {
  const shown = new Set([1, pageCount]);
  for (let p = page - 2; p <= page + 2; p++) if (p >= 1 && p <= pageCount) shown.add(p);
  const sorted = [...shown].sort((a, b) => a - b);
  const out: (number | null)[] = [];
  sorted.forEach((p, i) => {
    const prev = sorted[i - 1];
    // A gap of one page is shown as that page rather than "…".
    if (prev !== undefined && p - prev === 2) out.push(prev + 1);
    else if (prev !== undefined && p - prev > 2) out.push(null);
    out.push(p);
  });
  return out;
}
