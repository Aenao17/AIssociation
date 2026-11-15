import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import AccountPage from './AccountPage';
import WorkspacePage from './WorkspacePage';

function state(): AuthState {
  return {
    user: {
      id: 'u1',
      displayName: 'Alex',
      email: 'alex@example.test',
      memberships: [
        { associationId: 'a1', associationName: 'Association A', associationSlug: 'assoc-a', role: 'MEMBER' },
      ],
      platformAdmin: false,
    },
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn().mockResolvedValue(undefined),
    request: vi.fn(),
  };
}
function view(auth: AuthState, path = '/workspaces/assoc-a') {
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/workspaces/:slug" element={<WorkspacePage />} />
          <Route path="/account" element={<AccountPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('association workspace', () => {
  it('allows a member to enter without requesting the private member roster', async () => {
    const auth = state();
    vi.mocked(auth.request).mockResolvedValue({
      associationId: 'a1',
      name: 'Association A',
      slug: 'assoc-a',
      role: 'MEMBER',
    });
    view(auth);
    await screen.findByRole('heading', { name: 'Association A' });
    expect(screen.queryByRole('heading', { name: 'Manage members' })).not.toBeInTheDocument();
    expect(auth.request).toHaveBeenCalledTimes(1);
    expect(auth.request).toHaveBeenCalledWith('/api/workspaces/assoc-a');
  });

  it('shows denied workspace access without membership management controls', async () => {
    const auth = state();
    vi.mocked(auth.request).mockRejectedValue(new ApiError(403, 'MEMBERSHIP_REQUIRED', 'Not a member.'));
    view(auth, '/workspaces/other-association');
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have access');
    expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
  });

  it('removes admin controls when refreshed server permissions change', async () => {
    const auth = state();
    vi.mocked(auth.request)
      .mockResolvedValueOnce({ associationId: 'a1', name: 'Association A', slug: 'assoc-a', role: 'ADMIN' })
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({ associationId: 'a1', name: 'Association A', slug: 'assoc-a', role: 'MEMBER' });
    view(auth);
    await screen.findByRole('button', { name: 'Add member' });
    fireEvent.click(screen.getByRole('button', { name: 'Refresh workspace' }));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Manage members' })).not.toBeInTheDocument());
    expect(auth.refreshUser).toHaveBeenCalled();
  });

  it('links account memberships to their workspace', () => {
    const auth = state();
    view(auth, '/account');
    expect(screen.getByRole('link', { name: 'Open workspace' })).toHaveAttribute('href', '/workspaces/assoc-a');
  });
});
