# VAPT Management Platform — Product Requirements Document (PRD)

**Document status:** Draft v1.2 — Greenfield build; H2-first testing decision recorded  
**Product:** VAPT Management Platform  
**Primary users:** VAPT administrators, application/server owners, security stakeholders, executive readers  
**Implementation principle:** Build a secure, auditable, self-hosted application. No hosted third-party APIs or SaaS services are required at runtime.

---

## 1. Product vision

Build a central platform to ingest weekly VA/PT reports, enrich findings with CMDB ownership and application metadata, reconcile findings across reporting periods, track remediation and comments, enforce user-specific visibility, and provide current plus historical dashboards for operational and executive use.

The platform must answer five core questions:

1. What vulnerabilities exist now, and where do they exist?
2. Which application, owner and business group are responsible for each finding?
3. Is a finding new, still open, closed, or reopened compared with earlier reports?
4. What remediation action has been taken, by whom, and when?
5. How are findings, risk and remediation progressing week over week and month over month?

The application is being created from the beginning. There is no legacy VAPT codebase to preserve. Requirements collected in the earlier discussions remain the product input, but the implementation and database design will be greenfield.

## 2. Goals

- Provide independent workflows for the defined report sections.
- Ingest large CSV/XLSX reports without loading the entire workbook into application memory.
- Retain every original upload and every import/version for traceability.
- Enrich report findings through versioned CMDB snapshots.
- Identify recurring findings using stable source identifiers.
- Maintain a reliable finding lifecycle, including reopen and RCA tracking.
- Allow users to comment and record remediation actions within their permitted asset/application scope.
- Give administrators full visibility and a complete audit trail.
- Provide interactive operational dashboards and an aggregated executive dashboard.
- Support weekly, monthly and historical as-of analysis.
- Measure and log performance throughout upload, reconciliation, data retrieval, dashboard API execution and browser rendering.
- Keep data within the organisation's controlled environment; do not send report contents to external services.

## 3. Non-goals for the first release

- Corporate SSO integration. The initial release uses locally managed application accounts; SSO can be added later.
- External vulnerability scanner integrations or direct connections to vendor clouds.
- Automatic AI-based vulnerability triage or remediation recommendations.
- Automatic closure based only on an incomplete or partial report.
- AppSec-specific comment/mapping workflow before its source-to-CMDB mapping is defined.
- Microservice architecture. Start as a modular monolith.
- Public internet hosting or third-party analytics/telemetry SaaS.

## 4. Product scope and navigation

### 4.1 Report sections

The left navigation contains these six independent report sections. Each section has its own upload, dashboard, data view, import history, finding lifecycle and permissions boundary.

1. **GDP VA Report**
2. **Internal VA Brinqa**
3. **Wiz**
4. **VERIZON 26 Network PT**
5. **PT Verizon GCP**
6. **AppSec Findings PT**

The first five are general VA/PT report sources and use the common report-field, UID-based reconciliation and remediation model, subject to source-file validation. **Internal VA Brinqa and GDP VA Report are distinct report sections and must never be treated as aliases or combined imports.** AppSec Findings PT has its own source schema and Finding ID-based identity workflow. The AppSec Comments/mapping workflow is out of scope until its mapping logic is confirmed.

### 4.2 CMDB section

CMDB is a separate section with three independent workbook upload actions:

- `TuHeadCount EE & CWR`
- `JAS Inventory`
- `ApplicationInventory`

Each CMDB dataset has its own version history, active version, upload metadata and immutable source-file reference.

### 4.3 Executive dashboard

A separate consolidated dashboard aggregates authorised data across all six report sections. It must retain report-section filters so viewers can compare or isolate a source. Do not merge the underlying report identities or comment histories.

All six report sections are included in the weekly reporting and consolidated dashboard scope. Internal VA Brinqa is a distinct source from GDP VA Report. Each must have its own import history, finding namespace and independent dashboard/data view.

## 5. User roles and access

### 5.1 Roles

- **ADMIN:** Full access to all report sections, CMDB versions, uploads, mappings, dashboards, users, comments, remediation history, audit history and diagnostics.
- **USER:** Can view and update only findings in the asset/application scope assigned to that user. User visibility is enforced by the backend on every relevant query and mutation.

