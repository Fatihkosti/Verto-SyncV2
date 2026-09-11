# Verto v384 — Authentication Test Report

Date: 2026-08-29
Base: Verto v383 authentication/OTP repair

## Executive result

- Executed deterministic checks: **43 PASS / 0 FAIL**.
- Android/JUnit test definitions present: **59**.
- JUnit/Compose execution: **BLOCKED_ENVIRONMENT** because Gradle 8.9 is not cached and `services.gradle.org` is unreachable.
- Android instrumentation/device execution: **BLOCKED_ENVIRONMENT** because `adb`/emulator is unavailable in this environment.
- No product test is currently failing in the checks that could actually execute.

## Executed — PASS

### A. Source/legacy/auth-contract checks — 20/20 PASS

1. PASS — `test_main_activity_always_starts_at_splash`
2. PASS — `test_main_activity_has_no_password_recovery_deep_link_handler`
3. PASS — `test_manifest_has_no_legacy_reset_password_deep_link`
4. PASS — `test_supabase_client_has_no_legacy_auth_deep_link_configuration`
5. PASS — `test_recovery_request_is_otp_only_no_redirect_url`
6. PASS — `test_recovery_verification_uses_supabase_recovery_otp`
7. PASS — `test_password_change_requires_verified_recovery_state_and_same_email`
8. PASS — `test_recovery_session_is_denied_before_splash_server_admission`
9. PASS — `test_recovery_otp_length_is_eight`
10. PASS — `test_clipboard_extraction_accepts_only_complete_eight_digit_code`
11. PASS — `test_otp_screen_attempts_clipboard_fill_when_returning_to_app`
12. PASS — `test_otp_component_supports_full_code_paste_and_completion`
13. PASS — `test_viewmodel_rejects_non_exact_otp_format`
14. PASS — `test_auth_gateway_contains_only_canonical_recovery_methods`
15. PASS — `test_login_uses_email_password_and_requires_active_profile`
16. PASS — `test_registration_provisions_organization_through_server_rpc`
17. PASS — `test_join_flow_validates_invite_then_calls_canonical_join_rpc`
18. PASS — `test_logged_in_password_change_reauthenticates_before_update`
19. PASS — `test_logout_clears_recovery_state_and_revokes_push_token_before_signout`
20. PASS — `test_no_legacy_reset_password_uri_in_production_sources`

Command: `python3 -m unittest -v tools/tests/test_auth_cutover_contract.py`

Note: the first draft of one harness assertion had an escaping mistake for the Kotlin regex. That test-code defect was corrected; the final rerun is 20/20 PASS. It was not a product failure.

### B. Pure Kotlin policy execution — 12/12 PASS

1. PASS — login trims valid email
2. PASS — login rejects blank email
3. PASS — login rejects malformed email
4. PASS — registration phone is optional
5. PASS — registration rejects alphabetic phone
6. PASS — password below 15 rejected
7. PASS — password length 15 accepted
8. PASS — recovery otp contract is 8 digits
9. PASS — clipboard extracts standalone 8 digit otp
10. PASS — clipboard extracts otp from email text
11. PASS — clipboard rejects old 6 digit otp
12. PASS — clipboard rejects 8 digits embedded in longer number

Execution used local `kotlinc`/`kotlin`; it does not depend on Gradle or Android.

### C. Live Supabase server checks — 11/11 PASS

1. PASS — RPC verto_auth_session_status hardening + ACL
2. PASS — Password AMR session -> AUTHORIZED
3. PASS — Recovery AMR session -> ACCOUNT_BLOCKED
4. PASS — OTP AMR session -> ACCOUNT_BLOCKED
5. PASS — Magic-link AMR session -> ACCOUNT_BLOCKED
6. PASS — Non-authenticated role -> ACCOUNT_BLOCKED
7. PASS — Anonymous session -> ACCOUNT_BLOCKED
8. PASS — Missing session_id -> ACCOUNT_BLOCKED
9. PASS — Valid auth session without Verto profile -> PROFILE_MISSING
10. PASS — Registration/join RPC identity + lock hardening
11. PASS — RLS enabled + policies present on auth-adjacent tables

