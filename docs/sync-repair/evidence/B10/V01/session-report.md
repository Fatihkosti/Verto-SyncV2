---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B10-V01 local source/SQLite/policy only; G-B10 BLOCKED"
---
# تقرير تنفيذ B10 — الاستلام الدائم والتطبيق المستقل

## نتيجة التسليم

**نُفّذ الكود المحلي للأقسام B10.01–B10.05، وأضيفت اختبارات وترحيل98→99. بقي اعتماد المرحلة BLOCKED: لم يعمل البناء أو Room/JSON الفعليان، ولم يُثبت عقد B08 على السيرفر.** لا APK ولا نشر SQL ولا تنفيذ B11.

مصدر التنفيذ الوحيد هو `Verto-425-B09-V02-WIP_NOT_RELEASE_READY.zip`، SHA-256 `5dd0bd9337b4a5cbaa970b2f8835cd082228174ce8228a4f515faa898cd0f059`. تطابقت ملفات manifest السابق **1851/1851**؛ لا تغييرات مجهولة في المصدر. طلب المستخدم الصريح «نفذ b10» يجيز هذه الكتابة المحلية رغم تعليق G-B09، لكنه ليس إعفاءً من بوابات السلامة أو الاختبارات. التفاصيل في `input-validation.json` ولوحة الاستئناف.

بصمة المنتج الحالية: **`2c9379972d1d9db3f52d96f0a2dbe2325f77558e53bf3ef97265e1f3ee90eda0`**، عدد ملفات نطاق المنتج **1866**. الآلية والمسارات والاستثناءات في `product-tree.json`؛ تشمل المصدر والاختبارات والأدوات، ولا تدّعي commit/Git أو بصمة حاوية داخل نفسها. العقد المضمّن والمستقل محفوظان حرفيًا.

## ما تغيّر في السلوك والكود

### B10.01 — الاستلام يُحفظ قبل محاولة التطبيق

`DurableInboxPageValidator` يتحقق من النطاق، coverage، fromCursor، المجموعة المكتملة، الترتيب والأعداد، canonical UTF-8 bytes وSHA-256. يرفض split/interleaved groups وبيانًا ناقصًا أو divergent revision. المجموعة يجب أن تشمل مفاتيح كتابات DTO المشتقة؛ لا يعتمد الحفظ على manifest ناقص يمرر جذرًا ويخفي أبناءه.

`DurableInboxApplyCoordinator.receive` يحفظ أعضاء الصفحة وبيانات المجموعات وتبعياتها ومفاتيحها وطلب الإيقاظ مع **receivedCursor** في معاملة قصيرة واحدة. التحقق المكلف يحدث قبل المعاملة؛ لا طلب شبكة داخلها. replay المطابق لا ينشئ مجموعة أو حجزًا ثانيًا؛ اختلاف المحتوى بنفس الهوية خطأ صريح. observed version يمكن حفظها عند الاستلام، لكن applied version/checkpoint لا يتحركان بذلك.

بعد commit تبقى الصفحة في Inbox حتى لو فشل التطبيق أو أُعيد فتح القاعدة. قبل commit يؤدي الخطأ إلى rollback للصفوف والرمز معًا. الاختبارات المحلية تؤكد سلوك SQL harness؛ إثبات قتل عملية Android الحقيقية ما زال مطلوبًا.

**المسارات:** `data/network/.../sync/SyncInboxProtocolV2.kt`، `data/sync/.../pull/DurableInboxPageValidator.kt` و`DurableInboxApplyCoordinator.kt`، `data/database/.../dao/DurableSyncInboxDao.kt`.

### B10.02 — تعارض X لا يُسقط Y المستقلة

الحالات أصبحت RECEIVED/READY/APPLIED/WAITING_LOCAL/WAITING_DEPENDENCY/REQUIRES_REVIEW. التطبيق يجري بمعاملة مستقلة لكل مجموعة مع الحماية الموحدة للجذر وكل مفاتيح الأطفال/الحقائق المتأثرة. المجموعة ذات pending محلي تنتظر؛ مجموعة مستقلة قد تُطبّق، بينما المجموعة التي تمس المفتاح المحجوب أو تعتمد عليه تنتظر كاملة.

