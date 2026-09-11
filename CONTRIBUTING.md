---
status: canonical
scope: system
owner: "repository-maintainers"
last_verified_against: v320
---
# Contributing to Verto

## Source of truth

Work from the explicitly accepted source package for the session. A historical report, session contract, or generated verification artifact does not override current code. Do not claim a build, test, runtime, server, or migration result that was not executed and observed.

This repository does not define a Git/branch workflow in the v314 baseline; do not invent one as a contribution requirement.

## Code changes

- Preserve module and ownership rules in `docs/architecture/ARCHITECTURE_CONTRACT.md`.
- New feature boundaries must satisfy `docs/architecture/FEATURE_ADMISSION_CONTRACT.md`.
- UI changes must satisfy the Design System and UX/UI quality contracts.
- Kotlin changes must satisfy the Kotlin quality and exception policies.
- Database, server, sync, financial, inventory, or migration changes require their own explicit execution scope and evidence.

## Documentation changes

- `README.md` points to the system; `docs/INDEX.md` maps it; a Canonical document defines a current responsibility; `docs/archive/` preserves history.
- Every current Canonical Markdown document must use the required frontmatter: `status`, `scope`, `owner`, and `last_verified_against`.
- Do not use `FINAL`, `LATEST`, `NEW`, or similar lifecycle words in new Canonical filenames.
- Do not promote a versioned historical plan/report to Canonical merely because its filename says `FINAL` or `SOURCE OF TRUTH`.
- Before moving a Markdown file, inspect `tools/`, `scripts/`, source/config files, and generated manifests for path, hash, allowlist, read, or write dependencies.
- Path-coupled historical evidence stays at its legacy path until the executable dependency is changed in an explicitly authorized session.
- Archive moves preserve content bytes when possible and must be recorded in `docs/archive/ARCHIVE_MANIFEST.md`.
- New or changed Canonical documentation must be reachable from `docs/INDEX.md` and represented in `docs/CANONICAL_DOCUMENT_MAP.md`.

Every implementation contract/change must record `Documentation Impact: NONE | REQUIRED`. `NONE` requires a non-empty reason. `REQUIRED` requires the affected Canonical documents. API/RPC, auth/authorization, errors, idempotency/retry, database/SQL, sync, architecture/module ownership, feature business rules, critical invariants, release/deployment, and recovery/rollback changes require documentation impact review.

A change with `Documentation Impact: REQUIRED` is incomplete until its Canonical documentation is updated and the Documentation Gate passes. Static D1–D9 checks cannot infer every semantic business-rule change; the explicit declaration is the governance control for that gap.

## Verification expectations

Use the verification appropriate to the change scope. For ordinary code work, the repository exposes quality gates through:

```bash
scripts/ci/run-quality-gate.sh <gate> <session>
```

For documentation changes, run `./scripts/run-documentation-gate.sh` and the integrated `scripts/ci/run-quality-gate.sh documentation <session>` path. The aggregate `all` gate is fail-closed and ordered: Change Contract, Architecture, Kotlin Ratchet, Documentation, Design System, Detekt, Lint, Tests, Debug Build, Source-of-Truth Admission. `NOT RUN` and `BLOCKED` remain observed statuses and prevent source-of-truth admission.

## Artifacts and handoff

- Use stable functional names for Canonical documentation.
- Generated reports and verification evidence are Supporting/Historical artifacts, not Canonical policy by default.
- Package source with `scripts/package-source.sh` when the session contract requires a source-of-truth ZIP. A `source-of-truth` filename is rejected unless a fresh PASS admission report matches the current canonical tree fingerprint.
- Record the package SHA-256 and the exact verification verdict in the session report.
