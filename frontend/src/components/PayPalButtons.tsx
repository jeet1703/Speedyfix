import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Order } from '../api/types';

interface PayPalButtonsApi {
  Buttons: (options: {
    style?: Record<string, string | number>;
    onClick?: (data: unknown, actions: { resolve: () => Promise<void>; reject: () => Promise<void> }) => Promise<void> | void;
    createOrder: () => Promise<string>;
    onApprove: (data: { orderID: string }) => Promise<void>;
    onCancel?: () => void;
    onError: (err: unknown) => void;
  }) => { render: (el: HTMLElement) => Promise<void>; close?: () => Promise<void> };
}

declare global {
  interface Window { paypal?: PayPalButtonsApi }
}

let sdk: Promise<PayPalButtonsApi> | null = null;

/** Thrown by the checkout's order step when the order couldn't be placed; checkout shows why. */
export class CheckoutStopped extends Error {}

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

interface Props {
  clientId: string;
  /** The unpaid order's reference, or (at checkout) a function that places the order and returns it. */
  reference: string | (() => Promise<string>);
  onPaid: (o: Order) => void;
  /** Runs when a button is clicked, before PayPal opens: false stops it (the checkout form isn't filled in, say). */
  canPay?: () => boolean;
  /** The shopper closed PayPal without paying (the order stays, unpaid). */
  onCancel?: (reference: string) => void;
}

/**
 * PayPal's buttons (PayPal, and debit or credit card) for an unpaid order. The server creates and captures the
 * payment; onPaid gets the order once the server has confirmed it with PayPal.
 */
export function PayPalButtons({ clientId, reference, onPaid, canPay, onCancel }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  // The latest callbacks, without re-drawing PayPal's buttons each time the page re-renders.
  const props = useRef({ reference, onPaid, canPay, onCancel });
  props.current = { reference, onPaid, canPay, onCancel };
  const placed = useRef<string | null>(typeof reference === 'string' ? reference : null);
  const key = typeof reference === 'string' ? reference : 'checkout';

  useEffect(() => {
    let live = true;
    let buttons: ReturnType<PayPalButtonsApi['Buttons']> | null = null;
    loadPayPal(clientId)
      .then((paypal) => {
        if (!live || !box.current) return;
        buttons = paypal.Buttons({
          // PayPal draws these in its own frame, so only its options apply. Black matches the shop's dark buttons (and
          // PayPal's card button, which is always black); height and corners match .btn.
          style: { layout: 'vertical', color: 'black', shape: 'rect', label: 'pay', height: 48 },
          onClick: (_data, actions) => (props.current.canPay && !props.current.canPay() ? actions.reject() : actions.resolve()),
          createOrder: async () => {
            setError('');
            const ref = props.current.reference;
            // At checkout the order is placed once; a second try (after closing PayPal) pays the same order.
            const orderRef = placed.current ?? (typeof ref === 'string' ? ref : await ref());
            placed.current = orderRef;
            return (await api.startPayPal(orderRef)).paypalOrderId;
          },
          onApprove: async ({ orderID }) => {
            try {
              props.current.onPaid(await api.capturePayPal(placed.current!, orderID));
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'We couldn’t confirm your payment. Call us before trying again.');
            }
          },
          onCancel: () => {
            if (placed.current) props.current.onCancel?.(placed.current);
          },
          onError: (err) => {
            // A checkout form problem is shown by the form itself.
            if (err instanceof CheckoutStopped) return;
            if (err instanceof ApiError) setError(err.message);
            else setError('PayPal had a problem, and you haven’t been charged. Try again, or call us to pay by phone.');
          }
        });
        return buttons.render(box.current);
      })
      .catch(() => live && setError('PayPal couldn’t load. Check your connection and refresh the page, or call us to pay by phone.'))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
      void buttons?.close?.().catch(() => undefined);
    };
  }, [clientId, key]);

  return (
    <div className="paypal">
      {loading && <p className="page-body__note">Loading secure payment…</p>}
      <div ref={box} className="paypal__buttons" />
      {error && <p className="notice notice--error" role="alert">{error}</p>}
    </div>
  );
}
