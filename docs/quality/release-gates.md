---
status: canonical
scope: system
owner: "release-governance"
last_verified_against: v318
---
# Release and Documentation Quality Gates

## Status vocabulary

| Status | Meaning | Source-of-Truth release |
|---|---|---|
| `PASS` | Required command executed successfully and success was observed | eligible if every other required gate also passes |
| `FAIL` | Required validation executed and found a defect, mismatch, or validator failure | blocked |
| `BLOCKED` | Required execution could not proceed because a prerequisite/environment was unavailable or input freeze failed | blocked |
| `NOT RUN` | Required/optional execution was not invoked | blocked whenever that gate is required |

Only observed successful execution may be `PASS`. A textual or verbal statement cannot convert `FAIL`, `BLOCKED`, or `NOT RUN` into `PASS`.

## Executable entry points

Standalone documentation validation:

```bash
./scripts/run-documentation-gate.sh
```

Integrated documentation validation:

```bash
scripts/ci/run-quality-gate.sh documentation <session>
```

Aggregate repository quality pipeline:

```bash
scripts/ci/run-quality-gate.sh all <session>
```

The aggregate order begins with Documentation Gate, then Design System, Design System diff, detekt, lint, unit tests, and debug build. Documentation failure is fail-closed and prevents every later aggregate stage from running.

## Source-of-Truth promotion rule

A new Source-of-Truth version must not be declared unless the mandatory Documentation Gate is observed as `PASS`. Any required `FAIL`, `BLOCKED`, or `NOT RUN` blocks promotion.

Documentation Gate integration itself is proven only by successfully executing the documentation path through `scripts/ci/run-quality-gate.sh`; a standalone success alone does not prove orchestrator integration.
