---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B09-V02 observed local runs only"
---
# أوامر التحقق — B09-V02

جميع الأوامر من جذر المشروع. ملفات logs هنا مخرجات تشغيل فعلية، لا نتائج متوقعة مكتوبة يدويًا.

| الأمر الذي شُغّل | exit | الدليل/النتيجة |
|---|---|---|
| `python tools/test_sync_b09_kotlin_smoke.py` قبل تصحيح validator | 1 | `shared-write-before.log`؛ duplicate effect business identity |
| `python tools/test_sync_b09_kotlin_smoke.py` بعد التصحيح | 0 | `kotlin-smoke-after.log`؛ 3 semantic +347 mapping؛ codec stub |
| `python tools/test_sync_b09_materializer.py --output docs/sync-repair/evidence/B09/V02` | 0 | `sqlite-static.log` و`sqlite-static-results.json`؛ 612 source/SQLite-model |
| `python tools/test_sync_contract_v2_schema.py` | 0 | `b06-schema-regression.log` |
| `python tools/test_sync_b06_producers.py` | 0 | `b06-producers-regression.log` |
| `python docs/sync-repair/evidence/B09/V02/runtime-preflight.py` | 2 | `runtime-environment.json`؛ BLOCKED قبل wrapper |

نتائج أوامر النطاق والتوثيق اللاحقة محفوظة في `local-gates.json` وملفات logs المعنية؛ لا تعد قبولًا لـG-B09.

## الأوامر الفعلية المطلوبة — NOT_RUN

يلزم Gradle8.9/Android SDK واعتماديات المشروع وجهاز/محاكي. `--offline` يناسب البيئة ذات الاعتماديات المخزنة فقط؛ يمكن حذفه عند تشغيل المستخدم بيئته المتصلة، دون اعتبار ذلك منفذًا هنا.

```bash
bash gradlew --offline --no-daemon :data:sync:testDebugUnitTest   --tests com.verto.app.data.sync.pull.FinancialMaterializationContractV2Test
bash gradlew --offline --no-daemon :data:sync:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.pull.FinancialMaterializerV2InstrumentedTest
bash gradlew --offline --no-daemon :app:compileDebugKotlin
```

بعدها أعد حزم B04/B05/B06 المتأثرة كما توثق أوامرها السابقة، وحدّث تقارير G-B09 بالدليل على هذه الشجرة. لا أمر أعلاه شُغّل في هذه الزيارة، ولا استثناء اختبارات/بوابة مُجاز. B10 لم تبدأ.

## بوابات النطاق والتوثيق المحلية

- `python scripts/ci/verify-change-contract.py --root . --contract docs/sync-repair/evidence/B09/V02/change-contract.json --output docs/sync-repair/evidence/B09/V02/change-contract-result.json`: exit=0، نطاق محلي فقط.
- `DOCUMENTATION_GATE_REPORT_DIR=docs/sync-repair/evidence/B09/V02 bash scripts/run-documentation-gate.sh`: exit=1؛ 141 خطأ سابق بعد تسجيل الوثيقتين الجديدتين.
- `CI_GATE_REPORT_DIR=docs/sync-repair/evidence/B09/V02/ci-gates bash scripts/ci/run-quality-gate.sh documentation 4250902`: exit=126، سكربت التوثيق المضمن غير executable في المصدر نفسه.
- `python scripts/documentation/documentation_gate.py --root <temporary-original-ZIP-tree> --json-out docs/sync-repair/evidence/B09/V02/documentation-baseline.json`: exit=1، تشغيل على شجرة الأصل المنفصلة للمقارنة؛ أزيلت الشجرة المؤقتة بعد حفظ الناتج.

الأخطاء التاريخية لا تعتبر PASS؛ سجل المقارنة لا يثبت إلا عدم زيادة أخطاء التوثيق. لا تُطلق جودة شاملة/Release اعتمادًا عليه.
