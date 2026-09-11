# B12-V01 session report — mutable/versioned expense

## Scope

Implemented B12.01–B12.05 code locally from the B11-V01 input archive. No task is closed because the required Gradle/Room and PostgreSQL/B07 acceptance evidence is unavailable.

## Product changes

- Expense edits and VOID retain the same `expenseId` and capture before/after snapshots, hashes, actor, timestamp, and captured base version.
- The producer freezes the expense revision intent and cash delta in one Room transaction and links expense/cash mutations through one batch/dependency chain.
- Cash delta is `-(E_new-E_old)`, where ACTIVE uses `amountMinor` and VOID uses zero. Note-only = 0; 10000→15000 = -5000; VOID after 15000 = +15000.
- Remote apply is mutable/versioned, uses `amountMinor` as authority, validates revision hashes/delta, records append-only expense history, and does not synthesize a second cash effect.
- Local expense revision history has append-only SQLite guards on migration/open.
- Added B12 DTO/schema definitions and a Supabase migration/server adapter for versioned expense history and group validation.
- Non-zero server cash effects intentionally fail closed with `B12_EXPENSE_BATCH_REQUIRED` until the B07 atomic expense+cash server batch exists and is proven. This prevents partial financial writes.

## Verification actually run

- `python tools/verify_b12_expense_revision.py` → PASS, 22 checks.
- `python tools/test_sync_b12_sqlite.py` → PASS.
- `python tools/test_sync_contract_v2_schema.py` → PASS, 42 definitions / 10 full financial lists.
- `kotlinc .../ExpenseRevisionContractB12.kt` selected pure contract compile → PASS.
- `python -m py_compile tools/verify_b12_expense_revision.py tools/test_sync_b12_sqlite.py` → PASS.

These are source/native-SQLite/pure-Kotlin checks only. They are not Room or end-to-end B12 acceptance.

## Blocked / not run

- Gradle 8.9 project compile, KSP/Hilt, Room migrations/DAO tests, Android runtime and device tests: NOT_RUN. The wrapper attempted to fetch Gradle 8.9 and failed with `UnknownHostException: services.gradle.org`.
- T21 and T27 as complete application+Room scenarios: NOT_RUN. Only the T21 arithmetic precursor was executed.
- PostgreSQL/Supabase migration and RPC execution: NOT_RUN; no live or isolated server was modified.
- Atomic expense+cash server group acceptance: BLOCKED on B07.
- An older non-B12 finance behavior harness timed out twice; it produced no result and is not counted PASS or FAIL.

## Gate result

`G-B12 = BLOCKED`. B12.01–B12.05 remain `BLOCKED`, R08 remains OPEN, and delivery is `WIP_NOT_RELEASE_READY`.

## Safety / live actions

No live SQL, production data changes, Supabase deployment, APK, GitHub, or Drive action occurred.
