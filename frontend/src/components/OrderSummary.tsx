import { money } from '../api/client';
import type { Basket, BasketLine } from '../api/types';

/** Subtotal, delivery, total and VAT, as the basket, checkout and order pages show them. */
export function Totals({ totals }: { totals: Pick<Basket, 'subtotal' | 'delivery' | 'total' | 'vatIncluded'> }) {
  return (
    <>
      <dl className="totals">
        <div><dt>Subtotal</dt><dd>{money(totals.subtotal)}</dd></div>
        {totals.delivery.label && (
          <div><dt>{totals.delivery.label}</dt><dd>{totals.delivery.price ? money(totals.delivery.price) : 'Free'}</dd></div>
        )}
        <div className="totals__total"><dt>Total</dt><dd>{money(totals.total)}</dd></div>
      </dl>
      <p className="totals__vat">Includes VAT of {money(totals.vatIncluded)}</p>
    </>
  );
}

/** A compact, read-only list of order lines. */
export function LineList({ lines }: { lines: BasketLine[] }) {
  return (
    <ul className="mini-lines">
      {lines.map((l) => (
        <li key={l.productId}>
          <img src={l.imageUrl.replace('w=800&h=800', 'w=120&h=120')} alt="" width={56} height={56} />
          <span className="mini-lines__name">
            {l.name}
            <small>Qty {l.quantity}{l.reference ? ` · ${l.reference}` : ''}</small>
          </span>
          <span className="mini-lines__total">{money(l.lineTotal)}</span>
        </li>
      ))}
    </ul>
  );
}
