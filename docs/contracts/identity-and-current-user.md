# Identity and current-user API

Owner: Gabriel. First account slice, 15 November 2026.

This contract provides shared identity and membership references. Project roles and
reporting hierarchy are separate work packages. Workspace/member endpoints are
documented in [the association contract](association-workspaces.md). A participant account is not association membership.

## Endpoints

| Method/path | Authentication | Result |
| --- | --- | --- |
| POST `/api/auth/register` | Public | 201 public account identity |
| POST `/api/auth/login` | Public | 200 bearer token, expiry and public identity |
| GET `/api/me` | Bearer | 200 current account and current membership list |
| POST `/api/auth/logout` | Bearer | 204, current session revoked |

Register body:

```json
{"displayName":"Alex","email":"alex@example.com","password":"a-long-example-password"}
```

Display name is trimmed, nonblank, at most 100 characters. Email is trimmed,
lowercased with `Locale.ROOT`, syntactically validated and at most 254 characters.
Passwords are not trimmed: registration requires 10–72 characters and at most
72 UTF-8 bytes (BCrypt input limit). Do not log request passwords.

Register response:

```json
{"id":"uuid","displayName":"Alex","email":"alex@example.com","createdAt":"ISO-8601 instant"}
```

Registration does not sign in or create any membership. Login body contains
`email` and `password`. Login normalizes email in the same way.

Login response:

```json
{
  "accessToken":"opaque random token",
  "tokenType":"Bearer",
  "expiresAt":"ISO-8601 instant",
  "user":{"id":"uuid","displayName":"Alex","email":"alex@example.com","createdAt":"ISO-8601 instant"}
}
```

Send `Authorization: Bearer <accessToken>` on protected calls. Current-user response:

```json
{
  "id":"uuid",
  "displayName":"Alex",
  "email":"alex@example.com",
  "platformAdmin": false,
  "memberships":[
    {"associationId":"uuid","associationName":"Example association","associationSlug":"example","role":"MEMBER"}
  ]
}
```

An unassigned participant receives `memberships: []`. Association roles are
`MEMBER` and `ADMIN`; project responsibilities are not account-wide roles.
The additive `platformAdmin` boolean reports operator-configured platform access;
it is separate from association membership roles and defaults to false. See
[platform administration](platform-association-admin.md). Memberships are read from the database on each `/api/me` call. Another user's
memberships are never included. A membership list is UI information, not proof
of permission for future backend operations: recheck the current association
and project permissions in every endpoint.

## Errors and sessions

Errors use `{"code":"...","message":"...","fieldErrors":{}}`.
Validation errors may include field names mapped to explanations; rejected
password values are never echoed. HTTP 400 means invalid input/JSON, 409 means
`EMAIL_EXISTS`, 401 means `INVALID_CREDENTIALS` (login) or `UNAUTHENTICATED`
(protected calls), and 403 means authorization failed.

Wrong-password and unknown-account login failures use the same message.
CORS rejections are handled by Spring's CORS layer and may be plain-text 403;
clients should retain a generic fallback for non-JSON failures.

Sessions last eight hours by default, configured by `AUTH_SESSION_DURATION`
(Java duration syntax, e.g. `PT8H`). Tokens are 32 secure-random bytes encoded
as unpadded base64url; only their SHA-256 hashes are stored. Sessions are
checked server-side and expiration is enforced on every authenticated request.
Logout revokes that specific session, leaving other logins intact. A repeated
logout with the revoked token returns 401; the frontend treats this as signed out.

The client keeps its token in React memory, never local/session storage or a
cookie. A full-page refresh requires sign-in again. Client navigation retains
the session. There is no refresh-token or password-reset flow in this slice.
The frontend retains a failed network logout for retry instead of claiming
server revocation succeeded.

## Shared database foundation

Migration `V1__identity_foundation.sql` creates:

- `app_users`: UUID `id`, `display_name`, unique normalized `email`,
  `password_hash`, `created_at`.
- `auth_sessions`: UUID `id`, `user_id`, unique `token_hash`, `created_at`,
  `expires_at`.
- `associations`: UUID `id`, unique `slug`, `name`, `created_at`.
- `association_memberships`: UUID `id`, `association_id`, `user_id`, `role`,
  `created_at`; unique association/user pair.

These association tables establish IDs and response shapes; they do not seed
organizations or provide membership-management endpoints. Existing membership
rows represent current membership in this first slice. Coordinate removal/status
extensions with the workspace owner.

Raul coordinates future migrations. Reserve V1 for this foundation; do not edit
an applied migration. Add new migrations for profile, project or status fields.
Reference `app_users.id` rather than creating a parallel user identity. Identity
persistence uses Spring JDBC; other modules can use JPA over the shared schema.
Never serialize identity/session persistence data directly into API responses.

## Backend and frontend integration

Backend: inject `@AuthenticationPrincipal AuthenticatedUser user` to get the
trusted `id()` and current `sessionId()`. Do not read a client-provided role or
user ID as authorization. Protected operations default to authenticated.
Only POST register/login, GET/HEAD health and GET `/api/public/**` are public.
Future public event/profile GET endpoints should use `/api/public/**`.
No private membership/project/roster endpoint belongs under that namespace.

`SecurityConfig` integrates the existing CORS source into the security chain,
so allowed preflights and 401 responses carry the appropriate CORS headers.
Basic login, form login, servlet sessions and cookie authentication are disabled.
CSRF cookies are not used because authentication requires an explicit bearer
header. Keep these choices consistent when adding Spring Security policies.

Frontend: wrap routes once in `AuthProvider`; use `useAuth()` for `user`,
`login`, `register`, `logout`, `refreshUser`, and `request<T>(path, options)`.
The request helper attaches the current bearer token and clears a session on
401. Public requests use `apiRequest` without a token. Use client-side navigation
rather than a full reload when retaining an in-memory session matters.
Routes: `/register`, `/login`, `/account`; public placeholder at `/home`.
Memberships link to `/workspaces/{slug}`; see the association contract for workspace entry and membership administration.

## Verification

Backend integration tests cover password hashing, email normalization/uniqueness,
current-user membership isolation and live changes, rejected/expired sessions,
logout scope, input validation, CORS and default-private endpoints. Tests use
Flyway on H2 in PostgreSQL mode; local Docker checks also exercise PostgreSQL.
Frontend tests cover session identity loading, no browser-storage token,
logout/retry, invalid-session clearing and membership refresh.
