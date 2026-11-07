import { ApiError, BASE, request } from '../api/client';
import type { Vehicle } from '../api/types';
import type {
  AdminSession, StaffSession, CategoryOption, CustomerRow, Dashboard, Fitment, Order, OrderStatus, PartRequest, PartRequestStatus, PlateCheck,
  EngineChoice, ProductDetail, ProductFormData, ProductRow, SettingsValues, SettingsView, InvoiceStatus, EbaySyncStatus
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
  brands: () => authed<string[]>('/api/admin/brands'),
  vehicles: () => request<Vehicle[]>('/api/vehicles'),

  /** Creates the part when id is null. Sends the form as JSON plus any new photos in one multipart request. */
  saveProduct: (id: string | null, form: ProductFormData, photos: File[]) => {
    const body = new FormData();
    body.append('part', new Blob([JSON.stringify(form)], { type: 'application/json' }));
    photos.forEach((p) => body.append('photos', p));
    return authed<ProductDetail>(id ? `/api/admin/products/${encodeURIComponent(id)}` : '/api/admin/products', { method: 'POST', body });
  },

  catalogueMakes: () => authed<string[]>('/api/admin/vehicle-catalogue/makes'),
  catalogueModels: (make: string) => authed<string[]>(`/api/admin/vehicle-catalogue/models?make=${encodeURIComponent(make)}`),
  catalogueYears: (make: string, model: string) =>
    authed<string[]>(`/api/admin/vehicle-catalogue/years?make=${encodeURIComponent(make)}&model=${encodeURIComponent(model)}`),
  catalogueEngines: (make: string, model: string, yearFrom: number, yearTo: number) =>
    authed<EngineChoice[]>(`/api/admin/vehicle-catalogue/engines?make=${encodeURIComponent(make)}&model=${encodeURIComponent(model)}&yearFrom=${yearFrom}&yearTo=${yearTo}`),
  addVehicleRange: (range: { make: string; model: string; yearFrom: number; yearTo: number; engines: string[] }) =>
    authed<Fitment[]>('/api/admin/vehicle-catalogue', json('POST', range)),
  /** Vehicles from a compatibility table copied off an eBay listing page. */
  suggestVehicles: (part: { title: string; partNumber: string; engineCode: string; brand: string; category: string; notes: string }) =>
    authed<{ vehicles: Fitment[]; notMatched: string[] }>('/api/admin/vehicle-catalogue/suggest', json('POST', part)),
  pastedVehicles: (text: string) => authed<Fitment[]>('/api/admin/vehicle-catalogue/pasted', json('POST', { text })),
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
  ebaySync: () => authed<EbaySyncStatus>('/api/admin/ebay-sync'),
  startEbaySync: () => authed<EbaySyncStatus>('/api/admin/ebay-sync', { method: 'POST' }),
  orderInvoice: (reference: string) => authed<InvoiceStatus>(`/api/admin/orders/${encodeURIComponent(reference)}/invoice`),
  emailInvoice: (reference: string) => authed<InvoiceStatus>(`/api/admin/orders/${encodeURIComponent(reference)}/invoice/email`, { method: 'POST' }),

  customers: () => authed<CustomerRow[]>('/api/admin/customers'),

  settings: () => authed<SettingsView>('/api/admin/settings'),
  saveSettings: (values: SettingsValues) => authed<SettingsView>('/api/admin/settings', json('PUT', values))
};

export const errorMessage = (err: unknown, fallback = 'Something went wrong. Try again.') =>
  err instanceof ApiError ? err.message : fallback;
