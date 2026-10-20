import type { Feature } from '../api/types';
import { Icon } from './Icons';

export function Features({ items }: { items: Feature[] }) {
  return (
    <section className="features" aria-label="Why buy from SpeedyFix">
      <ul className="container features__list">
        {items.map((f) => (
          <li key={f.title} className="feature">
            <span className="feature__icon"><Icon name={f.icon} size={26} /></span>
            <span>
              <strong>{f.title}</strong>
              <span>{f.detail}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
