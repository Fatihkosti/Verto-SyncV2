# SESSION 333 FINAL — Real Behavioral Mutation Execution & Semantic Contract Conformance

## 0. Contract Identity

- **Project:** Verto
- **Session:** 333
- **Input:** `Verto-v332-final-admission-blocked-environment.zip`
- **Purpose:** Close the final verified gaps preventing trustworthy roadmap closeout.
- **Session type:** Narrow corrective closeout.
- **Roadmap impact:** No new phase.
- **Behavioral intent:** Preserve production behavior; strengthen verification only.
- **Primary defects to fix:**
  1. Behavioral mutation runner reports `8/8 DETECTED` without applying real behavioral mutations or executing the detecting tests.
  2. Critical contract tests can satisfy evidence with symbol presence/reflection rather than semantic conformance.

---

# 1. Mission

Session 333 MUST make both statements true:

> A required behavioral mutation is considered detected only if the mutation is actually applied to an isolated source/test fixture and the expected real test fails because of that mutation.

and:

> A critical stable contract is considered tested only when the test executes the contract or a declared production implementation and verifies meaningful semantic behavior.

No regex-only or symbol-presence evidence may satisfy either requirement.

---

# 2. Verified Starting Defect — Behavioral Mutation Runner

Current Session 332 runner can report:

```text
8/8 DETECTED
PASS
```

even if production behavior is actually modified incorrectly.

A manual verification changed authorization behavior inside `InvoiceWriteCoordinator`, but the runner still returned PASS because it only inspected expected strings/test artifacts.

This is a false positive.

---

# 3. Verified Starting Defect — Contract Tests

Some critical contract tests currently prove little more than:

```text
symbol exists
class name exists
reflection can see type
```

Examples of invalid semantic evidence include tests equivalent to:

```kotlin
assertNotNull(PartyDirectoryGateway::class.java.simpleName)
```

Such tests MUST NOT count as contract conformance.

---

# 4. Scope

Session 333 is restricted to:

```text
behavioral mutation runner
behavioral mutation fixtures
focused critical-flow tests
semantic contract conformance tests
contract-test registry validation
CI admission wiring
closeout evidence
```

---

# 5. Explicit Non-Goals

Session 333 MUST NOT:

```text
change business rules
change persistence schema
change sync protocol
change Room migrations
change server SQL
change Supabase schema
redesign architecture
add new features
change UI
add plugin frameworks
perform broad refactoring
```

Production changes are allowed only if needed to expose an already-existing semantic seam for testing.

---

# 6. Real Mutation Execution Requirement

A mutation is valid only if all steps occur:

```text
1. Create isolated working copy or temporary source tree.
2. Apply one specific behavioral mutation.
3. Run the exact detecting test(s).
4. Observe expected failure.
5. Record evidence.
6. Destroy/revert mutation.
7. Verify clean tree.
```

---

# 7. Forbidden Mutation Implementation

Forbidden:

```text
searching for expected strings only
checking test file existence only
checking test name existence only
declaring mutation detected from metadata
simulating failure by returning hardcoded FAIL
mutating only registry JSON
```

---

# 8. Mutation Isolation

Preferred:

```text
temporary directory
copy-on-write fixture
isolated generated source tree
```

The production source tree MUST remain unchanged after mutation execution.

---

# 9. Mandatory Behavioral Mutations

The following eight mutations remain mandatory.

## M1 — Bypass Invoice Validation

Apply a real mutation that causes invalid invoice input to proceed.

Expected:

```text
real invoice critical-flow test FAILS
```

---

## M2 — Bypass Invoice Authorization

Apply a real mutation that allows unauthorized write flow.

Expected:

```text
authorization regression test FAILS
```

This exact mutation MUST reproduce the manually discovered Session 332 false positive.

---

## M3 — Reorder Post-Commit Before Persistence

Apply a mutation that executes a post-commit effect before durable persistence success.

Expected:

```text
ordering/transaction regression test FAILS
```

---

## M4 — Skip Inventory Effect

Apply a mutation that allows invoice completion while skipping the required inventory effect.

