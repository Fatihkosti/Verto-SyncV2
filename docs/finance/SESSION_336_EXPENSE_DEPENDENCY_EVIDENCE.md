---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v336
---
# Session 336 Expense Dependency Evidence

Final dependency matrix:

| Expense operation | Local cash effect | Cash dependency |
|---|---|---|
| CREATE | cash out | expense CREATE mutation |
| UPDATE increase | additional cash out | expense UPDATE mutation |
| UPDATE equal | none | none |
| UPDATE decrease | refund | expense UPDATE mutation |
| VOID | refund | expense VOID mutation |

Code evidence:

- `CashRegisterManager.recordMovementMinor()` preserves the existing CREATE/UPDATE-increase dependency inline (`sourceType.startsWith("EXPENSE")`).
- `CashRegisterManager.reverseMovementMinor(...)` now accepts `CashReverseContext336`; its explicit `dependsOnMutationId` is stored in the reverse cash identity.
- `ExpenseRepository.refund(...)` passes the exact expense mutation id for UPDATE-decrease and VOID.
- `UnifiedSyncPushEngine.eligibility()` reads the parent row and blocks missing/PENDING/LEASED/RETRY/REJECTED/REQUIRES_REVIEW states; only ACKNOWLEDGED releases the child.
- The cash outbox mutation id remains `cash:<movementId>`, distinct from the expense mutation id, so self-dependency is rejected by construction and by the persistence guard.

Deterministic behavioral evidence:

```text
CREATE dependency             PASS
UPDATE increase dependency    PASS
UPDATE decrease dependency    PASS
VOID dependency               PASS
zero delta no cash row        PASS
missing parent blocked        PASS
PENDING/LEASED/RETRY blocked  PASS
REJECTED/REVIEW fail-closed   PASS
ACKNOWLEDGED releases child   PASS
self/cycle direction guard    PASS
```
