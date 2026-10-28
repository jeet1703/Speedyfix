import type { CSSProperties } from 'react';

/**
 * Grey shimmering placeholders in the shape of the content that's on its way, shown while it loads.
 * They're hidden from screen readers; the surrounding region carries aria-busy and a "Loading" label instead.
 */
export function Skeleton({ width, height, radius, className, style }: {
  width?: number | string; height?: number | string; radius?: number | string; className?: string; style?: CSSProperties;
}) {
  return <span className={'skeleton' + (className ? ` ${className}` : '')} aria-hidden="true"
    style={{ width, height, borderRadius: radius, ...style }} />;
}

/** Several lines of text, the last one shorter. */
export function SkeletonText({ lines = 3, lastWidth = '60%' }: { lines?: number; lastWidth?: string }) {
  return (
    <span className="skeleton-text" aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => <Skeleton key={i} height={14} width={i === lines - 1 ? lastWidth : '100%'} />)}
    </span>
  );
}

/** The same shape as a ProductCard. */
export function ProductCardSkeleton() {
  return (
    <div className="card card--skeleton" aria-hidden="true">
      <Skeleton className="card__media" radius={0} />
      <div className="card__body">
        <Skeleton width="35%" height={12} />
        <Skeleton height={16} />
        <Skeleton width="75%" height={16} />
        <Skeleton width="55%" height={13} />
        <Skeleton width="40%" height={24} style={{ marginTop: 'auto' }} />
        <Skeleton height={44} radius={4} />
      </div>
    </div>
  );
}

/** A product grid's worth of card skeletons. */
export function ProductGridSkeleton({ count = 9, className = 'product-grid' }: { count?: number; className?: string }) {
  return (
    <ul className={className} aria-hidden="true">
      {Array.from({ length: count }, (_, i) => <li key={i}><ProductCardSkeleton /></li>)}
    </ul>
  );
}

/** Stand-in for the homepage: hero, plate finder, category banners and a row of products. */
export function HomeSkeleton() {
  return (
    <div className="home-skeleton" aria-busy="true" aria-label="Loading the store">
      <Skeleton className="home-skeleton__hero" radius={0} />
      <div className="container home-skeleton__body">
        <Skeleton height={88} radius={6} />
        <div className="home-skeleton__banners">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="home-skeleton__banner" radius={6} />)}
        </div>
        <Skeleton width={260} height={28} />
        <ProductGridSkeleton count={4} className="product-grid product-grid--4" />
      </div>
    </div>
  );
}

/** Stand-in for a product page: photo on the left, title, price and buttons on the right. */
export function ProductPageSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading the part">
      <div className="page-title"><div className="container"><Skeleton width={280} height={14} /></div></div>
      <div className="container product">
        <div className="product__gallery"><Skeleton className="product-skeleton__photo" radius={6} /></div>
        <div className="product__summary">
          <Skeleton width="30%" height={14} />
          <Skeleton height={34} />
          <Skeleton width="70%" height={34} />
          <Skeleton width="40%" height={30} />
          <Skeleton width="35%" height={16} />
          <SkeletonText lines={3} />
          <Skeleton height={120} radius={6} />
          <Skeleton height={52} radius={4} />
        </div>
      </div>
    </div>
  );
}

/** Rows of a table, for the admin's lists. */
export function TableSkeleton({ rows = 8, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="table-skeleton" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="table-skeleton__row" style={{ gridTemplateColumns: `48px repeat(${columns - 1}, minmax(0, 1fr))` }}>
          <Skeleton width={48} height={48} radius={4} />
          {Array.from({ length: columns - 1 }, (_, c) => <Skeleton key={c} height={14} width={c === 0 ? '90%' : '60%'} />)}
        </div>
      ))}
    </div>
  );
}

/** Cards of grey blocks, for the admin overview and detail pages. */
export function CardsSkeleton({ cards = 4 }: { cards?: number }) {
  return (
    <div className="cards-skeleton" aria-busy="true" aria-label="Loading">
      {Array.from({ length: cards }, (_, i) => (
        <div key={i} className="cards-skeleton__card">
          <Skeleton width="40%" height={20} />
          <Skeleton height={64} radius={6} />
          <SkeletonText lines={2} />
        </div>
      ))}
    </div>
  );
}
