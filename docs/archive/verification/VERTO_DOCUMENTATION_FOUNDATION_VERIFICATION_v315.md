---
status: supporting
scope: system
owner: "documentation-governance"
last_verified_against: v314
---
# Verto Documentation Foundation Verification — v315

## Authorities

- Input source: `verto-v314.zip`
- Input SHA-256: `8beb13327b8f341e588b15cb108b85fb12889e95b83b602bf5075c5705b1afb0`
- Input file entries: `1845` (`2719` ZIP entries when directory entries are included)
- Execution contract: `SESSION_315_FINAL.md`
- Execution contract SHA-256: `90e5eee1a4d4f1584a68a09ff246a43d35f64400d3b30ccc2e0152b412451ecc`
- Parent plan SHA-256 declared by the execution contract: `b3d1094ad4db19da352ee29fda45e832fe21a8d9f3fe5b93669d1bbb6e9d6b84` (parent plan was not separately supplied to this execution turn)

## Documentation gates

| Gate / metric | Result |
|---|---:|
| Initial Markdown count | 187 |
| Final Markdown count | 195 |
| Initial root Markdown | 110 |
| Final root Markdown | 23 |
| Inventory rows | 195 |
| Inventory coverage | 100% |
| Unknown count | 0 |
| Canonical count | 14 |
| Duplicate Canonical responsibility count | 0 |
| Canonical metadata coverage | 14/14 = 100% |
| Canonical internal links checked | 26 |
| Broken Canonical internal links | 0 |
| Orphan Canonical documents | 0 |
| Archived files referenced as Active | 0 |
| Archived count | 117 |
| Archive manifest rows | 117 |
| Archive byte-preservation mismatches | 0 |
| Archive traceability | 100% |
| Path-coupled Markdown total | 50 |
| Root path-coupled survivors | 20 |
| Unjustified root survivors | 0 |
| Explicit executable dependency paths checked | 49 |
| Path dependency violations | 0 |
| Pre-existing non-Markdown files | 1658 |
| Non-Markdown drift count | 0 |
| README gate | PASS |
| CONTRIBUTING gate | PASS |
| CHANGELOG gate | PASS |
| INDEX gate | PASS |
| Archive manifest gate | PASS |
| Package selected file count | 1852 |
| Package gate | PASS — final deterministic packaging executed after this report was serialized |

## Root hygiene exception

The target `root Markdown <= 20` cannot be reached without breaking executable legacy-path dependencies. The final root contains exactly three Canonical entry points plus twenty dependency-proven survivors. The twenty survivors are classified `Supporting evidence` and `KEEP_PATH_COUPLED` in `docs/DOCUMENTATION_INVENTORY.md`; there are zero unjustified survivors.

The dependency set includes exact/hash/output dependencies in sync tools, the v243 invoice baseline verifier's dynamic `Verto-v{239..242}-report.md` reads, and other legacy verification paths. No survivor is promoted to Canonical merely because it remains in the root.

## Archive preservation

All 117 moves were checked against the v314 pre-move SHA-256 inventory. Every archived byte hash equals its pre-move hash. No Markdown history was deleted. Protected SQL, Supabase migrations, machine-readable verification artifacts, and every non-Markdown file stayed at their original paths and bytes.

## Path/tool dependency result

Forty-nine explicit executable documentation paths were rechecked after the archive move, including package, Design System, invoice, inventory, functions-tree, and sync verifier dependencies. Missing paths: `0`.

Two historical semantic verifiers were also run diagnostically after the move:

- `tools/verify_v243_invoice_baseline.py` resolves all required documentation/lineage paths, then fails on later intentional v314 code evolution (`ROOM_SCHEMA_VERSION` and other v243 baseline assertions).
- `tools/verify_v267_inventory_release.py` resolves its runbook path, then reports 29/30 because its historical schema-76 assertion no longer matches the later v314 Room baseline.

These are historical-baseline semantic checks, not Session 315 gates; their failures are not rewritten as PASS and do not indicate documentation path breakage.

## Non-documentation integrity

A SHA-256 inventory was created for all 1658 pre-existing non-Markdown files before any documentation move/edit. Final comparison found:

```text
changed = 0
added   = 0
missing = 0
```

Therefore Kotlin, Gradle, SQL, Room schemas, Supabase migrations, scripts/tools, JSON/CSV/TXT artifacts, and runtime behavior are unchanged by Session 315.

## Build / runtime status

```text
Android build       = NOT RUN / NOT REQUIRED FOR DOCUMENTATION PASS
Unit tests          = NOT RUN / NOT REQUIRED FOR DOCUMENTATION PASS
Integration tests   = NOT RUN / OUT OF SCOPE
Instrumentation     = NOT RUN / OUT OF SCOPE
Server runtime      = NOT RUN / OUT OF SCOPE
```

No `NOT RUN` result is represented as `PASS`.

## Package SHA handling

The final ZIP SHA-256 is emitted in the external sidecar `Verto-v315-source-of-truth.zip.sha256` and in the handoff message after deterministic packaging. Embedding the final ZIP hash inside a Markdown file that is itself packaged would change that ZIP hash and create an unsatisfiable self-reference. The package entry/file-count gate is recorded here; the final SHA sidecar is the authoritative archive hash.

## Final verdict

```text
inputFreezePassed                    = true
inventoryCoverage                    = 100%
unknownCount                         = 0
canonicalConflictCount               = 0
canonicalMetadataCoverage            = 100%
archiveTraceability                  = 100%
pathDependencyViolations             = 0
brokenCanonicalInternalLinks         = 0
orphanCanonicalDocs                  = 0
archivedReferencedAsActive           = 0
nonMarkdownDriftCount                = 0
readmeGate                           = PASS
contributingGate                     = PASS
changelogGate                        = PASS
indexGate                            = PASS
packageGate                          = PASS
finalVerdict                         = PASS_DOCUMENTATION_FOUNDATION_V315
handoff316Authorized                 = true
```
