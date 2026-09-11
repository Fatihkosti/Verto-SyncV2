# Verto v296 Execution Report

## Final verdict

**SESSION 296 = PASS_STATIC / SOURCE_OF_TRUTH_ACCEPTED_BY_EXPLICIT_USER_OVERRIDE**

For this execution only, the user explicitly overrode the v295 full-PASS prerequisite and the Gradle/test/compile success requirement. Static gates are the acceptance authority. No Gradle task was run or claimed as PASS.

## Input

- Input ZIP: `Verto-v295-implemented-static.zip`
- Input SHA-256: `63297a840a621fde405d2d9d1808296985ddd430096724fc2d9e626189097ac2`
- v295 report state: `IMPLEMENTED_STATIC / TEST_ENVIRONMENT_BLOCKED`
- v295 full PASS proof: **not present; explicitly overridden for this execution**
- Pre-Migration Gate: **PASS**

## Pre metrics

- `legacyFocusedHardcodedCount` = `74`
- `hardcodedUserFacingStringsFull` = `375`
- `coreVisibleLiteralDebt` = `0`
- `rawMaterialMustWrap` = `214`
- `forbiddenMaterial` = `0`
- `rawDpOutsideApprovedTokenFiles` = `22`
- `rawSpOutsideApprovedTokenFiles` = `0`
- `rawColorOutsideApprovedTokenFiles` = `0`
- `rawMotionDurations` = `5`
- `coreBoundaryViolations` = `0`
- `designSystemDomainStringCount` = `820`
- `externalDesignSystemDomainStringReferences` = `1214`
- `expiredExceptions` = `0`
- `permanentExceptions` = `0`
- `duplicateGuardedPrimitives` = `0`
- `activeExceptionEntryCount` = `29`

## Protected files — before/after

| File | Before SHA-256 | After SHA-256 | Result |
|---|---|---|---|
| `docs/design-system/BASELINE.json` | `af64cc9250e9baa8e45bcfd58b434d9897df210bae774d6bf1532d9b2b5e659d` | `af64cc9250e9baa8e45bcfd58b434d9897df210bae774d6bf1532d9b2b5e659d` | UNCHANGED |
| `scripts/design-system-scan.py` | `12110b782b8e395becdd6b149262a20f2f75c43c2649c13e09b2903dd646e72c` | `12110b782b8e395becdd6b149262a20f2f75c43c2649c13e09b2903dd646e72c` | UNCHANGED |
| `config/design-system/v226-enforcement-exceptions.json` | `45f316b2728a7e52972e47532d590aae0b526c31b2db72fec8e31de795f46413` | `45f316b2728a7e52972e47532d590aae0b526c31b2db72fec8e31de795f46413` | UNCHANGED |
| `config/design-system/v291-exceptions.json` | `0af40bf47522157e1eabb9a704dfdf327dd7c6421da5da0f773597c7496e5bc8` | `0af40bf47522157e1eabb9a704dfdf327dd7c6421da5da0f773597c7496e5bc8` | UNCHANGED |
| `config/design-system/final-zero-targets.json` | `1a1c853f0ae3380b3c2073b97cbaf1c7ff00fbf2dd51d16249e67214b2991175` | `1a1c853f0ae3380b3c2073b97cbaf1c7ff00fbf2dd51d16249e67214b2991175` | UNCHANGED |
| `config/design-system/material-usage-policy.json` | `fb73079486af45d54fc74705f1e33ca7d40f573c3b3f3d1c1c764ba9da02f989` | `fb73079486af45d54fc74705f1e33ca7d40f573c3b3f3d1c1c764ba9da02f989` | UNCHANGED |
| `tools/design_system_diff_gate.py` | `52c19df6b5ff6210b6551b90bc14805037fc1aa0a9b0e0071c822488dacee53a` | `52c19df6b5ff6210b6551b90bc14805037fc1aa0a9b0e0071c822488dacee53a` | UNCHANGED |
| `scripts/ci/run-quality-gate.sh` | `d3a00384e25a73aa8f99ab518809b51ef3ab9e185288e15e8d38a37dc75301bf` | `d3a00384e25a73aa8f99ab518809b51ef3ab9e185288e15e8d38a37dc75301bf` | UNCHANGED |

## Current defect proven before v296

- White on `AccentBlue #3B82F6` = `3.678:1` → FAIL.
- White on `StatusRed #EF4444` = `3.763:1` → FAIL.
- `StatusRed #EF4444` on `StatusRedDim #4A171B` = `3.904:1` → FAIL.
- `ErrorColor`/`ErrorContainer` were static aliases and did not follow Light/Dark Material error mapping.

## Final semantic model

| Theme | Danger | DangerContainer | OnDanger | OnDangerContainer | OnSecondary | SecondaryContainer | OnSecondaryContainer |
|---|---|---|---|---|---|---|---|
| Light | `#B91C1C` | `#FFEEEE` | `#FFFFFF` | `#7F1D1D` | `#10111A` | `#EEF5FF` | `#1E3A8A` |
| Dark | `#F87171` | `#4A171B` | `#10111A` | `#F7F8FC` | `#10111A` | `#172D52` | `#F7F8FC` |

