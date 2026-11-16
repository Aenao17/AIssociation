import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ApiError, apiRequest } from '../auth/client';
import BackLink from '../components/BackLink';
import BrandHeader from '../components/BrandHeader';
import '../pages/Identity.css';

import EventDiscovery from './EventDiscovery';
import { eventTime } from './types';
import type { PublicEvent } from './types';
import './Events.css';

export function EventsPage(): ReactElement {
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell events-shell">
          <BrandHeader wide />
          <nav className="identity-actions">
            <BackLink to="/home">Home</BackLink>
            <BackLink to="/associations" direction="right">
              Associations
            </BackLink>
          </nav>
          <h1>Student events, together.</h1>
          <EventDiscovery />
        </main>
      </IonContent>
    </IonPage>
  );
}

function Detail({ id }: { id: string }): ReactElement {
  const [event, setEvent] = useState<PublicEvent | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void apiRequest<PublicEvent>(`/api/public/events/${encodeURIComponent(id)}`, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setEvent(data);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted)
          setError(
            failure instanceof ApiError && failure.status === 404
              ? 'Event not found or not published.'
              : 'Event could not be loaded. Please try again.',
          );
      });
    return () => controller.abort();
  }, [id, attempt]);
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell events-shell">
          <BrandHeader wide />
          <BackLink to="/events">Browse all events</BackLink>
          {error ? (
            <>
              <p role="alert">{error}</p>
              <button
                className="identity-secondary"
                type="button"
                onClick={() => {
                  setError('');
                  setAttempt((n) => n + 1);
                }}
              >
                Retry event
              </button>
            </>
          ) : !event ? (
            <p role="status">Loading event…</p>
          ) : (
            <article className="event-card">
              <p className="identity-eyebrow">{event.status}</p>
              <h1>{event.title}</h1>
              <Link to={`/associations/${event.association.slug}`}>{event.association.name}</Link>
              <p>
                <time dateTime={event.startsAt}>{eventTime(event.startsAt, event.timeZone)}</time> –{' '}
                <time dateTime={event.endsAt}>{eventTime(event.endsAt, event.timeZone)}</time> ({event.timeZone})
              </p>
              <p>{event.location}</p>
              <p className="event-prose">{event.description}</p>
              <p>
                Registration deadline: {eventTime(event.registrationDeadline, event.timeZone)} ({event.timeZone})
              </p>
              <p>Capacity: {event.capacity ?? 'Unlimited'}</p>
              <div id="event-registration" data-event-id={event.id} aria-label="Event registration">
                {event.status === 'CANCELLED' ? (
                  <p role="status">This event has been cancelled.</p>
                ) : event.status === 'COMPLETED' ? (
                  <p>This event has ended.</p>
                ) : new Date(event.registrationDeadline).getTime() <= now ? (
                  <p>Registration is closed.</p>
                ) : (
                  <p>Registration is not available on this page yet. Check back before the deadline.</p>
                )}
              </div>
            </article>
          )}
        </main>
      </IonContent>
    </IonPage>
  );
}
export function EventDetailPage(): ReactElement {
  const { eventId } = useParams<{ eventId: string }>();
  return <Detail key={eventId} id={eventId ?? ''} />;
}
