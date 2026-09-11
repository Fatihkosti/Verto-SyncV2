# B04-V01 commands and results

Execution date: 2026-09-10 (Africa/Khartoum). Source mode: `NO_GIT_TREE_HASH_MODE`. No server, production, or user-device write was performed.

| Command | Result |
|---|---|
| Contract SHA-256 extraction/verification | PASS: `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`. |
| `./gradlew --no-daemon :data:sync:testDebugUnitTest :data:database:testDebugUnitTest` | PASS: sync 64/64; database 27/27; zero failures/errors/skips. |
| `./gradlew --no-daemon :data:sync:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.ownership.SyncPendingProtectionInstrumentedTest,com.verto.app.data.sync.migration.LegacySyncV2MigrationResumeInstrumentedTest` | PASS on Pixel_8 AVD / Android 17: 5/5; zero failures/errors/skips. |
| `./gradlew --no-daemon :data:sync:compileDebugAndroidTestKotlin` | PASS. |
| `./gradlew --no-daemon :app:compileDebugKotlin` | PASS after replacing three invalid post-B03 assignments to immutable `amountMinor` with constructor/copy values. Warnings only. |
| Ownership set comparison | PASS: exactly 35 unique records; keys equal `UnifiedSyncAggregateRegistry.byId.keys`; six specialized local owners plus server-only notification are explicit. |
| Product fingerprint (`app/schemas`, `app/src`, `data/database/src`, `data/operations/src`, `data/sync/src`) | PASS: 612 files; SHA-256 `a600f4e0386d3ea6f240cd50ad2efb999ff960a7e96425d8448156ae52309013`. |

The first combined app compile attempt exposed two immutable-field assignments in `ExpenseRepository`; the next app compile exposed the same issue in `ExpensesOperationsAdapter`. These were build regressions from the schema-97 fixed-point conversion, were repaired without changing money values, and the final app compile passed.

The Android XML result is at `data/sync/build/outputs/androidTest-results/connected/debug/TEST-Pixel_8(AVD) - 17-_data_sync-.xml`. JVM XML results are under each module's `build/test-results/testDebugUnitTest` directory. These build outputs are local and reproducible; the durable assertions and run summary are recorded here.
