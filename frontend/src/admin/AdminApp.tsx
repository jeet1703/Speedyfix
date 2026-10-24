import { useEffect } from 'react';
import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import '../styles/admin.css';
import { AdminAuthProvider, RequireAdmin } from './AdminAuth';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { PartRequestsPage } from './pages/PartRequestsPage';
import { ProductEditPage } from './pages/ProductEditPage';
import { ProductsPage } from './pages/ProductsPage';
import { SettingsPage } from './pages/SettingsPage';
import { VehicleTesterPage } from './pages/VehicleTesterPage';

/** A fresh form for each part, so switching from one part to another doesn't carry state over. */
function EditPart() {
  const { id } = useParams();
  return <ProductEditPage key={id ?? 'new'} />;
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
        <Route index element={<RequireAdmin><DashboardPage /></RequireAdmin>} />
        <Route path="products" element={<RequireAdmin><ProductsPage /></RequireAdmin>} />
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
