---
status: supporting
scope: system
owner: "architecture"
last_verified_against: v319
---
# Verto Technical-Debt Ratchet Policy

The v318 baseline is an adoption point, not a declaration that current debt is clean architecture.

```text
current debt is frozen as pre-existing debt
new debt is forbidden
existing debt may only decrease
```

## Mandatory semantics

- Numeric debt: `new_metric <= previous_admitted_metric`.
- Stable-identity debt: `new_identity_set ⊆ previous_admitted_identity_set`.
- A removed identity may not reappear.
- A count-only ratchet is insufficient when path/signature identity exists.
- Baselines may tighten; they may not loosen silently.

## Baseline changes

Any increase or exception requires owner, reason, impact, approval, remediation reference, and expiry when temporary. Architectural semantic changes additionally require an ADR. Zero-Tolerance rules are not routine exception candidates.

## Anti-gaming

Forbidden: broad module/path wildcards, deleting offending identities manually, raising thresholds to make a gate pass, replacing stable identities with counts to permit substitution, or relabeling legacy debt as `ALLOWED` without architectural justification.

## v320 measurements

Class size, constructor dependencies, cyclomatic/cognitive complexity and coupling remain `MEASUREMENT_REQUIRED_IN_320`. Session 319 does not invent numeric baselines for them.
