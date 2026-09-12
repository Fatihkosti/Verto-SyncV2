# B07-V02 commands and results

All identifiers below are non-secret test/run identifiers. Payloads, bearer tokens, signing passwords, and organization/user identifiers are intentionally omitted.

| Check | Actual result |
|---|---|
| Branch / accepted product | `b07-b08-release-20260912` / `ad9c2887a33fafb3de45abd7a7cd58e1a710c63d` |
| Supabase health | PASS; connected project reported `ACTIVE_HEALTHY`; Actions Auth health request succeeded |
| Live definition/security inspection | PASS; required V2 RPCs present, `SECURITY DEFINER`, Auth-derived scope; receipt tables use RLS with direct anonymous/authenticated reads revoked |
| Concurrent batch acceptance | PASS; two independent sessions returned the same APPLIED response/hash |
| Replay | PASS; ten further submissions returned the same response |
| Durable cardinality | PASS; one batch receipt, one member receipt, one domain receipt, one change fact |
| Idempotency conflict | PASS; reused identity with changed body/base rejected without effect |
| Atomic rollback | PASS; valid member followed by invalid member produced no partial fact/receipt |
| Temporary harness cleanup | PASS; helper table/function absent after cleanup migration |
| JVM | PASS; `./gradlew --stacktrace testDebugUnitTest` |
| Android compile | PASS for app, design system, database, operations, sync, auth, inventory, shipment AndroidTest sources |
| Focused B07/B08 Room gate | PASS; 8/8 tests |
| Release build | PASS; minified `:app:assembleRelease` and artifact upload |

GitHub Actions: https://github.com/Fatihkosti/Verto-SyncV2/actions/runs/34695836595

The full Android regression was intentionally allowed to continue across modules so every available suite ran. App 3/3, design system 16/16, operations 2/2, sync 61/61, auth 3/3, inventory 4/4, shipment 6/6, and focused B07/B08 8/8 passed. Database executed 49 tests (one skipped): nine failed because source exports 56/67/73/74/75/82 are absent, and `InvoiceEditorDraftProcessDeathTest` had one runner-initialization failure. These ten unrelated failures are not converted to PASS, and missing historical schemas were not fabricated.
