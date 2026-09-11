# Schema 97 and invariant evidence

## Schema transition

- Before: `AppDatabase` version 96, schema hash `70a3a996f77623abc2b74155aca2d06b3a74148cc89e6fb33da5d46ec1fff748`.
- After: `AppDatabase` version 97, schema hash `f0966a1a27fc6b102cd9f4482181e9986d6e89a44b3dca90b937c452b3e2cab4`.
- Migration is additive: new tables, indexes, and columns only; no drop, truncate, network call, delivery-state update, or guessed server backfill.

The non-empty fixture retained the pending `sync_outbox` mutation and its state, copied the cursor token/high-water/applied checkpoint into their new distinct fields, retained the attachment, and converted `12.34→1234`, `1.25→125`, `0.16→16`, `0.50→50`, and `1.50→150` exactly.

## Runtime invariants proved

- Receiving/observing server version 7 does not set `applied_server_version`; applying version 5 leaves observed 7 intact; applying stale version 4 is rejected.
- An unknown observed version remains SQL `NULL`, never sentinel `0`.
- Local generation advances `1→2` even when `updated_at` decreases, proving sequence is stored state rather than inferred time.
- Batch members must exactly match declared count and orders `0..size-1`; a gap is rejected before insert and negative order violates SQLite `CHECK`.
- Evidence pointing at a mutation survives deletion of that mutation, proving no accidental cascade from heterogeneous owner queues.
- A non-finite legacy money value aborts with `MONEY_MIGRATION_REVIEW_REQUIRED` instead of inventing a rounded value.

Product-scope fingerprint (`app/schemas` + `data/database/src`, 268 files): `e1c4c9a86fae95ebf2b1e00fc0df5be15e0041591ba8219730e700eb17f95aef`.

