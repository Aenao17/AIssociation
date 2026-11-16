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

## 16 November 2026 — restrained layout refinement

- Human direction: clean, professional product design with restrained layout.
- Codex-assisted changes: open centered homepage, clearer action hierarchy,
  quieter empty states, consistent spacing/controls and reduced-motion support.
  Approved logo, palette and typography retained; guidance added for teammates.
- Verification: frontend lint/type checks and production build, plus visual
  desktop and 390px mobile review. No backend changes.

## 16 November 2026 — association workspaces and membership administration

- Owner: Gabriel; Codex-assisted implementation of the agreed owned workflow.
- Changes: workspace entry, admin-only membership add/role-change/removal,
  confirmation, refreshed identity/permissions, and reusable AssociationAccess.
- Human requirements: every member can enter their association, association
  administrators manage only their own membership, and the last admin remains.
- Engineering decision: serialize membership writes with a parent-association
  row lock and check current actor permissions after acquiring that lock.
- Verification: Maven verify (31 tests), frontend tests (20), lint/type checks
  and production build. Browser checks covered admin add/member entry and mobile
  controls. PostgreSQL API checks covered cross-association denial, private roster,
  immediate access revocation and simultaneous self-demotions (200 + 409, one
  administrator retained). Temporary local fixtures were removed afterward.
- Handoff: docs/contracts/association-workspaces.md. No new migration or hosted
  data changes. Project/task/chat owners must apply both membership and their
  own finer-grained rules. Playwright suite not run.

## 16 November 2026 — Raul's project-workflow inspection and planning

- Owner: Raul; inspection and draft documentation prepared with Codex assistance.
  No project-domain application code or SQL was generated in this session.
- Human direction: FR-04–FR-07 only; preserve shared identity/access conventions,
  coordinate hierarchy/contracts and migrations, and identify independent work
  before generating its implementation. The no-GenAI feature question is pending;
  Gabriel's research form remains excluded.
- Inspected local revision `76e3238`, existing source/tests/migrations/contracts,
  README, Git state and the supplied team-plan HTML. Created local branch
  `feature/raul-projects-recruitment`; left the pre-existing untracked root lockfile
  untouched. No commit, push, merge, deployment or teammate message was made.
- Artifacts: `docs/plans/raul-work-package.md`, draft
  `docs/contracts/projects-and-reporting.md`, and `docs/data-model.md` with the
  actual V1 ERD and migration index. Proposed domain choices are labeled open;
  the current-model diagram does not claim project tables already exist.
- Verification: cached Maven wrapper offline verify passed (31 H2 tests), frontend
  unit tests passed (20), production build passed. Frontend lint passed ESLint
  and TypeScript stages but failed Prettier on 28 existing files. Initial sandbox
  wrapper/Vite failures were retried with permission. No PostgreSQL, Playwright,
  browser or deployed verification was run.
- Toolchain caveat: Node 24.14.0 differs from specified 26; successful Maven used
  Java 21.0.12.1 while the POM targets 17. Settings/dependencies were not changed.
- Review required: no-GenAI selection; other pending applications after acceptance;
  root/department reporting; removal/rejoining/history; department audiences;
  member selector and first-slice fields. No human acceptance is claimed.
- Tool: Codex. Exact model identifier must be taken from session metadata for
  the GenAI declaration; it is not inferred here. Raul's review and explanation
  of the artifacts remain pending. Next slice: scoped project creation and
  initial manager appointment across UI/API/persistence.

## 16 November 2026 — Raul's FR-04–FR-07 implementation

- Human direction: implement the complete assigned work package without further
  permission questions and provide a brief Markdown explanation. All delivered
  work is AI-assisted; no independent assessment feature or human review is claimed.
- Added project/recruitment/reporting APIs, Flyway V2, workspace-linked UI,
  transactional selection and shared ProjectAccess helpers. Reused Gabriel's
  identities, membership checks, sessions and global errors. No tasks, events,
  attendance, chat or research-form implementation was added.
- Decisions: close other pending applications after acceptance; root project
  managers; explicit department-manager appointments; project-local labels;
  one extendable call; no withdrawal/reapplication. Preserve history and revoke
  access by nulling original membership references on removal; rejoin does not
  silently restore project access. Choices are documented, not teammate approvals.
- Verification: Java 17 Maven verify passed 45 tests; all 14 new workflow tests
  passed on PostgreSQL 17, including concurrent acceptance, direct constraint
  checks and V1 upgrade retaining an existing account. Frontend 26 tests, build,
  ESLint and end-to-end typecheck passed. Desktop/mobile real browser workflows
  passed with separate accounts. Initial browser locator and migration-test
  identifier-quoting issues were corrected and rechecked.
- Artifacts: RAUL_IMPLEMENTATION.md explains what/why; project API contract,
  physical ERD/migration index, requirement mapping and verification runbook
  describe the implemented behavior and consumer integration points.
- Limits: Node 24 used locally versus specified 26; existing global formatting
  failures and build bundle-size warning remain. No hosted verification, commit,
  push, merge or external teammate message. The pre-existing root lockfile remains
  untouched. V2 allocation must be reconciled before shared application.
- Tool: Codex; exact model metadata belongs in the eventual GenAI declaration.
  Raul must review/explain the implementation and record actual human corrections.
- Final checks repeated after review: all 45 Java 17 tests and packaging passed;
  all 14 PostgreSQL tests and both desktop/mobile browser flows passed. A Windows
  JAR lock from the temporary backend was resolved by stopping it before packaging.
  Final global lint reports 26 untouched formatting failures; touched-file formatting
  passes. Temporary verification servers, container and anonymous test volume were
  removed; the application's existing database was not used or changed.

## 16 November 2026 — research questionnaire

- Owner: Gabriel; Codex-assisted questionnaire implementation, tests and documentation.
- Human direction: build the agreed research form with simple syntax and no new
  inline code comments. AI implementation assistance supersedes the earlier
  independent-feature reservation; a different independent feature is needed.
- Changes: public `/research`, platform-only `/admin/research`, bounded validated
  API, Flyway V3, optional written/contact fields, and idempotent submission retry.
- Verification: Maven verify passed 49 tests; frontend passed 33 tests, lint/type
  checks and production build. Browser submission persisted to isolated PostgreSQL
  17; local HTTP checks verified 401/403/200 read access and no duplicate on retry.
- Integration adjustment: the existing project migration test now explicitly
  targets V2 so its expected one-step V1-to-V2 upgrade remains valid after V3.
- Limits: no production deployment or real interviews/responses claimed. Test
  fixtures are synthetic. Existing bundle-size warning remains. No full Playwright
  suite run. Exact model identifier must come from session metadata.
- Contract: docs/contracts/research-form.md. Review code and explain the workflow
  before presenting it as an individual contribution.
