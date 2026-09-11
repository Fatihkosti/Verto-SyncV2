# Invoice Financial Invariants — Frozen at Session 243

These invariants are the acceptance contract for sessions 244–251. They are derived from the approved plan and mapped against v242 current behavior.

## A. Money and quantity

1. Financial amounts, unit prices, exchange rates, taxes/discounts, cash values and report totals must not use `Double`/`Float` as the persisted/domain source of truth.
2. One deterministic rounding policy must be shared by UI/domain/persistence boundaries.
3. Invalid quantity text must fail; it must never become `1` silently.
4. Invalid/blank monetary text must fail where value is required; it must never become `0` silently.
5. Financial overflow/out-of-range values must fail explicitly.

Current v242 violations/evidence:
- financial entities use `Double`.
- `InvoiceSaveValidator`, `InvoiceDraftFactory`, and `InvoiceInventoryWriter` use `toIntOrNull() ?: 1` and monetary `?: 0.0` fallbacks.
- `MoneyMath` improves calculation precision internally but explicitly returns/stores `Double` and rounds each operation to two decimals, so it is not the target Money Core required by 244.

## B. Atomic invoice command

6. A financial command must commit invoice + lines + payment allocation + cash + inventory + revaluation + audit + durable sync event together, or commit none.
7. Any unexpected affected-row count or child failure must abort the owner transaction.
8. Network calls must not run inside the local Room transaction.
9. Post-commit UI/notification failures must not mutate financial truth.

Current v242 status:
- local invoice/payment/cash/inventory writes can share one Room transaction: good foundation.
- invoice/payment audit writes are currently post-commit: invariant not yet met.
- regular Verto sync uses dirty flags rather than an owner-transaction Outbox: invariant not yet met.

## C. Idempotency and concurrency

10. Every financial command owns a stable `writeId/requestId` from first attempt through every retry.
11. Uniqueness must be enforced by the database, not only UI state.
12. Repeating the same write id produces one financial effect.
13. Stock deduction must be race-safe at the database update level; two concurrent sales cannot both consume the same protected quantity.
14. Derived movements need stable source identity/version so replay cannot duplicate them.
15. Supplier external invoice number, when present, must be normalized and unique per organization + supplier.

Current v242 gaps:
- standalone payment uses request id as PK, which gives useful local duplicate protection.
- invoice `writeId` is generated in `SaveInvoiceUseCase` but not stored on invoice and has no local invoice unique constraint.
- invoice number is indexed but not unique.
- supplier external invoice number is not represented in the current invoice entity.
- stock deduct currently does read → validate → update inside a Room transaction. It prevents interleaving inside the same SQLite writer transaction, but 245 still requires a conditional affected-row update contract rather than treating this as the final concurrency invariant.

## D. Inventory identity and purchase price

16. `inventoryItemId` is authoritative; item-name matching is never a silent financial fallback.
17. Local purchase accepted/posting sets the newest purchase price as `currentBuyPrice` for the entire current balance.
18. No weighted-average-cost path may be introduced.
19. Repricing current stock must emit a revaluation event.
20. Historical sales preserve `unitCostAtSale` and historical profit.
21. Sales price is independent from buy price.
22. International purchase invoice creation does not enter stock; accepted receiving is the stock gate.
23. Landed cost is allocated only to accepted received goods using deterministic allocation and rounding.

Current v242 gaps/status:
- international purchase skip-inventory behavior already exists.
- local purchase updates `buyPrice` to latest positive value before adding quantity: directionally matches last-purchase-price policy, but no revaluation event/snapshot contract exists.
- item lookup can fall back to normalized name.
- invoice lines do not store `unitCostAtSale`, revenue/cost/profit snapshots.

## E. Currency and payments

24. Every international invoice stores transaction currency and functional currency separately.
25. Historical invoice exchange rate and functional recognition amount are immutable snapshots.
26. Payment allocation to invoice is modeled explicitly.
27. Payment stores supplier-currency amount and actual functional cash amount/rate.
28. Realized FX difference is stored as its own event/value.
29. Historical invoice values are never recalculated with today's rate.
30. Unknown legacy currency is marked unknown/review-required; it is not guessed.

Current v242 gaps:
- invoice/payment entities have no currency columns.
- invoice command carries only `exchangeRate: Double` and it is not persisted on invoice/payment.
- initial payment converts to local cash in `InvoicePaymentWriter`, but the historical rate is only placed in a text note.
- `PaymentAllocation` table does not exist.

## F. Lifecycle, reversals and audit

31. Financial lifecycle is `DRAFT / POSTED / VOID`; payment state is independent and derived.
32. `POSTED` lines are financially immutable.
33. Financial correction uses reversal/return/replacement documents, never destructive rewrite.
34. Void/reversal uses original historical movements and snapshots, not a value recalculated from today's state.
35. Posted financial records are never hard-deleted.
36. Every override/edit/void records actor, occurredAt, recordedAt, reason and before/after values.
37. Aggregate optimistic version prevents silent concurrent edits.

Current v242 gaps:
- stored invoice status is only `CLOSED_CASH / CLOSED_CREDIT`; `voided` is a separate Boolean.
- full financial edit replaces items and reconstructs inventory/cash consequences.
- void creates compensating inventory/cash movements, but invoice aggregate does not preserve all required immutable historical snapshots/version/reason.
- audit is post-commit.

## G. Sync

38. Local DB is offline-first display truth.
39. Every financial transaction writes a durable Outbox event inside the same transaction.
40. Delivery can be at-least-once; financial effect must be exactly-once via idempotency/constraints.
41. Inbox prevents duplicate remote application.
42. Aggregate event order is preserved.
43. Posted/void financial aggregates never use field-level Last-Write-Wins.
44. Unresolvable financial conflict becomes `REQUIRES_REVIEW`.
45. Payloads are schema-versioned and old/new client transition is contract-tested.

Current v242 gaps:
- normal Verto invoice sync is dirty-row table upsert/pull.
- no general invoice financial Inbox.
- no invoice aggregate version/schemaVersion in normal sync payload.
- Optimal integration outbox has sequence/idempotency machinery, but it is not the regular invoice sync transport.

## H. Reports/reconciliation

46. Mixed currencies are never summed without stored functional conversion.
47. Realized gross profit uses historical `unitCostAtSale`.
48. Replacement margin uses current buy price and is explicitly named differently.
49. Voids/reversals/returns remain traceable; originals are not erased.
50. Reconciliation must prove invoice balance, payments, cash, inventory quantity/value and sync-event completeness.

## Exit rule

Any implementation in 244–251 that violates one of these invariants fails the session even when the screen appears correct.
