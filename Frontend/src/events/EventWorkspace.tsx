import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import BackLink from '../components/BackLink';
import BrandHeader from '../components/BrandHeader';
import type { ProjectSummary } from '../projects/types';
import type { Workspace } from '../workspaces/types';
import '../pages/Identity.css';

import { eventTime } from './types';
import type { OrganizerEvent, PublicAssociation } from './types';
import './Events.css';

function message(error: unknown): string {
  return error instanceof ApiError
    ? `${error.message}${Object.entries(error.fieldErrors)
        .map(([field, detail]) => ` ${field}: ${detail}`)
        .join('')}`
    : 'Request failed. Please try again.';
}
function json(body: unknown, method = 'PUT'): RequestInit {
  return { method, body: JSON.stringify(body) };
}

function Editor({
  association,
  initial,
  projects,
  admin,
  onSaved,
  onClose,
}: {
  association: string;
  initial: OrganizerEvent | null;
  projects: ProjectSummary[];
  admin: boolean;
  onSaved: (saved: OrganizerEvent) => void;
  onClose: () => void;
}): ReactElement {
  const { request } = useAuth();
  const [projectId, setProject] = useState(initial?.projectId ?? (admin ? '' : (projects[0]?.id ?? '')));
  const [title, setTitle] = useState(initial?.event.title ?? '');
  const [description, setDescription] = useState(initial?.event.description ?? '');
  const [location, setLocation] = useState(initial?.event.location ?? '');
  const [startsAt, setStart] = useState(initial?.event.startsAt.slice(0, 16) ?? '');
  const [endsAt, setEnd] = useState(initial?.event.endsAt.slice(0, 16) ?? '');
  const [deadline, setDeadline] = useState(initial?.event.registrationDeadline.slice(0, 16) ?? '');
  const [timeZone, setZone] = useState(initial?.event.timeZone ?? 'Europe/Bucharest');
  const [capacity, setCapacity] = useState(initial?.event.capacity?.toString() ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save(event: FormEvent): Promise<void> {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const result = await request<OrganizerEvent>(
        `/api/associations/${association}/events${initial ? `/${initial.event.id}` : ''}`,
        json(
          {
            projectId: projectId || null,
            title,
            description,
            location,
            startsAt: new Date(`${startsAt}Z`).toISOString(),
            endsAt: new Date(`${endsAt}Z`).toISOString(),
            registrationDeadline: new Date(`${deadline}Z`).toISOString(),
            timeZone,
            capacity: capacity ? Number(capacity) : null,
          },
          initial ? 'PUT' : 'POST',
        ),
      );
      onSaved(result);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  let preview = '';
  try {
    if (startsAt) preview = eventTime(`${startsAt}Z`, timeZone);
  } catch {
    /* Validation is also enforced server-side. */
  }
  return (
    <section className="event-card">
      <h2>{initial ? 'Edit event' : 'Create event draft'}</h2>
      {error && (
        <p role="alert" className="identity-error">
          {error}
        </p>
      )}
      <form
        className="event-editor"
        onSubmit={(event) => {
          void save(event);
        }}
      >
        <fieldset disabled={busy}>
          <label>
            Event belongs to
            <select
              value={projectId}
              disabled={!!initial}
              onChange={(event) => setProject(event.target.value)}
              required={!admin}
            >
              {admin && <option value="">Standalone association event</option>}
              {initial?.projectId && !projects.some((project) => project.id === initial.projectId) && (
                <option value={initial.projectId}>Current project</option>
              )}
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Title
            <input required maxLength={150} value={title} onChange={(event) => setTitle(event.target.value)} />
          </label>
          <label>
            Description
            <textarea
              required
              maxLength={5000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <label>
            Location
            <input required maxLength={500} value={location} onChange={(event) => setLocation(event.target.value)} />
          </label>
          <p>Enter schedule times in UTC. The public page displays them in the event timezone below.</p>
          <div className="event-columns">
            <label>
              Start (UTC)
              <input
                type="datetime-local"
                required
                value={startsAt}
                onChange={(event) => setStart(event.target.value)}
              />
            </label>
            <label>
              End (UTC)
              <input
                type="datetime-local"
                required
                min={startsAt}
                value={endsAt}
                onChange={(event) => setEnd(event.target.value)}
              />
            </label>
            <label>
              Registration deadline (UTC)
              <input
                type="datetime-local"
                required
                max={startsAt}
                value={deadline}
                onChange={(event) => setDeadline(event.target.value)}
              />
            </label>
          </div>
          <label>
            Event timezone
            <input
              required
              maxLength={100}
              list="event-timezones"
              value={timeZone}
              onChange={(event) => setZone(event.target.value)}
            />
          </label>
          <datalist id="event-timezones">
            <option value="Europe/Bucharest" />
            <option value="Europe/London" />
            <option value="Europe/Paris" />
            <option value="UTC" />
          </datalist>
          {preview && (
            <p>
              Public start: {preview} ({timeZone})
            </p>
          )}
          <label>
            Capacity (leave empty for unlimited)
            <input
              type="number"
              min="1"
              max="2147483647"
              step="1"
              value={capacity}
              onChange={(event) => setCapacity(event.target.value)}
            />
          </label>
          {initial?.event.status === 'PUBLISHED' && (
            <p>
              Published capacity can only increase or become unlimited. A future registration deadline reopens
              registration.
            </p>
          )}
          <div className="identity-actions">
            <button className="identity-primary" type="submit">
              {busy ? 'Saving…' : 'Save event'}
            </button>
            <button className="identity-secondary" type="button" onClick={onClose}>
              Close editor
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}

function ProfileEditor({ workspace, profile }: { workspace: Workspace; profile: PublicAssociation }): ReactElement {
  const { request } = useAuth();
  const [description, setDescription] = useState(profile.description ?? '');
  const [website, setWebsite] = useState(profile.website ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function save(event: FormEvent): Promise<void> {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await request(
        `/api/associations/${workspace.associationId}/public-profile`,
        json({ description, website: website || null }),
      );
      setNotice('Public profile saved.');
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="event-card">
      <summary>Edit public association presentation</summary>
      <p>Name and address are managed through the existing association administration.</p>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      <form
        className="event-editor"
        onSubmit={(event) => {
          void save(event);
        }}
      >
        <fieldset disabled={busy}>
          <label>
            About the association
            <textarea maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} />
          </label>
          <label>
            Website
            <input type="url" maxLength={500} value={website} onChange={(event) => setWebsite(event.target.value)} />
          </label>
          <button type="submit" className="identity-secondary">
            {busy ? 'Saving…' : 'Save public profile'}
          </button>
        </fieldset>
      </form>
    </details>
  );
}

function Content({ slug }: { slug: string }): ReactElement {
  const { user, request } = useAuth();
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [events, setEvents] = useState<OrganizerEvent[]>([]);
  const [profile, setProfile] = useState<PublicAssociation | null>(null);
  const [editing, setEditing] = useState<OrganizerEvent | 'new' | null>(null);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    if (!user) return;
    void (async () => {
      setLoading(true);
      try {
        const scope = await request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`);
        const [list, projectList, publicProfile] = await Promise.all([
          request<OrganizerEvent[]>(`/api/associations/${scope.associationId}/events`),
          request<ProjectSummary[]>(`/api/associations/${scope.associationId}/projects`),
          request<PublicAssociation>(`/api/public/associations/${encodeURIComponent(slug)}`),
        ]);
        if (active) {
          setWorkspace(scope);
          setEvents(list);
          setProjects(projectList.filter((project) => project.canManage));
          setProfile(publicProfile);
          setError('');
        }
      } catch (failure) {
        if (active) {
          setError(message(failure));
          setWorkspace(null);
          setEvents([]);
          setEditing(null);
        }
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [request, slug, user, reload]);
  async function transition(item: OrganizerEvent, action: string): Promise<void> {
    if (!workspace) return;
    if (action === 'cancel' && !window.confirm('Cancel this published event? It cannot be reopened.')) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const saved = await request<OrganizerEvent>(
        `/api/associations/${workspace.associationId}/events/${item.event.id}/${action}`,
        { method: 'POST' },
      );
      setEvents((list) => list.map((entry) => (entry.event.id === saved.event.id ? saved : entry)));
      setNotice(`Event ${saved.event.status.toLowerCase()}.`);
    } catch (failure) {
      setError(message(failure));
      if (failure instanceof ApiError && failure.status === 403) {
        setEvents([]);
        setEditing(null);
        setReload((n) => n + 1);
      }
    } finally {
      setBusy(false);
    }
  }
  async function remove(item: OrganizerEvent): Promise<void> {
    if (
      !workspace ||
      !window.confirm(`Permanently delete ${item.event.title}? This removes its public page and cannot be undone.`)
    )
      return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await request(`/api/associations/${workspace.associationId}/events/${item.event.id}`, { method: 'DELETE' });
      setEvents((list) => list.filter((entry) => entry.event.id !== item.event.id));
      if (editing !== 'new' && editing?.event.id === item.event.id) setEditing(null);
      setNotice('Event deleted.');
    } catch (failure) {
      setError(message(failure));
      if (failure instanceof ApiError && failure.status === 403) {
        setEvents([]);
        setEditing(null);
        setReload((n) => n + 1);
      }
    } finally {
      setBusy(false);
    }
  }
  if (!user) return <Navigate to="/login" replace />;
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell events-shell">
          <BrandHeader wide />
          <nav className="identity-actions">
            <BackLink to={`/workspaces/${encodeURIComponent(slug)}`}>Workspace</BackLink>
            <Link to={`/associations/${encodeURIComponent(slug)}`}>Public association page</Link>
          </nav>
          <h1>{workspace?.name ?? 'Association'} events</h1>
          {error && (
            <p role="alert" className="identity-error">
              {error}
            </p>
          )}
          {notice && <p role="status">{notice}</p>}
          <button
            type="button"
            className="identity-secondary"
            disabled={loading || busy}
            onClick={() => {
              setEditing(null);
              setReload((n) => n + 1);
            }}
          >
            Refresh events
          </button>
          {loading ? (
            <p role="status">Loading organizer events…</p>
          ) : (
            workspace && (
              <>
                {(workspace.role === 'ADMIN' || projects.length > 0) && !editing && (
                  <button type="button" className="identity-primary" onClick={() => setEditing('new')}>
                    Create event
                  </button>
                )}
                {editing && (
                  <Editor
                    key={editing === 'new' ? 'new' : `${editing.event.id}:${editing.updatedAt}`}
                    association={workspace.associationId}
                    initial={editing === 'new' ? null : editing}
                    projects={projects}
                    admin={workspace.role === 'ADMIN'}
                    onClose={() => setEditing(null)}
                    onSaved={(saved) => {
                      setEditing(null);
                      setEvents((list) => [saved, ...list.filter((entry) => entry.event.id !== saved.event.id)]);
                      setNotice('Event saved.');
                    }}
                  />
                )}
                {!events.length && (
                  <p>
                    No events you can manage yet. Project managers manage their own project events; administrators
                    manage standalone events.
                  </p>
                )}
                <ul className="event-list">
                  {events.map((item) => (
                    <li className="event-card" key={item.event.id}>
                      <p>
                        {item.event.status} ·{' '}
                        {item.projectId
                          ? (projects.find((project) => project.id === item.projectId)?.name ?? 'Project event')
                          : 'Standalone event'}
                      </p>
                      <h2>{item.event.title}</h2>
                      <p>
                        {eventTime(item.event.startsAt, item.event.timeZone)} ({item.event.timeZone})
                      </p>
                      <div className="identity-actions">
                        <button
                          className="identity-secondary"
                          type="button"
                          disabled={busy}
                          onClick={() => {
                            void remove(item);
                          }}
                        >
                          Delete {item.event.title}
                        </button>
                        {['DRAFT', 'PUBLISHED'].includes(item.event.status) && (
                          <button
                            className="identity-secondary"
                            type="button"
                            disabled={busy}
                            onClick={() => {
                              setEditing(item);
                              setNotice('');
                            }}
                          >
                            Edit {item.event.title}
                          </button>
                        )}
                        {item.event.status === 'DRAFT' && (
                          <button
                            className="identity-primary"
                            type="button"
                            disabled={busy}
                            onClick={() => {
                              void transition(item, 'publish');
                            }}
                          >
                            Publish {item.event.title}
                          </button>
                        )}
                        {item.event.status === 'PUBLISHED' && (
                          <>
                            <button
                              className="identity-secondary"
                              type="button"
                              disabled={busy}
                              onClick={() => {
                                void transition(item, 'cancel');
                              }}
                            >
                              Cancel {item.event.title}
                            </button>
                            {new Date(item.event.endsAt).getTime() <= now && (
                              <button
                                className="identity-secondary"
                                type="button"
                                disabled={busy}
                                onClick={() => {
                                  void transition(item, 'complete');
                                }}
                              >
                                Complete {item.event.title}
                              </button>
                            )}
                          </>
                        )}
                        {item.event.status !== 'DRAFT' && (
                          <>
                            <Link to={`/events/${item.event.id}`}>View public event</Link>
                            <Link to={`/workspaces/${encodeURIComponent(slug)}/events/${item.event.id}/registrations`}>
                              Participants &amp; attendance
                            </Link>
                          </>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
                {workspace.role === 'ADMIN' && profile && <ProfileEditor workspace={workspace} profile={profile} />}
              </>
            )
          )}
        </main>
      </IonContent>
    </IonPage>
  );
}

export default function EventWorkspace(): ReactElement {
  const { slug } = useParams<{ slug: string }>();
  const { user } = useAuth();
  return <Content key={`${slug}:${user?.id}`} slug={slug ?? ''} />;
}
