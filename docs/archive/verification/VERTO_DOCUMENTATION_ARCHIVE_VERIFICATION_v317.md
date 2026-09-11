---
status: supporting
scope: system
owner: "documentation-governance"
last_verified_against: v316
---
# Verto Documentation Archive & Deduplication Verification — v317

## Authorities and input freeze

- Input source: `Verto-v316-source-of-truth.zip`
- Input SHA-256: `cba9e5c26aaedbdaa35fa124a39f62393c602cff2ff83d26db3fd838d6fce59e` — independently recomputed and matched the Session 317 contract.
- Parent plan: `VERTO_DOCUMENTATION_MODERNIZATION_PLAN_v315-v322.md`.
- Parent plan SHA-256 declared by the Session 317 contract: `7b098c41cdc4332c9136cd2b94d098dfdcd13e04f360fac9e6077452a94b534e`.
- The parent-plan file is an external authority and is not packaged in v316; its raw-byte SHA is therefore not re-computed inside this repository execution. The plan identity/content and Session 317 scope were independently located in the supplied File Library; no contradictory plan was used.
- v316 verification verdict: `PASS_DOCUMENTATION_CONTENT_V316`.
- `handoff317Authorized = true` was present in the retained v316 verification artifact.

Start baseline:

```text
files                         = 1881
Markdown                      = 223
non-Markdown                  = 1658
root Markdown                 = 23
archive category Markdown     = 120
archive Markdown + manifest   = 121
handoff317Authorized          = true
```

`archive category Markdown` is the sum of `sessions=10`, `reports=77`, `verification=28`, `superseded-contracts=5`; the additional archive Markdown is `docs/archive/ARCHIVE_MANIFEST.md` itself.

## Path-dependency audit

All 20 residual Historical/supporting root documents were revalidated. Every candidate still has a non-Markdown path dependency, so moving any of them would violate Session 317's non-Markdown freeze or break legacy evidence/tool contracts.

| Root survivor | Dependency proof | Decision |
|---|---|---|
| `SESSION_311_FINAL.md` | `tools/verify_sync_realtime_v312.py` | KEEP_PATH_COUPLED |
| `SESSION_312_FINAL.md` | `tools/verify_sync_recovery_v313.py` | KEEP_PATH_COUPLED |
| `SESSION_313_FINAL.md` | `tools/verify_sync_cutover_v314.py` | KEEP_PATH_COUPLED |
| `SESSION_314_FINAL.md` | `tools/verify_sync_cutover_v314.py` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_CONTRACT_VERIFICATION_v304.md` | `tools/verify_sync_server_v305.py`; `docs/sync/VERTO_SYNC_307_INPUT_MANIFEST.json` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_CUTOVER_VERIFICATION_v314.md` | `tools/verify_sync_cutover_v314.py` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_PULL_VERIFICATION_v308.md` | `tools/verify_sync_pull_v308.py` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_PUSH_VERIFICATION_v309.md` | `tools/verify_sync_push_v309.py` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_RECOVERY_VERIFICATION_v313.md` | `VERTO_SYNC_CUTOVER_VERIFICATION_v314.json`; recovery/cutover verifiers | KEEP_PATH_COUPLED |
| `VERTO_SYNC_ROOM_VERIFICATION_v306.md` | `VERTO_SYNC_ROOM_VERIFICATION_v306.json`; room verifier; v307 manifest | KEEP_PATH_COUPLED |
| `VERTO_SYNC_RUNTIME_EVIDENCE_v314.md` | `tools/verify_sync_cutover_v314.py` | KEEP_PATH_COUPLED |
| `VERTO_SYNC_STRONGER_VERIFICATION_v310.md` | `tools/verify_sync_stronger_v310.py` | KEEP_PATH_COUPLED |
| `Verto-v239-report.md` | v307 manifest; `docs/logistics/v239/V239_SESSION_ALLOWLIST.txt` | KEEP_PATH_COUPLED |
| `Verto-v240-report.md` | v307 manifest; `docs/logistics/v240/V240_SESSION_ALLOWLIST.txt` | KEEP_PATH_COUPLED |
| `Verto-v241-report.md` | `docs/sync/VERTO_SYNC_307_INPUT_MANIFEST.json` | KEEP_PATH_COUPLED |
| `Verto-v242-report.md` | v307 manifest; `docs/logistics/v242/V242_SESSION_ALLOWLIST.txt` | KEEP_PATH_COUPLED |
| `Verto-v306-report.md` | room JSON/verifier; v307 manifest | KEEP_PATH_COUPLED |
| `Verto-v308-report.md` | `tools/verify_sync_pull_v308.py` | KEEP_PATH_COUPLED |
| `Verto-v309-report.md` | `tools/verify_sync_push_v309.py` | KEEP_PATH_COUPLED |
| `verification-invoice-F243.md` | `tools/verify_v243_invoice_baseline.py`; v307 manifest | KEEP_PATH_COUPLED |

```text
root move candidates audited = 20
moved                       = 0
blocked/path-coupled        = 20
unjustified root Historical = 0
path dependency violations  = 0
```

## Duplicate audit and authority resolution

Exact-byte scan found one group and only one group:

```text
SHA-256 = 57ca0f1179cac2fa92f3e06fb8f22b547965a955ac43cc517cbcc2cb159057da
primary = docs/archive/reports/Verto-v252-report.md
duplicate= docs/archive/verification/verification-invoice-F252.md
type    = EXACT_BYTE_DUPLICATE
retained= both paths
reason  = preserve independent historical provenance; Delete allowed = false
```

Current semantic/responsibility review found one compatibility alias requiring explicit authority:

```text
docs/design-system/DESIGN_SYSTEM_CONTRACT.md
  status    = Canonical
  authority = retained

