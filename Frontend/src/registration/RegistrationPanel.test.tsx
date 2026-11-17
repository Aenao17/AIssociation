import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import RegistrationPanel from './RegistrationPanel';
import type { Availability, MyRegistration } from './types';

function availability(changes: Partial<Availability> = {}): Availability {
  return {
    eventId: 'e1',
    eventStatus: 'PUBLISHED',
    capacity: 30,
    activeRegistrations: 12,
    seatsRemaining: 18,
    registrationOpen: true,
    registrationDeadline: '2099-01-01T10:00:00Z',
    ...changes,
  };
}

function mine(changes: Partial<MyRegistration> = {}, seats: Partial<Availability> = {}): MyRegistration {
  return {
    eventId: 'e1',
    status: 'NOT_REGISTERED',
    registeredAt: null,
    cancelledAt: null,
    cancelledByOrganizer: false,
    canRegister: true,
    canCancel: false,
    availability: availability(seats),
    ...changes,
  };
}

function signedIn(request: AuthState['request']): AuthState {
  return {
    user: { id: 'u1', displayName: 'Ana', email: 'ana@example.test', memberships: [], platformAdmin: false },
    request,
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    refreshUser: vi.fn(),
  };
}

function show(auth?: AuthState, status: 'PUBLISHED' | 'CANCELLED' | 'COMPLETED' = 'PUBLISHED') {
  const panel = (
    <MemoryRouter>
      <RegistrationPanel eventId="e1" status={status} />
    </MemoryRouter>
  );
  return render(auth ? <AuthContext.Provider value={auth}>{panel}</AuthContext.Provider> : panel);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('RegistrationPanel', () => {
  it('offers sign-in (not a register button) to a signed-out visitor and shows only seat counts', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(async () => new Response(JSON.stringify(availability()), { status: 200 }));
    vi.stubGlobal('fetch', fetcher);
    show();
    expect(await screen.findByText('18 of 30 places left')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in to register' })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument();
    expect(String(fetcher.mock.calls[0][0])).toContain('/api/public/events/e1/availability');
  });

  it('registers immediately, then cancels and releases the place', async () => {
    const request = vi.fn();
    request.mockImplementation(async (_path: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return mine(
          { status: 'ACTIVE', registeredAt: '2026-11-16T10:00:00Z', canRegister: false, canCancel: true },
          {
            activeRegistrations: 13,
            seatsRemaining: 17,
          },
        );
      }
      if (init?.method === 'DELETE') {
        return mine({ status: 'CANCELLED', cancelledAt: '2026-11-16T11:00:00Z' });
      }
      return mine();
    });
    show(signedIn(request));

    fireEvent.click(await screen.findByRole('button', { name: 'Register for this event' }));
    expect(await screen.findByText('You are registered. Your place is confirmed.')).toBeInTheDocument();
    expect(screen.getByText('17 of 30 places left')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/events/e1/registration', { method: 'POST' });

    fireEvent.click(screen.getByRole('button', { name: 'Cancel registration' }));
    expect(await screen.findByText('Your registration was cancelled and your place released.')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/events/e1/registration', { method: 'DELETE' });
    expect(screen.getByRole('button', { name: 'Register again' })).toBeInTheDocument();
  });

  it('explains a full event and offers no register button', async () => {
    const request = vi
      .fn()
      .mockResolvedValue(mine({ canRegister: false }, { seatsRemaining: 0, activeRegistrations: 30 }));
    show(signedIn(request));
    expect(await screen.findByText('This event is full.')).toBeInTheDocument();
    expect(screen.getByText('0 of 30 places left')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument();
  });

  it('shows the server rejection when someone else took the last place, then resyncs', async () => {
    let full = false;
    const request = vi.fn();
    request.mockImplementation(async (_path: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        full = true; /
        throw new ApiError(409, 'EVENT_FULL', 'No places left');
      }
      return full ? mine({ canRegister: false }, { seatsRemaining: 0 }) : mine({}, { seatsRemaining: 1 });
    });
    show(signedIn(request));
    fireEvent.click(await screen.findByRole('button', { name: 'Register for this event' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This event is full: there are no places left.');
    await waitFor(() => expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument());
    expect(screen.getByText('0 of 30 places left')).toBeInTheDocument();
  });

  it('after the deadline a registered person cannot cancel and is told to contact an organizer', async () => {
    const request = vi.fn().mockResolvedValue(
      mine(
        { status: 'ACTIVE', registeredAt: '2026-11-16T10:00:00Z', canRegister: false, canCancel: false },
        {
          registrationOpen: false,
        },
      ),
    );
    show(signedIn(request));
    expect(await screen.findByText(/You are registered/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel registration' })).not.toBeInTheDocument();
    expect(screen.getByText(/Contact an organizer/)).toBeInTheDocument();
  });

  it('does not let a person re-register after an organizer cancelled them', async () => {
    const request = vi.fn().mockResolvedValue(
      mine({
        status: 'CANCELLED',
        cancelledByOrganizer: true,
        canRegister: false,
        cancelledAt: '2026-11-16T12:00:00Z',
      }),
    );
    show(signedIn(request));
    expect(await screen.findByText(/An organizer cancelled your registration/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument();
  });

  it('shows a load failure with retry instead of stale or empty data', async () => {
    const request = vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValue(mine());
    show(signedIn(request));
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to reach the server');
    fireEvent.click(screen.getByRole('button', { name: 'Retry registration' }));
    expect(await screen.findByRole('button', { name: 'Register for this event' })).toBeInTheDocument();
  });

  it('shows fixed text for cancelled and ended events without calling the server', () => {
    const request = vi.fn();
    const { unmount } = show(signedIn(request), 'CANCELLED');
    expect(screen.getByText('This event has been cancelled.')).toBeInTheDocument();
    unmount();
    show(signedIn(request), 'COMPLETED');
    expect(screen.getByText('This event has ended.')).toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });
});