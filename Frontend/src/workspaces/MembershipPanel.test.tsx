import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import MembershipPanel from './MembershipPanel';
import type { AssociationMember } from './types';

const admin: AssociationMember = {
  id: 'm1',
  userId: 'u1',
  displayName: 'Alex',
  email: 'alex@example.test',
  role: 'ADMIN',
  createdAt: '2026-11-16T00:00:00Z',
};
const member: AssociationMember = {
  id: 'm2',
  userId: 'u2',
  displayName: 'Sam',
  email: 'sam@example.test',
  role: 'MEMBER',
  createdAt: '2026-11-16T00:00:00Z',
};
function state(): AuthState {
  return {
    user: { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin: false },
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    request: vi.fn(),
  };
}
function view(auth: AuthState, changed = vi.fn().mockResolvedValue(true)) {
  render(
    <AuthContext.Provider value={auth}>
      <MembershipPanel associationId="a1" onChange={changed} />
    </AuthContext.Provider>,
  );
  return changed;
}

describe('membership administration', () => {
  it('adds an existing user through the association API and refreshes the roster', async () => {
    const auth = state();
    vi.mocked(auth.request)
      .mockResolvedValueOnce([admin])
      .mockResolvedValueOnce(member)
      .mockResolvedValueOnce([admin, member]);
    const changed = view(auth);
    await screen.findByText('Alex (you)');
    fireEvent.change(screen.getByLabelText('Account email'), { target: { value: 'sam@example.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add member' }));
    await screen.findByText('Sam');
    expect(auth.request).toHaveBeenNthCalledWith(2, '/api/associations/a1/members', {
      method: 'POST',
      body: JSON.stringify({ email: 'sam@example.test', role: 'MEMBER' }),
    });
    expect(changed).toHaveBeenCalled();
    expect(screen.getByLabelText('Account email')).toHaveValue('');
  });

  it('disables removal/demotion of the only administrator in the UI', async () => {
    const auth = state();
    vi.mocked(auth.request).mockResolvedValue([admin]);
    view(auth);
    await screen.findByText('Alex (you)');
    expect(screen.getByLabelText('Role for alex@example.test')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Remove alex@example.test' })).toBeDisabled();
  });

  it('requires explicit confirmation before removing a member', async () => {
    const auth = state();
    vi.mocked(auth.request)
      .mockResolvedValueOnce([admin, member])
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce([admin]);
    view(auth);
    await screen.findByText('Sam');
    fireEvent.click(screen.getByRole('button', { name: 'Remove sam@example.test' }));
    expect(auth.request).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('button', { name: 'Confirm removal' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remove sam@example.test' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm removal' }));
    await waitFor(() => expect(screen.queryByText('Sam')).not.toBeInTheDocument());
    expect(auth.request).toHaveBeenNthCalledWith(2, '/api/associations/a1/members/m2', { method: 'DELETE' });
  });

  it('shows a rejected role change without claiming success', async () => {
    const auth = state();
    vi.mocked(auth.request)
      .mockResolvedValueOnce([admin, { ...member, role: 'ADMIN' }])
      .mockRejectedValueOnce(new ApiError(409, 'LAST_ADMIN_REQUIRED', 'Appoint another administrator first.'));
    const changed = view(auth);
    await screen.findByText('Sam');
    fireEvent.change(screen.getByLabelText('Role for sam@example.test'), { target: { value: 'MEMBER' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save role for sam@example.test' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Appoint another administrator first.');
    expect(changed).not.toHaveBeenCalled();
    expect(screen.queryByText('Member role updated.')).not.toBeInTheDocument();
  });
});
