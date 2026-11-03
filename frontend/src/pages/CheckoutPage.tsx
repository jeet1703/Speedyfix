import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, money } from '../api/client';
import type { Basket, DeliveryOption, Order, OrderForm } from '../api/types';
import { Layout } from '../components/Layout';
import { LineList, Totals } from '../components/OrderSummary';
import { PageTitle } from '../components/PageTitle';
import { CheckoutStopped, PayPalButtons } from '../components/PayPalButtons';
import { useAccount } from '../state/AccountContext';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

const EMPTY: OrderForm = { name: '', email: '', phone: '', line1: '', line2: '', town: '', postcode: '', notes: '' };

/** A full UK postcode, as the server checks it. */
const POSTCODE = /^\s*[A-Za-z]{1,2}[0-9][A-Za-z0-9]?\s*[0-9][A-Za-z]{2}\s*$/;

const ARRIVES = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });

/** "Arrives by Wed 4 Nov" from FedEx's yyyy-MM-dd. */
function arrivesText(date: string | null) {
  if (!date) return null;
  const d = new Date(`${date}T12:00:00`);
  return Number.isNaN(d.getTime()) ? null : `Arrives by ${ARRIVES.format(d)}`;
}

/** The basket's totals with the chosen delivery in place of its own. */
function withDelivery(basket: Basket, option: DeliveryOption | undefined) {
  if (!option) return basket;
  const total = Math.round((basket.subtotal + option.price) * 100) / 100;
  return { ...basket, delivery: { method: option.method, label: option.label, price: option.price }, total, vatIncluded: Math.round(total / 6 * 100) / 100 };
}

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
  const { customer, token } = useAccount();
  const { data } = useSite();
  const navigate = useNavigate();
  const [form, setForm] = useState<OrderForm>(EMPTY);

  // A signed-in shopper starts with their name and email filled in.
  useEffect(() => {
    if (customer) setForm((f) => ({ ...f, name: f.name || customer.name, email: f.email || customer.email }));
  }, [customer]);
  const [errors, setErrors] = useState<Partial<Record<keyof OrderForm, string>>>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);
  const summary = useRef<HTMLDivElement>(null);
  const lines = basket?.lines ?? [];

  // FedEx prices delivery from the postcode (heavy parts by FedEx Freight). The shopper asks for the options with
  // "Show delivery options" and picks one; payment opens only once they have.
  const quoting = !!data?.deliveryQuotes && !!basket && basket.lines.length > 0;
  const [options, setOptions] = useState<DeliveryOption[] | null>(null);
  const [choice, setChoice] = useState<string | null>(null);
  const [quoteState, setQuoteState] = useState<'idle' | 'loading' | 'error'>('idle');
  const [deliveryError, setDeliveryError] = useState('');
  const postcodeOk = POSTCODE.test(form.postcode);
  const quotedFor = useRef('');
  // A new postcode or a changed basket needs new prices: the old options (and the choice) no longer apply.
  useEffect(() => {
    setOptions(null);
    setChoice(null);
    setQuoteState('idle');
  }, [form.postcode, basket?.id, basket?.subtotal, basket?.itemCount]);
  const chosen = options?.find((o) => o.id === choice);

  function loadOptions() {
    if (!basket || !postcodeOk) {
      setErrors((e) => ({ ...e, postcode: 'Enter a full UK postcode, like SW1A 1AA.' }));
      return;
    }
    setDeliveryError('');
    setQuoteState('loading');
    quotedFor.current = form.postcode;
    api.deliveryOptions(basket.id, form.postcode)
      .then((r) => {
        if (quotedFor.current !== form.postcode) return;
        setOptions(r.options);
        setChoice(null);
        setQuoteState('idle');
      })
      .catch(() => setQuoteState('error'));
  }

  const set = (key: keyof OrderForm) => (e: { target: { value: string } }) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    // The message for a field goes as soon as it's filled in.
    setErrors((errs) => {
      if (!errs[key] || !e.target.value.trim()) return errs;
      const rest = { ...errs };
      delete rest[key];
      return rest;
    });
  };

  /** Checks the form, showing what's missing; true when the order can be placed. */
  function check(): boolean {
    const found: typeof errors = {};
    for (const r of REQUIRED) if (!form[r.key].trim()) found[r.key] = r.message;
    if (!found.postcode && quoting && !postcodeOk) found.postcode = 'Enter a full UK postcode, like SW1A 1AA.';
    const delivery = quoting && postcodeOk && !chosen
      ? (options ? 'Choose a delivery option.' : 'Press “Show delivery options” and choose how you’d like it delivered.')
      : '';
    setErrors(found);
    setDeliveryError(delivery);
    setServerError('');
    if (Object.keys(found).length > 0 || delivery || !basket) {
      requestAnimationFrame(() => summary.current?.focus());
      return false;
    }
    return true;
  }

  /** Places the order (reserving the parts); null if it couldn't be, with the reason shown. */
  async function place(): Promise<Order | null> {
    setBusy(true);
    try {
      return await api.placeOrder(basket!.id, form, token, chosen?.id ?? null);
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'We couldn’t place your order. Try again.');
      requestAnimationFrame(() => summary.current?.focus());
      return null;
    } finally {
      setBusy(false);
    }
  }

  /** To the order's page; the basket is refreshed after leaving, so checkout doesn't flash "Your basket is empty". */
  function goToOrder(reference: string) {
    navigate(`/order-received/${reference}`);
    void refreshBasket();
  }

  // Without online payment: place the order, and staff take payment by phone.
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!check()) return;
    const order = await place();
    if (order) goToOrder(order.reference);
  }

  // With PayPal: its buttons check the form, place the order, then take the payment.
  async function placeForPayPal(): Promise<string> {
    const order = await place();
    if (!order) throw new CheckoutStopped();
    return order.reference;
  }

  // Only signed-in shoppers can order (the server checks too): ask them to sign in, then come back here.
  if (lines.length > 0 && data?.signInToBuy && !token) {
    return (
      <Layout title="Checkout">
        <PageTitle title="Checkout" crumbs={[{ label: 'Basket', to: '/basket' }, { label: 'Checkout' }]} />
        <div className="container">
          <div className="two-col">
            <section className="checkout__group" aria-labelledby="co-signin-title">
              <h2 id="co-signin-title" className="panel__title">Sign in to check out</h2>
              <p>You need an account to place an order, so you can follow it and find your VAT invoice later. It takes one click with Google. Your basket is kept.</p>
              <div className="empty__actions">
                <Link className="btn btn--primary" to="/account?next=/checkout">Sign in or create an account</Link>
                <Link className="btn btn--outline" to="/basket">Back to basket</Link>
              </div>
            </section>
            <aside className="panel" aria-labelledby="co-summary-title">
              <h2 id="co-summary-title" className="panel__title">Your order</h2>
              <LineList lines={lines} />
              <Totals totals={basket!} />
            </aside>
          </div>
        </div>
      </Layout>
    );
  }

  if (lines.length === 0) {
    return (
      <Layout title="Checkout">
        <PageTitle title="Checkout" crumbs={[{ label: 'Basket', to: '/basket' }, { label: 'Checkout' }]} />
        <div className="container">
          <div className="empty empty--page">
            <h2>Your basket is empty</h2>
            <p>Add a part to your basket before you check out.</p>
            <div className="empty__actions"><Link className="btn btn--primary" to="/products">Browse parts</Link></div>
          </div>
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
            {(errorList.length > 0 || deliveryError || serverError) && (
              <div className="notice notice--error" role="alert" tabIndex={-1} ref={summary}>
                <p><strong>{serverError ? 'Your order wasn’t placed' : 'Check your details'}</strong></p>
                {serverError ? <p>{serverError}</p> : (
                  <ul>
                    {errorList.map(([k, m]) => <li key={k}><a href={`#co-${k}`}>{m}</a></li>)}
                    {deliveryError && <li><a href="#co-delivery">{deliveryError}</a></li>}
                  </ul>
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

            {quoting && (
              <fieldset className="checkout__group" id="co-delivery" tabIndex={-1} aria-describedby="co-delivery-note">
                <legend className="panel__title">Delivery</legend>
                {deliveryError && <p className="field-error">{deliveryError}</p>}
                <p id="co-delivery-note" className="panel__note">
                  {quoteState === 'loading' ? 'Working out delivery to your postcode…'
                    : quoteState === 'error' ? 'We couldn’t work out delivery just now. Try again, or choose manual delivery and we’ll quote you.'
                    : !options ? 'We deliver to UK addresses. Enter your postcode above, then calculate delivery to see the prices and dates.'
                    : 'Choose how you’d like it delivered.'}
                </p>
                <div>
                  <button type="button" className={options ? 'btn btn--outline' : 'btn btn--primary'} onClick={loadOptions}
                    disabled={quoteState === 'loading'}>
                    {quoteState === 'loading' ? 'Calculating…' : options ? 'Calculate again' : 'Calculate delivery'}
                  </button>
                </div>
                {options && options.length > 0 && (
                  <ul className="delivery-options">
                    {options.map((o) => (
                      <li key={o.id}>
                        <label className={`delivery-option${chosen?.id === o.id ? ' delivery-option--on' : ''}`}>
                          <input type="radio" name="delivery" value={o.id} checked={chosen?.id === o.id}
                            onChange={() => { setChoice(o.id); setDeliveryError(''); }} />
                          <span className="delivery-option__name">
                            {o.method === 'MANUAL' ? 'Manual delivery' : o.label}
                            {o.method === 'MANUAL'
                              ? <small>Don’t want FedEx? Pay for your parts now, then contact us and we’ll quote you from our other delivery partners. Delivery is paid separately.</small>
                              : arrivesText(o.arrives) && <small>{arrivesText(o.arrives)}</small>}
                          </span>
                          <span className="delivery-option__price">{o.method === 'MANUAL' ? 'Quote' : o.price ? money(o.price) : 'Free'}</span>
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
              </fieldset>
            )}

            <fieldset className="checkout__group">
              <legend className="panel__title">Payment</legend>
              <div className="notice">
                {data?.paypalClientId ? (
                  <>
                    <p><strong>Pay securely with PayPal or a debit or credit card.</strong></p>
                    <p>You don’t need a PayPal account to pay by card. Paying places your order. If you close PayPal without paying, your parts stay reserved{data.unpaidHoldMinutes ? ` for ${holdText(data.unpaidHoldMinutes)}` : ''} and you can pay from your order page.</p>
                  </>
                ) : (
                  <>
                    <p><strong>Online payment isn’t switched on yet.</strong></p>
                    <p>Placing an order reserves the parts. We’ll call you to take payment by card over the phone.</p>
                  </>
                )}
              </div>
              {data?.paypalClientId && (quoting && !chosen
                ? <p className="panel__note"><strong>Calculate delivery and choose an option above, then pay here.</strong></p>
                : (
                  <div className="checkout__paypal">
                    <p className="panel__note">
                      To pay: <strong>{money(withDelivery(basket!, chosen).total)}</strong>
                      {chosen && <> including {chosen.method === 'MANUAL' ? 'parts only (delivery quoted separately)' : `${chosen.label}${chosen.price ? '' : ' (free)'}`}</>}.
                    </p>
                    <PayPalButtons clientId={data.paypalClientId} reference={placeForPayPal} canPay={check}
                      onPaid={(o) => goToOrder(o.reference)} onCancel={goToOrder} />
                  </div>
                ))}
            </fieldset>
          </div>

          <aside className="panel" aria-labelledby="co-summary-title">
            <h2 id="co-summary-title" className="panel__title">Your order</h2>
            <LineList lines={lines} />
            <Totals totals={withDelivery(basket!, chosen)} />
            {data?.paypalClientId
              ? <p className="panel__note"><strong>Pay with PayPal or a card in the Payment section{busy ? ' (placing your order…)' : ''}.</strong></p>
              : <button type="submit" className="btn btn--primary btn--block" disabled={busy}>{busy ? 'Placing order…' : 'Place order'}</button>}
            <p className="panel__note">By placing your order you agree to our <Link to="/terms">terms and conditions</Link>. Read our <Link to="/privacy">privacy policy</Link> to see how we use your details.</p>
          </aside>
        </form>
      </div>
    </Layout>
  );
}

/** "2 hours", "45 minutes". */
export function holdText(minutes: number) {
  if (minutes % 60 === 0) return minutes === 60 ? '1 hour' : `${minutes / 60} hours`;
  return `${minutes} minutes`;
}