Expected:

```text
inventory/invoice regression test FAILS
```

---

## M5 — Skip Required Payment Effect

Apply a mutation that skips a payment effect in a flow where payment behavior is required.

Expected:

```text
payment regression test FAILS
```

If exact current semantics make this mutation inapplicable, use an equivalent payment-critical mutation and document the substitution.

Required total remains 8.

---

## M6 — Wake Sync Before Persistence

Apply a mutation that calls scheduler/wake before orchestration generation persistence.

Expected:

```text
SyncManager ordering test FAILS
```

---

## M7 — Drop Sync Continuation

Apply a mutation that ignores required continuation scheduling.

Expected:

```text
sync continuation regression test FAILS
```

---

## M8 — Allow Invalid Logistics Transition

Apply a mutation that allows an invalid logistics transition or bypasses its rejection.

Expected:

```text
logistics regression test FAILS
```

---

# 10. Mutation Detection Rule

A mutation is `DETECTED` only if:

```text
mutation applied = true
target test executed = true
target test failed = true
failure is causally related to mutation = true
```

Anything else:

```text
NOT_DETECTED
```

---

# 11. Mutation Runner Interface

Create or replace the current runner with a reproducible command, recommended:

```bash
python3 tools/quality/verto_behavioral_mutation_runner.py verify --root .
```

Supported commands SHOULD include:

```text
verify
self-test
report
```

---

# 12. Mutation Runner Output

Each mutation result MUST include:

```json
{
  "id": "M2",
  "mutation_applied": true,
  "target": "InvoiceWriteCoordinator",
  "detecting_test": "...",
  "test_executed": true,
  "test_exit_code": 1,
  "detected": true
}
```

---

# 13. Mutation Runner Fail Semantics

Runner returns FAIL if:

```text
mutation not applied
detecting test not executed
detecting test passes unexpectedly
mutation cleanup fails
wrong test is executed
result is ambiguous
```

---

# 14. Mutation Self-Test

The mutation runner MUST have its own self-test proving that:

```text
a known real mutation
→ expected test fails
→ runner reports DETECTED
```

and:

```text
a no-op mutation
→ test still passes
→ runner reports NOT_DETECTED
```

---

# 15. Build/Test Execution Strategy

Use the narrowest test task that proves each mutation.

Examples:

```text
Invoice focused test
SyncManager focused test
Logistics focused test
Payment focused test
Inventory focused test
```

Do not run the full project build for every mutation if a focused task is sufficient.

---

# 16. Environment Handling

If a required focused Gradle test cannot execute because the environment cannot resolve Gradle/dependencies:

```text
mutation status = BLOCKED_ENVIRONMENT
```

It MUST NOT be reported as DETECTED.

---

# 17. Semantic Contract Conformance Requirement

A critical contract test is valid only if it verifies at least one meaningful semantic property.

Examples:

```text
input → expected output
not-found semantics
nullability semantics
ordering semantics
idempotency semantics
error semantics
authorization boundary
write side-effect behavior
```

---

# 18. Invalid Contract Test Patterns

The following MUST NOT count as conformance:

```text
assertNotNull(Contract::class.java.simpleName)
assertEquals("ContractName", ...)
test file references symbol but never executes it
reflection-only presence check
constructor-only instantiation with no behavior assertion
```

---

# 19. Critical Contracts to Revalidate

At minimum revalidate the critical stable contracts identified in Session 330/332, especially contracts related to:

```text
Party lookup/read
Invoice query/write
Payment query/write
Shipment/Logistics read
Sync extension seam where registered as stable
Commission report if registered stable
```

Use the actual registry.

---

# 20. Contract Conformance Test Shape

Preferred pattern:

```kotlin
abstract class PartyLookupContract {
    abstract fun subject(): PartyLookupPort

    @Test
    fun unknownPartyReturnsNotFound() { ... }

    @Test
    fun knownPartyReturnsStableSummary() { ... }
}
```

Then run the same semantics against:

```text
fake/test implementation
production adapter where practical
```

---

