import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../adminApi';
import { useAdminAuth } from '../AdminAuth';

export function LoginPage() {
  const { staff, signIn } = useAdminAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    document.title = 'Sign in | SpeedyFix Auto Sales admin';
  }, []);

  // Only follow admin paths, so a crafted link can't bounce staff somewhere else after sign-in.
  const next = from && from.startsWith('/admin') && !from.startsWith('/admin/login') ? from : '/admin';
  if (staff) return <Navigate to={next} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim()) {
      setError('Enter your email address.');
      return;
    }
    if (!password) {
      setError('Enter your password.');
      return;
    }
    setError('');
    setBusy(true);
    try {
      await signIn(email.trim(), password);
      navigate(next, { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'We couldn’t sign you in. Try again.'));
      setBusy(false);
    }
  }

  return (
    <main className="admin-login">
      <form className="admin-login__card" onSubmit={submit} noValidate>
        <p className="admin-logo admin-logo--dark">
          <span className="admin-logo__mark">SPEEDY<span>FIX</span></span>
          <span className="admin-logo__sub">Admin</span>
        </p>
        <h1 className="admin-login__title">Staff sign-in</h1>
        {error && <p className="notice notice--error" role="alert">{error}</p>}
        <div className="field">
          <label htmlFor="admin-email">Email</label>
          <input id="admin-email" className="input" type="email" inputMode="email" autoComplete="username" value={email}
            onChange={(e) => setEmail(e.target.value)} maxLength={200} aria-invalid={error ? true : undefined} autoFocus />
        </div>
        <div className="field">
          <label htmlFor="admin-password">Password</label>
          <input id="admin-password" className="input" type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} maxLength={200} aria-invalid={error ? true : undefined} />
        </div>
        <button type="submit" className="btn btn--primary btn--block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="field__hint">Sessions last 12 hours, or until you close this tab.</p>
      </form>
    </main>
  );
}
