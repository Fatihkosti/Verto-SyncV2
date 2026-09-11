# Verto Sync Producer Verification — Session 307

## Verdict

`PASS_STATIC_WITH_DOCUMENTED_EXCEPTIONS / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED`

This verdict uses the user's explicit waiver for exactly nine known static exceptions. It is **not** the original contract's zero-exception clean PASS. All nine remain visible as `WAIVED_KNOWN_EXCEPTION` in the JSON report.

## Source identity

- Input: `Verto-v306-source-of-truth.zip`
- SHA-256: `4f0cd4c334c510b0863645ebc56e2ce667460ae08916b7a88d65d8f812cfc413`
- Input archive entries: `1696`
- Room: `78 → 79`
- Schema 78 SHA-256: `d0d89b1a6d8309e92c48cace4b30f934d8c83bcd3d75fcc0a2b3946a1d434557`
- Schema 79 SHA-256: `ab4a24d0698914a852bf1c52498174adcd32999006a1011b6a80a438395031da`

## Producer coverage

- Aggregate registry: 34/34 classified
- Owner-307 aggregates: 16/16 closed
- Producer rows: 123
- Unified outbox: 104
- Preserved stronger outbox: 11
- Remote apply/no enqueue: 7
- Cache hydration/no enqueue: 1
- Blocked matrix rows: 0

## Known exceptions

Exactly nine checks were waived by explicit user instruction. Their exact names, observed states, rationale, and risk acceptance are in `docs/sync/VERTO_SYNC_307_KNOWN_EXCEPTIONS.json`.

- Unclassified heuristic hits: 3
- DataStore-only heuristic authority hits: 1
- Dirty-only heuristic hits: 3
- Stronger-outbox heuristic violations: 2

## Safety/regression evidence

- Server changes: 0
- Protected runtime changes: 0
- Gradle files changed: 0
- Direct remote-before-durable-commit: 0
- Hard-delete policy violations: 0
- Attachment binary in payload: 0
- Static fixtures: 80/80 PASS
- Deterministic normalized hash: `faf2cef8a0d09a63921bfba7f28f22cbeaab85c5d5d85a4d1291ad990ba8cb6b`

## Runtime/build truth

- Runtime V2: `DISABLED`
- Build: `NOT_RUN_ENVIRONMENT_UNAVAILABLE`
- Unit tests: `NOT_RUN_ENVIRONMENT_UNAVAILABLE`
- Instrumentation executed: `False`

No runtime/network/process-death claim is made.

## Handoff

- 308 authorized: true
- 309 authorized: true
- 310 authorized: true