# 21. Production Adapter Conformance

Where practical:

```text
production adapter
→ same contract suite
```

If Room semantics are involved:

```text
use isolated in-memory database test
```

No real network backend is required.

---

# 22. Contract-Test Registry Upgrade

Update:

```text
docs/architecture/contracts/contract-test-registry-v332.json
```

or supersede with:

```text
docs/architecture/contracts/contract-test-registry-v333.json
```

Each entry MUST include:

```yaml
contract_id:
contract_symbol:
implementation_symbol:
test_file:
test_symbol:
semantic_assertions:
coverage_mode:
status:
```

---

# 23. Semantic Assertion Metadata

`semantic_assertions` MUST list real semantics, for example:

```text
not_found
stable_result_mapping
authorization_failure
idempotent_write
ordering
```

It must not contain vague labels like:

```text
exists
loaded
referenced
```

---

# 24. Contract Evidence Guard

The compatibility/evidence guard MUST reject:

```text
symbol-only tests
reflection-only tests
missing implementation linkage
missing semantic assertions
missing test symbol
missing test file
```

---

# 25. Contract Evidence Mutation Test

Required mutation:

```text
replace a valid semantic contract test with a symbol-presence-only assertion
```

Expected:

```text
contract evidence guard FAIL
```

---

# 26. Contract Semantic Mutation

At least one critical contract MUST have a semantic mutation test.

Example:

```text
change not-found behavior
or change returned mapping
or change idempotency behavior
```

Expected:

```text
contract test FAIL
```

---

# 27. No Fake/Production Drift

Where both fake and production implementation exist, contract tests SHOULD verify both against the same semantic suite.

If not practical, document why.

---

# 28. Required Final Behavioral Mutation State

Must equal:

```text
required_behavioral_mutations = 8
mutations_applied = 8
detecting_tests_executed = 8
mutations_detected = 8
undetected_mutations = 0
```

---

# 29. Required Final Contract Test State

Must equal:

```text
critical_contracts_registered = <count>
critical_contracts_with_semantic_tests = same count
symbol_only_contract_tests_counted = 0
invalid_contract_test_evidence = 0
```

---

# 30. Existing Session 332 Fixes Must Remain

Reverify:

```text
multi-line breaking contract mutation = FAIL
unadmitted external dependency = FAIL
feature admission self-tests >= 10
scalability self-tests >= 6
```

No regression allowed.

---

# 31. Mandatory False-Negative Regression Test — Authorization

Automate the exact manual check that exposed the Session 332 flaw:

```text
mutate InvoiceWriteCoordinator authorization path
→ execute expected authorization regression test
→ test fails
→ mutation runner reports DETECTED
```

---

# 32. Mandatory False-Negative Regression Test — No-op

Apply no meaningful mutation.

Expected:

```text
test passes
runner reports NOT_DETECTED
runner overall self-test confirms no false detection
```

---

# 33. Mandatory False-Evidence Regression Test

Create fixture:

```text
contract registry points to a test file
test only asserts contract class name exists
```

Expected:

```text
contract evidence validation FAIL
```

---

# 34. CI Integration

Use existing stages where possible.

Recommended:

```text
maintainability-testability
contract-compatibility
feature-scalability-admission
```

Do not add a new stage if current stages can own the strengthened checks cleanly.

---

# 35. Roadmap Admission Requirement

Final roadmap admission MUST require:

```text
behavioral mutation execution PASS
semantic contract conformance PASS
contract evidence validation PASS
Session 332 guards remain PASS
```

---

# 36. Source-of-Truth Admission

Source-of-Truth admission MUST fail if:

```text
behavioral mutation runner is not executed
any mutation is NOT_DETECTED
critical contract lacks semantic conformance
contract evidence is symbol-only
```

---

# 37. Required Artifacts

Create at minimum:

