import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { CardsSkeleton } from '../components/Skeleton';
import { adminApi, adminToken, onSignedOut } from './adminApi';
import type { StaffSession as Staff } from './types';

interface AdminAuthState {
  /** undefined while the saved token is being checked; null when signed out. */
  staff: Staff | null | undefined;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthState | null>(null);

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [staff, setStaff] = useState<Staff | null | undefined>(() => (adminToken.get() ? undefined : null));

  useEffect(() => {
    onSignedOut(() => setStaff(null));
    if (adminToken.get()) {
      adminApi.session().then(setStaff).catch(() => setStaff(null));
    }
    return () => onSignedOut(null);
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const { token, ...session } = await adminApi.signIn(email, password);
    adminToken.set(token);
    setStaff(session);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await adminApi.signOut();
    } catch {
      /* signed out locally either way */
    }
    adminToken.set(null);
    setStaff(null);
  }, []);

  const value = useMemo(() => ({ staff, signIn, signOut }), [staff, signIn, signOut]);
  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}

export function useAdminAuth() {
  const ctx = useContext(AdminAuthContext);
  if (!ctx) throw new Error('useAdminAuth must be used inside AdminAuthProvider');
  return ctx;
}

/** Sends signed-out staff to the sign-in page, remembering where they were going. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { staff } = useAdminAuth();
  const location = useLocation();
  if (staff === undefined) {
    return <main className="admin-state" aria-busy="true" aria-label="Checking your session"><CardsSkeleton cards={2} /></main>;
  }
  if (staff === null) {
    return <Navigate to="/admin/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}
