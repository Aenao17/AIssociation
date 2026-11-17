import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Navigate, useParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import BackLink from '../components/BackLink';
import BrandHeader from '../components/BrandHeader';
import type { Workspace } from '../workspaces/types';

import { countRoster, registrationErrorText } from './types';
import type { Roster, RosterEntry } from './types';
import '../pages/Identity.css';
import '../events/Events.css';
import './Registration.css';

function Content({ slug, eventId }: { slug: string; eventId: string }): ReactElement {
  const { user, request } = useAuth();
  const userId = user?.id ?? null;
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [roster, setRoster] = useState<Roster | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [showCancelled, setShowCancelled] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!userId) return undefined;
    let active = true;
    async function load(): Promise<void> {
      try {
        const scope = await request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`);
        const data = await request<Roster>(
          `/api/associations/${scope.associationId}/events/${encodeURIComponent(eventId)}/registrations`,
        );
        if (active) {
          setWorkspace(scope);
          setRoster(data);
          setError('');
        }
      } catch (failure) {
        if (active) setError(registrationErrorText(failure));
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [request, userId, slug, eventId, attempt]);

  if (!user) return <Navigate to="/login" replace />;

  function applyUpdate(updated: RosterEntry): void {
    setRoster((current) => {
      if (!current) return current;
      const items = current.items.map((item) => (item.registrationId === updated.registrationId ? updated : item));
      return { ...current, items, ...countRoster(items) };
    });
  }

  async function run(entry: RosterEntry, action: () => Promise<RosterEntry>, success: string): Promise<void> {
    setBusyId(entry.registrationId);
    setNotice('');
    setError('');
    try {
      applyUpdate(await action());
      setNotice(success);
    } catch (failure) {
      setError(registrationErrorText(failure));
      setAttempt((n) => n + 1);
    } finally {
      setBusyId(null);
    }
  }

  const base =
    workspace && roster ? `/api/associations/${workspace.associationId}/events/${roster.eventId}/registrations` : '';

  function setAttendance(entry: RosterEntry, attended: boolean): Promise<void> {
    return run(
      entry,
      () =>
        request<RosterEntry>(`${base}/${entry.registrationId}/attendance`, {
          method: 'PUT',
          body: JSON.stringify({ attended }),
        }),
      attended ? `${entry.displayName} is checked in.` : `Check-in of ${entry.displayName} was corrected.`,
    );
  }

  function cancelRegistration(entry: RosterEntry): Promise<void> {
    if (!window.confirm(`Cancel the registration of ${entry.displayName}? Their place will be released.`)) {
      return Promise.resolve();
    }
    return run(
      entry,
      () => request<RosterEntry>(`${base}/${entry.registrationId}`, { method: 'DELETE' }),
      `The registration of ${entry.displayName} was cancelled.`,
    );
  }

  const needle = query.trim().toLowerCase();
  const visible = (roster?.items ?? []).filter(
    (item) =>
      (showCancelled || item.status === 'ACTIVE') &&
      (!needle || item.displayName.toLowerCase().includes(needle) || item.email.toLowerCase().includes(needle)),
  );
  const canCheckIn = roster?.eventStatus === 'PUBLISHED' || roster?.eventStatus === 'COMPLETED';
  const canCancel = roster?.eventStatus === 'PUBLISHED';

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell events-shell roster-shell">
          <BrandHeader wide />
          <BackLink to={`/workspaces/${encodeURIComponent(slug)}/events`}>Events</BackLink>

          {error && (
            <div className="identity-error" role="alert">
              <p>{error}</p>
              <button className="identity-secondary" type="button" onClick={() => setAttempt((n) => n + 1)}>
                Retry roster
              </button>
            </div>
          )}
          {notice && (
            <p className="identity-notice" role="status">
              {notice}
            </p>
          )}
          {!roster && !error && <p role="status">Loading roster…</p>}

          {roster && (
            <>
              <p className="identity-eyebrow">{roster.eventStatus}</p>
              <h1>{roster.eventTitle}</h1>
              <p className="roster-summary" data-testid="roster-summary">
                {roster.activeRegistrations} registered
                {roster.capacity === null ? ' (unlimited places)' : ` of ${roster.capacity} places`} ·{' '}
                {roster.checkedIn} checked in
              </p>

              <div className="roster-toolbar">
                <label>
                  Search participants
                  <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} />
                </label>
                <label className="roster-check">
                  <input
                    type="checkbox"
                    checked={showCancelled}
                    onChange={(event) => setShowCancelled(event.target.checked)}
                  />
                  Show cancelled
                </label>
                <button className="identity-secondary" type="button" onClick={() => setAttempt((n) => n + 1)}>
                  Refresh roster
                </button>
              </div>

              {roster.items.length === 0 ? (
                <p className="identity-empty">No one has registered for this event yet.</p>
              ) : visible.length === 0 ? (
                <p className="identity-empty">No participants match these filters.</p>
              ) : (
                <div className="roster-table-wrap">
                  <table className="roster-table">
                    <caption>Participants of {roster.eventTitle}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Participant</th>
                        <th scope="col">Registration</th>
                        <th scope="col">Attendance</th>
                        <th scope="col">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {visible.map((entry) => {
                        const active = entry.status === 'ACTIVE';
                        const busy = busyId === entry.registrationId;
                        return (
                          <tr key={entry.registrationId}>
                            <th scope="row">
                              {entry.displayName}
                              <span className="roster-email">{entry.email}</span>
                            </th>
                            <td>
                              {active
                                ? 'Registered'
                                : entry.cancelledByOrganizer
                                  ? 'Cancelled by organizer'
                                  : 'Cancelled'}
                            </td>
                            <td>
                              <span
                                className={`roster-badge${entry.attendance === 'CHECKED_IN' ? ' roster-badge-in' : ''}`}
                              >
                                {entry.attendance === 'CHECKED_IN' ? 'Checked in' : 'Not checked in'}
                              </span>
                            </td>
                            <td>
                              <div className="roster-actions">
                                {active && canCheckIn && entry.attendance === 'NOT_CHECKED_IN' && (
                                  <button
                                    className="identity-primary"
                                    type="button"
                                    disabled={busy}
                                    onClick={() => void setAttendance(entry, true)}
                                  >
                                    Check in {entry.displayName}
                                  </button>
                                )}
                                {active && canCheckIn && entry.attendance === 'CHECKED_IN' && (
                                  <button
                                    className="identity-secondary"
                                    type="button"
                                    disabled={busy}
                                    onClick={() => void setAttendance(entry, false)}
                                  >
                                    Undo check-in for {entry.displayName}
                                  </button>
                                )}
                                {active && canCancel && (
                                  <button
                                    className="identity-secondary"
                                    type="button"
                                    disabled={busy}
                                    onClick={() => void cancelRegistration(entry)}
                                  >
                                    Cancel registration of {entry.displayName}
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </main>
      </IonContent>
    </IonPage>
  );
}

export default function RosterPage(): ReactElement {
  const { slug, eventId } = useParams<{ slug: string; eventId: string }>();
  const { user } = useAuth();
  return <Content key={`${slug}:${eventId}:${user?.id}`} slug={slug ?? ''} eventId={eventId ?? ''} />;
}
