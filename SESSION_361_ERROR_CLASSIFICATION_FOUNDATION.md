# SESSION 361 — Unified Error Classification Foundation

**Source of truth:** `Verto-v360-home-recent-activity.zip`  
**Scope:** Error-classification foundation only. Feature-by-feature migration is reserved for 362.

## Implemented

- Added canonical `AppFailure` taxonomy in `core:common`.
- Classified failures by source: network, remote/server, device/local storage, business, security, and unknown.
- Added explicit retry guidance (`NEVER`, `USER_RETRY`, `AUTOMATIC_BACKOFF`, `AFTER_REAUTH`, `AFTER_CORRECTION`).
- Added structured remote error boundary:
  - `RemoteFailureMetadata(statusCode, code, target, retryAfterMillis)`
  - `RemoteFailureException`
- Added explicit business/classified failure adapters for migration without relying on display strings.
- Added `ErrorClassifier` as the single source of truth.
- Coroutine `CancellationException` is rethrown and never converted into a user-visible failure.
- Remote statuses are classified structurally: 400/422 validation, 401 auth, 403 permission, 404 missing, 408 timeout, 409 conflict, 429 rate limit, 5xx server.
- Generic `IOException` is no longer treated as a network error.
- Local file / SQLite-family failures are classified as device/local-storage failures.
- Reworked legacy `ErrorHumanizer` to delegate to `ErrorClassifier`.
- Removed the unsafe `hasArabic()` trust heuristic completely.
- `ErrorHumanizer` no longer passes arbitrary exception messages to the user.

## Deliberately not changed

- Did not migrate every feature/ViewModel to `AppFailure`; this is Session 362.
- Did not replace all existing `String?` UI error state yet.
- Did not change error presentation components, banners, dialogs, or retry UX; this is Session 363.
- Did not change Supabase schema, Room schema, navigation, invoices, inventory, synchronization semantics, or business workflows.
- Did not modify CrashReporter diagnostics/grouping in this session.

## Verification

- `python3 tools/quality/session361_verify.py` → **13/13 PASS**.
- New `core:common` error sources compiled successfully with local `kotlinc`.
- Direct runtime verification → **10/10 PASS** covering network unavailable, connection failure, timeout, generic I/O, missing file, permission, Arabic technical text, remote 409, remote 503, and coroutine cancellation.
- `ErrorHumanizer.kt` syntax compiled against the new contract using a local CrashReporter stub.
- Gradle command attempted:
  `./gradlew :core:common:testDebugUnitTest :core:crash:compileDebugKotlin --offline --build-cache`
- Result: **BLOCKED_ENVIRONMENT** — Gradle 8.9 is not cached locally and the wrapper attempted to reach `services.gradle.org`, which is unavailable in this environment.

## Known migration baseline for 362

- Static scan still finds legacy direct throwable/error `.message` usage across production modules. This is expected and intentionally not hidden by 361.
- Session 362 must migrate these feature paths to the structured failure contract instead of adding more heuristics to `ErrorHumanizer`.

## Files added

- `core/common/src/main/kotlin/com/verto/app/core/error/AppFailure.kt`
- `core/common/src/main/kotlin/com/verto/app/core/error/StructuredFailures.kt`
- `core/common/src/main/kotlin/com/verto/app/core/error/ErrorClassifier.kt`
- `core/common/src/test/kotlin/com/verto/app/core/error/ErrorClassifierTest.kt`
- `tools/quality/session361_verify.py`
- `SESSION_361_ERROR_CLASSIFICATION_FOUNDATION.md`

## Files changed

- `core/crash/src/main/kotlin/com/verto/app/utils/ErrorHumanizer.kt`
- `CHANGELOG.md`

**SESSION_361_STATIC = PASS**  
**SESSION_361_CORE_COMPILE = PASS_KOTLINC**  
**SESSION_361_BUILD = BLOCKED_ENVIRONMENT**