### 5.2 User eligibility and provisioning

- The `TuHeadCount EE & CWR` dataset supplies employee identity information: `Preferred Name`, `Associated's Email`, `CM4`, `CM5`, `CM6`.
- Initial bootstrap scope: create exactly one ADMIN account with email `manohar.kaloni@transunion.com`. Do not seed normal USER accounts at this stage.
- The bootstrap ADMIN is created independently of HeadCount eligibility. Checking a new USER against the active HeadCount version remains an unresolved product requirement and is not yet enforced by account creation.
- The bootstrap ADMIN password is supplied through `VAPT_ADMIN_INITIAL_PASSWORD`; a clean database will not start with an unusable generated or checked-in default secret. Existing credentials are not reset when the variable is absent.
- An ADMIN provisions accounts using the employee email as the normalized, unique username. The default role is USER; ADMIN must be explicitly selected. A new account starts pending, without scope grants.
- Account activation uses a cryptographically random, one-time token. Only its hash is stored, and it expires after the configured TTL. The ADMIN-controlled response supplies the activation link out-of-band because no approved mail integration exists. The user sets a password through that link; subsequent changes require authentication and the current password.
- BCrypt is used for password hashes. Passwords and activation tokens are never logged or returned after the one-time provisioning response.
- Corporate SSO is a future integration, not a requirement for the first release. Keep local authentication replaceable without changing roles or user scopes.

### 5.3 User scope

User visibility is mapped through CMDB ownership and application/server scope. The user scope model must support one user having multiple IP/asset/application mappings. Do not store a single IP address or application directly on the user record as a one-to-one field.

## 6. Authentication and visual design

- The login page is the default entry point.
- Authentication uses email and password for the initial release.
- The UI uses a TransUnion CIBIL-inspired blue/yellow visual direction, clean white surfaces and professional sans-serif typography.
- Use approved brand assets only. Do not copy official logos or imply that the application is an official TransUnion CIBIL public product unless the organisation authorises those assets and wording.
- Fonts and static assets must be locally bundled or use safe system-font fallbacks; do not load fonts from a public CDN.

## 6.1 Database strategy for the current testing phase

- Use a local H2 database for the current development/testing phase; PostgreSQL is a later migration target, not a current setup prerequisite.
- Prefer a file-backed H2 database for manual developer runs so test data survives restarts. Use in-memory H2 for automated tests that need an isolated database.
- Use Flyway versioned migrations from the beginning so schema evolution is explicit and repeatable. Do not rely on Hibernate `ddl-auto=update`.
- Keep SQL, identifiers, constraints and data types portable where practical. H2 tests do not guarantee PostgreSQL compatibility; before the PostgreSQL migration/cutover, run migrations and integration tests against a real PostgreSQL instance.
- The H2 database file contains sensitive report/application data. Keep it outside public/static folders, protect file permissions, and exclude it from distributable source packages.
- Git is not part of the current workflow and is not a setup blocker. Maintain explicit project backups and keep `HANDOFF.md` current when handing work to another agent.

## 7. Report ingestion requirements

### 7.1 General report fields

For the five general VA/PT sources (GDP VA Report, Internal VA Brinqa, Wiz, VERIZON 26 Network PT and PT Verizon GCP), ingest the following source fields where available:

- Type
- Port
- Risk score
- Risk rating
- Compliance status
- Severity
- Results
- UID
- Targets
- Display Name

Enrichment fields derived from CMDB include:

- IP Address
- Tu_AppName
- AppTag
- Environment
- OwnedBy
- Application ID
- HeadCount email and CM4/CM5/CM6
- Plan Application Owner 1
- Plan Application Owner 2

The source report remains immutable. Derived/enriched values must retain enough provenance to identify which source import and which CMDB versions were used.

### 7.2 AppTag derivation

`AppTag` is derived from the first three words of `Display Name`, after trimming and normalising whitespace. If fewer than three words exist, use all available words.

Examples:

