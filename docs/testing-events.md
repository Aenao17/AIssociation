# Event verification

Use Java 17 and the repository's Node 26. No global tool installation is required.
From Backend, run `./mvnw verify` (`.\mvnw.cmd verify` on Windows). From Frontend,
run `npm run build`, `npm run test:unit` and `npm run lint`.

## PostgreSQL

EventWorkflowTests defaults to H2 and can run unchanged on PostgreSQL using the
EVENTS_TEST_DB_* variables below. **Use a dedicated disposable database:** this
test deletes fixtures in events, projects, accounts and associations.

Example isolated container, from repository root with Docker available:

```powershell
docker run --detach --name aissociation-events-check --publish 127.0.0.1:15433:5432 --env POSTGRES_DB=events_verification --env POSTGRES_USER=events_test --env POSTGRES_PASSWORD=synthetic_local_only postgres:17
```

Wait for pg_isready. Then from Backend:

```powershell
$env:EVENTS_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15433/events_verification'
$env:EVENTS_TEST_DB_DRIVER = 'org.postgresql.Driver'
$env:EVENTS_TEST_DB_USER = 'events_test'
$env:EVENTS_TEST_DB_PASSWORD = 'synthetic_local_only'
.\mvnw.cmd '-Dtest=EventWorkflowTests' test
```

The test covers the composite FK, timestamps, transactional mutation behavior and
public/organizer workflows on the selected database. Its separate V2->V6 migration
retention check uses H2; also rehearse that upgrade on a disposable PostgreSQL
database before shared release. Do not claim H2 is PostgreSQL evidence.

## Browser

Keep the synthetic fixture produced by EventWorkflowTests, and run the backend on
port 18081 against that same disposable database. Enable CORS for
http://localhost:5177. Start Frontend with VITE_API_BASE_URL=http://localhost:18081
and `npm run dev -- --port 5177 --strictPort`. In another Frontend terminal:

```powershell
$env:EVENTS_E2E_API_URL = 'http://localhost:18081'
node node_modules/@playwright/test/cli.js test --config playwright.events.config.ts
```

The config uses existing Edge on Windows or installed Playwright Chromium elsewhere.
It does not auto-install browsers. Tests skip unless EVENTS_E2E_API_URL is set.
EVENTS_E2E_FRONTEND_URL can override the frontend address. The frontend's actual API
base URL must match the verification backend; the opt-in variable does not proxy it.
The suite uses only fixture event-1@example.test / synthetic-event-password and
the Alpha association. Never create those fixture credentials in production.
It exercises both desktop and Pixel 7 viewports, with a separate anonymous browser
context for draft privacy and discovery. Screenshots/traces go to ignored
Frontend/test-results. Direct routes are exercised locally; hosted checks remain
separate, after an authorized deployment.

For this session's local browser run, H2 file persistence under Backend/target was
used instead of PostgreSQL. Its temporary servers were stopped after verification.
See the engineering log for exact results and tool-environment workarounds.
