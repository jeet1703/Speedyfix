import type {
  AccountSession, Basket, Customer, Delivery, DeliveryOption, HomeData, LookupResult, Order, OrderForm, PartRequestReceived, Product, ProductDetail,
  ShopResult, SiteData, ChatReply } from './types';
import { progress } from './progress';

export const BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

/** A paid order's VAT invoice (PDF): opens in the browser, or saves with download. */
export function invoiceUrl(reference: string, download = false) {
  return `${BASE}/api/orders/${encodeURIComponent(reference)}/invoice.pdf${download ? '?download=true' : ''}`;
}

/** True once the order is paid, when it has a VAT invoice. */
export function hasInvoice(order: Order) {
  return order.status === 'AWAITING_DISPATCH' || order.status === 'DISPATCHED';
}

/** Error carrying the shopper-facing message from the API's problem details. */
export class ApiError extends Error {
  /** The server's reason code, where it sends one (like DECLINED for a declined payment). */
  constructor(public status: number, message: string, public code?: string) {
    super(message);
  }
}

/**
 * A call to the API. The page's top loading bar shows while it runs, unless quiet: the help chat shows its own
 * "thinking" instead, so its calls don't move the bar across the whole site.
 */
export async function request<T>(path: string, init?: RequestInit, quiet = false): Promise<T> {
  if (quiet) return send<T>(path, init);
  progress.start();
  try {
    return await send<T>(path, init);
  } finally {
    progress.done();
  }
}

