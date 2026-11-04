import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, money } from '../api/client';
import type { Order, Product, Site } from '../api/types';
import { useStore } from '../state/StoreContext';
import { telHref, whatsAppHref } from './contactLinks';
import { Price } from './Price';

/**
 * The help chat. It reads like an AI assistant (a moment of thought, then the answer written out word by word), but
 * it isn't one: it only ever says what the shop knows. Parts come from our search, a reg is checked against our
 * stock, an order's status needs its number and email, and delivery, returns and contact details come from the
 * site's own settings. Anything else goes to a person (WhatsApp, phone, Request a Part).
 */

type Topic = 'part' | 'order' | 'delivery' | 'returns' | 'warranty' | 'contact';

interface Message {
  id: number;
  from: 'bot' | 'me';
  /** The words, written out one by one for the assistant. **Like this** for bold. */
  text: string;
  /** Links, part cards and the like, shown once the words have finished. */
  extra?: ReactNode;
}

const STATUS: Record<Order['status'], string> = {
  AWAITING_PAYMENT: 'waiting for payment',
  AWAITING_DISPATCH: 'paid and being prepared for dispatch',
  DISPATCHED: 'on its way',
  CANCELLED: 'cancelled'
};

/** UK registrations, with or without the space: current (AB12 CDE), prefix (A123 BCD) and suffix (ABC 123D) styles. */
const REG = /^\s*([A-Z]{2}\d{2}\s?[A-Z]{3}|[A-Z]\d{1,3}\s?[A-Z]{3}|[A-Z]{3}\s?\d{1,3}[A-Z])\s*$/i;
const ORDER_REF = /\bSF-?[A-Z0-9]{8}\b/i;
const EMAIL = /[^\s@]+@[^\s@]+\.[^\s@]+/;