- `SSH weak Message detected` → `SSH weak Message`
- `Azul Zulu Java Runtime` → `Azul Zulu Java`
- `Weak SSL Certificate` → `Weak SSL Certificate`

Preserve the original Display Name. Do not silently correct source spelling or substitute a different product name.

### 7.3 AppSec Findings PT fields

The separately defined AppSec source fields are:

- Record Id
- Primary Owner
- Title
- Severity
- Detailed Finding Description

The user also identified `Finding ID` as the unique identity for recurring AppSec findings. Confirm whether `Finding ID` is a separate source column or whether `Record Id` is the actual identity field. Do not silently equate them.

### 7.4 File and workbook support

- Support CSV and Excel workbook ingestion, including workbooks with multiple sheets.
- Discover the relevant worksheet and header row dynamically because worksheet names and header positions are not fixed.
- Extract only configured/recognised columns; retain source headers and raw-file reference.
- Handle duplicate, blank and differently cased headers safely.
- If a column or worksheet cannot be identified confidently, show a preview and request administrator mapping instead of guessing.
- Validate file type, size, row shape and required fields before committing data.
- Large imports must use streaming/chunked processing and bounded batches.

### 7.5 Import lifecycle

An import has an explicit state, for example:

`RECEIVED → VALIDATING → STAGING → ENRICHING → RECONCILING → COMMITTED`

Failure or cancellation must produce a terminal state and a diagnostic record. A failed or partially processed import must not silently replace the active dataset.

Every import records:

- Import ID and report section / CMDB dataset type
- Original filename, file size and SHA-256 checksum
- Uploaded by and upload timestamp
- Selected worksheet and detected header row
- Source row count, accepted row count, rejected row count
- Stage-by-stage duration
- CMDB versions used for enrichment
- New, existing, reopened, unresolved and missing counts where applicable
- Final state, error category and safe diagnostic message

## 8. CMDB and enrichment requirements

### 8.1 TuHeadCount EE & CWR

Extract:

- Preferred Name
- Associated's Email
- CM4
- CM5
- CM6

Update frequency: weekly or biweekly.

### 8.2 JAS Inventory

Extract:

- Name
- Host (normalised/short hostname)
- IP Address
- FQDN-valued column (actual header name is not yet known; discover using header and value profiling, then confirm in preview if ambiguous)
- Tu_AppName
- Environment
- OwnedBy
- Application ID
- Public Facing Application

Update frequency: approximately quarterly, 3–4 times per year.

Target matching:

1. If `Targets` is an IP address, match exactly against JAS `IP Address`.
2. If `Targets` is an FQDN, match exactly against the JAS column identified as containing FQDN values.
3. If exact FQDN matching is unavailable, apply a controlled hostname-normalisation fallback and match against JAS `Host`.
4. Preserve the original `Targets` value.
5. If zero or multiple candidates remain, record `UNMATCHED` or `AMBIGUOUS`; never choose an arbitrary asset.

The sample transformations `www.abcd.com → abcd`, `mup.aakkass.com → aakkass`, and `mhco1.kkod.com → kkod` are examples, not a universal DNS rule. Implement normalisation as a separately tested, configurable strategy.

### 8.3 ApplicationInventory

Extract:

- ApplicationId
- Plan Application Owner 1
- Plan Application Owner 2

Update frequency: approximately quarterly, 3–4 times per year.

### 8.4 CMDB versioning

- Preserve each upload as an immutable historical version.
- One version per dataset type is marked active at a time.
- Do not create a new physical database table for every version.
- Store version metadata, checksum, source-file reference and activation history.
- Finding enrichment records which CMDB versions were used, so an old report remains explainable after CMDB data changes.
- Store raw files in a controlled local/on-premises file store. Database backups and file-store backups are separate responsibilities.

### 8.5 Mapping chain

`Report.Targets → JAS Inventory → (OwnedBy → TuHeadCount.Preferred Name) → (Application ID → ApplicationInventory.ApplicationId)`

Mapping output includes asset/IP, application, environment, owner, HeadCount attributes and application owners. Every mapping must carry match status, mapping rule and CMDB version references.

## 9. Finding identity and weekly reconciliation

