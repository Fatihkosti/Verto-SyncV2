# Verto v240 Report

## Implemented

- Completed station receipt UX: same carton count is the default; discrepancy/condition/Repack controls stay hidden until needed.
- Preserved custody integrity: invalid/unconfirmed receipts do not move custody; confirmed receipt creates the handoff.
- Reconciled v234 planning with execution: customs now runs as an event after `customsPlan.afterStationId`, without creating a new CUSTOMS route station.
- Added a hard domain gate preventing loading onward from the designated station until customs is completed.
- Operational status now exposes customs start/completion automatically after unloading the designated station.
- Customs start records broker custody; customs costs use scoped actual costs and the existing payment/proof flow; customs documents are saved as milestone-scoped `CUSTOMS_DOCUMENT` records.
- Added customs timing based on the existing calendar policy and shipment timezone, excluding Friday from elapsed/delay time.
- Movement preparation is blocked while customs is pending/in progress and opens again after customs completion.
- Updated milestone validation/operational route validation so customs execution facts are valid on the designated real station while legacy CUSTOMS rows remain supported.
- Updated card/customs detection for event-based customs execution.

## Tests / verification

- 5 v240 focused tests PASS under isolated Kotlin compilation/execution:
  - planned customs event on a real transit station + custody transfer only after valid confirmation;
  - customs load bypass gate + return to AT_STATION + outgoing handoff action;
  - Friday exclusion from customs elapsed time;
  - same-count receipt defaults;
  - discrepancy reason/received-count behavior.
- Relevant v240 domain/application Kotlin compiles successfully in isolation.
- Changed presentation files pass a Kotlin parser sanity check; Android/Compose classpath compilation still requires the Gradle wrapper.
- Session guard: delta scope PASS (`outside_allowlist=0`), modules **31**, Room **61**, no deletions. The guard still reports the exact same six inherited v239 source-package failures: missing schema `61.json` plus five already-absent legacy shipment table declarations.
- Full Gradle build is not runnable from this source package because `gradle/wrapper/gradle-wrapper.jar` is absent (same packaging limitation as v239).

## Architecture

- Room schema: **61 unchanged**.
- Modules: **31 unchanged**.
- No migration, dependency catalog, settings, or build-logic changes.
- No approved planning facts are overwritten by execution.