Server checks were read-only with temporary request JWT claims; no application data was created or changed.

## Written JUnit / Compose tests — execution blocked


### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/AuthSystemBehaviorTest.kt`
- BLOCKED_ENVIRONMENT — blank login fails locally without contacting gateway
- BLOCKED_ENVIRONMENT — successful login admits organization only after gateway success
- BLOCKED_ENVIRONMENT — coordinator failure prevents successful admission
- BLOCKED_ENVIRONMENT — successful registration provisions then admits organization
- BLOCKED_ENVIRONMENT — registration gateway failure never admits organization
- BLOCKED_ENVIRONMENT — join organization is blocked until invite has been verified
- BLOCKED_ENVIRONMENT — verified invite can join and admit organization
- BLOCKED_ENVIRONMENT — blank recovery email never calls Supabase gateway
- BLOCKED_ENVIRONMENT — successful recovery request exposes target email
- BLOCKED_ENVIRONMENT — six digit legacy recovery otp is rejected without server call
- BLOCKED_ENVIRONMENT — eight digit recovery otp is sent unchanged to gateway
- BLOCKED_ENVIRONMENT — otp containing non digits is rejected without normalization
- BLOCKED_ENVIRONMENT — invalid server otp does not advance password recovery
- BLOCKED_ENVIRONMENT — short new password is rejected without update call
- BLOCKED_ENVIRONMENT — password mismatch is rejected without update call
- BLOCKED_ENVIRONMENT — verified recovery session can complete password update
- BLOCKED_ENVIRONMENT — missing recovery session remains fail closed
- BLOCKED_ENVIRONMENT — network failure remains distinguishable from credential failure

### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/AuthValidationPolicyTest.kt`
- BLOCKED_ENVIRONMENT — valid login accepts trimmed email
- BLOCKED_ENVIRONMENT — login rejects blank password and malformed email
- BLOCKED_ENVIRONMENT — registration phone is optional
- BLOCKED_ENVIRONMENT — password policy rejects below fifteen and accepts boundary
- BLOCKED_ENVIRONMENT — password policy accepts long passphrases

### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/AuthViewModelTest.kt`
- BLOCKED_ENVIRONMENT — login success completes session
- BLOCKED_ENVIRONMENT — invalid credentials use canonical auth message
- BLOCKED_ENVIRONMENT — duplicate login tap is ignored while request is in flight
- BLOCKED_ENVIRONMENT — duplicate recovery send tap is ignored while request is in flight
- BLOCKED_ENVIRONMENT — network login failure stays network
- BLOCKED_ENVIRONMENT — thrown gateway exception is fail closed
- BLOCKED_ENVIRONMENT — eight digit otp rejected by server is invalid otp not format error
- BLOCKED_ENVIRONMENT — malformed otp uses format error
- BLOCKED_ENVIRONMENT — server verified otp advances recovery
- BLOCKED_ENVIRONMENT — expired otp remains expired otp
- BLOCKED_ENVIRONMENT — password reset without recovery state is rejected
- BLOCKED_ENVIRONMENT — password reset after verified recovery can complete

### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/RecoveryOtpPolicyTest.kt`
- BLOCKED_ENVIRONMENT — extracts eight digit otp copied alone
- BLOCKED_ENVIRONMENT — extracts eight digit otp from copied email text
- BLOCKED_ENVIRONMENT — does not accept six digit legacy otp
- BLOCKED_ENVIRONMENT — does not take eight digits out of a longer number

### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/splash/SplashSecurityMatrixTest.kt`
- BLOCKED_ENVIRONMENT — every non authorized server status is denied and wiped
- BLOCKED_ENVIRONMENT — authorized status with failed refresh and no trusted offline is denied
- BLOCKED_ENVIRONMENT — authorized status with failed refresh may use explicit trusted offline
- BLOCKED_ENVIRONMENT — timeout is denied unless trusted offline session exists
- BLOCKED_ENVIRONMENT — unexpected initialization exception fails closed
- BLOCKED_ENVIRONMENT — pending recovery session is wiped before server admission call

