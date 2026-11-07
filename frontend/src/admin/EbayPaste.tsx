import { useState } from 'react';
import { adminApi, errorMessage } from './adminApi';
import type { Fitment } from './types';

/**
 * Adds compatibility copied from the part's eBay listing: staff select the Compatibility table there (eBay shows 20
 * vehicles a page) and paste it here, a page at a time or several at once. The server turns the rows into vehicles
 * (CompatibilityTable).
 */
export function EbayPaste({ ebayUrl, onAdd }: { ebayUrl: string | null; onAdd: (rows: Fitment[]) => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');

  async function add() {
    setBusy(true);
    setError('');
    setNote('');
    try {
      const rows = await adminApi.pastedVehicles(text);
      onAdd(rows);
      setNote(`Added ${rows.length} ${rows.length === 1 ? 'vehicle' : 'vehicles'} (years in a row are joined into one). Paste the next page if there is one.`);
      setText('');
    } catch (e) {
      setError(errorMessage(e, 'We couldn’t read that table. Try copying it again.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <details className="ebay-paste">
      <summary>Paste from eBay</summary>
      <ol className="ebay-paste__steps">
        <li>{ebayUrl ? <a href={ebayUrl} target="_blank" rel="noreferrer">Open the eBay listing</a> : 'Open the eBay listing'} and click <strong>See compatible vehicles</strong>.</li>
        <li>Select the table from <strong>Make</strong> down to the last row, and copy it (Ctrl+C).</li>
        <li>Paste it below and add it. For more pages, go to the next page on eBay and repeat.</li>
      </ol>
      <label htmlFor="ebay-paste" className="sr-only">Compatibility table copied from eBay</label>
      <textarea id="ebay-paste" className="input mono" rows={6} value={text} onChange={(e) => setText(e.target.value)}
        placeholder={'Make\tModel\tYear\tVariant\tType\tChassis\tEngine\tNotes\nToyota\tLandCruiser\t2024\tDiesel Chassis Cab\t4.2 D 4x4\t…'}
        spellCheck={false} />
      <div aria-live="polite">
        {error && <p className="field-error">{error}</p>}
        {note && <p className="admin-muted">{note}</p>}
      </div>
      <div>
        <button type="button" className="btn btn--outline btn--sm" disabled={busy || !text.trim()} onClick={() => void add()}>
          {busy ? 'Adding…' : 'Add Pasted Vehicles'}
        </button>
      </div>
    </details>
  );
}
