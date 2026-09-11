---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B09-V02 source/semantic smoke only; G-B09 BLOCKED"
---
# تقرير استكمال B09 — الزيارة B09-V02

## نتيجة التسليم

**أُصلح عيب حقيقي في قبول عكس الدفعات المتعددة، وأضيفت خمسة اختبارات رجعية. لا تزال B09 محجوبة بمتطلبات تشغيل Gradle وRoom الفعلية؛ لم تبدأ B10.**

المصدر الوحيد: `Verto-425-B09-WIP_NOT_RELEASE_READY(1).zip`، SHA-256 `33c67ceab661921e0fb0e64e3f3025a195894fba36f503fe4ee0ccd0ee2f122b`.
مطابقة manifest السابقة: 1851/1851 ملف منتج، بلا اختلاف. بصمة المنتج الجديدة: `2aa1a4e16d9ea9d1d37fd9b33fc0bc730616a288634da78c7aa56fc3ad97bbc7`. لا Git/commit مُدّعى. ملفات الزيارات السابقة محفوظة.

## لماذا استؤنفت B09؟

لوحة الاستئناف في الملف المرفق تحدد `NEXT_SESSION=B09` و`NEXT_TASK=B09.01`، وبوابة `G-B09=BLOCKED`. تنفيذ B10 يعتمد على نجاح B09. لذلك نفذت الجزء المتاح من التحقق والتصحيح على نفس المرحلة، ولم أتجاوز البوابة أو أسمِ المرحلة منتهية بسبب نجاح فحص بديل. لا تفويض إعفاء جديد في طلب المستخدم.

## نطاق الفحص والدليل من الكود

تم تتبع منتج اللقطة المالية، validator، mapper، materializer، التحقق من مالك الآثار، DAO، وربط استقبال INVOICE/PAYMENT ومعاملته، مع قراءة الاختبارات الحالية. هذا فحص لمسار B09، **وليس شهادة تدقيق لكل ميزات المشروع**.

1. `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceVoidCoordinator.kt:118–120,215–234`: إلغاء الفاتورة يمر على الدفعات الأصلية؛ ينشئ عكسًا بهوية مشتقة من requestId وهوية الأصل، بينما `writeId=requestId` نفسه لجميعها.
2. `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialSnapshotFactoryV2.kt:31–35,59–60`: PAYMENT effect يحمل `factId=payment.id` و`businessIdentity=payment.writeId` أو id عند الفراغ؛ التفرد عند المنتج أصلًا على owner/type/factId.
3. `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/FinancialMaterializationContractV2.kt:140–146`: المستقبل كان يفرض تفرد owner/type/businessIdentity على كل الأنواع، فيرفض لقطة صحيحة فيها عكسان مختلفان لنفس عملية الإلغاء.

**الخطأ الملاحظ قبل التصحيح:** `CONTRACT_FIELD_INVALID: duplicate effect business identity` من validator Kotlin الحقيقي؛ محفوظ في `shared-write-before.log`، exit=1. الـcodec في هذا smoke بديل توقيعي صريح؛ لا JSON decoding أو hash acceptance مُدّعى.

## التعديل المنفذ

أضيف تفرد `(owner, factType, factId)` لجميع الآثار. بقي تفرد businessIdentity للأنواع غير PAYMENT؛ PAYMENT يسمح باشتراك writeId للحقائق المختلفة. لم يتغير factId أو hash أو serialization أو frozen packet. منع تعديل حقيقة دفع ثابتة ومحتواها ما زال في مسار materializer/effect verifier القائم. لم تتغير schema98 أو SQL أو حسابات النقد/المخزون/العمولات أو منطق إلغاء الفاتورة نفسه.

التغيير الإنتاجي محصور في ملف validator واحد؛ ملفا اختبارات وsmoke tool عُدّلت لتوثيق العيب وحمايته. التفاصيل الحرفية في `change.patch` والجرد في `changed-files.json`.

## الاختبارات المضافة — لم تشغل ببيئتها الفعلية

ملف JVM: `data/sync/src/test/kotlin/com/verto/app/data/sync/pull/FinancialMaterializationContractV2Test.kt`. أضيف قبول دفعات عكس متعددة بـwriteId مشترك، ورفض تكرار factId، وحفظ منع تكرار businessIdentity لغير PAYMENT.

ملف Room: `data/sync/src/androidTest/kotlin/com/verto/app/data/sync/pull/FinancialMaterializerV2InstrumentedTest.kt`. أضيف قبول اللقطة ثم replay دون دمج/ازدواج الدفعات، ورفض تغيير حقيقة دفع موجودة رغم اشتراك writeId، مع بقاء authority والبيانات السابقة.

الإجمالي الآن **12 JVM و33 Room، جميعها NOT_RUN في هذه الزيارة**. الاختبارات القديمة لم تمحَ، وخمسة اختبارات جديدة أضيفت.

