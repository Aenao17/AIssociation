import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import RosterPage from './RosterPage';
import type { Roster, RosterEntry } from './types';

const base = '/api/associations/a1/events/e1/registrations';

function entry(changes: Partial<RosterEntry> = {}): RosterEntry {
  return {
    registrationId: 'r1',
    userId: 'u2',
    displayName: 'Bianca',
    email: 'bianca@example.test',
    status: 'ACTIVE',
    registeredAt: '2026-11-16T10:00:00Z',
    cancelledAt: null,
    cancelledByOrganizer: false,
    attendance: 'NOT_CHECKED_IN',
    checkedInAt: null,
    ...changes,
  };
}

function roster(items: RosterEntry[], changes: Partial<Roster> = {}): Roster {
  return {
    eventId: 'e1',
    eventTitle: 'Autumn fair',
    eventStatus: 'PUBLISHED',
    capacity: 20,
    activeRegistrations: items.filter((item) => item.status === 'ACTIVE').length,
    checkedIn: items.filter((item) => item.attendance === 'CHECKED_IN').length,
    items,
    ...changes,
  };
}

function auth(request: AuthState['request'], signedOut = false): AuthState {
  return {
    user: signedOut
      ? null
      : { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin: false },
    request,
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    refreshUser: vi.fn(),
  };
}

function show(state: AuthState) {
  return render(
    <AuthContext.Provider value={state}>
      <MemoryRouter initialEntries={['/workspaces/alpha/events/e1/roster']}>
        <Routes>
          <Route path="/workspaces/:slug/events/:eventId/roster" element={<RosterPage />} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

function api(data: Roster, extra: (path: string, init?: RequestInit) => unknown = () => undefined) {
  const request = vi.fn();
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === '/api/workspaces/alpha') return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'ADMIN' };
    if (path === base && !init?.method) return data;
    return extra(path, init);
  });
  return request;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RosterPage', () => {
  it('lists participants with counters and a useful empty state', async () => {
    show(
      auth(
        api(
          roster([
            entry(),
            entry({
              registrationId: 'r2',
              displayName: 'Dan',
              email: 'dan@example.test',
              attendance: 'CHECKED_IN',
              checkedInAt: '2026-11-20T10:00:00Z',
            }),
          ]),
        ),
      ),
    );
    expect(await screen.findByRole('heading', { name: 'Autumn fair' })).toBeInTheDocument();
    expect(screen.getByTestId('roster-summary')).toHaveTextContent('2 registered of 20 places · 1 checked in');
    expect(screen.getByText('bianca@example.test')).toBeInTheDocument();
  });

  it('shows the empty state when nobody registered', async () => {
    show(auth(api(roster([]))));
    expect(await screen.findByText('No one has registered for this event yet.')).toBeInTheDocument();
  });

  it('checks a person in, updates the counters, and undoes it as a correction', async () => {
    const request = api(roster([entry()]), (_path, init) => {
      const attended = JSON.parse(String(init?.body)).attended as boolean;
      return entry(attended ? { attendance: 'CHECKED_IN', checkedInAt: '2026-11-20T10:00:00Z' } : {});
    });
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: 'Check in Bianca' }));
    expect(await screen.findByText('Bianca is checked in.')).toBeInTheDocument();
    expect(screen.getByTestId('roster-summary')).toHaveTextContent('1 checked in');
    expect(request).toHaveBeenCalledWith(`${base}/r1/attendance`, {
      method: 'PUT',
      body: JSON.stringify({ attended: true }),
    });

    fireEvent.click(screen.getByRole('button', { name: 'Undo check-in for Bianca' }));
    expect(await screen.findByText('Check-in of Bianca was corrected.')).toBeInTheDocument();
    expect(screen.getByTestId('roster-summary')).toHaveTextContent('0 checked in');
  });

  it('cancels a registration only after confirmation and then hides it by default', async () => {
    const request = api(roster([entry()]), () =>
      entry({ status: 'CANCELLED', cancelledAt: '2026-11-21T10:00:00Z', cancelledByOrganizer: true }),
    );
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    show(auth(request));
    const button = await screen.findByRole('button', { name: 'Cancel registration of Bianca' });

    fireEvent.click(button);
    expect(request).not.toHaveBeenCalledWith(`${base}/r1`, { method: 'DELETE' });

    fireEvent.click(button);
    expect(await screen.findByText('The registration of Bianca was cancelled.')).toBeInTheDocument();
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('roster-summary')).toHaveTextContent('0 registered');
    fireEvent.click(screen.getByLabelText('Show cancelled'));
    expect(await screen.findByText('Cancelled by organizer')).toBeInTheDocument();
  });

  it('filters by name or e-mail', async () => {
    show(auth(api(roster([entry(), entry({ registrationId: 'r2', displayName: 'Dan', email: 'dan@example.test' })]))));
    fireEvent.change(await screen.findByLabelText('Search participants'), { target: { value: 'dan@' } });
    const table = screen.getByRole('table');
    expect(within(table).queryByText('Bianca')).not.toBeInTheDocument();
    expect(within(table).getByText('Dan')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Search participants'), { target: { value: 'zzz' } });
    expect(screen.getByText('No participants match these filters.')).toBeInTheDocument();
  });

  it('shows the permission failure instead of a roster when the caller is not an organizer', async () => {
    const request = vi.fn();
    request.mockImplementation(async (path: string) => {
      if (path === '/api/workspaces/alpha')
        return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'MEMBER' };
      throw new ApiError(403, 'PROJECT_MANAGER_REQUIRED', 'denied');
    });
    show(auth(request));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Only the project manager or an association administrator',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('offers no actions on a cancelled event and keeps check-in off for it', async () => {
    show(auth(api(roster([entry()], { eventStatus: 'CANCELLED' }))));
    await screen.findByRole('heading', { name: 'Autumn fair' });
    expect(screen.queryByRole('button', { name: /Check in|Cancel registration of/ })).not.toBeInTheDocument();
  });

  it('reloads the truth when an action is rejected', async () => {
    let calls = 0;
    const request = vi.fn();
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === '/api/workspaces/alpha') return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'ADMIN' };
      if (init?.method === 'PUT') throw new ApiError(409, 'REGISTRATION_NOT_ACTIVE', 'x');
      calls += 1;
      return calls === 1
        ? roster([entry()])
        : roster([entry({ status: 'CANCELLED', cancelledAt: '2026-11-21T10:00:00Z' })]);
    });
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: 'Check in Bianca' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Only an active registration can be checked in.');
    await waitFor(() => expect(screen.getByTestId('roster-summary')).toHaveTextContent('0 registered'));
  });

  it('sends signed-out visitors to the login page', () => {
    show(auth(vi.fn(), true));
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });
});
