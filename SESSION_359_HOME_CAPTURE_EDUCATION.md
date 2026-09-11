# SESSION 359 — Home Capture & Education

## Source of truth

- Input: `Verto-v358-home-actions-pending.zip`
- Output: `Verto-v359-home-capture-education.zip`
- Scope: Home information capture + education only. Recent-activity behavior remains for v360.

## Implemented

### Information capture

- Replaced the single generic capture state with three explicit categories:
  - `IDEA` — فكرة
  - `MARKET_INFO` — معلومة سوق
  - `COMPLAINT` — شكوى
- Categories are horizontally swipeable.
- Idle category auto-rotation is exactly 5 minutes.
- Auto-rotation pauses once the user starts typing, preventing a category change while composing.
- Removed the text-field placeholder.
- Submit remains visually prominent while idle; blank submission is rejected with the existing user-facing validation path.
- Successful submission shows: `شكراً، وصلت المعلومة`.
- Home order is now: pending actions → capture → education → recent activity.

### Category persistence

Category is persisted end-to-end rather than being UI-only:

`Home UI → HomeViewModel → TeamObservationRepository → Room → sync DTO → Supabase → manager review UI`

- Added `TeamObservationCategory` to the Dashboard API contract.
- Added Room `category` column and migration `87→88`, defaulting historical rows to `IDEA`.
- Added instrumentation migration test `TeamObservationCategoryMigration359Test`.
- Added category to Supabase payload mapping and manager observation cards.

### Supabase

Applied and verified on the live `Verto-app` project:

- Migration: `v359_team_observation_categories`
- Added `public.team_observations.category text not null default 'IDEA'`.
- Added constraint allowing only `IDEA`, `MARKET_INFO`, `COMPLAINT`.
- Local SQL artifact retained at `docs/sql/v359_team_observation_categories.sql`.

### Education

- Home card continues to show the short summary and `اقرأ المزيد`.
- Detail view no longer repeats the summary.
- Detail opens with topic title followed directly by full content.

## Verification

### Session 359 static gate

`PASS`

Evidence: `docs/architecture/verification/SESSION_359_STATIC_GATE.json`.

### Room persistence guard

The new `87→88` migration is declared, registered, and has migration-test evidence.

The repository-wide persistence guard remains `FAIL` for two independent reasons:

1. Inherited from the v358 source: migrations `83→84`, `84→85`, `85→86`, `86→87` are registered but do not have the migration-test evidence required by the current guard.
2. The Room compiler-generated `88.json` schema export cannot be generated in this environment because Gradle 8.9 is unavailable.

No manual `88.json` was fabricated because the guard explicitly requires compiler-generated Room schema evidence.

Evidence: `docs/architecture/verification/SESSION_359_PERSISTENCE_SCHEMA.json`.

### Gradle

Command attempted:

`./gradlew :app:compileDebugKotlin --offline --build-cache`

Result: `BLOCKED_ENVIRONMENT` before compilation. The Gradle wrapper attempted to resolve `gradle-8.9-bin.zip`, but the distribution is not cached locally and this environment cannot resolve `services.gradle.org` (`UnknownHostException`).

Therefore no compile/build PASS is claimed.

Evidence:
- `docs/architecture/verification/SESSION_359_GRADLE.log`
- `docs/architecture/verification/SESSION_359_GRADLE_EXIT_CODE.txt`

## Scope discipline

- v358 quick-action and pending-action behavior was preserved.
- Recent-activity behavior/filtering was not changed; it remains v360 scope.
- No unrelated server-advisor findings were modified.
