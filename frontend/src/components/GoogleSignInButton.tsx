import { useEffect, useRef, useState } from 'react';

interface GoogleIdentity {
  accounts: {
    id: {
      initialize: (options: { client_id: string; callback: (response: { credential: string }) => void; ux_mode?: 'popup' }) => void;
      renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

const SCRIPT = 'https://accounts.google.com/gsi/client';
let loading: Promise<void> | null = null;

/** Loads Google's sign-in script once per page. */
function loadGoogle(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = SCRIPT;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => { loading = null; reject(new Error('Google sign-in didn’t load. Check your connection and try again.')); };
    document.head.appendChild(s);
  });
  return loading;
}

/** Google's own "Sign in with Google" button. onCredential gets the ID token to send to our API. */
export function GoogleSignInButton({ clientId, onCredential }: { clientId: string; onCredential: (credential: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  callback.current = onCredential;
  const [error, setError] = useState('');

  useEffect(() => {
    let live = true;
    loadGoogle().then(() => {
      if (!live || !ref.current || !window.google) return;
      window.google.accounts.id.initialize({ client_id: clientId, ux_mode: 'popup', callback: (r) => callback.current(r.credential) });
      window.google.accounts.id.renderButton(ref.current, {
        type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular', logo_alignment: 'left',
        width: Math.min(360, ref.current.clientWidth || 360)
      });
    }).catch((e: Error) => live && setError(e.message));
    return () => { live = false; };
  }, [clientId]);

  return (
    <div className="google-signin">
      <div ref={ref} className="google-signin__button" />
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}
