# SESSION 354 — Home Engagement / Team Observations / Education

## Source of truth
- Input: `Verto-v353-pending-actions-redesigned.zip`
- Output target: `Verto-v354-home-observations-education.zip`

## Implemented
1. Preserved the established Home content order. Existing sections remain Header → Quick Actions → Pending Action → Activity Feed.
2. Added the new content after the established sections inside the Home scroll:
   - free-form idea/observation capture;
   - educational content at the bottom.
3. Team observation capture is intentionally frictionless:
   - raw free text only;
   - no employee-side category;
   - no AI/classification;
   - local-first Room persistence;
   - durable sync participant.
4. Added manager-only `ملاحظات الفريق` screen:
   - every observation is a separate card;
   - raw employee text, author, and timestamp;
   - manual status: NEW / REVIEWED / CLOSED;
   - manual importance: normal / important.
5. Added Room `team_observations` entity/DAO and migration `86 → 87`.
6. Added `docs/sql/v354_team_observations.sql` with tenant RLS. Employees can submit/retry their own NEW normal observation; manager/admin controls review status and importance.
7. Home educational content:
   - no extra educational title;
   - summary itself is the visible heading;
   - Tajawal is used for educational long-form reading;
   - Cairo remains the general UI family.
8. Home username is highlighted with `AccentPrimary` in the Home header only.
9. Drawer branch label is sourced from organization city. No username was added to the drawer.
10. Removed obsolete Home pending pager production artifacts and pager references; Home pending action remains the single highest-priority item introduced in session 353.
11. Motivational copy is conservative and only appears when the current Home state supports it.

## Verification
- `python tools/verify_session_354.py` → **16/16 PASS**.
- XML resource parsing → PASS.
- Production Home pager references → none.
- Gradle compile attempt: **NOT RUN TO COMPLETION**. The wrapper requires Gradle 8.9, which is not cached, and the environment cannot resolve `services.gradle.org`.
- Therefore this session does **not** claim Build PASS.

## Server state
- The Supabase migration is included in `docs/sql/v354_team_observations.sql`.
- It was **not applied to the live Supabase project in this session**.
- Cross-device delivery of team observations requires applying that SQL migration.
