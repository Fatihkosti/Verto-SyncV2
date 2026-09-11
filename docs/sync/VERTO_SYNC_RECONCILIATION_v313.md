# Verto Sync Reconciliation — v313

Reconciliation is `ANTI_ENTROPY_ONLY_NOT_PRIMARY_FEED`. It consumes the v305 manifest RPC and compares it to the retained server-base bootstrap shadow using canonical aggregate-id ordering and PostgreSQL-compatible JSONB text hashing.

When any local outbox/attachment intent is pending, status is `DEFERRED_PENDING_LOCAL_MUTATIONS`. Equal manifests produce `CONVERGED`. A mismatch never advances or reconstructs the global cursor. v313 conservatively declares scoped repair ineligible; mismatch persists `RECOVERY_REQUIRED` and routes to the same safe full bootstrap.

Manifest scope, aggregate, partition, row count, digest, and server-provided manifest revision are validated/observed. Revision remains diagnostic only.
