# Engineering evidence log

## 16 November 2026 — Yasmine completion audit

- Reviewed the current source, routes, event/profile schemas, shared permissions,
  DTOs, recorded tests and team-plan deliverables. Core FR-10/FR-11 code exists;
  registration detail integration is still a notice, and final delivery includes
  unfinished real-user research, team decisions and validation. Wrote the explicit
  completion audit and owner UX-feedback register; corrected stale homepage-feed
  wording in the plan. Owner feedback is not claimed as independent user research.
- Added a real project browser scenario: administrator API creates a project;
  appointed manager UI creates/publishes two events; unrelated manager cancel is
  HTTP 403; public association/date filters, list/agenda and association feed agree.
  Anonymous contexts now retain each test's viewport for actual mobile coverage.
- Current expanded Playwright run: **4 passed** (standalone/project on Desktop and
  Pixel 7), 21.2s, real local UI/API and isolated H2 file. App/e2e TypeScript,
  scoped ESLint, test-file formatting and production build pass. No backend source
  changed; reviewed the earlier 57-test passing report rather than claiming a new run.
- Node remains 22.12, so UI unit execution is still blocked; Docker/psql unavailable,
  so no PostgreSQL verification. Registration integration, actual usability sessions,
  migration/profile/lifecycle agreement, hosted checks and human assessment evidence
  remain explicit. No extra core page was identified as missing. No commit/deploy;
  temporary verification servers stopped and original lockfile edit preserved.

## 16 November 2026 — Arrow navigation links

- Added shared BackLink with a decorative left-arrow SVG, softly tinted terracotta
  surface, rounded border, hover feedback and visible keyboard focus. Kept existing
  text labels and explicit destinations. Applied to public event/association Home
  and return links, event workspace return and workspace-to-account navigation.
  Minimum 44px target; hover motion respects reduced-motion preferences.
- Frontend TypeScript, touched-file ESLint and formatting passed. Cosmetic change;
  no business logic, authentication or history behavior changed, no tests added.

## 16 November 2026 — Dedicated discovery page

- At Yasmine's request, removed the embedded event feed from the homepage. The
  landing retains its introduction, Discover events/Explore associations links
  and header account actions. Discovery/list/agenda remain at /events; association
  feeds remain on public profiles. Homepage no longer fetches discovery data.
- Updated route documentation to match the simpler landing page.

## 16 November 2026 — Homepage account navigation

- Moved sign-in/create-account actions into an accessible header navigation: outlined
  Sign in, filled Create account; signed-in users see My account. Hero retains
  discovery/association actions. Shared BrandHeader accepts optional actions, so
  other pages retain their existing header. Mobile uses the brand symbol and compact
  44px-high controls with no hidden menu or extra tap for account access.
- TypeScript, touched-file ESLint/formatting and production build passed (existing
  bundle warning). Browser check verified header alignment/no horizontal overflow
  at 320/390/720/1365px and working sign-in/registration navigation. Public API data
  was intercepted for this layout check; no real authentication change is claimed.

## 16 November 2026 — Yasmine's visual refinements

- User requested matching dropdown/button colors, subtly richer fields, restrained
  text accents and aligned discovery content. Updated event/identity styles and
  homepage shell: centered shared-width discovery panel, equal-width responsive
  filter columns, terracotta selects, warm input surfaces, rounded controls,
  visible focus states, selected-view tint, accent headings and green date text.
  Scoped hero action centering to the hero so discovery controls align with its content.
- Frontend TypeScript and production build passed (existing bundle warning).
  Inspected desktop/mobile screenshots with synthetic intercepted event data;
  desktop discovery centered at 880px and mobile had no horizontal overflow.
  These are implementation checks, not real-user usability findings. No business
  logic or lockfile edit; no tests added for this reversible style change.

## 16 November 2026 — Yasmine's complete local FR-10/FR-11 slice

- Human direction: implement Yasmine's entire assigned workflow without further
  questions. All work below is AI-assisted; no independent feature or real-user
  usability findings are claimed. Codex produced code/docs/tests; Yasmine's review,
  explanation and actual corrections remain required.
- Added V3 event/profile persistence, event DTO/service/private and public APIs;
  draft/edit/publish/cancel/complete, strict standalone-admin/project-manager checks,
  live membership enforcement, scoped project FK, deadline/timezone validation,
  safe published-capacity rules and shared discovery filters/pagination.
- Added organizer editor/profile panel, homepage/public discovery, event details,
  association feeds and an agenda using the same records/query as list view. UTC
  schedule/filter inputs are explicit; public dates show the selected IANA timezone.
  No duplicate registration/attendance, recruitment, account or messaging module.
