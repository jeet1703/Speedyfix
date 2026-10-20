import { Link } from 'react-router-dom';

export function Logo({ inverted = false }: { inverted?: boolean }) {
  return (
    <Link to="/" className={'logo' + (inverted ? ' logo--inverted' : '')} aria-label="SpeedyFix Engine Solutions, home">
      <span className="logo__mark">SPEEDY<span>FIX</span></span>
      <span className="logo__sub">Engine Solutions</span>
    </Link>
  );
}
