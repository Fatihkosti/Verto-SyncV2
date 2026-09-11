# Verto v301 — Motion System Closure Report

## Status

`IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`

Session 301 was implemented against the exact v300 archive required by the contract. Static Design System gates pass, including the Final Gate with `rawMotionDurations = 0`. Gradle could not bootstrap because `services.gradle.org` is unreachable from this environment, so compile/unit-test PASS is not claimed and this archive is not labeled Source of Truth.

## Input evidence

- Input ZIP: `Verto-v300-implemented-static.zip`
- Input SHA-256: `8b8993e73692b014ab2141c27365b3f3e5126c0da61c9bb8799a31e11bc7459b`
- Archive entries inspected: `2493`
- Inherited v300 status: `IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`

### Preflight gates

- Session 300 Migration Gate: `PASS`
  - snapshotId: `3507b73f34060fb4ac57430a687f6f924ac09a70f65038ab9559ffe2fc4e61e7`
  - `rawDpOutsideApprovedTokenFiles = 0`
  - `rawMotionDurations = 5`
- Session 301 Migration Gate before mutation: `PASS`
  - snapshotId: `0d6ba6103615def3b57d822d1287fab20b2fe9dc4ed83a96f34b6ea14ad96a30`
  - `rawMotionDurations = 5`
- Session 301 Final Gate before mutation: `FAIL` for exactly one reason:
  - `final target: rawMotionDurations expected 0 actual 5`
- Contract verifier before mutation: `DESIGN_SYSTEM_CONTRACT PASS 0`
- Gradle bootstrap pre-state documented by the contract was reproduced after implementation; see Build/Test section.

## Pre raw-motion inventory

Exactly 5 scanner-owned occurrences across 4 production Kotlin files and 2 unique raw values:

| ID | File | Pre line | Pre literal | Pre SHA-256 |
|---|---|---:|---|---|
| v301-001 | `app/src/main/kotlin/com/verto/app/ui/components/Components.kt` | 64 | `tween(800)` | `1874145012f109e47403d26c89ba4385054ea10d2dde49171be30aabed3225b4` |
| v301-002 | `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt` | 59 | `tween(800)` | `e5f19ff810a34afd32617c0e0d50b58d873969e237d774863b67b7508a46b33a` |
| v301-003 | `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt` | 39 | `tween(800, easing = LinearEasing)` | `8216842cac8e7996294e646c321e1f32235d3a21cdfd2c925bab63919afc207f` |
| v301-004 | `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/splash/SplashScreen.kt` | 31 | `tween(durationMillis = 1500, easing = FastOutSlowInEasing)` | `adea93311cf69b5a02e45d74fc37c6eebbdc12655c9cbde33161ffbee626cace` |
| v301-005 | `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/splash/SplashScreen.kt` | 36 | `tween(durationMillis = 1500, easing = FastOutSlowInEasing)` | `adea93311cf69b5a02e45d74fc37c6eebbdc12655c9cbde33161ffbee626cace` |

Unique raw duration values: `800`, `1500`.

## VertoMotion pre-state

Token owner: `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/DesignTokens.kt`

Pre SHA-256: `06443db546c488dd1e579a5c65cafd87c927f21dd055593a717f6b32c7e20680`

Pre roles:

- `instant = 0`
- `fast = 120`
- `normal = 220`
- `slow = 360`
- `emphasized = 500`

Production references to `VertoMotion.*` in the four affected consumer files before v301: `0`.

## Implementation

Added only these two roles to the existing `VertoMotion` owner:

- `attentionPulse = 800`
- `long = 1500`

Exact 5-record mapping:

| ID | Replacement | Resolved duration |
|---|---|---:|
| v301-001 | `tween(VertoMotion.attentionPulse)` | 800ms |
| v301-002 | `tween(VertoMotion.attentionPulse)` | 800ms |
| v301-003 | `tween(VertoMotion.attentionPulse, easing = LinearEasing)` | 800ms |
| v301-004 | `tween(durationMillis = VertoMotion.long, easing = FastOutSlowInEasing)` | 1500ms |
| v301-005 | `tween(durationMillis = VertoMotion.long, easing = FastOutSlowInEasing)` | 1500ms |

Manifest: `docs/design-system/MOTION-MIGRATION-v301.json`

Manifest SHA-256: `6161fe5fa63f4bc72696bd3f205392123da034dff2e1de353dc7a88ba753ecda`

Accounting: `5/5`; unique source files `4`; unique raw values `2`; unclassified/unmigrated/duration/easing/repeat/primitive/behavior drift all `0`.

## Behavior-preservation proof

A reverse-transform comparison against the exact input ZIP proved all four production consumer files become byte-identical to pre-v301 after reversing only the token substitutions/import additions.