### 9.1 Identity rules

- For the five general VA/PT report sources, `UID` is the candidate source identity.
- Verify UID completeness, uniqueness within each report section and stability across repeated weekly uploads before enforcing a unique constraint.
- Treat identity as source-scoped unless evidence proves it is globally unique. The expected uniqueness is `(report_section, source_uid)`.
- AppSec uses its confirmed `Finding ID` field as its candidate identity.
- Maintain an application-generated internal primary key independent of source identifiers.

### 9.2 Weekly reconciliation outcomes

Each incoming finding is classified as one of:

- `NEW`
- `EXISTING_OPEN`
- `EXISTING_CLOSED`
- `REOPENED`
- `RETURNED_UNRESOLVED`
- `UNMATCHED_IDENTITY`
- `MISSING_FROM_COMPLETE_SNAPSHOT`

Rules:

1. New UID/Finding ID: create a new finding identity and observation.
2. Existing open finding: append a new observation; preserve comments and previous history.
3. Previously closed finding appears again: mark as reopened, append lifecycle event and require an RCA workflow.
4. Finding remains open and has a prior false-positive/remediation action but is present again: flag as returned/unresolved and preserve the earlier action history.
5. Finding absent from a complete authoritative weekly snapshot: classify as missing from that snapshot and close according to the agreed report policy, recording the source import as evidence.
6. Finding absent from a partial/incomplete report: do not infer closure.

Every weekly upload must record whether it represents a complete snapshot or a partial extract. This prevents accidental mass closure when an export is incomplete.

### 9.3 RCA

The reopened-finding workflow must capture an RCA record. The initial requirement is to require RCA when a previously closed finding returns. Exact RCA fields, approver rules and closure criteria are to be finalised before implementation of that form.

## 10. Comments and remediation

### 10.1 Scope

Comments workflow is enabled for:

- GDP VA Report
- Internal VA Brinqa
- Wiz
- VERIZON 26 Network PT
- PT Verizon GCP

AppSec Findings PT is explicitly excluded until its mapping and ownership logic is defined.

### 10.2 Navigation hierarchy

1. User opens Comments.
2. User sees only IP addresses mapped to their user scope.
3. User selects an IP and sees applications mapped to it.
4. User selects an application.
5. The selected Application Name is shown as a page heading, not as a table column.
6. The table shows:

| AppTags | Critical | High | Medium | Low | Comments | Remediation |
|---|---:|---:|---:|---:|---|---|

Risk-rating counts are calculated from the findings' `Risk rating` values for that AppTag and application.

### 10.3 Remediation actions

The user can select one of these four actions:

- False Positive Raised
- False Positive Approved
- Remediation Date — the user selects a date
- Already Remediated

Record actor, action, optional selected date, server-generated timestamp, previous value and new value. The server timestamp must be stored separately from the user-selected remediation date.

### 10.4 Data model behaviour

The AppTag table is an aggregated user interface. Finding recurrence and remediation dates are finding-level facts. Therefore, remediation state must be traceable to the individual finding UID (or AppSec Finding ID when AppSec workflow is later enabled), even if the user starts the action from an AppTag summary row.

Comments may be associated with the selected report/IP/application/AppTag context; the exact policy for whether a comment applies to the entire AppTag group or an individual finding must be confirmed in UX design. Every comment/change must retain author and timestamp.

### 10.5 Audit visibility

Administrators can inspect who changed what, when, and on which finding or AppTag context. Users can see relevant comment/remediation history within their allowed scope.

## 11. SLA requirements — deferred pending policy approval

The prior risk-rating TAT table is an unapproved proposal and is not an active product rule. A due-date source and HOD/system exceptions have not been approved. The application currently preserves the source Compliance Status and does not derive or display an SLA Status, due date, or SLA dashboard. Do not equate derived SLA with source Compliance Status or fabricate due dates. Revisit this section only after an authoritative date source and policy are approved.
## 12. Dashboards and analytics

### 12.1 Report-level dashboard

Expected slices/graphs include:

- Risk rating
- Compliance status
- Tu_AppName
- AppTag
- Environment
- Risk Score
- Severity
- (Deferred) SLA state, pending an approved due-date source and calculation policy.
- Remediation progress