`ErrorColor` and `ErrorContainer` now resolve to the active `VertoColors.danger` and `VertoColors.dangerContainer`. Material3 `error/onError/errorContainer/onErrorContainer` maps to the same roles.

## Files actually changed

- `app/src/main/kotlin/com/verto/app/ui/components/Components.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionComponents.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Theme.kt`
- `core/designsystem/src/test/kotlin/com/verto/app/ui/theme/DesignSystemTokenContractTest.kt`
- `docs/design-system/DESIGN_SYSTEM_CONTRACT.md`
- `docs/design-system/MIGRATION-LEDGER.md`
- `docs/design-system/SEMANTIC-COLOR-CONTRACT-v296.md`
- `docs/design-system/TOKEN-CATALOG.md`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/CashReconciliationCard.kt`
- `tools/verify_design_system_contract.py`

A JUnit contract test was added to the existing `DesignSystemTokenContractTest.kt` source. It was **not executed** because Gradle/tests were explicitly excluded from the acceptance requirement for this run.

## Raw colors added

- `#B91C1C` (`LightDanger`) — required Light Danger role.
- `#F87171` (`DarkDanger`) — required Dark Danger role.
- No raw production color was added outside `Color.kt`; scanner confirms `rawColorOutsideApprovedTokenFiles = 0`.

## Destructive filled controls repaired

- Core `VertoConfirmationDialog`: destructive container uses theme-aware Error/Danger and content uses `OnDanger`; non-destructive content follows `OnPrimary`.
- Settings logout/reset confirmations: `ErrorColor + OnDanger`.
- Reports `CashReconciliationCard`: `ErrorColor + OnDanger`.
- Commission reject action: `ErrorColor + OnDanger`.
- Employee permissions delete action: `ErrorColor + OnDanger`.
- Organization settings solid Error/Danger clear badge: icon uses `OnDanger`.
- Commission `AccentBlue` filled completion action now explicitly uses `OnSecondary`.

## Non-composable accessor adaptation

- `HomePendingActionComponents.kt`: `ErrorColor` is captured in `PendingActionIllustration()` composable before entering `Canvas`/DrawScope, then the captured `Color` is used by drawing calls. Geometry and state behavior were not changed.

## Invoice/App review

- Invoice TimerBadge: overdue → Error/Danger pair; urgent → Warning pair; normal → Info pair.
- App TimerBadge: same mapping.
- Existing pulse timing remains `tween(800)` and was not cleaned as Motion debt.
- Small semantic label text is now opaque; the existing pulse alpha is applied to the decorative emoji so alpha compositing cannot drop the label below the 4.5 text threshold.

## Reports review

- `CashReconciliationCard` destructive content repaired.
- `ReportsDesignTokens.reportCategorySeriesColors()` categorical palette audited and left unchanged; chart categorical color selection is not redesigned in 296.
- `SmartInsightCarousel` business/presentation mapping was not reinterpreted.
- `CashFlowStatement.kt` and `ProfitLossStatement.kt` stayed read-only because of active v291 hash coupling.

## Ledger-coupled read-only proof

| File | Before SHA | After SHA | Result |
|---|---|---|---|
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/CashFlowStatement.kt` | `8a3c00e63526d96f5ec4b5c50f23bade2017b8ff9ac55446384cf1356a6f98a1` | `8a3c00e63526d96f5ec4b5c50f23bade2017b8ff9ac55446384cf1356a6f98a1` | UNCHANGED |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/ProfitLossStatement.kt` | `4d09a17b12aeae33b9f554538c949a26a0b7b4f50e89bca3ae9cfdb53e3cbec4` | `4d09a17b12aeae33b9f554538c949a26a0b7b4f50e89bca3ae9cfdb53e3cbec4` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentComponents.kt` | `5c729f064464a18923ffcc0ef15aab221b6548fca0ce919ee48c43d7ebe18a3e` | `5c729f064464a18923ffcc0ef15aab221b6548fca0ce919ee48c43d7ebe18a3e` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | `55b67e4f6b8226c3436b3bd3b7e917df33a04d8916e69c04f40e20592a6e8aab` | `55b67e4f6b8226c3436b3bd3b7e917df33a04d8916e69c04f40e20592a6e8aab` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientStatementScreen.kt` | `87e0220dd2aa810ea26e529c170bf1f75041b84e1acdcef4da03c4ad01f6e05d` | `87e0220dd2aa810ea26e529c170bf1f75041b84e1acdcef4da03c4ad01f6e05d` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | `f3b944194d4dfbef7673f1b4678bc98a5481cfaa6c9c3e195655e59abd620117` | `f3b944194d4dfbef7673f1b4678bc98a5481cfaa6c9c3e195655e59abd620117` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyStatementComponents.kt` | `7b2d568b5a31becf6e067b0f629bc723a7905f2893cb9c9a0076c7c5c4e1a6ae` | `7b2d568b5a31becf6e067b0f629bc723a7905f2893cb9c9a0076c7c5c4e1a6ae` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | `172bdc310c3ac736a0af7cf3b0ac2437a5463dfb5f80f86cfc79691827183b9a` | `172bdc310c3ac736a0af7cf3b0ac2437a5463dfb5f80f86cfc79691827183b9a` | UNCHANGED |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementScreen.kt` | `edb5c25d756718124a57bf1233109b63459e7452b37b7c1f309854326a8162c4` | `edb5c25d756718124a57bf1233109b63459e7452b37b7c1f309854326a8162c4` | UNCHANGED |

