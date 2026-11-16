import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';

import ResearchResponsesPage from './ResearchResponsesPage';
import { emptyAnswers } from './questions';

function state(platformAdmin: boolean): AuthState {
  return {
    user: { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin },
    request: vi.fn().mockResolvedValue([]),
    register: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  };
}

function view(auth: AuthState) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <ResearchResponsesPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('research response administration', () => {
  it('does not load private responses for ordinary accounts', () => {
    const auth = state(false);
    view(auth);
    expect(screen.getByText('This area is available to AIssociation platform administrators.')).toBeInTheDocument();
    expect(auth.request).not.toHaveBeenCalled();
  });

  it('shows stored answers for a platform administrator', async () => {
    const auth = state(true);
    vi.mocked(auth.request).mockResolvedValue([
      {
        id: 'r1',
        submittedAt: '2026-11-16T12:00:00Z',
        answers: {
          ...emptyAnswers(),
          associationName: 'Example association',
          suggestions: '<b>Plain text</b>',
          tasks: ['Meetings'],
        },
      },
    ]);
    view(auth);
    expect(await screen.findByText(/Example association/, { selector: 'summary' })).toBeInTheDocument();
    expect(screen.getByText('<b>Plain text</b>')).toBeInTheDocument();
    expect(screen.getByText('Meetings')).toBeInTheDocument();
    expect(auth.request).toHaveBeenCalledWith('/api/admin/research/responses?page=0');
  });

  it('can retry a failed request', async () => {
    const auth = state(true);
    vi.mocked(auth.request).mockRejectedValueOnce(new Error('Network')).mockResolvedValueOnce([]);
    view(auth);
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load responses');
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }));
    expect(await screen.findByText('No responses on this page yet.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
