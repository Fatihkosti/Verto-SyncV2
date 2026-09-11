# B06-V01 ownership-matrix semantic coverage delta

The authoritative 35-row owner assignment remains `docs/sync-repair/evidence/B04/V01/SYNC_OWNERSHIP_MATRIX.csv`. B06 does not change owners; it expands the protected semantic keys and serializer evidence as follows:

| Aggregate | B06 protected semantic coverage | B06 serializer / producer |
|---|---|---|
| INVOICE / PAYMENT | invoice header; every item/installment/payment/allocation/FX/return row; explicit tombstones; referenced inventory/payment effects | `FinancialAggregateSnapshotV2`; `FinancialSnapshotFactoryV2`; `FinancialOutboxWriter` |
| CLIENT_CREDIT | credit identity, client, `amountMinor`, source payment, actor/time | `ClientCreditDtoV2`; `PaymentAppAdapters` |
| INVENTORY_MOVEMENT | movement and inventory item identities, signed base quantity, Minor unit price, source/idempotency/reversal facts | `InventoryMovementDtoV2`; `InventoryStockWriter` |
| INVENTORY_COST_REVISION | revision and item identities, all recorded Minor cost fields, sequence/idempotency/reversal facts | `InventoryCostRevisionDtoV2`; `InventoryStockWriter` |
| EXPENSE | complete current materialization and immutable Minor amount | `ExpenseDtoV2`; `ExpenseRepository` |
| CASH_MOVEMENT | movement and register facts with stored Minor balances | `CashMovementDtoV2`; `CashMovementSyncWriter` |
| CASH_RECONCILIATION | complete session and every denomination row with stored Minor values | `CashReconciliationDtoV2`; `CashReconciliationRepository` |
| GOODS_RECEIPT | purchase order/lines, receipt/lines, safe attachment metadata | `PurchaseRequestDtoV2`; `PurchaseCycleCoordinator` |
| PURCHASE_MATCH | match and lines/allocations | `PurchaseRequestDtoV2`; `PurchaseCycleCoordinator` |
| PURCHASE_PAYMENT_OVERRIDE | override and referenced invoice/payment request | `PurchaseRequestDtoV2`; `PurchaseCycleCoordinator` |

No owner or terminal predicate was changed in B06.
