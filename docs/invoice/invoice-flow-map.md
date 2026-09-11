# Invoice Flow Map — Verto v243 Baseline

Source inspected: `Verto-v242.zip`.
Purpose: freeze the actual financial write paths before sessions 244–251. This file documents current behavior; it does not approve it.

## 1. Current persistence boundary

- Room database: `AppDatabase`.
- Current schema: `ROOM_SCHEMA_VERSION = 61`.
- `RoomDatabaseTransactionRunner` delegates to `AppDatabase.withTransaction`.
- Invoice, invoice items, payments, cash register, inventory, audit, logistics, and Optimal outbox entities are all registered in this same Room database.
- Therefore a single local Room transaction is technically possible for the required invoice aggregate. The blocker is orchestration/contract design, not multiple local databases.

Evidence:
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt`
- `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/RoomDatabaseTransactionRunner.kt`

## 2. Create invoice

UI path:

`AddDebtViewModel.saveOrUpdateInvoice`
→ `PaymentDebtWorkflowService`
→ `PaymentDebtWorkflowBridge.saveInvoice`
→ `SaveInvoiceUseCase`
→ `InvoiceWriteCoordinator.save`

Application path:

1. Authorization.
2. `InvoiceSaveValidator.validate`.
3. Optional inventory-reference validation for sales.
4. Invoice number allocation.
5. `InvoiceDraftFactory.create`.
6. Load inventory context before the transaction.
7. `InvoiceTransactionPort.inTransaction`.
8. `InvoiceStorePort.insertInvoiceWithItems`.
9. `InvoicePaymentWriter.writeForCreate`.
10. `InvoiceInventoryWriter.writeForCreate`.
11. `InvoiceAtomicPersistenceCoordinator.persist` for integration-owned artifacts/outbox.
12. Commit.
13. `InvoicePostCommitEffects.afterCreate`: schedule sync, write invoice audit log, then notifications.

Current write targets:
- `invoices`
- `invoice_items`
- optionally `payments`
- optionally `cash_register` + `cash_register_movements`
- optionally `inventory_items` + `inventory_movements`
- optionally `optimal_outbox` and maintenance-owned rows through integration extension
- invoice `AuditLog` is currently **post-commit**, not inside the invoice transaction

Important current semantics:
- There is no separate DRAFT → POSTED command.
- Save creates a financially active invoice directly using `CLOSED_CASH` or `CLOSED_CREDIT`.
- `SaveInvoiceUseCase` generates a fresh UUID `writeId` for each invocation; it is not currently persisted as an invoice idempotency key.

## 3. Edit invoice

Path:

`AddDebtViewModel`
→ `SaveInvoiceUseCase`
→ `InvoiceWriteCoordinator.save`
→ `saveEdit`

Two branches:

### 3.1 Non-financial edit
Inside Room transaction:
- update invoice
- persist integration artifacts

After commit:
- schedule sync
- audit update

### 3.2 Full financial edit
Inside Room transaction:
- `InvoiceInventoryWriter.reverseForEdit`
- replace invoice + all invoice items
- write new inventory effects
- reconcile/delete/recreate payment records as needed
- reconcile cash movements
- persist integration artifacts

After commit:
- schedule sync
- audit update

Risk captured for later sessions:
- `reverseForEdit` deletes old invoice movements, then reconstructs effects from invoice lines/current inventory lookup rather than preserving immutable original financial events.
- Item resolution can fall back to normalized item name.
- Full edit is allowed by current lifecycle model; there is no stored `POSTED` immutability state.

## 4. Payment

UI path:

`AddPaymentScreen`
→ `AddPaymentUseCase`
→ `RecordPaymentCoordinator.record`

Local mode (`FeatureFlags.isFinancialMutationsEnabled == false`, current default):

1. Authorization and amount checks.
2. Load invoice; reject voided invoice.
3. Build `PaymentRecord` with `id = requestId`.
4. `PaymentTransactionPort.inTransaction`:
   - recompute remaining
   - insert payment with Room `IGNORE`; duplicate PK returns false
   - write cash movement
   - persist integration event/outbox
5. Commit.
6. Audit and notification are post-commit.

Remote-authoritative mode (feature flag enabled):

1. RPC `post_payment_v2` executes first with `p_client_request_id`.
2. Returned payment is mirrored locally inside Room transaction.
3. Integration event/outbox is written locally.
4. No local cash movement is performed in this client branch because the server RPC owns that financial mutation.

This remote-first branch is disabled by default in v242 but must remain accounted for in sessions 246/249.

## 5. Reverse payment

`ReversePaymentUseCase`
→ `ReversePaymentCoordinator.reverse`

Local mode:
- validates original and absence of reversal
- creates negative payment whose id is `requestId`
- Room transaction inserts reversal payment, writes opposite cash movement, persists integration event
- audit is post-commit

Remote mode:
- RPC `reverse_payment_v2` first
- mirror returned reversal locally + persist integration event

## 6. Void invoice

`InvoiceViewModel.deleteInvoiceById`
→ `VoidInvoiceUseCase`
→ `InvoiceVoidCoordinator.void`

Inside invoice Room transaction:
1. reverse inventory movements
2. reverse cash according to invoice/payment state
3. mark `invoices.voided = true`
4. append integration void event when organization is available

After commit:
- audit log `logDelete`
- UI requests sync

Current idempotency:
- already-voided invoice returns early locally
- integration void event uses stable logical identity
- there is no invoice-level persisted `voidRequestId` / version column

## 7. International purchase / receive

Invoice creation behavior:
- `PurchaseScope.INTERNATIONAL` causes `InvoiceInventoryWriter.skipInventory`.
- Therefore creating the international purchase invoice does **not** add stock.

Logistics path:
- purchase invoice lines are consumed by Logistics v2 adapters.
- receiving is handled in shipment/logistics application + Room adapters.
- inventory posting uses `InventoryDao.receiveShipmentStockAtomic`, with a stable `postingId` and duplicate check.
- landed-cost application uses `InventoryDao.applyShipmentLandedCostAtomic`.
- v241/v242 reports state final receiving and landed-cost settlement are idempotent and operate on accepted quantities.

Relevant sources:
- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV2AppAdapters.kt`
- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsReceivingAdapters.kt`
- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV232SettlementAdapters.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt`

