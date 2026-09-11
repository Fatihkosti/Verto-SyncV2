# SESSION 366 — Auth Error Cutover

## Scope
Authentication only: remove the legacy Auth error/presentation path, connect authentication to the canonical Verto error pipeline, and harden post-login session completion.

## Removed
- `feature/auth/.../domain/model/AuthFailure.kt`
- `feature/auth/.../presentation/AuthUiMessage.kt`
- Legacy `AuthGateway.setNewPassword(...)` compatibility API
- Legacy `AuthRepository/AuthAccountRemoteSource.setNewPassword(...)` bridge
- Old `auth_*` error strings that were only consumed by `AuthUiMessage`
- Unused AuthViewModel invite-code/info-message legacy state/actions

## Canonical path now
`Supabase/data failure -> AuthErrorTranslator -> ErrorClassifier/AppFailure -> ErrorPresentationPolicy -> UserErrorPresentation -> VertoUserError/resolveVertoError`

### Important behavior
- `Throwable.message` is no longer parsed to classify authentication failures.
- Stable provider `statusCode/errorCode` metadata is used when available.
- `refresh_token_not_found`, `refresh_token_already_used`, `session_not_found`, and `bad_jwt` map to `AppFailure.Unauthorized`.
- Invalid OTP maps to `AUTH_INVALID_OTP` only from OTP failure metadata/OTP operation.
- OTP format validation is a separate `AUTH_OTP_FORMAT` path.
- A six-digit OTP rejected by the server no longer displays “enter a six-digit code”.

## Login completion hardening
- `SyncManager` is now `@Singleton`, so all consumers share the same mutex/checkpoint coordination instance.
- `prepareSessionForOrg` remains the critical post-auth boundary.
- Realtime stop, Optimal scheduling, FCM, role refresh, and permission refresh are best-effort after successful authentication and no longer convert a successful login into a login error.
- Critical completion failures are recorded through `CrashReporter` and presented through the canonical error system.

## Verification
- Kotlin source audit: zero references to legacy `AuthFailure` or `AuthUiMessage`.
- Auth source audit: zero `ErrorHumanizer` references and zero token/OTP message parsing.
- `AuthFailure.kt` and `AuthUiMessage.kt`: physically removed.
- Core error + AuthErrorTranslator compiled with local `kotlinc`: PASS.
- Runtime mapping probe:
  - `refresh_token_not_found` -> `Unauthorized`: PASS.
  - `invalid_otp` -> `AUTH_INVALID_OTP`: PASS.
  - `invalid_credentials` -> `AUTH_INVALID_CREDENTIALS`: PASS.
- Modified Android string resources parse as valid XML: PASS.
- Gradle unit test execution: BLOCKED_ENVIRONMENT. The wrapper attempted to obtain Gradle 8.9, but network access to `services.gradle.org` is unavailable in this environment. No dependency versions were changed.
