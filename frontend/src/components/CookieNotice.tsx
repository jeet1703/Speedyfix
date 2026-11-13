import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adsConsent, onReopenCookieNotice, setAdsConsent, type AdsConsent } from '../state/ads';

/**
 * Asks once whether Google may set advertising cookies (UK PECR: none before the visitor says yes).
 * Stays until answered; the privacy page can bring it back.
 */
export function CookieNotice() {
  const [open, setOpen] = useState(() => adsConsent() === null);

  useEffect(() => onReopenCookieNotice(() => setOpen(true)), []);

  if (!open) return null;

  const choose = (choice: AdsConsent) => {
    setAdsConsent(choice);
    setOpen(false);
  };

  return (
    <section className="cookie-notice" aria-label="Cookies">
      <p>
        We’d like to use Google advertising cookies to see which adverts bring people to the shop. The shop works the
        same either way. <Link to="/privacy">Privacy policy</Link>
      </p>
      <div className="cookie-notice__actions">
        <button type="button" className="btn btn--outline-light" onClick={() => choose('denied')}>Reject</button>
        <button type="button" className="btn btn--primary" onClick={() => choose('granted')}>Accept</button>
      </div>
    </section>
  );
}