- Duration equivalence: `800→800` for all three pulse animations; `1500→1500` for both Splash animations.
- Default tween easing unchanged in both TimerBadge consumers.
- `LinearEasing` unchanged in Party `BlinkingBadge`.
- `FastOutSlowInEasing` unchanged in both Splash animations.
- `RepeatMode.Reverse` unchanged in all three repeating pulse animations.
- Animation primitives unchanged: `rememberInfiniteTransition.animateFloat + infiniteRepeatable + tween` and `animateFloatAsState + tween` remain identical.
- Initial/target expressions unchanged.
- Splash stroke/fill remain synchronized through the same `VertoMotion.long = 1500` token.
- `SplashScreen delay(2000)` remains present and unchanged; navigation decision/callback order was not modified.

## Non-motion duration review

Raw `delay(...)` calls were reviewed as evidence-only. They represent navigation holds, debounce, polling/readiness loops, recording clocks, pending-action clocks, save debounce, or other operational timing. They were not reclassified as Design System motion and were not modified. Project-wide reduced-motion policy is not invented in v301 and remains a session 303 concern.

## Changed production Kotlin files

Exactly 5 existing production Kotlin files were modified, matching the v301 cap:

1. `app/src/main/kotlin/com/verto/app/ui/components/Components.kt`
2. `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt`
3. `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt`
4. `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/splash/SplashScreen.kt`
5. `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/DesignTokens.kt`

No new production Kotlin files were created. The allowed Core token contract test was extended only with explicit Motion values/order assertions.

## Governance changes

Updated/created only the allowed v301 governance artifacts:

- `docs/design-system/CURRENT-DESIGN-STATE.md`
- `docs/design-system/CURRENT-HARDCODED-MANIFEST.json`
- `docs/design-system/DESIGN_SYSTEM_CONTRACT.md`
- `docs/design-system/MIGRATION-LEDGER.md`
- `docs/design-system/MOTION-MIGRATION-v301.json`
- `Verto-v301-report.md`

`CURRENT-HARDCODED-MANIFEST.json`: `findingCount = 0`, `findings = []`.

## Protected hashes

All 19 protected files listed in SESSION_301_FINAL.md §57 matched their required SHA-256 before mutation and remained byte-identical after implementation. No scanner, baseline, final-zero target, exception ledger, Material policy, prior migration manifest, protected Core component/token file, Logistics token/test file, or v300 report was modified.

## Post static gates

Post snapshotId: `544c6b93cf9fb32ec6b83cfd6fdaad7255842f5901a4cb9e26a21b65879a52ae`

- Session 301 Migration Gate: `PASS`
- Diff Gate: `PASS`
- Contract verifier: `DESIGN_SYSTEM_CONTRACT PASS 0`
- Session 301 Final Gate: `PASS`
- Final failures: `[]`

Post metrics:

```text
legacyFocusedHardcodedCount                  = 0
hardcodedUserFacingStringsFull              = 0
coreVisibleLiteralDebt                       = 0
rawMaterialMustWrap                         = 0
forbiddenMaterial                            = 0
rawDpOutsideApprovedTokenFiles              = 0
rawSpOutsideApprovedTokenFiles              = 0
rawColorOutsideApprovedTokenFiles            = 0
rawMotionDurations                           = 0
coreBoundaryViolations                       = 0
designSystemDomainStringCount                = 0
externalDesignSystemDomainStringReferences   = 0
expiredExceptions                            = 0
permanentExceptions                          = 0
duplicateGuardedPrimitives                   = 0
activeExceptionEntryCount                    = 0
```

## Build and tests

Gradle bootstrap attempt:

```text
./gradlew --version
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Because Gradle 8.9 could not bootstrap, the required Core/Auth/Invoice/Party/App compile tasks and Core/Auth/Invoice/Party unit-test tasks could not run. Their truthful status is `BLOCKED`, not PASS. No APK/build success is claimed.

## Sessions 302–303

Static Final Gate PASS in v301 does not complete the parent plan. Session 302 public API/component rationalization and Session 303 screenshot/accessibility/runtime closure remain pending and were not executed here.

## Out-of-scope findings

No scope expansion was required. Existing operational/business timers and the absence of a reduced-motion project policy were left unchanged by contract.

## Final verdict

`IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`

Motion debt was closed exactly `5→0` with 1:1 duration/easing/repeat/primitive/state behavior preservation and no protected-file regression. Full build/test PASS cannot be asserted until Gradle bootstrap is available.

## Output archive integrity

The final ZIP SHA-256 is emitted in the sibling `Verto-v301-implemented-static.zip.sha256` after packaging. Embedding the final ZIP hash inside a file contained by that same ZIP would invalidate the hash through self-reference.
