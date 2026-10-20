import { Link } from 'react-router-dom';

export interface Crumb {
  label: string;
  to?: string;
}

/**
 * The grey band at the top of inner pages: page title and breadcrumb.
 * Set heading={false} when the page has its own h1 (the product page).
 */
export function PageTitle({ title, crumbs, heading = true }: { title: string; crumbs: Crumb[]; heading?: boolean }) {
  const Title = heading ? 'h1' : 'p';
  return (
    <div className="page-title">
      <div className="container">
        <Title className="h-display page-title__text">{title}</Title>
        <nav aria-label="Breadcrumb">
          <ol className="crumbs">
            <li><Link to="/">Home</Link></li>
            {crumbs.map((c, i) => (
              <li key={c.label + i}>
                {c.to && i < crumbs.length - 1 ? <Link to={c.to}>{c.label}</Link> : <span aria-current={i === crumbs.length - 1 ? 'page' : undefined}>{c.label}</span>}
              </li>
            ))}
          </ol>
        </nav>
      </div>
    </div>
  );
}
