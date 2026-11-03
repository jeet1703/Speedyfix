import { useState } from 'react';
import { Link } from 'react-router-dom';
import { siteImage, siteSrcSet } from '../api/images';
import type { MenuGroup } from '../api/types';

type MenuLink = MenuGroup['links'][number];

/**
 * The Products menu. Pointing at (or tabbing to) a category shows its photo in the preview on the right, taken from
 * one of the shop's own listings; links without a photo keep the last one shown.
 */
export function MegaMenu({ groups, id, onNavigate }: { groups: MenuGroup[]; id: string; onNavigate: () => void }) {
  const first = groups.flatMap((g) => g.links).find((l) => l.imageUrl) ?? null;
  const [shown, setShown] = useState<MenuLink | null>(first);
  const show = (l: MenuLink) => { if (l.imageUrl) setShown(l); };

  return (
    <div className="megamenu" id={id} role="region" aria-label="Product categories">
      <div className="megamenu__groups">
        {groups.map((g) => (
          <div key={g.title} className="megamenu__group">
            <h2>{g.title}</h2>
            <ul>
              {g.links.map((l) => (
                <li key={l.href}>
                  <Link to={l.href} onClick={onNavigate} onMouseEnter={() => show(l)} onFocus={() => show(l)}
                    aria-current={shown?.href === l.href ? 'true' : undefined}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="megamenu__side">
        {shown?.imageUrl && (
          <Link className="megamenu__preview" to={shown.href} onClick={onNavigate} tabIndex={-1} aria-hidden="true">
            <img key={shown.imageUrl} src={siteImage(shown.imageUrl)} srcSet={siteSrcSet(shown.imageUrl)} sizes="260px" alt="" width={260} height={180} />
            <span>{shown.label}</span>
          </Link>
        )}
        <Link className="btn btn--dark btn--block" to="/products" onClick={onNavigate}>Shop all parts</Link>
        <Link className="megamenu__promo" to="/request-a-part" onClick={onNavigate}>
          <span>Can’t find the part?</span>
          <strong>Request a Part</strong>
        </Link>
      </div>
    </div>
  );
}
