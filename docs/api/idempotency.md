---
status: canonical
scope: system
owner: "data:sync + data:network"
last_verified_against: 'B10-V01 Inbox/financial source review; runtime acceptance BLOCKED'
---
# Idempotency and Retry Contract

| Surface | Status | Evidence-backed rule |
|---|---|---|
| Unified mutation outbox | VERIFIED client-side identity | durable `sync_outbox`/mutation identity is retried as the same logical mutation; server definition exists for `verto_apply_sync_mutation` |
| Orchestration wake | VERIFIED | durable generation is committed before WorkManager wake; repeated wake is not a new business mutation by itself |
| Financial event sync | PARTIAL | financial outbox/event request identity exists; server definitions exist for apply/pull RPCs; final live behavior not re-run in v316 |
| Inventory command/cost sync | PARTIAL | dedicated inventory outboxes and batch RPCs exist with repository SQL definitions |
| Payment post/reversal | PARTIAL | client APIs carry `requestId`; repository SQL definitions for `post_payment_v2`/`reverse_payment_v2` are absent, so server dedup enforcement is NOT VERIFIED |
| Withdrawal approve | PARTIAL | `clientRequestId` is sent and caller reconciles row after timeout; server SQL exists for `approve_withdrawal` |
| Withdrawal complete | NOT VERIFIED | no request identity is visible in the call expression and repository definition is absent |
| Push token register | retry-safe behavior NOT VERIFIED server-side | client retries registration up to three times; SQL definition is absent |
| Read/pull RPCs | NOT APPLICABLE to mutation identity | retry must preserve cursor/scope validation and ordered application |
| Direct table upsert | PARTIAL/feature-specific | `onConflict` is sometimes explicit; this does not prove full business idempotency |
| Direct insert/update/delete | NOT VERIFIED by default | must not be blindly retried unless the owning feature supplies an identity/reconciliation rule |

## Financial V2 payment effect identity — B09-V02

For incoming financial snapshots, a fact is identified by `(owner, factType, factId)`.
For `PAYMENT`, `factId` is the original `paymentId` or the distinct reversal's ID. The
existing B06 `businessIdentity` is `writeId` (falling back to the payment ID when blank).
It is a correlation value: one invoice-void write can reverse several different payments.
Do not reject or collapse those different facts merely because their `writeId` matches.

Duplicate fact IDs still fail validation, and an existing payment with different DTO/hash
still conflicts. Non-payment owners retain unique business/idempotency identities.
No packet bytes, owner hash scheme, financial rule, or server contract changes here.

Source: `FinancialMaterializationContractV2.validate`, `FinancialEffectVerifierV2.verify`,
`FinancialSnapshotFactoryV2.capture`, and `InvoiceVoidCoordinator.createPaymentReversal`.
The focused Kotlin identity regression passed with an explicitly stubbed codec; actual
JSON/JVM and Room acceptance remain **NOT_RUN**, so G-B09 remains **BLOCKED**. See the
[B09-V02 evidence](../sync-repair/evidence/B09/V02/session-report.md).

## Definitions

- `VERIFIED`: source and repository evidence support the stated identity/order rule.
- `PARTIAL`: client identity/order exists but server enforcement or full retry behavior is not proven.
- `NOT VERIFIED`: no safe retry/idempotency guarantee is established.
- `NOT APPLICABLE`: read-only operation where mutation deduplication is irrelevant; cursor/revision correctness still applies.

## Evidence

- `data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt` — durable generation before wake.
- `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt`.
- `docs/sql/v249_financial_event_sync.sql` and `docs/sql/v262_inventory_atomic_sync.sql`.

## B10 durable receive versus business authority

The same `(scope_id, server_revision)` event is immutable. Complete group manifests and every member commit with the **received** cursor; receipt is not APPLIED and does not write applied versions. Verified identical replay consumes no second group reservation; different content under the same revision/transaction is rejected.

Each independent group has its own atomic domain/authority transaction. Local pending owners protect both the envelope root and derived DTO child writes. Dependencies, shared-key predecessors and unapplied financial events block affected projections; independent groups may apply without advancing the checkpoint over a waiting prefix. Actual local intent resolution and group application persist an Inbox generation before a WorkManager wake hint.

The applied checkpoint is the last covered, fully APPLIED group boundary. It is never `MAX(server_revision)` or a synthesized opaque cursor. Original frozen packet bytes remain the echo proof; canonical payload equality is checked separately and does not replace the packet's original hash.

`WAITING_LOCAL`, `WAITING_DEPENDENCY`, review and storage waits are not successful synchronization. Quota/disk rejection leaves receipt cursor and payload admission atomic. There is no old manifest-less transport fallback.

This is implemented client source, not verified production behavior. G-B09/G-B10, Android kill/restart, actual Room/KSP/JSON and B08/B20 server round trips remain unclosed. See [B10 report](../sync-repair/evidence/B10/V01/session-report.md) and [required wire contract](../sync-repair/evidence/B10/V01/wire-contract.md).