## 8. Sync

Invoice sync participant order:
- PUSH invoices
- PUSH invoice items
- PUSH payments
- DELETE legacy invoice deletions
- PULL invoices
- PULL invoice items
- PULL payments

Mechanism today:
- normal Verto invoice/payment sync is dirty-flag + table upsert/pull, not an invoice financial Outbox/Inbox protocol.
- invoice upsert conflicts on `id`.
- payment upsert conflicts on `id`.
- local dirty rows are protected from overwrite during pull.
- no `aggregateVersion` exists on the invoice/payment entities.
- no general invoice Inbox exists.

There **is** an `optimal_outbox` integration path that emits invoice/payment lifecycle events atomically with local invoice/payment transactions. It is an integration boundary for Optimal; it is not the authoritative transport used by Verto's regular invoice table sync.

Relevant sources:
- `feature/invoice/.../InvoiceSyncParticipant.kt`
- `data/network/.../SyncInvoiceHeaders.kt`
- `data/network/.../SyncInvoiceLines.kt`
- `data/network/.../SyncInvoicePayments.kt`
- `feature/integration/optimal/.../OptimalInvoiceIntegrationOutboxAdapter.kt`

## 9. Current transaction truth

| Write | Inside invoice/payment Room transaction today? | Notes |
|---|---:|---|
| invoice header | Yes | create/edit/void |
| invoice items | Yes | create/edit |
| payment created during invoice save | Yes | when applicable |
| standalone payment | Yes | local mode |
| cash movement | Yes | local mode, via DAO transaction nested in owner transaction |
| inventory movements | Yes | create/edit/void local flow |
| inventory item quantity | Yes | same Room DB |
| inventory buy/sell price update | Yes when invoked inside invoice owner transaction | uses `saveItem` before add stock |
| Optimal integration outbox | Yes | if active binding exists |
| general Verto invoice sync outbox | No | dirty flags/table sync instead |
| invoice audit | No | post-commit |
| payment audit | No | post-commit |
| notifications | No | post-commit, appropriate as side effect |
| sync scheduling | No | post-commit, appropriate as scheduler side effect |

## 10. Session-243 conclusion

There is no multi-database blocker. Sessions 244–251 can build the required atomic financial aggregate on the existing `AppDatabase`, but must replace the current money representation, lifecycle semantics, idempotency gaps, and general sync contract rather than assuming the current partial safeguards already satisfy the final invariants.
