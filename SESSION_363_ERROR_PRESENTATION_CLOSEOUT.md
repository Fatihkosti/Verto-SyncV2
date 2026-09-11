# SESSION 363 — Error Presentation Closeout

**Source of truth:** `Verto-v362-error-migration.zip`

## Implemented

- Added a shared presentation-agnostic error contract in `core:common`:
  - `UserErrorPresentation`
  - `UserErrorMessageKey`
  - `ErrorSurface` = FIELD / INLINE / BANNER / FULL_SCREEN
  - `ErrorRecoveryAction` = NONE / RETRY / LOGIN / EDIT
  - `ErrorPresentationContext`
  - `ErrorPresentationPolicy`
- Recovery actions are derived from the classified `RetryAdvice`; no message-text guessing.
- Screen-load failures render as full-screen states; transient failures render as banners; form validation can resolve to field/inline errors.
- Added shared localized Arabic error copy to `core:common` resources.
- Added `VertoUserError` and `resolveVertoError()` in `core:designsystem`.
- Migrated Home Search from a generic hardcoded catch message to the structured presentation contract.
- Added a real retry generation for Home Search so the Retry action reruns the same query without editing it.
- Tightened generic `IOException`: it now becomes `Unknown` instead of being mislabeled as network or local-storage failure.
- Added final anti-regression gate `tools/quality/user_error_boundary_gate.py`:
  - rejects raw throwable/exception `.message` in UI/presentation code;
  - rejects text heuristics over exception messages;
  - prevents expansion of legacy `ErrorHumanizer.humanize` call sites beyond the v363 baseline.

## Verification

- `session361_verify.py` → **13/13 PASS**
- `session362_verify.py` → **25/25 PASS**
- `session363_verify.py` → **19/19 PASS**
- User error boundary gate → **PASS**, 386 UI/presentation files scanned.
- Core error contract `kotlinc` compile → **PASS**.
- Runtime presentation-policy harness → **8/8 PASS**.
- Shared error strings XML parse → **PASS**.
- Parser-level Kotlin scan of modified Android/Compose files → **0 syntax diagnostics**.
- Gradle attempted:
  `./gradlew :core:common:testDebugUnitTest :core:designsystem:compileDebugKotlin :app:compileDebugKotlin --offline --build-cache`
- Gradle result: **BLOCKED_ENVIRONMENT** because Gradle 8.9 is not locally cached and `services.gradle.org` is unreachable.

## Scope guard

- No SQL changes.
- No Supabase changes.
- No Room schema changes.
- No navigation changes.
- No invoice/inventory/payment/logistics business-rule changes.

**SESSION_363_STATIC = PASS**
**SESSION_363_CORE_COMPILE = PASS_KOTLINC**
**SESSION_363_BUILD = BLOCKED_ENVIRONMENT**
