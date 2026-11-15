import { IonContent, IonPage } from '@ionic/react';
import { useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';
import './Identity.css';

export default function AuthPage({ mode }: { mode: 'login' | 'register' }): ReactElement {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const registering = mode === 'register';
  const notice = (location.state as { notice?: string } | null)?.notice;

  if (auth.user) return <Navigate to="/account" replace />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError('');
    try {
      if (registering) {
        await auth.register({ displayName: name, email, password });
        setPassword('');
        navigate('/login', { replace: true, state: { notice: 'Account created. Sign in to continue.' } });
      } else {
        await auth.login(email, password);
        setPassword('');
        navigate('/account', { replace: true });
      }
    } catch (failure) {
      const details = failure instanceof ApiError ? Object.values(failure.fieldErrors).join(' ') : '';
      setError(
        failure instanceof ApiError ? `${failure.message} ${details}`.trim() : 'Unable to connect. Please try again.',
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <BrandHeader />
          <section className="identity-card" aria-labelledby={`${mode}-heading`}>
            <p className="identity-eyebrow">Student associations, connected</p>
            <h1 id={`${mode}-heading`}>{registering ? 'Create your account' : 'Welcome back'}</h1>
            <p className="identity-intro">
              {registering
                ? 'One account to participate in events across associations. Membership is managed separately.'
                : 'Sign in to see your account and association memberships.'}
            </p>
            {!registering && notice && (
              <p className="identity-notice" role="status">
                {notice}
              </p>
            )}
            <form
              onSubmit={(event) => {
                void submit(event);
              }}
            >
              {registering && (
                <label>
                  Display name
                  <input
                    name="displayName"
                    autoComplete="name"
                    required
                    maxLength={100}
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                  />
                </label>
              )}
              <label>
                Email
                <input
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  maxLength={254}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <label>
                Password
                <input
                  name="password"
                  type="password"
                  autoComplete={registering ? 'new-password' : 'current-password'}
                  required
                  minLength={registering ? 10 : undefined}
                  maxLength={72}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-describedby={registering ? 'password-help' : undefined}
                />
              </label>
              {registering && (
                <p className="identity-help" id="password-help">
                  At least 10 characters; maximum 72 UTF-8 bytes.
                </p>
              )}
              {error && (
                <p className="identity-error" role="alert">
                  {error}
                </p>
              )}
              <button className="identity-primary" disabled={pending} type="submit">
                {pending ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
              </button>
            </form>
            <p className="identity-switch">
              {registering ? 'Already have an account?' : 'New to AIssociation?'}{' '}
              <Link to={registering ? '/login' : '/register'}>{registering ? 'Sign in' : 'Create an account'}</Link>
            </p>
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
}
