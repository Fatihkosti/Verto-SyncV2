# Verto v255 — تقرير اختبارات الجلسات 243–255

## الملخص

- الإجمالي: **16** بوابة.
- اجتازت: **15**.
- فشلت: **1**.
- اختبار Auth الإضافي (`:feature:auth:testDebugUnitTest`): **PASS**.

## النتائج

| الجلسة | الاختبار | النتيجة |
|---:|---|---|
| 243 | `verify_v243_invoice_baseline.py` | FAIL — خط أساس موروث |
| 244 | `verify_v244_migration_sql.py` | PASS |
| 244 | `verify_v244_money_core.py` | PASS |
| 245 | `verify_v245_atomic_invariants.py` | PASS |
| 245 | `verify_v245_migration_sql.py` | PASS |
| 246 | `verify_v246_currency_truth.py` | PASS |
| 247 | `verify_v247_inventory_costing.py` | PASS |
| 248 | `verify_v248_invoice_lifecycle.py` | PASS |
| 249 | `verify_v249_financial_sync.py` | PASS |
| 250 | `verify_v250_reports_reconciliation.py` | PASS |
| 251 | `verify_v251_client_credit_money.py` | PASS |
| 251 | `verify_v251_local_contracts.py` | PASS |
| 252 | `verify_v252_invoice_returns.py` | PASS |
| 253 | `verify_v253_purchase_cycle.py` | PASS |
| 254 | `verify_v254_invoice_drafts.py` | PASS |
| 255 | `verify_v255_analytics_alerts.py` | PASS |

## تفاصيل الفشل

فشل فحص F243 لأنه يقارن النسخة الحالية بخط أساس تاريخي يتوقع Room schema 61 ووجود fallback قديم وتدقيق post-commit. النسخة الحالية v255 تحتوي Room schema 72، كما أن هذه النقاط موثقة كدين تقني موروث في تقارير الجلسات السابقة. لم يكن الفشل ناتجاً عن تغييرات هذا الطلب.

تم حفظ السجلات التفصيلية مؤقتاً تحت `build/session-tests-243-255/`، وهي مستبعدة من الأرشيف النهائي.