التبعيات صريحة ومقيدة بالنطاق. استثناء ترتيب اللمس يُقبل فقط لأصل مثبت تتطلبه مجموعة أسبق WAITING_DEPENDENCY، كي لا يحبس العكس أصله عند وصوله متأخرًا. الاستثناء يستند إلى dependency chain، لا تخمين بيانات أصلية أو إسقاط protection. إسقاط CASH_REGISTER لا يتقدم فوق آثار مالية أقدم غير مطبقة.

المطبق المالي B09 ما زال يكتب الحقائق الفعلية؛ التحقق من اكتمال آثار المجموعة يسبق APPLIED/checkpoint. فشل DTO معروف يتحول إلى انتظار/مراجعة بعد rollback، وفشل SQL غير المتوقع يُعاد ولا يُحوّل إلى نجاح. صححت أيضًا PRICE_LIST: المرجع المفقود ينتظر بدل إسقاط العنصر بصمت، ونوع غير مدعوم يُرفض بدل تأكيده بلا تطبيق.

إثبات echo يحتفظ بـhash **bytes الأصلية** للطلب المجمد؛ مقارنة canonical payload منفصلة. لا ACK اعتمادًا على originMutationId وحده، ولا تعديل frozen packet لإرضاء hash الوارد.

**المسارات:** `DurableInboxApplyCoordinator.applyGroup`، `DurableInboxTouchedKeys`، `SyncPendingProtection`، `DurableInboxEchoReconciler`، `UnifiedSyncChangeApplier.applyPriceList`.

### B10.03 — مؤشر تطبيق صحيح وإيقاظ دائم

applied checkpoint هو نهاية أطول سلسلة مجموعات **مغطاة ومطبقة**؛ وجود Y مطبقة بعد X المعلقة لا يقفز المؤشر فوق X. يمكن وجود فجوات revision ناتجة عن النطاق؛ لا MAX(revision) عمياء ولا تحويل رقم مراجعة إلى cursor مصطنع.

حسم نية محلية حقيقي يحرر مراجعها ويزيد generation في المعاملة نفسها. تطبيق تبعية يزيد generation كذلك. كل انتظار يُجرب مرة لكل generation؛ متابعة الميزانية تحفظ موعدًا دائمًا ولا تنشئ retry سريعًا بلا تغير.

`DurableInboxWakeObserver` يقرأ الالتزام بعد commit ويجدول WorkManager بسلسلة APPEND_OR_REPLACE مستقلة عن الاستبدال العنيف للعامل الجاري. invalidation مجرد إشارة؛ الحقيقة في DB. عند انتهاء drain يُعاد فحص الطلب المخزن؛ انهيار التطبيق لا يحذف الالتزام. العامل العام ما زال مشروطًا بالمصادقة والشبكة كما في المصدر؛ **عدم وجود شبكة داخل coordinator لا يعني توفير عامل offline مستقل**.

أضيف اختبار مالي لعكس يسبق الدفع الأصلي: يبقى pending حتى يرد الأصل الحقيقي ضمن مجموعة مثبتة؛ لا payment بديل. الاختبار مرفق لكنه لم يعمل عبر Room.

**المسارات:** `advanceCoveredCheckpoint`، `DurableSyncInboxDao`، `SyncPendingProtection.releaseAfterTerminal`، `DurableInboxWakeObserver`، `SyncManagerPorts` و`SyncWorker`.

### B10.04 — 1000 حد مرن، و1001 الأولى لا تُجزّأ

ميزانية التغييرات مشتركة بين صفحات الاستدعاء. أول مجموعة قانونية قد تتكون من1001 وتطبق كاملة؛ المجموعة التالية لا تستغل إعادة تعيين العدّاد عند صفحة جديدة. تحتسب محاولات المجموعات أيضًا حتى لا تدور حالات الانتظار بلا حد. المتابعة عند نهاية مجموعة ملتزمة، لا منتصف حقيقتها المالية.

الحد الصلب **2,097,152 byte** من canonical UTF-8 للجسم الكامل. ما فوقه خطأ عقد، وليس MORE_AVAILABLE فارغًا. receivedCursor يستخدم في الطلب التالي؛ صفحات الانتظار التي حُفظت لا يعاد طلبها من الرمز السابق. قرب حدود المساحة يخفض softLimit احتياطيًا بدل قبول استجابة لا تتسع.

**الدليل المنفذ:** 12 طريقة من `DurableInboxPolicyTest` نفذت بالسياسة الحقيقية عبر JUnit assertion shim. اختبارات validator تشمل2MiB/+1 وUnicode و1001 وhash/manifest/order، لكنها signature-compiled فقط؛ JSON الحقيقي لم يعمل.

