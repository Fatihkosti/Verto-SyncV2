# Verto v234 Report

## Scope completed
- Added canonical v234 planning/domain contract.
- Added explicit planned-vs-actual fields for shipment sources and route legs.
- Added customs-as-event model and planning documents.
- Added auditable plan revisions and future-edit policy.
- Added additive Room migration 60→61 and DAO/store bridge support.
- Protected invoice allocation when cancelling a shipment after execution started.
- Restricted production permanent deletion to unstarted drafts.
- Preserved current inventory, landed-cost, custody, repack and receiving logic.
- Added v234 characterization tests and migration-catalog test.
- Bundled the full revised UX plan and all seven visual references inside `docs/logistics/v234/`.

## Verification
- Kotlin domain compilation: PASS.
- Domain smoke: PASS.
- SQLite migration 60→61 execution: PASS.
- SQLite foreign-key check: PASS (0 violations).
- Gradle build/tests: NOT RUN — wrapper requires Gradle 8.9 download; sandbox network is disabled and distribution is not cached.

## v235 source of truth
Start from this project only. Read `docs/logistics/v234/V234_IMPLEMENTATION_CONTRACT.md` first, then the bundled revised plan and images.
