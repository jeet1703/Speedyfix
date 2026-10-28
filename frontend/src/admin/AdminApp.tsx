import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';
import '../styles/admin.css';
import { AdminAuthProvider, RequireAdmin } from './AdminAuth';
import { CustomersPage } from './pages/CustomersPage';
import { ListingsPage } from './pages/ListingsPage';
import { LoginPage } from './pages/LoginPage';
import { PartRequestsPage } from './pages/PartRequestsPage';
import { ProductEditPage } from './pages/ProductEditPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { OrdersPage } from './pages/OrdersPage';
import { OverviewPage } from './pages/OverviewPage';
import { SettingsPage } from './pages/SettingsPage';
import { VehicleTesterPage } from './pages/VehicleTesterPage';

/** A fresh form for each part, so switching from one part to another doesn't carry state over. */
function EditPart() {
  const { id } = useParams();
  return <ProductEditPage key={id ?? 'new'} />;
}

/** The old /admin/products list now lives at /admin/listings; keep any saved links working. */
function ToListings() {
  const { search } = useLocation();
  return <Navigate to={`/admin/listings${search}`} replace />;
}

/** Everything under /admin. Loaded on demand, so shoppers never download it. */
export default function AdminApp() {
  // Keep staff pages out of search engines.
  useEffect(() => {
    const meta = document.createElement('meta');
    meta.name = 'robots';
    meta.content = 'noindex, nofollow';
    document.head.appendChild(meta);
    return () => meta.remove();
  }, []);

  return (
    <AdminAuthProvider>
      <Routes>
        <Route path="login" element={<LoginPage />} />
        <Route index element={<RequireAdmin><OverviewPage /></RequireAdmin>} />
        <Route path="orders" element={<RequireAdmin><OrdersPage /></RequireAdmin>} />
        <Route path="orders/:reference" element={<RequireAdmin><OrderDetailPage /></RequireAdmin>} />
        <Route path="listings" element={<RequireAdmin><ListingsPage /></RequireAdmin>} />
        <Route path="customers" element={<RequireAdmin><CustomersPage /></RequireAdmin>} />
        <Route path="products" element={<ToListings />} />
        <Route path="products/new" element={<RequireAdmin><EditPart /></RequireAdmin>} />
        <Route path="products/:id" element={<RequireAdmin><EditPart /></RequireAdmin>} />
        <Route path="part-requests" element={<RequireAdmin><PartRequestsPage /></RequireAdmin>} />
        <Route path="vehicle-lookup" element={<RequireAdmin><VehicleTesterPage /></RequireAdmin>} />
        <Route path="settings" element={<RequireAdmin><SettingsPage /></RequireAdmin>} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </AdminAuthProvider>
  );
}
