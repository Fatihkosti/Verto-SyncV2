# SESSION 365-1 — Reports Filters Repair

## Result
`PASS_STATIC`

## Implemented
- Removed the temporary two-layer viewing behavior from Reports presentation.
- Report dashboard and all existing drill-down targets are temporarily visible to all report users.
- Export permission remains unchanged and still requires `reportsExport`.
- Fixed the misleading global-filter contract:
  - Report period remains the only cross-domain/global filter.
  - Payment method, item category, and cashier are explicitly Sales-only filters.
  - Advanced filter button is shown only inside Sales detail.
  - Active filter chips are shown only inside Sales detail.
  - Sales filters are cleared immediately when leaving Sales, preventing partially filtered dashboard/other domains.
  - Sales filter sheet is titled `فلاتر المبيعات`.
- No SQL, migration, schema, Supabase, or server changes.

## Verification
- `python tools/verify_session_365.py` => **37/37 PASS_STATIC**.
- `python tools/verify_session_365_1.py` => **11/11 PASS_STATIC**.

## Build gate
Attempted:

```bash
./gradlew :feature:reports:compileDebugKotlin --offline --build-cache
```

Compilation could not start because Gradle 8.9 is absent from the local wrapper cache. The wrapper attempted to fetch `gradle-8.9-bin.zip`, but network access is unavailable (`UnknownHostException: services.gradle.org`).

Therefore 365-1 is **PASS_STATIC**, not a claimed Gradle build PASS.
