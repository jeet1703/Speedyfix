import { Icon } from './Icons';

export function Pagination({ page, pageCount, onPage }: { page: number; pageCount: number; onPage: (page: number) => void }) {
  if (pageCount <= 1) return null;
  const pages = Array.from({ length: pageCount }, (_, i) => i + 1);
  return (
    <nav className="pagination" aria-label="Pages">
      <button type="button" className="pagination__btn" aria-label="Previous page" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        <Icon name="chevronLeft" size={18} />
      </button>
      {pages.map((p) => (
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
