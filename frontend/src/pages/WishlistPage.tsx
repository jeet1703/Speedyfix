import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Product } from '../api/types';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { ProductCard } from '../components/ProductCard';
import { useStore } from '../state/StoreContext';

/** Parts saved with the heart button. The list lives in this browser until accounts are built. */
export function WishlistPage() {
  const { wishlist, vehicle } = useStore();
  const [items, setItems] = useState<Product[] | null>(null);
  const [error, setError] = useState('');
  const ids = wishlist.join(',');

  useEffect(() => {
    if (!ids) {
      setItems([]);
      return;
    }
    api.shop(new URLSearchParams({ ids, pageSize: '48' }), vehicle?.id)
      .then((r) => { setItems(r.items); setError(''); })
      .catch((e: Error) => setError(e.message));
  }, [ids, vehicle?.id]);

  return (
    <Layout title="Wishlist">
      <PageTitle title="Wishlist" crumbs={[{ label: 'Wishlist' }]} />
      <div className="container page-body">
        {error && <p className="notice notice--error" role="alert">{error}</p>}
        {items && items.length === 0 && !error && (
          <div className="empty empty--page">
            <h2>Your wishlist is empty</h2>
            <p>Tap the heart on any part to save it here.</p>
            <div className="empty__actions"><Link className="btn btn--primary" to="/products">Browse parts</Link></div>
          </div>
        )}
        {items && items.length > 0 && (
          <>
            <p className="page-body__note">Saved in this browser. Sign-in to keep your wishlist across devices is coming with accounts.</p>
            <ul className="product-grid product-grid--4">
              {items.map((p) => <li key={p.id}><ProductCard product={p} /></li>)}
            </ul>
          </>
        )}
      </div>
    </Layout>
  );
}
