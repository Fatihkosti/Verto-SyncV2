---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v336
---
# Session 336 Cash Convergence Evidence

Implementation:

- `sameCashMovementIntent336()` compares immutable intent only.
- `canonicalAuthoritativeCashMovement336()` derives Double compatibility columns from minor units.
- `reconcileMovementAuthoritativeProjection()` updates only movement projection columns.
- `applyCashMovement()` rejects semantic drift but accepts server causal projection drift.
- Cash movement reconciliation never writes the `cash_register` row.

Deterministic host-side behavioral verifier:

```text
A then B reorder                 PASS
B then A reorder                 PASS
3-device (-10,-20,+5 from 100)   PASS => final 75
same-device canonical no-op      PASS
remote-first canonical insert    PASS
amount/source/write/time drift   PASS => conflict
raw Double noise                 PASS => not identity
projection-only reconcile        PASS
register isolation               PASS
```

`tools/quality/verto_finance_v336.py` executed **34/34 behavioral cases PASS**. Its executable behavioral mutation policies altered reconciliation/dependency behavior; **M1-M14 were each DETECTED by at least one required behavior**.

Physical/emulated independent-device staging was not available in this environment:

```text
MULTI_DEVICE_BEHAVIORAL_SIMULATION = PASS
TWO_DEVICE_RUNTIME = BLOCKED_ENVIRONMENT
```
