import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { Customer } from '../api/types';
import { storage } from './storage';

const MAX_SEARCHES = 10;

interface AccountState {
  /** undefined while a saved session is being checked; null when signed out. */
  customer: Customer | null | undefined;
  signInWithGoogle: (credential: string) => Promise<void>;
  signOut: () => Promise<void>;
  /** Recent searches, newest first: the account's when signed in, this browser's otherwise. */
  searches: string[];
  recordSearch: (query: string) => void;
  clearSearches: () => void;
}

const AccountContext = createContext<AccountState | null>(null);

/** Adds q to the front of the list, dropping an earlier copy of it (any case) and anything past the last 10. */
function withSearch(list: string[], q: string) {
  return [q, ...list.filter((s) => s.toLowerCase() !== q.toLowerCase())].slice(0, MAX_SEARCHES);
}

export function AccountProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => storage.get<string | null>('account_token', null));
  const [customer, setCustomer] = useState<Customer | null | undefined>(() => (token ? undefined : null));
  const [searches, setSearches] = useState<string[]>(() => (token ? [] : storage.get<string[]>('searches', [])));

  const forget = useCallback(() => {
    storage.set('account_token', null);
    setToken(null);
    setCustomer(null);
    setSearches(storage.get<string[]>('searches', []));
  }, []);

  // Check a saved session once, and load that account's searches.
  useEffect(() => {
    if (!token) return;
    let live = true;
    Promise.all([api.me(token), api.searches(token)])
      .then(([me, list]) => { if (live) { setCustomer(me); setSearches(list); } })
      .catch((err) => { if (live && err instanceof ApiError && (err.status === 401 || err.status === 404)) forget(); else if (live) setCustomer(null); });
    return () => { live = false; };
    // Only on first load: sign-in sets the customer itself.
  }, []);

  const signInWithGoogle = useCallback(async (credential: string) => {
    const session = await api.signInWithGoogle(credential);
    storage.set('account_token', session.token);
    setToken(session.token);
    setCustomer(session.customer);
    // Carry this browser's searches into the account, oldest first so the newest stays on top.
    let list = await api.searches(session.token).catch(() => [] as string[]);
    for (const q of [...storage.get<string[]>('searches', [])].reverse()) {
      list = await api.recordSearch(session.token, q).catch(() => list);
    }
    storage.set('searches', null);
    setSearches(list);
  }, []);

  const signOut = useCallback(async () => {
    if (token) await api.signOut(token).catch(() => undefined);
    forget();
  }, [token, forget]);

  const recordSearch = useCallback((query: string) => {
    const q = query.trim().replace(/\s+/g, ' ');
    if (q.length < 2 || q.length > 100) return;
    setSearches((prev) => withSearch(prev, q));
    if (token) {
      api.recordSearch(token, q).then(setSearches).catch(() => undefined);
    } else {
      storage.set('searches', withSearch(storage.get<string[]>('searches', []), q));
    }
  }, [token]);

  const clearSearches = useCallback(() => {
    setSearches([]);
    if (token) api.clearSearches(token).catch(() => undefined);
    else storage.set('searches', null);
  }, [token]);

  const value = useMemo(() => ({ customer, signInWithGoogle, signOut, searches, recordSearch, clearSearches }),
    [customer, signInWithGoogle, signOut, searches, recordSearch, clearSearches]);
  return <AccountContext.Provider value={value}>{children}</AccountContext.Provider>;
}

export function useAccount() {
  const ctx = useContext(AccountContext);
  if (!ctx) throw new Error('useAccount must be used inside AccountProvider');
  return ctx;
}
