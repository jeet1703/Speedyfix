import { Link } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { money } from '../api/client';
import { useStore } from '../state/StoreContext';
import { Icon } from './Icons';

export function BasketDrawer() {
  const { basket, basketOpen, setBasketOpen, setQuantity, removeFromBasket } = useStore();
  const closeBtn = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!basketOpen) return;
    closeBtn.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setBasketOpen(false);
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [basketOpen, setBasketOpen]);

  if (!basketOpen) return null;
  const lines = basket?.lines ?? [];

  return (
    <div className="drawer-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setBasketOpen(false)}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="basket-title">
        <div className="drawer__head">
          <h2 id="basket-title">Your basket</h2>
          <button ref={closeBtn} type="button" className="icon-btn" aria-label="Close basket" onClick={() => setBasketOpen(false)}>
            <Icon name="x" />
          </button>
        </div>
        {lines.length === 0 ? (
          <div className="drawer__empty">
            <p>Your basket is empty.</p>
            <p>Enter your reg on the homepage to see parts that fit.</p>
          </div>
        ) : (
          <>
            <ul className="drawer__lines">
              {lines.map((l) => (
                <li key={l.productId} className="line">
                  <img src={l.imageUrl.replace('w=800&h=800', 'w=160&h=160')} alt="" width={72} height={72} />
                  <div className="line__info">
                    <p className="line__name">{l.name}</p>
                    {l.reference && <p className="line__ref">{l.reference}</p>}
                    <div className="line__controls">
                      <div className="qty" role="group" aria-label={`Quantity for ${l.name}`}>
                        <button type="button" aria-label="Decrease quantity" onClick={() => setQuantity(l.productId, l.quantity - 1)}><Icon name="minus" size={16} /></button>
                        <span aria-live="polite">{l.quantity}</span>
                        <button type="button" aria-label="Increase quantity" disabled={l.quantity >= l.stock} onClick={() => setQuantity(l.productId, l.quantity + 1)}><Icon name="plus" size={16} /></button>
                      </div>
                      <button type="button" className="link-btn" onClick={() => removeFromBasket(l.productId)}>Remove</button>
                    </div>
                  </div>
                  <p className="line__total">{money(l.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <dl className="drawer__totals">
              <div><dt>Subtotal</dt><dd>{money(basket!.subtotal)}</dd></div>
              <div><dt>{basket!.delivery.label}</dt><dd>{basket!.delivery.price ? money(basket!.delivery.price) : 'Free'}</dd></div>
              <div className="drawer__total"><dt>Total</dt><dd>{money(basket!.total)}</dd></div>
            </dl>
            <p className="drawer__vat">Includes VAT of {money(basket!.vatIncluded)}</p>
            <div className="drawer__actions">
              <Link className="btn btn--primary btn--block" to="/checkout" onClick={() => setBasketOpen(false)}>Go to checkout</Link>
              <Link className="btn btn--outline btn--block" to="/basket" onClick={() => setBasketOpen(false)}>View basket</Link>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}
