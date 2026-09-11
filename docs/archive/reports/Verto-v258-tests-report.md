# Verto v258 — Tests Report

| Gate | Result |
|---|---|
| `tools/verify_v258_inventory_reconciliation.py` | **PASS — 65/65** |
| Kotlin standalone reconciliation contract compile/run | **PASS — `V258_CONTRACT_KOTLIN_PASS`** |
| `verify_v242_logistics_quality.py` | **PASS** |
| `verify_v245_atomic_invariants.py` | **PASS** |
| `verify_v247_inventory_costing.py` | **PASS** |
| `verify_v248_invoice_lifecycle.py` | **PASS** |
| `verify_v249_financial_sync.py` | **PASS** |
| `verify_v252_invoice_returns.py` | **PASS** |
| `verify_v253_purchase_cycle.py` | **PASS** |
| `InventoryReconciliationContractV258Test` via Gradle | **NOT RUN — Gradle 8.9 download blocked** |
| `InventoryReconciliationMigration258Test` Android/Room | **NOT RUN — Gradle + emulator/device unavailable** |
| Room KSP schema export 74 | **NOT GENERATED — Gradle unavailable** |
| PostgreSQL/Supabase v258 SQL execution | **NOT RUN — no connected Postgres service** |
| `scripts/verify-kotlin-quality-static.py` | **FAIL — repository baseline already failed in v257** |

## Quality-gate comparison

`v257` baseline vs `v258`:

- architecture violations: **18 → 18**
- broad catches: **21 → 21**
- dependency cycles: **0 → 0**
- large Kotlin files >500 lines: **21 → 21**
- not-null assertions: **1 → 1**
- excessive parameter lists: **565 → 570** بسبب عقود/DTOs المصالحة الجديدة
- long functions: **437 → 442** بسبب منطق Migration/Reconciliation الجديد

تم فصل `SyncInventoryReconciliation.kt` كي لا تتحول `SyncInventory.kt` إلى ملف >500 سطر؛ لذلك لم تضف v258 ملف Kotlin ضخم جديد.

## Gradle attempt

الأمر المنفذ:

```text
./gradlew :data:database:testDebugUnitTest --no-daemon
```

النتيجة الفعلية: فشل قبل Configuration/Compilation لأن Wrapper حاول تنزيل `gradle-8.9-bin.zip` وانتهى بـ`UnknownHostException: services.gradle.org`.

لا يوجد ادعاء Build/Test ناجح لما لم يُنفذ فعلياً.
