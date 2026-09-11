# Session 352 — Home data/source synchronization repair

Source of truth: `Verto-v351-invoice-adjustments-referrals.zip`

## Scope
Session 352 changes data ownership/synchronization only. It intentionally does **not** implement the Home/Drawer layout, pending-actions UI, idea catcher, educational-card placement, or typography planned for 353/354.

## Implemented
- User name now converges from `app_users.name` into the canonical `SessionReader.userName` cache when the authenticated profile is fetched.
- Editing the admin name is server-first; local session state changes only after the remote update succeeds.
- Removed the Home-specific `ownerName` shadow write that used a second preference key.
- Organization settings now have an explicit Room `is_dirty` marker (schema 86, migration 85→86).
- Organization sync pushes only locally edited settings; a clean/stale device no longer uploads before a pull.
- A pending local organization edit cannot be overwritten by remote pull; retry is serialized and clean marking is compare-by-`updated_at`.
- Unified remote organization materialization is always stored clean.
- Educational-content target encoding now matches the unified pull decoder (`|` between targets, U+001F between type/value).

## Verification
- `python tools/verify_session_352.py` → PASS 12/12.
- Gradle compile was attempted with `./gradlew --offline :app:compileDebugKotlin` but the wrapper tried to download Gradle 8.9 and the environment has no network access. Build status: `NOT_RUN_ENVIRONMENT_UNAVAILABLE`.

## Acceptance boundary
352 is complete statically. UI behavior remains unchanged except that Home reads the corrected canonical username source. Continue UI work in 353/354 only.