## Secondary direct-use audit

The global search for `containerColor = AccentBlue` / `background(AccentBlue)` found direct brand/accent consumers. The Commission consumer is in the allowlist and was repaired to `OnSecondary`. Other direct AccentBlue usages were audited but not mass-replaced because 296 forbids converting brand usage to Info/Secondary without role evidence; the ledger-coupled SupplierPayment consumer remained read-only.

## Error/Danger consumer inventory

- Post-change inventory: `198` source matches across `68` Kotlin files, including imports/owner declarations.
- `v291 coupling = YES` rows were kept read-only unless they are only imports/unchanged theme adoption; no active ledger entry was refreshed.

| File | Line | Usage kind | Context | v291 coupling | Action | Source |
|---|---:|---|---|---|---|---|
| `app/src/main/kotlin/com/verto/app/ui/components/Components.kt` | 32 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/components/Components.kt` | 33 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorContainer` |
| `app/src/main/kotlin/com/verto/app/ui/components/Components.kt` | 44 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `isOverdue -> ErrorContainer` |
| `app/src/main/kotlin/com/verto/app/ui/components/Components.kt` | 49 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `isOverdue -> ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/auditlog/AuditLogScreen.kt` | 203 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `AuditAction.DELETE -> ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/auditlog/AuditLogScreen.kt` | 288 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(R.string.ds_f8621d012957), color = ErrorColor, fontSize = AuditLogTextScale.sp11, fontWeight = FontWeig...` |
| `app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionComponents.kt` | 77 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionComponents.kt` | 284 | composable-capture-for-draw | captured in @Composable; draw lambda receives Color | NO | reviewed/repaired in 296 | `val errorColor = ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionComponents.kt` | 452 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `PendingActionPriority.CRITICAL -> ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActions.kt` | 75 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/onboarding/SetupNameScreen.kt` | 44 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(R.string.ds_bebea75c8a1e), color = ErrorColor, fontSize = OnboardingTextScale.sp12, modifier = Modifier...` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 124 | filled-control | Compose UI usage/audited | NO | reviewed/repaired in 296 | `colors  = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger),` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 143 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color = ErrorColor, fontWeight = FontWeight.Bold, fontSize = SettingsTextScale.sp15)` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 154 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `focusedBorderColor      = ErrorColor,` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 175 | filled-control | Compose UI usage/audited | NO | reviewed/repaired in 296 | `colors  = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger),` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 348 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `iconTint   = ErrorColor,` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 349 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `titleColor = ErrorColor,` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 359 | alpha-derived/decorative-or-token | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color    = ErrorColor.copy(alpha = 0.2f),` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 372 | alpha-derived/decorative-or-token | Compose UI usage/audited | NO | reviewed/repaired in 296 | `border   = BorderStroke(SettingsDimensions.dp1, if (isAll) ErrorColor else ErrorColor.copy(0.4f)),` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 375 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `contentColor = ErrorColor` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 399 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `border   = BorderStroke(SettingsDimensions.dp1, ErrorColor),` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 403 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Icon(Icons.Filled.Logout, null, tint = ErrorColor, modifier = Modifier.size(SettingsDimensions.dp18))` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 405 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Text(androidx.compose.ui.res.stringResource(R.string.ds_8710d64ff1ad), color = ErrorColor, fontSize = SettingsTextScale.sp14)` |
| `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt` | 442 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `is SectionState.Error   -> ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt` | 55 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 57 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 58 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 94 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `VertoStatusTone.Error -> ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 106 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `VertoStatusTone.Error -> ErrorContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt` | 359 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `val confirmColor = if (destructive) ErrorColor else AccentPrimary` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 75 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 76 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 130 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `hasError -> ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 201 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `errorBorderColor = ErrorColor,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 202 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `errorCursorColor = ErrorColor,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 484 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `VertoStatusTone.Error -> ErrorColor` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt` | 496 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `VertoStatusTone.Error -> ErrorContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 25 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `internal val LightErrorContainer = Color(0xFFFFEEEE)` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 98 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val onDangerContainer: Color = textPrimary,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 138 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `onDangerContainer = DarkTextPrimary,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 166 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `dangerContainer = LightErrorContainer,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 178 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `onDangerContainer = Color(0xFF7F1D1D),` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 215 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val DangerColor: Color @Composable get() = LocalVertoColors.current.danger` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 216 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val DangerContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 218 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val OnDangerContainer: Color @Composable get() = LocalVertoColors.current.onDangerContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 225 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val ErrorColor: Color @Composable get() = LocalVertoColors.current.danger` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt` | 226 | theme-owner/accessor | theme owner | NO | reviewed/repaired in 296 | `val ErrorContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Theme.kt` | 35 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `onErrorContainer = DarkVertoColors.onDangerContainer,` |
| `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Theme.kt` | 57 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `onErrorContainer = LightVertoColors.onDangerContainer,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 301 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `focusedBorderColor   = ErrorColor, unfocusedBorderColor = BorderColor,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 302 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `focusedLabelColor    = ErrorColor, focusedTextColor     = TextPrimary,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 311 | filled-control | Compose UI usage/audited | NO | reviewed/repaired in 296 | `colors   = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger)` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 392 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color = if (item.isAmountOverBalance) ErrorColor else TextSecondary,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 398 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color = ErrorColor,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 406 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color = ErrorColor,` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 437 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_5574893f2c9e), fontSize = CommissionTextScale.sp13, color =...` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt` | 456 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `CommissionWithdrawalStatus.REJECTED  -> ErrorColor` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionManagementScreen.kt` | 129 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_0d97fd6e069e), color = ErrorColor, fontSize = CommissionTextSca...` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/WithdrawFlowDialogs.kt` | 41 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/WithdrawFlowDialogs.kt` | 169 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor, fontSize = CommissionTextScale.sp11, fontWeight = FontWeight.SemiBold)` |
| `feature/dashboard/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UsersDashboardScreen.kt` | 61 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/dashboard/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UsersDashboardSections.kt` | 65 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/dashboard/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UsersDashboardSections.kt` | 88 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(message, color = ErrorColor, fontSize = DashboardTextScale.sp14)` |
| `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/MaintenanceDetailsScreen.kt` | 184 | container-semantic | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = MaterialTheme.colorScheme.onErrorContainer,` |
| `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt` | 27 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt` | 28 | import | Compose UI usage/audited | NO | reviewed/repaired in 296 | `import com.verto.app.ui.theme.ErrorContainer` |
| `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt` | 39 | container-semantic | Compose UI usage/audited | NO | reviewed/repaired in 296 | `isOverdue -> ErrorContainer` |
| `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt` | 44 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `isOverdue -> ErrorColor` |
| `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/ChatDetailScreen.kt` | 158 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_2d2bbdc2d694), color = ErrorColor)` |
| `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/ChatDetailScreen.kt` | 395 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Icon(Icons.Filled.Mic, null, tint = ErrorColor, modifier = Modifier.size(MessagesDimensions.dp24))` |
| `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/MessagesScreen.kt` | 130 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `}) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_2d2bbdc2d694), color = ErrorColor) }` |
| `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/MessagesScreen.kt` | 177 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `ErrorColor else Color.Transparent,` |
| `feature/notifications/src/main/kotlin/com/verto/app/feature/notifications/presentation/NotificationsScreen.kt` | 92 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt` | 166 | alpha-derived/decorative-or-token | Compose UI usage/audited | NO | reviewed/repaired in 296 | `color  = ErrorColor.copy(alpha = 0.1f),` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt` | 168 | alpha-derived/decorative-or-token | Compose UI usage/audited | NO | reviewed/repaired in 296 | `border = BorderStroke(OrganizationDimensions.dp1, ErrorColor.copy(alpha = 0.4f)),` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt` | 175 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Icon(Icons.Filled.Error, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp16))` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt` | 177 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Text(saveError, color = ErrorColor, fontSize = OrganizationTextScale.sp13)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt` | 320 | background | Compose UI usage/audited | NO | reviewed/repaired in 296 | `.background(ErrorColor)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/CreateInviteScreen.kt` | 157 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(err, color = ErrorColor, fontSize = OrganizationTextScale.sp12, textAlign = TextAlign.Center,` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeeDetailComponents.kt` | 92 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeeDetailComponents.kt` | 195 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `PermissionList("ماذا لا يرى", Icons.Filled.VisibilityOff, ErrorColor, summary.hiddenItems)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeeDetailScreen.kt` | 89 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 84 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Icon(Icons.Filled.PersonRemove, null, tint = ErrorColor)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 152 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `icon = { Icon(Icons.Filled.Warning, null, tint = ErrorColor) },` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 168 | filled-control | Compose UI usage/audited | NO | reviewed/repaired in 296 | `colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 220 | background | Compose UI usage/audited | NO | reviewed/repaired in 296 | `.background(ErrorColor.copy(alpha = 0.12f), RoundedCornerShape(OrganizationDimensions.dp10))` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 225 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Icon(Icons.Filled.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp18))` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 226 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Text(error, color = ErrorColor, fontSize = OrganizationTextScale.sp13)` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt` | 264 | foreground/accent | Compose UI usage/audited | NO | reviewed/repaired in 296 | `Icon(Icons.Filled.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp48))` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeesScreen.kt` | 189 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeesScreen.kt` | 192 | background | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `.background(ErrorColor.copy(alpha = 0.1f), RoundedCornerShape(OrganizationDimensions.dp6))` |
| `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeesScreen.kt` | 259 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Icon(Icons.Filled.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp48))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientComponents.kt` | 72 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp20))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientScreen.kt` | 245 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp20))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardScreen.kt` | 85 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `netBalance >  0.01 -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardScreen.kt` | 237 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color    = ErrorColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentComponents.kt` | 83 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color      = ErrorColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | 103 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = ErrorColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | 165 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (previewDebtAfter > 0.01) ErrorColor else SuccessColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | 234 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_348ae40b33e4), color = ErrorColor, fontWeight = FontWeight.Bold)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | 285 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (r.debtAfter > 0.01) ErrorColor else SuccessColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | 309 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `title = { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_78698483b2d9), color = ErrorColor, fontWeight = FontW...` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt` | 134 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `val balanceColor = if (remaining > 0) ErrorColor else if (remaining < 0) SuccessColor else TextMuted` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt` | 414 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_32110649b8fc), color = ErrorColor, fontWeight = FontWeight.Bold)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt` | 65 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `isOverdue -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt` | 154 | alpha-derived/decorative-or-token | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `summary.isOverdue -> ErrorColor.copy(0.4f)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt` | 252 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (invRemaining > 0 && isOwedToMe) ErrorColor else SuccessColor, fontSize = PartyTextScale.sp12)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt` | 343 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Icon(Icons.Filled.DeleteOutline, null, tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp18))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientStatementScreen.kt` | 75 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `closingBalance >  0.01 -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientStatementScreen.kt` | 138 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `val color = if (openingBalance > 0) ErrorColor else SuccessColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 233 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = ErrorColor, fontWeight = FontWeight.Bold` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 260 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_494feab789bf), color = ErrorColor, fontWeight = FontWeight.Bold)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 318 | background | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `.background(ErrorColor.copy(alpha = 0.15f))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 326 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Icon(Icons.Filled.Delete, null, tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp22))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 327 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_2d2bbdc2d694), color = ErrorColor, fontWeight = FontWeight.Bold...` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 348 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `hasDebt -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | 357 | alpha-derived/decorative-or-token | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `.border(PartyDimensions.dp1, if (hasDebt) ErrorColor.copy(0.2f) else BorderColor, RoundedCornerShape(PartyDimensions.dp14))` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/competitor/CompetitorScreen.kt` | 167 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `balance >  0.01 -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/competitor/CompetitorScreen.kt` | 270 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color    = ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/competitor/CompetitorScreen.kt` | 280 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `val profitColor = if (stats.profits >= 0) SuccessColor else ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyDashboardComponents.kt` | 37 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyDashboardComponents.kt` | 95 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `isOverdue -> ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyStatementComponents.kt` | 18 | import | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyStatementComponents.kt` | 38 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text("مدين", color = ErrorColor, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlig...` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyStatementComponents.kt` | 65 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (debit > 0) ErrorColor else TextMuted,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyStatementComponents.kt` | 79 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (balance > 0) ErrorColor else SuccessColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardScreen.kt` | 125 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `val balanceColor = if (netBalance > 0.01) ErrorColor` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardScreen.kt` | 219 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color    = ErrorColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 325 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (previewDebtAfter > 0.01) ErrorColor else SuccessColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 328 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (previewCashAfter >= 0) TextSecondary else ErrorColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 370 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_348ae40b33e4), color = ErrorColor, fontWeight = FontWeight.Bold)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 414 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (result.debtAfter > 0.01) ErrorColor else SuccessColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 416 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (result.cashBoxAfter >= 0) TextSecondary else ErrorColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 444 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `title = { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_78698483b2d9), color = ErrorColor, fontWeight = FontW...` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 480 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `WhatsAppUtils.formatAmount(totalDebt), ErrorColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | 488 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (cashBoxBalance >= 0) TextSecondary else ErrorColor)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementScreen.kt` | 132 | alpha-derived/decorative-or-token | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (openingBalance > 0) ErrorColor.copy(0.08f)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementScreen.kt` | 137 | alpha-derived/decorative-or-token | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `if (openingBalance > 0) ErrorColor.copy(0.3f)` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementScreen.kt` | 153 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (openingBalance > 0) ErrorColor else SuccessColor,` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementScreen.kt` | 196 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (closingBalance > 0) ErrorColor else SuccessColor,` |
| `feature/profile/src/main/kotlin/com/verto/app/feature/profile/presentation/ProfileEditDialog.kt` | 126 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `{ Text(androidx.compose.ui.res.stringResource(R.string.ds_b9509a00a55a), color = ErrorColor, fontSize = ProfileTextScale.sp11) }` |
| `feature/profile/src/main/kotlin/com/verto/app/feature/profile/presentation/ProfileEditDialog.kt` | 149 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `{ Text(androidx.compose.ui.res.stringResource(R.string.ds_f5768a2540ab), color = ErrorColor, fontSize = ProfileTextScale.sp11) }` |
| `feature/profile/src/main/kotlin/com/verto/app/feature/profile/presentation/ProfileEditDialog.kt` | 166 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(passwordError, color = ErrorColor, fontSize = ProfileTextScale.sp12)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsDesignTokens.kt` | 10 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsDesignTokens.kt` | 90 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/bottomsheets/CashCountSheet.kt` | 189 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `else                  -> ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/bottomsheets/CashCountSheet.kt` | 204 | container-semantic | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `else         -> ErrorContainer` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/AdvancedFiltersSheet.kt` | 63 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_04e7c7e15802, filters.activeCount), color = ErrorColor, fontSiz...` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt` | 50 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (netProfit >= 0) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt` | 59 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `val color = if (isUp) SuccessColor else ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt` | 95 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (positive) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/SmartInsightCarousel.kt` | 67 | container-semantic | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `InsightType.WARNING     -> ErrorContainer` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/SmartInsightCarousel.kt` | 73 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `InsightType.WARNING     -> ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedPayablesCard.kt` | 18 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedPayablesCard.kt` | 49 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(formatMinor(amount, data.currencyCode), color = if (amount > 0L) ErrorColor else TextMuted, fontSize = ReportsTextScale.sp11)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedPayablesCard.kt` | 59 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(formatMinor(supplier.totalMinor, supplier.currencyCode), color = ErrorColor, fontSize = ReportsTextScale.sp12)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedReceivablesCard.kt` | 40 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `InfoChip("إجمالي المتأخر", CurrencyFormatter.formatNoSymbol(data.grandTotal), ErrorColor)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedReceivablesCard.kt` | 65 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_520e177fb20a, client.oldestDueDays), color = ErrorColor, fontSi...` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedReceivablesCard.kt` | 69 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedReceivablesCard.kt` | 112 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (amount > 0) ErrorColor else TextMuted,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/CashFlowStatement.kt` | 54 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `Text("التدفقات الخارجة ▼", color = ErrorColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/CashFlowStatement.kt` | 93 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (neutral) TextPrimary else if (positive) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/FinancialIntegrityCard.kt` | 23 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/FinancialIntegrityCard.kt` | 128 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(value, color = if (healthy) SuccessColor else ErrorColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/ProfitLossStatement.kt` | 62 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (pnl.grossMargin >= 0) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/ProfitLossStatement.kt` | 89 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (pnl.netMargin >= 0) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/ProfitLossStatement.kt` | 124 | foreground/accent | Compose UI usage/audited | YES | READ ONLY (v291 ledger) | `color = if (positive) SuccessColor else ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/AuditLogTodayCard.kt` | 37 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (todayCount > 10) ErrorColor else WarningColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/CashReconciliationCard.kt` | 123 | filled-control | Compose UI usage/audited | NO | reviewed/repaired in 296 | `colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger),` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/InventoryHealthCard.kt` | 57 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `valueColor = if (data.stockoutRisk.isNotEmpty()) ErrorColor else SuccessColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/InventoryHealthCard.kt` | 170 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/InvoiceAnalyticsF255Card.kt` | 22 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/InvoiceAnalyticsF255Card.kt` | 102 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `"CRITICAL" -> ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ReturnsAnalysisCard.kt` | 41 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (data.returnRate > 5f) ErrorColor else SuccessColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShipmentsSummaryCard.kt` | 67 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShrinkageReportCard.kt` | 50 | background | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `.background(ErrorContainer)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShrinkageReportCard.kt` | 58 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShrinkageReportCard.kt` | 90 | background | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `.background(ErrorContainer)` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShrinkageReportCard.kt` | 96 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = ErrorColor,` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/RealMarginCard.kt` | 106 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `else       -> ErrorColor` |
| `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/TopItemsCard.kt` | 93 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (item.margin > 20) SuccessColor else if (item.margin > 0) WarningColor else ErrorColor,` |
| `feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsBackupSection.kt` | 51 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color     = if (backupState is SectionState.Error) ErrorColor else SuccessColor,` |
| `feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsBackupSection.kt` | 58 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color    = ErrorColor,` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailContent.kt` | 161 | container-semantic | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `contentColor = MaterialTheme.colorScheme.onErrorContainer,` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2Components.kt` | 55 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2Components.kt` | 164 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `LogisticsStatusChip(shipment.stateLabel, if (shipment.delayed) ErrorColor else AccentPrimary)` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2Components.kt` | 202 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `if (shipment.delayed) LogisticsStatusChip("متأخرة", ErrorColor)` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt` | 42 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt` | 71 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `border = BorderStroke(VertoStroke.thin, if (showValidation && !validation.isValid) ErrorColor else BorderColor),` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt` | 203 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(errors.joinToString(" • "), color = ErrorColor, style = MaterialTheme.typography.bodySmall)` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt` | 230 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(validation.readyDateError, color = ErrorColor, style = MaterialTheme.typography.bodySmall)` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt` | 47 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt` | 207 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (option.selectable) SuccessColor else ErrorColor,` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt` | 282 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `confirmButton = { TextButton(onClick = onConfirm) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_b257b4e1692...` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt` | 41 | import | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `import com.verto.app.ui.theme.ErrorColor` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt` | 293 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_5014ba36d0c1), color = ErrorColor, style = MaterialTheme.typogr...` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt` | 423 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.ds_0e94e9e0a5ad), color = ErrorColor)` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt` | 454 | foreground/accent | Compose UI usage/audited | NO | theme accessor adoption; no file edit required | `color = if (showValidation) ErrorColor else TextMuted,` |

