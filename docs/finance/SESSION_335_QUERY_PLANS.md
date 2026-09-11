---
status: supporting
scope: finance
owner: "finance-performance"
last_verified_against: v335
---
# Session 335 Query Plans

Host SQLite evidence from `SESSION_335_STATIC_EVIDENCE.json`:

```text
expense range:
SEARCH expenses USING INDEX index_expenses_lifecycle_date (lifecycle_state=? AND date>? AND date<?)

expense category summary:
SEARCH expenses USING INDEX index_expenses_lifecycle_date (lifecycle_state=? AND date>? AND date<?)
USE TEMP B-TREE FOR GROUP BY

cash recent:
SCAN cash_register_movements USING INDEX index_cash_register_movements_createdAt

eligible outbox:
SEARCH sync_outbox USING COVERING INDEX index_sync_outbox_delivery (organization_id=? AND state=? AND next_attempt_at<?)
USE TEMP B-TREE FOR ORDER BY
```

`SCAN ... USING INDEX index_cash_register_movements_createdAt` is an index-ordered limited scan for the recent-movement query; it is not the V2 push path and does not materialize all 250k rows. The V2 push route is driven by bounded Outbox eligibility and one aggregate lookup per selected mutation.

Room 83 adds indexes only for observed query shapes: expense lifecycle/date, expense lifecycle/category/date, and movement type/reference lookup.
