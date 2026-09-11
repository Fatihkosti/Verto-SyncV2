# B05-V01 commands and results

Execution date: 2026-09-10 (Africa/Khartoum). Source mode: `NO_GIT_TREE_HASH_MODE`. No server, production, or user-device read/write was performed.

| Command | Result |
|---|---|
| Embedded contract SHA-256 extraction/verification | PASS: `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`. |
| Product fingerprint before B05 over 612 B04-scope files | PASS: `a600f4e0386d3ea6f240cd50ad2efb999ff960a7e96425d8448156ae52309013`. |
| `ANDROID_HOME=/home/aboalftooh/Android/Sdk ./gradlew --no-daemon :data:database:compileDebugKotlin :data:sync:compileDebugKotlin :data:network:compileDebugKotlin` | PASS. The first attempt without `ANDROID_HOME` failed only because the SDK path was unavailable to Gradle; rerun with the explicit local SDK passed. |
| `ANDROID_HOME=/home/aboalftooh/Android/Sdk ./gradlew --no-daemon :data:database:testDebugUnitTest :data:sync:testDebugUnitTest :app:compileDebugKotlin` | PASS: database 27/27 and sync 68/68, with zero failures/errors/skips; whole-app debug Kotlin compilation passed. |
| `ANDROID_HOME=/home/aboalftooh/Android/Sdk ./gradlew --no-daemon :data:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.local.Migration97To98FrozenLeaseInstrumentedTest,com.verto.app.data.local.SyncRepairV2DaoInstrumentedTest` | PASS on Pixel_8 AVD / Android 17: 4/4. |
| `ANDROID_HOME=/home/aboalftooh/Android/Sdk ./gradlew --no-daemon :data:sync:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.FrozenMutationStoreInstrumentedTest,com.verto.app.data.sync.ownership.SyncPendingProtectionInstrumentedTest` | PASS on Pixel_8 AVD / Android 17: 8/8. |
| Golden frozen-member assertion | PASS: exact UTF-8 text, no BOM, no `leaseToken`, SHA-256 `3c92c39cedace92bc86f0c76ef368958e2b552bcbc39adbdd13ad33677835b16`. |

One earlier combined connected-test command supplied both module class filters to the database APK. It failed test-runner initialization because sync-module test classes are not packaged in the database test APK. It was a command-scoping error, not an assertion/product failure; the two correctly scoped commands above then passed.

The local G-B05 evidence covers atomic rollback, one generation/reference per protected key, immutable retry bytes, offline predecessor ordering, receipt-derived base version, stale epoch/token CAS, request-hash mismatch, old-request fail-closed classification, sealed batch membership, and additive schema 97→98 preservation. T08/T15/T17/T23/T39 remain `NOT_RUN` because their full contract scenarios require later producer/server/repair integration.

