import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode, ReactElement } from 'react';

import { AuthContext } from './AuthContext';
import { ApiError, apiRequest } from './client';
import type { CurrentUser, LoginResponse, RegisterInput } from './types';

interface Session {
  token: string;
  expiresAt: string;
  user: CurrentUser;
}

export function AuthProvider({ children }: { children: ReactNode }): ReactElement {
  const [session, setSession] = useState<Session | null>(null);
  const tokenRef = useRef<string | undefined>(undefined);
  const generation = useRef(0);

  const clearSession = useCallback(() => {
    generation.current += 1;
    tokenRef.current = undefined;
    setSession(null);
  }, []);

  useEffect(() => {
    if (!session) return;
    const checkExpiry = () => {
      if (Date.now() >= Date.parse(session.expiresAt) && tokenRef.current === session.token) clearSession();
    };
    const timer = window.setInterval(checkExpiry, 1000);
    return () => window.clearInterval(timer);
  }, [session, clearSession]);

  const register = useCallback(async (input: RegisterInput) => {
    await apiRequest('/api/auth/register', { method: 'POST', body: JSON.stringify(input) });
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const current = ++generation.current;
    const result = await apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    try {
      const user = await apiRequest<CurrentUser>('/api/me', {}, result.accessToken);
      if (generation.current !== current) {
        await apiRequest('/api/auth/logout', { method: 'POST' }, result.accessToken);
        return;
      }
      tokenRef.current = result.accessToken;
      setSession({ token: result.accessToken, expiresAt: result.expiresAt, user });
    } catch (error) {
      // Avoid retaining an unusable session when identity loading fails.
      await apiRequest('/api/auth/logout', { method: 'POST' }, result.accessToken).catch(() => undefined);
      throw error;
    }
  }, []);

  const request = useCallback(
    async <T,>(path: string, options?: RequestInit) => {
      const token = tokenRef.current;
      if (!token) throw new ApiError(401, 'UNAUTHENTICATED', 'Please sign in.');
      try {
        return await apiRequest<T>(path, options, token);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401 && tokenRef.current === token) clearSession();
        throw error;
      }
    },
    [clearSession],
  );

  const logout = useCallback(async () => {
    const token = tokenRef.current;
    if (!token) return;
    try {
      await apiRequest('/api/auth/logout', { method: 'POST' }, token);
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
    if (tokenRef.current === token) clearSession();
  }, [clearSession]);

  const refreshUser = useCallback(async () => {
    const token = tokenRef.current;
    const user = await request<CurrentUser>('/api/me');
    if (tokenRef.current === token) setSession((previous) => (previous ? { ...previous, user } : null));
  }, [request]);

  return (
    <AuthContext.Provider value={{ user: session?.user ?? null, login, register, logout, refreshUser, request }}>
      {children}
    </AuthContext.Provider>
  );
}
