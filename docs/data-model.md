# Data model and migration index

Owner/coordinator: Raul. Updated 16 November 2026.
Physical model below matches V1–V6 SQL. V6 (events, originally V3) is Yasmine's allocation pending
reconciliation with the migration coordinator before shared application.

## Relationships

~~~mermaid
erDiagram
    app_users ||--o{ auth_sessions : has
    app_users ||--o{ association_memberships : holds
    associations ||--o{ association_memberships : contains
    associations ||--o{ projects : owns
    associations ||--o{ events : publishes
    projects |o--o{ events : contains
    projects ||--o{ project_departments : defines
    projects ||--o{ recruitment_positions : offers
    project_departments ||--o{ recruitment_positions : groups
    projects ||--o{ project_memberships : contains
    app_users ||--o{ project_memberships : joins
    association_memberships |o--o{ project_memberships : grants_active_access
    recruitment_positions |o--o{ project_memberships : selected_position
    project_departments |o--o{ project_memberships : assigned_department
    project_memberships |o--o{ project_memberships : reporting_parent
    projects ||--o{ project_applications : receives
    recruitment_positions ||--o{ project_applications : targets
    app_users ||--o{ project_applications : submits
    association_memberships |o--o{ project_applications : submitted_under

    research_responses {
        UUID id PK
        TEXT answers
        TIMESTAMPTZ submitted_at
    }

    app_users {
        UUID id PK
        VARCHAR display_name
        VARCHAR email UK
        VARCHAR password_hash
        TIMESTAMPTZ created_at
    }
    auth_sessions {
        UUID id PK
        UUID user_id FK
        VARCHAR token_hash UK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ expires_at
    }
    associations {
        UUID id PK
        VARCHAR name
        VARCHAR slug UK
        VARCHAR description
        VARCHAR website "nullable"
        TIMESTAMPTZ created_at
    }
    events {
        UUID id PK
        UUID association_id FK
        UUID project_id FK "nullable; same association"
        VARCHAR title
        VARCHAR description
        VARCHAR location
        TIMESTAMPTZ starts_at
        TIMESTAMPTZ ends_at
        VARCHAR time_zone
        TIMESTAMPTZ registration_deadline
        INTEGER capacity "nullable means unlimited"
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    association_memberships {
        UUID id PK
        UUID association_id FK
        UUID user_id FK
        VARCHAR role
        TIMESTAMPTZ created_at
    }
    projects {
        UUID id PK
        UUID association_id FK
        VARCHAR name
        VARCHAR description
        VARCHAR recruitment_description "nullable"
        TIMESTAMPTZ recruitment_deadline "nullable"
        TIMESTAMPTZ created_at
    }
    project_departments {
        UUID id PK
        UUID project_id FK
        VARCHAR name
    }
    recruitment_positions {
        UUID id PK
        UUID project_id FK
        UUID department_id FK
        VARCHAR role_label
    }
    project_memberships {
        UUID id PK
        UUID project_id FK
        UUID user_id FK
        UUID association_membership_id FK "nullable on removal"
        VARCHAR project_role
        UUID department_id FK "nullable for root managers"
        UUID position_id FK "nullable for appointed managers"
        UUID reporting_parent_id FK "nullable for roots"
        TIMESTAMPTZ created_at
    }
    project_applications {
        UUID id PK
        UUID project_id FK
        UUID position_id FK
        UUID user_id FK
        UUID association_membership_id FK "nullable on removal"
        VARCHAR motivation
        VARCHAR status
        UUID selected_user_id "nullable uniqueness mirror"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ reviewed_at "nullable"
    }
~~~

This diagram does not show registration/task/message tables, which remain other
owners' work packages. Optional FK relationships retain
history; service rules are stricter than ERD cardinality for active accepted members.

## Constraints and behavior

| Area | Enforcement |
| --- | --- |
| Identity | V1 unique normalized email, unique token hash/association slug, session expiry after creation |
| Association membership | Unique association/account; MEMBER or ADMIN. Last-admin protection remains an application invariant in MembershipService |
| Recruitment call | Both description/deadline null or both set; API requires nonblank text and future deadline on configuration |
| Department labels | Unique exact project/name; project-local configurable vocabulary |
| Positions | Unique project/department/role label; composite project/department FK |
| Team membership | Unique project/account, independent explicit project_role |
| Reporting | Composite project/parent FK, no self-parent SQL check; root managers have no parent, non-roots require parent/department/position |
| Accepted selection | Application selected_user_id must equal its user exactly when ACCEPTED, otherwise null; unique project/selected_user_id enforces one accepted application |
| Repeat application | Unique project/position/account, regardless of decision status |
| Scope | Composite project/position and project/department references prevent mismatched children |
| Removal | Original association membership FKs use ON DELETE SET NULL; project/application history remains. Rejoin cannot revive the deleted membership reference |
| Semantic invariants | Service transaction validates current actor/applicant membership, supervisor role/department and no cycles; all mutations use association-then-project locks |

Membership can be appointed without a recruitment application for root managers.
Accepted recruits receive one selected position and reporting parent atomically;
the selected application's status and team row commit together. Position labels
are responsibilities, separate from project permissions.

Indexes support association project listing, parent traversal, account team lookup,
original membership references, project application review and own applications.
No production users or passwords are seeded by these migrations.
Account/project deletion is not added as a workflow; V2 domain FKs retain their
default restrictive behavior except the explicit membership-reference SET NULL.

## Research response storage

Migration owner: Gabriel. V3 creates `research_responses`:

| Field | Type | Constraints / purpose |
| --- | --- | --- |
| id | UUID | Primary key; client-generated submission identifier prevents duplicate retries |
| answers | TEXT | Required; stores the validated questionnaire answers as JSON |
| submitted_at | TIMESTAMP WITH TIME ZONE | Required; server-generated submission time |

The `(submitted_at, id)` index supports response listing. Research responses have
no account or association foreign key because the questionnaire accepts anonymous
submissions. Field lengths, allowed choices and selection limits are validated by
the API before insertion.

## Migration index

| Version | File | Owner | Dependencies / status |
| --- | --- | --- | --- |
| V1 | [V1__identity_foundation.sql](../Backend/src/main/resources/db/migration/V1__identity_foundation.sql) | Gabriel | Existing identity/session/association foundation; unchanged |
| V2 | [V2__projects_recruitment_reporting.sql](../Backend/src/main/resources/db/migration/V2__projects_recruitment_reporting.sql) | Raul | Depends on V1; projects, configurable departments/positions, team/reporting and applications; verified locally on H2/PostgreSQL |
| V6 | [V6__events_public_profiles.sql](../Backend/src/main/resources/db/migration/V6__events_public_profiles.sql) | Yasmine | Renumbered from V3 (duplicate of `V3__research_responses.sql`); adds events/profile fields, composite project/association FK and discovery indexes. Reconcile with Raul before shared application. H2 verification; PostgreSQL check remains pending |
| Unallocated | Registration, tasks and messages | Respective owners; Raul coordinates | Supply schema dependencies before allocating numbers; no implied reservations here |

Coordinate V2 with other owners before merging/applying across shared environments;
the local repository had only V1 when V2 was allocated. No external migration
reservation or teammate approval is claimed.

1. Inspect current main and this index before allocating a new number.
2. Record owner, filename and dependencies before parallel schema work diverges.
3. Add a forward migration; never modify or renumber an already-applied migration.
4. Reconcile conflicting unpublished allocations with affected owners before first
   shared application.
5. Migrate both a fresh PostgreSQL database and an existing prior-version schema;
   run the meaningful uniqueness/locking/business tests on PostgreSQL.
6. Update this ERD/index and the owning API contract together.

ProjectWorkflowTests includes a separate-schema V1 → V2 upgrade retaining a
synthetic existing account. All 14 project tests pass on PostgreSQL 17 and H2;
see [the handoff](../RAUL_IMPLEMENTATION.md).

