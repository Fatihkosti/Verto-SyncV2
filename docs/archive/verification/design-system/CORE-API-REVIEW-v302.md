# Core API Review — Session 302

## Source

- Input: `Verto-v301-implemented-static.zip`
- SHA-256: `f95d18e4687af72fe84e988959fe1c8bebee385d0d0171f8aa93dc2ba93cc097`
- Archive entries: `2495`

## Public-surface definition

Public/default-public top-level functions, supporting types, and top-level properties under `core/designsystem/.../ui/components`; private/internal/local/preview-only declarations are excluded. Production consumers are counted from `*/src/main/kotlin`, support both `Name(...)` and trailing-lambda `Name { ... }`, and require Core import/package attribution to avoid same-name local collisions.

## Pre-state

- Public declarations: **54**
- Unique public symbols: **53**
- Public functions: **51**
- Supporting types: **2**
- Public properties: **1**

## v295 counting correction

- `SettingsCard`: **19** calls / 8 files / 4 modules (not 0).
- `VertoFormCard`: **15** calls / 9 files / 2 modules.
- `VertoAuthScaffold`: **5** calls / 5 files / 1 module.
- Core `InfoChip`: **3** calls / 3 files / 1 module; local same-name Inventory/Reports functions are excluded.
- Surviving `VertoIconButton`: **109** calls / 61 files / 17 modules.

## Exact 54-record decision table

