---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v335
---
# Session 335 Server Contract Evidence

## Existing cash command

Static inspection of the existing v310 SQL proves that `verto_apply_cash_movement_v310`:

- derives/validates tenant authority server-side;
- requires stable `writeId` identity;
- applies `amountMinor` delta, not a client-authored final balance;
- locks the cash-register row before update;
- writes the immutable movement and server cash-register value transactionally;
- emits the secondary `CASH_REGISTER` change;
- rejects conflicting duplicate write identity through the existing uniqueness/idempotency contract.

Session 335 does not replace this cash RPC.

## Expense contract gap proven from v310

The v310 expense command treated an existing ACTIVE expense with changed materialization as `EXPENSE_COMMAND_CONFLICT`; it only accepted exact same category/amount as replay. That was insufficient for the existing `updateExpense` workflow.

`supabase/migrations/20260823060000_v335_finance_sync_authority.sql` is therefore append-only and replaces the expense command implementation so an existing ACTIVE expense may update its category/item/amount/note/date while incrementing sync version. A VOID expense is never revived. VOID/REVERSE semantics and outer mutation receipt idempotency remain intact.

## Deployment truth

The SQL is present and statically inspected only. No live Supabase deployment or concurrent server test ran here. `server_contract_runtime = BLOCKED_ENVIRONMENT` and `production_cutover = NOT_AUTHORIZED`.
