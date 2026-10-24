import type { Confidence, PartRequestStatus, ProductRow, WorkStatus } from './types';

const WORK_LABEL: Record<WorkStatus, string> = {
  NEEDS_FITMENT: 'Needs fitment',
  MISSING_PHOTO: 'Missing photo',
  DRAFT: 'Draft',
  LIVE: 'Live'
};

export function WorkPill({ status }: { status: WorkStatus }) {
  return <span className={`pill pill--${status.toLowerCase().replace('_', '-')}`}>{WORK_LABEL[status]}</span>;
}

export function ConfidencePill({ confidence }: { confidence: Confidence }) {
  return <span className={`pill pill--${confidence === 'HIGH' ? 'live' : 'missing-photo'}`}>{confidence === 'HIGH' ? 'High' : 'Medium'}</span>;
}

export const REQUEST_LABEL: Record<PartRequestStatus, string> = { NEW: 'New', QUOTED: 'Quoted', CLOSED: 'Closed' };

export function RequestPill({ status }: { status: PartRequestStatus }) {
  const tone = status === 'NEW' ? 'needs-fitment' : status === 'QUOTED' ? 'live' : 'draft';
  return <span className={`pill pill--${tone}`}>{REQUEST_LABEL[status]}</span>;
}

/** Work-queue filters, shared by the dashboard and the products page. */
export type QueueFilter = 'all' | 'needs-fitment' | 'missing-photo' | 'draft' | 'live';

export const QUEUE_FILTERS: { value: QueueFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'needs-fitment', label: 'Needs fitment' },
  { value: 'missing-photo', label: 'Missing photo' },
  { value: 'draft', label: 'Draft' },
  { value: 'live', label: 'Live' }
];

/** A part can be in several queues at once: no vehicles and no photo, say. Its pill shows the most urgent. */
export function inQueue(row: ProductRow, filter: QueueFilter) {
  switch (filter) {
    case 'needs-fitment': return row.vehicles === 0;
    case 'missing-photo': return !row.hasPhoto;
    case 'draft': return row.status === 'DRAFT';
    case 'live': return row.status === 'LIVE';
    default: return true;
  }
}

export function Chips<T extends string>({ label, options, value, onChange }: {
  label: string;
  options: { value: T; label: string; count?: number }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="chips" role="group" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" className="chip-btn" aria-pressed={value === o.value} onClick={() => onChange(o.value)}>
          {o.label}{o.count !== undefined && <span className="chip-btn__count"> {o.count}</span>}
        </button>
      ))}
    </div>
  );
}

const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });
export const gbpOrDash = (value: number | null) => (value === null ? '—' : gbp.format(value));

const dateTime = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/London' });
export const when = (iso: string) => dateTime.format(new Date(iso));
