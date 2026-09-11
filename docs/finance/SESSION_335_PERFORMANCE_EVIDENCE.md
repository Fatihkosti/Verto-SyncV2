---
status: supporting
scope: finance
owner: "finance-performance"
last_verified_against: v335
---
# Session 335 Performance Evidence

## Host-side bounded-work fixture

`tools/quality/verto_finance_sync_v335.py` created and queried an in-memory SQLite fixture containing:

- 100,000 expenses
- 250,000 cash movements
- 25,000 Outbox rows
- 5,000 pending/retry rows

The push eligibility fixture returned a bounded 500-row scan. The run took about 0.8 seconds on the host, but this timing is **not** an Android/device benchmark and is not used as a production latency claim.

| Operation | Dataset | Before complexity | After complexity | Bound/Index | Result |
|---|---:|---|---|---|---|
| cash push | 250k history | legacy O(N) history materialization | O(batch) V2 Outbox | push default 50; scan cap 500 | PASS static/model |
| expense month | 100k expenses | range scan risk | indexed range | `(lifecycle_state,date)` | PASS query plan |
| category summary | 100k expenses | range/grouping | indexed date filter + grouped result | `(lifecycle_state,date)` plus category composite available | PASS query plan |
| cash recent | 250k movements | history materialization risk | index-ordered limited read | `createdAt` index + LIMIT | PASS query plan |
| pull | revision changes | bounded pages | bounded pages | 100/page, 5 pages, <=1000 changes/run | PASS static |

## Critical distinction

The legacy full-history methods remain only for pre-V2 rollout compatibility. Session 335 proves that the V2 owner route cannot call `getAllMovementsSync()` and pushes from durable pending Outbox work instead.