### B10.05 — الحصة/القرص لا ينتجان نجاحًا كاذبًا

حصة Inbox غير المطبق **64MiB لكل scope**. يمكن استقبال صفحة قانونية عندما يبدأ الاستخدام دون64 وحتى إجمالي66MiB كحد أقصى. حالات المراجعة/الانتظار تحتسب ولا تُحذف لفتح مساحة. replay المطابق لا يضيف حصة.

يفحص `InboxStorageProbe` المساحة الفعلية بـStatFs، بحجز محافظ لكتابة DB/WAL/index/rollback، ويظل SQLiteFull سبب rollback ذريًا لا ضمانًا يتجاهل الخطأ. تُسجل WAITING_STORAGE_OR_REVIEW متى أمكن؛ إذا تعذر حتى تسجيل metadata بسبب القرص ينتشر الخطأ ولا يتحرك الرمز إلى نجاح. يمكن محاولة تطبيق المخزن قبل طلب صفحة جديدة. فحص SQLiteFull المنفذ يستخدم PRAGMA max_page_count على fixture صريح، وليس قرص Android أو Room.

`SyncManager` لا يسجل last successful/completed لمجموعة معلقة، ويعرض WAITING_INBOX أو NEEDS_REVIEW. الإيقاظ المؤجل المعروف لدفعات outbox لا يضيع بسبب انتظار Inbox. `SyncHealthSnapshot` يحسب غير APPLIED لمنع مسحها باعتبارها عملًا مؤكّدًا؛ اختبارات manager/worker الكاملة تحتاج إعادة تشغيل.

## تغييرات التخزين والسلامة

المخطط المطلوب **99**، مع `MIGRATION_98_99` مسجلًا في `MigrationCatalog` وentities في `AppDatabase`. أضيفت ثلاث جداول **metadata** فقط: `sync_inbox_apply_request`, `sync_inbox_dependency`, `sync_inbox_touched_key`. لا Inbox payload ثانٍ ولا queue مالية بديلة.

يعاد بناء sync_inbox داخل migration لتوسعة قيد الحالات/bytes مع نسخ **كل الأعمدة العشرين** قبل إعادة الاسم والفهارس وحارس ثبات المحتوى؛ لا تمسح النيات أو الأعمال أو الأحداث لاستكمال الترحيل. يضاف إلى manifest حجم serialized وattempt generation. receipt قديم غير مثبت البيان يبقى محفوظًا للمراجعة بدل اعتباره ناجحًا. يُصحح الفرق التاريخي بين observed server watermark وبين receive coverage فقط حيث ثبت عدم وجود وارد غير مطبق.

نفذت 20 عبارة migration حقيقية على SQLite انطلاقًا من export98، وحُضّرت كل استعلامات DAO الخمسة والعشرين على المخطط الناتج. هذا ليس تشغيل Android Room MigrationTestHelper؛ **لا export99 مولد في الأرشيف**. يلزم توليده بالـKSP، وفحص fresh install والترقية98→99 مع بيانات غير فارغة ومطابقة constraints/indices/identity.

## الربط بالسيرفر — حد جوهري قبل الاستخدام

يطلب العميل الآن **`verto_pull_sync_changes_v2`** مع أسماء ومعاني محددة للمعاملات والصفحة والبيانات. لم يُنشر RPC، ولم يُستعلم عن وجوده أو سلوكه حيًا في هذه الزيارة. لا ادعاء أن العميل الحالي يعمل مع RPC قديم؛ missing manifest/RPC يفشل بوضوح دون fallback.

ملف `wire-contract.md` يحدد جميع الحقول وقواعد canonical/hash والحدود ورؤية النطاق المطلوبة من B08/B20. مثال synthetic وcanonical bytes معه، لكن المرجع حُسب بـPython فقط ولا يثبت اتفاق Kotlin/PostgreSQL. **لا تُفعّل النسخة على قاعدة قائمة قبل اختبار هذا الربط والترحيل.** لا تغييرات خادم أو بيانات مستخدم تمت هنا.

## النتائج التي شُغّلت فعليًا

