import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { OrderForm } from '../api/types';
import { Layout } from '../components/Layout';
import { LineList, Totals } from '../components/OrderSummary';
import { PageTitle } from '../components/PageTitle';
import { useStore } from '../state/StoreContext';

const EMPTY: OrderForm = { name: '', email: '', phone: '', line1: '', line2: '', town: '', postcode: '', notes: '' };

const REQUIRED: { key: keyof OrderForm; message: string }[] = [
  { key: 'name', message: 'Enter your full name.' },
  { key: 'email', message: 'Enter your email address.' },
  { key: 'phone', message: 'Enter a phone number so the courier can reach you.' },
  { key: 'line1', message: 'Enter the first line of your address.' },
  { key: 'town', message: 'Enter your town or city.' },
  { key: 'postcode', message: 'Enter your postcode.' }
];

export function CheckoutPage() {
  const { basket, refreshBasket } = useStore();
  const navigate = useNavigate();
  const [form, setForm] = useState<OrderForm>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<keyof OrderForm, string>>>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);
  const summary = useRef<HTMLDivElement>(null);
  const lines = basket?.lines ?? [];

  const set = (key: keyof OrderForm) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found: typeof errors = {};
    for (const r of REQUIRED) if (!form[r.key].trim()) found[r.key] = r.message;
    setErrors(found);
    setServerError('');
    if (Object.keys(found).length > 0 || !basket) {
      requestAnimationFrame(() => summary.current?.focus());
      return;
    }
    setBusy(true);
    try {
      const order = await api.placeOrder(basket.id, form);
      // Leave the page before the emptied basket re-renders it as "Your basket is empty".
      navigate(`/order-received/${order.reference}`);
      void refreshBasket();
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'We couldn’t place your order. Try again.');
      requestAnimationFrame(() => summary.current?.focus());
    } finally {
      setBusy(false);
    }
  }

  if (lines.length === 0) {
    return (
      <Layout title="Checkout">
        <PageTitle title="Checkout" crumbs={[{ label: 'Basket', to: '/basket' }, { label: 'Checkout' }]} />
        <div className="container empty empty--page">
          <h2>Your basket is empty</h2>
          <p>Add a part to your basket before you check out.</p>
          <div className="empty__actions"><Link className="btn btn--primary" to="/products">Browse parts</Link></div>
        </div>
      </Layout>
    );
  }

  const errorList = Object.entries(errors) as [keyof OrderForm, string][];
  const field = (key: keyof OrderForm, label: string, props: { type?: string; autoComplete?: string; optional?: boolean; inputMode?: 'tel' | 'email' } = {}) => (
    <div className="field">
      <label htmlFor={`co-${key}`}>{label}{props.optional && <span className="field__optional"> (optional)</span>}</label>
      {errors[key] && <p id={`co-${key}-error`} className="field-error">{errors[key]}</p>}
      <input id={`co-${key}`} className="input" type={props.type ?? 'text'} value={form[key]} onChange={set(key)}
        autoComplete={props.autoComplete} inputMode={props.inputMode} aria-invalid={!!errors[key] || undefined}
        aria-describedby={errors[key] ? `co-${key}-error` : undefined} />
    </div>
  );

  return (
    <Layout title="Checkout">
      <PageTitle title="Checkout" crumbs={[{ label: 'Basket', to: '/basket' }, { label: 'Checkout' }]} />
      <div className="container">
        <form className="two-col" onSubmit={submit} noValidate>
          <div className="checkout">
            {(errorList.length > 0 || serverError) && (
              <div className="notice notice--error" role="alert" tabIndex={-1} ref={summary}>
                <p><strong>{serverError ? 'Your order wasn’t placed' : 'Check your details'}</strong></p>
                {serverError ? <p>{serverError}</p> : (
                  <ul>{errorList.map(([k, m]) => <li key={k}><a href={`#co-${k}`}>{m}</a></li>)}</ul>
                )}
              </div>
            )}

            <fieldset className="checkout__group">
              <legend className="panel__title">Contact details</legend>
              {field('name', 'Full name', { autoComplete: 'name' })}
              <div className="field-row">
                {field('email', 'Email', { type: 'email', autoComplete: 'email', inputMode: 'email' })}
                {field('phone', 'Phone', { type: 'tel', autoComplete: 'tel', inputMode: 'tel' })}
              </div>
            </fieldset>

            <fieldset className="checkout__group">
              <legend className="panel__title">Delivery address</legend>
              {field('line1', 'Address line 1', { autoComplete: 'address-line1' })}
              {field('line2', 'Address line 2', { autoComplete: 'address-line2', optional: true })}
              <div className="field-row">
                {field('town', 'Town or city', { autoComplete: 'address-level2' })}
                {field('postcode', 'Postcode', { autoComplete: 'postal-code' })}
              </div>
              <div className="field">
                <label htmlFor="co-notes">Delivery notes<span className="field__optional"> (optional)</span></label>
                <textarea id="co-notes" className="input" rows={3} maxLength={500} value={form.notes} onChange={set('notes')}
                  placeholder={basket!.delivery.method === 'PALLET' ? 'e.g. access for a pallet lorry, best times to deliver' : 'e.g. leave with a neighbour'} />
              </div>
            </fieldset>

            <fieldset className="checkout__group">
              <legend className="panel__title">Payment</legend>
              <div className="notice">
                <p><strong>Payment isn’t connected yet.</strong></p>
                <p>Card, Apple Pay and Google Pay will be added when the payment provider is chosen. Placing an order now reserves the parts; no payment is taken.</p>
              </div>
            </fieldset>
          </div>

          <aside className="panel" aria-labelledby="co-summary-title">
            <h2 id="co-summary-title" className="panel__title">Your order</h2>
            <LineList lines={lines} />
            <Totals totals={basket!} />
            <button type="submit" className="btn btn--primary btn--block" disabled={busy}>{busy ? 'Placing order…' : 'Place order'}</button>
            <p className="panel__note">By placing your order you agree to our <Link to="/terms">terms and conditions</Link>. Read our <Link to="/privacy">privacy policy</Link> to see how we use your details.</p>
          </aside>
        </form>
      </div>
    </Layout>
  );
}
