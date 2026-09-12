# B08-V02 commands and results

| Check | Actual result |
|---|---|
| Branch / accepted product | `b07-b08-release-20260912` / `ad9c2887a33fafb3de45abd7a7cd58e1a710c63d` |
| Delta | PASS; 88 changes in four complete groups with opaque cursor |
| Bootstrap | PASS; 1,179 sealed rows paged 1000 + 179, 35-type coverage, digest, high watermark, matching Delta token |
| Soft limit | PASS; legal first group of 1001 remained atomic |
| Byte limit | PASS at 2,097,152 canonical UTF-8 bytes; explicit rejection at 2,097,153 |
| Three legal DTOs | PASS for INVENTORY_MOVEMENT, INVENTORY_COST_REVISION, CLIENT_CREDIT through accepted-record aggregate/Delta and production Room receiver |
| Authority | PASS; server sequence/timestamps preserved; replay changes only server-authority fields after immutable equality |
| Scope/protocol | PASS; cross-tenant, anonymous, and V1 contract calls blocked |
| JVM / Android compile | PASS |
| Focused production Room gate | PASS; B07/B08 selection ran 8/8 tests |
| Release build | PASS; minified APK produced by Actions |

GitHub Actions: https://github.com/Fatihkosti/Verto-SyncV2/actions/runs/34695836595

Global T19/T20 and two-device acceptance remain at their exact tracker status because this component gate does not replace those broader scenarios.

The full regression also ran: app 3/3, design system 16/16, operations 2/2, sync 61/61, auth 3/3, inventory 4/4, and shipment 6/6 passed. Database executed 49 tests (one skipped) with nine absent-historical-schema failures and one unrelated test-runner initialization failure; no missing schema was fabricated.
