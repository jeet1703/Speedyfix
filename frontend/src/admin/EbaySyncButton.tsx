import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { adminApi, errorMessage } from './adminApi';
import type { CompatibilityImport, EbaySyncStatus } from './types';


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

/**
 * "Import compatibility from eBay": copies each listing's compatibility table (the vehicles ticked on eBay) onto its
 * part. Safe to run again: it only adds rows a part doesn't have. Greyed out, with the reason, until it's set up.
 */
export function CompatibilityImportButton({ onDone }: { onDone: () => void }) {
  const [state, setState] = useState<CompatibilityImport | null>(null);
  const [error, setError] = useState('');
  const timer = useRef<number | undefined>(undefined);
  const done = useRef(onDone);
  done.current = onDone;

  const poll = useCallback(async (wasRunning: boolean) => {
    try {
      const s = await adminApi.compatibilityImport();
      setState(s);
      if (s.status.running) {
        timer.current = window.setTimeout(() => void poll(true), 3000);
      } else if (wasRunning) {
        done.current();
      }
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) setError(errorMessage(e, 'Couldn’t check the compatibility import.'));
    }
  }, []);

  useEffect(() => {
    void poll(false);
    return () => window.clearTimeout(timer.current);
  }, [poll]);

  async function start() {
    setError('');
    try {
      setState(await adminApi.startCompatibilityImport());
      timer.current = window.setTimeout(() => void poll(true), 3000);
    } catch (e) {
      setError(errorMessage(e, 'The import didn’t start. Try again.'));
    }
  }

  if (!state) return null;
  if (!state.available) {
    return (
      <div className="ebay-sync">
        <button type="button" className="btn btn--outline" disabled aria-describedby="compat-note">Import compatibility from eBay</button>
        <p id="compat-note" className="ebay-sync__note">Not set up on this server: add EBAY_DEV_ID and EBAY_USER_TOKEN (the seller’s eBay sign-in) in Render.</p>
      </div>
    );
  }
  const s = state.status;
  return (
    <div className="ebay-sync">
      <button type="button" className="btn btn--outline" onClick={() => void start()} disabled={s.running} aria-describedby="compat-note">
        {s.running ? 'Importing compatibility…' : 'Import compatibility from eBay'}
      </button>
      <p id="compat-note" className="ebay-sync__note" role="status">
        {s.running
          ? `Checked ${s.checked} of ${s.total} listings: ${s.updated} parts got ${s.rows} vehicle rows so far.`
          : s.error
            ? `Stopped: ${s.error}`
            : s.finishedAt
              ? `Last import ${time.format(new Date(s.finishedAt))}: ${s.updated} parts got ${s.rows} vehicle rows; ${s.noTable} listings have no table on eBay` +
                (s.problems.length ? `, ${s.problems.length} to check` : '') + '.'
              : 'Copies each listing’s compatibility table from eBay onto its part. Safe to run again.'}
      </p>
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}