## Official contrast verifier output

```text
CONTRAST light material onPrimary/primary fg=#FFFFFF bg=#6D5CFF ratio=4.540 threshold=4.5 PASS
CONTRAST light material onPrimaryContainer/primaryContainer fg=#1A1F36 bg=#E9E7FF ratio=13.408 threshold=4.5 PASS
CONTRAST light material onSecondary/secondary fg=#10111A bg=#3B82F6 ratio=5.111 threshold=4.5 PASS
CONTRAST light material onSecondaryContainer/secondaryContainer fg=#1E3A8A bg=#EEF5FF ratio=9.441 threshold=4.5 PASS
CONTRAST light material onBackground/background fg=#1A1F36 bg=#F7F8FC ratio=15.301 threshold=4.5 PASS
CONTRAST light material onSurface/surface fg=#1A1F36 bg=#FFFFFF ratio=16.239 threshold=4.5 PASS
CONTRAST light material onSurfaceVariant/surfaceVariant fg=#4B556B bg=#F2F3F8 ratio=6.742 threshold=4.5 PASS
CONTRAST light material onError/error fg=#FFFFFF bg=#B91C1C ratio=6.470 threshold=4.5 PASS
CONTRAST light material onErrorContainer/errorContainer fg=#7F1D1D bg=#FFEEEE ratio=8.930 threshold=4.5 PASS
CONTRAST light status success/successContainer fg=#15803D bg=#EAF8EF ratio=4.579 threshold=4.5 PASS
CONTRAST light status warning/warningContainer fg=#B45309 bg=#FFF7E6 ratio=4.711 threshold=4.5 PASS
CONTRAST light status info/infoContainer fg=#2563EB bg=#EEF5FF ratio=4.711 threshold=4.5 PASS
CONTRAST light status waiting/waitingContainer fg=#4F46E5 bg=#F0F0FF ratio=5.573 threshold=4.5 PASS
CONTRAST light status offline/offlineContainer fg=#475569 bg=#F1F5F9 ratio=6.917 threshold=4.5 PASS
CONTRAST light status permission/permissionContainer fg=#0F766E bg=#ECFDF9 ratio=5.209 threshold=4.5 PASS
CONTRAST light status danger/dangerContainer fg=#B91C1C bg=#FFEEEE ratio=5.766 threshold=4.5 PASS
CONTRAST light surface success/surface fg=#15803D bg=#FFFFFF ratio=5.016 threshold=4.5 PASS
CONTRAST light surface warning/surface fg=#B45309 bg=#FFFFFF ratio=5.022 threshold=4.5 PASS
CONTRAST light surface info/surface fg=#2563EB bg=#FFFFFF ratio=5.169 threshold=4.5 PASS
CONTRAST light surface waiting/surface fg=#4F46E5 bg=#FFFFFF ratio=6.288 threshold=4.5 PASS
CONTRAST light surface offline/surface fg=#475569 bg=#FFFFFF ratio=7.578 threshold=4.5 PASS
CONTRAST light surface permission/surface fg=#0F766E bg=#FFFFFF ratio=5.473 threshold=4.5 PASS
CONTRAST light surface danger/surface fg=#B91C1C bg=#FFFFFF ratio=6.470 threshold=4.5 PASS
CONTRAST light container onSuccessContainer/successContainer fg=#14532D bg=#EAF8EF ratio=8.318 threshold=4.5 PASS
CONTRAST light container onWarningContainer/warningContainer fg=#78350F bg=#FFF7E6 ratio=8.510 threshold=4.5 PASS
CONTRAST light container onDangerContainer/dangerContainer fg=#7F1D1D bg=#FFEEEE ratio=8.930 threshold=4.5 PASS
CONTRAST light container onInfoContainer/infoContainer fg=#1E3A8A bg=#EEF5FF ratio=9.441 threshold=4.5 PASS
CONTRAST light container textPrimary/waitingContainer fg=#1A1F36 bg=#F0F0FF ratio=14.393 threshold=4.5 PASS
CONTRAST light container textPrimary/offlineContainer fg=#1A1F36 bg=#F1F5F9 ratio=14.823 threshold=4.5 PASS
CONTRAST light container textPrimary/permissionContainer fg=#1A1F36 bg=#ECFDF9 ratio=15.454 threshold=4.5 PASS
CONTRAST light disabled DISABLED_CONTRAST_EXEMPT_BY_POLICY
CONTRAST dark material onPrimary/primary fg=#FFFFFF bg=#6D5CFF ratio=4.540 threshold=4.5 PASS
CONTRAST dark material onPrimaryContainer/primaryContainer fg=#FFFFFF bg=#3D348F ratio=10.066 threshold=4.5 PASS
CONTRAST dark material onSecondary/secondary fg=#10111A bg=#3B82F6 ratio=5.111 threshold=4.5 PASS
CONTRAST dark material onSecondaryContainer/secondaryContainer fg=#F7F8FC bg=#172D52 ratio=12.902 threshold=4.5 PASS
CONTRAST dark material onBackground/background fg=#F7F8FC bg=#10111A ratio=17.713 threshold=4.5 PASS
CONTRAST dark material onSurface/surface fg=#F7F8FC bg=#171925 ratio=16.459 threshold=4.5 PASS
CONTRAST dark material onSurfaceVariant/surfaceVariant fg=#C1C6D5 bg=#262A3B ratio=8.333 threshold=4.5 PASS
CONTRAST dark material onError/error fg=#10111A bg=#F87171 ratio=6.796 threshold=4.5 PASS
CONTRAST dark material onErrorContainer/errorContainer fg=#F7F8FC bg=#4A171B ratio=13.842 threshold=4.5 PASS
CONTRAST dark status success/successContainer fg=#4ADE80 bg=#12351F ratio=7.751 threshold=4.5 PASS
CONTRAST dark status warning/warningContainer fg=#FBBF24 bg=#43290B ratio=8.060 threshold=4.5 PASS
CONTRAST dark status info/infoContainer fg=#60A5FA bg=#172D52 ratio=5.386 threshold=4.5 PASS
CONTRAST dark status waiting/waitingContainer fg=#818CF8 bg=#252750 ratio=4.742 threshold=4.5 PASS
CONTRAST dark status offline/offlineContainer fg=#94A3B8 bg=#28313E ratio=5.122 threshold=4.5 PASS
CONTRAST dark status permission/permissionContainer fg=#2DD4BF bg=#123B38 ratio=6.603 threshold=4.5 PASS
CONTRAST dark status danger/dangerContainer fg=#F87171 bg=#4A171B ratio=5.311 threshold=4.5 PASS
CONTRAST dark surface success/surface fg=#4ADE80 bg=#171925 ratio=10.024 threshold=4.5 PASS
CONTRAST dark surface warning/surface fg=#FBBF24 bg=#171925 ratio=10.464 threshold=4.5 PASS
CONTRAST dark surface info/surface fg=#60A5FA bg=#171925 ratio=6.871 threshold=4.5 PASS
CONTRAST dark surface waiting/surface fg=#818CF8 bg=#171925 ratio=5.856 threshold=4.5 PASS
CONTRAST dark surface offline/surface fg=#94A3B8 bg=#171925 ratio=6.813 threshold=4.5 PASS
CONTRAST dark surface permission/surface fg=#2DD4BF bg=#171925 ratio=9.384 threshold=4.5 PASS
CONTRAST dark surface danger/surface fg=#F87171 bg=#171925 ratio=6.315 threshold=4.5 PASS
CONTRAST dark container onSuccessContainer/successContainer fg=#F7F8FC bg=#12351F ratio=12.727 threshold=4.5 PASS
CONTRAST dark container onWarningContainer/warningContainer fg=#F7F8FC bg=#43290B ratio=12.679 threshold=4.5 PASS
CONTRAST dark container onDangerContainer/dangerContainer fg=#F7F8FC bg=#4A171B ratio=13.842 threshold=4.5 PASS
CONTRAST dark container onInfoContainer/infoContainer fg=#F7F8FC bg=#172D52 ratio=12.902 threshold=4.5 PASS
CONTRAST dark container textPrimary/waitingContainer fg=#F7F8FC bg=#252750 ratio=13.328 threshold=4.5 PASS
CONTRAST dark container textPrimary/offlineContainer fg=#F7F8FC bg=#28313E ratio=12.374 threshold=4.5 PASS
CONTRAST dark container textPrimary/permissionContainer fg=#F7F8FC bg=#123B38 ratio=11.582 threshold=4.5 PASS
CONTRAST dark disabled DISABLED_CONTRAST_EXEMPT_BY_POLICY
DESIGN_SYSTEM_CONTRACT PASS 0
```

