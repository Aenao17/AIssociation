# Platform administration: association creation

This interface creates live association records through the API. It does not seed
fictional associations in migrations and does not create default passwords.

## Access model and first operator

Platform administrators manage the AIssociation platform. An association's `ADMIN`
membership does not confer platform privileges, and platform access does not
implicitly grant membership in every association.

The operator configures `PLATFORM_ADMIN_USER_IDS` as a comma-separated list of
existing account UUIDs. Empty means nobody has platform-admin access. Invalid
UUID configuration fails startup. Roles are resolved server-side; public signup
cannot request this privilege. Revoking a UUID takes effect after the backend
restarts/redeploys with the new configuration, including for existing tokens.

Setup procedure for a trusted operator:

1. Register the intended platform administrator through the normal account flow.
2. Sign in and obtain that account's `id` from authenticated `GET /api/me`.
3. Verify the account belongs to the intended operator. Do not authorize an
   unverified account merely because its email resembles an administrator's.
4. Set `PLATFORM_ADMIN_USER_IDS` to that UUID in Render's backend environment,
   then redeploy/restart. Local Docker Compose accepts the same variable.
5. Sign in again or refresh memberships/current-user data. `/api/me` now includes
   `"platformAdmin": true` and the account page shows Platform administration.
6. Open `/admin/associations`.

UUID-based operator configuration is used instead of email-based auto-promotion,
because email verification is not implemented in the initial account slice.
There is no public first-user-is-admin rule, platform-admin toggle, or endpoint
allowing a participant to promote themselves. Initial operator configuration
requires trusted access to the hosting environment.

## Endpoints

Both require a valid bearer session AND platform-admin authorization.

- GET `/api/admin/associations`: association directory, including UUID, name,
  slug and creation timestamp. No membership records or passwords are returned.
- POST `/api/admin/associations`: create association and first `ADMIN` membership
  atomically, returning 201 and the created association.

Example body:

```json
{
  "name": "Student Tech Society",
  "slug": "student-tech-society",
  "initialAdminEmail": "existing-account@example.com"
}
```

Name: trimmed/nonblank/max 150 characters. Slug: trimmed, lowercased, max 100,
letters/numbers separated by single hyphens, unique across associations. Initial
administrator email: normalized and validated, referencing an existing account.
The selected account receives association `ADMIN` membership, not platform access.
The platform operator is not automatically added as a member.

Errors follow the identity contract: 400 validation/invalid JSON, 401 missing or
invalid session, 403 `PLATFORM_ADMIN_REQUIRED`, 404 `INITIAL_ADMIN_NOT_FOUND`,
409 `SLUG_EXISTS`. Failure leaves no partial association/membership pair. The UI
preserves input on rejection and supports refreshing the directory.

No schema migration is needed: this feature uses the shared V1 identity tables.
Association editing/deletion, member administration, and workspace navigation are
separate slices. Association creation itself does not provision external domains
or third-party resources.
