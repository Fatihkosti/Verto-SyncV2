# Session 378 — Optimal live company selection repair

## Problem
The Optimal join-code picker observed the Room `clients` projection immediately while the remote refresher only merged current server companies. It never removed or masked local company rows that no longer existed on the server. A stale local company could therefore be displayed and selected, after which the server correctly rejected `verto_issue_optimal_registration_code` with `client_not_found`.

## Repair
- `OptimalCompaniesRemoteRefresher.refresh()` now returns `OptimalCompaniesRefreshSnapshot` containing the current server COMPANY client IDs for the active Verto organization.
- `OptimalCodesViewModel` waits for that refresh before exposing company rows.
- The Room projection is filtered by the authoritative server ID set before it reaches UI state.
- If remote refresh fails, the picker fails closed with an empty list instead of exposing stale local candidates.

## Invariants preserved
- No Supabase schema or RPC changes.
- Company-specific code issuance still calls `verto_issue_optimal_registration_code(p_client_id, 1440)`.
- Room remains the local projection for names/link state/invoice counts; server eligibility gates what can be selected for linking.
- Linked-company checks and permission guards remain unchanged.

## Verification
- Static assertions confirm the refresh snapshot carries remote company IDs and the picker filters by them.
- Gradle compile attempted with `:feature:integration:optimal:compileDebugKotlin --offline`; blocked before compilation because Gradle 8.9 is not cached and `services.gradle.org` is unreachable in the environment.