## Static gates

- Migration Gate: **PASS**.
- Diff compatibility gate: **PASS**.
- Contract verifier: **PASS** (`DESIGN_SYSTEM_CONTRACT PASS 0`).
- All required Light/Dark semantic contrast pairs: **PASS**, threshold `4.5:1`.
- Disabled: `DISABLED_CONTRAST_EXEMPT_BY_POLICY`.

### Post metrics

- `legacyFocusedHardcodedCount` = `74`
- `hardcodedUserFacingStringsFull` = `375`
- `coreVisibleLiteralDebt` = `0`
- `rawMaterialMustWrap` = `214`
- `forbiddenMaterial` = `0`
- `rawDpOutsideApprovedTokenFiles` = `22`
- `rawSpOutsideApprovedTokenFiles` = `0`
- `rawColorOutsideApprovedTokenFiles` = `0`
- `rawMotionDurations` = `5`
- `coreBoundaryViolations` = `0`
- `designSystemDomainStringCount` = `820`
- `externalDesignSystemDomainStringReferences` = `1214`
- `expiredExceptions` = `0`
- `permanentExceptions` = `0`
- `duplicateGuardedPrimitives` = `0`
- `activeExceptionEntryCount` = `29`

No debt metric grew. Snapshot ID remained unchanged because the scanner debt snapshot is unchanged.

