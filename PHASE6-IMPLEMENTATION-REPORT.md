# Phase 6A — Finding Identity and Observation Foundation

**Status:** Implemented. The Phase 5 host fallback verification and Phase 6A identity/observation foundation are in place. This is not full Phase 6 weekly reconciliation.

## Phase 5 verification

`HostnameNormalizerTest` verifies the configured suffix mapping `company.web01.com` to `web01`; the report enrichment integration test verifies the resulting JAS asset and linked HeadCount/ApplicationInventory enrichment. Existing exact IP and FQDN matching remains covered. No suffix is inferred outside configuration.

## UID profile status

The requested aggregate profile could not be run against the committed local development imports. The H2 development database was exclusively locked by another process and could not be queried safely. No lock bypass or unverified database copy was used. No raw UID values were read or emitted.

| UID report section | Rows | Blank UIDs | Duplicate UIDs per import | Valid unique | Overlap across latest imports | Stability verifiable |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| GDP_VA_REPORT | unavailable | unavailable | unavailable | unavailable | unavailable | no |
| INTERNAL_VA_BRINQA | unavailable | unavailable | unavailable | unavailable | unavailable | no |
| WIZ | unavailable | unavailable | unavailable | unavailable | unavailable | no |
| VERIZON_26_NETWORK_PT | unavailable | unavailable | unavailable | unavailable | unavailable | no |
| PT_VERIZON_GCP | unavailable | unavailable | unavailable | unavailable | unavailable | no |

AppSec Findings PT is intentionally excluded; its Finding ID/Record Id identity and mapping requirements are not established by this work. No cross-import stability claim is made for the five UID sections. A database-backed aggregate profile is the first follow-up once safe read-only access to committed import data is available.

## Implemented foundation and guardrails

- Added Flyway V7 for reconciliation runs, section-scoped findings, per-row observations and append-only supported lifecycle events. V1–V6 were not modified.
- Removed V4's unique `(import_batch_id, source_identity)` restriction in V7 so missing and duplicate general-report UIDs can be retained and observed as invalid identity rows. Added a non-unique lookup index. No UID uniqueness constraint is asserted for findings because the source profile is unavailable.
- Reconciliation is integrated into the existing `RECONCILING` stage for GDP VA Report, Internal VA Brinqa, Wiz, VERIZON 26 Network PT and PT Verizon GCP. AppSec remains outside UID reconciliation and retains its prior Finding ID validation.
- Finding identity is `(report_section_id, source_uid)`. Section rows are pessimistically locked during processing; the worker uses keyset pages and bounded flush/clear chunks (default 250, maximum 1000) in one transactional worker call. Concurrent imports for the same section serialize.
- NEW and EXISTING_OPEN are the only matched identity transitions. Missing UID, duplicate UID within an import, duplicate canonical identity and unsupported states create UNMATCHED_IDENTITY observations. Source report rows remain intact; observations link to their source row and batch.
- The only persisted lifecycle state is OPEN. There is no closure, reopen, return, RCA, complete-snapshot or Compliance Status mapping behavior. Invalid identity rows do not create findings or lifecycle events.
- If reconciliation fails, its worker transaction rolls back observation/finding changes; the existing import failure path marks the new batch failed, and the prior active report batch remains active.

## Verification

- Backend: `.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Manohar/Development/VAPT-Management/.m2/repository' -q test` from `backend` exited 0. **126 tests, 13 suites, 0 failures, 0 errors, 0 skipped.** This includes the same-section concurrent import test.
- Frontend: `npm.cmd run build` from `frontend` exited 0. TypeScript passed; Vite built 33 modules and synchronized the static bundle.
- Flyway V1–V7 validated in H2. PostgreSQL integration has not been run.

## Exact Phase 6A files changed

- `backend/src/main/resources/db/migration/V7__finding_identity_observation_foundation.sql` (new)
- `backend/src/main/java/com/company/vapt/finding/application/FindingReconciliationCoordinator.java` (new)
- `backend/src/main/java/com/company/vapt/finding/application/FindingReconciliationFinalizer.java` (new)
- `backend/src/main/java/com/company/vapt/finding/application/FindingReconciliationService.java` (new)
- `backend/src/main/java/com/company/vapt/finding/application/FindingReconciliationWorker.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/Finding.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingEvent.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingEventRepository.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingObservation.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingObservationRepository.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingReconciliationRun.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingReconciliationRunRepository.java` (new)
- `backend/src/main/java/com/company/vapt/finding/persistence/FindingRepository.java` (new)
- `backend/src/main/java/com/company/vapt/ingestion/application/ReportIngestionService.java`
- `backend/src/main/java/com/company/vapt/ingestion/application/ReportRecordPersistenceService.java`
- `backend/src/main/java/com/company/vapt/ingestion/application/ReportFileParser.java`
- `backend/src/main/java/com/company/vapt/ingestion/persistence/ReportImportRecord.java`
- `backend/src/main/java/com/company/vapt/ingestion/persistence/ReportImportRecordRepository.java`
- `backend/src/test/java/com/company/vapt/enrichment/HostnameNormalizerTest.java`
- `backend/src/test/java/com/company/vapt/enrichment/ReportToCmdbEnrichmentIntegrationTest.java`
- `backend/src/test/java/com/company/vapt/ingestion/ImportControllerIntegrationTest.java`
- `PHASE6-IMPLEMENTATION-REPORT.md` (new)
- `HANDOFF.md`
- `VAPT-HANDOFF.md`

## Unresolved issues and next task

The real-data UID completeness, per-import uniqueness and cross-import stability profile remains unavailable. Lifecycle policy for closure/reopen/return and RCA is undefined; complete-snapshot semantics are not declared. These behaviors remain disabled.

**Next task: Phase 6B — safely profile committed UID imports for the five general sections, then confirm identity stability and lifecycle/snapshot/RCA policy before implementing broader weekly reconciliation behavior.** Keep GDP VA Report and Internal VA Brinqa independent. Keep AppSec separate pending a defined identity mapping.
