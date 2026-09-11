# SOURCE_DIFF — B01-V01

Status: `RECORDED_PROVENANCE_VARIANCE_ACCEPTED_SOURCE_TREE`

The execution contract names `Verto-425.zip` with SHA-256
`cb0a1956952376d75bfeb2d3ec71b0e06700024d5b95cd44f6b09db96855e1a0`.
The archive was not found in the workspace, `/home/aboalftooh` (to depth 6),
`/tmp`, or `/mnt` (to depth 6). Therefore no archive structure inspection,
checksum comparison, extraction, or file-by-file archive comparison was possible.

On the continuation of B01, the user explicitly clarified that the current
project directory is the already-extracted `Verto-425.zip` source. That
clarification is the source-selection authority for execution. It does not turn
the unavailable ZIP-container checksum into a computed or matching checksum.

The existing workspace is a non-Git tree. Its pre-execution deterministic tree
fingerprint in `SOURCE_BASELINE.json` is therefore the accepted baseline
identifier. No Git commit is invented.

Structural verification found 23 of the 24 P01-P24 paths listed in the backlog.
The following referenced file is absent:

`data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2IntentRepairCoordinator.kt`

No class or symbol with that name was found elsewhere under the product source.
This is retained as a source-layout difference for the later implementation
session that owns the coordinator; it is not silently treated as existing.

`Verto-1.0.zip` is present but has SHA-256
`dded7236e60201d805a3063252e4a7e9e47cf886d9be9750b210396f431617e6`.
It was not substituted for the named archive.

If the original ZIP container is supplied later, its expected SHA-256 can be
verified as additional provenance evidence. It must not be extracted over the
accepted repair tree.