## Tests and compilation

- Core unit tests: **NOT RUN — explicitly not required by user override for this v296 output**.
- Core compile: **NOT RUN — explicitly not required by user override**.
- Feature/app compile: **NOT RUN — explicitly not required by user override**.
- No Gradle/build result is claimed.

## Documentation

- `TOKEN-CATALOG.md` updated with Light/Dark Danger and Secondary content contracts.
- `DESIGN_SYSTEM_CONTRACT.md` updated with semantic runtime/contrast rules.
- `SEMANTIC-COLOR-CONTRACT-v296.md` created with Light/Dark contrast tables.
- `MIGRATION-LEDGER.md` records `IMPLEMENTED (static acceptance)` for 296 under the explicit override.

## Out-of-scope observations

- Hardcoded/domain strings remain owned by sessions 297–298.
- Raw Material wrapper debt remains for 299.
- Raw dp remains for 300.
- Raw motion duration debt, including `tween(800)`, remains for 301.
- API cleanup remains for 302.
- Visual/accessibility final campaign remains for 303.
- No chart palette redesign was performed.

## Output

- Output archive: `Verto-v296-source-of-truth.zip`.
- Output SHA-256: authoritative value is stored in `Verto-v296-source-of-truth.zip.sha256` after packaging. Embedding the archive's own final SHA inside itself would change that SHA.