The precise chart layout can be finalised during UI design, but all visualisations must be backed by server-side aggregations rather than downloading all findings into the browser.

### 12.2 Executive dashboard

- Consolidates authorised metrics across report sections.
- Allows report-section, time-period and other supported filters.
- Supports current status and historical trend analysis.
- Keeps data provenance and report-section identity intact.

### 12.3 Weekly and monthly trends

The application must retain weekly import observations/snapshots. Users can analyse by week or month:

- New findings
- Open findings
- Closed/remediated findings
- Reopened findings
- Returned/unresolved findings
- Risk-rating distribution
- (Deferred) SLA distribution, pending an approved due-date source and calculation policy.
- Remediation progress

Historical queries must represent the system state for the selected reporting period, not merely filter today's latest state by a date column.

## 13. Data tab and exports

### 13.1 Data tab

- Show agreed key fields for each section.
- Use server-side pagination, filtering and sorting.
- Enforce user scope in the database query/service layer.
- Do not send all rows to the browser for display.

For the five general VA reports, expected fields include Type, Port, Compliance Status, Risk Rating, Results, UID, IP Address, Tu_AppName, OwnedBy, AppTag and Remediation Date. Additional fields can be included where justified.

### 13.2 Excel export

Workbook sheet order is fixed:

1. Summary/Pivot
2. Data
3. Comments

Support filtered export and authorised full export. Large exports must use streaming generation and sanitise formula-like text values.

### 13.3 PowerPoint export

Generate an executive presentation from a predefined, approved template with report-section graphs and dashboard analysis. Template and slide-by-slide content rules will be specified before implementation.

## 14. Performance, logging and observability requirements

Performance measurement is a first-class product requirement, not an afterthought.

### 14.1 Three separate records

1. **Application logs:** technical events, errors and stage durations.
2. **Metrics:** aggregate counts, rates, durations and percentiles for operational monitoring.
3. **Audit history:** durable user/business changes such as login outcomes, uploads, comments, remediation actions, approvals, role/scope changes and CMDB activation.

Do not treat a debug log as an audit record. Do not store every low-level performance sample as an unbounded database row.

### 14.2 Correlation

Every HTTP request receives a correlation/request ID. A long-running upload also receives an import ID. All logs and stage measurements for the same operation carry these IDs so the complete flow can be reconstructed.

### 14.3 Required upload timings

Measure at least:

- Browser file selection to upload request start (client-side)
- Upload transfer duration and byte count
- File validation duration
- Workbook open duration
- Worksheet/header discovery duration
- Parsing duration
- Row normalisation/type conversion duration
- Staging write duration
- CMDB enrichment duration
- UID lookup/reconciliation duration
- Commit duration
- Total end-to-end import duration

Also log rows/second, accepted/rejected rows and error counts. Do not log raw row values, credentials or file contents.

### 14.4 Required retrieval/dashboard timings

Measure at least:

- HTTP request total duration
- Authentication/authorization duration where instrumented
- Service/business logic duration
- Each database query duration and returned row count
- Aggregation duration
- DTO mapping/serialization duration where material
- Payload size
- Browser API request duration
- Browser state update/render duration
- Chart initialisation/render-complete duration
- Total dashboard-ready duration

Dashboard-ready must be measured in the browser because server request duration alone does not include network transfer, JavaScript work or chart rendering.

### 14.5 Other measured operations

- Login success/failure and authentication latency (never log passwords)
- CMDB upload, matching and activation duration
- Comment/remediation save duration
- Excel/PPT export preparation and download duration
- User-management and scope-change operations
- Scheduled snapshot/aggregation job duration
- Application startup/readiness, JVM memory/GC, CPU, thread pools, connection-pool utilisation and disk space

### 14.6 Log format and levels

Use structured logs (JSON in deployed environments; readable console pattern may be used locally) with fields such as:

`timestamp`, `level`, `service`, `environment`, `event`, `correlationId`, `importId`, `userId` (internal ID only), `reportSection`, `operation`, `durationMs`, `rowsRead`, `rowsWritten`, `result`, `errorCode`.

