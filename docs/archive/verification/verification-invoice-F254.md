# verification-invoice-F254

**Date:** 2026-08-19  
**Session:** 254 — UX موثوق ومسودات قابلة للاستعادة

## 1. Source of Truth

- Input: `Verto-v253-source-of-truth.zip`
- Output: `Verto-v254-source-of-truth.zip`

## 2. Scope

F254 was limited to invoice-editor draft reliability and UX. No financial domain rule, inventory costing policy, invoice lifecycle rule, sync accounting contract, PO/GRN/three-way-match behavior, or server schema was intentionally changed.

## 3. Target invariants

1. Complex invoice drafts live in Room, not in a large Bundle.
2. `rememberSaveable` is limited to simple UI state.
3. A failed Post never deletes the draft.
4. A successful Post deletes the draft only after the invoice save boundary returns successfully.
5. One logical save keeps one stable `writeId` across recreation/retry.
6. Repeated save taps are blocked in UI and ViewModel; Domain/DB protections remain authoritative.
7. Draft items and maintenance attachment metadata survive database reopen/process recreation.
8. International purchase always exposes the functional-currency equation.
9. Local purchase visually distinguishes required purchase price from optional sale price.

## 4. Root cause before F254

The invoice form held most editor state in Compose memory. There was no durable local draft aggregate for lines/maintenance metadata, no process-death recovery path, and no UI-level save-in-flight contract. This left user input vulnerable to process loss and duplicate save taps even though lower layers already contained stronger financial safeguards.

## 5. Actual changes

### Room/local persistence

- Added `invoice_editor_drafts`.
- Added ordered child table `invoice_editor_draft_lines`.
- Added ordered `invoice_editor_draft_maintenance_images` metadata table.
- Added FK `ON DELETE CASCADE` from children to draft header.
- Added atomic `@Transaction replaceDraft(...)` and draft deletion DAO.
- Bumped Room schema version **70 → 71** and registered `MIGRATION_70_71`.
- Draft entities were grouped with `@Embedded` value objects so F254 does not increase the project's excessive-parameter-list metric.

### Application/bridge

- Added draft application models and `load/save/deleteInvoiceDraft` workflow contract.
- Added Room ↔ payment-model mapping in `PaymentPresentationBridge`.
- Draft persistence stays local-only and outside posted accounting data.

### ViewModel/UDF/save safety

- Added durable draft load/restore flow.
- Added stable save identity using `SavedStateHandle` + persisted draft `writeId`.
- Added debounced Room draft writes.
- Added ViewModel in-flight guard (`invoiceSaveInFlight`).
- Added explicit `IDLE / SAVING / SUCCESS / ERROR / CONFLICT / OFFLINE` UI save states.
- Draft deletion occurs after successful `debtWorkflow.saveInvoice(...)`; exceptions retain the draft.

### Compose UX

- Complex form state remains `remember`, not `rememberSaveable`.
- Simple exit-dialog state uses `rememberSaveable`.
- Added draft restoration/loading state.
- Added unsaved-change exit guard with Save Draft / Stay / Discard actions.
- Save button is disabled and shows progress while saving.
- International purchase shows: `amount currency × exchange rate = Functional`.
- Local purchase explicitly labels purchase price as primary and sale price as optional.
- New F254 UI was split into `AddDebtDraftUi.kt`; `AddDebtScreen.kt` remains below the project's 500-line quality threshold.

### Process-death verification support

- Added `InvoiceEditorDraftProcessDeathTest` with database reopen coverage and two device phases.
- Added `scripts/verify-v254-process-death.sh` using `adb shell am force-stop` between phases; this is not a Rotation-only test.

## 6. Schema/contract changes

- Room schema: **71**.
- New local-only draft tables and DAO.
- `PaymentDebtWorkflowPort` gained draft load/save/delete operations.
- No Supabase/server migration was added.
- No posted invoice/accounting schema contract was changed.

## 7. Tests and actual results

All available static/SQLite regression verifiers passed:

- F244 migration SQL — PASS
- F244 money core — PASS
- F245 atomic invariants — PASS
- F245 migration SQL — PASS
- F246 currency truth — PASS
- F247 inventory costing — PASS
- F248 invoice lifecycle — PASS
- F249 financial sync — PASS
- F250 reports/reconciliation — PASS
- F251 client-credit money — PASS
- F251 local contracts — PASS
- F252 invoice returns — PASS
- F253 purchase cycle — PASS
- F254 invoice draft UX/SQLite verifier — **PASS**

F254 SQLite verification specifically confirms:

- all three draft tables are created;
- required columns exist;
- draft header, two ordered lines, and attachment metadata survive close/reopen;
- deleting the header cascades to lines/images.

### Static quality parity against v253

| Metric | v253 | v254 | Delta |
|---|---:|---:|---:|
| excessive parameter lists | 564 | 564 | 0 |
| files >500 lines | 21 | 21 | 0 |
| long functions | 436 | 436 | 0 |
| architecture violations | 18 | 18 | 0 |
| broad catches | 21 | 21 | 0 |
| dependency cycles | 0 | 0 | 0 |

The repository's historical global Kotlin quality gate remains FAIL because its older ceilings were already exceeded in v253. F254 adds no regression to the compared debt metrics.

## 8. Rollback/atomicity evidence

- Draft replacement is a Room `@Transaction`: header/lines/images change as one local draft operation.
- Child rows use FK cascade, preventing orphan draft lines/images after discard/success cleanup.
- Posted invoice save remains owned by the existing invoice workflow.
- Draft cleanup is sequenced only after the invoice save call returns successfully.

## 9. Build result

**NOT RUN / ENVIRONMENT BLOCKED.**

Attempted:

```bash
./gradlew :data:database:compileDebugKotlin :feature:payment:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon --console=plain
```

The wrapper attempted to obtain `gradle-8.9-bin.zip` and failed with `UnknownHostException: services.gradle.org`. Gradle 8.9 is not present locally and this environment has no network access, so Kotlin/KSP/Room compilation did not start.

Because KSP did not run, the generated Room schema export for version 71 was not produced in this environment; it should be generated/checked on the laptop build.

## 10. Compatibility with earlier invoice work

Regression verifiers F244–F253 remain PASS. The F253 verifier was adjusted only so it accepts Room schema versions `>=70` instead of pinning exactly 70; its F253 invariant still requires `MIGRATION_69_70`.

## 11. Remaining risks / mandatory external gate

1. Run Gradle/KSP compilation with Gradle 8.9 available.
2. Run `scripts/verify-v254-process-death.sh` on a real device/emulator after installing the instrumentation APK.
3. Confirm generated Room schema 71 and run the Room migration test on device/CI.
4. Perform accessibility/RTL interaction smoke test on device.

## 12. Decision

**Implementation: PASS.**  
**Available static/SQLite regression gate: PASS.**  
**Final F254 acceptance: PENDING DEVICE/GRADLE GATE**, because the plan explicitly requires real Process Death and this environment cannot execute device instrumentation or download Gradle 8.9.
