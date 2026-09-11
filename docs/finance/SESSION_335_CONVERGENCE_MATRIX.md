---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v335
---
# Session 335 Convergence Matrix

| Scenario | Expected final cash | Movement count | Duplicate? | Both devices converge? | Evidence |
|---|---:|---:|---|---|---|
| A -30, B -20 from 100 | 50 | 2 | NO | YES | server contract proves serialized delta semantics; two-device runtime BLOCKED_ENVIRONMENT |
| replay same `writeId` | unchanged after first | 1 | NO | YES | stable movement ID + server unique write identity; static PASS |
| offline expense | exact signed cash fact | 1 cash fact | NO | YES | expense Outbox dependency precedes linked cash movement; runtime BLOCKED_ENVIRONMENT |
| app kill after server apply/before ACK | exact | 1 | NO | YES | durable Outbox + stable mutation/write identity + receipt reconciliation contract; runtime BLOCKED_ENVIRONMENT |
| V2 paused | unchanged until V2 resumes | unchanged | NO | YES after resume | Legacy financial fallback remains disabled; static PASS |
| organization switch | no cross-org apply | scoped | NO | isolated | trusted session organization + existing scoped Outbox/receipt checks; static PASS |

The table states contract outcomes. It does not claim that two physical devices were executed in this environment.
