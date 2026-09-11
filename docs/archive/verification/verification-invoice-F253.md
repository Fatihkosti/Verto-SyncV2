# Verto Invoice Verification — F253

Date: 2026-08-19  
Session: **253 — Purchase Cycle: PO → GRN → Supplier Invoice → Payment**  
Source in: `Verto-v252-source-of-truth.zip`  
Source out: `Verto-v253-source-of-truth.zip`

## 1. Status

**SOURCE IMPLEMENTED / RELEASE GATE BLOCKED**

F253 source contracts, exact SQLite migration SQL, and local model verification pass. Production readiness is not declared because the Gradle/Room gate cannot run in this sandbox, Room schema 70 is not exported, the F253 Supabase SQL has not been deployed/live-tested, and Android/live-server integration tests remain pending.

## 2. Scope implemented

- Optional Purchase Order before a supplier invoice.
- Purchase Order lines, lifecycle, partial receiving, and documented partial close.
- Goods Receipt / GRN with received, accepted, and rejected quantities.
- Three-Way Match: ordered vs received vs invoiced.
- Quantity/price tolerance with explicit variance reason and approval when exceeded.
- Supplier external invoice reference uniqueness per organization + supplier, locally and server-side.
- Receipt attachments with owner/tenant integrity guards.
- Payment limited by quantity actually accepted and allocated to the invoice.
- Documented permission-gated override for payment beyond received quantity.
- International PO can become a shipment source; PO/invoice never posts international inventory.
- LOCAL linked PO invoices do not post inventory twice; GRN owns inventory recognition.
- FIFO immutable allocation from accepted GRN lines to matched invoice lines.
- Dynamic payable recomputation: later receipts unlock later payment without rewriting the original match.
- Purchase-cycle push/pull ordering added around invoice/payment sync.
- F252 purchase-return cost-source compatibility extended to GRN-backed quantities.

## 3. Core invariants

1. A GRN cannot accept more than the remaining ordered quantity.
2. A linked supplier invoice must reference a real same-scope PO; dangling PO links fail closed.
3. A matched invoice cannot silently absorb quantity/price variance outside tolerance.
4. Variance override requires permission, reason, and approver.
5. Payment for linked purchases cannot exceed the currently received payable amount unless a documented override exists for that exact payment identity.
6. GRN/match/allocation/override facts are immutable after insertion, except exact idempotent retry semantics where supported.
7. A receipt allocation cannot consume more than either the invoiced match quantity or accepted GRN quantity.
8. International PO/invoice does not enter inventory; final logistics receipt remains the inventory owner.
9. LOCAL linked purchase inventory is recognized by GRN, not again by supplier invoice.
10. Supplier external invoice reference duplicate is rejected per organization + supplier.

## 4. Root cause addressed

Before F253, purchase flow centered on invoices and inventory posting. There was no first-class immutable bridge between what was ordered, what was physically accepted, what the supplier invoiced, and what was allowed to be paid. That leaves over-receipt, silent variance, duplicate supplier references, and payment-before-receipt as cross-layer risks. F253 introduces one auditable purchase-cycle model and enforces the critical boundaries in Domain, Room/SQLite, sync, and server SQL.

## 5. Main source changes

New primary files:

- `data/database/src/main/kotlin/com/verto/app/data/local/entity/PurchaseCycleEntities.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/PurchaseCycleDao.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations69To70.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/RoomPurchaseCycleStore.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/PurchaseCycleModels.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/port/PurchaseCyclePorts.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/purchase/*`
- `app/src/main/kotlin/com/verto/app/feature/payment/bridge/RoomPurchaseReceiptPaymentGuardAdapter.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/dto/PurchaseCycleDtos.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt`
- `docs/sql/v253_purchase_cycle.sql`
- `tools/verify_v253_purchase_cycle.py`

Existing invoice, payment, inventory, Room, DI, DTO, sync, and financial-outbox files were integrated without unrelated UI changes.

## 6. Schema and contracts

- Room source schema version: **70**.
- Migration: **69 → 70**.
- New tables include Purchase Orders, PO lines, GRNs, GRN lines, attachments, invoice matches, match lines, receipt allocations, payment overrides, and PO-shipment sources.
- `invoices.purchase_order_id` links supplier invoices to PO.
- Existing normalized supplier external-reference contract is preserved and synchronized.
- Server SQL adds equivalent tenant-bound tables, constraints/guards, RLS policies, and security-definer pre/post purchase-cycle sync RPCs.
- Purchase-cycle sync ordering ensures PO/GRN precede invoices and match/allocation/override precede actual payment rows.

