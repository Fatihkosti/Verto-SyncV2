# Verto sync repair implementation map

This map is current through `B13-V01` local implementation. The financial materializer, durable Inbox, explicit conflict-review path, mutable/versioned expense path, and sealed bootstrap protection path are wired in production source, but G-B09/G-B10/G-B11/G-B12/G-B13 validation is BLOCKED. Earlier verification cells are historical evidence, not current full-build/Room PASS.

| Contract area | Production implementation | Verification |
|---|---|---|
| DTO v2, explicit null/default serialization, validation | `data/network/src/main/kotlin/com/verto/app/data/sync/FinancialSyncContractV2.kt` | `FinancialSyncContractV2Test`; `tools/test_sync_contract_v2_schema.py` |
| Complete financial aggregate and semantic hash projection | `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialSnapshotFactoryV2.kt`; `FinancialOutboxWriter.kt` | fixed financial wire SHA-256 `8869bb431ede4f7455eb83e0d101863022bcf220f49bc1ed6cdbbb024961f2b7`; item-only-change test |
| Frozen packet, generation, references, batch membership | `data/sync/src/main/kotlin/com/verto/app/data/sync/FrozenMutationStore.kt`; `UnifiedOutboxWriter.kt`; `SpecializedMutationCaptureV2.kt` | B05 frozen-store tests plus B06 producer call map |
| Financial producer transaction end | `DefaultInvoiceAtomicPersistenceCoordinator.kt`; payment atomic coordinator; return coordinator; `FinancialOutboxWriter.kt` | `FinancialOutboxWriterV2InstrumentedTest` commit/rollback 2/2 on Room; producer-order gate |
| Movement and cost DTO mapping | `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedStrongerSourceFactory.kt`; `InventoryStockWriter.kt` | `FinancialSyncContractV2Test`; whole-app compilation |
| CLIENT_CREDIT immutable source payment reference | `PaymentModels.kt`; `BulkPaymentCoordinator.kt`; `PaymentAppAdapters.kt` | `FinancialSyncContractV2Test`; payment unit suite |
| Frozen owner310 materialization | expense, cash movement, cash reconciliation and purchase-cycle producers; `UnifiedFinancialOwner310Route.kt` | `UnifiedFinancialOwner310RouteV2Test`; schema gate |
| Retry immutability | `UnifiedFinancialOwner310Route.prepare` accepts only the persisted outbox row and registry; no database dependency | `UnifiedFinancialOwner310RouteV2Test` |

The PostgreSQL application, v2 remote round-trip and two-device scenarios remain owned by B07/B08/B20. B09 source implementation is present below; actual Room/Hilt/Gradle execution remains unverified.

## B13 sealed bootstrap and protected-local promotion boundary

| Requirement | Actual implementation | Evidence / limitation |
|---|---|---|
| Durable sealed staging | `SyncRecoveryEntities.kt`; `AppDatabaseMigrations100To101.kt`; `BootstrapSealPolicy.kt`; `UnifiedSyncRecoveryEngine.kt` | count/digest/exact 35-type coverage/high-watermark/delta-token must verify before `STAGED_VERIFIED`; static/native SQLite PASS; Room NOT_RUN |
| Protected intent/content seal | `RecoveryProtectionManifest.kt`; `SyncPendingProtection.kt`; frozen generation/reference tables | hashes unresolved owner rows, attachments, frozen packets/batches, pending references and producer-captured business content hashes; actual Room before/after T33 NOT_RUN |
| Safe promotion | `UnifiedSyncSnapshotApplier.kt` | rechecks protection per row inside cutover; protected remote rows become `WAITING_LOCAL`; unprotected rows apply remotely; no absence prune |
| Tombstones/version authority | `UnifiedSyncSnapshotApplier.kt`; `UnifiedRemoteMaterialization.kt` | explicit tombstone policy + version required for tombstones; non-financial applied version is recorded only after materialization in same transaction |
| M03 coexistence | `SyncManager.kt`; `SyncManagerPorts.kt` | migration-review evidence blocks promotion/pull but permits staging and one independent prepared push; JVM tests supplied, Gradle NOT_RUN |
| Server seal | `UnifiedSyncBootstrapWire.kt`; `SupabaseUnifiedSyncBootstrapRemote` | client fails closed when seal fields are absent; checked-in B08 SQL has not implemented them, so live promotion/T18/T31–T34 are BLOCKED |

B13 is locally implemented but not accepted. `docs/sync-repair/evidence/B13/V01/` is authoritative for the 48 static checks, native SQLite precursor, regression results, and environment/server blockers.

## B09 implementation and current verification boundary

