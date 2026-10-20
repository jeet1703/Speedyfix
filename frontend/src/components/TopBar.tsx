import { Link } from 'react-router-dom';
import { useStore } from '../state/StoreContext';

export function TopBar({ phone }: { phone: string }) {
  const { wishlist } = useStore();
  return (
    <div className="topbar">
      <div className="container topbar__inner">
        <p className="topbar__welcome">
          Welcome! <Link to="/account">Sign in or create an account</Link>
        </p>
        <nav aria-label="Account and help" className="topbar__links">
          <Link to="/trade">Trade accounts</Link>
          <Link to="/wishlist">Wishlist ({wishlist.length})</Link>
          <Link to="/account">My account</Link>
          <a href={`tel:${phone.replace(/[^+\d]/g, '')}`}>Call {phone}</a>
        </nav>
      </div>
    </div>
  );
}
