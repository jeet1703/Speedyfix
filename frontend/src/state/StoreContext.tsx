import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { Basket, Vehicle } from '../api/types';
import { storage } from './storage';

interface Toast {
  id: number;
  message: string;
}

interface StoreState {
  vehicle: Vehicle | null;
  setVehicle: (v: Vehicle | null) => void;
  basket: Basket | null;
  basketOpen: boolean;
  setBasketOpen: (open: boolean) => void;
  addToBasket: (productId: string, name: string, quantity?: number) => Promise<void>;
  setQuantity: (productId: string, quantity: number) => Promise<void>;
  removeFromBasket: (productId: string) => Promise<void>;
  /** Reloads the basket from the server, e.g. after an order has emptied it. */
  refreshBasket: () => Promise<void>;
  wishlist: string[];
  toggleWishlist: (productId: string) => void;
  toast: Toast | null;
  notify: (message: string) => void;
}

const StoreContext = createContext<StoreState | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const [vehicle, setVehicleState] = useState<Vehicle | null>(() => storage.get<Vehicle | null>('vehicle', null));
  const [basket, setBasket] = useState<Basket | null>(null);
  const [basketOpen, setBasketOpen] = useState(false);
  const [wishlist, setWishlist] = useState<string[]>(() => storage.get<string[]>('wishlist', []));
  const [toast, setToast] = useState<Toast | null>(null);
  const toastTimer = useRef<number>();

  const notify = useCallback((message: string) => {
    setToast({ id: Date.now(), message });
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 3500);
  }, []);

  const setVehicle = useCallback((v: Vehicle | null) => {
    setVehicleState(v);
    storage.set('vehicle', v);
  }, []);

  // Restore the basket saved in this browser, if the server still has it.
  useEffect(() => {
    const id = storage.get<string | null>('basketId', null);
    if (!id) return;
    api.getBasket(id).then(setBasket).catch(() => storage.set('basketId', null));
  }, []);

  const ensureBasketId = useCallback(async (): Promise<string> => {
    if (basket) return basket.id;
    const created = await api.createBasket();
    storage.set('basketId', created.id);
    setBasket(created);
    return created.id;
  }, [basket]);

  /** Runs a basket call; if the basket has expired on the server, starts a new one and retries once. */
  const withBasket = useCallback(
    async (call: (id: string) => Promise<Basket>) => {
      try {
        setBasket(await call(await ensureBasketId()));
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) {
          const fresh = await api.createBasket();
          storage.set('basketId', fresh.id);
          setBasket(await call(fresh.id));
          return;
        }
        throw e;
      }
    },
    [ensureBasketId]
  );

  const addToBasket = useCallback(
    async (productId: string, name: string, quantity = 1) => {
      try {
        await withBasket((id) => api.addToBasket(id, productId, quantity));
        notify(`Added to basket: ${name}`);
      } catch (e) {
        notify(e instanceof Error ? e.message : 'Couldn’t add that part. Try again.');
      }
    },
    [withBasket, notify]
  );

  const setQuantity = useCallback(
    async (productId: string, quantity: number) => {
      try {
        await withBasket((id) => api.setQuantity(id, productId, quantity));
      } catch (e) {
        notify(e instanceof Error ? e.message : 'Couldn’t update the quantity.');
      }
    },
    [withBasket, notify]
  );

  const removeFromBasket = useCallback(
    async (productId: string) => {
      try {
        await withBasket((id) => api.removeFromBasket(id, productId));
        notify('Removed from basket');
      } catch (e) {
        notify(e instanceof Error ? e.message : 'Couldn’t remove that part.');
      }
    },
    [withBasket, notify]
  );

  const refreshBasket = useCallback(async () => {
    if (!basket) return;
    try {
      setBasket(await api.getBasket(basket.id));
    } catch {
      storage.set('basketId', null);
      setBasket(null);
    }
  }, [basket]);

  const toggleWishlist = useCallback((productId: string) => {
    setWishlist((list) => {
      const next = list.includes(productId) ? list.filter((p) => p !== productId) : [...list, productId];
      storage.set('wishlist', next);
      return next;
    });
  }, []);

  const value = useMemo<StoreState>(
    () => ({ vehicle, setVehicle, basket, basketOpen, setBasketOpen, addToBasket, setQuantity, removeFromBasket, refreshBasket, wishlist, toggleWishlist, toast, notify }),
    [vehicle, setVehicle, basket, basketOpen, addToBasket, setQuantity, removeFromBasket, refreshBasket, wishlist, toggleWishlist, toast, notify]
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore() {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside StoreProvider');
  return ctx;
}
