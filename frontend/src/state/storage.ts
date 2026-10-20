/** localStorage that never throws (private browsing, storage disabled). */
export const storage = {
  get<T>(key: string, fallback: T): T {
    try {
      const raw = localStorage.getItem('sf_' + key);
      return raw ? (JSON.parse(raw) as T) : fallback;
    } catch {
      return fallback;
    }
  },
  set(key: string, value: unknown) {
    try {
      if (value === null || value === undefined) localStorage.removeItem('sf_' + key);
      else localStorage.setItem('sf_' + key, JSON.stringify(value));
    } catch {
      /* storage unavailable */
    }
  }
};
