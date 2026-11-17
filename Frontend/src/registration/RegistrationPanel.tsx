import { useContext, useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link } from 'react-router-dom';

import { AuthContext } from '../auth/AuthContext';
import { apiRequest } from '../auth/client';
import type { PublicEvent } from '../events/types';

import { registrationErrorText, seatsText } from './types';
import type { Availability, MyRegistration } from './types';
import './Registration.css';

interface Props {
  eventId: string;
  status: PublicEvent['status'];
}

interface View {
  availability: Availability;
  registration: MyRegistration | null;
}

interface Message {
  kind: 'error' | 'success';
  text: string;
}

function Panel({ eventId, status }: Props): ReactElement {
  const auth = useContext(AuthContext);
  const request = auth?.request;
  const userId = auth?.user?.id ?? null;
  const terminal = status === 'CANCELLED' || status === 'COMPLETED';

  const [view, setView] = useState<View | null>(null);
  const [loadError, setLoadError] = useState('');
  const [message, setMessage] = useState<Message | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (terminal) return undefined;
    const controller = new AbortController();
    const id = encodeURIComponent(eventId);

    async function load(): Promise<void> {
      try {
        if (request && userId) {
          const mine = await request<MyRegistration>(`/api/events/${id}/registration`, { signal: controller.signal });
          if (!controller.signal.aborted) {
            setView({ availability: mine.availability, registration: mine });
            setLoadError('');
          }
        } else {
          const availability = await apiRequest<Availability>(`/api/public/events/${id}/availability`, {
            signal: controller.signal,
          });
          if (!controller.signal.aborted) {
            setView({ availability, registration: null });
            setLoadError('');
          }
        }
      } catch (failure) {
        if (!controller.signal.aborted) setLoadError(registrationErrorText(failure));
      }
    }

    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [eventId, request, userId, terminal, attempt]);

  async function act(method: 'POST' | 'DELETE', success: string): Promise<void> {
    if (!request) return;
    setBusy(true);
    setMessage(null);
    try {
      const updated = await request<MyRegistration>(`/api/events/${encodeURIComponent(eventId)}/registration`, {
        method,
      });
      setView({ availability: updated.availability, registration: updated });
      setMessage({ kind: 'success', text: success });
    } catch (failure) {
      setMessage({ kind: 'error', text: registrationErrorText(failure) });
      setAttempt((n) => n + 1);
    } finally {
      setBusy(false);
    }
  }

  if (status === 'CANCELLED') return <p role="status">This event has been cancelled.</p>;
  if (status === 'COMPLETED') return <p>This event has ended.</p>;

  const registration = view?.registration ?? null;
  const availability = view?.availability ?? null;

  return (
    <section className="registration-panel" aria-label="Registration">
      <h2>Registration</h2>
      {loadError && (
        <div className="identity-error" role="alert">
          <p>{loadError}</p>
          <button className="identity-secondary" type="button" onClick={() => setAttempt((n) => n + 1)}>
            Retry registration
          </button>
        </div>
      )}
      {!view && !loadError && <p role="status">Loading registration…</p>}
      {availability && (
        <p className="registration-seats" data-testid="registration-seats">
          {seatsText(availability)}
        </p>
      )}
      {message && (
        <p
          className={message.kind === 'error' ? 'identity-error' : 'identity-notice'}
          role={message.kind === 'error' ? 'alert' : 'status'}
        >
          {message.text}
        </p>
      )}

      {view && !registration && (
        <>
          {availability?.registrationOpen ? (
            <p>
              <Link to="/login">Sign in to register</Link> or <Link to="/register">create an account</Link>.
            </p>
          ) : (
            <p>Registration is closed.</p>
          )}
        </>
      )}

      {registration && (
        <div className="registration-actions">
          {registration.status === 'ACTIVE' && (
            <>
              <p role="status">
                You are registered
                {registration.registeredAt
                  ? ` (since ${new Date(registration.registeredAt).toLocaleString('en-GB')})`
                  : ''}
                .
              </p>
              {registration.canCancel ? (
                <button
                  className="identity-secondary"
                  type="button"
                  disabled={busy}
                  onClick={() => void act('DELETE', 'Your registration was cancelled and your place released.')}
                >
                  Cancel registration
                </button>
              ) : (
                <p className="identity-help">
                  The deadline has passed. Contact an organizer if you can no longer attend.
                </p>
              )}
            </>
          )}

          {registration.status !== 'ACTIVE' && (
            <>
              {registration.status === 'CANCELLED' && !registration.cancelledByOrganizer && (
                <p>You cancelled your registration.</p>
              )}
              {registration.cancelledByOrganizer && (
                <p>An organizer cancelled your registration. Contact them to register again.</p>
              )}
              {registration.canRegister ? (
                <button
                  className="identity-primary"
                  type="button"
                  disabled={busy}
                  onClick={() => void act('POST', 'You are registered. Your place is confirmed.')}
                >
                  {registration.status === 'CANCELLED' ? 'Register again' : 'Register for this event'}
                </button>
              ) : (
                !registration.cancelledByOrganizer && (
                  <p>
                    {registration.availability.registrationOpen ? 'This event is full.' : 'Registration is closed.'}
                  </p>
                )
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}

export default function RegistrationPanel(props: Props): ReactElement {
  const auth = useContext(AuthContext);
  return <Panel key={`${props.eventId}:${auth?.user?.id ?? 'anonymous'}`} {...props} />;
}
