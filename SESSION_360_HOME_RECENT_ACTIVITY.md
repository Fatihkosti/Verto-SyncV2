# SESSION 360 — Home Recent Activity

**Source of truth:** `Verto-v359-home-capture-education.zip`  
**Scope:** Home recent activity only; no unapproved visual redesign.

## Implemented

- Preserved the existing **7-day** activity window.
- Changed the Home activity cap from **100 → 30 events**; ranking remains newest first.
- The final Home card now displays **up to 5 events** before internal vertical scrolling.
- Each visible row is **48dp** minimum touch height.
- Short feeds shrink to their actual row count instead of showing empty space for five rows.
- Added bottom spacing so the final activity rows are not covered by the Home FAB.
- Preserved Home order: pending actions → information capture → education → recent activity.
- Added an explicit regression assertion for the 30-event cap.

## Deliberately not changed

- No new colors, card style, typography system, or full-screen visual redesign was invented; that remains pending the approved visual reference.
- No Room migration.
- No Supabase migration.
- No changes to activity destinations, permission checks, providers, or synchronization semantics.

## Verification

- `python3 tools/quality/session360_verify.py` → **10/10 PASS**.
- Gradle test command attempted:
  `./gradlew :feature:dashboard:testDebugUnitTest :app:testDebugUnitTest --offline --build-cache`
- Result: **BLOCKED_ENVIRONMENT** — Gradle 8.9 is not cached locally and `services.gradle.org` cannot be resolved from this environment.

## Files changed for 360

- `app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeed.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/home/HomeDesignTokens.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt`
- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/activityevent/ObserveActivityEventsUseCase.kt`
- `feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/activityevent/ActivityEventHardening339Test.kt`
- `tools/quality/session360_verify.py`
- `CHANGELOG.md`

**SESSION_360_STATIC = PASS**  
**SESSION_360_BUILD = BLOCKED_ENVIRONMENT**
