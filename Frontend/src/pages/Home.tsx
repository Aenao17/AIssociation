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
        <main className="identity-shell identity-home events-shell">
          <BrandHeader
            wide
            actions={
              <nav className="homepage-account-nav" aria-label="Account">
                {user ? (
                  <Link className="identity-primary" to="/account">
                    My account
                  </Link>
                ) : (
                  <>
                    <Link className="identity-secondary homepage-signin" to="/login">
                      Sign in
                    </Link>
                    <Link className="identity-primary" to="/register">
                      Create account
                    </Link>
                  </>
                )}
              </nav>
            }
          />
          <section className="identity-card identity-landing">
            <p className="identity-eyebrow">For student communities</p>
            <h1>
              Your associations.
              <br />
              <span className="identity-hero-accent">One account.</span>
            </h1>
            <p className="identity-intro">One place for your account and student association memberships.</p>
            <div className="identity-actions">
              <Link className="identity-primary" to="/events">
                Discover events
              </Link>
              <Link className="identity-secondary" to="/associations">
                Explore associations
              </Link>
            </div>
            <p className="identity-help">
              <Link to="/research">Help shape AIssociation: share your experience</Link>
            </p>
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
};
export default Home;
