# VAPT Management Platform — Implementation Plan

**Status:** Delivery plan; current as-built completion is recorded in HANDOFF.md. Phases below are plans unless marked verified.  
**Rule:** Complete the exit criteria for each phase before starting the next phase.

## Phase 0 — Lock scope and resolve remaining source details

**Work**
- Treat the six report sections and their independence as confirmed scope.
- Implement six independent report sections: GDP VA Report, Internal VA Brinqa, Wiz, VERIZON 26 Network PT, PT Verizon GCP and AppSec Findings PT.
- Keep Internal VA Brinqa and GDP VA Report as distinct sources with separate import histories, finding namespaces and report dashboards.
- Confirm AppSec Finding ID versus Record Id.
- Preserve Comments for the five general VA/PT sources; keep AppSec excluded until its mapping is defined. Confirm any remaining comment-to-finding association detail and Remediation Date meaning.
- Confirm completeness semantics for weekly exports and the RCA fields.
- Confirm the organisation's interpretation of “no third party”: no hosted services (recommended) versus no third-party libraries at all.
- Confirm target deployment environment, expected users and retention policy.

**Deliverables**
- Approved PRD and Architecture.
- Decision register with owners and dates.
- Source-file samples with sensitive data masked.

**Exit criteria**
- Every field required for first ingestion is mapped to a real sample workbook/report or marked as an explicit unresolved field.

## Phase 1 — Repository, build and development foundation

**Work**
- Create a clean Git repository.
- Set up Java 21 and a supported Spring Boot release.
- Add the minimal backend dependencies: Spring MVC, Spring Security, validation, PostgreSQL driver, Flyway, Actuator/Micrometer, Apache POI and CSV parser.
- Create the React + TypeScript + Vite frontend and local production build.
- Establish environment-specific configuration and secret handling.
- Create module/package skeleton and global error response format.
- Configure structured logs, correlation IDs and baseline application health metrics immediately.

**Tests / exit criteria**
- Backend and frontend build from a clean checkout.
- Runtime serves frontend from the same origin.
- Health endpoint responds without calling the internet.
- No CDN/network request is needed to render the login shell.
- Logs contain correlation IDs and do not leak credentials.

## Phase 2 — Database foundation, identity and security

**Work**
- Design and create Flyway migrations.
- Create users, roles and user-scope model.
- Implement admin/user login using Spring Security session authentication.
- Hash passwords; add login throttling/lockout policy.
- Add backend method/service-level scope checks.
- Seed development-only admin and normal users using safe configuration, not committed credentials.
- Add audit event model for login and access administration.

**Tests / exit criteria**
- Admin and normal user can authenticate.
- Passwords are never stored in plain text.
- User A cannot read or mutate User B's unmapped assets by changing an ID or filter.
- CSRF and session-cookie behaviour is tested.
- Authentication and scope changes are auditable.

## Phase 3 — Report-section foundation and source contracts

**Work**
- Register all six report sections as independent modules.
- Define independent import configuration, report field mapping and current-import pointer per section.
- Define general report canonical fields and separate AppSec source model.
- Build source-file metadata and immutable local file storage.
- Create import-batch lifecycle and status API.

**Tests / exit criteria**
- All six sections are independently addressable; GDP VA Report and Internal VA Brinqa never share import or finding namespaces.
- A user cannot accidentally mix one section's filters, import history or findings with another.
- Source file checksum and upload metadata are retained.

## Phase 4 — CMDB ingestion and version management

**Work**
- Build workbook inspection and dynamic sheet/header detection.
- Add the three CMDB data adapters.
- Add preview/confirmation for ambiguous header/sheet/column detection.
- Add CMDB version history and active-version activation.
- Profile JAS columns to identify the FQDN-valued column; persist the admin-confirmed source header.
- Implement exact match and controlled normalisation functions with test fixtures.

**Tests / exit criteria**
- Sheet/header row may vary and can be detected or flagged for admin review.
- CMDB version history is retained; only one active version per dataset type.
- FQDN/IP/Host matching produces matched/unmatched/ambiguous results correctly.
- HeadCount name ambiguity and ApplicationInventory mapping ambiguity are not silently resolved.

## Phase 5 — Report ingestion and staging

**Work**
- Implement streamed CSV/XLSX parsing.
- Validate required fields and normalise headers.
- Derive AppTag from Display Name.
- Stage rows using bounded batches.
- Add import progress and stage timings.
- Add failed-import cleanup/retry semantics.

**Tests / exit criteria**
- Import works with representative reports and large synthetic datasets.
- Memory use remains bounded as row count grows.
- Malformed/ambiguous data is rejected or held for review without corrupting the active dataset.
- Per-stage timings, row counts, throughput and failure details are logged.

## Phase 6 — Finding identity and weekly reconciliation

**Work**
- Profile UID completeness, uniqueness and stability for each general source.
- Confirm AppSec Finding ID mapping.
- Implement finding, observation and lifecycle-event tables.
- Implement NEW, EXISTING_OPEN, EXISTING_CLOSED, REOPENED, RETURNED_UNRESOLVED and missing-from-complete-snapshot classification.
- Implement batch completeness declaration and safe closure rules.
- Define and implement initial RCA form after fields are approved.

**Tests / exit criteria**
- Repeat UID does not create duplicate findings within the validated source scope.
- Previously closed finding returning is marked reopened and requires RCA.
- Previously unresolved/actioned finding that remains present is flagged according to policy.
- Missing rows close only when the incoming import is declared a complete authoritative snapshot.
- Previous observations/events remain queryable.

## Phase 7 — Enrichment and user mapping