| ID | Symbol | Kind | Source | Calls | Files | Modules | Decision |
|---|---|---|---|---:|---:|---:|---|
| `v302-001` | `WhatsAppIcon` | `prop` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/AppIcons.kt:8` | 4 | 3 | 1 | `KEEP_PUBLIC` |
| `v302-002` | `VertoButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:71` | 78 | 45 | 12 | `KEEP_PUBLIC` |
| `v302-003` | `VertoOutlinedButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:97` | 63 | 35 | 12 | `KEEP_PUBLIC` |
| `v302-004` | `VertoOutlinedTextField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:122` | 81 | 39 | 15 | `KEEP_PUBLIC` |
| `v302-005` | `VertoTextButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:173` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-006` | `VertoIconButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:187` | 0 | 0 | 0 | `MERGE_REMOVE_UNUSED_OVERLOAD` |
| `v302-007` | `VertoSearchField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:205` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-008` | `VertoMoneyField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:226` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-009` | `VertoSelectionField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:248` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-010` | `VertoDateField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:271` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-011` | `VertoStatusBadge` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:281` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-012` | `VertoChip` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:301` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-013` | `VertoMetric` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:315` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-014` | `VertoListRow` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:325` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-015` | `VertoSectionHeader` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:350` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-016` | `VertoInfoBanner` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:358` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-017` | `VertoErrorState` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:365` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-018` | `VertoOfflineState` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:370` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-019` | `VertoBottomActionBar` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt:375` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-020` | `DateRangePickerDialog` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/DateRangePickerDialog.kt:25` | 2 | 2 | 2 | `KEEP_PUBLIC` |
| `v302-021` | `VertoIconButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/MaterialWrappers.kt:25` | 109 | 61 | 17 | `KEEP_PUBLIC` |
| `v302-022` | `VertoTopAppBar` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/MaterialWrappers.kt:41` | 32 | 31 | 11 | `KEEP_PUBLIC` |
| `v302-023` | `VertoBottomSheet` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/MaterialWrappers.kt:65` | 10 | 10 | 5 | `KEEP_PUBLIC` |
| `v302-024` | `VertoTabRow` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/MaterialWrappers.kt:86` | 6 | 6 | 4 | `KEEP_PUBLIC` |
| `v302-025` | `VertoScrollableTabRow` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/MaterialWrappers.kt:115` | 2 | 2 | 2 | `KEEP_PUBLIC` |
| `v302-026` | `SettingsCard` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:47` | 19 | 8 | 4 | `KEEP_PUBLIC` |
| `v302-027` | `SettingsSectionHeader` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:60` | 31 | 6 | 4 | `KEEP_PUBLIC` |
| `v302-028` | `SettingsDivider` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:75` | 16 | 4 | 3 | `KEEP_PUBLIC` |
| `v302-029` | `SettingsNavRow` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:83` | 10 | 1 | 1 | `KEEP_PUBLIC` |
| `v302-030` | `DialogTextField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:135` | 8 | 3 | 3 | `KEEP_PUBLIC` |
| `v302-031` | `dialogFieldColors` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SettingsPrimitives.kt:158` | 3 | 1 | 1 | `KEEP_PUBLIC` |
| `v302-032` | `VertoLinearValueProgress` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:68` | 2 | 2 | 2 | `KEEP_PUBLIC` |
| `v302-033` | `StatusDot` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:92` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-034` | `VertoCard` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:102` | 59 | 31 | 10 | `KEEP_PUBLIC` |
| `v302-035` | `VertoTopBar` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:163` | 18 | 18 | 6 | `KEEP_PUBLIC` |
| `v302-036` | `AmountText` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:204` | 3 | 2 | 2 | `KEEP_PUBLIC` |
| `v302-037` | `KpiMini` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:233` | 10 | 2 | 1 | `KEEP_PUBLIC` |
| `v302-038` | `InfoChip` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/SharedComponents.kt:255` | 3 | 3 | 1 | `KEEP_PUBLIC` |
| `v302-039` | `VertoStatusTone` | `type` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:78` | 24 | 10 | 3 | `KEEP_PUBLIC` |
| `v302-040` | `VertoStatusBanner` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:125` | 5 | 3 | 2 | `KEEP_PUBLIC` |
| `v302-041` | `VertoEmptyStateVariant` | `type` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:190` | 7 | 5 | 4 | `KEEP_PUBLIC` |
| `v302-042` | `VertoEmptyState` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:193` | 11 | 9 | 6 | `KEEP_PUBLIC` |
| `v302-043` | `VertoLoadingState` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:269` | 5 | 4 | 4 | `KEEP_PUBLIC` |
| `v302-044` | `VertoPermissionPrompt` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:300` | 0 | 0 | 0 | `REMOVE_UNUSED` |
| `v302-045` | `VertoConfirmationDialog` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/UiStateComponents.kt:350` | 3 | 1 | 1 | `KEEP_PUBLIC` |
| `v302-046` | `VertoTextField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:103` | 84 | 17 | 6 | `KEEP_PUBLIC` |
| `v302-047` | `VertoPasswordField` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:225` | 7 | 4 | 1 | `KEEP_PUBLIC` |
| `v302-048` | `VertoPrimaryButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:287` | 23 | 19 | 5 | `KEEP_PUBLIC` |
| `v302-049` | `VertoSecondaryButton` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:331` | 23 | 14 | 3 | `KEEP_PUBLIC` |
| `v302-050` | `VertoInlineStatus` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:353` | 16 | 7 | 2 | `KEEP_PUBLIC` |
| `v302-051` | `VertoFormSectionHeader` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:392` | 4 | 2 | 1 | `KEEP_PUBLIC` |
| `v302-052` | `VertoStepIndicator` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:418` | 2 | 2 | 1 | `KEEP_PUBLIC` |
| `v302-053` | `VertoFormCard` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoFormComponents.kt:458` | 15 | 9 | 2 | `KEEP_PUBLIC` |
| `v302-054` | `VertoAuthScaffold` | `fun` | `core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoScreenScaffolds.kt:38` | 5 | 5 | 1 | `KEEP_PUBLIC` |

## Retired declarations

- `v302-005` — `VertoTextButton` (REMOVE_UNUSED)
- `v302-006` — `VertoIconButton` (MERGE_REMOVE_UNUSED_OVERLOAD)
- `v302-007` — `VertoSearchField` (REMOVE_UNUSED)
- `v302-008` — `VertoMoneyField` (REMOVE_UNUSED)
- `v302-009` — `VertoSelectionField` (REMOVE_UNUSED)
- `v302-010` — `VertoDateField` (REMOVE_UNUSED)
- `v302-011` — `VertoStatusBadge` (REMOVE_UNUSED)
- `v302-012` — `VertoChip` (REMOVE_UNUSED)
- `v302-013` — `VertoMetric` (REMOVE_UNUSED)
- `v302-014` — `VertoListRow` (REMOVE_UNUSED)
- `v302-015` — `VertoSectionHeader` (REMOVE_UNUSED)
- `v302-016` — `VertoInfoBanner` (REMOVE_UNUSED)
- `v302-017` — `VertoErrorState` (REMOVE_UNUSED)
- `v302-018` — `VertoOfflineState` (REMOVE_UNUSED)
- `v302-019` — `VertoBottomActionBar` (REMOVE_UNUSED)
- `v302-033` — `StatusDot` (REMOVE_UNUSED)
- `v302-044` — `VertoPermissionPrompt` (REMOVE_UNUSED)

## 37 surviving declarations and post consumers

| Symbol | Kind | Calls | Files | Modules |
|---|---|---:|---:|---:|
| `WhatsAppIcon` | `prop` | 4 | 3 | 1 |
| `VertoButton` | `fun` | 78 | 45 | 12 |
| `VertoOutlinedButton` | `fun` | 63 | 35 | 12 |
| `VertoOutlinedTextField` | `fun` | 81 | 39 | 15 |
| `DateRangePickerDialog` | `fun` | 2 | 2 | 2 |
| `VertoIconButton` | `fun` | 109 | 61 | 17 |
| `VertoTopAppBar` | `fun` | 32 | 31 | 11 |
| `VertoBottomSheet` | `fun` | 10 | 10 | 5 |
| `VertoTabRow` | `fun` | 6 | 6 | 4 |
| `VertoScrollableTabRow` | `fun` | 2 | 2 | 2 |
| `SettingsCard` | `fun` | 19 | 8 | 4 |
| `SettingsSectionHeader` | `fun` | 31 | 6 | 4 |
| `SettingsDivider` | `fun` | 16 | 4 | 3 |
| `SettingsNavRow` | `fun` | 10 | 1 | 1 |
| `DialogTextField` | `fun` | 8 | 3 | 3 |
| `dialogFieldColors` | `fun` | 4 | 2 | 2 |
| `VertoLinearValueProgress` | `fun` | 2 | 2 | 2 |
| `VertoCard` | `fun` | 59 | 31 | 10 |
| `VertoTopBar` | `fun` | 18 | 18 | 6 |
| `AmountText` | `fun` | 3 | 2 | 2 |
| `KpiMini` | `fun` | 10 | 2 | 1 |
| `InfoChip` | `fun` | 3 | 3 | 1 |
| `VertoStatusTone` | `type` | 80 | 12 | 4 |
| `VertoStatusBanner` | `fun` | 5 | 3 | 2 |
| `VertoEmptyStateVariant` | `type` | 12 | 6 | 5 |
| `VertoEmptyState` | `fun` | 11 | 9 | 6 |
| `VertoLoadingState` | `fun` | 5 | 4 | 4 |
| `VertoConfirmationDialog` | `fun` | 3 | 1 | 1 |
| `VertoTextField` | `fun` | 85 | 18 | 7 |
| `VertoPasswordField` | `fun` | 7 | 4 | 1 |
| `VertoPrimaryButton` | `fun` | 23 | 19 | 5 |
| `VertoSecondaryButton` | `fun` | 23 | 14 | 3 |
| `VertoInlineStatus` | `fun` | 16 | 7 | 2 |
| `VertoFormSectionHeader` | `fun` | 4 | 2 | 1 |
| `VertoStepIndicator` | `fun` | 2 | 2 | 1 |
| `VertoFormCard` | `fun` | 15 | 9 | 2 |
| `VertoAuthScaffold` | `fun` | 5 | 5 | 1 |

## Duplicate analysis

- `VertoIconButton`: the unused canonical icon/contentDescription overload was retired; one content-lambda overload remains.
- `VertoTopAppBar` and `VertoTopBar`: distinct ownership and composition contracts.
- `VertoStatusBanner` and `VertoInlineStatus`: full state banner vs compact form feedback.
- Core `InfoChip` and feature-local same-name functions: distinct signatures and owners.
- Compatibility buttons and opinionated form buttons: distinct caller contracts.

## Post-state

- Public declarations: **37**
- Unique symbols: **37**
- Public functions: **34**
- Official visual/composable components: **33**
- Public styling helpers: **1**
- Supporting types: **2**
- Public properties/assets: **1**
- Zero-consumer public declarations: **0**
- Duplicate public symbols: **0**

## Scope proof

- Production Kotlin changes: exactly `CanonicalComponents.kt`, `SharedComponents.kt`, `UiStateComponents.kt`.
- Feature/App production Kotlin changes: **0**.
- Resource change: only `core/designsystem/src/main/res/values/strings.xml`.
- Orphan resources removed only: `verto_action_allow`, `verto_action_not_now`, `verto_state_error`, `verto_state_offline`.
- `verto_action_cancel` remains present.

## Inventory tooling proof

- Inventory schema: **302**.
- Trailing-lambda and import-aware attribution enabled.
- Same-name local functions are not attributed to Core without import/package evidence.
- Determinism JSON SHA-256: `11ba2fa342d5f77b9e2ee250c18896fde65e669e51ffd9b57a745c9523c2beed`.
- Determinism MD SHA-256: `491b6a451660d11724a64b9e42f2b16f856c92271a4808ce2b2c4e4f0214bb1e`.

Session 303 visual/accessibility runtime closure remains pending.
