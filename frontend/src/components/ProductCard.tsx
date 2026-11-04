import { Link } from 'react-router-dom';
import { useState } from 'react';
import type { FitStatus, Product, Vehicle } from '../api/types';
import { useStore } from '../state/StoreContext';
import { Price } from './Price';
import { Icon } from './Icons';

function fitLine(fit: FitStatus, vehicle: Vehicle | null): { icon: string; text: string; tone: string } {
  switch (fit) {
    case 'FITS': return { icon: 'check', text: `Fits your ${vehicle?.model ?? 'vehicle'}`, tone: 'ok' };
    case 'LIKELY_FITS': return { icon: 'check', text: `Likely fits your ${vehicle?.model ?? 'vehicle'}`, tone: 'ok' };
    case 'DOES_NOT_FIT': return { icon: 'x', text: `Doesn’t fit your ${vehicle?.model ?? 'vehicle'}`, tone: 'no' };
    case 'UNCONFIRMED': return { icon: 'ask', text: 'Fit not confirmed yet, ask us', tone: 'ask' };
    default: return { icon: 'car', text: 'Add your reg to check fit', tone: 'none' };
  }
}

export function ProductCard({ product }: { product: Product }) {
  const { vehicle, addToBasket, basket, setBasketOpen, wishlist, toggleWishlist } = useStore();
  const [adding, setAdding] = useState(false);
  // A used part is often the only one: once all of it is in the basket, the button opens the basket instead.
  const inBasket = basket?.lines.find((l) => l.productId === product.id)?.quantity ?? 0;
  const allInBasket = product.inStock && inBasket >= product.stock;
  const saved = wishlist.includes(product.id);
  const fit = fitLine(product.fit, vehicle);

  async function add() {
    setAdding(true);
    await addToBasket(product.id, product.name);
    setAdding(false);
  }

  return (
    <article className="card">
      <div className="card__media">
        <Link to={`/products/${product.id}`} tabIndex={-1} aria-hidden="true">
          <img src={product.imageUrl} alt="" loading="lazy" width={400} height={400} />
        </Link>
        <div className="card__chips">
          <span className="chip">{product.condition}{product.grade ? `, grade ${product.grade}` : ''}</span>
          {product.salePercent ? <span className="chip chip--sale">Sale -{product.salePercent}%</span> : null}
        </div>
        <button type="button" className={'wish' + (saved ? ' is-saved' : '')} aria-pressed={saved}
          aria-label={saved ? `Remove ${product.name} from wishlist` : `Save ${product.name} to wishlist`}
          onClick={() => toggleWishlist(product.id)}>
          <Icon name="heart" size={18} />
        </button>
      </div>
      <div className="card__body">
        <p className="card__category">{product.categoryName}</p>
        <h3 className="card__title"><Link to={`/products/${product.id}`}>{product.name}</Link></h3>
        {product.reference && <p className="card__ref">{product.reference}</p>}
        <p className={`card__fit card__fit--${fit.tone}`}><Icon name={fit.icon} size={16} />{fit.text}</p>
        <p className="card__price"><Price product={product} /> <span className="card__vat">inc. VAT</span></p>
        {allInBasket ? (
          <button type="button" className="btn btn--outline btn--block" onClick={() => setBasketOpen(true)}>
            <Icon name="check" size={16} />In your basket
          </button>
        ) : (
          <button type="button" className="btn btn--primary btn--block" onClick={add} disabled={adding || !product.inStock}>
            {!product.inStock ? 'Out of stock' : adding ? 'Adding…' : inBasket > 0 ? 'Add another' : 'Add to basket'}
          </button>
        )}
      </div>
    </article>
  );
}
