# B06-V01 commands and observed results

All commands ran on 2026-09-10 from the accepted workspace in `NO_GIT_TREE_HASH_MODE`. `ANDROID_HOME=/home/aboalftooh/Android/Sdk` was supplied to Gradle. No command contacted Supabase or modified live/user data.

| Command | Exit | Result |
|---|---:|---|
| embedded-contract `awk ... | sha256sum` | 0 | `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`, matches the declared contract |
| `python3 -m json.tool SYNC_CONTRACT_V2.schema.json` | 0 | JSON syntax PASS |
| `python3 tools/test_sync_contract_v2_schema.py` | 0 | `B06_SCHEMA_GATE=PASS defs=40 fullFinancialLists=10` |
| `python3 tools/test_sync_b06_producers.py` | 0 | `B06_PRODUCER_GATE=PASS financial=full owner310=frozen inventoryPackets=2` |
| `./gradlew --no-daemon :data:network:testDebugUnitTest :data:sync:testDebugUnitTest :feature:payment:testDebugUnitTest :data:operations:testDebugUnitTest :feature:inventory:compileDebugKotlin :feature:invoice:compileDebugKotlin :app:compileDebugKotlin` | 0 | BUILD SUCCESSFUL; network 56/56, sync 70/70, payment 27/27, operations 42/42; main/app compilation PASS |
| `./gradlew --no-daemon :data:operations:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.operations.transaction.FinancialOutboxWriterV2InstrumentedTest` | 0 | 2/2 PASS on Pixel_8 AVD Android 17; atomic commit and forced rollback of domain/snapshot/packet/generation/reference/batch rows |
| `./gradlew --no-daemon :feature:invoice:compileDebugUnitTestKotlin` | 1 | Existing test-source classpath failure: unresolved `kotlin.test` in `InstallmentEffectiveDue370Test` and Android `MockContext` in `FinancialPendingActionProvider337Test`; production `:feature:invoice:compileDebugKotlin` passed |
| original B05 product-scope fingerprint command | 0 | `f792281a150f400ba5a08be7b22bf171202850d4128a5eb1958b86182fb22653`, 709 files after B06 within the prior app/data-only scope |
| expanded B06 product-scope fingerprint command | 0 | `6b69c2c5c37ae6e3ceb313d24e97a45f0f1d253d6e94cbbeda8e72b19fb45090`, 909 files including schema/tools, Room test, and invoice/payment/inventory feature sources |

The earlier exploratory all-suite invocation reached the same pre-existing invoice test-source compilation failure after network/sync/payment passed. It did not expose a B06 test assertion failure. The final gate therefore records the four directly affected JVM suites and whole production compile separately and does not claim the invoice unit suite.

T09, T12, T19, and T20 remain `NOT_RUN`: these local tests are precursors and do not substitute for the contract's PostgreSQL, authoritative Room, or device scenarios.
