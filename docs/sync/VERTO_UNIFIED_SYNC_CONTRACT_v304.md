# VERTO Unified Sync Contract v304

**Contract family:** `verto-unified-sync`  
**Contract version:** `1`  
**Runtime activation:** none in Session 304.

## Non-negotiable invariants

- Server revision is authoritative ordering; numeric gaps are valid.
- Cursor is opaque and server-owned; device time and `updated_at` are forbidden cursor/order sources.
- Bootstrap must bind a consistent snapshot to its baseline cursor; snapshot then independent `max(revision)` is forbidden.
- A server transaction-group is never split across Pull pages or future Room commits.
- Filtered/targeted Pull never advances the global cursor unless it is proven full-stream coverage.
- `mutationId` is stable across retry/process death; same ID with different semantic content fails closed.
- Financial/ledger aggregates never use generic LWW.
- Realtime is hint-only; correctness must converge with Realtime permanently disabled.
- Attachments synchronize metadata/references/checksums only; binary bodies remain external.
- Reconciliation manifests detect divergence; they are not the primary change feed.

## Error taxonomy

`TRANSIENT`, `AUTH`, `VALIDATION`, `CONFLICT`, `CURSOR_EXPIRED`, `CONTRACT_UNSUPPORTED`.

## Aggregate registry

| Aggregate | Payload | Conflict | Delete | Attachment | Owner session |
|---|---:|---|---|---|---:|
| BUDGET | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| CASH_MOVEMENT | 1 | APPEND_ONLY_IDEMPOTENT | NO_CLIENT_DELETE | NONE | 310 |
| CASH_RECONCILIATION | 1 | SEMANTIC_COMMAND | NO_CLIENT_DELETE | NONE | 310 |
| CASH_REGISTER | 1 | SERVER_AUTHORITATIVE | NO_CLIENT_DELETE | NONE | 310 |
| CATEGORY | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| CLIENT_CREDIT | 1 | APPEND_ONLY_IDEMPOTENT | NO_CLIENT_DELETE | NONE | 310 |
| COMMISSION_PAYMENT | 1 | APPEND_ONLY_IDEMPOTENT | NO_CLIENT_DELETE | NONE | 310 |
| COST_ALLOCATION | 1 | IMMUTABLE_REVISION | NO_CLIENT_DELETE | NONE | 310 |
| CUSTOMER_PROFILE | 2 | BRIDGE_EXISTING_STRONGER_CONTRACT | TOMBSTONE | NONE | 307 |
| EDUCATIONAL_CONTENT | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| EXPENSE | 1 | SEMANTIC_COMMAND | VOID_OR_REVERSE | NONE | 310 |
| GOODS_RECEIPT | 1 | SEMANTIC_COMMAND | NO_CLIENT_DELETE | METADATA_ONLY_EXTERNAL_BINARY | 310 |
| INVENTORY_COST_REVISION | 1 | IMMUTABLE_REVISION | NO_CLIENT_DELETE | NONE | 310 |
| INVENTORY_ITEM | 1 | OPTIMISTIC_VERSION | ARCHIVE | NONE | 307 |
| INVENTORY_MOVEMENT | 1 | APPEND_ONLY_IDEMPOTENT | NO_CLIENT_DELETE | NONE | 310 |
| INVENTORY_UNIT | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| INVOICE | 1 | SEMANTIC_COMMAND | VOID_OR_REVERSE | NONE | 310 |
| ITEM_CATEGORY | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| NOTE | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| NOTIFICATION | 1 | SERVER_AUTHORITATIVE | SERVER_OWNED | NONE | 308 |
| OPTIMAL_FOLLOW_UP | 1 | BRIDGE_EXISTING_STRONGER_CONTRACT | NO_CLIENT_DELETE | NONE | 310 |
| OPTIMAL_MAINTENANCE | 1 | BRIDGE_EXISTING_STRONGER_CONTRACT | NO_CLIENT_DELETE | METADATA_ONLY_EXTERNAL_BINARY | 310 |
| OPTIMAL_VEHICLE | 1 | BRIDGE_EXISTING_STRONGER_CONTRACT | SERVER_OWNED | NONE | 310 |
| ORGANIZATION_SETTINGS | 1 | OPTIMISTIC_VERSION | NO_CLIENT_DELETE | NONE | 307 |
| PARTY_IDENTITY | 2 | BRIDGE_EXISTING_STRONGER_CONTRACT | TOMBSTONE | NONE | 307 |
| PARTY_ROLE | 2 | BRIDGE_EXISTING_STRONGER_CONTRACT | TOMBSTONE | NONE | 307 |
| PAYMENT | 1 | APPEND_ONLY_IDEMPOTENT | VOID_OR_REVERSE | NONE | 310 |
| PRICE_LIST | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| PURCHASE_MATCH | 1 | SEMANTIC_COMMAND | NO_CLIENT_DELETE | NONE | 310 |
| PURCHASE_ORDER | 1 | SERVER_STATE_MACHINE | CANCEL_STATE_TRANSITION | NONE | 307 |
| PURCHASE_PAYMENT_OVERRIDE | 1 | SEMANTIC_COMMAND | NO_CLIENT_DELETE | NONE | 310 |
| REMINDER | 1 | OPTIMISTIC_VERSION | VERSIONED_DELETE | NONE | 307 |
| SHIPMENT | 1 | SERVER_STATE_MACHINE | CANCEL_STATE_TRANSITION | METADATA_ONLY_EXTERNAL_BINARY | 307 |
| SUPPLIER_PROFILE | 2 | BRIDGE_EXISTING_STRONGER_CONTRACT | TOMBSTONE | NONE | 307 |

## Retention and budgets

Final numeric retention and runtime budgets are **not proven by supplied Session 304 evidence**. The existing 90-day tombstone target is recorded only as baseline evidence. The machine authority therefore marks these fields `EVIDENCE_BLOCKED`; no numbers were invented.

## Server baseline

`SERVER_BASELINE_NOT_REPRODUCIBLE`: no exact current server dump bytes were supplied to the executor. Plan-derived Optimal/change-log metadata is reference-only until rebound.

## Authority

If prose and machine data differ, `VERTO_UNIFIED_SYNC_CONTRACT_v304.json` plus Kotlin contract tests are authoritative.

