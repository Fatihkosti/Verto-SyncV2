---
status: supporting
scope: system
owner: "documentation-governance"
last_verified_against: v315
---
# Verto Documentation Content Verification — v316

## Authorities

- Input source: `Verto-v315-source-of-truth.zip`
- Input SHA-256: `85fef440abeaef275f10c2905fa6a28ef0ff4f4205856370bd457d16db69ab44`
- Execution contract: `SESSION_316_FINAL.md` (supplied externally to this execution turn)
- Parent plan SHA-256 declared by the execution contract: `b3d1094ad4db19da352ee29fda45e832fe21a8d9f3fe5b93669d1bbb6e9d6b84`.
- The parent plan file was not separately supplied in this turn; this matches the v315 verification precedent and no independent parent-plan hash is claimed.

## Start baseline

```text
files                       = 1852
Markdown                    = 194
production Kotlin           = 1194
Gradle Android modules      = 31
Room schema                 = 81
Supabase Kotlin BOM         = 3.0.2
WorkManager                 = 2.10.0
handoff316Authorized        = true
```

## Content coverage

```text
architecture required docs          = 6/6
modules documented                  = 31/31
production RPC call sites           = 49
unique RPC names                    = 48
RPCs documented                     = 48/48
RPCs with repository SQL definition = 21
RPCs without repository definition  = 27
RPCs explicitly runtime-verified    = 1
named direct PostgREST tables       = 71
Realtime surfaces                   = 2
Supabase Auth operation forms       = 9
production-critical feature docs    = 16/16
```

`RPCs explicitly runtime-verified = 1` is intentionally conservative: retained `Verto-v314-SQL-deployment-report.md` explicitly proves live `verto_resolve_sync_scope` resolution. Successful application of containing migrations is not expanded into fabricated per-RPC invocation evidence.

## Integrity and governance gates

Final values are generated from the completed v316 tree:

```text
initial non-Markdown files          = 1658
changed non-Markdown                = 0
added non-Markdown                  = 0
missing non-Markdown                = 0
historical Markdown mutations       = 0
canonical metadata coverage         = 41/41
broken canonical internal links     = 0
orphan canonical docs               = 0
duplicate canonical responsibilities= 0
deprecated docs referenced as active= 0
Unknown documentation inventory     = 0
final Markdown                      = 223
final selected files                = 1881
```

## Server-definition truth

A production client RPC with no repository SQL definition is documented exactly as:

```text
CLIENT_VERIFIED = yes
REPOSITORY_SERVER_DEFINED = no
RUNTIME_VERIFIED = no
```

No absent server body, transaction, RLS rule, idempotency guarantee, error code or database dependency is invented. Direct PostgREST filters are documented as client scoping intent, not server authorization proof.

## Build / test / runtime status

```text
Android build       = NOT RUN / NOT REQUIRED FOR DOCUMENTATION CONTENT PASS
Unit tests          = NOT RUN / NOT REQUIRED FOR DOCUMENTATION CONTENT PASS
Integration tests   = NOT RUN
Instrumentation     = NOT RUN
Server runtime      = NOT RUN
```

The retained v314 SQL deployment report is evidence from that prior server execution; v316 itself did not execute server runtime.

## Final verdict

```text
inputFreezePassed                     = true
nonMarkdownDriftCount                 = 0
moduleCoverage                        = 31/31
architectureRequiredDocs              = complete
rpcDocumentationCoverage              = 100%
inventedRpcCount                      = 0
unsupportedServerClaimCount           = 0
featureCoverage                       = 16/16
canonicalMetadataCoverage             = 100%
brokenInternalLinks                   = 0
orphanCanonicalDocs                   = 0
duplicateCanonicalResponsibilities    = 0
deprecatedReferencedAsActive          = 0
unknownDocuments                      = 0
historicalMutationViolations          = 0
finalVerdict                          = PASS_DOCUMENTATION_CONTENT_V316
handoff317Authorized                  = true
```
