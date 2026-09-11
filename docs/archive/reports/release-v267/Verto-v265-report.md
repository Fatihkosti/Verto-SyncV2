# Verto v265 — Cost Ledger

Status: **IMPLEMENTED / STATIC PASS**

- Routed purchase, provisional receipt, approved landed-cost and reversal changes through canonical cost revisions.
- Added a dedicated idempotent cost outbox and atomic server RPC.
- Added an independent cost cursor so stock and cost streams can recover safely.

Validation: local and remote contract checks passed statically. Live server ordering/idempotency tests remain pending.
