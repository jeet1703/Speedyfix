import { ApiError, BASE, request } from '../api/client';
import type { Vehicle } from '../api/types';
import type {
  AdminSession, StaffSession, CategoryOption, CustomerRow, Dashboard, Fitment, Order, OrderStatus, PartRequest, PartRequestStatus, PlateCheck,
  ProductDetail, ProductFormData, ProductRow, SettingsValues, SettingsView
} from './types';

const KEY = 'sf_admin_token';

/** The staff JWT lives in sessionStorage, so it goes when the tab closes. Never throws. */
export const adminToken = {
  get(): string | null {
    try {
      return sessionStorage.getItem(KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) sessionStorage.setItem(KEY, token);
      else sessionStorage.removeItem(KEY);
    } catch {
      /* storage unavailable: the session lasts until the page reloads */
    }
  }
};

let signedOutHandler: (() => void) | null = null;

/** AdminAuthProvider registers this so any 401 sends staff back to the sign-in page. */
export function onSignedOut(handler: (() => void) | null) {
  signedOutHandler = handler;
}

const authHeader = (): Record<string, string> => {
  const token = adminToken.get();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

async function authed<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await request<T>(path, { ...init, headers: { ...authHeader(), ...(init?.headers ?? {}) } });
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      adminToken.set(null);
      signedOutHandler?.();
    }
    throw err;
  }
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const adminApi = {
  signIn: (email: string, password: string) => request<AdminSession>('/api/admin/session', json('POST', { email, password })),
  session: () => authed<StaffSession>('/api/admin/session'),
  signOut: () => authed<void>('/api/admin/session', { method: 'DELETE' }),

  dashboard: () => authed<Dashboard>('/api/admin/dashboard'),
  products: () => authed<ProductRow[]>('/api/admin/products'),
  product: (id: string) => authed<ProductDetail>(`/api/admin/products/${encodeURIComponent(id)}`),
  categories: () => authed<CategoryOption[]>('/api/admin/categories'),
  vehicles: () => request<Vehicle[]>('/api/vehicles'),

  /** Creates the part when id is null. Sends the form as JSON plus any new photos in one multipart request. */
  saveProduct: (id: string | null, form: ProductFormData, photos: File[]) => {
    const body = new FormData();
    body.append('part', new Blob([JSON.stringify(form)], { type: 'application/json' }));
    photos.forEach((p) => body.append('photos', p));
    return authed<ProductDetail>(id ? `/api/admin/products/${encodeURIComponent(id)}` : '/api/admin/products', { method: 'POST', body });
  },

  fitment: (partNumber: string, engineCode: string, productId: string | null) => {
    const q = new URLSearchParams();
    if (partNumber) q.set('partNumber', partNumber);
    if (engineCode) q.set('engineCode', engineCode);
    if (productId) q.set('productId', productId);
    return authed<Fitment[]>(`/api/admin/fitment?${q}`);
  },
  plateCheck: (registration: string) => authed<PlateCheck>('/api/admin/plate-check', json('POST', { registration })),

  partRequests: () => authed<PartRequest[]>('/api/admin/part-requests'),
  setRequestStatus: (reference: string, status: PartRequestStatus) =>
    authed<PartRequest>(`/api/admin/part-requests/${encodeURIComponent(reference)}`, json('PATCH', { status })),
  /** Request photos need the staff token, so they're fetched here and shown from an object URL (revoke it when done). */
  requestPhoto: async (reference: string, index: number): Promise<string> => {
    const res = await fetch(`${BASE}/api/admin/part-requests/${encodeURIComponent(reference)}/photos/${index}`, { headers: authHeader() });
    if (!res.ok) throw new ApiError(res.status, 'That photo isn’t stored any more.');
    return URL.createObjectURL(await res.blob());
  },

  orders: () => authed<Order[]>('/api/admin/orders'),
  order: (reference: string) => authed<Order>(`/api/admin/orders/${encodeURIComponent(reference)}`),
  setOrderStatus: (reference: string, status: OrderStatus, courier?: string, trackingNumber?: string) =>
    authed<Order>(`/api/admin/orders/${encodeURIComponent(reference)}`, json('PATCH', { status, courier, trackingNumber })),

  customers: () => authed<CustomerRow[]>('/api/admin/customers'),

  settings: () => authed<SettingsView>('/api/admin/settings'),
  saveSettings: (values: SettingsValues) => authed<SettingsView>('/api/admin/settings', json('PUT', values))
};

export const errorMessage = (err: unknown, fallback = 'Something went wrong. Try again.') =>
  err instanceof ApiError ? err.message : fallback;
