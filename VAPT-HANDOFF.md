# VAPT Management — Current Handoff

Verified: 2026-11-16 (local Windows development environment; clean H2 test database)

## Current status

The application is prepared for **manual UAT**, not organizational acceptance or production deployment. The prior features remain in place; this work standardized recognizable Java layers, corrected a CSRF bootstrap defect behind upload 403s, tightened H2 Console access, removed unapproved SLA display/calculation and unsafe development account seeds, added secure account activation, and synchronized the project status documents.

The approved TransUnion CIBIL SVG was not present in the workspace. The login page references `/assets/transunion-cibil-logo.svg`; place the original approved file at `frontend/public/assets/transunion-cibil-logo.svg`. No substitute was downloaded or recreated.

## Implemented behavior and operational decisions

- The SPA obtains `/api/auth/csrf` before loading authenticated state. The SPA sends the raw `XSRF-TOKEN` cookie value in `X-XSRF-TOKEN`; the backend uses the SPA-aware CSRF handler to validate that raw header while keeping XOR masking for request attributes. The API still enforces ADMIN-only uploads, import history, active import, section catalog and CMDB administration. 403 responses now distinguish CSRF failures from role failures and carry the correlation ID. This addresses the observed upload failures; restart the backend and frontend dev server/build before retesting. The supplied runtime log later shows a graceful backend shutdown; if the backend is stopped, start it with the existing file-backed database and set `VAPT_ADMIN_INITIAL_PASSWORD` when bootstrapping a fresh database.
- Vite uses `localhost:3000` and proxies `/api` and `/actuator` to Spring Boot at `localhost:8080`. Production serves the bundled React app and API from the same origin. No CORS configuration is needed and CSRF remains enabled.
- The dev H2 Console uses the same file-backed datasource as the app at `/h2-console`. It is dev-profile-only, requires ADMIN, is restricted to loopback addresses, disables CSRF only in its dedicated filter chain and uses SAMEORIGIN frames. Production explicitly disables it. MockMvc checks verify profile settings and chain behavior; open the console manually on localhost during UAT.
- A clean dev database requires `VAPT_ADMIN_INITIAL_PASSWORD` to provision the bootstrap ADMIN `manohar.kaloni@transunion.com`. Existing passwords are not reset at each restart. Runtime does not seed USER accounts or scopes.
- ADMIN-created accounts use normalized company email usernames (`VAPT_ALLOWED_EMAIL_DOMAIN`, default `transunion.com`), default to USER, begin PENDING with no scopes, and receive a cryptographically random one-time activation link for out-of-band delivery. Only a SHA-256 token hash is persisted; activation is single-use and expires according to `VAPT_ACTIVATION_TOKEN_TTL_HOURS` (default 24). No approved SMTP integration exists. Subsequent password changes require authentication and the current password. Active HeadCount employee validation is not yet implemented.
- Risk Rating and Compliance Status use donut charts. The duplicate SLA Status view, SLA calculations and unapproved due dates are removed. The `sla_due_at` database/entity field remains unused for migration compatibility. Compliance Status is preserved from the source.
- The original `company.web01.com → web01` hostname fallback remains. No additional suffixes or owner mappings are enabled by this work.
- AppSec Finding ID versus Record Id remains unresolved; AppSec comments stay disabled. Automatic closure requires an approved authoritative complete-snapshot policy and remains disabled by default. HOD/system exceptions remain disabled. The PowerPoint template remains replaceable until an approved corporate template is available.

## Java package structure

```text
com.company.vapt
├── security
├── common/{exception,logging,observability,response}
├── user/{controller,service,repository,domain,entity,dto/request,dto/response}
├── report/{controller,domain,entity,repository,dto/response}
├── ingestion/{controller,service,repository,entity,domain,dto,storage}
├── cmdb/{controller,service,repository,entity,domain,storage}
├── enrichment/{controller,service,repository,entity,domain}
├── finding/{controller,service,repository,entity,dto/response}
└── audit/{controller,service,repository,domain}
```

Controllers use services, persistence entities are not API response objects, and DTOs define REST contracts. `finding` currently owns reconciliation, comment, remediation, dashboard and export orchestration within its aggregate. These are not separate top-level roots yet. Some enrichment/reconciliation services also access another feature's repository directly. This is a known modular-boundary limitation; a future refactor should introduce real module-owned use-case interfaces, not forwarding wrappers.

## Configuration

