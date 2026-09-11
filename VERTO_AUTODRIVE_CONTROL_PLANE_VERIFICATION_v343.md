> **SUPERSEDED BY V377 (2026-08-29):** The Verto join-request approval flow documented below is no longer active. Verto now issues AutoDrive invite codes only for unlinked MARKETER/WORKSHOP_OWNER client records; AutoDrive v75 remains unchanged.

# Verto AutoDrive Control Plane — Verification v343

## Input

- Baseline: `Verto-v342-auth-integrated.zip`
- Scope: Verto-side AutoDrive control plane integration only.

## Implemented

### Benzine control plane
- Pending AutoDrive join requests loaded through `verto_autodrive_join_requests_v1`.
- Approve with a newly-created Verto client through `verto_approve_autodrive_join_request_v1`.
- Approve by linking an explicitly selected existing Verto client.
- Reject through `verto_reject_autodrive_join_request_v1`.
- User health loaded through `verto_autodrive_health_v1`.
- Client errors loaded through `verto_autodrive_errors_v1`.
- Summary for pending / healthy / warning / critical / offline / recent errors.
- Existing commissions and marketer/workshop screens remain reachable from Benzine.

### Identity safety
- Approval does not infer canonical client identity from phone.
- Existing-client linking requires explicit Verto client selection.
- New-client creation requires an explicit confirmation.
- Client candidate search filters to the approved AutoDrive account type.

### Architecture
- New feature-side contract: `BenzineControlPlaneGateway`.
- Supabase implementation is isolated in the app bridge.
- New control-plane bridge contains no direct PostgREST table access.
- Verto UI has no knowledge of AutoDrive physical table names.

### Legacy registration
- The AutoDrive join-code action was removed from the commission-management UI.
- Existing legacy join-code implementation remains internally for controlled rollback/compatibility only; it is no longer reachable from that screen.
- Optimal join-code behavior was not changed.

### Authorization
- Benzine route now requires both `viewManagement` and `commissionManage`, matching the current server-side AutoDrive management contract.

## Server contract verification

Verified against live Supabase project `madkfvggyolmdberzmtb`:

- `verto_autodrive_join_requests_v1(p_status text, p_limit integer, p_before timestamptz)`
- `verto_autodrive_health_v1()`
- `verto_autodrive_errors_v1(p_limit integer, p_before timestamptz, p_severity text)`
- `verto_approve_autodrive_join_request_v1(p_request_id uuid, p_client_id uuid, p_create_client boolean)`
- `verto_reject_autodrive_join_request_v1(p_request_id uuid, p_reason text)`

## Static verification

- New control-plane Kotlin files: balanced braces/parentheses.
- Pure domain + gateway Kotlin compile: PASS via local `kotlinc`.
- New control-plane bridge direct-table PostgREST references: 0.
- Legacy join-code UI references in `CommissionManagementScreen`: 0.
- Scope diff checked against v342 baseline.

## Gradle verification

Attempted:

```text
./gradlew :feature:management:compileDebugKotlin :app:compileDebugKotlin --stacktrace
```

Result:

```text
BUILD_NOT_RUN_ENVIRONMENT
Gradle wrapper attempted to download Gradle 8.9 from services.gradle.org.
Environment has no external network access (UnknownHostException).
```

No Gradle PASS is claimed.

## Deliberately not claimed

This change does not claim that every historical AutoDrive-related transport in Verto has been migrated away from physical tables. Existing commission/message/withdrawal transports remain unchanged unless backed by a dedicated versioned server business contract. The new Benzine control-plane path itself is RPC-boundary based.

## Verdict

`IMPLEMENTED_STATIC / SERVER_CONTRACT_MATCHED / BUILD_NOT_RUN_ENVIRONMENT`
