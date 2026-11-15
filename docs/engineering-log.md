# Engineering evidence log

## 15 November 2026 — accounts/current-user slice

- Owner: Gabriel. Code and documentation prepared with Codex assistance; student
  review, understanding and technical defense remain required.
- Tool: Codex; record the exact model identifier from the app/session metadata
  when preparing the GenAI declaration (not inferred here).
- Purpose: implement the approved identity API, frontend account flow, shared
  identity contract, schema migration, tests, and authentication decision record.
- Human direction: keep participant accounts separate from association membership;
  use UUID identities; use revocable opaque bearer sessions and a memory-only
  frontend token; reserve the research form for independent implementation.
- Verification: backend Maven verify (15 tests), frontend unit tests (8), frontend
  build/lint, Docker image/startup and PostgreSQL-backed HTTP register/login/me/
  logout/revoked-token checks. Browser verification exercised login, membership
  refresh, logout and navigation to the registration screen.
- Relevant files: `docs/contracts/identity-and-current-user.md`, ADR 0001,
  identity migration/services/controllers and frontend auth/pages/tests.
- Limits: association bootstrap/admin screens, membership mutation, password reset,
  email verification and refresh-token flows are not delivered by this slice.
  The Playwright end-to-end suite has not been run; browser checks above were
  manual automation of the local application.
- Follow-up: coordinate the V1/shared IDs with Raul and the authentication helper
  with other workflow owners before they implement dependent features.
