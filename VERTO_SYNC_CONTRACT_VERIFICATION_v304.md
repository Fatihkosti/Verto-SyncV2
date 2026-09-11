# VERTO — Session 304 Verification Report

**Final verdict:** `TOOL_ERROR`
**Static contract state:** `STATIC_ANDROID_CONTRACT_COMPLETE / SERVER_BASELINE_NOT_REPRODUCIBLE`
**Evidence state:** `STATIC_CONTRACT_COMPLETE / RETENTION_BUDGET_EVIDENCE_BLOCKED`

## Input & baseline
- Source: `Verto-v303.zip`
- SHA-256: `6fddd8d485cdbdbe7b7331f8b342739b0543ef7e4e99057f41bb4fbb099a5e6b`
- Archive entries: `1651`
- Production Kotlin baseline: `1153` — `05963e2a9817f6c2aeb803212f74f09c3783d5c6fc4beef45a009a06012aaa00`
- Sync-focused baseline: `67` — `410e5119396f99e988a917bcf1ca5e1f2ba3aa13656c230790a9735bbfd6c046`
- Room schema: `77`
- SQL baseline files: `32`

## Scope integrity
- Modified pre-existing Production Kotlin: **0 / 1153**
- Approved new Production Kotlin: **2**
- SQL changes: **0**
- Room/exported schema changes: **0**
- Gradle changes: **0**
- Feature Flags changes: **0**

## Current v303 sync baseline
- Participants: `cash, clients, educational_content, invoices, inventory, logistics-v2, optimal_outbox, organization`
- Known discrepancy: `V303-PARTICIPANT-KEY-DRIFT-001` — required `shipments`, actual `logistics-v2`; intentionally not fixed in 304.
- Realtime remains disabled and current full-sync trigger behavior is unchanged.
- WorkManager `ExistingWorkPolicy.KEEP` remains unchanged.
- Legacy timestamp/Preferences cursors and deletion queues are documented only; no runtime migration in 304.

## Unified contract
- Family/version: `verto-unified-sync` / `1`
- Registry aggregates: **34**
- Revisions are monotonic; gaps are allowed.
- Unified cursor is opaque/server-owned; timestamp cursor is forbidden.
- Filtered pull cannot advance the global cursor.
- Bootstrap requires a gap-free snapshot/baseline handoff.
- Transaction groups cannot be split across pull pages/Room commits.
- Realtime is hint-only.

### Aggregate registry
`BUDGET` | `CASH_MOVEMENT` | `CASH_RECONCILIATION` | `CASH_REGISTER` | `CATEGORY` | `CLIENT_CREDIT` | `COMMISSION_PAYMENT` | `COST_ALLOCATION` | `CUSTOMER_PROFILE` | `EDUCATIONAL_CONTENT` | `EXPENSE` | `GOODS_RECEIPT` | `INVENTORY_COST_REVISION` | `INVENTORY_ITEM` | `INVENTORY_MOVEMENT` | `INVENTORY_UNIT` | `INVOICE` | `ITEM_CATEGORY` | `NOTE` | `NOTIFICATION` | `OPTIMAL_FOLLOW_UP` | `OPTIMAL_MAINTENANCE` | `OPTIMAL_VEHICLE` | `ORGANIZATION_SETTINGS` | `PARTY_IDENTITY` | `PARTY_ROLE` | `PAYMENT` | `PRICE_LIST` | `PURCHASE_MATCH` | `PURCHASE_ORDER` | `PURCHASE_PAYMENT_OVERRIDE` | `REMINDER` | `SHIPMENT` | `SUPPLIER_PROFILE`

## Coverage
- Rows: **34**
- Uncovered aggregates: **0**
- Unknown producer/status: **0**
- Runtime RPC literals checked: **12**
- Inventory reconciliation RPC discovered by the gate and added explicitly to `INVENTORY_MOVEMENT` evidence.

