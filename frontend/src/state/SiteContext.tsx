import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { SiteData } from '../api/types';

interface SiteState {
  data: SiteData | null;
  error: string;
  retry: () => void;
}

const SiteContext = createContext<SiteState | null>(null);

/** Loads the business details and Products menu once, for every page's header and footer. */
export function SiteProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<SiteData | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    api.site().then(setData).catch((e: Error) => setError(e.message));
  }, []);

  useEffect(load, [load]);

  return <SiteContext.Provider value={{ data, error, retry: load }}>{children}</SiteContext.Provider>;
}

export function useSite() {
  const ctx = useContext(SiteContext);
  if (!ctx) throw new Error('useSite must be used inside SiteProvider');
  return ctx;
}
