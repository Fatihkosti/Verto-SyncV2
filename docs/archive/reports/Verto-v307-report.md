# Verto v307 Report

- Session: 307
- Status: `PASS_STATIC_WITH_DOCUMENTED_EXCEPTIONS / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED`
- Input SHA-256: `4f0cd4c334c510b0863645ebc56e2ce667460ae08916b7a88d65d8f812cfc413`
- Output SHA-256: authoritative value is emitted in `Verto-v307-source-of-truth.zip.sha256` after packaging.
- Room: 78 → 79
- Producer matrix rows: 123
- Owner-307 coverage: 16/16
- Aggregate registry coverage: 34/34
- Unclassified producer heuristic hits: 3 (documented exception)
- DataStore-only authority heuristic hits: 1 (documented exception)
- Dirty-only authority heuristic hits: 3 (documented exception)
- Delete-policy violations: 0
- Stronger-outbox heuristic exceptions: 2
- Static fixtures: 80/80 PASS
- Known waived static checks: 9
- Runtime V2: OFF
- Server changed: 0
- Protected runtime changed: 0
- Gradle/runtime: NOT RUN / environment unavailable
- Handoff 308/309/310: authorized under documented user waiver

The nine exceptions are preserved as exceptions, not silently converted to ordinary PASS checks.