| Contract area | Actual source | Current evidence |
|---|---|---|
| Exact DTO shape, required nullable/default fields, strict enum/rate/version/ref rules | `data/sync/.../pull/FinancialMaterializationContractV2.kt` | 12 JVM + 33 Room methods supplied; NOT_RUN. Selected Kotlin signature smoke only. |
| Nine tables / 153 business fields; Minor-to-compatibility one-way mapping | `data/network/.../FinancialEntityMappingsV2.kt`; producer `FinancialSnapshotFactoryV2.kt` | 347 actual Kotlin mapping assertions PASS; no JSON runtime in smoke. |
| Selective insertion/update, explicit child deletion, protected references | `data/database/.../dao/FinancialMaterializationDao.kt`; inherited by `InvoiceDao` | 25 SQL statements prepared on real schema98; Python-model rollback/roundtrip checks; actual Room tests NOT_RUN. |
| Financial transaction / authority / cross-scope checks | `FinancialMaterializerV2.kt`; `SyncPendingProtection.kt` | Source and schema checks; actual Room failure injection and child protection tests supplied. |
| No duplicate owned cash/stock/credit/commission effects | `FinancialEffectVerifierV2.kt` | PAYMENT/movement use existing B06 hashes; other owner facts require already-applied owner hash. Missing authority defers; no fabricated owner hashes. |
| Real Hilt/pull/transaction path | Pull Engine → ChangeApplier → StrongerApplier → FinancialMaterializerV2 → DAO; inject EffectVerifier/PendingProtection | Source trace, not a successful Hilt build. Complete group business → verify effects/readback → applied financial version → every inbox APPLIED → page cursor CAS, same Room transaction. |
| Snapshot cutover reuse | `recovery/UnifiedSyncSnapshotApplier.kt` | Financial baseline must be actual positive server baseline; no synthetic financial version/revision; full B13 bootstrap/pruning remains deferred. |

See `docs/sync-repair/evidence/B09/V01/materialization-map.md` for full paths, signature trace and owner boundaries. B09 verification remains blocked; B10 and B11 now also contain local implementation but do not inherit a runtime PASS.

## B09-V02 identity correction

`FinancialMaterializationContractV2.validate` now distinguishes immutable payment fact ID
from shared write correlation. It permits distinct reversals emitted by one invoice-void
write without changing B06 DTOs/hashes or frozen packets. Duplicate fact IDs and non-payment
business identities still fail. Existing immutable payment content is still compared by the
unchanged materializer/effect verifier.

Evidence: `docs/sync-repair/evidence/B09/V02/shared-write-before.log` reproduces the prior
false conflict in the real Kotlin semantic validator with a stubbed codec. The after-run
passes three identity assertions and 347 mapping/Money assertions. The 612 source/SQLite
model checks pass separately. These are not JSON/Room/KSP/Hilt execution. Added 3 JVM and
2 Room regression methods; totals 12/33 remain NOT_RUN. G-B09 is BLOCKED; later B10/B11 local implementation does not close that predecessor gate. Full detail: `docs/sync-repair/evidence/B09/V02/session-report.md`.

## B10 durable Inbox implementation and validation boundary

| Requirement | Actual implementation | Evidence / limitation |
|---|---|---|
| Receive manifest/scope/hash/count/order and cursor CAS | `SyncInboxProtocolV2`, `DurableInboxPageValidator`, `DurableInboxApplyCoordinator.receive`, `DurableSyncInboxDao.advanceReceivedCursor` | 12 validator JVM methods supplied; real JSON NOT_RUN; 15 native SQLite tests include actual SQL |
| Per-group atomic business apply, protection/dependencies | `DurableInboxApplyCoordinator.applyGroup`; `SyncPendingProtection`; `DurableInboxTouchedKeys`; existing B09 materializer/effect verifier | 14 Inbox Room +34 financial Room methods supplied, NOT_RUN |
| Covered applied prefix, independent X/Y, causal late origin | `advanceCoveredCheckpoint`; `missingInboxDependencies`; recursive earlier-touch query; financial late-original regression | SQLite/Pure policy checks pass within stated limits; device test absent |
| Durable intent-resolution/apply wake | `SyncInboxApplyRequestEntity`, `SyncPendingProtection.releaseAfterTerminal`, `DurableInboxWakeObserver`, `SyncManagerPorts`, `SyncWorker.enqueueInboxContinuation` | production scheduling source wired; Android/Hilt integration NOT_RUN |
| First group1001, canonical2MiB and scope64MiB+2MiB | `DurableInboxPolicy`, `InboxStorageProbe`, receive/drain boundaries | 12 actual policy methods executed with assertion shim; Android disk/group boundary NOT_RUN |
| No premature sync success / destructive reset | `SyncManager`, `UnifiedSyncPullEngine`, `SyncHealthSnapshot` | WAITING_INBOX/REQUIRES_REVIEW and non-APPLIED health count; full manager/worker suite NOT_RUN |
| Schema98→99 | `AppDatabaseMigrations98To99.kt`, `MigrationCatalog`, three metadata entities + actual DAO | 20 migration statements executed in native SQLite; generated schema99/Room validation NOT_RUN |
| B08 server wire binding | `SupabaseUnifiedSyncPullRemote` → `verto_pull_sync_changes_v2` | required exact interface in B10 evidence/wire-contract.md; no deployment/existence assertion |

