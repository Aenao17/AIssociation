import { IonContent, IonPage } from '@ionic/react';
import { useCallback, useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { Link, Navigate } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';
import './Identity.css';

interface Association {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
}

export default function PlatformAssociationsPage(): ReactElement {
  const { user, request, refreshUser } = useAuth();
  const [associations, setAssociations] = useState<Association[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [loadError, setLoadError] = useState('');
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setAssociations(await request<Association[]>('/api/admin/associations'));
    } catch {
      setLoadError('Unable to load associations. Check your access and try again.');
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    if (!user?.platformAdmin) return;
    let active = true;
    request<Association[]>('/api/admin/associations')
      .then((result) => {
        if (active) setAssociations(result);
      })
      .catch(() => {
        if (active) setLoadError('Unable to load associations. Check your access and try again.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user?.platformAdmin, user?.id, request]);

  if (!user) return <Navigate to="/login" replace />;

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setFormError('');
    setNotice('');
    try {
      const created = await request<Association>('/api/admin/associations', {
        method: 'POST',
        body: JSON.stringify({ name, slug, initialAdminEmail: adminEmail }),
      });
      setAssociations((previous) => [...previous, created].sort((a, b) => a.name.localeCompare(b.name)));
      setNotice(`${created.name} was created and its first administrator was assigned.`);
      setName('');
      setSlug('');
      setAdminEmail('');
      // Creation has succeeded even if a subsequent identity refresh is interrupted.
      await refreshUser().catch(() => undefined);
    } catch (error) {
      setFormError(
        error instanceof ApiError
          ? `${error.message} ${Object.values(error.fieldErrors).join(' ')}`.trim()
          : 'Unable to create the association. Check the list before retrying.',
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <BrandHeader wide />
          {!user.platformAdmin ? (
            <section className="identity-card">
              <h1>Platform administration</h1>
              <p>This area is available to AIssociation platform administrators.</p>
              <Link to="/account">Back to my account</Link>
            </section>
          ) : (
            <div className="platform-layout">
              <section className="identity-card" aria-labelledby="create-association-heading">
                <p className="identity-eyebrow">Platform administration</p>
                <h1 id="create-association-heading">Create an association</h1>
                <p className="identity-intro">
                  Set up an association and appoint its first administrator from an existing account.
                </p>
                <form
                  onSubmit={(event) => {
                    void create(event);
                  }}
                >
                  <label>
                    Association name
                    <input
                      name="name"
                      required
                      maxLength={150}
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                    />
                  </label>
                  <label>
                    URL slug
                    <input
                      name="slug"
                      required
                      maxLength={100}
                      pattern="[a-z0-9]+(-[a-z0-9]+)*"
                      value={slug}
                      onChange={(event) => setSlug(event.target.value.toLowerCase())}
                      aria-describedby="slug-help"
                    />
                  </label>
                  <p id="slug-help" className="identity-help">
                    For example: student-tech-society. Use lowercase letters, numbers and hyphens.
                  </p>
                  <label>
                    First administrator’s email
                    <input
                      name="initialAdminEmail"
                      type="email"
                      required
                      maxLength={254}
                      value={adminEmail}
                      onChange={(event) => setAdminEmail(event.target.value)}
                      aria-describedby="admin-help"
                    />
                  </label>
                  <p id="admin-help" className="identity-help">
                    Ask this person to register first. This grants administration of this association, not the
                    AIssociation platform.
                  </p>
                  {formError && (
                    <p className="identity-error" role="alert">
                      {formError}
                    </p>
                  )}
                  {notice && (
                    <p className="identity-notice" role="status">
                      {notice}
                    </p>
                  )}
                  <button className="identity-primary" type="submit" disabled={saving}>
                    {saving ? 'Creating…' : 'Create association'}
                  </button>
                </form>
                <p className="identity-switch">
                  <Link to="/account">Back to my account</Link>
                </p>
              </section>
              <section className="identity-card" aria-labelledby="associations-heading">
                <p className="identity-eyebrow">Association directory</p>
                <h2 id="associations-heading">Existing associations</h2>
                {loading && <p role="status">Loading associations…</p>}
                {loadError && (
                  <p className="identity-error" role="alert">
                    {loadError}
                  </p>
                )}
                {!loading &&
                  !loadError &&
                  (associations.length === 0 ? (
                    <p className="identity-empty">No associations yet. Create the first one using the form.</p>
                  ) : (
                    <ul className="identity-memberships">
                      {associations.map((association) => (
                        <li key={association.id}>
                          <strong>{association.name}</strong>
                          <span>{association.slug}</span>
                        </li>
                      ))}
                    </ul>
                  ))}
                <button
                  className="identity-secondary"
                  disabled={loading || saving}
                  onClick={() => {
                    void load();
                  }}
                >
                  Refresh list
                </button>
              </section>
            </div>
          )}
        </main>
      </IonContent>
    </IonPage>
  );
}