```text
docs/quality/maintainability/BEHAVIORAL_MUTATION_MATRIX_v333.json
docs/quality/maintainability/BEHAVIORAL_MUTATION_MATRIX_v333.md

docs/architecture/contracts/contract-test-registry-v333.json

docs/architecture/verification/SEMANTIC_CONTRACT_CONFORMANCE_v333.json
docs/architecture/verification/SEMANTIC_CONTRACT_CONFORMANCE_v333.md

docs/architecture/verification/PHASE_5_REVERIFICATION_v333.json
docs/architecture/verification/PHASE_6_REVERIFICATION_v333.json
docs/architecture/verification/ARCHITECTURE_ROADMAP_CLOSEOUT_v333.json
docs/architecture/verification/ARCHITECTURE_ROADMAP_CLOSEOUT_v333.md

docs/architecture/contracts/sessions/session-333.json
docs/architecture/verification/SESSION_333_INPUT_SNAPSHOT.json
```

---

# 38. Historical Evidence Rule

Do NOT rewrite:

```text
v329
v330
v331
v332
```

evidence to make historical execution appear stronger than it was.

v333 supersedes the closeout status.

---

# 39. Mandatory Static Gates

Run:

```bash
python3 tools/architecture/verto_arch_guard.py verify --root .
python3 tools/technical_debt/dependency_gate.py --root .
python3 tools/persistence/verto_persistence_guard.py verify --root .
python3 tools/architecture/verto_contract_compatibility_guard.py self-test --root .
python3 tools/architecture/verto_contract_compatibility_guard.py verify --root .
python3 tools/architecture/verto_feature_admission_guard.py self-test --root .
python3 tools/architecture/verto_feature_admission_guard.py verify --root .
python3 tools/quality/verto_testability_guard.py self-test --root .
python3 tools/quality/verto_testability_guard.py verify --root .
python3 tools/technical_debt/verify.py ratchet --root .
python3 tools/technical_debt/verify.py differential --root .
python3 scripts/verify-kotlin-quality-static.py verify --root . --mode current-ratchet
```

Use actual paths if they differ.

---

# 40. Mandatory Mutation Runner Verification

Run:

```bash
python3 tools/quality/verto_behavioral_mutation_runner.py self-test --root .
python3 tools/quality/verto_behavioral_mutation_runner.py verify --root .
```

Expected:

```text
8/8 real mutations detected
```

---

# 41. Mandatory Focused Tests

Execute actual focused tests for:

```text
Invoice authorization/validation
Invoice post-commit ordering
Inventory effect
Payment effect
Sync ordering
Sync continuation
Logistics transition
Critical contract conformance suites
```

---

# 42. Mandatory Gradle Verification

Where environment permits:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon detekt
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
```

---

# 43. Unified Gate

Run:

```bash
bash scripts/ci/run-quality-gate.sh all 333
```

---

# 44. Environment Block Rule

If Gradle cannot execute because of external network/environment:

```text
static_gates = PASS
mutation_execution = BLOCKED_ENVIRONMENT if focused tests cannot run
source_of_truth_admission = BLOCKED_ENVIRONMENT
SESSION_333 = BLOCKED_ENVIRONMENT
ROADMAP = BLOCKED_ENVIRONMENT
```

Never translate unexecuted mutations into PASS.

---

# 45. Acceptance Criteria — Behavioral Mutations

Session 333 passes this section only if:

1. All 8 mutations are actually applied.
2. All 8 detecting tests are actually executed.
3. All 8 detecting tests fail for the intended mutation.
4. No-op mutation is NOT falsely detected.
5. Mutation cleanup succeeds.
6. Final source tree is clean.

---

# 46. Acceptance Criteria — Contract Conformance

Session 333 passes this section only if:

1. Every critical stable contract has semantic conformance evidence.
2. Symbol-only/reflection-only tests are not counted.
3. At least one semantic mutation is detected by a contract test.
4. Contract evidence registry matches actual symbols/tests.
5. Production adapter conformance is tested where practical.
6. Invalid contract evidence fixture fails correctly.

---

# 47. Acceptance Criteria — Existing Guards

Must remain:

```text
architecture_guard = PASS
dependency_gate = PASS
persistence_guard = PASS
contract_compatibility_guard = PASS
feature_scalability_admission = PASS
maintainability_testability = PASS
technical_debt_ratchet = PASS
differential_quality = PASS
kotlin_quality = PASS
documentation = PASS
design_system = PASS
```

---

# 48. Required Final Metrics

Final report MUST include:

```text
behavioral_mutations_required = 8
behavioral_mutations_applied = 8
detecting_tests_executed = 8
behavioral_mutations_detected = 8
false_positive_noop_mutation = 0

