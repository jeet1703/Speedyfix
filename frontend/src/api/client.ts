import type {
  Basket, HomeData, LookupResult, Order, OrderForm, PartRequestReceived, Product, ProductDetail, ShopResult, SiteData
} from './types';

const BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

/** Error carrying the shopper-facing message from the API's problem details. */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  // FormData sets its own multipart Content-Type, boundary included.
  const json = !(init?.body instanceof FormData);
  try {
    res = await fetch(BASE + path, {
      ...init,
      headers: { ...(json ? { 'Content-Type': 'application/json' } : {}), Accept: 'application/json', ...(init?.headers ?? {}) }
    });
  } catch {
    throw new ApiError(0, 'Can’t reach the store right now. Check your connection and try again.');
  }
  if (!res.ok) {
    let detail = 'Something went wrong. Try again.';
    try {
      const body = await res.json();
      if (body && typeof body.detail === 'string') detail = body.detail;
    } catch {
      /* keep the default message */
    }
    throw new ApiError(res.status, detail);
  }
  return res.json() as Promise<T>;
}

const vehicleParam = (vehicleId?: string | null) => (vehicleId ? `vehicleId=${encodeURIComponent(vehicleId)}` : '');

export const api = {
  site: () => request<SiteData>('/api/site'),
  home: (vehicleId?: string | null) => request<HomeData>(`/api/home?${vehicleParam(vehicleId)}`),

  /** params are passed through as the query string (category, q, condition, sort, page, ids…). */
  shop: (params: URLSearchParams, vehicleId?: string | null) => {
    const query = new URLSearchParams(params);
    if (vehicleId) query.set('vehicleId', vehicleId);
    return request<ShopResult>(`/api/shop?${query}`);
  },
  product: (id: string, vehicleId?: string | null) =>
    request<ProductDetail>(`/api/products/${encodeURIComponent(id)}?${vehicleParam(vehicleId)}`),

  search: (q: string, vehicleId?: string | null) =>
    request<Product[]>(`/api/search?q=${encodeURIComponent(q)}&limit=6&${vehicleParam(vehicleId)}`),

  lookUpVehicle: (registration: string) =>
    request<LookupResult>('/api/vehicles/lookup', { method: 'POST', body: JSON.stringify({ registration }) }),

  createBasket: () => request<Basket>('/api/baskets', { method: 'POST' }),
  getBasket: (id: string) => request<Basket>(`/api/baskets/${id}`),
  addToBasket: (id: string, productId: string, quantity = 1) =>
    request<Basket>(`/api/baskets/${id}/items`, { method: 'POST', body: JSON.stringify({ productId, quantity }) }),
  setQuantity: (id: string, productId: string, quantity: number) =>
    request<Basket>(`/api/baskets/${id}/items/${productId}`, { method: 'PATCH', body: JSON.stringify({ quantity }) }),
  removeFromBasket: (id: string, productId: string) =>
    request<Basket>(`/api/baskets/${id}/items/${productId}`, { method: 'DELETE' }),

  placeOrder: (basketId: string, form: OrderForm) =>
    request<Order>('/api/orders', { method: 'POST', body: JSON.stringify({ basketId, ...form }) }),
  getOrder: (reference: string) => request<Order>(`/api/orders/${encodeURIComponent(reference)}`),

  requestPart: (form: FormData) => request<PartRequestReceived>('/api/part-requests', { method: 'POST', body: form })
};

const whole = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 });
const exact = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

/** £640 for whole pounds, £9.95 otherwise. */
export const price = (value: number) => (Number.isInteger(value) ? whole.format(value) : exact.format(value));
export const money = (value: number) => exact.format(value);
