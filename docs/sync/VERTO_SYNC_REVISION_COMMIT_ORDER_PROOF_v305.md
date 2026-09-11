# Verto Sync Revision Commit-Order Proof — v305

## Fixed algorithm

Every insert into `public.verto_sync_change_log` passes through `verto_sync_change_assign_revision`, whose trigger function `public.verto_assign_sync_change_revision()` executes:

```sql
pg_advisory_xact_lock(hashtextextended('verto-sync-revision:' || organization_id::text, 0));
nextval('public.verto_sync_change_revision_seq');
pg_current_xact_id()::text;
```

The advisory lock is transaction-scoped and is acquired **before** revision allocation. The key is deterministically derived from the organization ID. A hash collision can only serialize unrelated organizations; it cannot permit reordering within one organization.

## Same-organization safety

For transactions `T1` and `T2` touching the same organization:

1. `T1` acquires the organization advisory xact lock.
2. `T1` allocates revision `R1`.
3. `T2` blocks before allocating any revision.
4. `T1` commits or rolls back; only then is the lock released.
5. `T2` acquires the lock and allocates `R2 > R1`.

Therefore a higher same-organization revision cannot commit while a lower allocated revision for that organization remains unresolved. A Pull statement that advances a scope cursor beyond revision `R` cannot later observe a newly committed same-organization event with revision `<= R`.

## Cross-organization concurrency

Different organizations use different advisory keys and may allocate from the same global sequence concurrently. Their revisions can interleave. This only creates gaps inside each organization's visible stream. Cursors are scope-bound and Pull never waits for `previous + 1`, so cross-tenant gaps are harmless.

## Rollback

PostgreSQL sequences are non-transactional. If `T1` allocates revision `100` and rolls back, `100` remains consumed. The next committed event may be `103`. Pull uses `revision > decoded_cursor`, not gap detection, so rollback gaps do not stall delivery.

## Transaction groups

`transaction_id` is assigned server-side from `pg_current_xact_id()::text`. Pull groups visible rows by `organization_id + transaction_id`, computes group order by revision, and selects only whole groups. Client-provided transaction IDs are impossible through the public surface.

## Safe watermark

`page_high_watermark` is the maximum committed visible revision for the scope at the Pull statement snapshot. It does not use sequence `last_value`, wall clock, or `updated_at`. The organization advisory-lock invariant guarantees that no unresolved lower same-organization revision can later appear beneath a watermark that already contains a higher committed same-organization revision.

## Bootstrap boundary

`verto_begin_sync_bootstrap` acquires the **same** organization advisory xact lock before reading the committed baseline revision and before materializing rows into `verto_sync_bootstrap_rows`. Snapshot rows and `baseline_cursor` are committed in one server transaction. Delta begins exclusively from that baseline cursor.

## Required runtime proof

The SQL design establishes the ordering invariant statically. Delayed-commit, rollback, concurrent-writer, 10,000-change and page-boundary tests still require PostgreSQL execution. In the current tool environment `psql/postgres/initdb` are absent, so those database-runtime proofs are explicitly not claimed by this artifact.
