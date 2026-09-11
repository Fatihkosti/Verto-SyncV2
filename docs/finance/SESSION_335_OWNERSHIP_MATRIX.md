---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v335
---
# Session 335 Ownership Matrix

| Aggregate | Local source | Push owner | Server owner | Pull owner | Legacy writer enabled? |
|---|---|---|---|---|---|
| EXPENSE | Room `expenses` + durable Outbox | Unified owner310 stronger route | `verto_apply_expense_command_v310` | unified revision/change-feed apply | YES only before V2 ownership; NO under V2 |
| CASH_MOVEMENT | Room immutable movement + durable Outbox | Unified owner310 stronger route | `verto_apply_cash_movement_v310` | unified revision/change-feed apply | YES only before V2 ownership; NO under V2 |
| CASH_REGISTER | Room read model | NONE | server transaction inside cash movement RPC | unified revision/change-feed apply | YES only before V2 ownership; NO under V2 |

## Ownership invariant

For V2-owned finance, all three aggregates must resolve to `V2_AUTHORITATIVE`, `V2_PAUSED_SAFE`, or `RETIRED_LEGACY` before Legacy financial steps are suppressed. A paused V2 path stays fail-closed rather than silently switching writers.
