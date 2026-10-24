import { Link } from 'react-router-dom';

export function Logo({ inverted = false }: { inverted?: boolean }) {
  return (
    <Link to="/" className={'logo' + (inverted ? ' logo--inverted' : '')} aria-label="SpeedyFix Auto Sales, home">
      <span className="logo__mark">SPEEDY<span>FIX</span></span>
      <span className="logo__sub">Auto Sales</span>
    </Link>
  );
}
