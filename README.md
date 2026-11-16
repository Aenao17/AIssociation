# AIssociation

UBB student association event management application — Team Stucanii.

## Monorepo

- `Backend/`: Java 17, Spring Boot 4.1.2, Maven wrapper, Spring MVC, validation,
  Spring Data JPA, PostgreSQL, and Actuator.
- `Frontend/`: Ionic React, TypeScript, and Vite.
- `compose.yaml`: local PostgreSQL and an optional containerized backend.

The application provides accounts, association workspaces, membership administration,
projects, recruitment, reporting hierarchies, events, public profiles, discovery
and an agenda calendar. Registration/attendance, tasks and messaging remain separate
work packages. See [the identity contract](docs/contracts/identity-and-current-user.md)
and [the project contract](docs/contracts/projects-and-reporting.md).

## Prerequisites

- JDK 17 (set `JAVA_HOME` to your JDK if necessary).
- Node.js 26 and npm; `.nvmrc` records the Node major version.
- Docker Desktop with its engine running, including Docker Compose.
- VS Code: open this repository root. Extension recommendations and run tasks
  are in `.vscode/`.

No global Maven or Ionic CLI installation is required.

## Run locally

From the repository root, start PostgreSQL:

```sh
docker compose up -d db
```

In a second terminal, start the backend:

```sh
cd Backend
export MAVEN_USER_HOME="$(pwd)/../../.maven"
./mvnw -Dmaven.repo.local="$MAVEN_USER_HOME/repository" spring-boot:run
```

These Maven settings keep the wrapper and dependency cache inside the parent
`Facultate` folder. Maven downloads itself on the first run. On Windows, use
`mvnw.cmd` and equivalent environment-variable syntax.

In another terminal, start the frontend:

```sh
cd Frontend
npm ci --cache ../../.npm-cache
npm run dev
```

- Frontend: <http://localhost:5173>
- Backend health: <http://localhost:8080/actuator/health>
- PostgreSQL: `localhost:5432`, database/user `aissociation`, local password
  `aissociation_local`.

Vite forwards `/api` and `/actuator` requests to the local backend. Use
`Frontend/src/api.ts` to construct request URLs. The `.env.example` files document
settings; copying them is optional for the default setup. Docker Compose reads
root `.env`; Vite reads `Frontend/.env.local`. Spring Boot reads operating-system
environment variables, not `.env` files automatically.

The backend supports `PORT`, `DB_URL` (a JDBC PostgreSQL URL), `DB_USER`, and
`DB_PASSWORD`. If you change the Compose database password, export the same
`DB_PASSWORD` in the terminal running the backend. Use separate production
credentials and never commit `.env` files.

VS Code's **Terminal → Run Task** menu offers **Database: start**,
**Backend: run**, and **Frontend: run**. Install frontend dependencies first.

## Run backend and database in Docker

```sh
docker compose up --build -d backend
```

This also starts PostgreSQL. Stop a natively running backend first to free
port 8080. Run the frontend separately with `npm run dev`.

```sh
docker compose down
```

Database data persists in a named Docker volume after shutdown.

## Checks

From `Backend/`, with `MAVEN_USER_HOME` set as above:

```sh
./mvnw -Dmaven.repo.local="$MAVEN_USER_HOME/repository" verify
```

Backend context and identity integration tests use H2 in PostgreSQL compatibility mode with Flyway migrations, so they do not require Docker. This does not replace PostgreSQL integration
testing. Schema generation is disabled for the running application; add
versioned migrations when introducing the domain model.

From `Frontend/`:

```sh
npm run build
npm run lint
npm run test:unit
```

The starter also includes Playwright end-to-end tests. Install its browser
dependencies before running `npm run test:e2e`.

## Hosting plan

- Render: Docker service with root directory `Backend`; configure database
  credentials and use `/actuator/health` as the health check.
- Vercel: root directory `Frontend`, build command `npm run build`, output `dist`.
  `vercel.json` provides the client-side routing fallback.
- PostgreSQL hosting remains to be selected.

The frontend's Vercel environment variable is
`VITE_API_BASE_URL=https://aissociation.onrender.com`.

Backend CORS allows `https://aissociation.vercel.app` and
`http://localhost:5173` for `/api/**` and read-only health requests at
`/actuator/health`. Override the complete origin list with the Render environment
variable `CORS_ALLOWED_ORIGINS` (comma-separated origins, no trailing slashes).
For production-only access, set it to `https://aissociation.vercel.app`.
Preview deployments need their own explicitly allowed origin. Cookie credentials
are not enabled; the API allows the `Authorization` and `Content-Type` headers.

