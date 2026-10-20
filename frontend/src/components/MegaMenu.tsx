import { Link } from 'react-router-dom';
import type { MenuGroup } from '../api/types';

export function MegaMenu({ groups, id, onNavigate }: { groups: MenuGroup[]; id: string; onNavigate: () => void }) {
  return (
    <div className="megamenu" id={id} role="region" aria-label="Product categories">
      <div className="megamenu__groups">
        {groups.map((g) => (
          <div key={g.title} className="megamenu__group">
            <h2>{g.title}</h2>
            <ul>
              {g.links.map((l) => (
                <li key={l.href}>
                  <Link to={l.href} onClick={onNavigate}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="megamenu__side">
        <Link className="btn btn--dark btn--block" to="/products" onClick={onNavigate}>Shop all parts</Link>
        <Link className="megamenu__promo" to="/request-a-part" onClick={onNavigate}>
          <span>Can’t find the part?</span>
          <strong>Request a part</strong>
        </Link>
      </div>
    </div>
  );
}