docs/design-system/DESIGN-SYSTEM-CONTRACT.md
  type      = COMPATIBILITY_ALIAS / semantic duplicate
  status    = deprecated
  authority = none
  retained  = tools/verify_design_system_contract.py requires filename existence
```

`tools/verify_design_system_contract.py` checks existence of the hyphenated filename but does not read or hash its content. The deprecated file was therefore safely reduced to a compatibility-only pointer. The v307 JSON manifest also records the legacy path/hash, but the v307 verifier only applies manifest drift checks to `supabase/migrations/` and `functions/`; it does not content-verify this Design System Markdown.

Versioned session/report/baseline title families were reviewed as historical/versioned supporting evidence rather than competing Canonical responsibilities. No additional Canonical conflict was found.

```text
exact-byte duplicate groups          = 1
semantic compatibility aliases       = 1
duplicate Canonical responsibilities = 0
deprecated referenced as active      = 0
archive referenced as primary current= 0
```

## Archive and historical integrity

Session 317 performed no historical moves and no historical deletions. The existing Session 315 archive move table was reconciled and revalidated. One stale manifest-only row for `Verto-v314-build-report.md` was removed because that path was already absent from the v316 input (the v316 inventory had already documented this pre-existing inconsistency). All 116 real move records point to existing archived files and every listed pre-move SHA equals its archived SHA. Six documentation-session evidence files (v315–v317 report + verification pairs) are recorded separately as directly authored archive evidence, not historical moves.

```text
historicalDeletedCount       = 0
historicalMutationViolations = 0
archiveHashMismatches        = 0
new archive moves            = 0
existing move records valid  = 116/116
archiveManifestCoverage      = 100% (122/122 archive category documents represented)
```

The two new Session 317 evidence documents are newly authored supporting evidence, not historical moves.

## Inventory, Canonical map and links

Final registry and authority checks:

```text
final Markdown                        = 225
inventory rows                        = 225
inventoryCoverage                     = 100%
unknownDocuments                      = 0
canonical metadata coverage           = 41/41
orphanCanonicalDocs                   = 0
duplicateCanonicalResponsibilities    = 0
brokenInternalLinks                   = 0
deprecatedReferencedAsActive          = 0
archiveReferencedAsPrimaryCurrent     = 0
```

`docs/INDEX.md` required no path change because no Current primary path moved. `docs/CANONICAL_DOCUMENT_MAP.md` now records the hyphenated Design System filename only as a deprecated compatibility alias; it is not a Canonical row.

## Non-Markdown integrity

A SHA-256 baseline was captured for all 1,658 non-Markdown files before the first Session 317 edit and compared to the final tree.

```text
initial non-Markdown files = 1658
changed non-Markdown       = 0
added non-Markdown         = 0
missing non-Markdown       = 0
```

No Kotlin, Gradle, SQL, Room schema, Supabase migration, Python tool, shell script, JSON artifact, TXT allowlist, or runtime configuration was changed.

## Existing compatibility verification

The affected Design System verifier was executed after the compatibility-document change:

```text
python tools/verify_design_system_contract.py
DESIGN_SYSTEM_CONTRACT PASS 0
```

No path-coupled root document was moved; all required legacy root paths were also existence-checked after the final documentation edits.

## Build / test / runtime status

```text
Android build       = NOT RUN / NOT REQUIRED FOR DOCUMENTATION ARCHIVE PASS
Unit tests          = NOT RUN / NOT REQUIRED FOR DOCUMENTATION ARCHIVE PASS
Integration tests   = NOT RUN
Instrumentation     = NOT RUN
Server runtime      = NOT RUN
```

## Final metrics

```text
inputFreezePassed                    = true
historicalDeletedCount               = 0
historicalMutationViolations         = 0
archiveHashMismatches                = 0
pathDependencyViolations             = 0
unjustifiedRootHistoricalDocs        = 0
duplicateCanonicalResponsibilities   = 0
deprecatedReferencedAsActive         = 0
archiveReferencedAsPrimaryCurrent    = 0
brokenInternalLinks                  = 0
orphanCanonicalDocs                  = 0
unknownDocuments                     = 0
inventoryCoverage                    = 100%
archiveManifestCoverage              = 100%
changedNonMarkdown                   = 0
addedNonMarkdown                     = 0
missingNonMarkdown                   = 0
```

## Verdict

```text
PASS_DOCUMENTATION_ARCHIVE_V317
handoff318Authorized = true
```
