import { createContext } from 'react';

import type { CurrentUser, RegisterInput } from './types';

export interface AuthState {
  user: CurrentUser | null;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  request: <T>(path: string, options?: RequestInit) => Promise<T>;
}

export const AuthContext = createContext<AuthState | undefined>(undefined);
