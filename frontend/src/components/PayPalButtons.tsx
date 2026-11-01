import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Order } from '../api/types';

interface PayPalButtonsApi {
  Buttons: (options: {
    style?: Record<string, string | number>;
    createOrder: () => Promise<string>;
    onApprove: (data: { orderID: string }) => Promise<void>;
    onError: (err: unknown) => void;
  }) => { render: (el: HTMLElement) => Promise<void>; close?: () => Promise<void> };
}

declare global {
  interface Window { paypal?: PayPalButtonsApi }
}

let sdk: Promise<PayPalButtonsApi> | null = null;

/** PayPal's script, loaded once per page with the shop's public client id. GBP, taking the payment straight away. */
function loadPayPal(clientId: string): Promise<PayPalButtonsApi> {
  if (!sdk) {
    sdk = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=GBP&intent=capture&components=buttons`;
      s.async = true;
      s.onload = () => (window.paypal ? resolve(window.paypal) : reject(new Error('PayPal didn’t load.')));
      s.onerror = () => { sdk = null; reject(new Error('PayPal didn’t load.')); };
      document.head.appendChild(s);
    });
  }
  return sdk;
}

/**
 * PayPal's buttons (PayPal, and debit or credit card) for an unpaid order. The server creates and captures the
 * payment; onPaid gets the order once the server has confirmed it with PayPal.
 */
export function PayPalButtons({ clientId, order, onPaid }: { clientId: string; order: Order; onPaid: (o: Order) => void }) {
  const box = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const paid = useRef(onPaid);
  paid.current = onPaid;

  useEffect(() => {
    let live = true;
    let buttons: ReturnType<PayPalButtonsApi['Buttons']> | null = null;
    loadPayPal(clientId)
      .then((paypal) => {
        if (!live || !box.current) return;
        buttons = paypal.Buttons({
          style: { layout: 'vertical', color: 'gold', shape: 'rect', label: 'pay', height: 48 },
          createOrder: async () => {
            setError('');
            return (await api.startPayPal(order.reference)).paypalOrderId;
          },
          onApprove: async ({ orderID }) => {
            try {
              paid.current(await api.capturePayPal(order.reference, orderID));
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'We couldn’t confirm your payment. Call us before trying again.');
            }
          },
          onError: () => setError('PayPal had a problem, and you haven’t been charged. Try again, or call us to pay by phone.')
        });
        return buttons.render(box.current);
      })
      .catch(() => live && setError('PayPal couldn’t load. Check your connection and refresh the page, or call us to pay by phone.'))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
      void buttons?.close?.().catch(() => undefined);
    };
  }, [clientId, order.reference]);

  return (
    <div className="paypal">
      {loading && <p className="page-body__note">Loading secure payment…</p>}
      <div ref={box} className="paypal__buttons" />
      {error && <p className="notice notice--error" role="alert">{error}</p>}
    </div>
  );
}
