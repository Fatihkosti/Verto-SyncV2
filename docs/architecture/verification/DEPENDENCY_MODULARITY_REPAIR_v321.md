---
status: supporting
scope: system
owner: "architecture"
last_verified_against: v321
---
# Dependency & Modularity Repair v321

## Input freeze

```text
inputZipSha256 = ebb5d5f11b04f428c49b862ad1bbc5c48e1f34222ba1740c85cc10ded1dfeda2
planSha256     = fd04d47a1b03fcad0afa0aa93312396ea630cea366e3eb46ebf42a5921eb0979
```

## Dependency boundary repair

The exact v320 baseline contained 18 current architecture findings: `VARCH-003=4`, `VARCH-005=2`, and `VARCH-012=12`. The v321 scan contains zero findings for all three rules and zero new architecture identities.

Optimal no longer directly depends on or imports the Invoice, Management, Messages, Party, or Payment implementation modules. Cross-feature translation is performed by explicit app composition bridges against Optimal-owned Ports/DTOs and provider Public contracts. The seven formerly debt-classified Dashboard API consumer edges are now verified as valid API-only Public-contract edges; Dashboard's own `dashboard -> dashboard:api` edge remains valid.

```text
legacy debt                 18 -> 0
Optimal foreign imports     42 -> 0
Optimal provider impl edges  5 -> 0
dependency cycles            0 -> 0
undeclared project deps           0
undeclared external deps          0
foreign Entity/DAO/storage        0
feature manifests                18/18
Room version                       81
```

## Layer repair

- Inventory Quick Stock Application no longer imports Room Entity/DAO or Android rendering infrastructure. Data mapping is owned by the data boundary and Android PDF rendering by an infrastructure gateway.
- Party `CalculateClientBalanceUseCase` now consumes Party-owned domain inputs rather than Room entities/data mappers; its decision semantics are preserved by regression tests present in source.

## Ratchet and governance

`technical-debt-baseline.json` remains immutable v318 historical evidence. `technical-debt-governance-v321.json` admits zero remaining identities for `VARCH-003`, `VARCH-005`, and `VARCH-012`; mutation coverage proves a removed identity fails if reintroduced. Complexity budgets were not raised.

Architecture decision rationale is recorded in `ADR-0001-optimal-composition-boundaries.md`.

## Static verification

```text
Change Contract             = PASS
Architecture Guard          = PASS
Dependency Admission        = PASS
Data Ownership              = PASS
Kotlin Quality Ratchet      = PASS
Complexity                  = PASS
Documentation               = PASS
Design System               = PASS
Design System Diff          = PASS
Guard regression tests      = PASS (18/18)
Unified gate self-test      = PASS
```

## Gradle/final admission

The mandatory unified `scripts/ci/run-quality-gate.sh all 321` path passed every static stage through Design System Diff. At Detekt, the Gradle wrapper attempted to download `https://services.gradle.org/distributions/gradle-8.9-bin.zip` and failed with `java.net.UnknownHostException: services.gradle.org`. The environment therefore blocked Gradle execution before Detekt could run; Lint, unit tests, and Debug Build were not run because the gate is fail-fast. Independent Source-of-Truth admission correctly returned FAIL.

## Verdict

```text
implementationVerdict = IMPLEMENTED_STATIC_DEPENDENCY_REPAIR_V321
finalVerdict          = FINAL_ADMISSION_BLOCKED_ENVIRONMENT
sourceOfTruthAdmission= FAIL
handoff322Authorized  = true (static/modularity only; no build/test PASS inherited)
```

The machine-readable evidence is `DEPENDENCY_MODULARITY_REPAIR_v321.json`.
