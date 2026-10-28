import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { SiteData } from '../api/types';

interface SiteState {
  data: SiteData | null;
  error: string;
  /** True while the first load keeps failing and is being retried (a sleeping server takes up to a minute to wake). */
  waking: boolean;
  retry: () => void;
}

const SiteContext = createContext<SiteState | null>(null);

const RETRY_EVERY_MS = 3000;
const GIVE_UP_AFTER_MS = 90_000;

/** Unreachable, or the server (or the proxy in front of it) failed: worth trying again shortly. */
const retryable = (e: unknown) => e instanceof ApiError && (e.status === 0 || e.status >= 500);

/** Loads the business details and Products menu once, for every page's header and footer. */
export function SiteProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<SiteData | null>(null);
  const [error, setError] = useState('');
  const [waking, setWaking] = useState(false);
  const timer = useRef<number>();

  const load = useCallback(() => {
    const started = Date.now();
    window.clearTimeout(timer.current);
    setError('');
    const attempt = () => {
      api.site()
        .then((d) => { setData(d); setWaking(false); })
        .catch((e: Error) => {
          if (retryable(e) && Date.now() - started < GIVE_UP_AFTER_MS) {
            setWaking(true);
            timer.current = window.setTimeout(attempt, RETRY_EVERY_MS);
          } else {
            setWaking(false);
            setError(e.message);
          }
        });
    };
    attempt();
  }, []);

  useEffect(() => {
    load();
    return () => window.clearTimeout(timer.current);
  }, [load]);

  return <SiteContext.Provider value={{ data, error, waking, retry: load }}>{children}</SiteContext.Provider>;
}

export function useSite() {
  const ctx = useContext(SiteContext);
  if (!ctx) throw new Error('useSite must be used inside SiteProvider');
  return ctx;
}
