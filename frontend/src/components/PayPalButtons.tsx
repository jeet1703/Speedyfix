import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Order } from '../api/types';
import { Icon } from './Icons';

interface PayPalField { render: (el: HTMLElement) => Promise<void> }

interface CardFieldsApi {
  isEligible: () => boolean;
  NameField: (o?: object) => PayPalField;
  NumberField: (o?: object) => PayPalField;
  ExpiryField: (o?: object) => PayPalField;
  CVVField: (o?: object) => PayPalField;
  getState: () => Promise<{ isFormValid: boolean }>;
  submit: () => Promise<void>;
}

interface PayPalButtonsApi {
  FUNDING: { PAYPAL: string };
  Buttons: (options: {
    fundingSource?: string;
    style?: Record<string, string | number>;
    onClick?: (data: unknown, actions: { resolve: () => Promise<void>; reject: () => Promise<void> }) => Promise<void> | void;
    createOrder: () => Promise<string>;
    onApprove: (data: { orderID: string }, actions: { restart: () => Promise<void> }) => Promise<void>;
    onCancel?: () => void;
    onError: (err: unknown) => void;
  }) => { render: (el: HTMLElement) => Promise<void>; close?: () => Promise<void> };
  CardFields: (options: {
    style?: Record<string, Record<string, string>>;
    createOrder: () => Promise<string>;
    onApprove: (data: { orderID: string }) => Promise<void>;
    onError: (err: unknown) => void;
  }) => CardFieldsApi;
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
      s.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=GBP&intent=capture&components=buttons,card-fields`;
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
 * Paying an unpaid order: a card form on the page (PayPal's card fields, charged through the shop's own PayPal account,
 * so cards from countries PayPal's own checkout refuses, like India, work), and PayPal's button for PayPal accounts.
 * Where the card form isn't available (PayPal hasn't switched it on for the account), PayPal's own card button shows
 * instead. The server creates and captures the payment; onPaid gets the order once the server has confirmed it.
 */
export function PayPalButtons({ clientId, reference, onPaid, canPay, onCancel }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const nameBox = useRef<HTMLDivElement>(null);
  const numberBox = useRef<HTMLDivElement>(null);
  const expiryBox = useRef<HTMLDivElement>(null);
  const cvvBox = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [card, setCard] = useState<CardFieldsApi | null>(null);
  const [paying, setPaying] = useState(false);
  // The latest callbacks, without re-drawing PayPal's buttons each time the page re-renders.
  const props = useRef({ reference, onPaid, canPay, onCancel });
  props.current = { reference, onPaid, canPay, onCancel };
  const placed = useRef<string | null>(typeof reference === 'string' ? reference : null);
  const key = typeof reference === 'string' ? reference : 'checkout';

  useEffect(() => {
    let live = true;
    let buttons: ReturnType<PayPalButtonsApi['Buttons']> | null = null;

    // At checkout the order is placed once; a second try (after closing PayPal, or a declined card) pays the same order.
    async function orderRef() {
      const ref = props.current.reference;
      const r = placed.current ?? (typeof ref === 'string' ? ref : await ref());
      placed.current = r;
      return r;
    }

    function failed(err: unknown) {
      // A checkout form problem is shown by the form itself.
      if (err instanceof CheckoutStopped) return;
      if (err instanceof ApiError) setError(err.message);
      else setError('The payment didn’t go through, and you haven’t been charged. Check the card details and try again, or call us to pay by phone.');
    }

    loadPayPal(clientId)
      .then(async (paypal) => {
        if (!live || !box.current) return;
        const cardFields = paypal.CardFields({
          style: { input: { 'font-size': '16px', padding: '12px' }, '.invalid': { color: '#b42318' } },
          createOrder: async () => {
            setError('');
            return (await api.startPayPal(await orderRef(), true)).paypalOrderId;
          },
          onApprove: async ({ orderID }) => {
            try {
              props.current.onPaid(await api.capturePayPal(placed.current!, orderID));
            } catch (e) {
              failed(e);
            } finally {
              setPaying(false);
            }
          },
          onError: (err) => {
            setPaying(false);
            failed(err);
          }
        });
        const withCard = cardFields.isEligible() && !!nameBox.current && !!numberBox.current && !!expiryBox.current && !!cvvBox.current;
        if (withCard) {
          await Promise.all([
            cardFields.NameField({ placeholder: 'Name on card' }).render(nameBox.current!),
            cardFields.NumberField({ placeholder: 'Card number' }).render(numberBox.current!),
            cardFields.ExpiryField({ placeholder: 'MM / YY' }).render(expiryBox.current!),
            cardFields.CVVField({ placeholder: 'Security code' }).render(cvvBox.current!)
          ]);
          if (live) setCard(cardFields);
        }
        if (!live || !box.current) return;
        buttons = paypal.Buttons({
          // With the card form on the page, only PayPal's own button; otherwise PayPal's card button too.
          ...(withCard ? { fundingSource: paypal.FUNDING.PAYPAL } : {}),
          // PayPal draws these in its own frame, so only its options apply; height and corners match .btn. Without the
          // card form, black matches the shop's dark buttons (and PayPal's card button, which is always black).
          style: withCard
            ? { layout: 'vertical', color: 'gold', shape: 'rect', label: 'paypal', height: 48 }
            : { layout: 'vertical', color: 'black', shape: 'rect', label: 'pay', height: 48 },
          onClick: (_data, actions) => (props.current.canPay && !props.current.canPay() ? actions.reject() : actions.resolve()),
          createOrder: async () => {
            setError('');
            return (await api.startPayPal(await orderRef())).paypalOrderId;
          },
          onApprove: async ({ orderID }, actions) => {
            try {
              props.current.onPaid(await api.capturePayPal(placed.current!, orderID));
            } catch (e) {
              // A declined card: PayPal reopens so the shopper can pick another card or pay another way (PayPal's advice).
              if (e instanceof ApiError && e.code === 'DECLINED') {
                setError(e.message);
                return actions.restart();
              }
              setError(e instanceof ApiError ? e.message : 'We couldn’t confirm your payment. Call us before trying again.');
            }
          },
          onCancel: () => {
            if (placed.current) props.current.onCancel?.(placed.current);
          },
          onError: (err) => {
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
      setCard(null);
      void buttons?.close?.().catch(() => undefined);
    };
  }, [clientId, key]);

  async function payByCard() {
    if (!card || paying) return;
    if (props.current.canPay && !props.current.canPay()) return;
    setError('');
    setPaying(true);
    try {
      // Check the card fields first: at checkout, submitting places the order, which mustn't happen for a mistyped card.
      const state = await card.getState().catch(() => null);
      if (state && !state.isFormValid) {
        setPaying(false);
        setError('Check the card details: the name, number, expiry date and security code.');
        return;
      }
      // Checks the fields, then creates the payment, runs the bank's check where the card needs it, and calls onApprove.
      await card.submit();
    } catch (e) {
      setPaying(false);
      if (e instanceof CheckoutStopped) return;
      // PayPal's reason, for staff looking at the browser console; the shopper sees a plain message.
      console.warn('Card payment not submitted', e);
      // At checkout the order may already be placed: say so, so the shopper knows trying again won't order twice.
      const saved = typeof props.current.reference !== 'string' && placed.current
        ? ` Your order ${placed.current} is saved and its parts are reserved: try again here, or pay later from your account.` : '';
      setError((e instanceof ApiError ? e.message : 'The card wasn’t accepted. Check the card number, expiry date and security code.') + saved);
    }
  }

  return (
    <div className="paypal">
      {loading && <p className="page-body__note">Loading secure payment…</p>}
      <div className="card-pay" hidden={!card}>
        <p className="card-pay__title"><Icon name="lock" size={18} />Pay by debit or credit card</p>
        <p className="card-pay__secure">Your card details go straight to PayPal over an encrypted connection. We never see or store them.</p>
        <div ref={nameBox} className="card-pay__field" />
        <div ref={numberBox} className="card-pay__field" />
        <div className="card-pay__row">
          <div ref={expiryBox} className="card-pay__field" />
          <div ref={cvvBox} className="card-pay__field" />
        </div>
        <button type="button" className="btn btn--dark btn--block card-pay__submit" onClick={payByCard} disabled={paying}>
          <Icon name="lock" size={18} />{paying ? 'Paying…' : 'Pay securely by card'}
        </button>
        <p className="card-pay__note">You don’t need a PayPal account. Your bank may ask you to confirm the payment.</p>
      </div>
      {card && <p className="card-pay__or"><span>or</span></p>}
      <div ref={box} className="paypal__buttons" />
      {error && <p className="notice notice--error" role="alert">{error}</p>}
    </div>
  );
}
