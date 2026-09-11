# B03-V01 commands and results

| Command/check | Result | Evidence |
|---|---|---|
| `ANDROID_HOME=... ./gradlew --no-daemon :data:database:testDebugUnitTest :data:database:kspDebugKotlin` | PASS | Build successful; 27 JVM tests, zero failures/errors; schema 97 generated. |
| Focused `connectedDebugAndroidTest` for `Migration96To97SyncRepairInstrumentedTest` | PASS | Two tests: non-empty 96→97 preservation/conversion and non-finite money fail-closed. |
| Focused `connectedDebugAndroidTest` for `SyncRepairV2DaoInstrumentedTest` | PASS | Three tests: observed/applied separation, stored local sequence, complete zero-based batch. |
| Combined focused run of both classes | PASS | `Starting 5 tests`; `Finished 5 tests`; `BUILD SUCCESSFUL in 39s`. |
| Generated Android XML | PASS | `tests=5 failures=0 errors=0 skipped=0`; includes all five test cases. |
| First build without `ANDROID_HOME` | EXPECTED ENVIRONMENT FAILURE | SDK location was not configured in the shell; rerun with the existing SDK passed. |
| One combined run after intentionally stopping the AVD | EXPECTED HARNESS FAILURE | `No connected devices`; the AVD was restarted and the same combined command then passed. |
| Supabase/server mutations | NONE | B03 is local-only; paid branch was skipped per user instruction. |

Android result SHA-256: `626846c5b3632f57bf7c7e01c49d47269e8ecd3cf27b58f41b9a3f463a15490e`.

