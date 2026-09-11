---
status: supporting
scope: finance
owner: "finance-integrity"
last_verified_against: v334
---
# Session 334 Transaction Matrix

| Workflow | Top-level boundary | Cash effect | Outbox | Failure behavior |
|---|---|---|---|---|
| Expense insert | `ExpenseRepository.db.withTransaction` | exact `amountMinor` out | same transaction | rollback all |
| Expense update increase | same | exact positive delta out | same transaction | rollback all |
| Expense update decrease | same | `EXPENSE_UPDATE_REFUND` | same transaction | rollback all |
| Expense void | same | `EXPENSE_VOID_REFUND` once | same transaction | VOID state prevents second refund |
| Manual cash adjust | adapter `database.withTransaction` | exact minor add/deduct | same transaction | permission/write failure rolls back |
| Linked expense | adapter outer `database.withTransaction` | expense cash effect | expense + inventory intents | any landed-cost failure rolls back entire local workflow |

Executable Gradle transaction tests are BLOCKED_ENVIRONMENT in this sandbox; the contract must not treat static inspection as their PASS result.
