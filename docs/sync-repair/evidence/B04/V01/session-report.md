# B04-V01 session report

## Outcome

G-B04 is PASS for its local declared scope. All 35 aggregate types have one explicit owner decision, specialized queues remain authoritative, and unknown owners fail closed with `BLOCKED_OWNER_UNPROVEN`. `SyncPendingProtection` is the single pending decision used by Pull, Bootstrap staging, M03 validation, health, logout, and organization switching.

## Implemented

- Added `SyncOwnershipRegistry` with exact financial, Party-role, stock, cost, Optimal, attachment, owner310, and server-only ownership.
- Added transaction-bound pending-reference capture and terminal-only release with generation, content hash, dependency kind, and content keys.
- Added DAO state resolution and unresolved counts for every owner. Party rows without organization evidence cannot be adopted.
- Replaced Pull's custom protection map, Bootstrap's row guard, M03's migrated-candidate check, and health/logout checks with the central service.
- Disabled Bootstrap's old blind absence-prune path until B13 supplies content-aware enumeration; this preserves local rows rather than treating an empty/partial snapshot as delete permission.
- Derived recovery owner selection from the central registry.
- Repaired three immutable `amountMinor` assignments revealed by whole-app compilation after B03; the same Minor values are now passed through `copy`/constructor parameters.

## Verification and limits

- JVM: 91 tests passed (64 sync + 27 database).
- Android/Room: 5 focused tests passed on a local Pixel_8 AVD running Android 17.
- Whole app `compileDebugKotlin`: PASS.
- T10 and T33 remain `NOT_RUN`: the tests here cover B04's local protection invariants, not full M03 repair or a real Bootstrap across every owner/attachment.
- No Supabase branch, SQL execution, live data access, production deployment, release build, or user-device change occurred.
- B02-BLK-01 and B02-BLK-02 remain active but do not invalidate B04's local gate under C§4.3.
- The workspace has no `.git`; the deterministic product fingerprint and artifact hashes replace a commit reference for this checkpoint.

Next authorized session: B05, beginning at B05.01. Do not rerun B04 unless its product scope changes or evidence becomes stale.