## ما شُغّل فعليًا وحدوده

| الفحص | النتيجة | ما لا يثبته |
|---|---|---|
| استنساخ العيب بالـvalidator الفعلي قبل الإصلاح | FAIL المتوقع، exit 1 | لا codec/JSON أو Room runtime |
| نفس smoke بعد الإصلاح | PASS، 3 تحققات هوية | codec stub؛ ليس تشغيل JUnit الأصلي |
| Kotlin mapping/Money الحقيقيان | PASS، 347 assertion | تسعة DTOs؛ ليس Gradle/KSP/Hilt |
| مصدر + SQLite على export المخطط98 | PASS، 612 check | إسقاط Python اصطناعي وليس تنفيذ materializer داخل Room |
| B06 schema/producers static | PASS/PASS | لا يعيد اعتماد JVM/Room التاريخية |
| preflight محلي للأدوات | BLOCKED، exit 2 | لا Gradle8.9 cache/SDK/adb/emulator |
| 12 JVM /33 Room /app compile | NOT_RUN | لا بناء فعلي أو APK أو release claim |
| T01–T50، PostgreSQL، جهازان | NOT_RUN | لا إصلاح الحالة الحية مُثبت |

أوامر التنفيذ والمخرجات في `commands-and-results.md`. تقارير ضبط النطاق والتوثيق منفصلة عن G-B09؛ لا يجوز تحويل نجاحها إلى قبول مالي/Android.

## عائق البيئة وحدود التشغيل

`runtime-environment.json` يوثق Java21 وkotlinc1.9 المتاحين، وغياب Gradle8.9 المخزن وAndroid SDK وأدوات الجهاز. لم أشغّل wrapper غير المخزن: التحميل يسبق أثر `--offline`. **لا محاولة شبكة في هذه الزيارة**. خطأ UnknownHost السابق محفوظ كتاريخ V01، وليس نتيجة أُعيد اختلاقها.

لم يجر تعديل سيرفر أو حسابات أو بيانات مستخدم أو رفع خارجي أو بناء APK. `B09-BLK-01` مفتوح. عوائق B02 محفوظة. لا اختبارات Txx تغيرت إلى PASS، ولا جلسات/مهام إضافية أغلقت.

## الاستئناف المحدد

ابدأ من ZIP هذه الزيارة، ثم `B09.01`: شغّل الحزمتين الفعليتين و`:app:compileDebugKotlin` في بيئة مجهزة، وأعد الحزم المتأثرة B04–B06. أصلح كل فشل قبل اعتماد B09.01–B09.05. لا تنتقل إلى B10 إلا بعد دليل Room والتحقق من متطلبات G-B09. تشغيل أجهزة/SQL المرتبط بـB20 أو B07/B08 لا يُستبدل بنتائج محلية.

## Documentation Impact

السلوك المتغير موثق في `docs/api/idempotency.md`، وسجل المالك `docs/CANONICAL_DOCUMENT_MAP.md`؛ تحديث محدود لا ينسب تحققًا حيًا لجميع الفقرات القديمة. حدّثت Backlog وCHANGELOG وEVIDENCE_INDEX وSYNC_IMPLEMENTATION_MAP، وسجلت وثيقتي الزيارة في docs/DOCUMENTATION_INVENTORY.md؛ لا canonical منافس ولا تعديل لعقد الإصلاح المضمن/المستقل. change-contract رقمه 4250902 لأداة النطاق فقط؛ أُنشئ كدليل لهذه الزيارة وليس ادعاء تسجيل سابق أو تغيير ترقيم الخطة.

التسليم **WIP_NOT_RELEASE_READY**؛ ليس Source-of-Truth معتمدًا ولا تطبيقًا جاهزًا للإطلاق.

## نتائج بوابات النطاق والتوثيق

ضبط نطاق التغيير نجح. بوابة التوثيق المباشرة **FAIL**، وقد شُغلت كذلك على المصدر الأصلي: 141 رسالة سابقة (2 ملكية CHANGELOG، و14 انجراف RPC، و1 وحدة قديمة، و124 ملف Markdown غير مسجل). أُدرجت وثيقتا هذه الزيارة في السجل؛ مقارنة الرسائل في `documentation-delta.json` تثبت عدم إضافة إخفاق جديد. لا ادعاء نجاح بوابة التوثيق الكاملة.

المسار المدمج `run-quality-gate.sh documentation 4250902` توقف exit=126 لأن `scripts/run-documentation-gate.sh` غير executable في ZIP الأصلي (mode 0644). التشغيل المباشر بـbash وصل إلى الفحوص وسجل إخفاقاتها. لم أغيّر صلاحيات/كود بوابات الجودة أو أعفِها؛ هذا عائق تغليف سابق مستقل عن الخطأ المالي. بوابة الجودة الشاملة وSource-of-Truth admission لم تشغلا.
