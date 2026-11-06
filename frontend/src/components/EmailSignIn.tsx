import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { useAccount } from '../state/AccountContext';

const MIN_PASSWORD = 8;

type Step = 'sign-in' | 'sign-up' | 'code' | 'forgot' | 'reset';

/**
 * Sign in or create an account with an email address and password. A new account is only made once the shopper types
 * in the 6-digit code we email them. Shown only when the server has email sign-in switched on.
 */
export function EmailSignIn() {
  const { signInWithEmail, confirmEmail, resetPassword } = useAccount();
  const [step, setStep] = useState<Step>('sign-in');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<void>) {
    setError('');
    setNote('');
    setBusy(true);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong. Try again.');
    } finally {
      setBusy(false);
    }
  }

  function go(next: Step) {
    setStep(next);
    setError('');
    setNote('');
    setCode('');
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    if (step === 'sign-in') {
      void run(() => signInWithEmail(email.trim(), password));
    } else if (step === 'sign-up') {
      if (password.length < MIN_PASSWORD) {
        setError(`Use at least ${MIN_PASSWORD} characters for your password.`);
        return;
      }
      void run(async () => {
        await api.signUpWithEmail(name.trim(), email.trim(), password);
        setStep('code');
      });
    } else if (step === 'forgot') {
      void run(async () => {
        await api.forgotPassword(email.trim());
        setStep('reset');
      });
    } else if (step === 'reset') {
      if (password.length < MIN_PASSWORD) {
        setError(`Use at least ${MIN_PASSWORD} characters for your new password.`);
        return;
      }
      void run(() => resetPassword(email.trim(), code.trim(), password));
    } else {
      void run(() => confirmEmail(email.trim(), code.trim()));
    }
  }

  const resend = () => run(async () => {
    await api.resendEmailCode(email.trim());
    setNote('We’ve sent a new code. Use the one in the latest email.');
  });

  const title = {
    'sign-in': 'Sign in with email', 'sign-up': 'Create your account', code: 'Check your email',
    forgot: 'Reset your password', reset: 'Choose a new password'
  }[step];

  return (
    <form className="email-auth" onSubmit={submit} noValidate aria-labelledby="email-auth-title" aria-busy={busy}>
      <h3 id="email-auth-title" className="email-auth__title">{title}</h3>
      {error && <p className="notice notice--error" role="alert">{error}</p>}
      {note && <p className="notice" role="status">{note}</p>}

      {step === 'forgot' ? (
        <>
          <p>Enter the email address you signed up with. If it has an account, we’ll email a code to set a new password. You can ask for one code a day.</p>
          <div className="field">
            <label htmlFor="ea-email">Email</label>
            <input id="ea-email" className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              autoComplete="email" inputMode="email" placeholder="Your email address" maxLength={200} required autoFocus />
          </div>
          <button type="submit" className="btn btn--primary" disabled={busy || !email.trim()}>
            {busy ? 'Please Wait…' : 'Email Me a Code'}
          </button>
          <p className="page-body__note">Remembered it? <button type="button" className="link-btn" onClick={() => go('sign-in')}>Sign In</button></p>
        </>
      ) : step === 'reset' ? (
        <>
          <p>If <strong>{email.trim()}</strong> has an account, we’ve emailed it a 6-digit code. It works for 15 minutes.</p>
          <div className="field">
            <label htmlFor="ea-code">Code</label>
            <input id="ea-code" className="input email-auth__code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric" autoComplete="one-time-code" placeholder="123456" required autoFocus />
          </div>
          <div className="field">
            <label htmlFor="ea-password">New password</label>
            <input id="ea-password" className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password" placeholder="Choose a new password" maxLength={200} required aria-describedby="ea-password-hint" />
            <p id="ea-password-hint" className="field__hint">At least {MIN_PASSWORD} characters.</p>
          </div>
          <button type="submit" className="btn btn--primary" disabled={busy || code.length !== 6}>
            {busy ? 'Saving…' : 'Set New Password and Sign In'}
          </button>
          <p className="page-body__note">
            No email? Check your spam folder. You can ask for one code a day; if you’re stuck, call us.
            {' '}<button type="button" className="link-btn" onClick={() => go('sign-in')}>Back to Sign In</button>
          </p>
        </>
      ) : step === 'code' ? (
        <>
          <p>We’ve emailed a 6-digit code to <strong>{email.trim()}</strong>. It works for 15 minutes.</p>
          <div className="field">
            <label htmlFor="ea-code">Code</label>
            <input id="ea-code" className="input email-auth__code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric" autoComplete="one-time-code" placeholder="123456" required autoFocus />
          </div>
          <button type="submit" className="btn btn--primary" disabled={busy || code.length !== 6}>
            {busy ? 'Checking…' : 'Confirm and Create Account'}
          </button>
          <p className="page-body__note">
            No email? Check your spam folder, or <button type="button" className="link-btn" onClick={() => void resend()} disabled={busy}>send a new code</button>.
            {' '}Wrong address? <button type="button" className="link-btn" onClick={() => go('sign-up')}>Change It</button>.
          </p>
        </>
      ) : (
        <>
          {step === 'sign-up' && (
            <div className="field">
              <label htmlFor="ea-name">Name</label>
              <input id="ea-name" className="input" value={name} onChange={(e) => setName(e.target.value)}
                autoComplete="name" placeholder="Your name" maxLength={100} required />
            </div>
          )}
          <div className="field">
            <label htmlFor="ea-email">Email</label>
            <input id="ea-email" className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              autoComplete="email" inputMode="email" placeholder="Your email address" maxLength={200} required />
          </div>
          <div className="field">
            <label htmlFor="ea-password">Password</label>
            <input id="ea-password" className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete={step === 'sign-up' ? 'new-password' : 'current-password'}
              placeholder={step === 'sign-up' ? 'Create a password' : 'Your password'} maxLength={200} required
              aria-describedby={step === 'sign-up' ? 'ea-password-hint' : undefined} />
            {step === 'sign-up' && <p id="ea-password-hint" className="field__hint">At least {MIN_PASSWORD} characters.</p>}
          </div>
          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? 'Please Wait…' : step === 'sign-up' ? 'Create Account' : 'Sign In'}
          </button>
          <p className="page-body__note">
            {step === 'sign-in'
              ? <>New here? <button type="button" className="link-btn" onClick={() => go('sign-up')}>Create an Account</button>
                {' · '}<button type="button" className="link-btn" onClick={() => { setPassword(''); go('forgot'); }}>Forgot Password?</button></>
              : <>Already have an account? <button type="button" className="link-btn" onClick={() => go('sign-in')}>Sign In</button></>}
          </p>
        </>
      )}
    </form>
  );
}
