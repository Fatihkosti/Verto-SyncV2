# B08-V02 — final legal read / Delta / Bootstrap acceptance

Date: 2026-09-12  
Branch: `b07-b08-release-20260912`  
Product commit under acceptance: `ad9c2887a33fafb3de45abd7a7cd58e1a710c63d`

## Result

`G-B08: PASS` for the declared B08 gate.

- Aggregate/Delta responses are built from accepted records and return authoritative revision, sequence, recorded time, and server-accepted time rather than client-clock or zero fallbacks.
- Delta returns complete transaction groups with touched keys, dependencies, opaque scope-bound cursors, and visibility filtering.
- Bootstrap persists a stable server snapshot sealed by high watermark, Delta token, row count, digest, and exact aggregate coverage. Page limits are 1000 rows / 2 MiB.
- CLIENT_CREDIT is decoded from `amountMinor`; no forbidden `amount` field is required. Inventory movement/cost receivers require legal V2 fields and authoritative ordering.
- Room replay may update only server-authority fields after exact immutable-content equality. A mismatch fails closed; ACK no longer invents `serverAcceptedAt` from the device clock.

## PostgreSQL evidence

- Delta: 88 changes in four complete groups from an opaque cursor.
- Bootstrap: 1,179 sealed rows, exact 35-type coverage, 64-hex digest, high watermark and matching Delta token; pages were 1000 + 179 and ended complete.
- Legal first group of 1001 was returned atomically despite soft limit 1000.
- Canonical group size assertions passed at exactly 2,097,152 bytes and failed explicitly at 2,097,153 bytes.
- INVENTORY_MOVEMENT, INVENTORY_COST_REVISION, and CLIENT_CREDIT survived aggregate read + Delta with legal payload keys, Minor values, and server ordering/timestamps.
- Cross-tenant, anonymous, and V1-scope calls were blocked.

## Room/JVM evidence

- Production receiver test covers legal payload → decode → real Room for the three B08 types and rejects missing Minor/server ordering without a write.
- `./gradlew --stacktrace testDebugUnitTest`: PASS.
- All eight Android suites were invoked. App 3/3, design system 16/16, operations 2/2, sync 61/61, auth 3/3, inventory 4/4, shipment 6/6, and the focused B07/B08 Room gate 8/8 passed. Database ran 49 tests (one skipped) with ten unrelated failures itemized in `commands-and-results.md`.
- GitHub Actions run: https://github.com/Fatihkosti/Verto-SyncV2/actions/runs/34695836595.

This closes the B08 server seal dependency for B13. Full two-device T19/T20 and unrelated B09/B10 materialization scenarios retain their exact tracker status unless their complete scenario is separately evidenced.
