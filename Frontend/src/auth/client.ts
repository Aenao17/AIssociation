import { apiUrl } from '../api';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(apiUrl(path), { ...options, headers, credentials: 'omit' });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as {
      code?: string;
      message?: string;
      fieldErrors?: Record<string, string>;
    };
    throw new ApiError(
      response.status,
      error.code ?? 'REQUEST_FAILED',
      error.message ?? 'Request failed. Try again.',
      error.fieldErrors,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