The three B10 tables store group dependencies/touched keys/apply scheduling metadata; `sync_inbox` remains the single payload authority. The adapter does not turn a receipt into an APPLIED fact. Worker scheduling still uses the existing authenticated, network-constrained worker; network-free coordinator transactions do not claim independent offline worker availability. B08/B20 SQL acceptance remains unimplemented/unverified.


## B11 explicit conflict-review implementation and validation boundary

| Requirement | Actual implementation | Evidence / limitation |
|---|---|---|
| Persist both sides and traceable differences | `UnifiedSyncConflictEngine`; `SyncConflictReviewEvidenceEntity`; `SyncConflictReviewService`; `NavigationDrawerContent` | full local/remote JSON + SHA-256 stored; UI redacts secrets before diff/preview; Gradle/Compose runtime NOT_RUN |
| Explicit authorized server choice | `SyncConflictReviewService.acceptServer`; `SessionSyncConflictDecisionAuthorizer` | records append-only audit and `SUPERSEDED_WITH_PROOF`, never ACK; preserves newer local intent; runtime permission flow NOT_RUN |
| Explicit resend on displayed remote version | `SyncConflictReviewService.resendLocal`; `FrozenMutationStore.captureUnified(... useUnresolvedPredecessor=false)` | new immutable mutation uses exact displayed `baseVersion`, carries `supersedes_mutation_id`; old waits for proof |
| Replacement proof / later local ordering | `FrozenMutationStore.finalizeReplacementProofChain`; `resolvedPredecessorServerVersion`; `UnifiedSyncPushEngine.eligibility` | replacement may pass only its superseded parent; ordinary later intent remains blocked until proof, then chains to actual acknowledged version |
| Immutable and unknown-outcome protection | `UnifiedSyncConflictPolicy`; `SyncConflictResolutionPolicy`; conflict UI | non-`OPTIMISTIC_VERSION` => `DOMAIN_CORRECTION_REQUIRED`; unknown outcome disables decision; no automatic server-wins/LWW/amount merge |
| Schema/integrity | `AppDatabaseMigrations99To100`; `MigrationCatalog`; `AppDatabase` open callback | outbox proof states + conflict evidence/audit + supersedes relation; append-only/frozen guards; native SQLite probe PASS, Room schema100 export NOT_RUN |

B11 host probes PASS only for static source invariants and native SQLite DDL behavior. G-B10/G-B11 and T30 remain blocked until Gradle/Room/Android plus real receipt/echo scenarios run.

## B12 mutable/versioned expense implementation and validation boundary

| Requirement | Actual implementation | Evidence / limitation |
|---|---|---|
| Stable expense identity and versioned history | `ExpenseRepository`; `ExpenseRevisionContractB12`; `SyncRepairV2Dao`; `AppDatabaseB12ExpenseGuards` | same `expenseId`; before/after hashes + previous/server version + writeId + actor/time; static/native SQLite PASS; actual Room NOT_RUN |
| Frozen producer intent and cash batch | `ExpenseRepository`; `CashRegisterManager.recordExpenseDeltaB12`; `CashMovementSyncWriter` | revision intent + `capturedBaseVersion` + cash delta/reference frozen in one Room transaction and shared batch/dependency; Gradle/Room runtime NOT_RUN |
| Exact cash formula | `expenseEffectiveMinorB12`; `expenseCashDeltaMinorB12` | T21 arithmetic precursor PASS: note 0, 10000→15000 -5000, VOID +15000; full T21 app/Room/SQL NOT_RUN |
| Mutable remote apply without duplicate cash | `UnifiedStrongerSyncChangeApplier` EXPENSE path | `amountMinor` authority; validates revision/delta/hashes; records history; never creates receive-side cash effect; integration NOT_RUN |
| Wire contract | `FinancialSyncContractV2.kt`; `SYNC_CONTRACT_V2.schema.json` | B12 revision intent/revision DTOs; schema gate PASS with 42 defs |
| Server authority/history/group validation | `supabase/migrations/20260911001500_b12_expense_versioned_history.sql` | RLS/revoked direct access, append-only history, version/hash/cash validation, B12 stronger adapter; PostgreSQL execution NOT_RUN |
| Atomic non-zero cash boundary | B12 server adapter + future B07 batch | non-zero cash intentionally returns `B12_EXPENSE_BATCH_REQUIRED` before writes until B07 atomic expense+cash batching is available/proven; G-B12/R08 remain BLOCKED |

B12 code is implemented locally, not accepted. `docs/sync-repair/evidence/B12/V01/` records the 22 static checks, native SQLite contract, schema/pure-Kotlin probes, Gradle wrapper blocker and exact missing Room/PostgreSQL acceptance work. No live SQL or server deployment was performed.

