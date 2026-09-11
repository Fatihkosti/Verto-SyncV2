---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v335
---
# Session 335 Sync Authority

## Baseline and override

Input is the exact v334 ZIP with SHA-256 `d8169f0e420a19c2fe16db528cae68733db9114e6815dc75c22e9e30be7dc423`.
Session 334 recorded `handoff335Authorized=false` because Gradle/Room runtime verification was blocked by the execution environment. The user explicitly authorized proceeding. This is recorded as `USER_OVERRIDE`; it does **not** change the predecessor result to PASS.

## V2 finance authority

When `SyncRolloutPolicy` makes `EXPENSE`, `CASH_MOVEMENT`, and `CASH_REGISTER` V2-owned:

- `CASH_REGISTER`: server-authoritative read model. No V2 client balance snapshot is pushed.
- `CASH_MOVEMENT`: append-only durable `sync_outbox` command. Push materializes exactly one movement by ID; it does not scan cash history.
- `EXPENSE`: owner310 stronger route materializes exactly one current expense by ID.
- Legacy expense/cash push/delete/pull steps are filtered from `CashSyncParticipant` for V2-owned finance.
- `V2_PAUSED_SAFE` does not silently reactivate Legacy financial writers.

Before V2 finance ownership, the pre-existing Legacy participant remains as rollout compatibility. This is not dual ownership: the ownership predicate selects one path.

## Cash movement mutation contract

`CashMovementSyncWriter` records the local cash effect and durable Outbox command in one Room transaction. It derives the organization from the trusted session, uses exact minor units, and derives movement identity deterministically from organization + `writeId`.

The Outbox payload contains movement/source identity and `amountMinor`; it contains no authoritative `balanceBefore`, `balanceAfter`, or floating-point cash amount. Reverse business effects remain new append-only movements, not mutation of old movements.

## Server authority

The existing `verto_apply_cash_movement_v310` remains the cash write authority: it uses write identity, row locking, minor-unit delta application, immutable movement creation, and secondary cash-register update.

The v310 expense command could not represent a legitimate active expense update with changed materialization. Session 335 therefore adds a new append-only SQL migration that replaces only the expense command implementation while preserving VOID/non-revival semantics and the outer receipt-ledger idempotency contract.

## Runtime truth

Static implementation and static/model evidence pass. Server deployment, concurrent multi-device execution, Android migration instrumentation, Gradle tests, lint, detekt, and assemble remain `BLOCKED_ENVIRONMENT`; production cutover is therefore `NOT_AUTHORIZED`.
