import { storage } from './storage';

/** The Google Ads tag is loaded in index.html; this is the shop's side of it: the cookie choice and conversions. */

export type AdsConsent = 'granted' | 'denied';

const KEY = 'adsConsent'; // index.html reads the same key (as sf_adsConsent) before the tag loads
const REOPEN_EVENT = 'sf-consent-reopen';

function gtag(...args: unknown[]) {
  (window as { gtag?: (...a: unknown[]) => void }).gtag?.(...args);
}

/** The visitor's choice, or null when they haven't been asked yet. */
export const adsConsent = () => storage.get<AdsConsent | null>(KEY, null);

/** Saves the choice and tells Google's tag straight away. */
export function setAdsConsent(choice: AdsConsent) {
  storage.set(KEY, choice);
  gtag('consent', 'update', { ad_storage: choice, ad_user_data: choice, ad_personalization: choice, analytics_storage: choice });
}

/** Shows the cookie notice again so the visitor can change their mind (from the privacy page). */
export const reopenCookieNotice = () => window.dispatchEvent(new Event(REOPEN_EVENT));

export function onReopenCookieNotice(handler: () => void) {
  window.addEventListener(REOPEN_EVENT, handler);
  return () => window.removeEventListener(REOPEN_EVENT, handler);
}

/** Counts a "Contact Us" conversion in Google Ads: a tap on a phone, WhatsApp or email link, or a part request sent. */
export const trackContact = () => gtag('event', 'ads_conversion_Contact_Us_1', {});

/** True for a link that starts a call, a WhatsApp chat or an email. */
export const isContactLink = (href: string) => /^(tel:|mailto:|https:\/\/wa\.me\/)/.test(href);