| الفحص | النتيجة | الحد الصريح |
|---|---|---|
| سلامة ZIP ومطابقة baseline manifest | PASS؛1851/1851 | هوية المصدر، ليست جاهزية التطبيق |
| B10 native SQLite | PASS؛15 طريقة اختبار | migration/DAO فعليان؛ معاملة coordinator نموذج SQL لا Room |
| سياسة B10 Kotlin | PASS؛12 طريقة اختبار فعلية | assertion shim فقط، دون Android أو JSON |
| B10 separate-module signature compile | PASS بعد إصلاح smart cast | collaborators/Room/JSON/Hilt stubs صريحة؛ ليس Gradle أو KSP |
| B09 source/SQLite-model | PASS؛612 تحققًا و13 cut | نموذج projection Python على export98 |
| B09 mapping/Money | PASS؛347 assertion | تنفيذ mapping الحقيقي لا materializer Room |
| B09 semantic identity | PASS؛3 assertions | validator حقيقي، codec بديل صريح |
| B06 schema/producer static | PASS/PASS | ليس إعادة قبول suites runtime السابقة |
| Gradle/JVM JSON وKSP/Hilt/app compile | NOT_RUN | Gradle8.9 غير مخزن، Kotlin المشروع2.1 مقابل smoke1.9، SDK غائب |
| Room/Android/process kill ومخطط99 المولد | NOT_RUN | adb/emulator/SDK غير متاحة |
| RPC/SQL وجهازان وT01–T50 | NOT_RUN | لا قاعدة تكامل مفوضة أو إثبات B08 |

المخرجات لا تُستبدل بنتائج متوقعة: `sqlite-results.txt`, `kotlin-results.txt`, `regression-*`, `runtime-environment.json` و`commands-and-results.md`. أول فشل compile حقيقي محفوظ في `kotlin-first-failure.txt`؛ أصلحت أخذ قيمة entityVersion محلية لتجاوز منع smart cast العابر للوحدات ثم أعيد compile ونجح.

**اختبارات موجودة لا نتائج نجاح:** 24 JVM جديدة (12 policy+12 validator)، و14 Room Inbox جديدة. زاد الملف المالي إلى34 Room بعد إضافة أصل متأخر، مع تحديث اختبارات القطع لتفريق receipt commit عن apply rollback. 12 مالية JVM قديمة محفوظة. تشغيل Gradle الكامل لهذه الملفات **NOT_RUN**؛ لا تُجمع فحوص shim/SQL لتصنيع PASS لـT25–T37.

## الاستئناف ومعايير الإغلاق

NEXT_SESSION=B10 وNEXT_TASK=B10.01. شغّل الأوامر المرفقة في بيئة Gradle8.9/Android مجهزة، أصلح الفشل، ولّد99 واختبر migration/fresh schema. أعد B09 المالية وسuites الحماية والتجميد والجدولة المتأثرة؛ ثم اختبر kill/restart قبل وبعد receive commit وداخل apply، X/Y/shared keys، الدفع/العكس قبل الأصل،1001 و2MiB/+1 والحصة/القرص وreplay. طابق بعد ذلك عقد B08/B20 فعليًا على قاعدة معزولة.

G-B09 وG-B10 تبقيان BLOCKED؛ المهام الخمس مكتوبة لكن لم تُغلق DONE. العوائق B10-BLK-01 للبيئة وB10-BLK-02 لعقد الخادم مضافة، وعوائق B02/B09 محفوظة. LAST_COMPLETED_SESSION=B06 وعدد المهام المكتملة28 ثابت؛ كل Txx بقي NOT_RUN. لا بدء B11 تلقائيًا، ولا بوابة سلامة ساقطة بسبب تفويض كتابة الكود.

هذا فحص وتنفيذ لمسار B10 وما يمسه من B09/جدولة/حماية، **وليس شهادة تدقيق لجميع ميزات Verto**. ملفات الزيارات السابقة محفوظة، والتغييرات المحددة وبصماتها في `change.patch` و`changed-files.json`.

## ضبط نطاق التغييرات والتوثيق

فحص change-contract المحلي نجح مقابل manifest مأخوذ من ZIP المصدر قبل التعديل. فحص التوثيق العام شُغّل لكنه ما زال FAIL بسبب141 خطأ قائم؛ بعد تحديث المرجع القانوني للـRPC لا توجد أخطاء إضافية قياسًا بتقرير B09-V02 المرفق. هذه مقارنة تقارير وليست إعادة تشغيل baseline مستقلة. لا يُعد ذلك اجتيازًا لبوابة جودة عامة أو إطلاق. ثلاثة تقارير B10 الجديدة أضيفت إلى جرد الوثائق، وحدث مرجع RPC وidempotency وخريطة التنفيذ والباك لوج مع حفظ التاريخ.
