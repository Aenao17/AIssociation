# AIssociation

UBB student association event management application — Team Stucanii.

## Monorepo

- `Backend/`: Java 17, Spring Boot 4.1.2, Maven wrapper, Spring MVC, validation,
  Spring Data JPA, PostgreSQL, and Actuator.
- `Frontend/`: Ionic React, TypeScript, and Vite.
- `compose.yaml`: local PostgreSQL and an optional containerized backend.

These are starter scaffolds. Event workflows, authentication, and the domain
model have not been implemented yet.

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

The starter backend context test uses H2 in PostgreSQL compatibility mode,
so it does not require Docker. This does not replace PostgreSQL integration
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
