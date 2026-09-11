# B12-V01 commands and observed results

## Passed local probes

```text
python tools/verify_b12_expense_revision.py
B12_STATIC_GATE=PASS checks=22
```

Full output: `static-results.txt`.

```text
python tools/test_sync_b12_sqlite.py
B12_SQLITE_CONTRACT=PASS
```

Full output: `sqlite-results.txt`.

```text
python tools/test_sync_contract_v2_schema.py
B06_SCHEMA_GATE=PASS defs=42 fullFinancialLists=10
```

Full output: `schema-results.txt`.

```text
kotlinc data/sync/src/main/kotlin/com/verto/app/data/sync/expense/ExpenseRevisionContractB12.kt -d <temp>/b12.jar
KOTLIN_B12_CONTRACT_COMPILE=PASS
```

Full status: `kotlin-contract-compile.txt`.

```text
python -m py_compile tools/verify_b12_expense_revision.py tools/test_sync_b12_sqlite.py
exit 0
```

## Gradle attempt — blocked before project build

Requested:

```text
./gradlew --offline :data:sync:test :data:operations:test :data:database:test
```

Observed: wrapper attempted to obtain `gradle-8.9-bin.zip`; DNS/network was unavailable and it stopped with `java.net.UnknownHostException: services.gradle.org`. No Gradle task compiled or ran. First failure is preserved in `gradle-first-failure.txt`.

## Non-B12 regression harness

`tools/quality/verto_finance_behavior_v334.py` exceeded the execution limit on two attempts. It produced no completed result and is deliberately classified `NOT_COMPLETED_TIMEOUT`, not PASS or FAIL.

## Required acceptance commands when prerequisites exist

Run the project’s B12-focused Gradle/Room tests on Gradle 8.9/Android tooling, then execute T21/T27 against actual Room state. After B07 atomic server batching is available, apply the B12 migration in an isolated PostgreSQL/Supabase test environment and execute the expense+cash group scenarios, verifying server history, versions, write IDs, cash facts, replay and domain-drift rejection.

Do not promote B12 or R08 from BLOCKED until those observed results are stored here.
