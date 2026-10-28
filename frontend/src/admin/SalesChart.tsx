import { useEffect, useRef, useState } from 'react';

interface Day {
  date: string;
  orders: number;
  total: number;
}

const H = 170;
const PAD = { top: 12, right: 8, bottom: 26, left: 48 };
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const GAP = 2;
const RADIUS = 4;

const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });
const gbpShort = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', notation: 'compact', maximumFractionDigits: 1 });
const dayLong = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });

/** The smallest of 1, 2 or 5 × a power of ten at or above max, so the top gridline is a round figure. */
function niceMax(max: number) {
  if (max <= 0) return 100;
  const step = 10 ** Math.floor(Math.log10(max));
  return [1, 2, 5, 10].map((m) => m * step).find((v) => v >= max)!;
}

/** A bar with 4px rounded corners on top and a square foot on the baseline. */
function bar(x: number, y: number, w: number, h: number) {
  const r = Math.min(RADIUS, w / 2, h);
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}

/** Daily takings for the last 31 days: one bar per day, a tooltip on hover, and the same figures as a table. */
export function SalesChart({ days }: { days: Day[] }) {
  const [hover, setHover] = useState<number | null>(null);
  // Drawn at the card's real width rather than scaled, so axis text stays a readable size on any screen.
  const plotRef = useRef<HTMLDivElement>(null);
  const [W, setW] = useState(360);
  useEffect(() => {
    const el = plotRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setW(Math.max(240, Math.floor(entry.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  const top = niceMax(Math.max(...days.map((d) => d.total), 0));
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const slot = plotW / days.length;
  const barW = Math.max(2, slot - GAP);
  const y = (v: number) => PAD.top + plotH - (v / top) * plotH;
  const date = (iso: string) => new Date(`${iso}T00:00:00Z`);
  const ticks = [0, top / 2, top];
  const h = hover === null ? null : days[hover];

  return (
    <figure className="sales-chart">
      <figcaption className="sales-chart__title">Sales by day, last 31 days</figcaption>
      <div ref={plotRef} className="sales-chart__plot" onMouseLeave={() => setHover(null)}>
        <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Daily sales for the last 31 days, up to ${gbp.format(top)} a day. The table below has the figures.`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className="sales-chart__grid" />
              <text x={PAD.left - 8} y={y(t)} className="sales-chart__axis" textAnchor="end" dominantBaseline="middle">{gbpShort.format(t)}</text>
            </g>
          ))}
          {days.map((d, i) => {
            const x = PAD.left + i * slot + GAP / 2;
            const bh = (d.total / top) * plotH;
            return (
              <g key={d.date}>
                {bh > 0 && <path d={bar(x, y(d.total), barW, bh)} className={'sales-chart__bar' + (hover === i ? ' is-hover' : '')} />}
                {/* A full-height hit area per day, much bigger than the bar, so thin and empty days are easy to hover. */}
                <rect x={PAD.left + i * slot} y={PAD.top} width={slot} height={plotH} fill="transparent" onMouseEnter={() => setHover(i)} />
                {/* Every 7th day, counted back from today so the newest day is always labelled. */}
                {(days.length - 1 - i) % 7 === 0 && (
                  <text x={x + barW / 2} y={H - 8} className="sales-chart__axis" textAnchor="middle">
                    {date(d.date).getUTCDate()} {MONTHS[date(d.date).getUTCMonth()]}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
        {h && hover !== null && (
          <div className="sales-chart__tip" role="status" style={{
            left: PAD.left + (hover + 0.5) * slot,
            // Centred over the day, but kept inside the chart at either end.
            transform: `translateX(${hover < days.length * 0.2 ? '0' : hover > days.length * 0.8 ? '-100%' : '-50%'})`
          }}>
            <strong>{dayLong.format(date(h.date))}</strong>
            <span>{gbp.format(h.total)}</span>
            <span>{h.orders} {h.orders === 1 ? 'order' : 'orders'}</span>
          </div>
        )}
      </div>
      <table className="sr-only">
        <caption>Sales by day, last 31 days</caption>
        <thead><tr><th scope="col">Day</th><th scope="col">Sales</th><th scope="col">Orders</th></tr></thead>
        <tbody>
          {days.map((d) => <tr key={d.date}><td>{dayLong.format(date(d.date))}</td><td>{gbp.format(d.total)}</td><td>{d.orders}</td></tr>)}
        </tbody>
      </table>
    </figure>
  );
}
