import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { adminApi, errorMessage } from './adminApi';
import type { EbaySyncStatus } from './types';


const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', day: 'numeric', month: 'short' });

/**
 * "Sync with eBay" for the Listings page: starts the sync on the server, then checks every few seconds until it's
 * done and says what changed. Greyed out, with the reason, where the eBay keys aren't set.
 */
export function EbaySyncButton({ onDone }: { onDone: () => void }) {
  const [status, setStatus] = useState<EbaySyncStatus | null>(null);
  const [error, setError] = useState('');
  const timer = useRef<number | undefined>(undefined);
  const done = useRef(onDone);
  done.current = onDone;

  const poll = useCallback(async (wasRunning: boolean) => {
    try {
      const s = await adminApi.ebaySync();
      setStatus(s);
      if (s.running) {
        timer.current = window.setTimeout(() => void poll(true), 3000);
      } else if (wasRunning) {
        done.current();
      }
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) setError(errorMessage(e, 'Couldn’t check the eBay sync.'));
    }
  }, []);

  useEffect(() => {
    void poll(false);
    return () => window.clearTimeout(timer.current);
  }, [poll]);

  async function start() {
    setError('');
    try {
      setStatus(await adminApi.startEbaySync());
      timer.current = window.setTimeout(() => void poll(true), 3000);
    } catch (e) {
      setError(errorMessage(e, 'The eBay sync didn’t start. Try again.'));
    }
  }

  if (!status) return null;
  if (!status.available) {
    return (
      <div className="ebay-sync">
        <button type="button" className="btn btn--outline" disabled aria-describedby="ebay-sync-note">Sync with eBay</button>
        <p id="ebay-sync-note" className="ebay-sync__note">Not set up on this server: add EBAY_CLIENT_ID and EBAY_CLIENT_SECRET in Render.</p>
      </div>
    );
  }
  const last = status.last;
  return (
    <div className="ebay-sync">
      <button type="button" className="btn btn--outline" onClick={() => void start()} disabled={status.running} aria-describedby="ebay-sync-note">
        {status.running ? 'Syncing with eBay…' : 'Sync with eBay'}
      </button>
      <p id="ebay-sync-note" className="ebay-sync__note" role="status">
        {status.running
          ? 'Checking every eBay listing. New ones take a little longer (photos).'
          : status.lastError
            ? `Last sync failed: ${status.lastError}`
            : last
              ? `Last sync ${time.format(new Date(last.at))}: ${last.added} added, ${last.repriced} repriced, ${last.ended} ended` +
                (last.endingSkipped ? ' (ending skipped: too many missing at once, check eBay)' : '') +
                (last.problems.length ? `, ${last.problems.length} to check` : '') + '.'
              : 'Syncs by itself every 30 minutes.'}
      </p>
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}
