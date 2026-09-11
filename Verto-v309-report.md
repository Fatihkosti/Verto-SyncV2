# Verto v309 Report

- Status: `FAIL_STATIC_V309`
- Input SHA: `b7723b93d958cb0d47bd5f4d7a4487c53093a40d0ba2ad0517abcf5b6adb03b3`
- Room: `79→80`; schema79 unchanged; durable `sync_conflict` added.
- v305 unchanged: `a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908`
- v309 server migration: `c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf`
- `verto_apply_sync_mutation`: present, authenticated-only, idempotent receipt authority.
- Aggregate classification: `34/34`; owner307 `16/16`; owner310 deferred `17/17`; Notification client push `0`.
- Runtime-safe generic owner307 adapters: `0`; shadow-only/not-runtime-safe: `15`; PARTY_ROLE stronger bridge: `1`.
- Fixtures: v309 `272/272` PASS; v308 regression `137/137` PASS.
- Idempotency duplicate-effect violations: `0`; conflict durability violations: `0`; origin-echo violations: `0`.
- Inherited 307 exceptions: `9`; new 309 waivers: `0`.
- Runtime V2: `OFF`; build: `NOT VERIFIED`; PostgreSQL: `NOT EXECUTED`.
- Handoff 310/311/312: `BLOCKED` on static evidence.
