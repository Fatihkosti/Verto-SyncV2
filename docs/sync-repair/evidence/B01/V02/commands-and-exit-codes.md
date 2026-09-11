# B01-V02 commands and exit codes

| Command/purpose | Exit | Observed result |
|---|---:|---|
| Validate all P01-P24 paths with `test -f` | 1 | 23/24 present; P04 `LegacySyncV2IntentRepairCoordinator.kt` absent. |
| Search Kotlin sources for the absent filename/class | 0 | No alternate file or symbol found. |
| Validate source-packager required paths | 0 | All required paths present. |
| `sha256sum VERTO_SYNC_REPAIR_EXECUTION_CONTRACT_AR.md` | 0 | `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`. |

The source designation itself came from the user and is recorded verbatim in
meaning, not inferred from these structural checks. No product build or Txx test
was run.
