# Sync V2 M05 — Final Status

Date: 2026-09-09
Source archive SHA-256: `46cbf8ff7b355f8571e458dfac875159a7f608ea1211346498c568c628ace9ba`

## Result

- Static M05 verification: **PASS 36/36**.
- V2 drain no longer executes legacy participant PUSH/DELETE transport.
- Push coordination covers unified generic, Party, financial, inventory stock/cost, and Optimal lanes.
- ACK uses mutation id + lease token + sent semantic fingerprint.
- Failed/missing dependencies are terminalized for review; future retries honor `nextEligibleAt`.
- Coroutine cancellation is propagated; persistent rejected/review work cannot be reported as push success.

## Evidence

- `evidence/m05/M05_STATIC_GATE.txt`
- `evidence/m05/M05_STATIC_GATE.json`
- `evidence/m05/M05_SOURCE_PATCH.diff`
- `evidence/m05/M05_CHANGED_PATHS.txt`
- `evidence/m05/M05_GRADLE_ATTEMPT.txt`

Source patch SHA-256: `f258376fc98b0aa69e404cd70557be19f197063b4355533879bc9f657320ec10`

## Build status

**BLOCKED / NOT RUN TO COMPLETION.** Gradle 8.9 was not available locally and the wrapper could not reach `services.gradle.org` (`UnknownHostException`). This is not recorded as a build PASS.

## M05 acceptance

Code/static M05 gate: **PASS**. Gradle compile/unit-test gate: **BLOCKED by environment**.
