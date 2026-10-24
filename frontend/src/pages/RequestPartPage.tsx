import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { PartRequestReceived, ReplyBy } from '../api/types';
import { Layout } from '../components/Layout';
import { PageTitle } from '../components/PageTitle';
import { PlateField } from '../components/PlateField';
import { useSite } from '../state/SiteContext';
import { useStore } from '../state/StoreContext';

const REPLY: { value: ReplyBy; label: string }[] = [
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'PHONE', label: 'Phone call' },
  { value: 'EMAIL', label: 'Email' }
];

const STEPS = [
  'We identify your vehicle from the reg.',
  'We search our stock and suppliers.',
  'We send a price, condition and lead time.',
  'You pay only when you’re happy with the quote.'
];

export function RequestPartPage() {
  const { vehicle } = useStore();
  const { data } = useSite();
  const [reg, setReg] = useState(vehicle?.registration ?? '');
  const [replyBy, setReplyBy] = useState<ReplyBy>('WHATSAPP');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState<PartRequestReceived | null>(null);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setError('');
    setBusy(true);
    try {
      setSent(await api.requestPart(form));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'We couldn’t send your request. Try again.');
    } finally {
      setBusy(false);
    }
  }

  const site = data?.site;
  return (
    <Layout title="Request a part">
      <PageTitle title="Request a part" crumbs={[{ label: 'Request a part' }]} />
      <div className="container two-col two-col--wide">
        {sent ? (
          <div className="received">
            <div className="received__banner" role="status">
              <div>
                <h2>Request {sent.reference} sent</h2>
                <p>
                  {sent.vehicle ? <>We’ve identified your vehicle as a {sent.vehicle}. </> : null}
                  We’ll reply by {sent.replyBy === 'WHATSAPP' ? 'WhatsApp' : sent.replyBy === 'PHONE' ? 'phone' : 'email'} with a price and lead time.
                </p>
              </div>
            </div>
            <div className="empty__actions">
              <button type="button" className="btn btn--outline" onClick={() => setSent(null)}>Request another part</button>
              <Link className="btn btn--primary" to="/products">Browse parts</Link>
            </div>
          </div>
        ) : (
          <form className="request" onSubmit={submit} noValidate encType="multipart/form-data">
            <h2 className="panel__title">Tell us what you need</h2>
            <p className="request__intro">Can’t find a part in the shop? Send your reg and the part you need, and we’ll source it and reply with a price and lead time.</p>

            {error && <p className="notice notice--error" role="alert">{error}</p>}

            <div className="field">
              <label htmlFor="rq-reg">Your registration</label>
              <PlateField id="rq-reg" value={reg} onChange={setReg} required />
            </div>
            <div className="field">
              <label htmlFor="rq-part">Part you need</label>
              <input id="rq-part" name="part" className="input" placeholder="e.g. turbocharger, or the part number" maxLength={200} required />
            </div>
            <div className="field">
              <label htmlFor="rq-notes">Anything else we should know<span className="field__optional"> (optional)</span></label>
              <textarea id="rq-notes" name="notes" className="input" rows={4} maxLength={2000} />
            </div>
            <div className="field">
              <label htmlFor="rq-photos">Photos of the old part<span className="field__optional"> (optional)</span></label>
              <input id="rq-photos" name="photos" className="input input--file" type="file" accept="image/*" multiple aria-describedby="rq-photos-hint" />
              <p id="rq-photos-hint" className="field__hint">Up to 4 photos, 5 MB each. A clear shot of any part number helps.</p>
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="rq-name">Your name</label>
                <input id="rq-name" name="name" className="input" autoComplete="name" maxLength={100} required />
              </div>
              <div className="field">
                <label htmlFor="rq-phone">Phone or WhatsApp{replyBy === 'EMAIL' && <span className="field__optional"> (optional)</span>}</label>
                <input id="rq-phone" name="phone" className="input" type="tel" inputMode="tel" autoComplete="tel" required={replyBy !== 'EMAIL'} />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="rq-email">Email{replyBy !== 'EMAIL' && <span className="field__optional"> (optional)</span>}</label>
                <input id="rq-email" name="email" className="input" type="email" inputMode="email" autoComplete="email" required={replyBy === 'EMAIL'} />
              </div>
              <div className="field">
                <label htmlFor="rq-reply">How should we reply?</label>
                <select id="rq-reply" name="replyBy" className="input" value={replyBy} onChange={(e) => setReplyBy(e.target.value as ReplyBy)}>
                  {REPLY.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                </select>
              </div>
            </div>
            <div>
              <button type="submit" className="btn btn--primary" disabled={busy}>{busy ? 'Sending…' : 'Send request'}</button>
            </div>
            <p className="field__hint">We use these details only to reply to this request. See our <Link to="/privacy">privacy policy</Link>.</p>
          </form>
        )}

        <aside className="request__side">
          <section className="panel" aria-labelledby="next-title">
            <h2 id="next-title" className="panel__title">What happens next</h2>
            <ol className="steps">
              {STEPS.map((s) => <li key={s}>{s}</li>)}
            </ol>
          </section>
          <section className="panel panel--dark" aria-labelledby="talk-title">
            <h2 id="talk-title" className="panel__title">Prefer to talk?</h2>
            <p>Call {site?.phone}</p>
            <p>WhatsApp {site?.whatsapp}</p>
            <p>Email {site?.email}</p>
          </section>
        </aside>
      </div>
    </Layout>
  );
}
