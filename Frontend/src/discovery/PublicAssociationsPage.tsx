import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ApiError, apiRequest } from '../auth/client';
import BackLink from '../components/BackLink';
import BrandHeader from '../components/BrandHeader';
import EventDiscovery from '../events/EventDiscovery';
import '../pages/Identity.css';
import './PublicAssociations.css';

interface PublicAssociation {
  id: string;
  name: string;
  slug: string;
  description?: string;
  website?: string | null;
}

type LoadState =
  { kind: 'loading' } | { kind: 'ready'; associations: PublicAssociation[] } | { kind: 'error'; message: string };

// Remount scoped state on navigation so an old profile cannot flash on a new URL.
export default function PublicAssociationsPage(): ReactElement {
  const { slug } = useParams<{ slug: string }>();
  return <AssociationView key={slug ?? 'directory'} slug={slug} />;
}

function AssociationView({ slug }: { slug?: string }) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const path = '/api/public/associations';
    void (async () => {
      try {
        const associations = slug
          ? [await apiRequest<PublicAssociation>(`${path}/${encodeURIComponent(slug)}`, { signal: controller.signal })]
          : await apiRequest<PublicAssociation[]>(path, { signal: controller.signal });
        if (!controller.signal.aborted) setState({ kind: 'ready', associations });
      } catch (error) {
        if (!controller.signal.aborted) {
          setState({
            kind: 'error',
            message:
              error instanceof ApiError && error.status === 404
                ? 'Association not found. Browse the directory to find another community.'
                : 'We could not load associations. Please try again.',
          });
        }
      }
    })();
    return () => controller.abort();
  }, [slug, attempt]);

  const profile = slug && state.kind === 'ready' ? state.associations[0] : undefined;
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell public-associations">
          <BrandHeader wide />
          <nav aria-label="Public navigation" className="identity-actions">
            <BackLink to="/home">Home</BackLink>
            {slug && <BackLink to="/associations">All associations</BackLink>}
          </nav>
          <section className="identity-card">
            <p className="identity-eyebrow">Student communities</p>
            <h1>{slug ? (profile?.name ?? 'Association') : 'Find your community'}</h1>
            {!slug && <p>Discover student associations. You can browse without an account.</p>}
            {state.kind === 'loading' && <p role="status">Loading associations…</p>}
            {state.kind === 'error' && (
              <div>
                <p className="identity-error" role="alert">
                  {state.message}
                </p>
                <button
                  className="identity-secondary"
                  type="button"
                  onClick={() => {
                    setState({ kind: 'loading' });
                    setAttempt((value) => value + 1);
                  }}
                >
                  Try again
                </button>
              </div>
            )}
            {state.kind === 'ready' &&
              !slug &&
              (state.associations.length ? (
                <ul className="public-association-list">
                  {state.associations.map((association) => (
                    <li key={association.id}>
                      <Link to={`/associations/${encodeURIComponent(association.slug)}`}>{association.name}</Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p>No associations are listed yet. Please check back later.</p>
              ))}
            {profile && <p>Welcome to {profile.name} on AIssociation.</p>}
            {profile?.description && <p className="event-prose">{profile.description}</p>}
            {profile?.website && /^https?:\/\//i.test(profile.website) && (
              <p>
                <a href={profile.website} rel="noopener noreferrer" target="_blank">
                  Association website
                </a>
              </p>
            )}
          </section>
          {profile && <EventDiscovery key={profile.id} associationId={profile.id} />}
        </main>
      </IonContent>
    </IonPage>
  );
}
