# Research questionnaire

Owner: Gabriel. This feature is AI-assisted, including its implementation and tests.
The earlier reservation as an independently written feature no longer applies;
Gabriel must select another feature for that assessment requirement.

## Pages

- `/research`: public questionnaire, linked from the homepage. No account required.
- `/admin/research`: response viewer, linked from a platform administrator’s account.
  Association administrators and ordinary accounts cannot read responses.

The introduction explains the research purpose, approximate completion time,
readers, optional contact details and follow-up use. Written answers, association
name and email are optional. The seven choice questions require an answer;
challenge and capability priorities allow at most three selections each.
Responses are research input, not verified findings or a replacement for interviews.

## API

`POST /api/public/research/responses` accepts `{ submissionId, answers }`.
`submissionId` is a random client-generated UUID kept for the form’s lifetime.
Success returns 204 with no response data. Retrying the identical ID and answers
also returns 204 without adding a second row. A different payload for a saved ID
returns 409 `SUBMISSION_EXISTS`; no stored answers are returned publicly.
Validation rejects missing choice answers, unknown/duplicate options, excessive
selections, oversized text and invalid optional email. Error bodies follow the
shared `code`, `message`, `fieldErrors` convention.

`answers` fields:

| Fields | Type / limit |
| --- | --- |
| involvement, memberCount | One predefined option |
| recruitment, tasks, tools | Arrays of predefined options, at least one |
| challenges, capabilities | Arrays of predefined options, one to three |
| organization, registration, barriers, suggestions | Text, max 2000 characters |
| recruitmentDetails, taskDetails, toolDetails, challengeDetails, capabilityDetails | Text, max 1000 characters |
| associationName | Text, max 150 characters |
| contactEmail | Empty or valid email, max 254 characters |

Current labels/options are in `Frontend/src/research/questions.ts`; server-side
allowlists and constraints are in `ResearchDtos.Answers`. Update both together.

`GET /api/admin/research/responses?page=0` requires a bearer session and platform
administrator allowlist access. It returns up to 50 `{ id, submittedAt, answers }`
records, newest first, with `Cache-Control: no-store`. Pages start at zero.
There is no public response-reading endpoint. The client renders written answers
as plain text and does not persist them in browser storage.

## Persistence and operation

Flyway V3 creates `research_responses` with UUID primary key, validated answer
JSON stored as text, and server timestamp. Research is platform-wide and has no
association foreign key because anonymous respondents may represent associations
that are not registered. No account, membership or automatic role is created.

No new deployment environment variables are needed. Existing platform administrator
configuration controls the viewer. Merge and deploy backend V3 and frontend before
sharing the public link. Test fixtures are not interview evidence. Public collection
has no bot filtering in this version; add abuse controls if submissions warrant it.

## Verification

Backend API tests cover anonymous writes, private reads, validation, replay/conflict
and bounded pagination. Frontend tests cover submission, selection limits, preserved
answers/retry identity, pending submission, admin gating and loading-error recovery.
Local browser submission was persisted to a temporary PostgreSQL 17 instance;
HTTP checks verified anonymous/member denial, platform-admin reads and safe retry.
No hosted data was changed and no real research responses have been collected.
