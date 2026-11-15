import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';

const user = { id: 'u1', displayName: 'Alex', email: 'alex@example.com', memberships: [], platformAdmin: false };
const loginResult = {
  accessToken: 'test-token',
  expiresAt: new Date(Date.now() + 3600000).toISOString(),
  tokenType: 'Bearer',
  user,
};
function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}

function Probe() {
  const auth = useAuth();
  return (
    <>
      <p>{auth.user ? auth.user.email : 'signed out'}</p>
      <button
        onClick={() => {
          void auth.login('alex@example.com', 'example-password');
        }}
      >
        login
      </button>
      <button
        onClick={() => {
          void auth.logout().catch(() => undefined);
        }}
      >
        logout
      </button>
      <button
        onClick={() => {
          void auth.refreshUser().catch(() => undefined);
        }}
      >
        refresh
      </button>
    </>
  );
}

async function signedIn(fetchMock: ReturnType<typeof vi.fn>) {
  fetchMock.mockResolvedValueOnce(json(loginResult)).mockResolvedValueOnce(json(user));
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  fireEvent.click(screen.getByText('login'));
  await screen.findByText(user.email);
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('account session', () => {
  it('loads /me using the token and keeps credentials out of browser storage', async () => {
    const fetchMock = vi.fn();
    const storageWrites = vi.spyOn(Storage.prototype, 'setItem');
    vi.stubGlobal('fetch', fetchMock);
    await signedIn(fetchMock);
    const options = fetchMock.mock.calls[1][1] as RequestInit;
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer test-token');
    expect(options.credentials).toBe('omit');
    expect(storageWrites).not.toHaveBeenCalled();
  });

  it('revokes the server session before clearing the interface', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await signedIn(fetchMock);
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    fireEvent.click(screen.getByText('logout'));
    await screen.findByText('signed out');
    expect(fetchMock.mock.calls[2][0]).toMatch(/\/api\/auth\/logout$/);
  });

  it('keeps a failed logout retryable rather than claiming revocation', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await signedIn(fetchMock);
    fetchMock.mockRejectedValueOnce(new TypeError('Network unavailable'));
    fireEvent.click(screen.getByText('logout'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(screen.getByText(user.email)).toBeInTheDocument();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    fireEvent.click(screen.getByText('logout'));
    await screen.findByText('signed out');
  });

  it('clears an invalidated session after a protected request returns 401', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await signedIn(fetchMock);
    fetchMock.mockResolvedValueOnce(json({ code: 'UNAUTHENTICATED', message: 'Sign in again.' }, 401));
    fireEvent.click(screen.getByText('refresh'));
    await screen.findByText('signed out');
  });

  it('refreshes memberships from the server', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await signedIn(fetchMock);
    fetchMock.mockResolvedValueOnce(json({ ...user, email: 'updated@example.com' }));
    fireEvent.click(screen.getByText('refresh'));
    await screen.findByText('updated@example.com');
  });
});
