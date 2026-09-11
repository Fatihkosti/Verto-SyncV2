# Invoice Schema & Sync Baseline — Verto v243

## 1. Local Room baseline

- Database: `AppDatabase` / file `verto_db`.
- Schema version: **61**.
- Migration catalog has a continuous path through `MIGRATION_60_61`.
- `fallbackToDestructiveMigration` is not used by `AppDatabase.getDatabase`.

### Financial entities currently relevant

#### `InvoiceEntity` / table `invoices`
Important columns:
- id: String PK
- invoiceNumber: Int
- clientId: String FK → clients, `CASCADE`
- category: SALE/PURCHASE
- totalAmount: Double
- status: CLOSED_CASH/CLOSED_CREDIT
- shipmentId
- purchase_scope: LOCAL/INTERNATIONAL
- voided: Boolean
- isDirty: Boolean

Current indexes:
- clientId
- createdAt
- invoiceNumberSearch
- purchase_scope

Missing for target architecture:
- durable invoice `writeId`
- organization-scoped local unique idempotency key
- external supplier invoice number + normalized unique key
- DRAFT/POSTED/VOID lifecycle field
- aggregateVersion
- transaction/functional currency fields
- exchange-rate snapshot/source/timestamp/direction
- functional recognition amount

#### `InvoiceItemEntity` / table `invoice_items`
- quantity: Int
- buyPrice/sellPrice/totalPrice/adjustedPurchasePrice: Double
- inventoryItemId: String (not enforced FK)
- no `unitCostAtSale`, revenue/cost/profit snapshots

#### `PaymentEntity` / table `payments`
- id: String PK
- invoiceId FK → invoices, CASCADE
- amount: Double
- paymentMethod
- paidAt
- reversedPaymentId nullable
- isDirty

Missing:
- explicit PaymentAllocation table
- currency/original amount
- functional cash amount
- FX rate snapshot/source/direction
- realized FX difference
- local unique operation tuple independent from payment PK

#### Cash
`CashRegisterEntity.balance` and cash movement amount/balances are `Double`.
Cash movements have `referenceId` but no stable typed `sourceType/sourceVersion` uniqueness contract.

#### Inventory
Inventory item buy/sell prices and movement unit price use `Double`.
Inventory movement is the main quantity trail; invoice purchase/sale paths currently write it.

#### Audit
Audit rows are stored in the same Room DB, so they can technically be moved into the owner transaction in 245/248.

#### Outbox
The project has `OptimalOutboxEntity` and an Optimal invoice/payment integration outbox with aggregate sequence/idempotency behavior. This is not the normal Verto invoice sync owner.

## 2. Server/client contract visible in repository

### Table sync DTOs
`InvoiceDto`, `InvoiceItemDto`, `PaymentDto` use `BigDecimal` serializers for remote decimal fields.
This precision is lost when mapped back to current local `Double` entities.

### Normal Verto sync today

#### Push invoices
- selects `isDirty = 1`
- Supabase upsert `invoices`
- `onConflict = "id"`
- marks rows clean after success

#### Pull invoices
- incremental by `updated_at`
- protects locally dirty invoice from overwrite
- applies remote core fields otherwise
- resolves invoice-number collision for new remote invoices by assigning a new local number
- no aggregate version conflict protocol

#### Push/pull invoice items
- table upsert by id
- remote pull avoids dirty parent invoices and removes stale local items for pulled invoices

#### Push/pull payments
- table upsert by id
- pull keeps local when local row is dirty
- remote payment may REPLACE local row

### Financial posting RPC
`post_payment_v2` receives:
- invoice id
- amount
- cash amount
- payment method
- note
- paidAt
- client request id
- original FX rate

`reverse_payment_v2` receives:
- payment id
- client request id

`FinancialPostingResult` includes `request_id` and `replayed`, indicating an idempotency-capable server API.
However, authoritative SQL definitions/unique constraints for these invoice/payment RPCs are **not present in this source package**, so server exactly-once guarantees cannot be certified from v242 alone.

## 3. Conflict policy currently observable

- Dirty local invoice/payment: keep local during pull.
- Clean invoice: remote fields overwrite selected local core fields.
- Invoice status contains a compatibility guard preventing local CLOSED_CREDIT → remote CLOSED_CASH downgrade.
- New invoice-number collision: local number is changed.
- Posted/void state has no aggregate version or event-only conflict rule.
- No `REQUIRES_REVIEW` financial conflict state exists.

## 4. Sync architecture gap for session 249

Current dirty-row sync is useful for offline operation but does not satisfy the approved financial sync contract because:
- no owner-transaction general invoice Outbox
- no general Inbox
- no immutable event sequence for invoice financial lifecycle
- no stored aggregateVersion on invoice/payment aggregate
- no schemaVersion on normal invoice/payment payloads
- no explicit server DDL in repository proving `(organization, operationType, writeId)` uniqueness

The Optimal outbox is a reusable pattern/evidence source, not a drop-in replacement unless ownership boundaries are deliberately redesigned.

## 5. Migration baseline for later sessions

- Start version for session-244+ migrations: **61** unless the incoming Source of Truth advances before that session.
- Every later session must rediscover the actual current version rather than hard-code 61.
- Existing v242 lineage reports 238–242 all state Room schema 61 unchanged.
