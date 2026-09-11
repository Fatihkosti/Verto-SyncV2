# VERTO Sync Server Verification — Session 305

**Final verdict:** `SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED`  
**Contract:** `SESSION_305_FINAL_REVISED_V2.md`  
**Cutover:** `DISABLED`  
**306 handoff:** `NOT AUTHORIZED`

## Source identity

- v304 ZIP SHA-256: `68088f78fbd47033b0a4acdab23804c06e68d82c7ce8ef34dd49f4c1aafd4b1e`
- Archive entries: `2535`
- Server dump: `schema(2).sql`
- Server dump SHA-256: `fb83bf253fefe7f7b684a41dc5df6e06b0aa933a2c13e26cb835842e1c7f9ea8`
- Dump: `568177` bytes / `16352` lines
- PostgreSQL: `17.6`; pg_dump: `17.11`
- v304 contract authority fingerprints: **PASS**

## Revision 2 handoff

Owner budgets/retention are accepted. The prior Gradle TOOL_ERROR is explicitly non-blocking for this server-only session because Android/Room/Gradle are unchanged. The five former Coverage Drift aggregates are recorded as absent/deferred to their explicit 307/310 owners rather than invented in 305.

## Implemented server expand

- One additive migration: `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`
- Migration SHA-256: `a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908`
- Global server-owned revision sequence with per-organization transaction advisory lock before allocation.
- Append-only tenant-scoped change log; client revision and transaction identity are rejected/server-owned.
- Immutable receipt infrastructure (Push apply remains 309).
- Server-owned scope using `auth.uid()`, active `app_users`, role and complete `employee_permissions` JSON fingerprint.
- Opaque scope-bound cursor token registry; no numeric client cursor arithmetic.
- Pull ordered only by revision, bounded at 200 changes / 1 MiB normal page, with unsplit transaction groups up to 2 MiB.
- `min_available_revision` / `CURSOR_EXPIRED` semantics.
- Bootstrap uses the same organization lock, captures baseline and materializes stable private rows in one transaction. No business adapter is guessed or backfilled; producer owners populate snapshot state in later sessions before cutover.
- Deterministic reconciliation manifest over adapter-owned snapshot state.
- RLS enabled on new sync metadata with direct app table mutation revoked; public app access is RPC-only.
- Existing Optimal stream, tombstones, financial/inventory semantics and legacy Android runtime remain untouched.

## Coverage reconciliation

- Aggregates: **34/34**
- Mapping rows: **89**
- `UNKNOWN`: **0**
- Present mappings: **65**
- Absent mappings explicitly deferred: **24**
- Existing stronger mappings preserved: **17**
- Inert mappings preserved: **24**

## Static verification

- v305 static gates: **92/92 PASS**
- Semantic/model fixtures: **92/92 PASS**
- 10,000-change Python semantic model: **PASS** (ordering/gaps/tenant filtering/group-safe paging model only).
- Static verifier deterministic run hash: `7ec1a6c33207970db29f131351735caf1b993b6db6101ee405d4e6cd3602db16` on two identical runs.
- Production Kotlin changed: **0**
- Gradle files changed: **0**
- Historical SQL changed: **0**
- Room: **77 → 77**

## Database execution gate

`psql`, `postgres`, `initdb`, and `pg_ctl` are absent from the execution environment. Therefore the migration was **not applied** to PostgreSQL here, and delayed-commit, rollback, RLS adversarial, bootstrap concurrency and real 10,000-row database tests were **not executed**. By the Session 305 contract this forbids Full PASS and forbids naming the ZIP `source-of-truth`.

## Final state

`SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED`

Android V2 runtime remains OFF, Legacy remains authoritative, and no 306 handoff is authorized until the database-runtime gates pass.
