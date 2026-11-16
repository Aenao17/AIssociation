import { IonContent, IonPage } from '@ionic/react';
import { useState } from 'react';
import type { ReactElement, ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import '../pages/Identity.css';

import BackLink from './BackLink';
import BrandHeader from './BrandHeader';
import './Dashboard.css';

export default function DashboardShell({
  children,
  account = false,
}: {
  children: ReactNode;
  account?: boolean;
}): ReactElement {
  const { user, logout } = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  if (!user) return <Navigate to="/login" replace />;
  async function signOut(): Promise<void> {
    setPending(true);
    setError('');
    try {
      await logout();
    } catch {
      setError('Unable to sign out. Please try again.');
    } finally {
      setPending(false);
    }
  }
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell dashboard-shell">
          <BrandHeader
            wide
            actions={
              <div className="dashboard-header-actions">
                <BackLink to={account ? '/home' : '/account'}>{account ? 'Home' : 'My account'}</BackLink>
                <button
                  className="identity-secondary dashboard-signout"
                  type="button"
                  disabled={pending}
                  onClick={() => {
                    void signOut();
                  }}
                >
                  {pending ? 'Signing out…' : 'Sign out'}
                </button>
              </div>
            }
          />
          {error && (
            <p className="identity-error" role="alert">
              {error}
            </p>
          )}
          <div className="dashboard-content">{children}</div>
        </main>
      </IonContent>
    </IonPage>
  );
}