## Retention & budgets
- Retention: `RETENTION_BUDGET_EVIDENCE_BLOCKED` — final numeric values are intentionally null; measurements were not supplied.
- Budgets: `RETENTION_BUDGET_EVIDENCE_BLOCKED` — final numeric values are intentionally null; measurements were not supplied.
- Current tombstone 90-day reference is baseline evidence only, not adopted as the unified final value.

## Server baseline
- Status: `SERVER_BASELINE_NOT_REPRODUCIBLE`.
- The parent plan references `schema(2).sql`, but exact server dump bytes were not provided in this execution input.
- No SQL/RPC/RLS was modified.

## Subgates
| Subgate | Exit | Status | Error kind |
|---|---:|---|---|
| `identity` | 0 | `PASS` | `NONE` |
| `contract` | 0 | `PASS` | `NONE` |
| `coverage` | 0 | `PASS` | `NONE` |
| `fixtures` | 0 | `PASS` | `NONE` |
| `gradle-contract` | 2 | `TOOL_ERROR` | `TOOL_ERROR` |
| `gradle-registry` | 2 | `TOOL_ERROR` | `TOOL_ERROR` |
| `integrity-after` | 0 | `PASS` | `NONE` |

Gradle tests could not start because Gradle 8.9 was not cached and network access is unavailable (`UnknownHostException: services.gradle.org`).
A separate `kotlinc` syntax/type sanity compile for the two new Production Kotlin files passed; it does not replace Gradle unit tests.

## Failure fixtures
- Explicit outcomes: **51**
- Passed: **51**
- Failed: **0**
- Minimum category counts satisfied: **True**

