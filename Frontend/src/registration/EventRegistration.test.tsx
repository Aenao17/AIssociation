import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { EventDetailPage } from '../events/EventPages';

const event = {
  id: 'e1',
  title: 'Open workshop',
  description: 'A public workshop',
  location: 'Campus',
  startsAt: '2099-01-01T12:00:00Z',
  endsAt: '2099-01-01T13:00:00Z',
  registrationDeadline: '2099-01-01T11:00:00Z',
  timeZone: 'UTC',
  capacity: 10,
  status: 'PUBLISHED',
  association: { id: 'a1', name: 'Association A', slug: 'assoc-a' },
};
const availability = {
  eventId: 'e1',
  eventStatus: 'PUBLISHED',
  capacity: 10,
  activeRegistrations: 0,
  seatsRemaining: 10,
  registrationOpen: true,
  registrationDeadline: event.registrationDeadline,
};

function view(auth: AuthState) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/events/e1']}>
        <Routes>
          <Route path="/events/:eventId" element={<EventDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

function state(signedIn = true): AuthState {
  return {
    user: signedIn
      ? {
          id: 'u1',
          displayName: 'Participant',
          email: 'participant@example.test',
          memberships: [],
          platformAdmin: false,
        }
      : null,
    request: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    refreshUser: vi.fn(),
  };
}

afterEach(() => vi.unstubAllGlobals());

describe('registration from public event details', () => {
  it('lets an account without association membership register and cancel from event details', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response(JSON.stringify(event), { status: 200 })),
    );
    const auth = state();
    vi.mocked(auth.request).mockImplementation(async (_path, options) => {
      const active = options?.method === 'POST';
      return {
        eventId: 'e1',
        status: active ? 'ACTIVE' : options?.method === 'DELETE' ? 'CANCELLED' : 'NOT_REGISTERED',
        registeredAt: active ? '2026-11-18T10:00:00Z' : null,
        cancelledAt: options?.method === 'DELETE' ? '2026-11-18T10:01:00Z' : null,
        cancelledByOrganizer: false,
        canRegister: !active,
        canCancel: active,
        availability,
      };
    });
    view(auth);
    expect(await screen.findByRole('heading', { name: 'Open workshop' })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: 'Register for this event' }));
    expect(await screen.findByText('You are registered. Your place is confirmed.')).toBeInTheDocument();
    expect(auth.request).toHaveBeenCalledWith('/api/events/e1/registration', { method: 'POST' });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel registration' }));
    expect(await screen.findByText('Your registration was cancelled and your place released.')).toBeInTheDocument();
    expect(auth.request).toHaveBeenCalledWith('/api/events/e1/registration', { method: 'DELETE' });
  });

  it('shows public availability and a sign-in link without requesting private registration data', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(
        async (path: string) =>
          new Response(JSON.stringify(path.endsWith('/availability') ? availability : event), { status: 200 }),
      );
    vi.stubGlobal('fetch', fetcher);
    const auth = state(false);
    view(auth);
    expect(await screen.findByRole('link', { name: 'Sign in to register' })).toHaveAttribute('href', '/login');
    expect(await screen.findByText('10 of 10 places left')).toBeInTheDocument();
    expect(auth.request).not.toHaveBeenCalled();
    expect(screen.queryByText(/Registration is not available on this page yet/)).not.toBeInTheDocument();
  });
});
