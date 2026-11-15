import { IonContent, IonPage } from '@ionic/react';
import { Link } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';
import './Identity.css';

const Home: React.FC = () => {
  const { user } = useAuth();
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <BrandHeader wide />
          <section className="identity-card identity-landing">
            <p className="identity-eyebrow">For student communities</p>
            <h1>
              Your associations.
              <br />
              One account.
            </h1>
            <p className="identity-intro">
              Create your account to get started. Event discovery and association workspaces are coming next.
            </p>
            <div className="identity-actions">
              {user ? (
                <Link className="identity-primary" to="/account">
                  My account
                </Link>
              ) : (
                <>
                  <Link className="identity-primary" to="/register">
                    Create an account
                  </Link>
                  <Link className="identity-secondary" to="/login">
                    Sign in
                  </Link>
                </>
              )}
            </div>
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
};
export default Home;
