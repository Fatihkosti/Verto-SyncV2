# Verto v356 — Sync operation order repair

## Source
- Base: `Verto-v355-notifications-repaired.zip`
- Reproduction evidence: `SYNC_ISSUE.md`

## Root cause
`OrganizationSyncParticipant` and `InventorySyncParticipant` both declared `SyncStage.PUSH` order `160`. Runtime validation rejected the whole plan before any server request.

## Repair
1. Added `SyncOperationSlot` as the single central registry for all sync stage/order allocations.
2. Kept inventory categories at `PUSH/160` and moved organization settings to semantic slot `PUSH_ORGANIZATION_SETTINGS = PUSH/170`.
3. Migrated every production `SyncOperation` contributor from raw stage/order numbers to central semantic slots.
4. Made the raw `SyncOperation(stage, order, ...)` constructor internal to `:data:sync`; feature modules must use a central slot.
5. Preserved runtime duplicate validation and improved diagnostics to include stage, order, participant and operation label.
6. Added regression tests for:
   - uniqueness of the complete central slot registry;
   - the exact organization/inventory collision;
   - runtime conflict diagnostics;
   - deterministic PUSH -> DELETE -> PULL execution.

## Verification
- Production sync slot references: 60
- Unique `(stage, order)` pairs: 60
- Duplicate pairs: 0
- Raw stage/order `SyncOperation` constructors in production participants: 0
- Core Kotlin syntax compilation with local stubs: PASS
- Gradle unit test execution: BLOCKED_ENVIRONMENT because Gradle 8.9 was not cached and network access to `services.gradle.org` is unavailable.

## Device acceptance still required
Install the new build and confirm through ADB that manual sync no longer emits `Duplicate sync operation order`, then verify server PUSH/DELETE/PULL traffic under a valid session/network.
