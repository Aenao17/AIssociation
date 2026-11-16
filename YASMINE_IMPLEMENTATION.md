# Yasmine's FR-10 / FR-11 implementation

Current completion status: [requirement-by-requirement audit](docs/yasmine-completion-audit.md).
Core code is implemented; integration, real-user research and final validation
remain outstanding as listed there.

Organizers can save event drafts, edit and publish directly; visitors can discover
published events across associations and open public details without signing in.
Association presentation pages show description, website and published events.
List and agenda share the same filters, pagination and data.

## Where to start

- `/home`: landing page with discovery/association links and account actions.
- `/events`: discovery and agenda, association/UTC-date filters.
- `/associations`, `/associations/{slug}`: directory and public presentation.
- `/events/{id}`: public event details, deadline, timezone and optional capacity.
- `/workspaces/{slug}/events`: authorized event management and admin profile panel.

The backend `events` package owns validation, organizer checks, lifecycle and public
queries. `Frontend/src/events` owns the editor, discovery and details. V3 stores
events/profile fields; V1/V2 remain unchanged. Public DTOs are deliberately separate
from organizer projections. Existing identity and project permission helpers are
reused; a manager in another project cannot manage this project's events.

An event can be standalone or linked to a project; several events per project are
supported. Drafts are private. Publishing needs a future start/registration deadline.
Cancelled/completed events are read-only; completed requires the end to have passed.
Published capacity cannot be reduced until Denisa's seat ledger is integrated.
The [API contract](docs/contracts/events-and-public-discovery.md) records these local
defaults and the integration requirements; no teammate approval is claimed.

## Verification this session

- **57 backend tests pass**, including 12 new discovery/event tests. Coverage:
  standalone HTTP create/edit/publish, project multiplicity, rejected organizer
  permissions, cross-association IDs/FK, revoked membership, draft privacy,
  date filters/pagination, deadline extension, capacity/lifecycle, profile validation
  and V2-to-V3 migration preserving existing association data.
- **Four desktop/mobile Playwright checks pass** against real local UI/API/H2
  persistence: draft hidden anonymously, edit, publish, public listing, agenda,
  association feed and direct detail route, plus two published events per project,
  association/date filtering and denied unrelated-manager access. Screenshots
  inspected locally. The expanded browser suite was checked during the completion audit.
- Frontend app/e2e TypeScript checks, touched-file ESLint/Prettier, production Vite
  build and diff whitespace check pass. Existing large-bundle warning remains.
- UI unit tests are authored, but Vitest cannot execute with the installed Node
  22.12/jsdom combination. Repository requires Node 26. No UI unit pass is claimed.
- Ordinary Maven verify encounters a sandbox JDK archive-close AccessDenied error.
  The test sources were compiled together using cached workspace-local dependencies;
  executing Surefire directly then passed the complete suite. See the engineering log.
- PostgreSQL verification and hosted routes remain unverified: Docker/PostgreSQL
  executables were unavailable. The browser run used a disposable H2 file database,
  not the user's application database. See [reproduction](docs/testing-events.md).

## Handoffs and limits

Denisa still supplies real registration/attendance. Public detail has a registration
region and stable event DTO; it does not implement duplicate registration logic.
Her backend must re-read status/deadline/capacity while holding the event row lock.
Gabriel should review the public presentation editing split; Raul must reconcile
the locally allocated V3 before shared application.

The [UX flow/wireframes and traceability](docs/plans/yasmine-work-package.md) describe
the screens. No real-user usability findings, independent/no-GenAI feature or
student review are claimed. This work is AI-assisted and needs Yasmine's review
and explanation for assessment. No commit/push/merge/deploy was made; branch
creation remains blocked by read-only `.git`. The original lockfile edit is preserved.

## Suggested demonstration

1. Administrator creates a standalone draft. Anonymous detail/list cannot see it.
2. Edit required fields, optional capacity and deadline; publish without approval.
3. Open association feed, combined list, agenda and direct details anonymously.
4. Project manager creates two events for one project. Another project's manager
   and another association's member cannot edit them.
5. Extend registration deadline; public DTO changes. Show cancellation notice and
   rejected terminal edits. Hand the event ID/fields to Denisa's registration flow.
