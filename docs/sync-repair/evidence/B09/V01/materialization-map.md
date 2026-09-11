# B09 materialization, field and ownership map

Injection chain: the existing runtime injects `UnifiedSyncPullEngine`; its `UnifiedSyncChangeApplier` injects `UnifiedStrongerSyncChangeApplier`, which now injects `FinancialMaterializerV2(AppDatabase, SyncPendingProtection, FinancialEffectVerifierV2)`. The same app database instance/Room transaction encloses all writes. This is a source trace, not a successful Hilt build.

## Concrete source locations

### DTO validation
`data/sync/src/main/kotlin/com/verto/app/data/sync/pull/FinancialMaterializationContractV2.kt`
- `fun decode` — line(s) 16
- `fun validate` — line(s) 49, 189, 217
- `fun minorTotals` — line(s) 167
- `fun paymentsInDependencyOrder` — line(s) 154

### Transaction and authority
`data/sync/src/main/kotlin/com/verto/app/data/sync/pull/FinancialMaterializerV2.kt`
- `suspend fun materialize` — line(s) 45
- `suspend fun apply(` — line(s) 57
- `suspend fun completeBatch` — line(s) 178
- `suspend fun protect` — line(s) 196
- `suspend fun validateReferences` — line(s) 220
- `suspend fun readPersisted` — line(s) 270

### Owned effects
`data/sync/src/main/kotlin/com/verto/app/data/sync/pull/FinancialEffectVerifierV2.kt`
- `suspend fun verify` — line(s) 21

### Full pull path
`data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt`
- `class UnifiedSyncPullEngine` — line(s) 48
- `suspend fun commitPageAtomically` — line(s) 222
- `completeFinancialBatch` — line(s) 256
- `dao.markInboxApplied` — line(s) 258

### Hilt route
`data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt`
- `class UnifiedStrongerSyncChangeApplier` — line(s) 52
- `financialMaterializer.materialize` — line(s) 68

### Remote selective DAO
`data/database/src/main/kotlin/com/verto/app/data/local/dao/FinancialMaterializationDao.kt`
- `interface FinancialMaterializationDao` — line(s) 10
- `deleteRemoteInvoiceItem` — line(s) 112
- `remoteInvoiceItemHasProtectedReference` — line(s) 120

### Child pending protection
`data/sync/src/main/kotlin/com/verto/app/data/sync/ownership/SyncPendingProtection.kt`
- `suspend fun isProtected` — line(s) 88
- `FINANCIAL_CHILD_TYPES` — line(s) 105, 180

### Snapshot adapter
`data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt`
- `suspend fun materializeAndPrune` — line(s) 28
- `completeFinancialBatch` — line(s) 58

## Per-table mapping

| DTO | Business fields | Entity fields | Receive policy |
|---|---:|---:|---|
| InvoiceDtoV2 | 41 | 48 | ABORT insert / selective update |
| InvoiceItemDtoV2 | 21 | 31 | ABORT insert / selective update |
| InvoiceDueInstallmentDtoV2 | 8 | 8 | ABORT insert / selective update |
| PaymentDtoV2 | 24 | 26 | ABORT insert / exact immutable no-op or conflict |
| PaymentAllocationDtoV2 | 11 | 11 | ABORT insert / exact immutable no-op or conflict |
| RealizedFxEventDtoV2 | 13 | 13 | ABORT insert / exact immutable no-op or conflict |
| InvoiceReturnDocumentDtoV2 | 17 | 17 | ABORT insert / exact immutable no-op or conflict |
| InvoiceReturnLineDtoV2 | 13 | 13 | ABORT insert / exact immutable no-op or conflict |
| InvoiceReturnPaymentAllocationDtoV2 | 5 | 5 | ABORT insert / exact immutable no-op or conflict |

`FinancialEntityMappingsV2.kt` uses named assignments for all 153 business fields in both directions. Local-only values: imageUri preserved, invoiceNumberSearch derived by current normalizer, voided derived from lifecycleStatus, dirty false. Compatibility Double derives from its explicitly assigned LongMinor, never the reverse.

## Fact owners and hashes

| Fact | Existing owner | Verification, never production |
|---|---|---|
| PAYMENT | financial_outbox | Actual row ↔ snapshot DTO equality; exact existing B06 DTO serialization hash; writeId/id business identity |
| INVENTORY_MOVEMENT | inventory_stock_outbox | Actual row org/invoice/idempotencyKey and B06 six-field projection hash; no stock count replay |
| INVENTORY_COST_REVISION | inventory_cost_outbox | Existing scoped applied owner hash + existing row/idempotency key |
| CASH_MOVEMENT / CLIENT_CREDIT / COMMISSION_PAYMENT | sync_outbox | Existing applied owner hash + existing immutable row identity; credit party scope proof |

External owner absence/mismatch fails closed. B08/B12 must supply their own real accepted projection/authority; B09 does not create a fake hash or mutate those owner rows. Commission legacy totalAmount is not a newly invented Minor column.

## Group ordering and deferred boundaries

`AppDatabase.withTransaction`: validated complete server group → inbox insert + domain rows → all owner facts verified + readback → financial applied authority CAS → every group inbox member APPLIED → opaque page cursor CAS/checkpoint. Page-level rollback remains intentionally until B10. No independent receive queue or sync_inbox_group coordinator is implemented here.

Payment feed metadata must carry explicit paymentId/reversedPaymentId; no lookup by date, largest version or free-form snapshot name. The current frozen B06 packets are untouched. Server canonicalization and live activation are NOT established by this change.

Full source patch, golden fixture, temporary-model dump, current fingerprints and actual test commands are adjacent in this evidence directory.
