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

## 16 November 2026 — platform association creation

- Owner: Gabriel; implementation prepared with Codex assistance for review.
- Purpose: platform-admin interface and API to create live association records
  and assign an existing account as initial association administrator.
- Human direction: use an interface/API rather than seeding demo organizations
  in migrations; keep platform and association administration separate.
- Implementation choice: server-configured trusted UUID allowlist, empty by
  default, plus an additive current-user platformAdmin flag. No public
  privilege assignment or default administrator credentials.
- Verification: Maven verify (22 tests), frontend tests (12), build/lint, Docker
  startup, browser creation against PostgreSQL, duplicate/missing-account errors,
  and denial of platform operations to an association administrator. Temporary
  local records and test allowlist removed after verification.
- Exact Codex model identifier: record from session metadata when completing
  the GenAI report. No hosted operator privileges have been configured.

## 16 November 2026 — shared brand kit and product styling

- Owner: Gabriel; Codex-assisted implementation using the supplied branding kit.
- Human direction: version the branding guide for the team and apply it to the
  current product. Existing supplied SVG/PNG artwork was reused, not regenerated.
- Changes: responsive shared logo header; warm paper/ink/terracotta tokens;
  Arial interface/Georgia landing typography; consistent controls/focus states;
  branded favicon/app manifest/social metadata; repeatable local asset sync.
- Shared kit: HTML/PDF guide, approved reference source, vector/raster exports,
  and web assets. The frontend asset-sync script copies the approved files.
- Verification: frontend lint/type checks, 12 unit tests and production build;
  copied logo/token integrity, manifest/icon paths/dimensions, SVG parsing and
  social-card dimensions; visual desktop/390px mobile and keyboard-focus checks.
- Limits: existing backend behavior is unchanged; dark UI surfaces remain opt-in.
  Hosting changes await merge/deployment; use the checked-in runtime assets for production builds.