- V3 is a local migration allocation to reconcile with Raul before shared use.
  Profile presentation editing split requires Gabriel review; event registration
  slot and persisted status/deadline/capacity/row-lock contract handed off in docs
  for Denisa. No teammate approval or external message is claimed.
- Added/updated YASMINE_IMPLEMENTATION.md, event API contract, UX flow/wireframes,
  traceability, physical ERD/migration index, README and event verification runbook.
  Existing project V1->V2 migration test now explicitly targets V2, so later
  migrations do not invalidate its one-migration assertion.
- Verification: **57 backend tests pass**, zero failures/errors/skips: existing
  45 plus 3 public association and 9 event workflow tests. Fresh V1-V3 H2 SQL also
  passed independently. H2 V2->V3 upgrade retains existing association identity.
- Backend environment: JDK 21 with --release 17; Maven 3.10.0 offline. Ordinary
  compilation hits a sandbox ZipFS archive-close AccessDenied error. Copied the
  pre-existing .m2 cache into ignored workspace .maven/repository (no downloads),
  compiled main and test sources together with -proc:none via an ignored javac
  argument file; class files were emitted with no source diagnostics despite the
  archive-close exception. Then ran Maven surefire:test directly using that cache
  and process-local lock factory. Full passing output: Backend/target/events-final-tests.log.
- **Two Playwright workflows pass** (desktop and Pixel 7) against running real
  local UI/API/H2 file persistence. Covered create draft, anonymous draft privacy,
  edit capacity, direct publication, public list/agenda/association feed and direct
  detail route. Separate anonymous browser context; synthetic fixture accounts only.
  Inspected mobile agenda and desktop association screenshots in Frontend/test-results.
- Frontend app/e2e TypeScript checks, touched-file ESLint/Prettier, production
  Vite build and whitespace diff check pass. Large bundle warning remains.
  Node 22.12 vs required 26; preserve-symlinks flags avoid sandbox CLI path failure.
- Vitest attempt with inherited preservation flags/threads gets past initial
  resolution failure but jsdom's Undici requires unavailable createZstdDecompress
  on Node 22.12. Zero unit tests executed; do not claim a unit pass. No runtime
  dependency or lockfile change was made to work around this mismatch.
- PostgreSQL not verified: no Docker/PostgreSQL executable available. Browser
  fixture used only Backend/target/events-browser-db, never the application DB.
  No hosted verification or deployment. Test servers were stopped after checks.
- Read-only .git still prevents branch creation. All changes uncommitted on main;
  original Frontend/package-lock.json edit preserved. No push/merge/deploy or
  global installations/settings. Next: review defaults, reconcile V3 with Raul,
  integrate Denisa's real registration and run PostgreSQL/hosted/Node-26 checks.

## 16 November 2026 — Yasmine's public association foundation

- Human direction: inspect the current checkout, use the delivery plan and begin
  Yasmine's scope without further questions. Inspected README, routes, shared
  identity/project contracts and authorization helpers, migrations and tests.
- Added public association GET list/detail with an ID/name/slug DTO, anonymous
  directory/profile pages and homepage navigation. Added three backend privacy/
  access tests and five frontend tests. No migration, profile administration or
  registration implementation was added.
- Added the Yasmine work-package plan and public/event handoff in docs/plans and
  docs/contracts: current state, requirement mapping, proposed wireframes, event
  DTO/API/schema, unallocated migration and unresolved team decisions.
- This work is AI-assisted. Yasmine's independent feature remains unassigned;
  Gabriel's research form is excluded. No user findings, teammate approvals or
  human review are claimed. Tool: Codex; exact model belongs in session metadata.
- Git: main had an existing Frontend/package-lock.json edit, preserved. Branch
  creation failed because .git is read-only. Changes remain uncommitted; no
  commit, push, merge, deploy, global configuration change or external message.
- Passed: frontend TypeScript tsc --noEmit, ESLint and Prettier on the four
  touched TS/TSX files, production Vite build and git diff --check. Used existing
  local tools with Node --preserve-symlinks --preserve-symlinks-main. Node is
  22.12.0 versus requested 26; build reports the large-bundle warning.
- Blocked: Vitest fails before executing tests with EPERM resolving the Windows
  user directory and worker startup; zero tests ran. No browser/hosted checks ran.
- Backend offline verify attempted with existing Maven 3.10.0/JDK 21, Java 17
  target. IdeaProjects cache lacks Surefire 3.5.6. The .m2 cache reaches compilation
  but fails opening micrometer-commons-1.17.2.jar. No backend tests ran. A prior
  cache lock failure was avoided using Maven's process-local lock factory.
- Next: coordinate event migration allocation with Raul and event edge cases with
  Denisa, then implement standalone draft → edit → publish → public detail.
  Rerun backend/UI tests in a working environment before claiming acceptance.

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