## 7. Verification run

Forward verification suite after the final F253 patch:

```text
V244_MIGRATION_SQL_PASS
V244_MONEY_CORE_PASS
V245_ATOMIC_INVARIANTS_PASS
V245_MIGRATION_SQL_PASS
V246_CURRENCY_TRUTH_PASS
V247_INVENTORY_COSTING_PASS
V248_INVOICE_LIFECYCLE_PASS
V249_FINANCIAL_SYNC_PASS
V250_REPORTS_RECONCILIATION_PASS
V251_CLIENT_CREDIT_MONEY_PASS
V251_LOCAL_CONTRACTS_PASS
scope=static-source+sqlite-model; room/instrumented/gradle-build=NOT_RUN
PASS v252 invoice returns verifier
PASS v253 purchase cycle verifier
```

F253 verifier additionally executes every exact triple-quoted 69→70 SQLite migration/trigger block against an in-memory SQLite base model and tests:

- duplicate supplier invoice reference rejection;
- partial receipt;
- over-receipt rejection;
- unaudited variance override rejection;
- three-way match facts;
- partial received payable;
- payment beyond received quantity rejection;
- later GRN dynamically unlocking the remaining payable;
- receipt allocation over-consumption rejection;
- explicit documented payment override;
- GRN-resolved inventory identity in later invoice matching.

## 8. Atomicity / retry / concurrency

- Invoice + Three-Way Match + initial-payment authorization remains inside the owner transaction.
- Initial payment ID and override key use the same deterministic identity, preventing retry mismatch.
- Receipt allocations use stable deterministic IDs and append-only facts.
- SQLite triggers provide local backstops even if a higher layer is bypassed.
- Server GRN/allocation guards use row locking (`FOR UPDATE`) for concurrent accepted/allocation bounds.
- Actual live multi-client Supabase concurrency is still pending deployment testing.

## 9. Build gate

Attempted:

```bash
./gradlew :feature:invoice:compileDebugKotlin :feature:payment:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon --console=plain
```

Result: **BLOCKED BY ENVIRONMENT**.

The wrapper tries to download `gradle-8.9-bin.zip` from `services.gradle.org` and fails with `UnknownHostException`. Therefore Kotlin/KSP/Room compilation was not reached in this sandbox.

## 10. Preserved prior behavior

- Legacy unlinked LOCAL purchase invoices retain their existing direct inventory path.
- Linked LOCAL PO invoices use GRN as inventory owner to prevent duplicate stock posting.
- INTERNATIONAL purchase invoices remain non-inventory-posting until shipment receipt.
- Last Purchase Price policy remains the Verto policy established in earlier sessions; F253 does not replace it with weighted average.
- F252 purchase returns can trace GRN-backed invoice quantity/cost ownership.
- F249 sync ordering contracts continue to pass.

## 11. Remaining release risks / required external checks

1. Gradle 8.9 distribution must be available on the build machine; run full compilation/tests.
2. Exported Room schemas are still missing for `56,57,58,59,62,63,64,65,66,67`, and new `69.json` / `70.json` must be generated by the real Room/KSP build. Do not fabricate them.
3. Deploy `docs/sql/v253_purchase_cycle.sql` to the target Supabase environment and validate syntax/migration there.
4. Run Room migration/instrumented tests on Android/emulator.
5. Run live-server retry/duplicate/concurrency tests with multiple clients.
6. Run end-to-end LOCAL and INTERNATIONAL purchase-cycle scenarios, including shipment final receipt.

## 12. Acceptance decision

- Duplicate supplier invoice reference rejected: **PASS — source/SQLite/server contract**.
- Partial receiving leaves remainder open: **PASS — source/SQLite model**.
- Quantity/price variance visible and never silently adjusted: **PASS — source/SQLite/server contract**.
- International PO/invoice does not enter inventory: **PASS — source contract**.
- Payment for unreceived quantity blocked unless documented override: **PASS — source/SQLite/server contract**.
- Regression gates 244–252 remain green: **PASS**.
- Production release gate: **BLOCKED** until Gradle/Room + Supabase + integration/E2E checks complete.

**Decision:** F253 is implemented in the source and passes every verification executable in this environment. It is suitable as the next Source of Truth, but not yet a production-release declaration.
