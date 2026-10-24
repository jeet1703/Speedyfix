import { Link } from 'react-router-dom';
import { useAccount } from '../state/AccountContext';
import { useStore } from '../state/StoreContext';

export function TopBar({ phone }: { phone: string }) {
  const { wishlist } = useStore();
  const { customer } = useAccount();
  return (
    <div className="topbar">
      <div className="container topbar__inner">
        <p className="topbar__welcome">
          {customer
            ? <>Welcome back, {customer.name.split(' ')[0]}! <Link to="/account">My account</Link></>
            : <>Welcome! <Link to="/account">Sign in or create an account</Link></>}
        </p>
        <nav aria-label="Account and help" className="topbar__links">
          <Link to="/wishlist">Wishlist ({wishlist.length})</Link>
          <Link to="/account">My account</Link>
          <a href={`tel:${phone.replace(/[^+\d]/g, '')}`}>Call {phone}</a>
        </nav>
      </div>
    </div>
  );
}
