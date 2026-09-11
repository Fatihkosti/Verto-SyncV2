# Verto Logistics v236 — Implementation Contract

## Source of truth

v236 starts from `Verto-v235-source-of-truth.zip` and preserves the v235 contract in `docs/logistics/v235/V235_IMPLEMENTATION_CONTRACT.md` plus the v234 master UX plan in `docs/logistics/v234/VERTO_LOGISTICS_UX_IMPLEMENTATION_PLAN_v234-v242_REVISED.md`.

## Implemented scope

v236 delivers the dedicated **الموردون والفواتير** planning slice before route planning:

- Supplier search/selection shows only suppliers represented by eligible international purchase invoices.
- Multiple suppliers are supported as separate visual groups; suppliers are never merged.
- Each supplier group shows selected invoices and supports adding more invoices through a searchable multi-select bottom sheet.
- The invoice sheet shows invoice number, invoice date, total, and readiness status only. Item rows, item images, item prices, and item names remain hidden.
- A selected invoice card exposes only planned carton count, planned weight, and expected readiness date, with inline validation.
- Carton count must be a positive integer, weight a positive decimal, and expected readiness date is required before advancing.
- Removing an invoice or supplier requires confirmation and mutates the draft only.
- Purchase selection is now a distinct wizard boundary: PURCHASE advances to ROUTE only after the purchase slice validates and saves.
- Loading, empty, retryable error, save-progress, and save-error states are present.
- The invoice card retains the bundled v236 reference structure and reflows metrics for narrow width / large font scale.

## Eligibility and conflict contract

An invoice can be selected only when all of the following hold:

- category is PURCHASE;
- purchase scope is INTERNATIONAL;
- supplier is a GLOBAL_SUPPLIER;
- invoice is not voided;
- it has remaining shippable quantity;
- its hidden invoice-line inventory identities are resolvable;
- it is not linked to another conflicting active shipment.

Conflict protection is enforced twice:

1. the purchase-invoice query excludes conflicting invoices before the chooser is built;
2. the Room transaction re-checks every selected invoice immediately before persisting the purchase plan.

The current shipment is excluded from its own conflict check. Cancelled-before-start shipments release their invoice association. Existing closed allocations continue to be respected by remaining-quantity calculation.

## Snapshot and persistence contract

- Room schema remains **61**; v236 adds no database migration.
- Existing `LogisticsShipmentSource` persistence fields are the durable v236 planning metadata:
  - `plannedPackageCount`
  - `plannedWeightKg`
  - `expectedReadyAt`
- Selected invoice source/line snapshots continue to be rebuilt from the live eligible purchase invoice at save time, preserving supplier name, invoice number, inventory identity, item-name snapshot, quantity, and purchase-unit-price snapshot inside the domain/persistence layer while not rendering item details in the v236 UI.
- Invoice date and total are chooser metadata only and do not change the Room contract.
- Autosave continues to persist partial purchase drafts so carton/weight/date edits restore after recreation/process restart through the existing Room mapping.
- Partial draft values may be persisted while editing; the PURCHASE Next boundary is the enforcement point that requires all v236 fields.

## Exact v236 validation / state text

- `اختر موردًا`
- `أضف فاتورة واحدة على الأقل`
- `أدخل عدد الكراتين`
- `أدخل الوزن`
- `حدد تاريخ الجاهزية`
- Empty invoice sheet: `لا توجد فواتير دولية متاحة لهذا المورد`

## Compatibility rules

- No module or dependency-graph changes.
- No Room version change, destructive fallback, or migration addition.
- v235 country identity and country-visibility rules remain unchanged.
- v236 does not create route execution events, payment facts, transport facts, or landed-cost postings.
- Legacy source-selection UI was removed from the ROUTE screen so PURCHASE has one authoritative UI.

## Verification status

- Domain models compile directly with the local Kotlin compiler.
- The pure v236 presentation/rules slice compiles directly with local stubs for UI-session types and passes a smoke execution covering validation, search privacy, multiple suppliers, removal, and carton/weight totals.
- `SaveShipmentPurchasePlanUseCase` compiles directly with minimal local port/injection stubs.
- Static Kotlin quality matches v235 with no regression: architecture violations 18, broad catches 20, dependency cycles 0, excessive parameter lists 451, large files 12, long functions 330.
- Full Gradle tests remain environment-limited because Gradle 8.9 is not cached locally and the sandbox has no network access.
- Automated screenshot/golden and device checks remain environment-limited for the same reason; source/layout checks cover the bundled v236 reference structure, RTL ordering, 320dp reflow path, and large-font reflow path.
- The repository session guard retains the pre-existing missing exported Room schema `61.json`; v236 does not fabricate a schema export because the schema is unchanged.

## Source of truth for v237

Start from the packaged v236 tree. v237 owns trip type + route + repeated station-detail screens. Do not recreate supplier/invoice selection there. Use `v236PlannedPackageTotal` and `v236PlannedWeightTotal` as the initial planned carton/weight defaults for station planning, while keeping per-invoice source metadata intact and separate from actual execution values.