/** Which answer a typed question is about, if any; anything else is treated as a part search. */
function topicOf(text: string): Topic | null {
  const t = text.toLowerCase();
  if (ORDER_REF.test(text) || /\b(my order|order status|track|where('| i)s my|dispatched|arriv)/.test(t)) return 'order';
  if (/\b(return|refund|send (it )?back|exchange)/.test(t)) return 'returns';
  if (/\b(warrant|guarantee)/.test(t)) return 'warranty';
  if (/\b(deliver|ship|postage|courier|pallet|how long|international)/.test(t)) return 'delivery';
  if (/\b(phone|call|contact|whatsapp|email|address|speak|human|person|opening)\b/.test(t)) return 'contact';
  return null;
}

const pick = <T,>(options: T[]) => options[Math.floor(Math.random() * options.length)];
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const reducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
const formatRef = (ref: string) => ref.toUpperCase().replace(/^SF-?/, 'SF-');

let nextId = 1;

/** Splits "**bold** words" into words, each knowing whether it's bold, keeping the spaces. */
function words(text: string): { word: string; bold: boolean }[] {
  const out: { word: string; bold: boolean }[] = [];
  text.split(/(\*\*[^*]+\*\*)/).forEach((part) => {
    const bold = part.startsWith('**') && part.endsWith('**');
    ((bold ? part.slice(2, -2) : part).match(/\S+\s*|\s+/g) ?? []).forEach((w) => out.push({ word: w, bold }));
  });
  return out;
}

/** The assistant's words appearing a few at a time, like an AI writing its answer, with a cursor while it writes. */
function Streamed({ text, onDone }: { text: string; onDone: () => void }) {
  const all = useRef(words(text)).current;
  const [shown, setShown] = useState(reducedMotion() ? all.length : 0);
  const done = useRef(onDone);
  done.current = onDone;

  useEffect(() => {
    if (shown >= all.length) {
      done.current();
      return;
    }
    // About 25-45ms a word, faster for long answers, so nothing takes more than ~2 seconds to write.
    const pace = Math.min(45, 1900 / all.length);
    const t = window.setTimeout(() => setShown((n) => Math.min(all.length, n + (Math.random() < 0.3 ? 2 : 1))), pace * (0.6 + Math.random() * 0.8));
    return () => window.clearTimeout(t);
  }, [shown, all.length]);

  return (
    <span className="chat__text">
      {all.slice(0, shown).map((w, i) => (w.bold ? <strong key={i}>{w.word}</strong> : <span key={i}>{w.word}</span>))}
      {shown < all.length && <span className="chat__cursor" aria-hidden="true" />}
    </span>
  );
}

/** fedex: parcel delivery is priced by FedEx at checkout rather than the flat £9.95. */
export function HelpChat({ site, ai, fedex }: { site: Site; ai: boolean; fedex: boolean }) {
  const { vehicle, setVehicle } = useStore();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  // Assistant messages whose words have finished (so their links and cards can show).
  const [finished, setFinished] = useState<Set<number>>(new Set());
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  // What the assistant is "doing" while it thinks ("Checking our stock"); null when it isn't.
  const [thinking, setThinking] = useState<string | null>(null);
  // Replies go out one after another: think, write, then the next.
  const queue = useRef<Promise<void>>(Promise.resolve());
  const writing = useRef(new Map<number, () => void>());
  // "Where's my order?" asks for the number, then the email.
  const [orderStep, setOrderStep] = useState<{ reference?: string } | null>(null);
  const list = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);

  const close = () => setOpen(false);

  /** The shopper's message appears at once; the assistant's after a short think, then writes itself out. */
  function say(from: Message['from'], words: string, extra?: ReactNode, afterLookup = false) {
    if (from === 'me') {
      setMessages((m) => [...m, { id: nextId++, from, text: words }]);
      return;
    }
    queue.current = queue.current.then(async () => {
      setThinking((t) => t ?? 'Thinking');
      if (!reducedMotion()) await sleep(Math.min((afterLookup ? 150 : 450) + Math.random() * 400, 1200));
      const id = nextId++;
      const written = new Promise<void>((resolve) => writing.current.set(id, resolve));
      setThinking(null);
      setMessages((m) => [...m, { id, from, text: words, extra }]);
      await written;
    });
  }

  function wrote(id: number) {
    setFinished((s) => new Set(s).add(id));
    writing.current.get(id)?.();
    writing.current.delete(id);
  }

  /** A real lookup, with a status line while it runs, and at least a moment's thought so it doesn't feel abrupt. */
  async function withThinking<T>(label: string, work: Promise<T>): Promise<T> {
    await queue.current;
    setThinking(label);
    const [result] = await Promise.all([work, sleep(reducedMotion() ? 0 : 800 + Math.random() * 400)]);
    return result;
  }

  useEffect(() => {
    if (open && messages.length === 0) {
      say('bot', pick([
        'Hi! I’m the SpeedyFix assistant. I can find a part, check what fits your car, track an order, or answer questions about delivery and returns. What can I help with?',
        'Hello! Looking for a part? Tell me the part number, what you need, or your reg, and I’ll check our stock. I can also track an order or explain delivery and returns.'
      ]));
    }
    if (open) requestAnimationFrame(() => input.current?.focus());
  }, [open]); // runs when the panel opens

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  useEffect(() => {
    list.current?.scrollTo({ top: list.current.scrollHeight, behavior: 'smooth' });
  }, [messages, finished, thinking]);

  const person = (
    <span className="chat__links">
      <a href={whatsAppHref(site.whatsapp)} target="_blank" rel="noopener noreferrer">WhatsApp us</a>
      <a href={telHref(site.phone)}>Call {site.phone}</a>
      <Link to="/request-a-part" onClick={close}>Request a Part</Link>
    </span>
  );

  function answer(topic: Topic) {
    switch (topic) {
      case 'part':
        say('bot', pick(['Sure! What are you looking for? ', 'Happy to help. ']) +
          'You can give me a part number, describe the part (like “Qashqai turbo”), or type your reg and I’ll check what fits.');
        break;
      case 'order':
        setOrderStep({});
        say('bot', 'I can check that for you. What’s your order number? It starts with **SF-** and it’s on your order confirmation.');
        break;
      case 'delivery':
        say('bot', `Most UK orders arrive within **7 working days**. We ship with ${site.couriers}. ${fedex ? 'Parcels go by **FedEx**: enter your postcode at checkout to see the price and date for each service. The cheapest is free on orders over £100. Engines' : 'Parcels are **£9.95**, or free on orders over £100, and engines'} and gearboxes travel by pallet for **£79**. We can ship abroad too, at extra cost.`,
          <Link to="/delivery" onClick={close}>Read delivery and returns</Link>);
        break;
      case 'returns':
        say('bot', `Here’s how returns work: ${site.returns}`, <Link to="/delivery" onClick={close}>Full returns policy</Link>);
        break;
      case 'warranty':
        say('bot', 'Aftermarket and refurbished parts come with a **3 months replacement warranty**. It covers the part only, not labour, towing or rentals. There’s no warranty on new genuine parts or electrical parts.', <Link to="/delivery" onClick={close}>Full returns and warranty policy</Link>);
        break;
      case 'contact':
        say('bot', `Of course. The quickest way to reach the team is WhatsApp, or you can call or email ${site.email}.`, person);
        break;
    }
  }

  async function findParts(query: string) {
    const results = await withThinking(vehicle ? `Searching our stock for your ${vehicle.make} ${vehicle.model}` : 'Searching our stock', api.search(query, vehicle?.id, true));
    if (results.length === 0) {
      say('bot', `I couldn’t find “${query}” in stock right now. Good news though: the team can often source parts we don’t have listed. Send them a quick message:`, person, true);
      return;
    }
    const top = results.slice(0, 3);
    say('bot', (top.length === 1 ? 'I found **1 part** that matches' : `Good news! I found **${results.length} parts** that match`) +
      (vehicle ? `, checked against your ${vehicle.make} ${vehicle.model}` : '') + `. ${top.length > 1 ? 'Here are the top picks:' : 'Here it is:'}`,
      partCards(top, query, results.length), true);
  }

  async function checkReg(reg: string) {
    try {
      const r = await withThinking('Looking up your reg with the DVSA', api.lookUpVehicle(reg, true));
      setVehicle(r.vehicle);
      const v = r.vehicle;
      const car = `${v.year ? `${v.year} ` : ''}${v.make} ${v.model}, ${v.engine}`;
      if (r.fittingParts > 0) {
        say('bot', `That’s a **${car}**. We have **${r.fittingParts} part${r.fittingParts === 1 ? '' : 's'}** that may fit it. I’ll keep your car in mind, so tell me which part you need, or browse them all:`,
          <Link to="/products?fitsOnly=true" onClick={close}>Show parts for my car</Link>, true);
      } else {
        say('bot', `That’s a **${car}**. We don’t have parts listed for it right now, but the team may be able to source what you need:`, person, true);
      }
    } catch (e) {
      say('bot', `${e instanceof ApiError ? e.message : 'I couldn’t look up that reg.'} You can also search by part number.`, undefined, true);
    }
  }

  async function trackOrder(reference: string, email: string) {
    try {
      const o = await withThinking('Finding your order', api.lookupOrder(reference, email));
      const sent = o.dispatch ? ` It went with ${o.dispatch.courier}${o.dispatch.trackingNumber ? `, tracking number **${o.dispatch.trackingNumber}**` : ''}.` : '';
      say('bot', `Found it! Order **${o.reference}** (${money(o.total)}) is **${STATUS[o.status]}**.${sent}`,
        o.status === 'AWAITING_PAYMENT' ? <Link to={`/order-received/${o.reference}`} onClick={close}>Pay for this order</Link> : undefined, true);
    } catch (e) {
      say('bot', `${e instanceof ApiError ? e.message : 'I couldn’t look up that order.'}`, person, true);
    }
  }

  /** The cards for parts an answer mentions, and a way to all of them. */
  function partCards(parts: Product[], query?: string, total?: number) {
    return (
      <>
        <ul className="chat__parts">
          {parts.map((p) => (
            <li key={p.id}>
              <Link to={`/products/${p.id}`} onClick={close}>
                <img src={p.imageUrl} alt="" width={48} height={48} />
                <span><strong>{p.name}</strong><span><Price product={p} /> · {p.inStock ? `${p.stock} in stock` : 'out of stock'}</span></span>
              </Link>
            </li>
          ))}
        </ul>
        {query && total && total > parts.length && <Link to={`/products?q=${encodeURIComponent(query)}`} onClick={close}>See all {total} results</Link>}
      </>
    );
  }

  /** Asks the AI, with the conversation so far. False if it couldn't answer, so the chat answers by itself instead. */
  async function askAi(q: string): Promise<boolean> {
    const history = [...messages.map((m) => ({ role: m.from === 'me' ? 'user' as const : 'assistant' as const, text: m.text.replace(/\*\*/g, '') })),
      { role: 'user' as const, text: q }];
    try {
      const r = await withThinking(pick(['Thinking', 'Checking our stock', 'Looking into that']), api.chat(history, vehicle?.id));
      if (r.vehicle) setVehicle(r.vehicle);
      say('bot', r.text, r.parts.length ? partCards(r.parts) : undefined, true);
      return true;
    } catch {
      return false;
    }
  }

  async function send(e?: FormEvent) {
    e?.preventDefault();
    const q = text.trim();
    if (!q || busy || thinking) return;
    setText('');
    say('me', q);
    setBusy(true);
    try {
      if (orderStep) {
        const ref = orderStep.reference ?? q.match(ORDER_REF)?.[0];
        const email = q.match(EMAIL)?.[0];
        if (!ref) {
          say('bot', 'Hmm, that doesn’t look like an order number. They look like **SF-AB12CD34**. Could you check it?');
        } else if (!email) {
          setOrderStep({ reference: formatRef(ref) });
          say('bot', 'Thanks! And what’s the email address you ordered with? I need it to make sure the order is yours.');
        } else {
          setOrderStep(null);
          await trackOrder(ref, email);
        }
        return;
      }
      const topic = topicOf(q);
      // Free questions go to the AI when it's on. Order tracking and a reg on its own stay here: they're quicker,
      // and an order number or email never goes to the AI.
      if (ai && topic !== 'order' && !REG.test(q) && await askAi(q)) {
        return;
      }
      if (topic === 'order' && q.match(ORDER_REF)) {
        setOrderStep({ reference: formatRef(q.match(ORDER_REF)![0]) });
        say('bot', 'I’ve got your order number. What’s the email address you ordered with? I need it to make sure the order is yours.');
      } else if (topic) {
        answer(topic);
      } else if (REG.test(q)) {
        await checkReg(q);
      } else {
        await findParts(q);
      }
    } catch {
      say('bot', 'Sorry, something went wrong on our side. Could you try that again? Or reach the team directly:', person, true);
    } finally {
      await queue.current;
      setBusy(false);
      input.current?.focus();
    }
  }

  async function choose(topic: Topic, label: string) {
    if (busy || thinking) return;
    setBusy(true);
    setOrderStep(null);
    say('me', label);
    answer(topic);
    await queue.current;
    setBusy(false);
  }

  return (
    <div className="chat">
      {open && (
        <section className="chat__panel" role="dialog" aria-label="Help chat">
          <header className="chat__head">
            <span className="chat__avatar" aria-hidden="true">✦</span>
            <div>
              <strong>SpeedyFix assistant</strong>
              <span>{ai ? 'AI assistant · answers from our live stock' : 'Answers from our live stock and policies'}</span>
            </div>
            <button type="button" className="chat__close" aria-label="Close help chat" onClick={close}>×</button>
          </header>
          <div className="chat__messages" ref={list}>
            {messages.map((m) => (
              <div key={m.id} className={`chat__msg chat__msg--${m.from}`}>
                {m.from === 'bot' ? <Streamed text={m.text} onDone={() => wrote(m.id)} /> : m.text}
                {m.from === 'bot' && m.extra && finished.has(m.id) && <div className="chat__extra">{m.extra}</div>}
              </div>
            ))}
            {thinking && (
              <div className="chat__msg chat__msg--bot chat__thinking" role="status">
                <span className="chat__dots" aria-hidden="true"><span /><span /><span /></span>
                <span className="chat__thinking-label">{thinking}…</span>
              </div>
            )}
            {/* Screen readers hear each answer once it's complete, not word by word. */}
            <p className="sr-only" aria-live="polite">
              {(() => { const last = [...messages].reverse().find((m) => m.from === 'bot' && finished.has(m.id)); return last ? last.text.replace(/\*\*/g, '') : ''; })()}
            </p>
          </div>
          {ai && <p className="chat__disclaimer">AI can make mistakes. Check the part’s page, or ask the team, before you buy.</p>}
          <div className="chat__quick" role="group" aria-label="Common questions">
            <button type="button" onClick={() => void choose('part', 'Find a part')}>Find a part</button>
            <button type="button" onClick={() => void choose('order', 'Where’s my order?')}>Where’s my order?</button>
            <button type="button" onClick={() => void choose('delivery', 'Delivery')}>Delivery</button>
            <button type="button" onClick={() => void choose('returns', 'Returns')}>Returns</button>
            <button type="button" onClick={() => void choose('contact', 'Talk to a person')}>Talk to a person</button>
          </div>
          <form className="chat__form" onSubmit={(e) => void send(e)}>
            <label className="sr-only" htmlFor="chat-input">Your question</label>
            <input id="chat-input" ref={input} className="input" value={text} onChange={(e) => setText(e.target.value)}
              placeholder={orderStep ? (orderStep.reference ? 'Email address' : 'Order number, like SF-AB12CD34') : 'Ask about a part, your reg or an order'}
              maxLength={100} autoComplete="off" />
            <button type="submit" className="btn btn--primary" disabled={busy || !!thinking || !text.trim()}>Send</button>
          </form>
        </section>
      )}
      <button type="button" className="chat__bubble" aria-expanded={open} aria-label={open ? 'Close help chat' : 'Open help chat'}
        onClick={() => setOpen(!open)}>
        {open ? '×' : <><span aria-hidden="true">✦</span><span className="chat__bubble-text">Ask our assistant</span></>}
      </button>
    </div>
  );
}
