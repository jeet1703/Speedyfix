import { Link } from 'react-router-dom';
import { money } from '../api/client';
import { Icon } from '../components/Icons';
import { Layout } from '../components/Layout';
import { Totals } from '../components/OrderSummary';
import { PageTitle } from '../components/PageTitle';
import { useStore } from '../state/StoreContext';

export function BasketPage() {
  const { basket, setQuantity, removeFromBasket } = useStore();
  const lines = basket?.lines ?? [];

  return (
    <Layout title="Basket">
      <PageTitle title="Basket" crumbs={[{ label: 'Basket' }]} />
      <div className="container">
        {lines.length === 0 ? (
          <div className="empty empty--page">
            <h2>Your basket is empty</h2>
            <p>Add your reg to see parts that fit your car.</p>
            <div className="empty__actions">
              <Link className="btn btn--primary" to="/products">Browse parts</Link>
              <Link className="btn btn--outline" to="/request-a-part">Request a part</Link>
            </div>
          </div>
        ) : (
          <div className="two-col">
            <section aria-labelledby="basket-lines-title">
              <h2 id="basket-lines-title" className="sr-only">Parts in your basket</h2>
              <table className="basket-table">
                <thead>
                  <tr>
                    <th scope="col">Part</th>
                    <th scope="col">Price</th>
                    <th scope="col">Quantity</th>
                    <th scope="col" className="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {lines.map((l) => (
                    <tr key={l.productId}>
                      <td>
                        <div className="basket-table__part">
                          <img src={l.imageUrl.replace('w=800&h=800', 'w=160&h=160')} alt="" width={80} height={80} />
                          <div>
                            <Link to={`/products/${l.productId}`} className="basket-table__name">{l.name}</Link>
                            {l.reference && <p className="line__ref">{l.reference}</p>}
                            <button type="button" className="link-btn basket-table__remove" onClick={() => removeFromBasket(l.productId)}>
                              Remove<span className="sr-only"> {l.name}</span>
                            </button>
                          </div>
                        </div>
                      </td>
                      <td data-label="Price">{money(l.unitPrice)}</td>
                      <td data-label="Quantity">
                        <div className="qty" role="group" aria-label={`Quantity for ${l.name}`}>
                          <button type="button" aria-label="Decrease quantity" onClick={() => setQuantity(l.productId, l.quantity - 1)}><Icon name="minus" size={16} /></button>
                          <span aria-live="polite">{l.quantity}</span>
                          <button type="button" aria-label="Increase quantity" disabled={l.quantity >= l.stock} onClick={() => setQuantity(l.productId, l.quantity + 1)}><Icon name="plus" size={16} /></button>
                        </div>
                      </td>
                      <td data-label="Total" className="num"><strong>{money(l.lineTotal)}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Link className="btn btn--outline" to="/products">Continue shopping</Link>
            </section>

            <aside className="panel" aria-labelledby="basket-totals-title">
              <h2 id="basket-totals-title" className="panel__title">Basket totals</h2>
              <Totals totals={basket!} />
              <Link className="btn btn--primary btn--block" to="/checkout">Go to checkout</Link>
              <p className="panel__note">
                {basket!.delivery.method === 'PALLET'
                  ? 'Engines and gearboxes travel by pallet, so this order has pallet delivery.'
                  : 'Tracked parcel delivery is free on orders of £100 or more.'}
              </p>
            </aside>
          </div>
        )}
      </div>
    </Layout>
  );
}
