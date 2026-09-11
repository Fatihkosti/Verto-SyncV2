# Session 374 — Optimal organization link repair

## Scope
Repair the Verto side of the Verto ↔ Optimal onboarding link so the current Verto organization issues the Optimal code directly.

## Implemented
- Removed Verto client/company selection from the Optimal code screen.
- Removed `clientId` from the registration repository/use-case/remote-source contract.
- Updated the Supabase RPC call to `verto_issue_optimal_company_join_code(p_expires_in_minutes := 1440)`.
- Updated RPC DTO parsing to the live organization-level response: `organization_id`, `organization_name`, `code`, `expires_at`.
- Kept the backend registration-ready check and permission guards.
- Added tenant validation: returned `organization_id` must equal the active Verto organization.
- Added code validation: exactly 8 numeric digits and a future expiry.
- Updated the UI and labels to describe organization linking rather than selecting an existing Verto client.
- Updated unit coverage for the organization-level registration repository contract.

## Removed legacy behavior
- No search/select client flow on `OptimalCodesScreen`.
- No `p_client_id` sent to the Optimal company-join RPC.
- No local requirement that an existing Verto COMPANY client be selected before issuing a code.

## Verification
- XML resource parsing: PASS.
- Static contract scan: PASS (`p_client_id` absent from the Optimal registration call path).
- Gradle test/build: BLOCKED_ENVIRONMENT. The Gradle 8.9 distribution is not cached and the sandbox has no network access; the wrapper attempted to fetch `services.gradle.org`.