critical_contracts = <count>
critical_contracts_with_semantic_conformance = <same count>
symbol_only_contract_tests_counted = 0
invalid_contract_test_evidence = 0

compatibility_multiline_break_test = PASS
unadmitted_external_dependency_test = PASS
feature_admission_self_tests = <count >=10>
scalability_self_tests = <count >=6>
```

---

# 49. Phase 5 Closeout

Phase 5 may be considered complete only if:

```text
5/5 critical flows protected
8/8 real behavioral mutations detected
testability guard PASS
semantic contract conformance PASS for critical seams
```

---

# 50. Phase 6 Closeout

Phase 6 may be considered complete only if:

```text
contract compatibility PASS
semantic contract evidence PASS
feature admission PASS
scalability admission PASS
external dependency admission PASS
```

---

# 51. Roadmap Closeout

Structural roadmap status may be:

```text
PASS_STATIC
```

only if all non-Gradle controls are truly executed and pass.

Final full status:

```text
PASS
```

requires mandatory Gradle/build verification.

---

# 52. Definition of Done — Mutation Runner

The following must be impossible:

```text
authorization is broken in production source
mutation runner only sees test text
mutation runner reports DETECTED
```

Correct behavior:

```text
mutation applied
→ test executed
→ test fails
→ DETECTED
```

---

# 53. Definition of Done — Contract Tests

The following must be impossible:

```text
test references Contract::class.java.simpleName
→ registry calls it semantic contract coverage
```

Correct behavior:

```text
contract/adapter executed
→ semantic result asserted
→ evidence valid
```

---

# 54. Final Definition of Done

Session 333 is DONE only when the last two verification gaps are closed:

```text
fake behavioral mutation evidence = impossible
symbol-only contract conformance = impossible
```

and all existing guards remain green.

---

# 55. Required Final Status Block

The implementation report MUST state exactly:

```text
SESSION_333 = PASS | BLOCKED_ENVIRONMENT | FAIL

PHASE_5 = PASS | BLOCKED_ENVIRONMENT | FAIL
PHASE_6 = PASS | BLOCKED_ENVIRONMENT | FAIL
ROADMAP = PASS | BLOCKED_ENVIRONMENT | FAIL

behavioral_mutations_required = 8
behavioral_mutations_applied = 8
detecting_tests_executed = 8
behavioral_mutations_detected = 8
undetected_mutations = 0
false_positive_noop_mutations = 0

critical_contracts = <count>
critical_contracts_with_semantic_conformance = <count>
symbol_only_contract_tests_counted = 0
invalid_contract_test_evidence = 0

compatibility_multiline_break_test = PASS
external_dependency_false_negative_regression = PASS

feature_admission_self_tests = <count>
scalability_self_tests = <count>

architecture_guard = PASS
dependency_gate = PASS
persistence_guard = PASS
contract_compatibility_guard = PASS
feature_scalability_admission = PASS
maintainability_testability = PASS
technical_debt_ratchet = PASS
differential_quality = PASS
kotlin_quality = PASS
documentation = PASS
design_system = PASS

focused_tests = PASS | BLOCKED_ENVIRONMENT
full_unit_tests = PASS | BLOCKED_ENVIRONMENT
detekt = PASS | BLOCKED_ENVIRONMENT
lint = PASS | BLOCKED_ENVIRONMENT
assemble_debug = PASS | BLOCKED_ENVIRONMENT
source_of_truth_admission = PASS | BLOCKED_ENVIRONMENT
```

No roadmap completion statement is allowed while the mutation runner can report detection without executing a failing test, or while symbol-only contract tests can satisfy semantic conformance.