| ID | Category | Expected | Actual | Result |
|---|---|---|---|---|
| AT01 | attachments | `0:PASS` | `0:PASS` | **PASS** |
| AT02 | attachments | `1:FAIL_ATTACHMENT_POLICY` | `1:FAIL_ATTACHMENT_POLICY` | **PASS** |
| BT01 | bootstrap/transaction | `0:PASS` | `0:PASS` | **PASS** |
| BT02 | bootstrap/transaction | `1:FAIL_BOOTSTRAP_HANDOFF_POLICY` | `1:FAIL_BOOTSTRAP_HANDOFF_POLICY` | **PASS** |
| BT03 | bootstrap/transaction | `1:BOOTSTRAP_RESTART_REQUIRED` | `1:BOOTSTRAP_RESTART_REQUIRED` | **PASS** |
| BT04 | bootstrap/transaction | `1:BOOTSTRAP_RESTART_REQUIRED` | `1:BOOTSTRAP_RESTART_REQUIRED` | **PASS** |
| BT05 | bootstrap/transaction | `0:PASS` | `0:PASS` | **PASS** |
| BT06 | bootstrap/transaction | `1:INCOMPLETE_TRANSACTION_GROUP` | `1:INCOMPLETE_TRANSACTION_GROUP` | **PASS** |
| BT07 | bootstrap/transaction | `1:INCOMPLETE_TRANSACTION_GROUP` | `1:INCOMPLETE_TRANSACTION_GROUP` | **PASS** |
| BT08 | bootstrap/transaction | `0:PASS` | `0:PASS` | **PASS** |
| BT09 | bootstrap/transaction | `0:PASS` | `0:PASS` | **PASS** |
| BT10 | bootstrap/transaction | `0:PASS` | `0:PASS` | **PASS** |
| BT11 | bootstrap/transaction | `1:DEPENDENCY_ORDER_VIOLATION` | `1:DEPENDENCY_ORDER_VIOLATION` | **PASS** |
| BT12 | bootstrap/transaction | `1:FAIL_TRANSACTION_GROUP_POLICY` | `1:FAIL_TRANSACTION_GROUP_POLICY` | **PASS** |
| CV01 | coverage | `0:PASS` | `0:PASS` | **PASS** |
| CV02 | coverage | `1:FAIL_AGGREGATE_COVERAGE` | `1:FAIL_AGGREGATE_COVERAGE` | **PASS** |
| CV03 | coverage | `1:FAIL_AGGREGATE_COVERAGE` | `1:FAIL_AGGREGATE_COVERAGE` | **PASS** |
| CV04 | coverage | `1:FAIL_AGGREGATE_COVERAGE` | `1:FAIL_AGGREGATE_COVERAGE` | **PASS** |
| CV05 | coverage | `1:FAIL_AGGREGATE_COVERAGE` | `1:FAIL_AGGREGATE_COVERAGE` | **PASS** |
| CV06 | coverage | `1:FAIL_AGGREGATE_COVERAGE` | `1:FAIL_AGGREGATE_COVERAGE` | **PASS** |
| FP01 | financial policies | `1:FAIL_CONFLICT_POLICY` | `1:FAIL_CONFLICT_POLICY` | **PASS** |
| FP02 | financial policies | `1:FAIL_CONFLICT_POLICY` | `1:FAIL_CONFLICT_POLICY` | **PASS** |
| FP03 | financial policies | `1:FAIL_CONFLICT_POLICY` | `1:FAIL_CONFLICT_POLICY` | **PASS** |
| PA01 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA02 | payload/aggregate | `1:CONTRACT_UNSUPPORTED` | `1:CONTRACT_UNSUPPORTED` | **PASS** |
| PA03 | payload/aggregate | `1:CONTRACT_UNSUPPORTED` | `1:CONTRACT_UNSUPPORTED` | **PASS** |
| PA04 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA05 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA06 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA07 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA08 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA09 | payload/aggregate | `0:PASS` | `0:PASS` | **PASS** |
| PA10 | payload/aggregate | `1:CONTRACT_UNSUPPORTED` | `1:CONTRACT_UNSUPPORTED` | **PASS** |
| RC01 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC02 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC03 | revision/cursor | `1:INVALID_REVISION_ORDER` | `1:INVALID_REVISION_ORDER` | **PASS** |
| RC04 | revision/cursor | `1:VALIDATION` | `1:VALIDATION` | **PASS** |
| RC05 | revision/cursor | `1:VALIDATION` | `1:VALIDATION` | **PASS** |
| RC06 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC07 | revision/cursor | `1:DUPLICATE_REVISION_CONTENT_MISMATCH` | `1:DUPLICATE_REVISION_CONTENT_MISMATCH` | **PASS** |
| RC08 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC09 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC10 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC11 | revision/cursor | `0:PASS` | `0:PASS` | **PASS** |
| RC12 | revision/cursor | `1:FAIL_TIMESTAMP_CURSOR_POLICY` | `1:FAIL_TIMESTAMP_CURSOR_POLICY` | **PASS** |
| RC13 | revision/cursor | `1:FILTERED_GLOBAL_CURSOR_ADVANCE` | `1:FILTERED_GLOBAL_CURSOR_ADVANCE` | **PASS** |
| RC14 | revision/cursor | `1:SCOPE_MISMATCH` | `1:SCOPE_MISMATCH` | **PASS** |
| RC15 | revision/cursor | `1:CONTRACT_UNSUPPORTED` | `1:CONTRACT_UNSUPPORTED` | **PASS** |
| TE01 | tool errors | `2:TOOL_ERROR` | `2:TOOL_ERROR` | **PASS** |
| TE02 | tool errors | `2:TOOL_ERROR` | `2:TOOL_ERROR` | **PASS** |
| TE03 | tool errors | `2:TOOL_ERROR` | `2:TOOL_ERROR` | **PASS** |

## Determinism
- Static verifier: **PASS** — `422d1322c268a6fca600976a1f0bb9764a8f900dcc757581c8027e643c9e3d71`
- Fixture harness: **PASS** — `fc826e79525e86188bc3264066da90f62d7bcbae58b0aeefd977a147995e5e1a`

## 305 handoff
**Not authorized yet.** Session 305 must receive: exact current server dump identity, accepted retention/budget numeric decisions, and runnable targeted Gradle tests.

## Final
The static Session 304 contract/governance implementation is complete and integrity-safe, but this build is intentionally packaged as `sync-contract-static`, not `source-of-truth`.

