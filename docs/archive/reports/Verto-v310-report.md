# Verto v310 — Stronger Sync Bridge Execution Report

## Result

`PASS_STATIC_STRONGER_FINANCIAL_INVENTORY_OPTIMAL_BRIDGE / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED / RUNTIME_EXECUTION_BYPASSED_BY_USER`

## Baseline integrity

- Input: `Verto-v309-source-of-truth.zip`
- Input SHA-256: `e45f32c009ae605d010361cf12da188230ef3bf8eed0650bba81bb35dc21817d`
- Input archive entries: `2622`
- Production Kotlin baseline: `1175`
- Room: `80 → 80`
- schema80 SHA-256: `1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543`
- v305 migration SHA-256: `a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908`
- v309 migration SHA-256: `c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf`
- Historical migration drift: `0`

## Session 310 implementation

- Owner310 aggregates: `17/17`
- Coverage rows: `17/17`
- Bridge-ready/shadow-safe: `17`
- Blocked owner310 aggregates: `0`
- Generic LWW fallbacks: `0`
- New waivers: `0`
- Runtime V2: `DISABLED`
- New server migration: `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- New server migration SHA-256: `43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce`

The bridge preserves Financial, Inventory, and Optimal stronger identities and transports them through unified immutable receipts/revision changes without creating a second authoritative generic durable intent.

## Static/model evidence

- v310 fixtures: `499/499 PASS`
- v309 regression fixtures: `272/272 PASS`
- v308 regression fixtures: `137/137 PASS`
- v308 `MODEL_10K_PASS`: `true`
- v309 regression failures: `0`
- v308 regression failures: `0`
- Deterministic verifier normalized hash: `38daab50e4a9e41bdf018e5396eb186db58e851afeeee8e8495373135e5be21a`
- Kotlin delimiter/lexical static scan: `PASS` for all v310 modified/new Kotlin implementation files.
- Python verifier syntax: `PASS`
- Shell verifier syntax: `PASS`
- v310 implementation TODO/FIXME/UNKNOWN/LATER markers: `0`

## Acceptance counters

All required violation counters are zero, including posted-invoice generic overwrite, financial hard delete, inventory ledger delete, mutable cost revision, duplicate Financial/Inventory/Optimal effects, regenerated stronger identities, receipt/change atomicity violations, owner310 timestamp cursor authority, remote-apply enqueue echo, new waivers, and 308/309 regressions.

## Coverage artifacts

- `docs/sync/VERTO_SYNC_STRONGER_COVERAGE_v310.csv`: `17` rows
- `docs/sync/VERTO_SYNC_STRONGER_SERVER_ADAPTERS_v310.csv`: `17` rows
- `docs/sync/VERTO_SYNC_STRONGER_PRODUCER_COVERAGE_v310.csv`: `17` rows


## Runtime execution status — explicitly bypassed by user

The user explicitly requested continuing with static verification and packaging while bypassing unavailable/runtime execution gates.

- Compile: `NOT_RUN_USER_AUTHORIZED_STATIC_ONLY`
- Unit tests: `NOT_RUN_USER_AUTHORIZED_STATIC_ONLY`
- PostgreSQL runtime execution: `False` (`NOT_EXECUTED_USER_AUTHORIZED_STATIC_ONLY`)
- Runtime V2 remains: `DISABLED`

This omission is **documented, not converted into a runtime PASS, and is not counted as a new waiver**. The session verdict is static only.

## Handoff

- handoff311Authorized: `true`
- handoff312Authorized: `true`
- blockers: `[]`

311 may build orchestration/scheduling only on this static-passed data plane; runtime correctness still requires later build/runtime/PostgreSQL execution when the environment is available.
