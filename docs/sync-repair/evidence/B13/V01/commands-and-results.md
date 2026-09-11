# B13-V01 Commands and Results

## B13 static contract
```text
python tools/verify_b13_bootstrap_safety.py
B13_STATIC_GATE=PASS checks=48
```

## B13 SQLite contract
```text
python tools/test_sync_b13_sqlite.py
B13_SQLITE_CONTRACT=PASS
```

## Verification-script syntax
```text
python -m py_compile tools/verify_b13_bootstrap_safety.py tools/test_sync_b13_sqlite.py
PY_COMPILE=PASS
```

## Regression checks
```text
python tools/test_sync_contract_v2_schema.py
B06_SCHEMA_GATE=PASS defs=42 fullFinancialLists=10

python tools/test_sync_b11_static.py
B11_STATIC_CONTRACT: PASS

python tools/verify_b12_expense_revision.py
B12_STATIC_GATE=PASS checks=22

python tools/test_sync_b12_sqlite.py
B12_SQLITE_CONTRACT=PASS
```

## Gradle compile attempt
```text
./gradlew :data:database:compileDebugKotlin :data:sync:compileDebugKotlin --offline --stacktrace
```
Result: `BLOCKED` before Gradle could run. The wrapper attempted to obtain Gradle 8.9 and failed with `java.net.UnknownHostException: services.gradle.org`. No Kotlin/KSP/Room/Android result is inferred from this failure.

## Checked-in server seal inspection
Searched the checked-in Supabase migrations for B13 server seal fields (`snapshot_digest_sha256`, `coverage_aggregate_types`, `delta_token`). They are absent. Existing v305 bootstrap code exposes prior anchors such as `page_high_watermark`, but that does not satisfy the B13 seal contract.

Result: `B08 SERVER SEAL = BLOCKED / NOT IMPLEMENTED IN CHECKED-IN MIGRATIONS`.
