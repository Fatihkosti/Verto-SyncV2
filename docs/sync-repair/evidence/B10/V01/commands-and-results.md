---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B10-V01 local source/SQLite/policy only; G-B10 BLOCKED"
---
# أوامر التحقق وحدود النتائج — B10-V01

كل المسارات من جذر المشروع. المخرجات المرفقة تشغيل فعلي، ولا تمثل قبول Room أو بناء التطبيق.

| الأمر المنفذ | النتيجة | الدليل وحدوده |
|---|---|---|
| `python tools/test_sync_b10_sqlite.py` | exit 0؛ 15 اختبارًا | `sqlite-results.txt`؛ SQLite الحقيقي ينفذ 20 عبارة migration فعلية ويحضر/يفحص 25 استعلام DAO؛ سيناريو المعاملة harness صريح وليس Room |
| `python tools/test_sync_b10_kotlin.py` | exit 0 في التشغيل المصحح | `kotlin-results.txt`؛ 12 طريقة اختبار سياسة فعلية عبر assertion shim؛ compile توقيعي منفصل الوحدات مع collaborators صريحة |
| التشغيل الأول لفحص B10 Kotlin | exit 1 ثم أُصلح | `kotlin-first-failure.txt`؛ منع smart cast لخاصية عامة من وحدة أخرى؛ استبدلت بقيمة محلية ثم نجح compile |
| `python tools/test_sync_b09_materializer.py --output docs/sync-repair/evidence/B10/V01/regression-B09` | exit 0؛ 612 تحققًا | `regression-b09-sqlite.txt`؛ نموذج Python على export المخطط98، ليس مخطط99 مولدًا أو Room |
| `python tools/test_sync_b09_kotlin_smoke.py` | exit 0؛ 347 mapping/Money و3 identity | `regression-b09-kotlin.txt`؛ codec stubbed، ليست JSON runtime |
| `python tools/test_sync_contract_v2_schema.py` | exit 0 | `regression-b06-schema.txt`؛ gate مصدر/Schema فقط |
| `python tools/test_sync_b06_producers.py` | exit 0 | `regression-b06-producers.txt`؛ gate ربط المصدر فقط |

فُحصت البيئة محليًا بأوامر `java -version` و`kotlinc -version` ووجود distribution/SDK/adb؛ النتيجة في `runtime-environment.json`. لا wrapper ولا تحميل شبكة. compiler المتاح 1.9.0 ليس compiler Gradle المشروع 2.1.0؛ نجاح signatures ليس ضمان توافق الاعتماديات الفعلية أو Hilt/KSP.

## أوامر الاعتماد المطلوبة — لم تُشغل

```bash
bash gradlew --offline --no-daemon :data:sync:testDebugUnitTest \
  --tests 'com.verto.app.data.sync.pull.DurableInbox*' \
  --tests 'com.verto.app.data.sync.pull.FinancialMaterializationContractV2Test'

bash gradlew --offline --no-daemon :data:sync:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.pull.DurableInboxApplyCoordinatorInstrumentedTest,com.verto.app.data.sync.pull.FinancialMaterializerV2InstrumentedTest

bash gradlew --offline --no-daemon :data:database:kspDebugKotlin :data:sync:compileDebugKotlin :app:compileDebugKotlin

bash gradlew --offline --no-daemon :app:assembleDebug
```

يلزم تشغيل واختبار migration98→99 فعليًا عبر Room على نسخة98 غير فارغة، وتوليد export99 بالـKSP ومراجعة schema validation. لا export99 مختلق في التسليم. أعد suites B04/B05/B06 وSyncManager/Worker المتأثرة؛ الفحوص التوقيعية لا تشمل كل هذا الربط.

الاختبارات المستهدفة: 24 JVM جديدة (12 سياسة+12 validator) و12 مالية سابقة؛ 14 Room Inbox جديدة و34 مالية (كان العدد33). تعني الأعداد طرقًا موجودة في المصدر، وليس اجتيازها. مجموعتا Gradle/JVM وAndroid هنا NOT_RUN. أغلق G-B09 بالدليل المحدث أيضًا.

اختبارات إغلاق/إعادة فتح SQLite أو Room الاختبارية ليست قتل عملية Android بالقوة. T25/T26 يتطلبان kill/restart حقيقيًا ونسخ قبل/بعد؛ T29 يعتمد أصلًا ماليًا حقيقيًا؛ T35–T37 تتطلب الحدود على JSON/Room/السيرفر والمساحة الفعلية. C§21 وG-B10 لا تغلق بالفحوص الجزئية.

## الخطوة التالية

B10.01: شغّل الأوامر في بيئة Gradle8.9 وAndroid مجهزة؛ عالج أي فشل، صدّر99، اختبر قتل العملية والتبعيات/الحصة، ثم طابق RPC وcanonical bytes مع B08/B20 على قاعدة معزولة. لا تبدأ B11 أو نشر إنتاجي تلقائيًا، ولا تعيد تنفيذ receipt/applied محفوظ لمجرد الاستئناف.

## بوابات التوثيق والنطاق المنفذة

`python scripts/ci/verify-change-contract.py --root . --contract docs/sync-repair/evidence/B10/V01/change-contract.json --output docs/sync-repair/evidence/B10/V01/change-contract-result.json`: exit0؛ PASS لضبط النطاق فقط، وليس قبولًا لـG-B10.

`python scripts/documentation/documentation_gate.py --json-out docs/sync-repair/evidence/B10/V01/documentation-gate.json`: exit1؛ 141 خطأ باقٍ. عند تحديث مرجع RPC الجديد أزيل انحرافا التوثيق اللذان ظهرَا من هذا التغيير. المقارنة مع تقرير B09-V02 المرفق لا تظهر أخطاء إضافية؛ baseline لم يُعَد تشغيلها منفصلة هنا. النتيجة العامة تبقى FAIL ولا شهادة جودة شاملة. التفاصيل `documentation-delta.json`.
