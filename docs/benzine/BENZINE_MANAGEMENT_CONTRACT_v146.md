# Benzine Management Contract — v146

## Purpose

This file freezes the Verto v145 baseline and the product/architecture decisions for sessions v146-v150. Session v146 changes no production behavior.

## Frozen decisions

### D-001 — Visible name
The user-visible name inside Verto is **بنزين**. `AutoDrive` or `Benzine` may appear only in non-user-facing technical identifiers.

### D-002 — Final primary entry flow
`الإعدادات / مركز الإدارة → بنزين → إدارة العمولات | المسوقون والورش`.
After v147 the Benzine management card must no longer open `users_dashboard` directly.

### D-003 — Ownership boundaries
- `feature/management`: Benzine card, Benzine gateway route, static gateway screen only.
- `feature/commission`: commission finance UI, withdrawal requests, finance attention items, existing commission reports.
- `feature/dashboard`: marketers/workshops, weekly performance, derived performance status, follow-up, leaderboard.
- `:app`: navigation composition, Android intents, and existing bridges only.

No new direct dependency may be introduced among `management`, `commission`, and `dashboard`.

### D-004 — No backend work in v146-v150
No SQL, RPC, RLS, Realtime publication, Room schema, migration, or backend contract changes are allowed.

### D-005 — Single week definition
Benzine weekly analysis uses the current competition boundary: Friday 09:00 in `Asia/Riyadh`.

### D-006 — Eligible weekly invoice statuses
Only `CLOSED_CASH` and `CLOSED_CREDIT` participate in weekly analysis.

### D-007 — Weekly performance fields
Per account derive: `currentWeekSales`, `previousWeekSales`, `currentWeekCommission`, `previousWeekCommission`, `currentWeekInvoices`, `previousWeekInvoices`, `firstPurchaseAt`, `lastPurchaseAt` from the current read contracts.

### D-008 — Derived performance statuses
Statuses are not stored. Allowed values are `NEW`, `ACTIVE`, `GROWING`, `DECLINING`, `INACTIVE`, with the priority and fallback defined in the execution plan.

### D-009 — Follow-up is non-financial
Follow-up reasons are only `NEW_NOT_STARTED`, `DECLINING`, `INACTIVE`; finance state never becomes a follow-up performance reason.

### D-010 — Week-over-week delta
If previous sales are positive use `((current - previous) / previous) * 100`; previous zero/current positive means `بدأ هذا الأسبوع`; both zero means `—`.

### D-011 — Active this week
`currentWeekInvoices > 0`. Online/last-seen is not the commercial active definition.

### D-012 — Final filters
`الكل`, `مسوقون`, `ورش`, `نشطون`, `جدد`, `يحتاج متابعة` with the exact mappings defined by the execution plan.

### D-013 — Financial attention items
Only two item types are allowed: open `WithdrawalRequest` (`PENDING` or `APPROVED`) and `ReadyPayout` for withdrawable commission when the same client has no open request. `REJECTED` and `COMPLETED` are excluded. Over-balance remains a defensive guard, not a KPI/action.

### D-014 — Mutation contracts remain unchanged
The semantics of `WithdrawAll`, `WithdrawSingle`, `WithdrawFreeAmount`, `ApproveRequest`, `RejectRequest`, `CompleteRequest`, `DeleteCommissionPayment`, and `GenerateJoinCode` remain unchanged.

### D-015 — Generate Join Code stays legacy-compatible
Its repository/RPC/contract is not moved. If the current Add action is its only entry, it remains a deliberate legacy exception.

### D-016 — Deep links and notifications stay direct
Finance notifications may open Commission Management directly; marketer reminders may open Users Dashboard/details directly. The Benzine gateway is the primary navigation path, not a mandatory deep-link intermediary.

## Verto v145 frozen current state

- Benzine route: `users_dashboard`.
- Commission route: `commission_management`.
- Users route: `users_dashboard`.
- User detail route: `user_dashboard_detail/{clientId}`.
- Room schema: `49`.
- Gradle modules: `31`.
- `get_marketer_stats` RPC exists through `WithdrawalRepository.getMarketerStats()`.
- `commission_eligibility` read source exists through `WithdrawalRepository.getCommissionEligibility()`.
- `DashboardAdminGateway` exposes `getMarketerStats()` and `getCommissionEligibility()`.
- `CommissionViewModel` is a thin delegate over `CommissionController`.
- `CommissionControllerAdapter` owns the current infrastructure dependencies and merges commission/withdrawal state.
- `LeaderboardViewModel` currently defines the competition week as Friday 09:00 `Asia/Riyadh` and includes only `CLOSED_CASH` / `CLOSED_CREDIT`.
- Account types used for Benzine are `MARKETER` and `WORKSHOP_OWNER`.
- Legacy AutoDrive notification filtering still contains `WITHDRAWAL_APPROVED`, `WITHDRAWAL_REJECTED`, `WITHDRAWAL_COMPLETED`, `INACTIVITY`, and `ADMIN_REMINDER`.

## Protected architecture for subsequent sessions

```text
                  :app
       Navigation / Composition Root
             /       |        \
            v        v         v
 feature/management  feature/commission  feature/dashboard
   Benzine Hub         Finance UI        Performance UI
```

Forbidden dependency directions:

```text
management -> commission/dashboard
commission -> management/dashboard
dashboard -> management/commission
Compose/ViewModel -> Repository/DAO/Supabase in the protected presentation paths
```

The only already-existing feature relationship inside this protected trio that may remain is `feature/dashboard -> feature/dashboard:api`.

## Session v146 invariant

Session v146 adds only this contract, `BenzineProtectedBoundaryArchitectureTest.kt`, and `verification-v146.md`. No production source, Gradle dependency, Room schema, backend, sync, realtime, or navigation behavior may change.
