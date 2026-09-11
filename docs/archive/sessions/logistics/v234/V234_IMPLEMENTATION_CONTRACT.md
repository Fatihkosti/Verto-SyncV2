# Verto Logistics v234 — Implementation Contract

This folder is the self-contained source of truth for v235+.

## Sources bundled in the project
- `VERTO_LOGISTICS_UX_IMPLEMENTATION_PLAN_v234-v242_REVISED.md`: functional + UX implementation plan.
- `assets/logistics-planning/00..06-*.png`: mandatory planning-screen visual references.
- Canonical code contract: `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/model/LogisticsV234Contract.kt`.
- Planning/cancellation policy: `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/policy/LogisticsV234PlanningPolicy.kt`.
- Additive Room migration: `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations60To61.kt`.

## v234 decisions that v235 must preserve
1. Shipment planning has a durable plan revision. Revision 1 is initial approval; later revisions are auditable future edits.
2. Stations are route places. Legs are movements between adjacent stations.
3. Customs is an event positioned after a station; it is not a new route station. Legacy `CUSTOMS` milestones stay readable only for old data.
4. Planning values and execution facts are separate. Planned carrier/contact/cargo/cost/proof never create cash, expense, custody or movement events.
5. Canonical planned movement duration is minutes; legacy `expectedTransitDays` remains readable and is backfilled to minutes by migration 60→61.
6. Origin/destination retain legacy display strings plus explicit country-key/name/city fields for v235 migration-safe adoption.
7. Purchase-source planning stores planned cartons, weight and readiness date without altering invoice snapshots.
8. Started/completed movement and station history is immutable. Only future stages can change after approval, with `PlanRevision` reason/user/time/change list.
9. Cancelling an unstarted shipment releases invoice reservation. Cancelling a started shipment preserves allocation/history.
10. Production permanent deletion is limited to an unstarted DRAFT. Executed hard-delete remains only an explicit development capability.
11. Existing inventory, landed-cost, custody, repack and receiving rules are retained; v234 adds contract/data protections without rebuilding UI.

## Persistence
- Room schema version: **61**.
- Migration 60→61 is additive/non-destructive.
- New tables: `logistics_customs_plans`, `logistics_customs_plan_documents`, `logistics_plan_revisions`, `logistics_plan_revision_changes`.
- New shipment/source/leg columns carry explicit planning data while legacy columns remain compatible.

## Verification performed in v234
- Domain model/policies/validation compiled directly with `kotlinc`.
- Domain smoke test passed.
- Migration 60→61 executed against schema-60 SQLite and passed `PRAGMA foreign_key_check` with zero violations.
- Gradle task execution could not start in the sandbox because Gradle 8.9 was not cached and external network access is disabled.

## v235 start rule
Use only this project ZIP. Do not request separate images or plan files; they are already included under this folder.
