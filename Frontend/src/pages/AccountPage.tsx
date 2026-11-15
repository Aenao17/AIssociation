import { IonContent, IonPage } from '@ionic/react';
import { useState } from 'react';
import type { ReactElement } from 'react';
import { Link, Navigate } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import './Identity.css';

export default function AccountPage(): ReactElement {
  const { user, logout, refreshUser } = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  if (!user) return <Navigate to="/login" replace />;

  async function act(operation: () => Promise<void>) {
    setPending(true);
    setError('');
    try {
      await operation();
    } catch {
      setError('Unable to complete the request. Please try again.');
    } finally {
      setPending(false);
    }
  }

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <Link className="identity-brand" to="/home">
            AIssociation<span>.</span>
          </Link>
          <section className="identity-card identity-account" aria-labelledby="account-heading">
            <p className="identity-eyebrow">Your account</p>
            <h1 id="account-heading">Hello, {user.displayName}</h1>
            <p className="identity-intro">{user.email}</p>
            {user.platformAdmin && (
              <p>
                <Link className="identity-admin-link" to="/admin/associations">
                  Platform administration →
                </Link>
              </p>
            )}
            <h2>Association memberships</h2>
            {user.memberships.length === 0 ? (
              <p className="identity-empty">
                You aren’t a member of an association yet. An association administrator can add your account.
              </p>
            ) : (
              <ul className="identity-memberships">
                {user.memberships.map((membership) => (
                  <li key={membership.associationId}>
                    <strong>{membership.associationName}</strong>
                    <span>{membership.role === 'ADMIN' ? 'Administrator' : 'Member'}</span>
                  </li>
                ))}
              </ul>
            )}
            <p className="identity-help">Event participation is separate from association membership.</p>
            {error && (
              <p className="identity-error" role="alert">
                {error}
              </p>
            )}
            <div className="identity-actions">
              <button
                className="identity-secondary"
                disabled={pending}
                onClick={() => {
                  void act(refreshUser);
                }}
              >
                Refresh memberships
              </button>
              <button
                className="identity-primary"
                disabled={pending}
                onClick={() => {
                  void act(logout);
                }}
              >
                Sign out
              </button>
            </div>
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
}
