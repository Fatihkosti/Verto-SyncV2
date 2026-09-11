# B06-V01 producer transaction and retry map

| Owner / aggregate | Capture point | Frozen content |
|---|---|---|
| INVOICE / PAYMENT / RETURN / VOID | `FinancialOutboxWriter`, called after optional integration/dependent writes while the feature owns the Room transaction | header, items, installments, payments, allocations, FX, return documents/lines/allocations, tombstones, effect references, applied base version |
| INVENTORY_MOVEMENT | `InventoryStockWriter`, after canonicalization and specialized outbox insert in the same transaction | camelCase movement DTO v2 and item/movement protected generations |
| INVENTORY_COST_REVISION | `InventoryStockWriter`, after cost outbox insert in the same transaction | camelCase Minor cost DTO v2 and item/revision protected generations |
| CLIENT_CREDIT | `PaymentAppAdapters`, after the source payment and credit are known in the producer transaction | `amountMinor`, mandatory `sourcePaymentId`, actor and time fields |
| EXPENSE / CASH_MOVEMENT | their repository/writer before transaction commit | complete current materialization using stored Minor values |
| CASH_RECONCILIATION | repository after session/denomination persistence and before commit | session plus sorted denomination records using stored Minor values |
| GOODS_RECEIPT | `PurchaseCycleCoordinator` after order/receipt/inventory effects | order + order lines + receipt + receipt lines + safe attachment metadata |
| PURCHASE_MATCH | same producer transaction as match persistence | match and match lines plus explicit empty sibling lists |
| PURCHASE_PAYMENT_OVERRIDE | same producer transaction as override persistence | complete override plus explicit empty sibling lists |

`UnifiedOutboxWriter`, `FinancialOutboxWriter`, and `SpecializedMutationCaptureV2` feed `FrozenMutationStore` in the producer transaction, recording the immutable intent, local generations, pending references, and original batch identity. `UnifiedFinancialOwner310Route.prepare` has no database dependency and can only parse `SyncOutboxEntity.payloadJson`; retry therefore cannot re-read newer business rows.

The static gate `tools/test_sync_b06_producers.py` verifies the production ordering and all stronger owner capture points. Runtime PostgreSQL/Room round-trip is deliberately reserved for B08; authoritative financial Room application is B09.

`FinancialOutboxWriterV2InstrumentedTest` ran on a Pixel_8 AVD (Android 17): one case proved domain rows, full snapshot, packet, two generations/references, sealed batch and member commit together; the second forced an exception and proved all nine affected tables rolled back to zero rows.