async function send<T>(path: string, init?: RequestInit): Promise<T> {
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
    let code: string | undefined;
    try {
      const body = await res.json();
      if (body && typeof body.detail === 'string') detail = body.detail;
      if (body && typeof body.code === 'string') code = body.code;
    } catch {
      /* keep the default message */
    }
    throw new ApiError(res.status, detail, code);
  }
  if (res.status === 204) return undefined as T;
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

  search: (q: string, vehicleId?: string | null, quiet = false) =>
    request<Product[]>(`/api/search?q=${encodeURIComponent(q)}&limit=6&${vehicleParam(vehicleId)}`, undefined, quiet),

  lookUpVehicle: (registration: string, quiet = false) =>
    request<LookupResult>('/api/vehicles/lookup', { method: 'POST', body: JSON.stringify({ registration }) }, quiet),

  createBasket: () => request<Basket>('/api/baskets', { method: 'POST' }),
  getBasket: (id: string) => request<Basket>(`/api/baskets/${id}`),
  addToBasket: (id: string, productId: string, quantity = 1) =>
    request<Basket>(`/api/baskets/${id}/items`, { method: 'POST', body: JSON.stringify({ productId, quantity }) }),
  setQuantity: (id: string, productId: string, quantity: number) =>
    request<Basket>(`/api/baskets/${id}/items/${productId}`, { method: 'PATCH', body: JSON.stringify({ quantity }) }),
  removeFromBasket: (id: string, productId: string) =>
    request<Basket>(`/api/baskets/${id}/items/${productId}`, { method: 'DELETE' }),

  /**
   * The delivery choices for the address, cheapest first. export: outside the UK VAT area, where basket has the parts'
   * prices without UK VAT. Quiet: checkout shows its own "working out delivery".
   */
  deliveryOptions: (basketId: string, postcode: string, town: string, country: string) =>
    request<{ options: DeliveryOption[]; export: boolean; basket: Basket }>(`/api/baskets/${basketId}/delivery-options`,
      { method: 'POST', body: JSON.stringify({ postcode, town, country }) }, true),

  /**
   * token links the order to the signed-in shopper's account; leave it out for a guest checkout. deliveryOption is
   * the id of the delivery chosen at checkout (the server prices it again); null for the cheapest.
   */
  placeOrder: (basketId: string, form: OrderForm, token?: string | null, deliveryOption?: string | null) =>
    request<Order>('/api/orders', {
      method: 'POST', body: JSON.stringify({ basketId, ...form, deliveryOption: deliveryOption ?? null }), headers: token ? bearer(token) : undefined
    }),
  getOrder: (reference: string) => request<Order>(`/api/orders/${encodeURIComponent(reference)}`),
  /** The help chat's AI answer to the conversation so far (the last message is the shopper's). */
  chat: (messages: { role: 'user' | 'assistant'; text: string }[], vehicleId?: string | null) =>
    request<ChatReply>('/api/chat', { method: 'POST', body: JSON.stringify({ messages, vehicleId: vehicleId ?? null }) }, true),
  /** An order by its number and the email it was placed with (the help chat's "Where's my order?"). */
  lookupOrder: (reference: string, email: string) =>
    request<Order>('/api/orders/lookup', { method: 'POST', body: JSON.stringify({ reference, email }) }, true),
  /** card: for the card form on the page rather than PayPal's buttons. */
  startPayPal: (reference: string, card = false) =>
    request<{ paypalOrderId: string }>(`/api/orders/${encodeURIComponent(reference)}/paypal${card ? '?card=true' : ''}`, { method: 'POST' }),
  capturePayPal: (reference: string, paypalOrderId: string) =>
    request<Order>(`/api/orders/${encodeURIComponent(reference)}/paypal/capture`, { method: 'POST', body: JSON.stringify({ paypalOrderId }) }),

  requestPart: (form: FormData) => request<PartRequestReceived>('/api/part-requests', { method: 'POST', body: form }),

  /** credential is the ID token Google's Sign in button hands back. */
  signInWithGoogle: (credential: string) =>
    request<AccountSession>('/api/account/google', { method: 'POST', body: JSON.stringify({ credential }) }),
  /** Emails a 6-digit code; the account is made once confirmEmail sends it back. */
  signUpWithEmail: (name: string, email: string, password: string) =>
    request<void>('/api/account/email/sign-up', { method: 'POST', body: JSON.stringify({ name, email, password }) }),
  resendEmailCode: (email: string) =>
    request<void>('/api/account/email/resend', { method: 'POST', body: JSON.stringify({ email }) }),
  confirmEmail: (email: string, code: string) =>
    request<AccountSession>('/api/account/email/confirm', { method: 'POST', body: JSON.stringify({ email, code }) }),
  signInWithEmail: (email: string, password: string) =>
    request<AccountSession>('/api/account/email/sign-in', { method: 'POST', body: JSON.stringify({ email, password }) }),
  /** "Forgot password": emails a reset code (once a day per account; the same answer for any address). */
  forgotPassword: (email: string) =>
    request<void>('/api/account/email/forgot', { method: 'POST', body: JSON.stringify({ email }) }),
  resetPassword: (email: string, code: string, password: string) =>
    request<AccountSession>('/api/account/email/reset', { method: 'POST', body: JSON.stringify({ email, code, password }) }),
  me: (token: string) => request<Customer>('/api/account', { headers: bearer(token) }),
  signOut: (token: string) => request<void>('/api/account/session', { method: 'DELETE', headers: bearer(token) }),
  myOrders: (token: string) => request<Order[]>('/api/account/orders', { headers: bearer(token) }),
  searches: (token: string) => request<string[]>('/api/account/searches', { headers: bearer(token) }),
  recordSearch: (token: string, query: string) =>
    request<string[]>('/api/account/searches', { method: 'POST', headers: bearer(token), body: JSON.stringify({ query }) }),
  clearSearches: (token: string) => request<void>('/api/account/searches', { method: 'DELETE', headers: bearer(token) })
};

const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });

const whole = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 });
const exact = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

/** £640 for whole pounds, £9.95 otherwise. */
export const price = (value: number) => (Number.isInteger(value) ? whole.format(value) : exact.format(value));
export const money = (value: number) => exact.format(value);

/**
 * A delivery's price as the totals show it: "£9.95", "Free", "At checkout" while FedEx hasn't priced it yet, or
 * "Quoted separately" for manual delivery.
 */
export function deliveryPrice(delivery: Delivery) {
  if (delivery.method === 'QUOTE') return 'At checkout';
  if (delivery.method === 'MANUAL') return 'Quoted separately';
  return delivery.price ? money(delivery.price) : 'Free';
}
