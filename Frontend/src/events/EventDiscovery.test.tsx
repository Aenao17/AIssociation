import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import EventDiscovery from './EventDiscovery';
import { EventDetailPage } from './EventPages';
import { discoveryQuery, eventTime } from './types';

afterEach(() => vi.unstubAllGlobals());
const event = {
  id: 'event-1',
  title: 'Community workshop',
  description: 'Student workshop',
  location: 'Campus',
  association: { id: 'a1', name: 'Alpha', slug: 'alpha' },
  startsAt: '2026-11-20T16:00:00Z',
  endsAt: '2026-11-20T18:00:00Z',
  timeZone: 'Europe/Bucharest',
  registrationDeadline: '2026-11-20T15:00:00Z',
  capacity: null,
  status: 'PUBLISHED',
};
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

describe('event discovery and calendar FR-11', () => {
  it('shares query and records between list and agenda while preserving filters', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation((url: string) =>
        Promise.resolve(
          response(url.includes('/events?') ? { items: [event], total: 1, page: 0, size: 20 } : [event.association]),
        ),
      );
    vi.stubGlobal('fetch', fetcher);
    render(
      <MemoryRouter initialEntries={['/events?association=a1&from=2026-11-20&to=2026-11-20']}>
        <EventDiscovery />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('link', { name: event.title })).toHaveAttribute('href', '/events/event-1');
    const initialCalls = fetcher.mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: 'Agenda calendar' }));
    expect(screen.getByRole('button', { name: 'Agenda calendar' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('link', { name: event.title })).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(initialCalls);
    const query = new URL(fetcher.mock.calls[0][0], 'https://example.test').searchParams;
    expect(query.get('associationId')).toBe('a1');
    expect(query.get('from')).toBe('2026-11-20T00:00:00Z');
    expect(query.get('to')).toBe('2026-11-21T00:00:00.000Z');
  });
  it('uses the same discovery API for a fixed association and resets pages on filter changes', async () => {
    const fetcher = vi.fn().mockResolvedValue(response({ items: [], total: 0, page: 0, size: 20 }));
    vi.stubGlobal('fetch', fetcher);
    render(
      <MemoryRouter initialEntries={['/associations/alpha?page=2']}>
        <EventDiscovery associationId="a1" />
      </MemoryRouter>,
    );
    await screen.findByText('No published events match these filters.');
    fireEvent.change(screen.getByLabelText('From (UTC)'), { target: { value: '2026-11-20' } });
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
    const query = new URL(fetcher.mock.calls[1][0], 'https://example.test').searchParams;
    expect(query.get('page')).toBe('0');
    expect(query.get('associationId')).toBe('a1');
    expect(screen.queryByLabelText('Association')).not.toBeInTheDocument();
  });
  it('shows failure and retries without presenting stale results', async () => {
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValue(response({ items: [], total: 0, page: 0, size: 20 }));
    vi.stubGlobal('fetch', fetcher);
    render(
      <MemoryRouter>
        <EventDiscovery associationId="a1" />
      </MemoryRouter>,
    );
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Retry events' }));
    expect(await screen.findByText('No published events match these filters.')).toBeInTheDocument();
  });
  it('converts inclusive UTC date inputs to an exclusive upper bound across month and year boundaries', () => {
    expect(new URLSearchParams(discoveryQuery('', '2026-12-31', '2026-12-31', 0)).get('to')).toBe(
      '2027-01-01T00:00:00.000Z',
    );
    expect(() => discoveryQuery('', '', '2026-99-99', 0)).not.toThrow();
    expect(eventTime('2026-11-20T16:00:00Z', 'Europe/Bucharest')).toContain('18:00');
    expect(eventTime('2026-07-20T16:00:00Z', 'Europe/Bucharest')).toContain('19:00');
  });
  it('displays cancellation and no registration promise on a public event', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ ...event, status: 'CANCELLED' })));
    render(
      <MemoryRouter>
        <EventDetailPage />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { name: event.title })).toBeInTheDocument();
    expect(screen.getByText('This event has been cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument();
  });
});
