# SESSION 362 — Error Migration to Structured Classification

**Source of truth:** `Verto-v361-error-classification-foundation.zip`  
**Scope:** migrate feature error paths to the Session 361 classifier; UI presentation unification remains Session 363.

## Implemented

- Removed direct user-facing `Throwable.message` propagation from the audited feature paths:
  - organization/team
  - dashboard education
  - invoice details
  - expenses
  - party/client
  - commission join-code flow
  - Optimal codes/company invoices
  - payment record/reverse/bulk coordinators
  - shipment/logistics v2
  - sync presentation sentinel handling
- Technical exceptions now cross the UI boundary through the Session 361 classified error path (`ErrorClassifier` / compatibility `ErrorHumanizer`).
- Preserved explicit, intentional business-validation messages where the application already knows the real cause.

## Misleading heuristics removed

### Invoice save
Removed keyword matching over exception text (`conflict`, `409`, `offline`, `timeout`, etc.).
The save state now uses `ErrorClassifier.classify(error)` and exact `AppFailure` types.

### Shipment purchase planning
Removed matching against English exception sentences such as:
- `line.inventoryItemId is required`
- `linked to another active shipment`
- `Eligible international purchase invoice not found`

Producer boundaries now emit stable codes:
- `SHIPMENT_INVENTORY_LINK_REQUIRED`
- `SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE`
- `SHIPMENT_PURCHASE_INVOICE_CONFLICT`

The presentation layer maps those codes to the existing Arabic recovery copy.

### Shipment documents
Removed `error.message.contains(...)` classification for size/type/permission.
Stable business codes are emitted for:
- `LOGISTICS_DOCUMENT_TOO_LARGE`
- `LOGISTICS_DOCUMENT_UNSUPPORTED`

Device permission failures are resolved structurally through `AppFailure.PermissionDenied`.

### Sync orchestration
Replaced the string sentinel `sync_skipped: ...` with `BusinessRuleFailureException("SYNC_SKIPPED")`.
The UI checks the structured business code, not exception text.

### Optimal integration
- `DefaultOptimalSyncRecoveryPolicy` now persists `ErrorClassifier` diagnostic codes instead of exception class + raw message.
- Remote outbox failure storage no longer persists arbitrary remote reason text for retry/conflict/rejection classification.
- `OptimalSyncErrorHumanizer` uses exact stable codes; token/substring guessing was removed.

## Deliberately unchanged

- No Room schema changes.
- No Supabase schema/data changes.
- No navigation changes.
- No invoice, inventory, payment, shipment, or synchronization business semantics changed.
- Existing UI state shapes (`String?`, banners/snackbars/dialogs) were not globally redesigned; that is Session 363.
- Internal diagnostic/message inspection that is not user-facing remains where it belongs to legacy sync/diagnostic logic; it is not treated as trusted UI copy.

## Verification

- `python3 tools/quality/session361_verify.py` → **13/13 PASS**.
- `python3 tools/quality/session362_verify.py` → **25/25 PASS**.
- Pure Kotlin compile of the core error contract plus the migrated Optimal recovery/humanizer → **PASS**.
- Parser-level `kotlinc` scan across all changed Kotlin sources → **no syntax diagnostics**. Full semantic compilation is not claimed because Android/module dependencies are unavailable to standalone `kotlinc`.
- Gradle attempted:
  `./gradlew :core:common:testDebugUnitTest :core:crash:compileDebugKotlin :feature:shipment:compileDebugKotlin :feature:payment:compileDebugKotlin --offline --build-cache`
- Gradle result: **BLOCKED_ENVIRONMENT** — Gradle 8.9 is not cached and the wrapper attempts to reach `services.gradle.org`, which is unavailable in this environment.

## Remaining for Session 363

- Move final UI error presentation to one shared presentation contract.
- Standardize field error vs banner/snackbar vs full-screen state vs permission/session action.
- Attach retry/login/edit actions from `RetryAdvice`.
- Consolidate localized string resources.
- Add final anti-regression gate preventing new raw throwable/server text from becoming user-visible.

**SESSION_362_STATIC = PASS**  
**SESSION_362_PURE_KOTLIN_COMPILE = PASS**  
**SESSION_362_BUILD = BLOCKED_ENVIRONMENT**
