# Verto Session 382 — Authentication Cutover & Legacy Removal

## Scope
Verto authentication only. AutoDrive / Optimal / Max authentication systems were intentionally untouched.

## Result
- Android authentication path is single-source: Splash -> live `verto_auth_session_status()` -> Home only on `AUTHORIZED`.
- Removed startup shortcut that admitted any locally cached Supabase session directly to Home.
- External navigation intents are queued until successful authentication admission.
- Deferred startup services now begin only after Home admission.
- Password recovery is OTP-only in the Android client.
- Removed `com.verto://reset-password` Android intent filter and all deep-link import/handling code.
- Removed recovery redirect URL usage from `resetPasswordForEmail`.
- Recovery OTP must be verified by Supabase for the same email before password update.
- Recovery session state is persisted as a temporary guard and is wiped after password update/logout.
- Splash detects an unfinished recovery session and wipes/signs out instead of admitting it.
- Removed obsolete local Splash profile fallback/status compatibility API.
- Removed default recovery fallback implementations from `AuthGateway`; recovery is now a mandatory contract.
- Removed auth `legacy_ui_*` resource aliases and renamed them semantically.
- Closed duplicate-submit races in auth actions by setting loading state before coroutine launch.

## Live Supabase hardening
Applied migration: `verto_auth_recovery_session_block_v382`.
`verto_auth_session_status()` now blocks JWT AMR methods `recovery`, `otp`, and `magiclink` from becoming operational Verto sessions.

### Live verification
- simulated password session -> `AUTHORIZED`
- simulated recovery session -> `ACCOUNT_BLOCKED`
- simulated `email/signup` session -> `AUTHORIZED`
- function remains `SECURITY DEFINER` with fixed `search_path`
- `EXECUTE` remains restricted to `authenticated` and `service_role`; `anon`/`public` revoked

## Hosted Supabase Auth configuration still required
The connected Supabase tooling does not expose the hosted Auth configuration PATCH endpoint. The hosted **Reset password** email template is still link-based according to live Auth logs.

Set the hosted Recovery template to an OTP-only body using `{{ .Token }}` and remove the old redirect allow-list entry `com.verto://reset-password`.

Recommended body:
```html
<h2>إعادة تعيين كلمة المرور</h2>
<p>رمز التحقق الخاص بك:</p>
<p style="font-size:28px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>إذا لم تطلب تغيير كلمة المرور فتجاهل هذه الرسالة.</p>
```

The Android application no longer registers or consumes that legacy redirect, so the legacy route is dead client-side even before hosted configuration is cleaned.

## Verification
Static authentication scan found no auth `legacy`, `reset-password`, deep-link handler, recovery redirect, old Splash profile fallback, or obsolete permission status identifiers.
The only remaining `legacy` matches in the wider repository are unrelated invoice/inventory/party migration fields and were not altered.

Full Gradle verification is environment-blocked because Gradle 8.9 is not cached and the runtime cannot reach `services.gradle.org` (`UnknownHostException`). No Gradle task began.
