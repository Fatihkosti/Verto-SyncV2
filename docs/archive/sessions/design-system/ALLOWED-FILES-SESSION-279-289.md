# Frozen Allowed-File Sets

The implementation used these frozen ownership boundaries. Feature business logic, domain, data, navigation, and synchronization files were not intentionally modified.

## v279

- `scripts/design-system-scan.py`
- `scripts/ci/run-quality-gate.sh`
- `tools/design_system_diff_gate.py`
- `tools/verify_design_system_contract.py`
- `docs/design-system/BASELINE.*`
- `docs/design-system/BASELINE.json`

## v280

- `core/export/build.gradle.kts`
- `core/export/src/main/kotlin/com/verto/app/pdf/PdfUtils.kt`
- removal of `core/designsystem/src/main/kotlin/com/verto/app/pdf/PdfUtils.kt`
- ownership and Material policy documents

## v281

- `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/**`
- `core/designsystem/src/test/kotlin/com/verto/app/ui/theme/**`
- token and contract documents

## v282

- `tools/design_system_inventory.py`
- `docs/design-system/COMPONENT-INVENTORY.*`
- `docs/design-system/SCREENSHOT-TEST-ADR.md`

## v283

- `core/designsystem/src/main/kotlin/com/verto/app/ui/components/CanonicalComponents.kt`
- `core/designsystem/build.gradle.kts`
- component catalog and ownership documents

## v284–v288

- Presentation-only Kotlin files under `app/src/main/kotlin/**/ui/**`.
- Presentation-only Kotlin files under `feature/**/src/main/kotlin/**/presentation/**` and feature UI packages.
- `core/designsystem/src/main/res/values/strings.xml`.
- `tools/migrate_ui_strings.py`.

The migration changed component ownership and visual resources only. ViewModels, UseCases, repositories, domain models, database, sync, permissions, and navigation contracts were not intentionally changed.

## v289

- `tools/design_system_diff_gate.py`
- `scripts/ci/run-quality-gate.sh`
- `docs/design-system/**`
- `Verto-v289-*.md`