Levels:

- INFO: start/end of business operations and important state transitions.
- WARN: validation rejections, ambiguous mappings, slow operations and recoverable failures.
- ERROR: failed operations and unexpected exceptions with stack trace and correlation ID.
- DEBUG: limited diagnostic details in non-production; never raw sensitive row data.

Logs must be rotated, access-controlled and retained according to policy. Do not log passwords, session tokens, raw comments, full IP/hostname inventories or complete uploaded rows unless explicitly approved for a secure diagnostic case.

### 14.7 Performance diagnostics

Add an administrator-only diagnostics view in a later phase to show recent import timings, API latency, dashboard load time, slow queries and application health. It must not expose secrets or raw vulnerability data.

### 14.8 Initial performance acceptance

Collect a baseline with representative volumes (including 100k+ findings and a larger stress dataset). Define p50/p95/p99 targets after the baseline and deployment hardware are known. Do not claim a performance target is met based on local toy data. Every performance improvement must be verified with measurements and query plans.

## 15. Security and privacy

- All business data stays within the controlled application environment.
- No report content is sent to external AI, analytics or file-conversion services.
- Use TLS in deployed environments.
- Use Spring Security, secure session cookies and CSRF protection for browser session authentication.
- Hash passwords; never store plain-text credentials.
- Enforce RBAC and asset/application scope on the backend.
- Validate uploads and prevent path traversal, oversized file abuse and spreadsheet formula injection in exports.
- Keep audit logs append-only from the application's normal user workflow.
- Protect logs and raw uploaded files with filesystem/database permissions and backup controls.
- Avoid exposing actuator/diagnostic endpoints publicly.

## 16. Proposed acceptance criteria

The first release is accepted when:

1. A user can log in and receives only their permitted navigation/data.
2. Admin can create users, assign roles and manage scope.
3. All six report sections are independently usable; GDP VA Report and Internal VA Brinqa remain distinct.
4. CMDB datasets can be uploaded, versioned, activated and mapped.
5. Worksheet/header discovery handles varying worksheet names and header rows, with manual review on ambiguity.
6. A large report imports without unbounded memory growth.
7. The same UID does not create duplicate findings within its confirmed source scope.
8. Reopened and returned findings are distinguished and have lifecycle history.
9. Missing findings are closed only under an explicitly complete snapshot policy.
10. Comments/remediation actions are attributed to users and timestamped.
11. AppSec Comments remain disabled until AppSec mapping is approved.
12. Current dashboard and historical weekly/monthly analysis work.
13. Excel export uses the required three-sheet order.
14. Upload, retrieval and dashboard timing are measurable end to end.
15. Logs and audit records do not contain passwords or raw sensitive report rows.
16. Automated tests cover access isolation, reconciliation, import failure recovery and historical snapshots. SLA boundary tests are deferred until an approved rule exists.

## 17. Open questions / decision register

These items must be resolved before the affected feature is implemented:

1. Is AppSec `Finding ID` a distinct source column from `Record Id`, or is one the actual header for the other?
2. Are there any source-specific required columns or validation rules that differ between GDP VA Report and Internal VA Brinqa?
4. Is the JAS FQDN column uniquely identifiable by value profile, or must an admin select it once per workbook version?
5. Are weekly report exports always complete snapshots? If not, how is completeness declared?
6. What are the exact RCA form fields, approver and closure rules?
7. Are comments at AppTag group level, individual finding level, or both?
8. Does a Remediation Date represent a planned target date or the actual completion date? The current UI label suggests a selected date, but its meaning must be fixed.
9. What are the exact HOD/system exception SLA rules and boundary inclusivity?
10. What are the PowerPoint template and required slides?
11. What data-retention period applies to uploaded source files, operational logs and audit records?
12. What are the deployment environment, expected concurrent users and available server resources?

## 18. Delivery approach

Implementation will follow the separate `IMPLEMENTATION-PLAN.md`. Work proceeds in small, testable phases. Each phase must include design review, implementation, automated tests, build verification, security review where relevant, and updated documentation before the next phase begins.
