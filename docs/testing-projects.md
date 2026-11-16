# Project workflow verification

The fast default backend suite uses H2. ProjectWorkflowTests can also run against
PostgreSQL without changing production configuration. It deletes and recreates
synthetic fixture rows in its target database: use a dedicated disposable database,
never an application database.

## Backend

From Backend, set JAVA_HOME to a JDK 17 installation, then run:

~~~powershell
.\mvnw.cmd --offline verify
~~~

Omit --offline on a machine whose dependencies have not already been cached.
For PostgreSQL, first start an isolated container from the repository root:

~~~powershell
docker run --detach --name aissociation-raul-verification --publish 127.0.0.1:15432:5432 --env POSTGRES_DB=raul_verification --env POSTGRES_USER=raul_test --env POSTGRES_PASSWORD=synthetic_local_only postgres:17
~~~

Wait until pg_isready succeeds, then from Backend:

~~~powershell
$env:PROJECT_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/raul_verification'
$env:PROJECT_TEST_DB_DRIVER = 'org.postgresql.Driver'
$env:PROJECT_TEST_DB_USER = 'raul_test'
$env:PROJECT_TEST_DB_PASSWORD = 'synthetic_local_only'
.\mvnw.cmd --offline '-Dtest=ProjectWorkflowTests' test
~~~

These variables affect only ProjectWorkflowTests. Other backend test classes keep
their own H2 databases. The project suite includes concurrent acceptance, scoped
FK/uniqueness checks, revoked membership, branches/audiences and an isolated-schema
V1-to-V2 migration test preserving an existing account.

## Real browser flow

Keep the PostgreSQL test fixture from the previous command. Run the packaged
backend against it on a separate port, using Java 17:

~~~powershell
java -jar target\aissociation-0.0.1-SNAPSHOT.jar --server.port=18080 --spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/raul_verification --spring.datasource.username=raul_test --spring.datasource.password=synthetic_local_only --app.platform-admin.user-ids=30000000-0000-0000-0000-000000000001 --app.cors.allowed-origins=http://localhost:5176
~~~

The operator allowlist and synthetic password are only for this disposable local
fixture. Production migrations do not seed accounts or administrator privileges.
In a second terminal, from Frontend:

~~~powershell
$env:VITE_API_BASE_URL = 'http://localhost:18080'
npm.cmd run dev -- --port 5176 --strictPort
~~~

In a third terminal, from Frontend:

~~~powershell
$env:PROJECT_E2E_API_URL = 'http://localhost:18080'
npm.cmd run test:e2e:projects
~~~

The opt-in suite uses installed Edge on Windows, and Playwright Chromium elsewhere
(install its browser locally if needed). It creates synthetic associations through
the existing protected API, then exercises real UI/API/PostgreSQL behavior with
separate administrator/manager/member browser contexts on desktop and Pixel 7.
Screenshots and failure traces are written to ignored Frontend/test-results.
The existing general Playwright suite skips this flow unless explicitly opted in.

Demonstration sequence: create project and appoint manager → configure positions
and call → member applies to two positions → manager accepts one with supervisor →
other application closes → manager appoints department manager → member sees team,
without management controls. Backend tests cover the negative permission and
concurrent selection cases rather than relying on screenshots.

## Cleanup

Stop only the two verification servers with Ctrl+C. Then:

~~~powershell
docker rm --force --volumes aissociation-raul-verification
~~~

This removes only the named disposable test container and its anonymous database
volume. It does not use the application's Compose database or its named volume.

