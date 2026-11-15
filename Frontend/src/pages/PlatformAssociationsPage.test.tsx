import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import PlatformAssociationsPage from './PlatformAssociationsPage';

function state(platformAdmin: boolean): AuthState {
  return {
    user: { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin },
    request: vi.fn().mockResolvedValue([]),
    register: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn().mockResolvedValue(undefined),
  };
}
function view(auth: AuthState) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <PlatformAssociationsPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}
function submit() {
  fireEvent.change(screen.getByLabelText('Association name'), { target: { value: 'Tech Society' } });
  fireEvent.change(screen.getByLabelText('URL slug'), { target: { value: 'tech-society' } });
  fireEvent.change(screen.getByLabelText('First administrator’s email'), { target: { value: 'owner@example.test' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create association' }));
}

describe('platform association administration', () => {
  it('does not show the creation form or request directory data to ordinary accounts', () => {
    const auth = state(false);
    view(auth);
    expect(screen.getByText('This area is available to AIssociation platform administrators.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create association' })).not.toBeInTheDocument();
    expect(auth.request).not.toHaveBeenCalled();
  });

  it('creates an association through the API and updates the directory', async () => {
    const auth = state(true);
    vi.mocked(auth.request).mockResolvedValueOnce([]).mockResolvedValueOnce({
      id: 'a1',
      name: 'Tech Society',
      slug: 'tech-society',
      createdAt: '2026-11-16T00:00:00Z',
    });
    view(auth);
    await screen.findByText('No associations yet. Create the first one using the form.');
    submit();
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tech Society was created and its first administrator was assigned.',
    );
    expect(auth.request).toHaveBeenNthCalledWith(2, '/api/admin/associations', {
      method: 'POST',
      body: JSON.stringify({ name: 'Tech Society', slug: 'tech-society', initialAdminEmail: 'owner@example.test' }),
    });
    expect(screen.getByText('Tech Society')).toBeInTheDocument();
    expect(screen.getByLabelText('Association name')).toHaveValue('');
    expect(auth.refreshUser).toHaveBeenCalled();
  });

  it('shows server rejection and preserves the entered details', async () => {
    const auth = state(true);
    vi.mocked(auth.request)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new ApiError(409, 'SLUG_EXISTS', 'An association with this URL slug already exists.'));
    view(auth);
    await screen.findByText('No associations yet. Create the first one using the form.');
    submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('An association with this URL slug already exists.');
    expect(screen.getByLabelText('Association name')).toHaveValue('Tech Society');
    expect(screen.queryByText('Tech Society', { selector: 'strong' })).not.toBeInTheDocument();
  });

  it('keeps a successful creation successful when membership refresh fails', async () => {
    const auth = state(true);
    vi.mocked(auth.request).mockResolvedValueOnce([]).mockResolvedValueOnce({
      id: 'a1',
      name: 'Tech Society',
      slug: 'tech-society',
      createdAt: '2026-11-16T00:00:00Z',
    });
    vi.mocked(auth.refreshUser).mockRejectedValueOnce(new TypeError('Network unavailable'));
    view(auth);
    await screen.findByText('No associations yet. Create the first one using the form.');
    submit();
    await waitFor(() => expect(auth.refreshUser).toHaveBeenCalled());
    expect(screen.getByRole('status')).toHaveTextContent('was created');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