## Account flow

Open `/register`, create a participant account, then sign in at `/login` to view
`/account`. Registration does not add association membership. Tokens stay in
browser memory; a page reload requires signing in again. Sign out revokes the
current server session.

Flyway applies the initial identity/association schema on backend startup.
Coordinate subsequent migration numbers with Raul and do not modify an applied
migration. No default users or passwords are seeded. Platform administrators create associations and appoint their initial administrators through the protected interface; association administrators manage membership from their workspace.

API details and teammate handoffs: [identity/current-user contract](docs/contracts/identity-and-current-user.md).
Authentication rationale: [ADR 0001](docs/decisions/0001-bearer-sessions.md).

## Platform administration

Trusted platform operators can create associations at `/admin/associations` and
appoint an existing account as the initial association administrator. Platform
access is disabled until the backend operator configures `PLATFORM_ADMIN_USER_IDS`
with trusted account UUIDs. This is separate from association `ADMIN` membership.
See [setup and API contract](docs/contracts/platform-association-admin.md).

No demo associations or administrator passwords are seeded. The interface creates
ordinary records using the protected API.

## Shared branding

The versioned guide and source assets are in [branding/](branding/README.md).
Open [the HTML guide](branding/brand-guide.html) or [PDF](branding/brand-guide.pdf).

Use the shared `BrandHeader`, `src/theme/brand.css` tokens, and semantic theme
variables for new screens. Run `npm run brand:sync` from `Frontend/` after editing
the source kit; commit synchronized assets with the source changes. Production
builds use the checked-in frontend copies and require no branding tools.

## Public association discovery

From `/home`, choose **Explore associations** to open `/associations`, then an
association's public identity page at `/associations/{slug}`. These pages read
persisted association names/slugs without requiring sign-in. They do not expose
member rosters or grant workspace access. Profiles show public description, website
and published events from the same discovery API used by the platform listing.

From `/home`, choose **Discover events** to browse at `/events`; switch between list and agenda calendar
while retaining association/UTC-date filters. Public details are at `/events/{id}`.
From an association workspace, open **Events & public profile** to create, edit
and publish drafts without approval. Administrators manage standalone events;
active project managers manage events for their own projects. The same screen
lets administrators edit public presentation fields. Registration/attendance
remain Denisa's integration; the public page does not offer a fake registration.

Flyway V6 (originally V3) adds events/public-profile fields without modifying V1–V5. Reconcile its
local allocation with Raul before shared application. See the contract for recorded
lifecycle, deadline, timezone and capacity defaults.

See [Yasmine's implementation sequence and UX flow](docs/plans/yasmine-work-package.md)
and [the public/event API handoff](docs/contracts/events-and-public-discovery.md).
Checks and demonstration: [Yasmine's guide](YASMINE_IMPLEMENTATION.md) and
[event verification instructions](docs/testing-events.md).

## Association workspaces

From `/account`, choose **Open workspace** beside a membership. Members can enter
their association; administrators can add existing accounts by email, change
MEMBER/ADMIN roles and remove memberships. The last administrator is protected.

Backend integration: [workspace/member API and shared permission checks](docs/contracts/association-workspaces.md).
Project, task and chat modules must combine association membership checks with
their own project/reporting/audience rules.

## Projects and recruitment

Open **Projects & recruitment** from an association workspace. Administrators
create projects and appoint existing association members as project managers.
Managers configure a recruitment call and role/department positions. Members apply
with motivation; a manager accepts one position with a valid reporting supervisor,
or rejects the application. Acceptance persists the team assignment and closes the
member's other pending applications in that project. Managers can appoint department
managers and edit reporting relationships.

Flyway V2 adds the project domain without modifying V1. Removed association
members lose active project access; rejoining does not revive old assignments.
The shared ProjectAccess helpers supply scoped membership, strict reporting ancestry
and audience queries for the other owners.

Details: [Raul's implementation handoff](RAUL_IMPLEMENTATION.md),
[API and behavior](docs/contracts/projects-and-reporting.md),
[ERD/migration index](docs/data-model.md), and
[PostgreSQL/browser verification setup](docs/testing-projects.md).

## Research questionnaire

The public `/research` page collects feedback without requiring an account.
Platform administrators can read responses at `/admin/research`, linked from
My account. Text responses and contact details are optional. Flyway V3 adds
research storage; no additional environment variables are needed.

See [questions, API, access and verification](docs/contracts/research-form.md).
This implementation is AI-assisted and does not count as Gabriel’s independent
no-GenAI feature. Responses collected later do not replace midterm interviews.
