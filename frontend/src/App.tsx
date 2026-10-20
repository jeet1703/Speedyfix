import { useEffect } from 'react';
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { BasketPage } from './pages/BasketPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { ComingSoon } from './pages/ComingSoon';
import { ContactPage } from './pages/ContactPage';
import { HomePage } from './pages/HomePage';
import { OrderReceivedPage } from './pages/OrderReceivedPage';
import { ProductPage } from './pages/ProductPage';
import { RequestPartPage } from './pages/RequestPartPage';
import { ShopPage } from './pages/ShopPage';
import { WishlistPage } from './pages/WishlistPage';
import { SiteProvider } from './state/SiteContext';
import { StoreProvider } from './state/StoreContext';

/** New pages start at the top; filter changes on the same page keep the scroll position. */
function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

export default function App() {
  return (
    <SiteProvider>
      <StoreProvider>
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <ScrollToTop />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/products" element={<ShopPage />} />
            <Route path="/products/:id" element={<ProductPage />} />
            <Route path="/basket" element={<BasketPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/order-received/:reference" element={<OrderReceivedPage />} />
            <Route path="/request-a-part" element={<RequestPartPage />} />
            <Route path="/wishlist" element={<WishlistPage />} />
            <Route path="/contact" element={<ContactPage />} />
            <Route path="*" element={<ComingSoon />} />
          </Routes>
        </BrowserRouter>
      </StoreProvider>
    </SiteProvider>
  );
}
