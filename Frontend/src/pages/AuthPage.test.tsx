import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import AuthPage from './AuthPage';

function view(auth: AuthState) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route path="/login" element={<AuthPage mode="login" />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

function state(): AuthState {
  return {
    user: null,
    login: vi.fn(),
    register: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    request: vi.fn(),
  };
}

function submit() {
  fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Alex' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'alex@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'example-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('registration screen', () => {
  it('registers an account and asks the user to sign in', async () => {
    const auth = state();
    view(auth);
    submit();
    await screen.findByRole('heading', { name: 'Welcome back' });
    expect(screen.getByRole('status')).toHaveTextContent('Account created. Sign in to continue.');
    expect(auth.register).toHaveBeenCalledWith({
      displayName: 'Alex',
      email: 'alex@example.com',
      password: 'example-password',
    });
    expect(auth.login).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('keeps the form open and explains a duplicate-account rejection', async () => {
    const auth = state();
    vi.mocked(auth.register).mockRejectedValue(
      new ApiError(409, 'EMAIL_EXISTS', 'An account with this email already exists.'),
    );
    view(auth);
    submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.');
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled();
  });
});
