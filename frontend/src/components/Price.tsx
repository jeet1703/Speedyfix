import { price } from '../api/client';
import type { Product } from '../api/types';

/** A part's price; during a sale, the sale price with the old price struck through and a "-20%" badge. */
export function Price({ product, className }: { product: Pick<Product, 'price' | 'originalPrice' | 'salePercent'>; className?: string }) {
  if (product.originalPrice == null || !product.salePercent) {
    return <span className={className}>{price(product.price)}</span>;
  }
  return (
    <span className={`sale-price ${className ?? ''}`.trim()}>
      <span className="sale-price__now">{price(product.price)}</span>{' '}
      <s className="sale-price__was"><span className="sr-only">Was </span>{price(product.originalPrice)}</s>{' '}
      <span className="sale-price__off">-{product.salePercent}%</span>
    </span>
  );
}
