# Phase 0 — VAPT Management Project Readiness Audit

**Date:** 2026-11-15  
**Status:** Audit complete — awaiting approval to proceed to Phase 1  
**Documents reviewed:** [PRD.md](file:///c:/Users/Manohar/Development/VAPT-Management/PRD.md), [ARCHITECTURE.md](file:///c:/Users/Manohar/Development/VAPT-Management/ARCHITECTURE.md), [IMPLEMENTATION.md](file:///c:/Users/Manohar/Development/VAPT-Management/IMPLEMENTATION.md)

---

## 1. Requirements Understanding Confirmation

### 1.1 Six Independent Report Modules ✅

Confirmed. The PRD defines six independent report sections, each with its own upload, dashboard, data view, import history, finding lifecycle, and permissions boundary:

| # | Report Section | Identity Model | Comments Enabled |
|---|---|---|---|
| 1 | GDP VA Report | UID-based | Yes |
| 2 | Internal VA Brinqa | UID-based | Yes |
| 3 | Wiz | UID-based | Yes |
| 4 | VERIZON 26 Network PT | UID-based | Yes |
| 5 | PT Verizon GCP | UID-based | Yes |
| 6 | AppSec Findings PT | Finding ID-based | **No** (until mapping defined) |

> [!IMPORTANT]
> GDP VA Report and Internal VA Brinqa are **distinct** sections — separate import histories, finding namespaces, and dashboards. They must never be treated as aliases or combined.

### 1.2 CMDB Ingestion and Mapping ✅

Confirmed. Three independent CMDB workbook uploads:

| CMDB Dataset | Key Fields | Update Frequency |
|---|---|---|
| TuHeadCount EE & CWR | Preferred Name, Associated's Email, CM4/CM5/CM6 | Weekly/biweekly |
| JAS Inventory | Name, Host, IP Address, FQDN column (TBD), Tu_AppName, Environment, OwnedBy, Application ID, Public Facing Application | ~Quarterly |
| ApplicationInventory | ApplicationId, Plan Application Owner 1/2 | ~Quarterly |

Mapping chain: `Report.Targets → JAS Inventory → (OwnedBy → HeadCount) → (Application ID → ApplicationInventory)`

Each CMDB upload is versioned, immutable, and one version per dataset type is marked active. Enrichment records which CMDB versions were used.

### 1.3 UID-Based Reconciliation (First Five Reports) ✅

Confirmed. Source identity is `(report_section_id, source_uid)`. Weekly reconciliation classifies each finding as: `NEW`, `EXISTING_OPEN`, `EXISTING_CLOSED`, `REOPENED`, `RETURNED_UNRESOLVED`, `UNMATCHED_IDENTITY`, or `MISSING_FROM_COMPLETE_SNAPSHOT`.

Batch completeness declaration prevents accidental mass closure from partial reports.

### 1.4 Finding ID-Based Reconciliation (AppSec) ✅

Confirmed. AppSec Findings PT uses `Finding ID` as its candidate identity. The relationship between `Finding ID` and `Record Id` remains an **open question** (PRD §17, item 1).

### 1.5 User Roles and Access Control ✅

Confirmed:
- **ADMIN**: Full access to everything.
- **USER**: Scoped to assigned asset/application mappings, enforced server-side on every query and mutation.
- User scope supports one-to-many IP/asset/application mappings (not a single one-to-one field).
- Initial provisioning: 2–3 manual test accounts. User eligibility validated against active HeadCount version.

### 1.6 Comments and Remediation Workflow (First Five Reports) ✅

Confirmed. Navigation: IP → Application → AppTag table with severity counts, Comments, and Remediation columns. Four remediation actions: False Positive Raised, False Positive Approved, Remediation Date, Already Remediated. All changes carry actor, timestamp, and audit trail.

AppSec Comments are **explicitly excluded** until mapping is defined.

### 1.7 Historical Weekly/Monthly Analysis ✅

Confirmed. The system retains weekly import observations/snapshots. Historical queries reconstruct system state for the selected reporting period — not just filtering current rows by date. Tracked: new, open, closed, reopened, returned/unresolved, risk/SLA distribution, remediation progress.

### 1.8 Dashboard and Performance Logging ✅

Confirmed:
- **Report-level dashboards**: risk rating, compliance status, Tu_AppName, AppTag, environment, risk score, severity, SLA, remediation progress — all backed by server-side aggregations.
- **Executive dashboard**: consolidated cross-section metrics with report-section filters and historical trends.
- **Performance logging**: first-class requirement with three separate record types (application logs, metrics, audit events), mandatory correlation IDs, detailed stage timings for uploads, dashboard APIs, and browser rendering.

---

## 2. Environment Verification

### 2.1 Installed Software

| Tool | Status | Version | Location / Notes |
|---|---|---|---|
| **Java** | ✅ Installed | 21.0.12.1 LTS (2026-08-18) | `C:\Program Files\Java\jdk-21.0.12.1` — JAVA_HOME set correctly |
| **Maven** | ⚠️ No system install | Wrapper bundles 3.10.0 | Maven Wrapper (`mvnw.cmd`) present in `backend/` — functional but no global `mvn` |
| **Node.js** | ✅ Installed | v26.13.1 | `C:\Program Files\nodejs\node.exe` |
| **npm** | ✅ Installed | 11.20.0 | PowerShell script execution policy blocks `npm.ps1`; `npm.cmd` works |
| **PostgreSQL** | ❌ **Not found** | — | No `psql` on PATH, no service registered, no `C:\Program Files\PostgreSQL` directory |
| **Git** | ❌ **Not found** | — | No `git` on PATH, no installation directory found |
| **pnpm** | ❌ Not found | — | Not installed (optional, npm is available) |

### 2.2 Missing Prerequisites

> [!CAUTION]
> **Two blockers must be resolved before Phase 1 can begin.**

| Blocker | Impact | Resolution |
|---|---|---|
| **PostgreSQL not installed** | Cannot create the database, run Flyway migrations, or test any data layer | Install PostgreSQL 16 or 17 (compatible with Spring Boot 4.1.x and Flyway) |
| **Git not installed** | Cannot create the repository, track changes, or collaborate | Install Git for Windows |

| Warning | Impact | Resolution |
|---|---|---|
| npm PowerShell policy | `npm.ps1` is blocked; `npm.cmd` works | Run `Set-ExecutionPolicy RemoteSigned -Scope CurrentUser` or continue using `npm.cmd` |
| No global Maven | Must use `.\mvnw.cmd` instead of `mvn` | Acceptable — Maven Wrapper is the recommended approach |

---

## 3. Architecture Compatibility Analysis

### 3.1 Existing Project State

A Spring Boot skeleton already exists at [backend/](file:///c:/Users/Manohar/Development/VAPT-Management/backend):

- **Spring Boot 4.1.2** parent POM
- **Java 21** target — matches architecture spec
- **Package**: `com.findings.tracker` — **diverges** from architecture's `com.company.vapt`
- **Maven Wrapper**: Maven 3.10.0 via wrapper
- **Frontend directory**: Exists but is **empty** — no React/Vite project initialized

### 3.2 Current pom.xml Dependencies vs. Architecture Requirements

| Dependency | In pom.xml? | Architecture Requires | Status |
|---|---|---|---|
| Spring Boot 4.1.2 (parent) | ✅ | Spring Boot (stable release for Java 21) | ✅ Compatible |
| spring-boot-starter-data-jpa | ✅ | JPA for persistence | ✅ |
| spring-boot-starter-security | ✅ | Spring Security | ✅ |
| spring-boot-starter-webmvc | ✅ | Spring MVC REST | ✅ |
| PostgreSQL driver | ✅ | PostgreSQL | ✅ |
| spring-boot-starter-flyway | ✅ | Flyway migrations | ✅ |
| H2 database | ✅ (runtime) | Test-only acceptable | ⚠️ Should be `test` scope only |
| spring-boot-h2console | ✅ | Not required | ⚠️ Remove for production |
| **Apache POI** | ❌ Missing | Excel parsing (XLSX) | 🔴 Must add |
| **Commons CSV** | ❌ Missing | CSV parsing | 🔴 Must add |
| **Actuator + Micrometer** | ❌ Missing | Metrics, health, JVM monitoring | 🔴 Must add |
| **Validation starter** | ❌ Missing | Input validation (Bean Validation) | 🔴 Must add |
| **SLF4J / Logback** | Implicit | Structured logging | ✅ Included transitively via Spring Boot |
| **Logback JSON encoder** | ❌ Missing | Structured JSON logs in deployed environments | 🔴 Must add |

### 3.3 Artifact ID Concerns

> [!WARNING]
> Several artifact IDs in the current [pom.xml](file:///c:/Users/Manohar/Development/VAPT-Management/backend/pom.xml) use Spring Boot 4.x naming conventions that should be verified against the actual Spring Boot 4.1.2 BOM:
> - `spring-boot-h2console` (traditionally `spring-boot-starter-h2-console` or similar)
> - `spring-boot-starter-webmvc` (was `spring-boot-starter-web` in 3.x)
> - `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-flyway-test` (traditionally just `spring-boot-starter-test`)
>
> **Action**: Run `.\mvnw.cmd dependency:resolve` to verify all artifacts resolve correctly before adding more dependencies.

### 3.4 Frontend Stack Compatibility

| Component | Architecture Spec | Current State | Action |
|---|---|---|---|
| React | Required | Not initialized | Create Vite + React + TypeScript project |
| TypeScript | Required | Not initialized | Included with Vite React template |
| Vite | Required | Not initialized | Use `npm create vite@latest` |
| Apache ECharts | Required (bundled locally) | Not initialized | Add as npm dependency |
| Fonts/assets | Must be locally bundled | Not initialized | Bundle fonts; no CDN |

### 3.5 Package Naming Decision Required

| Aspect | Current | Architecture Spec | Decision Needed |
|---|---|---|---|
| Group ID | `com.findings` | `com.company` | Align before any code is written |
| Artifact ID | `tracker` | `vapt` (implied) | Align before any code is written |
| Base package | `com.findings.tracker` | `com.company.vapt` | Align before any code is written |

> [!NOTE]
> Since no application code exists beyond the starter class, renaming the package now is trivial. This should be decided before Phase 1.

### 3.6 Full Stack Compatibility Matrix

| Technology | Required Version | Compatibility with Java 21 + Spring Boot 4.1.x | Notes |
|---|---|---|---|
| Java | 21 LTS | ✅ | Installed: 21.0.12.1 |
| Spring Boot | Stable for Java 21 | ✅ 4.1.2 | Already in pom.xml |
| Spring Security | Via Boot BOM | ✅ | Managed by parent |
| PostgreSQL | 16 or 17 | ✅ | **Not installed yet** |
| Flyway | Via Boot starter | ✅ | Already in pom.xml |
| React | 18.x or 19.x | ✅ (frontend, no Java dep) | Not yet initialized |
| TypeScript | 5.x | ✅ (frontend) | Not yet initialized |
| Vite | 5.x or 6.x | ✅ (frontend) | Not yet initialized |
| Apache ECharts | 5.x | ✅ (frontend) | Not yet initialized |
| Apache POI | 5.x | ✅ with Java 21 | **Not in pom.xml** |
| SLF4J / Logback | Via Boot BOM | ✅ | Transitive |
| Micrometer | Via Actuator starter | ✅ | **Not in pom.xml** |

No fundamental compatibility conflicts exist. All proposed technologies work together with Java 21 and Spring Boot 4.1.x.

---

## 4. Logging and Monitoring Readiness

### 4.1 Architecture Coverage Assessment

The architecture document (§11) defines a comprehensive four-mechanism observability model. Here is a mapping of every required measurement to the proposed mechanism:

#### Upload/Import Timings

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| Upload transfer duration + byte count | Micrometer timer `upload.transfer` | ✅ §11.4 |
| File validation duration | Micrometer timer `import.file_validation` | ✅ §11.4 |
| Workbook open duration | Micrometer timer `import.workbook_open` | ✅ §11.4 |
| Sheet/header discovery duration | Micrometer timer `import.sheet_header_detection` | ✅ §11.4 |
| Excel parsing duration | Micrometer timer `import.parse` | ✅ §11.4 |
| Row normalisation duration | Micrometer timer `import.normalise` | ✅ §11.4 |
| Staging write duration | Micrometer timer `import.stage_write` | ✅ §11.4 |
| CMDB matching/enrichment duration | Micrometer timer `import.cmdb_enrichment` | ✅ §11.4 |
| UID lookup/reconciliation duration | Micrometer timer `import.reconciliation` | ✅ §11.4 |
| Commit duration | Micrometer timer `import.commit` | ✅ §11.4 |
| Total import duration | Micrometer timer `import.total` | ✅ §11.4 |

#### Dashboard/Retrieval Timings

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| HTTP request total duration | `http.server.requests` (built-in Actuator) | ✅ §11.4 |
| Database query duration + row count | Micrometer timer per query category | ✅ §11.4 |
| Dashboard API duration | Micrometer timer `dashboard.summary.total` | ✅ §11.4 |
| Aggregation duration | Covered by dashboard query timers | ✅ §11.4 |
| DTO serialization duration | Micrometer timer `data.search.serialization` | ✅ §11.4 |
| Frontend dashboard rendering | Browser Performance API milestones | ✅ §11.5 |
| Chart init/render-complete | `dashboard.charts.render.start/end` | ✅ §5.2 |

#### Export Timings

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| Excel export duration | Micrometer timer `export.xlsx.total` | ✅ §11.4 |
| PPT export duration | Micrometer timer `export.pptx.total` | ✅ §11.4 |

#### Authentication & Security Events

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| Login success/failure | Audit event `LOGIN_SUCCESS`/`LOGIN_FAILURE` | ✅ §11.7 |
| Authentication latency | Micrometer timer | ✅ §11.5 |

#### Error & Exception Tracking

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| Error/exception with stack trace | Structured log at ERROR level + correlation ID | ✅ §11.2, §11.6 |
| Failed import diagnostics | Import batch terminal state + diagnostic record | ✅ §7.5 (PRD) |

#### Infrastructure Metrics

| Measurement | Mechanism | Architecture Support |
|---|---|---|
| JVM memory/GC | Actuator + Micrometer (built-in) | ✅ §11.1 |
| CPU | Actuator + Micrometer (built-in) | ✅ §14.5 (PRD) |
| Connection pool utilisation | Actuator + Micrometer (HikariCP metrics) | ✅ §14.5 (PRD) |
| Thread pools | Actuator + Micrometer (built-in) | ✅ §14.5 (PRD) |
| Disk space | Actuator health indicator | ✅ §14.5 (PRD) |

### 4.2 Three-Way Separation Confirmed

| Record Type | Storage | Content | Not Used For |
|---|---|---|---|
| **Application logs** | Rotating local log files (SLF4J + Logback) | Technical events, errors, stage durations, correlation IDs | Audit records or unbounded perf samples |
| **Metrics** | Micrometer registry (local diagnostics endpoint) | Aggregate counts, rates, durations, percentiles | Debug logging or audit |
| **Audit events** | Append-only database table (`audit_event`) | User/business changes: logins, uploads, comments, scope changes | Debug/diagnostic logging |

✅ The architecture cleanly separates all three. No gaps identified.

---

## 5. Security and Privacy Verification

| Requirement | Architecture Support | Reference |
|---|---|---|
| BCrypt password hashing | ✅ BCrypt / DelegatingPasswordEncoder specified | ARCH §1 table, §9 |
| Role-based access control (ADMIN/USER) | ✅ Spring Security + method/service scope checks | ARCH §9, PRD §5 |
| Server-side authorization on every query | ✅ "Every repository/service query that returns findings must include the caller's permitted scope" | ARCH §9 |
| No sensitive data in application logs | ✅ Explicitly prohibited: passwords, cookies, tokens, CSRF, raw rows, comments, IP lists | ARCH §11.8, PRD §14.6 |
| No external data transmission | ✅ "No external third-party API, SaaS, telemetry or CDN dependency" at runtime | ARCH §1.1, PRD §15 |
| No CDN-based runtime dependencies | ✅ "All static assets, fonts and chart libraries are bundled locally" | ARCH §5.1, PRD §6 |
| CSRF protection | ✅ "Keep CSRF protection enabled for state-changing browser requests" | ARCH §9 |
| Secure session cookies | ✅ HttpOnly, Secure, SameSite policy | ARCH §9 |
| File upload validation | ✅ Type, size, row shape, path traversal, formula injection prevention | ARCH §13, PRD §7.4 |
| Actuator endpoint protection | ✅ "Protect actuator endpoints; expose only required read-only health/metrics" | ARCH §13 |
| TLS in deployment | ✅ At application server or reverse proxy | ARCH §3, PRD §15 |

✅ **No security gaps identified** between requirements and architecture. All PRD §15 security requirements are addressed in ARCHITECTURE.md §9 and §13.

---

## 6. Unresolved Requirements and Ambiguities

These items are taken directly from the PRD §17 open questions. **None have been invented.**

| # | Open Question | Blocks Phase |
|---|---|---|
| 1 | Is AppSec `Finding ID` a distinct source column from `Record Id`? | Phase 6 (reconciliation), Phase 13 (AppSec) |
| 2 | Source-specific required columns or validation rules differing between GDP VA and Internal VA Brinqa? | Phase 5 (ingestion) |
| 3 | Is the JAS FQDN column identifiable by value profile, or must admin select it? | Phase 4 (CMDB) |
| 4 | Are weekly report exports always complete snapshots? How is completeness declared? | Phase 6 (reconciliation) |
| 5 | Exact RCA form fields, approver, and closure rules? | Phase 6 (reconciliation) |
| 6 | Are comments at AppTag group level, individual finding level, or both? | Phase 8 (comments) |
| 7 | Does Remediation Date mean planned target date or actual completion date? | Phase 8 (remediation) |
| 8 | Exact HOD/system exception SLA rules and boundary inclusivity? | Phase 9 (SLA) |
| 9 | PowerPoint template and required slides? | Phase 12 (exports) |
| 10 | Data retention period for uploaded files, logs, and audit records? | Phase 14 (hardening) |
| 11 | Deployment environment, expected concurrent users, server resources? | Phase 14 (hardening) |

> [!NOTE]
> Items 1–5 should ideally be resolved before their respective phases begin. Items 6–11 can be deferred to later phases without blocking Phase 1. None block Phase 1 (project foundation).

---

## 7. Proposed Greenfield Project Structure

Based on ARCHITECTURE.md §4 and §5, the target project layout is:

```
VAPT-Management/
├── PRD.md
├── ARCHITECTURE.md
├── IMPLEMENTATION.md
├── .gitignore
│
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/company/vapt/     ← (package TBD)
│       │   │   ├── VaptApplication.java
│       │   │   ├── common/
│       │   │   │   ├── api/
│       │   │   │   ├── error/
│       │   │   │   ├── logging/
│       │   │   │   ├── security/
│       │   │   │   ├── time/
│       │   │   │   └── validation/
│       │   │   ├── identity/
│       │   │   ├── report/
│       │   │   ├── ingestion/
│       │   │   ├── cmdb/
│       │   │   ├── finding/
│       │   │   ├── comments/
│       │   │   ├── remediation/
│       │   │   ├── dashboard/
│       │   │   ├── history/
│       │   │   ├── export/
│       │   │   ├── audit/
│       │   │   └── observability/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── db/migration/          ← Flyway SQL migrations
│       │       ├── logback-spring.xml
│       │       └── static/               ← Built frontend assets
│       └── test/
│           └── java/com/company/vapt/
│
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── app/
│       │   ├── App.tsx
│       │   ├── router.tsx
│       │   └── providers/
│       ├── api/
│       │   ├── httpClient.ts
│       │   └── contracts/
│       ├── components/
│       │   ├── layout/
│       │   ├── tables/
│       │   ├── filters/
│       │   ├── charts/
│       │   └── feedback/
│       ├── features/
│       │   ├── auth/
│       │   ├── executive-dashboard/
│       │   ├── reports/
│       │   ├── cmdb/
│       │   ├── comments/
│       │   ├── remediation/
│       │   ├── import-history/
│       │   ├── audit/
│       │   ├── administration/
│       │   └── diagnostics/
│       ├── styles/
│       └── telemetry/
│
└── uploads/                              ← Protected file store (outside web root)
```

---

## 8. Readiness Summary

### Overall Status: 🟡 CONDITIONALLY READY

The requirements are well-defined, the architecture is sound, and there are no fundamental compatibility conflicts. Two infrastructure blockers must be resolved before Phase 1 begins.

### Blockers (Must Fix)

| # | Blocker | Severity | Action Required |
|---|---|---|---|
| 1 | **PostgreSQL not installed** | 🔴 Critical | Install PostgreSQL 16 or 17 |
| 2 | **Git not installed** | 🔴 Critical | Install Git for Windows |

### Pre-Phase-1 Decisions (Should Fix)

| # | Item | Severity | Action Required |
|---|---|---|---|
| 3 | Package naming: `com.findings.tracker` vs `com.company.vapt` | 🟡 Medium | Decide before writing any application code |
| 4 | H2 scope: currently `runtime`, should be `test` only | 🟡 Medium | Change scope in pom.xml |
| 5 | H2 console dependency: not needed | 🟡 Low | Remove `spring-boot-h2console` |
| 6 | Verify Spring Boot 4.1.2 artifact IDs resolve correctly | 🟡 Medium | Run `mvnw dependency:resolve` after network access |
| 7 | Missing pom.xml dependencies (POI, CSV, Actuator, Validation) | 🟡 Medium | Add during Phase 1 setup |
| 8 | Frontend not initialized | 🟡 Medium | Create Vite + React + TS project in Phase 1 |
| 9 | application.properties needs PostgreSQL config, logging config | 🟡 Medium | Configure in Phase 1 |

### What Is Ready

| Area | Status |
|---|---|
| Java 21 LTS | ✅ Installed and matches architecture spec |
| Spring Boot 4.1.2 skeleton | ✅ Present with core starters |
| Maven (via wrapper) | ✅ Functional |
| Node.js + npm | ✅ Installed for frontend build |
| Requirements documents | ✅ Comprehensive and internally consistent |
| Architecture document | ✅ Detailed with clear module boundaries |
| Implementation plan | ✅ 16 phases with exit criteria |
| Security architecture | ✅ No gaps vs. requirements |
| Logging/monitoring architecture | ✅ All 11 measurement categories covered |
| Stack compatibility | ✅ No fundamental conflicts |

---

> [!IMPORTANT]
> **This is an audit only.** No application code, dependencies, or infrastructure changes have been made. Awaiting approval to resolve blockers and proceed to Phase 1.