### `feature/auth/src/test/kotlin/com/verto/app/feature/auth/presentation/splash/SplashViewModelTest.kt`
- BLOCKED_ENVIRONMENT — no session goes to login
- BLOCKED_ENVIRONMENT — recovery session is wiped and never admitted to home
- BLOCKED_ENVIRONMENT — authorized online session goes home
- BLOCKED_ENVIRONMENT — profile inactive is wiped and denied
- BLOCKED_ENVIRONMENT — blocked account is denied
- BLOCKED_ENVIRONMENT — network failure without trusted offline is denied without wipe
- BLOCKED_ENVIRONMENT — network failure permits only explicit trusted offline
- BLOCKED_ENVIRONMENT — unknown failure is wiped and denied

### `feature/auth/src/androidTest/kotlin/com/verto/app/feature/auth/presentation/RecoveryOtpUiTest.kt`
- BLOCKED_ENVIRONMENT — otp_input_renders_eight_editable_cells
- BLOCKED_ENVIRONMENT — full_eight_digit_paste_populates_code_and_completes_once
- BLOCKED_ENVIRONMENT — six_digit_legacy_paste_does_not_complete

### `app/src/test/kotlin/com/verto/app/feature/auth/data/AuthErrorTranslatorTest.kt`
- BLOCKED_ENVIRONMENT — refresh token failure is session failure never otp failure
- BLOCKED_ENVIRONMENT — invalid login credentials remain login credentials
- BLOCKED_ENVIRONMENT — invalid otp only maps inside otp operation

Total: **59** JUnit/Compose test cases could not be executed in this runtime.

## Exact Gradle blocker

Attempted:
`./gradlew :feature:auth:testDebugUnitTest :app:testDebugUnitTest --no-daemon --stacktrace`

Result:
- Gradle wrapper attempted to download `https://services.gradle.org/distributions/gradle-8.9-bin.zip`.
- Failed with `java.net.UnknownHostException: services.gradle.org`.
- Therefore this is **NOT RUN / BLOCKED_ENVIRONMENT**, not a failed unit-test suite.

## Device/UI blocker

- `adb` is unavailable in the execution environment.
- The 3 Compose instrumentation tests for the 8-cell OTP UI, full-code paste, and rejection of legacy 6-digit completion are written but cannot be run here.

## Production runtime observation

- Supabase Auth logs show a successful `/recover` request at 2026-08-29 19:57:48 UTC, but its referer is still `com.verto://reset-password`.
- v383 source no longer contains that legacy URI. Therefore that traffic came from an older installed build/client, not from v383 source.
- An end-to-end verdict on the new v383 installed APK requires installing/building v383+ and repeating recovery on a device.

## Coverage now included

- Login validation, successful login, invalid credentials, network failure, duplicate taps.
- Registration validation, provisioning success/failure, server provisioning RPC contract.
- Invite verification and organization join gate/RPC hardening.
- Splash admission, server authorization statuses, offline fallback, recovery-session denial.
- Recovery request, exact 8-digit OTP contract, invalid/expired OTP, clipboard extraction, full-code paste/autofill path.
- Recovery password update and missing-recovery-session fail-closed behavior.
- Logged-in password change reauthentication ordering.
- Logout cleanup ordering.
- Removal of password-reset deep-link legacy from MainActivity, manifest, Supabase client, and production auth sources.
- Server AMR blocking for recovery/otp/magiclink and RLS/ACL protections.

## Files added/updated for tests

- `feature/auth/src/test/.../AuthSystemBehaviorTest.kt`
- `feature/auth/src/test/.../splash/SplashSecurityMatrixTest.kt`
- `feature/auth/src/androidTest/.../RecoveryOtpUiTest.kt`
- `feature/auth/build.gradle.kts` (Compose UI test dependencies)
- `tools/tests/test_auth_cutover_contract.py`
- `tools/tests/AuthPurePolicyHarness.kt`
- `tools/tests/auth_server_session_gate.sql`
- `SESSION_384_AUTH_TEST_REPORT.md`
