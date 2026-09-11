---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B11-V01 local static/native-SQLite only; G-B10/G-B11 BLOCKED"
---
# أوامر التحقق وحدود النتائج — B11-V01

| الأمر | النتيجة | الحد |
|---|---|---|
| `python3 tools/test_sync_b11_static.py` | PASS | فحص عقد المصدر/الربط فقط؛ ليس Kotlin/Compose/Hilt compile |
| `python3 tools/test_sync_b11_sqlite.py` | PASS | SQLite host يختبر DDL 99→100 والحالات والحراس؛ ليس Room MigrationTestHelper |
| `python3 -m py_compile tools/test_sync_b11_sqlite.py tools/test_sync_b11_static.py` | PASS | صحة Python فقط |
| `./gradlew :data:database:compileDebugKotlin :data:sync:compileDebugKotlin :app:compileDebugKotlin --offline --stacktrace` | BLOCKED exit1 | Gradle8.9 غير مخزن؛ wrapper حاول `services.gradle.org` وفشل UnknownHost؛ لا compile فعلي |

## اختبارات الاعتماد المطلوبة — NOT_RUN

```bash
./gradlew --offline --no-daemon :data:database:kspDebugKotlin :data:database:testDebugUnitTest \
  --tests 'com.verto.app.data.local.MigrationCatalogB11Test'

./gradlew --offline --no-daemon :data:sync:testDebugUnitTest \
  --tests 'com.verto.app.data.sync.conflict.SyncConflict*B11Test'

./gradlew --offline --no-daemon :app:testDebugUnitTest \
  --tests 'com.verto.app.feature.sync.di.ConflictResolutionPermissionPolicyB11Test'

./gradlew --offline --no-daemon :app:compileDebugKotlin
```

ثم يلزم Room migration99→100 على قاعدة99 غير فارغة وfresh schema100، ومسار UI/permission، وT30 على receipt/echo فعليين. يجب اختبار: accept-server، resend-local، matching echo، رفض الصلاحية، تغير نسخة الخادم، OUTCOME_UNKNOWN، immutable facts، ووجود تعديل محلي أحدث. لا يبدأ B12 قبل إغلاق G-B10 وG-B11.

## بوابات النطاق/التوثيق

- `verify-change-contract.py`: PASS؛ لا تغيير خارج allowlist.
- `documentation_gate.py`: FAIL بـ141 خطأ سابقًا موجودًا؛ لا خطأ جديد يحمل مسار B11 بعد إضافة ملفي الدليل إلى inventory. هذا ليس PASS للتوثيق العام.
