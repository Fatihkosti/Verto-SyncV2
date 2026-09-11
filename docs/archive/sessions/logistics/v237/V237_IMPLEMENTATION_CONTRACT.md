# Verto Logistics v237 — Implementation Contract

## Source of truth

v237 starts from `Verto-v236-source-of-truth.zip`, preserves the v236 purchase-planning boundary, and implements §§5.3–5.5 of `docs/logistics/v234/VERTO_LOGISTICS_UX_IMPLEMENTATION_PLAN_v234-v242_REVISED.md`.

## Implemented scope

v237 owns exactly three route-planning screens:

1. **نوع الرحلة — تفاصيل الشحنة 2 من 6**
   - Explicit fixed/mixed selection.
   - Fixed requires one of ROAD/SEA/AIR and applies it to every movement.
   - Mixed requires ROAD/SEA/AIR per movement in station details.
   - Moving from mixed back to fixed requires an explicit unified mode selection before replacing future movement modes.

2. **بناء المسار — تفاصيل الشحنة 3 من 6**
   - Origin and destination are read-only anchors from shipment definition.
   - Intermediate stations can be added, edited, reordered, and deleted with confirmation.
   - Reordering/deleting reconciles adjacent legs; changed movement pairs must be reviewed again.
   - Immediate duplicate station names require explicit confirmation.
   - Country is never rendered in this route UI.
   - The customs continuation row is present; v238 owns the customs screen/event contract.

3. **بيانات المحطة — تفاصيل الشحنة 4 من 6**
   - Repeated per planned movement target so every movement has an expected duration and planned operational defaults.
   - Implements station name, conditional mixed-mode transport, shipping company autocomplete/free text, responsible-person autocomplete/free text, phone, cartons, weight, planned cost/currency/exchange rate/local value, transfer-proof attachment, and expected duration with hours/days.
   - Phone is normalized without a country picker.
   - Company/responsible/phone and attachment are optional; cartons, weight, and duration are required positive values. Planned amount is optional but cannot be negative; foreign exchange rate must be positive when amount is present.

## Planning versus execution boundary

All v237 station values are planning defaults only:

- `plannedCarrierPartnerId` / `plannedCarrierNameSnapshot`
- `plannedRepresentativeNameSnapshot` / `plannedRepresentativePhoneSnapshot`
- `plannedPackageCount` / `plannedWeightKg`
- `plannedCost`
- `plannedProof`
- `expectedTransitMinutes` (with legacy `expectedTransitDays` compatibility)

Actual carrier, actual cargo count/weight, actual timestamps, execution representative data, custody transfers, cashbox postings, expenses, and departure/arrival events remain absent from the v237 write path.

## v236 integration

- Initial per-movement cartons use `v236PlannedPackageTotal`; later newly-created movements inherit the previous planned movement when available.
- Initial per-movement weight uses `v236PlannedWeightTotal`; later newly-created movements inherit the previous planned movement when available.
- Supplier/invoice UI remains exclusively in PURCHASE; v237 does not recreate it.

## Draft durability

`LogisticsRouteWorkspaceSnapshot` now durably stores:

- explicit trip-type selection;
- explicit unified-mode selection;
- route sub-screen cursor;
- intentional duplicate confirmations;
- all v234 planned leg fields, including canonical minute duration and planned attachment/cost fields.

The DataStore DTO remains backward-compatible through defaults. Staged planning documents are no longer erased by ordinary route edits. Room remains unchanged.

## Route persistence

`SaveShipmentRouteCommand` carries an optional backward-compatible `LogisticsRouteTransportIntent` containing the explicit route kind and optional unified mode for v237 callers. `SaveShipmentRouteUseCase` enforces:

- UNIFIED: one concrete mode and every leg matches it;
- MIXED: no unified mode and every leg has a concrete ROAD/SEA/AIR mode.

The selected route kind is persisted on the shipment. No Room migration is required because these shipment fields already exist.

## Validation and accessibility behavior

- Next remains actionable; invalid input reveals inline errors rather than failing silently.
- Route details counter is 2/6 → 3/6 → 4/6.
- All content is vertically scrollable under the fixed action bar and remains compatible with narrow screens / large font scales.
- Route actions expose touch targets; long-press drag performs reorder, while move up/down menu actions remain accessible alternatives alongside edit/delete.

## Architecture / schema

- Room schema: **61 → 61**.
- Migrations: **none**.
- Modules/dependencies: **unchanged**.
- v236 supplier/invoice contract: **preserved**.
- v235 country reporting identity: **preserved while route UI hides country**.

## Verification status

- v237 pure route rules compile with the local Kotlin compiler against real shipment domain models.
- Static source audit confirms no country picker, TODO/stub marker, or execution-write marker in the v237 route UI.
- v237 unit tests cover fixed/mixed mode behavior, purchase/previous-movement defaults, reorder reconciliation, duplicate confirmation, decimal entry, and local/foreign planned-cost behavior.
- Full Gradle tests and screenshot/golden checks are environment-limited because Gradle 8.9 is not cached and the sandbox has no network access.

## Source of truth for v238

Start from packaged v237. v238 owns **الجمارك — تفاصيل الشحنة 5 من 6** and review/approval integration. Customs must remain a separate planned event after a station, never a route milestone. Preserve v237 station fields and route intent exactly.
