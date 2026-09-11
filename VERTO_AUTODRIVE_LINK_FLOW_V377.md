# Verto ↔ AutoDrive link flow — V377

Status: implemented in Verto and applied to Supabase. AutoDrive v75 source code was not modified.

## Adopted flow

1. AutoDrive: phone number.
2. AutoDrive: OTP verification.
3. Existing linked user: `autodrive_users.user_id` resolves and the user proceeds to Dashboard.
4. New user: enters the 8-digit Verto invite code.
5. AutoDrive calls the existing `verify_invite_code_v2` / `redeem_invite_code` flow.
6. Redemption creates the AutoDrive identity with `autodrive_users.client_id` equal to the Verto `clients.id` embedded in the invite.

## Verto changes

- The AutoDrive management screen no longer exposes join-request approval/create/link/reject operations.
- “New join code” loads candidates from `verto_autodrive_join_code_candidates_v1`.
- Candidates are limited server-side to the current organization, `MARKETER` or `WORKSHOP_OWNER`, with no existing `autodrive_users` link.
- Linked clients are also hidden from the commission client picker.
- The generated code is 8 numeric digits and expires after 24 hours.

## Server changes

- Added `invite_codes.autodrive_account_type`.
- Added dedicated `autodrive_generate_numeric_code()`.
- Added `verto_autodrive_join_code_candidates_v1()`.
- Hardened `verto_issue_autodrive_join_code(...)` against wrong organization/type and already-linked clients.
- Hardened `redeem_invite_code(...)` while preserving its AutoDrive v75 signature.
- Redemption records `used_by_user_id` and binds the authenticated AutoDrive user to the exact Verto client record carried by the invite.
- Legacy Verto join-request management RPCs were revoked from `authenticated` users; data was not destructively dropped.

## Verification

- Live migration applied successfully on Supabase project `Verto-app`.
- Populated organization check: 8 workshop/marketer records total, 3 currently unlinked candidates.
- Privilege check: legacy Verto join-request RPCs = not executable; candidate/issue/redeem RPCs = executable as designed.
- Static source check: no active Verto management references to the legacy join-request workflow.
- Android compile could not be run because the environment has no cached Gradle 8.9 distribution and outbound network access is unavailable.
