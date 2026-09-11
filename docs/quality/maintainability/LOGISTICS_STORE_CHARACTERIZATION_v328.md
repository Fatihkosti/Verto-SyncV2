---
status: supporting
scope: feature
owner: "maintainability-testability"
last_verified_against: v328
---
# Logistics Store Characterization — v328

## Purpose

Freeze the observable persistence/outbox boundary of `RoomLogisticsV2ShipmentStoreAdapter` for Session 329. Session 328 does not decompose or change this production file.

## Characterization matrix

| Boundary | v327/v328 behavior to preserve | Transaction/outbox evidence | Session 329 contract-test target |
|---|---|---|---|
| Transaction capture | Mutating adapter commands use `captured(...)`; its body and shipment mutation enqueue execute inside one `database.withTransaction`. | `captured` wraps `block()` then `enqueueShipmentMutation(...)` in the same Room transaction. | Force domain write failure and outbox failure separately; assert atomic rollback/no partial state. |
| Outbox capture | Every `captured` shipment mutation emits a unified outbox shipment mutation using organization, shipment, command, semantic key and operation type. | Central `enqueueShipmentMutation(...)` is invoked after the mutation body but before transaction commit. | Assert one durable mutation per successful command and none after rollback. |
| Create shipment | `createShipment` delegates to core creation through `captured(..., "CREATE", ...)`. | Core write and CREATE outbox capture share the `captured` transaction. | Assert shipment/event creation and CREATE outbox atomicity/idempotent semantic key. |
| State transition | `saveShipmentState` delegates to journey state persistence through `captured(...)`. | State/event change and outbox capture are transaction-coupled. | Assert valid transition persists together with event/outbox; invalid transition leaves all unchanged. |
| Document write | `saveDocument` runs document persistence inside `captured`, then enqueues attachment intent before shipment mutation capture commits. | Document row, attachment intent and shipment mutation are enclosed by the same outer Room transaction. | Assert all three commit/rollback as one unit. |
| Document delete | `deleteDocument` executes through `captured(..., "DELETE_DOCUMENT", ...)`. | Delete result and shipment mutation capture share one transaction. | Assert successful delete emits capture; failed delete/outbox does not partially commit. |
| Cost write | `saveCost` delegates to cost adapter through `captured(..., "SAVE_COST", ...)`. | Cost persistence and mutation capture share outer transaction; nested Room transactions remain transactionally joined. | Assert cost/event/outbox atomicity and no duplicate side effects. |
| Payment write | `savePayment` delegates to core payment persistence through `captured(..., "SAVE_PAYMENT", ...)`; core validates shipment/cost and writes payment/event transactionally. | Inner `database.withTransaction` participates in outer `captured` transaction; outbox capture occurs before outer commit. | Assert missing shipment/cost rejects all writes; successful payment/event/outbox commit atomically. |
| Receiving | `saveReceivingBatch` delegates to receiving adapter through `captured(..., "SAVE_RECEIVING_BATCH", ...)`; shortage/recovery mutations use the same capture pattern. | Receiving state and shipment mutation capture are coupled by outer transaction. | Assert accepted/shortage state, event and outbox rollback together on failure. |
| Route template write | `upsertRouteTemplate` executes route-template adapter write through `captured(..., "UPSERT_ROUTE_TEMPLATE", ...)`. | Route-template adapter uses Room transaction and outer capture adds durable shipment-style mutation in same transaction. | Assert template update plus outbox capture atomicity and stable semantic key. |

## Frozen invariants for Session 329

1. Domain persistence must not commit without its required durable outbox/attachment intent.
2. Outbox/attachment failure must roll back the associated domain mutation.
3. Existing semantic keys and operation types are compatibility surface.
4. Nested Room transactions must preserve the outer atomic boundary.
5. Session 329 may add contract/negative tests; broad Logistics decomposition remains a separate change.

## v328 change statement

`LogisticsShipmentStoreAdapters.kt` remains byte-for-byte unchanged from the v327 input. Direct `RoomLogisticsV2ShipmentStoreAdapter` tests remain a Session 329 hardening target.
