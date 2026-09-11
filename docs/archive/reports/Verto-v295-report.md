# Verto v295 Execution Report

## Provenance

- Input ZIP: `Verto-v294-source-of-truth.zip`
- Input SHA-256: `c92ed2b0810421f1665367f10f1a1db569f58477fbf851b9d6a26ead26008aba`
- v294 prerequisite: migration pre-gate `PASS`; source metrics exactly matched the session contract.

## Pre-scan metrics

```json
{
  "legacyFocusedHardcodedCount": 74,
  "hardcodedUserFacingStringsFull": 386,
  "coreVisibleLiteralDebt": 8,
  "rawMaterialMustWrap": 214,
  "forbiddenMaterial": 0,
  "rawDpOutsideApprovedTokenFiles": 22,
  "rawSpOutsideApprovedTokenFiles": 0,
  "rawColorOutsideApprovedTokenFiles": 0,
  "rawMotionDurations": 5,
  "coreBoundaryViolations": 0,
  "designSystemDomainStringCount": 820,
  "externalDesignSystemDomainStringReferences": 1214,
  "expiredExceptions": 0,
  "permanentExceptions": 0,
  "duplicateGuardedPrimitives": 0
}
```

## Exact Core hardcoded findings before

| File | Line | Rule/context | Literal | User-facing | Action |
|---|---:|---|---|---|---|
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt` | 364 | `UI_PARAMETER` | `Error` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt` | 369 | `UI_PARAMETER` | `Offline` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 43 | `TEXT` | `التالي` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 43 | `TEXT` | `تأكيد` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 47 | `TEXT` | `إلغاء` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 47 | `TEXT` | `رجوع` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 52 | `TEXT` | `تاريخ البداية` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt` | 54 | `TEXT` | `تاريخ النهاية` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 301 | `COMPOSABLE_DEFAULT` | `السماح` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 337 | `TEXT` | `ليس الآن` | yes | moved to Core-owned generic string resource |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 352 | `COMPOSABLE_DEFAULT` | `إلغاء` | yes | moved to Core-owned generic string resource |

## VertoListRow discovery

- Declaration: `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:323` in v294 (`:325` after added imports).
- Signature: `fun VertoListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null, onClick: (() -> Unit)? = null)`.
- v294 behavior: `onClick` existed but only conditional button-role semantics were added; no click handler was installed.
- Production consumers: **0** (`*/src/main/*`, declaration excluded).
- Test consumers: **0**.
- Final implementation: `Modifier.clickable(role = Role.Button, onClick = click)` is applied only when `onClick != null`; null retains the non-clickable modifier chain.

## Active-ledger coupling

- Active v291 entries before: **29**.
- Active entries targeting modified Core production files: **0**.
- Active v291 entries after: **29**.
- No exception added, allowance raised, count edited, or source hash refreshed.

## Files changed

- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt`
- `core/designsystem/src/main/res/values/strings.xml`
- `docs/design-system/CORE-API-REVIEW-v295.md`
- `docs/design-system/COMPONENT-CATALOG.md`
- `docs/design-system/CURRENT-HARDCODED-MANIFEST.json`
- `docs/design-system/CURRENT-DESIGN-STATE.md`
- `docs/design-system/MIGRATION-LEDGER.md`

## Resources

Reused: `verto_navigate_back` (`رجوع`).

Added generic Core resources: `verto_action_next`, `verto_action_confirm`, `verto_action_cancel`, `verto_action_allow`, `verto_action_not_now`, `verto_date_start`, `verto_date_end`, `verto_state_error`, `verto_state_offline`.

No `ds_*` resource was added; `designSystemDomainStringCount` stayed at **820**.

## Core hardcoded findings after

- `coreVisibleLiteralDebt`: **0**.
- Full hardcoded manifest Core findings: **0**.
- Global `hardcodedUserFacingStringsFull`: **375** (386 → 375).

## Post-scan metrics

```json
{
  "legacyFocusedHardcodedCount": 74,
  "hardcodedUserFacingStringsFull": 375,
  "coreVisibleLiteralDebt": 0,
  "rawMaterialMustWrap": 214,
  "forbiddenMaterial": 0,
  "rawDpOutsideApprovedTokenFiles": 22,
  "rawSpOutsideApprovedTokenFiles": 0,
  "rawColorOutsideApprovedTokenFiles": 0,
  "rawMotionDurations": 5,
  "coreBoundaryViolations": 0,
  "designSystemDomainStringCount": 820,
  "externalDesignSystemDomainStringReferences": 1214,
  "expiredExceptions": 0,
  "permanentExceptions": 0,
  "duplicateGuardedPrimitives": 0
}
```

## VertoListRow tests

- Required tests T295-LR-01..05 were **not added/executed** because the module has only `src/test` with `testImplementation(libs.junit)` and no Compose UI test dependency.
- Adding Compose UI test dependencies or changing Gradle/source-set infrastructure is forbidden by session 295.
- `./gradlew :core:designsystem:tasks --all --no-daemon` could not run because the wrapper attempted to download Gradle 8.9 and failed with `java.net.UnknownHostException: services.gradle.org`.
- Therefore no false claim is made for `performClick()` or semantics test PASS.

## Compile result

- `BLOCKED_ENVIRONMENT`: Gradle wrapper distribution 8.9 is not cached and network access is unavailable. `:core:designsystem:compileDebugKotlin` could not be executed without changing infrastructure.

## Gates

- Migration Gate: **PASS**.
- Diff compatibility gate: **PASS**.
- Contract verifier: **PASS** (`DESIGN_SYSTEM_CONTRACT PASS 0`).
- Snapshot: `c758c2a434d05ed51b22b1771b4f2ee746177a91ddd952f9e62e3c3f2e67d561`.

## API review summary

- `docs/design-system/CORE-API-REVIEW-v295.md` reviews 45 public/general top-level UI functions in Core.
- No API was deleted, renamed, moved or internalized in 295.
- `VertoListRow` is `ADOPT_CANDIDATE`; zero production consumers are documented rather than used as a reason to delete it.
- `VertoPermissionPrompt` is a zero-consumer `REMOVE_CANDIDATE`; `VertoConfirmationDialog` remains actively consumed by `feature/shipment`.

## Out-of-scope observations

- Existing global debt remains intentionally for sessions 296–303: raw Material 214, raw dp 22, raw motion 5, Design System domain strings 820, external DS references 1214.
- No feature/business/navigation/data/sync/semantic-color work was performed.

## Final verdict

**SESSION 295 = IMPLEMENTED_STATIC / TEST_ENVIRONMENT_BLOCKED**

The production correctness changes and all static design-system gates are complete. Session 295 cannot be declared full PASS or packaged as `Verto-v295-source-of-truth.zip` because the mandatory VertoListRow interaction tests were not executable.
