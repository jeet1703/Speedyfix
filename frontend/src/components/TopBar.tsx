import { Link } from 'react-router-dom';
import { telHref } from './contactLinks';
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
            ? <>Welcome back, {customer.name.split(' ')[0]}! <Link to="/account">My Account</Link></>
            : <>Welcome! <Link to="/account">Sign In or Create an Account</Link></>}
        </p>
        <nav aria-label="Account and help" className="topbar__links">
          <Link to="/wishlist">Wishlist ({wishlist.length})</Link>
          <Link to="/account">My Account</Link>
          <a href={telHref(phone)}>Call {phone}</a>
        </nav>
      </div>
    </div>
  );
}
