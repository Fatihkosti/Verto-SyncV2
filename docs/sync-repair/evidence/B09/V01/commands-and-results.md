# B09-V01 commands, observations and acceptance handoff

All paths are relative to the supplied project. Commands below were local. No live SQL, device data or release build was performed.

## Executed

| Command | Exit/result | Evidence |
|---|---|---|
| `bash gradlew --offline --no-daemon :data:sync:testDebugUnitTest` | 1; wrapper could not retrieve uncached Gradle8.9; UnknownHostException. No Kotlin tasks started. | `gradle-offline.log` |
| `python tools/test_sync_b09_kotlin_smoke.py` | 0; 347 REAL mapping/Money assertions; selected production-source signatures compile with explicit temporary collaborators | `kotlin-smoke.log` |
| `python tools/test_sync_b09_materializer.py --output docs/sync-repair/evidence/B09/V01` | 0; 612 source/schema SQLite checks; 25 prepared queries; 13 transaction cuts | `sqlite-static.log`, `sqlite-static-results.json`, `sqlite-model-dump.sql` |
| `python tools/test_sync_contract_v2_schema.py` | 0; existing B06 schema gate | `b06-schema-regression.log` |
| `python tools/test_sync_b06_producers.py` | 0; existing B06 producer wiring gate | `b06-producers-regression.log` |

The smoke script generates signature-only dependency stubs in a temporary directory and never adds them to Android sources. It executes the real mapper, Money and normalizer; it does NOT exercise serialization, Room transactions, Hilt or Gradle. The SQLite script loads every actual Room98 exported table/index/view and executes actual DAO SQL, but its DTO projection is a Python model. These results cannot close G-B09 or Txx.

## Required next run — NOT_RUN here

```bash
# Run on the final source tree with Gradle/SDK/dependencies and a connected device/emulator.
bash gradlew --no-daemon :data:sync:testDebugUnitTest   --tests com.verto.app.data.sync.pull.FinancialMaterializationContractV2Test
bash gradlew --no-daemon :data:sync:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.pull.FinancialMaterializerV2InstrumentedTest
bash gradlew --no-daemon :app:compileDebugKotlin
# Recheck touched historical paths (B04 protection/B05 cursor/B06 factory mapping).
bash gradlew --no-daemon :data:network:testDebugUnitTest :data:database:testDebugUnitTest   :data:sync:testDebugUnitTest :data:operations:testDebugUnitTest
bash gradlew --no-daemon :data:database:connectedDebugAndroidTest :data:operations:connectedDebugAndroidTest
```

Nine JVM and 31 Room methods are supplied. The Room test uses `Room.inMemoryDatabaseBuilder(AppDatabase)` and the actual materializer and Pull Engine; it is not a mocked DAO test. One method injects failure at 13 real SQL boundaries. Run summaries/XML/device logs and post-transaction dumps must be attached before accepting G-B09. Fault-injection exceptions do not replace a genuine process-kill/two-device T26/T31 test; those full Txx remain pending.

The standalone contract SHA-256 and embedded contract SHA-256 remain `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`. Historical B04/B05/B06 PASS evidence is preserved, not reissued for this changed tree. The current checkpoint is BLOCKED until runtime acceptance succeeds.
