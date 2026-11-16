import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { apiRequest } from '../auth/client';

import { discoveryQuery, eventTime } from './types';
import type { EventPage, PublicAssociation } from './types';
import './Events.css';

export default function EventDiscovery({ associationId = '' }: { associationId?: string }): ReactElement {
  const [search, setSearch] = useSearchParams();
  const selected = associationId || search.get('association') || '';
  const from = search.get('from') ?? '';
  const to = search.get('to') ?? '';
  const agenda = search.get('view') === 'agenda';
  const rawPage = Number(search.get('page') ?? 0);
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const query = discoveryQuery(
    selected,
    /^\d{4}-\d{2}-\d{2}$/.test(from) ? from : '',
    /^\d{4}-\d{2}-\d{2}$/.test(to) ? to : '',
    page,
  );
  const [result, setResult] = useState<{ query: string; data: EventPage } | null>(null);
  const [failure, setFailure] = useState<{ query: string; message: string } | null>(null);
  const [associations, setAssociations] = useState<PublicAssociation[]>([]);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    void apiRequest<EventPage>(`/api/public/events?${query}`, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) {
          setResult({ query, data });
          setFailure(null);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setFailure({ query, message: 'Events could not be loaded. Check the dates and try again.' });
      });
    return () => controller.abort();
  }, [query, attempt]);
  useEffect(() => {
    const controller = new AbortController();
    if (!associationId)
      void apiRequest<PublicAssociation[]>('/api/public/associations', { signal: controller.signal })
        .then((data) => {
          if (!controller.signal.aborted) setAssociations(data);
        })
        .catch(() => undefined);
    return () => controller.abort();
  }, [associationId]);
  function change(key: string, value: string): void {
    const next = new URLSearchParams(search);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page' && key !== 'view') next.delete('page');
    setSearch(next);
  }
  const data = result?.query === query ? result.data : null;
  const error = failure?.query === query ? failure.message : '';
  return (
    <section className="event-discovery" aria-label="Published events">
      <h2>{associationId ? 'Published events' : 'Discover events'}</h2>
      <div className="event-filters">
        {!associationId && (
          <label>
            Association
            <select value={selected} onChange={(event) => change('association', event.target.value)}>
              <option value="">All associations</option>
              {selected && !associations.some((item) => item.id === selected) && (
                <option value={selected}>Selected association</option>
              )}
              {associations.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          From (UTC)
          <input type="date" value={from} onChange={(event) => change('from', event.target.value)} />
        </label>
        <label>
          Through (UTC)
          <input type="date" value={to} min={from} onChange={(event) => change('to', event.target.value)} />
        </label>
      </div>
      <div className="identity-actions" aria-label="Event display">
        <button className="identity-secondary" type="button" aria-pressed={!agenda} onClick={() => change('view', '')}>
          List
        </button>
        <button
          className="identity-secondary"
          type="button"
          aria-pressed={agenda}
          onClick={() => change('view', 'agenda')}
        >
          Agenda calendar
        </button>
      </div>
      {error ? (
        <div>
          <p role="alert">{error}</p>
          <button
            type="button"
            className="identity-secondary"
            onClick={() => {
              setFailure(null);
              setResult(null);
              setAttempt((n) => n + 1);
            }}
          >
            Retry events
          </button>
        </div>
      ) : !data ? (
        <p role="status">Loading events…</p>
      ) : !data.items.length ? (
        <p>No published events match these filters.</p>
      ) : (
        <>
          <p>{data.total} events · dates displayed in each event’s timezone</p>
          <ul className={agenda ? 'event-list event-agenda' : 'event-list'}>
            {data.items.map((event) => (
              <li key={event.id} className="event-card">
                <p>
                  <time dateTime={event.startsAt}>{eventTime(event.startsAt, event.timeZone)}</time> · {event.timeZone}
                </p>
                <h3>
                  <Link to={`/events/${event.id}`}>{event.title}</Link>
                </h3>
                <p>
                  <Link to={`/associations/${event.association.slug}`}>{event.association.name}</Link> ·{' '}
                  {event.location}
                </p>
                {!agenda && <p className="event-excerpt">{event.description}</p>}
              </li>
            ))}
          </ul>
        </>
      )}
      {data && (
        <nav className="identity-actions" aria-label="Event pages">
          <button
            type="button"
            className="identity-secondary"
            disabled={page === 0}
            onClick={() => change('page', String(page - 1))}
          >
            Previous
          </button>
          <span>Page {page + 1}</span>
          <button
            type="button"
            className="identity-secondary"
            disabled={(page + 1) * data.size >= data.total}
            onClick={() => change('page', String(page + 1))}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  );
}
