# Verto Logistics v235 — Implementation Contract

## Source of truth

v235 starts from `Verto-v234-source-of-truth.zip` and preserves the v234 logistics/domain contract in `docs/logistics/v234/V234_IMPLEMENTATION_CONTRACT.md`.
The UX requirements are defined by `docs/logistics/v234/VERTO_LOGISTICS_UX_IMPLEMENTATION_PLAN_v234-v242_REVISED.md` and its bundled reference image for the shipment-definition screen.

## Implemented scope

v235 delivers the first saveable planning-Wizard vertical slice and the shipment-definition screen:

- Planning header uses the Verto design-system top bar and two-stage planning progress model.
- Definition is `1 من 2`; subsequent details use the shared six-step detail counter.
- Back/Next actions stay fixed below scrollable content and respect safe-area/IME padding.
- The shipment number is domain-generated and read-only.
- Origin and destination use free country entry plus city entry.
- Country suggestions come only from prior shipment usage, appear after typing, and rank by frequency then recency.
- New country text remains valid; no static/global country list is required.
- Country values are normalized to a stable internal key while preserving a normalized user-facing name.
- Partial definition edits persist through the existing draft-progress/store path and restore after recreation/process death.
- Definition validation uses the exact v235 Arabic error messages and brings the first invalid field into view.
- Follow-up employee selection remains restricted to active employees through the existing read model.
- Leaving the first planning screen asks to save the draft and exit; normal Back inside later planning steps moves one step without discarding the draft.
- Planning route metadata retains country identity internally while post-definition visible labels use city/place only.
- Review, route, execution timeline, unplanned-station entry, analytics, and shipment summary paths do not render the country for newly saved v235 definitions.
- Existing inventory, landed-cost, custody, repack, receiving, cancellation, and immutable execution-history behavior is unchanged.

## Country identity contract

`LogisticsCountryNormalizer` is the canonical v235 country identity helper.

- Display name: trim, remove Arabic tatweel, collapse repeated whitespace.
- Canonical key: Unicode-normalize, remove combining marks, lowercase, collapse whitespace, then prefix with `name:`.
- Legacy two-letter uppercase ISO keys remain readable for backward compatibility.
- `LogisticsLocation.countryCode` continues to be the internal persistence slot; v235 may store a canonical `name:` key there.
- ISO/canonical keys are never intentionally rendered to the user.
- `sourceLocation` and `destinationLocation` are persisted as city-only display values by the v235 header save path; structured `originLocationDetails` / `destinationLocationDetails` carry country identity.

## Compatibility rules

- Room schema remains **61**; v235 introduces no database migration.
- No module/dependency graph change is allowed.
- Existing route-template compatibility is preserved by carrying country metadata in structured route milestones while keeping user-visible endpoint labels city-only.
- Historical records containing legacy packed location strings remain parseable by the planning compatibility helpers.

## Validation contract

The definition screen uses these exact errors:

- `تعذر إنشاء رقم الشحنة، حاول مجددًا`
- `اكتب دولة الانطلاق`
- `اكتب مدينة الانطلاق`
- `اكتب دولة الوصول`
- `اكتب مدينة الوصول`
- `يجب أن تختلف نقطة الوصول عن نقطة الانطلاق`
- `اختر مسؤول المتابعة`

Next validates only the current definition slice and does not advance on failure.

## Verification status

- Domain/model and definition validation were compiled directly with the locally available Kotlin compiler.
- Header-save application code was compiled directly with minimal local interface/injection stubs.
- Presentation-model definition logic was compiled directly and exercised by a smoke test.
- Static Kotlin quality metrics were compared with v234 and show no regression.
- The logistics session guard confirms the v235 delta has no deletions/out-of-allowlist changes, keeps 31 modules and Room 61. Its final result still fails on the unchanged v234 omission of exported Room schema `61.json`; with `--expect-legacy-removed`, that is the only remaining guard failure.
- Full Gradle compilation/unit-test execution is not verifiable in this sandbox because Gradle 8.9 is not locally cached and network access is disabled.
- Automated screenshot/golden execution is not verifiable here because the shipment module has no configured screenshot-test harness and Gradle cannot resolve its wrapper distribution.
- Device-only checks (320/360dp runtime rendering, FontScale 2.0, TalkBack traversal) therefore remain runtime verification items; the implementation includes RTL, semantics, IME, safe-area, and first-error focus/scroll handling.

## Source of truth for v236

Start from the packaged v235 source tree. Read this contract first, then the v234 contract and revised implementation plan. v236 proceeds to the suppliers/invoices planning slice without weakening the country-visibility or persistence rules above.
