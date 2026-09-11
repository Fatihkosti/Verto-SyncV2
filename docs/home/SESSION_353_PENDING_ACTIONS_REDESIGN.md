# SESSION 353 — Pending Actions Redesign

Source of truth: `Verto-v352-data-sync-repaired.zip`

## Scope
- Keep Home ordering unchanged.
- Home shows one highest-ranked pending event.
- `قراءة المزيد` opens a scrollable screen containing every currently open event.
- Pending events are not truncated by UI, provider, or DAO fixed-count limits.
- Snooze does not remove an event from the open-events view.
- Closing/dismissing an event removes it; provider resolution may also remove a resolved condition.
- Educational-content relocation and idea capture remain for Session 354.

## Implementation
- Removed aggregate 5-event cap.
- Removed 5-event caps from Inventory, Invoice, Party, Shipment, and Optimal maintenance providers.
- Removed fixed `LIMIT 5` from pending-action DAO read models.
- Home work-card composition now selects only the first globally ranked event.
- Added `home_pending_actions` route and unbounded `LazyColumn` screen.
- Changed pending-event menu wording from `إخفاء` to `إغلاق`.

## Verification
- `python tools/verify_session_352.py` → 12/12 PASS (352 preserved).
- `python tools/verify_session_353.py` → 10/10 PASS.
- Gradle compile/test: NOT RUN because Gradle 8.9 distribution is not cached and the environment cannot resolve `services.gradle.org`.

Status: `PASS_STATIC / BUILD_ENV_BLOCKED`.
