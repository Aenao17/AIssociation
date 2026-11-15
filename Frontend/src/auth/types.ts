export interface Membership {
  associationId: string;
  associationName: string;
  associationSlug: string;
  role: 'MEMBER' | 'ADMIN';
}

export interface CurrentUser {
  id: string;
  displayName: string;
  email: string;
  memberships: Membership[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: { id: string; displayName: string; email: string; createdAt: string };
}

export interface RegisterInput {
  displayName: string;
  email: string;
  password: string;
}
