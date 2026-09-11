# Verto v383 — Recovery OTP alignment and clipboard autofill

## Scope
- Align password-recovery UI with the hosted Supabase Recovery OTP length currently observed in production: 8 digits.
- Remove remaining 6-digit assumptions from the recovery screen, validation and OTP component.
- Support full-code paste into any OTP cell.
- Fill a copied 8-digit OTP from the clipboard when the app resumes from the email app.
- Fix completion dispatch so the final digit/pasted code is verified using the completed value rather than stale Compose state.

## Implementation
- Added `RecoveryOtpPolicy.kt` with one source of truth: `RECOVERY_OTP_LENGTH = 8`.
- `AuthViewModel.verifyOtp` now validates against the shared recovery OTP length.
- `PasswordResetSentScreen` submit/verify button gating now uses the shared length.
- `OtpCodeInput` renders 8 cells and accepts a complete pasted/autofilled code in any focused cell.
- Clipboard extraction only accepts a standalone 8-digit sequence; it does not truncate longer numbers or accept legacy 6-digit values.
- On `Lifecycle.Event.ON_RESUME`, the recovery screen reads a valid copied OTP and populates the field automatically.

## Verification
- Static scan: no 6-digit recovery assumptions remain in the production auth recovery path.
- `RecoveryOtpPolicy.kt`: standalone Kotlin compilation PASS.
- Targeted Gradle unit tests: NOT RUN / BLOCKED_ENVIRONMENT because Gradle 8.9 distribution is not cached and the environment cannot reach `services.gradle.org`.