**Work**
- Join report Targets to JAS using exact IP/FQDN and controlled Host fallback.
- Map JAS OwnedBy to HeadCount Preferred Name.
- Map JAS Application ID to ApplicationInventory ApplicationId.
- Create finding mapping provenance and ambiguity resolution UI.
- Link users to asset/application scopes.

**Tests / exit criteria**
- Each enrichment records CMDB version and match method.
- Unmatched and ambiguous mappings remain visible and do not receive fabricated owners.
- User visibility reflects approved scope mappings.

## Phase 8 — Comments, remediation and audit

**Work**
- Implement Comments for GDP VA Report, Internal VA Brinqa, Wiz, VERIZON 26 Network PT and PT Verizon GCP. Exclude AppSec Findings PT until its mapping workflow is defined.
- Build IP → application → AppTag navigation.
- Display Application Name above the table.
- Show AppTags, Critical, High, Medium, Low, Comments and Remediation columns.
- Add four remediation actions.
- Store finding-level remediation state/date and user/timestamp history.
- Keep AppSec Comments disabled until its mapping workflow is approved.

**Tests / exit criteria**
- Users see only mapped IPs/applications.
- Risk counts match underlying findings.
- Every comment and remediation change records actor and server timestamp.
- Remediation Date remains distinct from created/updated timestamps.
- Admin can inspect who changed what and when.

## Phase 9 — SLA engine (deferred)

No SLA state or due date is calculated or shown. The previously proposed risk-rating timing table, due-date source, and HOD/system exceptions have not been approved. Source Compliance Status is retained as supplied. Reopen this phase only after the business owner approves an authoritative due-date source and calculation rules.
## Phase 10 — Current dashboards and data tab

**Work**
- Build report-level dashboard aggregate APIs.
- Build executive aggregate APIs across authorised report sections.
- Build server-side filtering, sorting and pagination for detail data.
- Build the current risk and Compliance Status donut charts and the application, AppTag, environment, severity and remediation views. Do not show a duplicate SLA Status.
- Add empty/loading/error states.

**Tests / exit criteria**
- Charts use aggregate DTOs; the browser does not receive the full finding set.
- Detail data is paginated server-side.
- Dashboard queries include caller scope and report-section filter.
- Query timing, DB time, response size and browser render time are measurable.

## Phase 11 — Historical weekly/monthly analytics

**Work**
- Record import snapshots and historical observation membership.
- Implement weekly comparison and monthly aggregation.
- Show new/open/closed/reopened/returned counts and risk/remediation trends.
- Add historical as-of dashboard selection.

**Tests / exit criteria**
- A selected past week/month reflects the state known at that time.
- Historical metrics do not silently change when today's finding state changes.
- Aggregation definitions are documented and consistent.

## Phase 12 — Excel and PowerPoint exports

**Work**
- Excel export with required sheet order: Summary/Pivot, Data, Comments.
- Support authorised filtered and complete exports.
- Streaming workbook generation and formula-injection defence.
- Implement approved PowerPoint template and slide definitions.
- Instrument generation duration and file size.

**Tests / exit criteria**
- Export respects user scope and selected filters.
- Large export does not load the complete dataset into heap unnecessarily.
- Correct sheet order and expected totals are verified.
- Export logs record duration, rows, bytes and outcome.

## Phase 13 — AppSec-specific mapping and workflow

**Work**
- Confirm the unique Finding ID source field.
- Define AppSec mapping to assets/applications/owners.
- Define whether/how AppSec comments and remediation are enabled.
- Reuse finding lifecycle and historical patterns where the domain permits.

**Exit criteria**
- AppSec finding identity and mapping are confirmed against real source files.
- AppSec remains isolated from the other report sections.

## Phase 14 — Performance, security and operational hardening

**Work**
- Build representative 100k+ and larger stress datasets.
- Profile upload stages, reconciliation, dashboard queries, data retrieval, exports and browser charts.
- Review query plans and indexes.
- Test concurrent imports/read traffic, connection pool limits, disk usage and log rotation.
- Perform authorisation, upload, export and session security tests.
- Validate backups and restore process.
- Finalise operational dashboard/diagnostics and log retention.

**Exit criteria**
- p50/p95/p99 timings are reported on documented hardware/data volumes.
- No cross-scope data leakage is found.
- No sensitive data is exposed in logs.
- Database/file backups restore successfully.
- Performance claims are supported by repeatable measurements.

## Phase 15 — Release and handover

**Work**
- Finalise installation, configuration and backup/restore guides.
- Document data dictionary, APIs and reconciliation rules.
- Tag release and dependency inventory.
- Prepare admin and normal-user guides.

**Exit criteria**
- Clean install and upgrade from migrations succeed.
- No outbound runtime request is required.
- PRD, Architecture and implementation documentation match the shipped code.

## As-built engineering status (2026-11-16)

The implementation has working report upload/import, CMDB dataset versioning, enrichment, finding lifecycle/workspace, dashboards and exports, user activation, security and H2 development console controls. It is prepared for manual UAT, not organizational acceptance or production deployment. Current verification: backend clean suite 139 tests (138 passed, 0 failed, 0 errors, 1 opt-in 100k test skipped); frontend TypeScript and production build passed (Vite 6.4.6, 37 modules). Flyway V1–V10 validated/applied in a clean H2 test database; V10 adds activation tokens and V1–V9 were not modified by this standardization work. The prior standalone 100k results are retained as baseline and were not rerun here.

The as-built Java package tree and known module-boundary limitations are documented in ARCHITECTURE.md. The next delivery activity is administrator and normal-user manual UAT with representative organizational source files, including validation of data mapping and business outcomes. Do not infer AppSec identity, complete-snapshot closure, SLA due-date, user section-catalog, or hostname suffix policies.