| Variable | Purpose | Default / requirement |
|---|---|---|
| `VAPT_ADMIN_INITIAL_PASSWORD` | First-run bootstrap ADMIN password | Required when bootstrap ADMIN does not yet exist; no checked-in fallback |
| `VAPT_ALLOWED_EMAIL_DOMAIN` | Allowed provisioning email domain | `transunion.com` |
| `VAPT_ACTIVATION_TOKEN_TTL_HOURS` | Activation-token expiry | `24` |
| `VAPT_STORAGE_ROOT` | Original upload storage root | `./data/storage/uploads` |
| `VAPT_ENRICHMENT_BATCH_SIZE` | Enrichment batch size | `250` |
| `VAPT_ENRICHMENT_HOST_SUFFIXES` | Explicit hostname suffix rules | Empty; do not add without approval |
| `VAPT_RECONCILIATION_BATCH_SIZE` | Reconciliation batch size | `250` |
| `VAPT_CLOSE_MISSING_FINDINGS` | Close missing findings automatically | `false` |
| `VAPT_RCA_ADMIN_REVIEW_REQUIRED` | Require ADMIN review of RCA | `true` |

Dev H2 URL: `jdbc:h2:file:./data/vaptdb;DB_CLOSE_DELAY=-1`; test contexts use isolated in-memory H2. Flyway V1–V10 were validated/applied in the clean test context. V10 adds activation-token storage; V1–V9 were not modified. PostgreSQL startup/cutover is unverified.

## Verification

Backend command from `backend`:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=../.m2/repository' clean test '-DargLine=-javaagent:../.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
```

Result: exit 0; **15 suites, 139 tests, 138 passed, 0 failures, 0 errors, 1 skipped**. The skipped test is the opt-in synthetic 100k test. The Mockito agent argument avoids a Windows self-attach pipe failure in this test environment. Fresh test H2 applied Flyway V1–V10.

Frontend command from `frontend`: `npm.cmd run build`.

Result: exit 0; TypeScript passed; Vite 6.4.6 built 37 modules; static bundle sync completed.

Preserved prior synthetic 100k H2 measurements (not rerun in this work): upload/enrichment/reconciliation **234,782 ms**; first and last 100-row pages **869 ms**; dashboard aggregation **2,137 ms**; XLSX export **105,461 ms**, **11,477,918 bytes**. These local measurements are not production guarantees.

## Remaining decisions and limitations

- USER permission to view report-section metadata/catalog remains undefined; the safe behavior is ADMIN-only.
- Confirm employee eligibility against active HeadCount before enabling that policy in account provisioning.
- Confirm AppSec Finding ID/Record Id mapping before AppSec finding identity/comments are enabled.
- Confirm what qualifies as a complete authoritative snapshot before enabling automatic closure.
- Approve the due-date source and calculation rules before reintroducing SLA; do not derive SLA from Compliance Status.
- Confirm hostname suffix and owner/application mapping rules against source data.
- Provide the approved TransUnion CIBIL SVG and corporate PowerPoint template when available.
- PostgreSQL behavior, production performance, restore procedures, browser-based organizational UAT and real report-result accuracy are not verified.
- Git CLI is unavailable in this workspace, so a repository diff/status could not be produced. Package moves are reflected in the current source tree; see the architecture tree above.

## Manual UAT checklist

1. Start the backend with `VAPT_ADMIN_INITIAL_PASSWORD` set for first-time bootstrap; start Vite on port 3000; sign in as ADMIN and confirm `/api/auth/me` reports `ADMIN`.
2. Upload a valid test file to CMDB and a report section. Verify requests no longer return 403, CSRF cookie/header are present, progress/error details are visible, history updates and correlation IDs match backend logs.
3. Sign in as USER; confirm USER cannot upload, read import history, browse section catalog, administer CMDB, or open `/h2-console`. Confirm finding rows/aggregates are limited to explicitly assigned scopes.
4. As ADMIN, create a USER and verify normalized email, default USER role, PENDING status, no scopes, one-time activation link, expiration, single-use behavior and password not returned/logged. Assign scopes explicitly and test allowed/denied assets.
5. Open H2 Console from localhost as ADMIN in dev; confirm non-loopback ADMIN is denied and production profile denies the route.
6. Upload representative CMDB exports and reports; verify worksheet/header selection, mapping, enrichment, AppSec isolation, findings and dashboards against source files.
7. Verify risk and Compliance Status donut charts; confirm no duplicate SLA chart/column or fabricated due dates.
8. Exercise comments, remediation, lifecycle/RCA, exports and audit history with scoped USER and ADMIN accounts. Check original report contents never appear in logs.
9. Record observed results and timings on the intended deployment database/machine. Do not treat local H2 scale figures as production acceptance.

## Exact next task

**Administrator and USER manual UAT using representative organizational CMDB and report files**, including browser verification of the fixed upload flow and business-owner confirmation of unresolved policies. Fix only defects found during that UAT.