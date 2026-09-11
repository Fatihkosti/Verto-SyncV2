# Verto v237 Implementation Report

## Result

v237 implements §§5.3–5.5: trip type, route construction, and repeated station-detail planning on top of v236 without changing Room schema, migrations, modules, or dependencies.

## Delivered

### نوع الرحلة — 2/6

- [x] Explicit fixed / mixed choice.
- [x] Fixed ROAD / SEA / AIR choice.
- [x] Mixed mode hides the global choice and requires a mode for every incoming movement.
- [x] Fixed → mixed preserves the old fixed mode as a visible suggestion only; it does not auto-select movement modes.
- [x] Mixed → fixed requires a fresh unified-mode confirmation before replacing leg modes.
- [x] Exact validation messages: `اختر نوع الرحلة` and `اختر نوع النقل`.

### بناء المسار — 3/6

- [x] Vertical route timeline with immutable origin and destination.
- [x] Route UI renders city/station only; country is absent.
- [x] Add and edit intermediate stations.
- [x] Long-press drag handle performs reorder; move-up/down menu actions remain accessible alternatives.
- [x] Delete lives in the overflow menu and requires confirmation.
- [x] Reorder/delete reconcile adjacency and invalidate new movement pairs for re-entry.
- [x] Immediate duplicate station names require deliberate confirmation.
- [x] `تحديد نقطة الجمارك` continuation exists; v238 owns the customs event screen.

### بيانات المحطة — 4/6

- [x] Separate repeated screen with `المحطة x من y`.
- [x] Station name + historical route suggestions.
- [x] Mixed-only incoming ROAD / SEA / AIR selection.
- [x] Shipping company autocomplete plus free text.
- [x] Responsible-person autocomplete plus free text; saved carrier contact can fill phone.
- [x] Phone accepts `+`, digits, and spaces visually and stores a normalized value; no country picker.
- [x] Cartons and weight are positive planning values.
- [x] Initial cargo defaults come from v236 invoice totals; newly created movements inherit the previous planned movement when available.
- [x] Planned amount, currency, exchange rate, and read-only base-currency value.
- [x] Transfer-proof attachment with staging progress, retry, open, replace, size, and delete behavior.
- [x] Expected duration in hours/days with helper `من المحطة السابقة`.
- [x] Company/responsible/phone/cost/attachment remain optional; required fields validate inline.

## Planning / execution boundary

All v237 station values are planning defaults. Saving route data does **not** create cashbox entries, expenses, custody transfers, actual timestamps, actual carrier/cargo facts, or execution events other than the existing plan-updated audit event.

Planned fields are persisted through the existing v234 contract:

- `plannedCarrierPartnerId` / `plannedCarrierNameSnapshot`
- `plannedRepresentativeNameSnapshot` / `plannedRepresentativePhoneSnapshot`
- `plannedPackageCount` / `plannedWeightKg`
- `plannedCost`
- `plannedProof`
- `expectedTransitMinutes` with legacy `expectedTransitDays` compatibility

## Draft durability

- Route sub-screen cursor, explicit trip choice, explicit fixed mode, and duplicate confirmations survive process recreation.
- DataStore now round-trips all v234 planned leg fields and canonical minute duration.
- Staged planning documents are preserved across normal route edits instead of being cleared.
- DataStore DTO additions use defaults, so v236 workspace JSON remains readable.

## Route persistence

`SaveShipmentRouteCommand` now accepts an optional `LogisticsRouteTransportIntent` for backward-compatible v237 callers. `SaveShipmentRouteUseCase` enforces:

- UNIFIED: one concrete mode and every leg must match it.
- MIXED: no unified mode and every leg must have an explicit concrete ROAD/SEA/AIR mode.

The selected route kind/unified mode uses shipment fields that already existed before v237; no migration is required.

## Files added

- `docs/logistics/v237/V237_IMPLEMENTATION_CONTRACT.md`
- `docs/logistics/v237/V237_SESSION_ALLOWLIST.txt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Rules.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237StationStep.kt`
- `feature/shipment/src/test/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV237RoutePlanningTest.kt`
- `Verto-v237-report.md`

## Files modified

- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsDraftProgressDataStore.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/SaveShipmentRouteUseCase.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/model/LogisticsCommands.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsDraftProgressStore.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsPlanningScaffold.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2OperationalWriter.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2PresentationModels.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2ViewModel.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningScreen.kt`

## Verification

### Passed

- Pure v237 rule layer compiled with local `kotlinc 1.9` against the real shipment domain models.
- `SaveShipmentRouteUseCase` plus the shipment domain/ports/validation compiled locally with `kotlinc 1.9`; only pre-existing deprecated-CUSTOMS warnings were emitted.
- A local rule smoke run passed fixed/mixed mutation, phone/decimal normalization, and previous-movement cargo inheritance (`V237_RULE_SMOKE_PASS`).
- Static architecture scan: v236 → v237 remains **18 violations**, **0 dependency cycles**, and **12 production files >500 lines**; no architecture-regression count was introduced.
- Session scope guard: **7 added, 9 modified, 0 deleted, 0 outside allowlist**.
- Module count: **31 → 31**.
- Room version: **61 → 61**.
- Source audit: no TODO/FIXME/stub marker or country field/picker in v237 route UI.
- Added rule tests cover fixed/mixed mode, previous/invoice cargo defaults, reorder reconciliation, duplicate confirmation, decimal input, and local/foreign planned-cost behavior.

### Pre-existing source-tree guard failures

The official logistics session guard reports the same failures when run against **v236 compared with itself** and against v237:

- missing exported `app/schemas/com.verto.app.data.local.AppDatabase/61.json`;
- legacy declarations expected by the guard are already absent: `shipment_costs`, `shipment_documents`, `shipment_receipts`, `shipment_stops`, `shipments`.

These are baseline conditions, not v237 regressions.

### Environment-limited

- Full Gradle compile/tests cannot run because Gradle 8.9 is not cached and this sandbox has no network access; wrapper download fails at `services.gradle.org`.
- Consequently device screenshot/golden checks for 360dp, 320dp, and Font Scale 2.0 cannot be executed here. The v237 layouts use the existing scrollable planning scaffold, 48dp minimum action targets, and compact route-row actions to preserve those constraints in source.

## v238 handoff

Start v238 from this packaged source. Read `docs/logistics/v237/V237_IMPLEMENTATION_CONTRACT.md`. v238 owns customs as a separate planned event (5/6), review/approval (6/6), deep-link edit return, and plan revision/idempotency behavior. Customs must never become a route milestone.
