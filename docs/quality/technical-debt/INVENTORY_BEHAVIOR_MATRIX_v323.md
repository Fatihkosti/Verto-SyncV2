# Inventory Behavior Characterization — v323

The split is source-only: Room schema remains 81, SQL text remains unchanged, and the transaction owner remains the materialized InventoryDao.

| Operation family | Owner after split | Transaction evidence |
|---|---|---|
| Catalog and lifecycle | CatalogRead / ItemLifecycle | existing Room DAO tests |
| Opening, sale, purchase stock | StockCore / PurchasePosting | InventoryBaselineF256Test, InvoiceFinancialDb251Test |
| Sales return and purchase return | SalesReturn / PurchaseReturn | inventory return-flow tests |
| Shipment receipt, landed cost, reversal | ShipmentReceipt / Shipment | shipment posting tests |
| Invoice movement reversal | InvoiceOperations | invoice lifecycle tests |
| Pull movement and cost revision | Sync | v308-v314 sync verification |
| Canonical movement and cost idempotency | Cost / Movement | inventory ledger contract tests |
| Cursor, outbox, conflict, reconciliation reads | Sync / Cost / Catalog | sync and reconciliation verification |
| Backup/reset and unit helpers | Maintenance / StockTarget | backup and unit tests |

All 25 pre-existing transaction identities are mapped to the same atomic operation owner. No transaction was split into independent DAO calls.
