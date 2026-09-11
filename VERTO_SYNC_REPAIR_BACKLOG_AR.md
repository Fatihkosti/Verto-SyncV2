# Backlog إصلاح مزامنة Verto — جلسات قابلة للاستئناف

**الملف المعتمد لحالة هذا الإصلاح:** `VERTO_SYNC_REPAIR_BACKLOG_AR.md`  
**معرّف الخطة:** `VERTO-SYNC-REPAIR-BACKLOG-01` · **الإصدار التنظيمي:** `1.0` · **تاريخ الإعداد:** `2026-09-10`  
**التصميم المرجعي:** العقد `VERTO-SYNC-REPAIR-01` إصدار `1.0`، محفوظ كاملًا حرفيًا في آخر هذا الملف.  
**الوضع عند إنشاء هذه الوثيقة:** خطة جاهزة للتنفيذ؛ **لم تُنفذ أي جلسة، ولم يُعدّل التطبيق أو السيرفر، وجميع الاختبارات NOT_RUN.**

> ابدأ من **لوحة الاستئناف**، لا من أول مهمة تتذكرها. مصدر التصميم هو العقد المضمّن، ومصدر التقدم هو هذا الملف. لا تعتمد على ذاكرة المحادثة أو كلمة «تم» دون تغيير واختبار ودليل.

## دليل القراءة السريع

[لوحة الاستئناف](#resume-state) · [قواعد كل جلسة](#session-protocol) · [جدول الجلسات](#session-index) · [بطاقات التنفيذ](#session-cards) · [تتبع الاختبارات](#test-tracker) · [العوائق والقرارات](#blockers) · [سجل التسليم](#handoff-log) · [العقد الكامل](#contract-reference)

**ما أضيف هنا:** تقسيم عمل العقد إلى 22 جلسة و110 مهام مرقمة، واعتمادياتها، وبوابات قبولها، وآلية الاستئناف وتحديث الأدلة. هذه إضافات تنظيمية وليست حلولًا تقنية جديدة. لا تختصر البطاقات أي قرار أو حقل أو اختبار من العقد؛ عند اختلاف الملخص مع النص المرجعي يوقف الفرع المختلف ويسجل العائق، ولا يختار المنفذ حلًا من عنده.

**مراجع البطاقات:** `C§9.3` تعني القسم 9.3 من العقد المضمّن؛ `Cملحق A` هو جرد الحقول الكامل، و`P01–P24` اختصارات مسارات مصدره في جدول الملفات أدناه. ترقيم العيوب R01–R13 والاختبارات T01–T50 محفوظ كما هو. `G-Bxx` بوابة عمل للجلسة، وليست اختبارات T جديدة أو بديلًا عنها.

الـ22 جلسة وحدات عمل منطقية، لا تقديرًا لعدد المحادثات أو مدة التنفيذ. إن لم تكتمل جلسة، تستأنف بالمعرّف نفسه في زيارة لاحقة؛ لا يعاد ترقيم بقية الخطة ولا تمحى الأعمال الجزئية.

<a id="resume-state"></a>
## 1. لوحة الاستئناف — تُحدّث بعد كل جلسة وقبل تسليمها

<!-- BACKLOG_STATE_BEGIN -->
```yaml
PLAN_ID: VERTO-SYNC-REPAIR-BACKLOG-01
PLAN_REVISION: 1
CONTRACT_ID: VERTO-SYNC-REPAIR-01
CONTRACT_VERSION: '1.0'
CONTRACT_EMBEDDED_SHA256: 34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0
SOURCE_ARCHIVE: Verto-425.zip
SOURCE_ARCHIVE_EXPECTED_SHA256: cb0a1956952376d75bfeb2d3ec71b0e06700024d5b95cd44f6b09db96855e1a0
SOURCE_BASELINE_STATUS: VERIFIED_USER_DESIGNATED_TREE_HASH
BASELINE_PRODUCT_COMMIT: null
EXECUTION_BRANCH: NO_GIT_TREE_HASH_MODE
LAST_WORKING_PRODUCT_COMMIT: null
LAST_VERIFIED_PRODUCT_COMMIT: null
LAST_PRODUCT_TREE_SHA256: 158fccae5d886f47b1101453d64406522512e8666a2b5cbcf0be6cdfad5a9711
WORKTREE_CHECKPOINT: B13-V02
OVERALL_STATUS: IN_PROGRESS_WITH_BLOCKERS
EXECUTION_MODE: EXECUTION_TREE_HASH_MODE
CURRENT_SESSION: B13
CURRENT_TASK: B13.01
CURRENT_VISIT: 2
LAST_COMPLETED_SESSION: B06
LAST_CHECKPOINT_ID: B13-V02
NEXT_SESSION: B13
NEXT_TASK: B13.01
NEXT_ACTION: 'B13.01: استأنف قبول B13 على بيئة Gradle8.9/Android/Room؛ شغّل migration100→101 واختبارات recovery/kill/T32–T34. بعد تنفيذ B08 seal fields شغّل T31/T18 والتكامل الفعلي؛ لا تبدأ B14 ولا تغلق G-B13 قبل الدليل.'
NEXT_ACTION_PRECONDITIONS: 'استأنف B13-V02؛ يلزم Gradle8.9 واعتماديات المشروع وAndroid SDK/Room ثم B08 server bootstrap seal/digest/coverage/high-watermark/delta-token وتكامل فعلي؛ الأوامر في docs/sync-repair/evidence/B13/V02/commands-and-results.md؛ لا SQL حي أو نشر تم.'
RESUME_FROM: 'B13-V02 أعاد التحقق دون تغيير منتج: product hash ثابت 158fccae...؛ static48/48 وSQLite وB06/B11/B12 regressions PASS. Gradle8.9 غير مخزن فعليًا، Android SDK/Room غير متاح، وB08 seal/live integration غير مثبتة؛ G-B13 BLOCKED ولا يبدأ B14.'
COMPLETED_SESSIONS: 5
TOTAL_SESSIONS: 22
COMPLETED_TASKS: 28
TOTAL_TASKS: 110
DEFERRED_CONDITIONAL_TASKS: 0
TESTS_PASS: 0
TESTS_FAIL: 0
TESTS_BLOCKED: 0
TESTS_NOT_RUN: 50
TESTS_RECHECK_REQUIRED: 0
ACTIVE_BLOCKER_IDS: [B02-BLK-01, B02-BLK-02, B09-BLK-01, B10-BLK-01, B10-BLK-02, B11-BLK-01, B12-BLK-01, B12-BLK-02, B13-BLK-01, B13-BLK-02]
WORK_IN_FLIGHT: []
BACKUP_GATE: BLOCKED_BACKUP_UNVERIFIED
LIVE_SERVER_CONTRACT_GATE: PASS_READ_ONLY_CATALOG_EXPORT
TEST_ENVIRONMENT_GATE: BLOCKED_FREE_PLAN_BRANCH_SKIPPED_AND_CLEAN_BASELINE_MISSING
SIGNING_GATE: PASS_INSTALLED_SIGNER_MATCHES_LOCAL
LIVE_WRITE_AUTHORIZATION: NOT_RECORDED
RELEASE_DELIVERY_AUTHORIZATION: NOT_RECORDED
USER_DEVICE_CASE_STATUS: NOT_VERIFIED
PRODUCTION_DEPLOYMENT_STATUS: NOT_STARTED
LAST_UPDATED_AT: '2026-09-11'
LAST_UPDATED_BY: 'B13-V02؛ إعادة تحقق قبول B13: المحلي PASS، المنتج بلا تغيير، Gradle/Android/B08/live integration محجوبة؛ B14 لم يبدأ؛ لا نشر أو APK'
```
<!-- BACKLOG_STATE_END -->

**الحقول أعلاه حالة ابتدائية، وليست عوائق مثبتة:** NOT_CHECKED لا تعني BLOCKED. يقرأ المنفذ فعليًا ويتحقق ثم يحدثها. التوكنات وكلمات المرور وبيانات الجهاز/المؤسسة الحساسة لا تكتب هنا.

**قاعدة المصدر بعد الجلسة الأولى:** فك المصدر وتثبيته عمل B01 فقط. كل جلسة لاحقة تستأنف آخر فرع/commit/عمل جزئي موثق من فرع الإصلاح، ولا تعيد فك Verto-425 فوق الإصلاحات ولا تعود إلى baseline. الأرشيف مرجع الأصل، وليس بديل آخر شجرة تنفيذ. أحدث GitHub ليس المصدر تلقائيًا. CURRENT_SESSION/CURRENT_TASK يوثقان آخر عمل جارٍ أو انتهى؛ NEXT_SESSION/NEXT_TASK هما مؤشر بدء الزيارة التالية، ولا تُعاد المهمة المغلقة لأن اسمها بقي في CURRENT.

## 2. قواعد الحالة والإغلاق

| المجال | الحالات المسموحة | قاعدة الاستخدام |
|---|---|---|
| المهمة | `TODO` / `IN_PROGRESS` / `DONE` / `BLOCKED` / `DEFERRED_CONDITIONAL` | DONE يعني تنفيذ المطلوب ودليله؛ التأجيل الشرطي مسموح فقط لـB22.03 وB22.04 وفق شروطهما، ولا يطبق على أي Txx. |
| الجلسة | `TODO` / `IN_PROGRESS` / `PARTIAL` / `BLOCKED` / `DONE` | PARTIAL تعني إنجازًا حقيقيًا مع عمل متبقٍ؛ BLOCKED عندما يمنع عائق الخطوة المطلوبة التالية. DONE بعد إغلاق المهام وبوابة G-Bxx وتحديث السجل. |
| الاختبار | `PASS` / `FAIL` / `BLOCKED` / `NOT_RUN` | حسب C§21.5. نجاح fixture أو فحص نصي لا يتحول إلى نجاح مسار كامل. |
| صلاحية دليل الاختبار | `NONE` / `CURRENT` / `RECHECK_REQUIRED` | لا تستخدم PASS قديمًا للشجرة الحالية؛ احتفظ بنتيجة تشغيله السابقة وأضف أنه يحتاج إعادة تحقق بعد تغيير مؤثر. |
| بوابة المرحلة E | `NOT_EVALUATED` / `IN_PROGRESS` / `PASS` / `BLOCKED` | لا ترث PASS من اكتمال جلسات كتابة محلية إذا غاب اختبار التكامل المطلوب. |
| العيب R | `OPEN` / `IN_PROGRESS` / `BLOCKED` / `CLOSED_VERIFIED` | الإغلاق يتطلب اختبارات C§21.6 الحديثة وأثر البيانات المطلوب، لا commit وحده. |

**إغلاق الجلسة ليس إغلاق العيب أو المرحلة أو حادثة المستخدم.** قد تكون جلسة محلية DONE بعد بوابتها المحلية بينما اختبارات جهازين لا تزال NOT_RUN وR لا يزال OPEN. إذا نصت بوابة الجلسة على SQL/Room/Storage فعلي فلا يبدلها المنفذ بmock ليغلقها. في B12 مثلًا نقص إثبات SQL يبقي بوابة التكامل والجلسة غير مكتملتين، ولو انتهى الجزء المحلي.

كل شرط سلامة في C§3 وC§23 ملزم لجميع الجلسات. لا حذف بيانات، ولا تعطيل M03، ولا ACK/CLEAN/APPLIED جماعي، ولا Legacy transport احتياطي، ولا اختراع بيانات مالية أو إعادة استعمال mutationId بمحتوى جديد. حدود النطاق الدقيقة في C§1.3 محفوظة؛ تقسيم العمل ليس تفويضًا بتغييرها.

<a id="session-protocol"></a>
## 3. بروتوكول بدء كل جلسة وإنهائها

### 3.1 بداية الجلسة

1. اقرأ لوحة الاستئناف، سجل آخر تسليم، جدول الجلسات، سجل العوائق، وبطاقة CURRENT_SESSION/الجلسة المختارة مع **أقسام العقد المحالة كاملة**. أول زيارة تقرأ العقد كاملًا، بما فيه الحقول والاختبارات والممنوعات؛ الزيارات التالية لا تكتفي بملخص البطاقة. اقرأ الأجزاء على دفعات إذا اقتُطع خرج الأداة؛ ظهور بداية الملف وحدها لا يعني قراءة العقد أو بطاقة الجلسة.
2. تحقق من بصمة العقد المضمّن، ومن الفرع وHEAD وحالة worktree ومصدر الشجرة بالمقارنة مع آخر checkpoint. افحص تغييرات المستخدم/جلسة أخرى ولا تحذفها. في B01 تُنشأ بيانات baseline لأول مرة ولا يعد كونها null قبل البدء عائقًا. بعد B01، غياب commit أو بصمة شجرة بديلة مثبتة معًا، أو اختلاف غير مفسر، يسجل عائق مصدر قبل أي كتابة متأثرة.
3. راجع الدليل الفعلي للمهام الموسومة DONE التي تعتمد عليها المهمة الحالية: وجود الملفات/الدوال، وإعدادات الربط، ونتائج التشغيل وcommit. إذا تعارضت الوثيقة مع الكود، سجل ذلك واجعل الجلسة PARTIAL/BLOCKED والمهمة المعنية IN_PROGRESS/BLOCKED بدل إعادة كل العمل أو تجاهل الاختلاف.
4. استأنف أول مهمة غير DONE في الجلسة الحالية ما دامت ممكنة وآمنة. إن كانت هناك عملية معلقة من جلسة سابقة، تحقق من نتيجتها بهويتها أولًا؛ لا تعيد تنفيذ ترحيل/نشر/إصلاح بيانات لمجرد انقطاع المحادثة.
5. حدّث CURRENT_SESSION/CURRENT_TASK وحالة الجلسة إلى IN_PROGRESS، وسجل فرع/commit البداية وCHECKPOINT_ID مثل `B05-V02` (زيارة ثانية للجلسة B05). نفذ نطاق هذه الجلسة فقط؛ إغلاقها لا يبدأ جلسة أخرى تلقائيًا في الاستدعاء نفسه.

### 3.2 اختيار المهمة التالية دون تخمين

```text
عند انتهاء/بدء زيارة:
  إن كان CURRENT_SESSION غير مكتمل وغير محجوب: استأنفه من أول مهمة غير DONE.
  وإلا:
    افحص الجلسات المتبقية حسب الرقم، واختر أول جلسة لم تغلق
    وقد أغلقت اعتمادياتها المحددة وبواباتها المطلوبة، ويمكن تنفيذها دون خرق عائق قائم.
  إن وجد BLOCKED زال سببه بدليل جديد: أعد تقييمه بأولوية الرقم، لا تقفز فوقه بلا سبب.
  لا تجعل B02 DONE لاستكمال محلي؛ استعمل فقط الاستقلال المعلن في الجدول.
  إن لم توجد جلسة آمنة مؤهلة:
    اترك CURRENT/NEXT على أقدم مهمة محجوبة، واجعل NEXT_ACTION خطوة فك العائق المحددة.
    لا تمثل NONE/COMPLETED نجاحًا مع بقاء عمل إلزامي غير متحقق.
```

**تفصيل استثناء العمل المحلي:** C§4.3 يسمح بكود واختبارات محلية عند تعذر قراءة عقد السيرفر، ولا يسمح بنشر SQL أو إصلاح حي. لذلك B03–B06 وB09–B16 وB18 لا تعتمد كلها على B02 إداريًا؛ لكن كل جلسة تظل مقيدة باعتمادياتها وبوابة إثباتها. أي جزء يحتاج SQL حيًا/اختباريًا موثقًا يبقى محجوبًا. لا تسقط بوابات E0/E2/E3 ولا تحقق جهازين اعتمادًا على هذا الاستثناء. إذا تطلبت الجلسة نفسها شرطًا غير متاح، أنجز الجزء الآمن ثم سجل PARTIAL/BLOCKED؛ لا تعلن DONE بجزء منها.

**ضيق السياق ليس سببًا لاختراع حل أو ضغط مرحلتين:** توقف عند حد آمن، سجل الملفات وما تغير وآخر اختبار وآخر نقطة التزام، ثم ضع NEXT_ACTION للمهمة الجزئية نفسها. لا تختزل المتبقي إلى «أكمل الإصلاح» ولا تغيّر IDs أو تقسيم العقد.

### 3.3 تحديث بعد كل مهمة أو حد مهم

حدّث صف المهمة فور تغير حالتها وأضف دليلها/اختبارها وcommit أو patch digest؛ ثم حدّث الجلسة والاختبارات والعوائق المرتبطة. احفظ checkpoint خاصًا قبل أي نشر/تفعيل/ترحيل بيانات، وبعد نتيجة العملية، وعند تغير النطاق. لا تنتظر آخر المحادثة لحفظ التقدم كله.

إذا انتهى الاختبار محجوبًا بالبيئة، سجل أمره وما منع تشغيله والملف/الإذن/الأداة المطلوبة. لا تعاود السبب نفسه مرارًا دون تغيير، ولا تسأل عن قرارات حسمها العقد. معلومة أو تفويض غير موجودين لا يستبدلان بتخمين.

### 3.4 نهاية الجلسة — إلزامية ولو لم تكتمل

1. سجل ما نفذ فعليًا، وما لم ينفذ، والملفات/الدوال التي تغيرت، وأوامر الاختبارات ونتائجها، والعمليات المعلقة. حدث خريطة التنفيذ والملفات المطلوبة من C§22 بحسب الجزء الذي أُنجز.
2. حدّث حالات المهام والجلسة والمرحلة والعيوب واختبارات T المعنية، مع صلاحية الدليل للشجرة الحالية. لا تغير PASS/FAIL قديمًا لمحو التاريخ؛ أضف سجل تشغيل جديدًا.
3. اكتب سجل تسليم بالصيغة في القسم 9، وحدّث `NEXT_SESSION` و`NEXT_TASK` و`NEXT_ACTION` و`RESUME_FROM` والخطوة التي لا تعاد. تتضمن NEXT_ACTION معرّف المهمة والفعل والملف/الدالة أو المدخل المفقود وشرط النجاح.
4. حدث Changelog، وافحص اتساق هذا الملف ثم احفظه في فرع العمل مع الأدلة. لكل جلسة مكتملة commit موثق وفق C§20؛ إذا تعذر Git فاحفظ patch/diff وبصمته وسجل العائق، ولا تدعِ رفعًا أو commit لم ينفذ.
5. في الرد اذكر الجلسة والمهام المنجزة، الاختبارات التي شغلت، المتبقي والعائق، NEXT_ACTION، والملف المحدث/commit. لا تنتقل تلقائيًا إلى جلسة ثانية، ولا تعد المستخدم بعمل خلفي.

**تجنب مرجع commit دائري:** ثبّت commit المنتج أولًا ثم سجل معرّفه في Backlog/Changelog بتغيير توثيقي. لا تحاول كتابة hash commit الوثيقة داخل نفسه أو تعديل commits السابقة من أجل ذلك. `LAST_WORKING_PRODUCT_COMMIT` يشمل آخر كود جزئي، و`LAST_VERIFIED_PRODUCT_COMMIT` آخر كود اختبرت بوابته؛ لا تجعل الأخير مساويًا للأول بلا دليل. افصل تغييرات التوثيق وحدها عن تغير شجرة المنتج في صلاحية الاختبارات.

### 3.5 قواعد التفويض والنشر

هذه الوثيقة لا تنفذ إصلاحًا الآن ولا تضيف تلقائيًا إذن نشر إنتاجي أو تعديل بيانات أو رفع GitHub/Drive. عند تفويض التنفيذ، يسجل المنفذ حدوده من طلب المستخدم الصريح والتعليمات القائمة. إعداد migrations واختبارها يكون في البيئة المعزولة؛ الإنتاج فقط بعد تحقق العقد والنسخ والسياج والبوابات.

B22.03 (انتقال حي) وB22.04 (بناء/تسليم Release) مشروطتان بتفويضهما ومدخلاتهما. إن لم يطلبهما المستخدم، يمكن تسجيل `DEFERRED_CONDITIONAL` مع السبب ونطاق الإغلاق الاختباري، وليس DONE. لا تعرقل بهما كتابة تقرير تسليم اختبار صحيح، لكن تبقى حالة الإنتاج/جهاز المستخدم غير متحققة، ويمنع الادعاء بأن المستخدم استلم APK أو أصلح جهازه. لا يؤجل أي Txx بهذه الآلية؛ بناء/ترقية الاختبار T48 شرط قبول مختلف عن تسليم Release إلى المستخدم.

### 3.6 عند انتهاء النطاق المفوض

إذا أغلقت المهام الإلزامية والبوابات والعيوب والاختبارات وفق C§22 في **النطاق المعلن**، سجل `OVERALL_STATUS: CLOSED_VERIFIED_DECLARED_SCOPE` وحدد ذلك النطاق في سجل التسليم. يمكن حينئذ فقط جعل NEXT_SESSION/NEXT_TASK فارغتين، وNEXT_ACTION عبارة صريحة: «لا توجد مهمة مفوضة متبقية؛ لا تعِد B01 ولا تبدأ نشرًا أو بناءً جديدًا دون طلب». لا تغيّر USER_DEVICE_CASE_STATUS أو PRODUCTION_DEPLOYMENT_STATUS إلى نجاح إن لم يتحقق نطاقهما فعليًا.

إذا كانت B22.03/B22.04 غير مطلوبتين ومؤجلتين شرطيًا، تبقيان DEFERRED_CONDITIONAL، ولا تدخلان في عدد المهام DONE. تقفل جلسة التسليم في نطاقها الموثق دون ادعاء نشر أو تسليم Release. أما إذا كانتا مطلوبتين ولم تنفذا، أو بقي اختبار/عيب/دليل إلزامي غير متحقق، فلا إغلاق عام: NEXT_ACTION تبقى على العمل أو العائق المحدد، ولا يختزل النقص إلى «انتهت الخطة».

<a id="session-index"></a>
## 4. جدول الجلسات والاعتماديات

تنفذ الجلسة المؤهلة ذات الرقم الأصغر، مع أولوية استئناف الجاري. العمود «الاعتماديات» مطلوب قبل الإغلاق والتنفيذ المعتمد؛ الاستثناء المحلي لا يبيح خرق شرط مالي أو أمني. البوابات الإضافية داخل بطاقة كل جلسة ملزمة أيضًا.

| الجلسة | المرحلة | نطاق الجلسة | الاعتماديات | الحالة | المهام DONE | بوابة الجلسة | آخر دليل/زيارة |
|---|---|---|---|---|---:|---|---|
| [B01](#b01) | E0 | تثبيت المصدر وتهيئة التنفيذ | لا شيء | DONE | 5/5 | PASS | B01-V02؛ المصدر عيّنه المستخدم، tree hash مثبت، وP04 المفقود مسجل |
| [B02](#b02) | E0 | النسخ المتحقق ومطابقة السيرفر وبيئة الاختبار | B01 | BLOCKED | 3/5 | BLOCKED | B02-V02؛ توقيع المثبت PASS؛ النسخة محجوبة والفرع المدفوع تخطاه المستخدم |
| [B03](#b03) | E1 | ترقية Room وإضافة مخازن التنظيم والنسخ | B01 | DONE | 5/5 | PASS | B03-V01؛ schema 97؛ JVM 27/27 وRoom 5/5 |
| [B04](#b04) | E1 | سجل الملكية والحماية الموحدة | B03 | DONE | 5/5 | PASS | B04-V01؛ 35/35؛ JVM 91/91 وAndroid 5/5 وapp compile PASS |
| [B05](#b05) | E1 | الطلبات الثابتة والنسخ والتنظيف المشروط | B03، B04 | DONE | 5/5 | PASS | B05-V01؛ JVM 95/95؛ Android 12/12؛ app compile وgolden hash PASS |
| [B06](#b06) | E2 | DTO v2 ومصانع اللقطة الكاملة وربط المنتجين | B05 | DONE | 5/5 | PASS | B06-V01؛ schema/producer PASS؛ JVM 195/195؛ Room 2/2؛ app compile PASS |
| [B07](#b07) | E2 | خادم المجموعات والإيصالات وعدم تكرار الأثر | B02، B05، B06 | TODO | 0/5 | NOT_EVALUATED | — |
| [B08](#b08) | E2 | قراءة الخادم القانونية وDelta وBootstrap | B07 | TODO | 0/5 | NOT_EVALUATED | — |
| [B09](#b09) | E3 | التطبيق المالي الفعلي داخل Room | B03، B04، B06 | BLOCKED | 0/5 | BLOCKED | B09-V02: إصلاح shared writeId مع حفظ factId؛ 33 Room و12 JVM تنتظر التشغيل؛ G-B09 BLOCKED |
| [B10](#b10) | E3 | Inbox الدائم والتبعيات والصفحات الكبيرة | B05، B09 | BLOCKED | 0/5 | BLOCKED | B10-V01: الكود والاختبارات وترحيل99 مكتوبة؛ 15 SQLite و12 policy نجحت محليًا؛ Room/JSON/B08 غير متحققة |
| [B11](#b11) | E3 | واجهة حل التعارض والتدقيق | B10 | BLOCKED | 0/5 | BLOCKED | B11-V01: التنفيذ المحلي مكتوب؛ static/SQLite PASS؛ Gradle/Room/T30 وG-B10/G-B11 معلقة |
| [B12](#b12) | E3 | المصروف القابل للتحديث وتاريخ النقد | B06، B09، B11 | BLOCKED | 0/5 | BLOCKED | B12-V01 |
| [B13](#b13) | E3 | Bootstrap الآمن فوق البيانات المعلقة | B04، B05، B10، B11 | BLOCKED | 0/5 | BLOCKED | B13-V02: المنتج بلا تغيير؛ static48/48 وSQLite/regressions PASS؛ Gradle8.9/Android/B08/T18/T31–T34 ما زالت محجوبة |
| [B14](#b14) | E4 | مخطط M03 وإصلاح الفواتير والدفعات والأدوار | B05، B06، B13 | TODO | 0/5 | NOT_EVALUATED | — |
| [B15](#b15) | E4 | تصنيف Optimal وربط حاجز M03 | B14 | TODO | 0/5 | NOT_EVALUATED | — |
| [B16](#b16) | E4 | ترميم APPLIED القديمة والتوفيق مع المصادر | B09، B13، B14، B15 | TODO | 0/5 | NOT_EVALUATED | — |
| [B17](#b17) | E5 | دورة مستندات الشحن كاملة | B02، B05، B06، B08، B13 | TODO | 0/5 | NOT_EVALUATED | — |
| [B18](#b18) | E5 | الحجوزات والجدولة وطلب الإيقاظ الدائم | B05، B10، B15 | TODO | 0/5 | NOT_EVALUATED | — |
| [B19](#b19) | E5 | المنسق الموحد وحالة المستخدم والإلغاء | B08، B11، B12، B13، B15، B16، B17، B18 | TODO | 0/5 | NOT_EVALUATED | — |
| [B20](#b20) | E6 | القبول السلوكي وجهازان والترقية الاختبارية | B19 | TODO | 0/5 | NOT_EVALUATED | — |
| [B21](#b21) | E6 | إزالة Legacy بعد إثبات البديل | B20 | TODO | 0/5 | NOT_EVALUATED | — |
| [B22](#b22) | E6 | التحقق النهائي والتسليم والانتقال المحكوم | B21 | TODO | 0/5 | NOT_EVALUATED | — |

### 4.1 بوابات المراحل الأصلية — لا تختصر ببوابات الجلسات

| المرحلة الأصلية | جلسات العمل | بوابة الانتقال وفق العقد C§20 | الحالة | الدليل/العائق |
|---|---|---|---|---|
| E0 | B01–B02 | المصدر والنسخ والتعريفات مثبتة، أو BLOCKED صريح لا PASS. | BLOCKED | B01 PASS؛ B02 نسخة الجهاز/البيئة المعزولة BLOCKED مع تجاوز الفرع المدفوع |
| E1 | B03–B05 | اختبارات الهجرة وCAS وثبات المحتوى والحماية تجتاز. | PASS | B03–B05 PASS محليًا؛ B05 JVM 95/95 وAndroid 12/12 وapp compile؛ لا يرقّي Txx أو بوابة E2 |
| E2 | B06–B08 | round-trip PostgreSQL وإسقاط الرد ومنع ازدواج الأثر تجتاز. | NOT_EVALUATED | — |
| E3 | B09–B13 | استعادة مالية كاملة فوق قاعدة نظيفة وفوق pending محمي، مع تكرار آمن. | BLOCKED | B13-V02 أعاد تحقق المحلي دون تغير منتج؛ G-B09/G-B10/G-B11/G-B12/G-B13 وB07/B08 غير متحققة؛ لا تكامل فعلي أو جهازين |
| E4 | B14–B16 | لا مصدر مجهول مخفي؛ نتائج dry-run ثم اختبارات قاعدة الحالة متطابقة. | NOT_EVALUATED | — |
| E5 | B17–B19 | لا pending مع COMPLETED، وملف حقيقي موثق بعد انقطاع وتشغيل. | NOT_EVALUATED | — |
| E6 | B20–B22 | كل معايير القسم 21 مثبتة؛ التقرير لا يرقّي اختبارات لم تُنفذ. | NOT_EVALUATED | — |

B20 تختبر البدائل قبل الإزالة؛ T50 الذي يتضمن غياب المسار القديم يغلق في B21، ثم يعاد التحقق من T01–T50 على المنتج النهائي في B22. لا تُعلن E6 ناجحة لمجرد اكتمال B20.

### 4.2 ملفات المصدر: اختصارات البطاقات

هذه المسارات من **ملحق B الأصلي** وليست إثبات قراءة جديد للكود في إعداد هذا الـBacklog. يكشف المنفذ مواضع الدوال الحالية أثناء التنفيذ ويحفظها في خريطة التسليم؛ المواضع القديمة لا تنسب تلقائيًا إلى commit معدل.

| الرمز | المسار في المصدر |
|---|---|
| P01 | `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt` |
| P02 | `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManagerPorts.kt` |
| P03 | `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt` |
| P04 | `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2IntentRepairCoordinator.kt` |
| P05 | `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2MigrationCoordinator.kt` |
| P06 | `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncMigrationPlanner.kt` |
| P07 | `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt` |
| P08 | `data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt` |
| P09 | `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedFinancialOwner310Route.kt` |
| P10 | `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedStrongerSourceFactory.kt` |
| P11 | `data/sync/src/main/kotlin/com/verto/app/data/sync/push/SyncV2PushCoordinator.kt` |
| P12 | `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt` |
| P13 | `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt` |
| P14 | `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt` |
| P15 | `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt` |
| P16 | `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt` |
| P17 | `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOrchestrationDao.kt` |
| P18 | `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOutboxDao.kt` |
| P19 | `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncProducerV307Dao.kt` |
| P20 | `data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt` |
| P21 | `data/operations/src/main/kotlin/com/verto/app/data/repository/CashReconciliationRepository.kt` |
| P22 | `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsShipmentStoreAdapters.kt` |
| P23 | `app/src/main/kotlin/com/verto/app/feature/integration/optimal/bridge/OptimalInvoiceIntegrationOutboxAdapter.kt` |
| P24 | `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt` |

<a id="session-cards"></a>
## 5. بطاقات التنفيذ — حدّث صفوف المهام ولا تستبدل نصها بملخص

لكل مهمة: الدليل المطلوب جزء من تعريف DONE. اكتب في عمود الدليل مسارًا فعليًا وموضع اختبار/commit أو checksum، لا «تم». تستكمل مخرجات البطاقة متراكمة في فرع واحد؛ لا تعيد إنشاء ملفات أدلة سابقة بحذف محتواها. مراجعة العقد المحددة في كل بطاقة تسبق التعديل.

<a id="b01"></a>
### B01 — تثبيت المصدر وتهيئة التنفيذ

**المرحلة:** E0 · **الاعتماديات:** لا توجد  
**اقرأ التصميم قبل التعديل:** C§1–4.1، C§20–23، Cملحق B وC  
**عيوب العقد المعنية:** R01–R13  
**مناطق العمل:** الأرشيف؛ تعليمات المشروع الموجودة فعلًا؛ SOURCE_BASELINE.json؛ هذا الملف؛ CHANGELOG.md.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B01.01 | احسب SHA-256 لأرشيف Verto-425.zip وقارنه بالبصمة المثبتة؛ افحص بنية الأرشيف ثم فكّه إلى مساحة عمل غير مختلطة بمصدر آخر. | بصمة مطابقة ومسار مصدر موثق؛ عند الاختلاف SOURCE_DIFF.md ولا اعتماد صامت لأحدث GitHub. | DONE | تعيين المستخدم أن المجلد الحالي هو المصدر المفكوك؛ `SOURCE_DIFF.md` يحفظ أن hash حاوية ZIP غير قابل لإعادة الحساب، وبصمة الشجرة في `SOURCE_BASELINE.json` |
| B01.02 | اقرأ تعليمات المشروع الفعلية واعرف وحداته ومسارات البناء والاختبارات وأماكن عمل المزامنة؛ سجّل ما وجد وما لم يوجد دون إنشاء تعليمات منتج جديدة. | قائمة مسارات وتعليمات وأوامر مكتشفة من المشروع؛ لا ادعاء وجود AGENTS أو Master Backlog غير موجود. | DONE | `docs/sync-repair/evidence/B01/V01/source-and-project-discovery.md`؛ README/CONTRIBUTING/settings/scripts مفحوصة، ولا AGENTS أو Backlog عام |
| B01.03 | ثبّت فرع إصلاح واحدًا من المصدر المحدد، وسجّل baseline commit أو بصمة شجرة؛ احفظ حالة الملفات غير الملتزمة الموجودة قبل العمل دون حذفها. | SOURCE_BASELINE.json يحدد أصل الكود؛ LAST_VERIFIED_PRODUCT_COMMIT أو tree hash صحيح؛ لا reset/clean/force-push لإخفاء اختلاف. | DONE | `SOURCE_BASELINE.json`؛ المصدر مقبول بتعيين المستخدم وبصمة ما قبل التنفيذ `4c5fa594...e13a0`؛ لا Git، فاستُخدم بديل C§4.1 دون اختراع commit |
| B01.04 | أنشئ هيكل أدلة التنفيذ وأوامر الاختبارات المثبتة من ملفات المشروع؛ افصل احتياجات JVM وRoom وPostgreSQL وجهازين والتوقيع، وحدد حالتها دون افتراض توفرها. | EVIDENCE_INDEX.md وأوامر قابلة للتنفيذ عند توافر البيئة؛ لا تنسب خطأً لمكتبة أو SDK لم تفحصه. | DONE | `EVIDENCE_INDEX.md` و`docs/sync-repair/evidence/B01/V01/commands-and-exit-codes.md`؛ البيئات مفصولة؛ بوابة التوثيق شُغلت عبر bash وفشلت، والمتكاملة خرجت 126 |
| B01.05 | ضع هذا الملف في جذر فرع التنفيذ؛ إن وجد Backlog عام فحدّث فيه رابطًا لهذا المسار دون نسخ حالة المهام أو استبدال محتواه؛ أضف قيد بدء الإصلاح إلى Changelog. | مصدر حالة واحد للإصلاح، ومؤشر NEXT_ACTION يشير إلى B02.01 بعد إغلاق B01؛ لا تغييرات مزامنة في هذه الجلسة. | DONE | الملف في الجذر و`CHANGELOG.md` محدث ولا Backlog عام؛ NEXT_ACTION أصبح B02.01؛ لا تغييرات مزامنة |

**بوابة الجلسة:** G-B01: تطابق المصدر، وإمكانية إعادة تحديد الشجرة، وصحة مسارات أوامر الاختبار وسجل التعليمات. ليس مطلوبًا الادعاء أن البناء نجح إن لم يُشغّل.

**اختبارات العقد المرتبطة:** لا Txx يُعتمد كناجح في هذه الجلسة؛ بوابة المصدر/التحضير فقط.

**التسليم المطلوب:** SOURCE_BASELINE.json؛ EVIDENCE_INDEX.md؛ دليل المصدر والتعليمات؛ سجل B01.

**حدود التوقف وعدم الاجتهاد:** لا تغيير كود الأعمال ولا SQL ولا بيانات الجهاز. اختلاف المصدر يمنع اعتماد أي إصلاح على نسخة غير محددة.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b02"></a>
### B02 — النسخ المتحقق ومطابقة السيرفر وبيئة الاختبار

**المرحلة:** E0 · **الاعتماديات:** B01  
**اقرأ التصميم قبل التعديل:** C§4.2–4.4، C§18.3–18.4، C§19.2–19.3، C§21.5  
**عيوب العقد المعنية:** R01، R06، R13  
**مناطق العمل:** نسخة Room وملفاتها المشار إليها؛ تعريفات السيرفر الحية قراءة فقط؛ migrations وRLS وStorage؛ لا تعديل إنتاجي.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B02.01 | احصل على نسخة SQLite متسقة مع WAL وفق آلية النسخ الصحيحة ونسخ الملفات المشار إليها؛ اختبر قراءة نسخة منفصلة واحفظ بصماتها وبيانها في مساحة خاصة. | BACKUP_VERIFICATION يثبت الاتساق والقراءة؛ غياب النسخة أو فشلها = BLOCKED_BACKUP_UNVERIFIED، لا بديل هو ZIP الكود. | BLOCKED | docs/sync-repair/evidence/B02/V01/BACKUP_VERIFICATION.md؛ لا جهاز متصل ولا DB/WAL/SHM/مرفقات |
| B02.02 | صدّر تعريفات RPC والنطاق والسحب وBootstrap والجداول والقيود والفهارس والمشغلات والإيصالات وRLS والمنح وملكية الدوال وسجل migrations من المشروع الفعلي، قراءة فقط. | SYNC_SERVER_DEFINITIONS/ مع المشروع والوقت وبصمات التعريفات؛ أسماء migrations وحالة المشروع الصحية لا تكفي. | DONE | docs/sync-repair/evidence/B02/V01/SYNC_SERVER_DEFINITIONS/؛ تعريفات كاملة وبصمات و214 migration وStorage |
| B02.03 | قارن التعريفات المنشورة بالمصدر وحدد حقيقة مالك كل أثر مالي ومخزني وعمولة ومجال صلاحية الإيصالات؛ وثق الانحرافات دون تصحيح تلقائي للبيانات. | خريطة trigger/owner والقيود والأعمدة الفعلية؛ الانحراف غير المحسوم يمنع المسار المتأثر والنشر. | DONE | SERVER_SOURCE_DRIFT.md؛ خريطة live owner/scope وانجراف RPC/migrations دون تعديل بيانات |
| B02.04 | جهّز مشروع/مؤسسة PostgreSQL اختبارية معزولة ونسخة بيانات معقمة؛ ثبّت كيفية إعادة بناء المخطط دون تعديل migrations تاريخية مطبقة. | إعادة بناء اختبارية موثقة؛ عند تعذر قراءة الحي BLOCKED_LIVE_CONTRACT_UNVERIFIED وتبقى أي SQL غير مخولة للنشر. | BLOCKED | TEST_ENVIRONMENT_AND_FENCE.md؛ لا فرع معزول/خادم محلي/fixture، ومصدر SQL التاريخي ناقص |
| B02.05 | وثق خطة السياج ونطاق المؤسسة وقراءة الإيصالات بعده والقفل org/principal/epoch وخطة الرجوع؛ تحقق من توقيع النسخة المثبتة دون مشاركة أسرار. | خطة fence وفق C§4.4؛ حالة التوقيع معلومة أو BLOCKED_SIGNING؛ لا تفعيل fence على الإنتاج في جلسة التحضير. | DONE | TEST_ENVIRONMENT_AND_FENCE.md؛ خطة مكتملة وAPK المحلي v2 صحيح؛ توقيع المثبت محجوب لغياب الجهاز |

**بوابة الجلسة:** G-B02: نسخة بيانات مقروءة مثبتة، وتعريفات حيّة مكتملة قابلة للمقارنة، وبيئة اختبار معزولة، وسياج/رجوع موثقان. الجزء غير المتاح يبقى BLOCKED ولو أُنجز غيره.

**اختبارات العقد المرتبطة:** لا Txx يُعتمد كناجح في هذه الجلسة؛ بوابة المصدر/التحضير فقط.

**التسليم المطلوب:** SYNC_SERVER_DEFINITIONS/؛ تقرير فروق؛ BACKUP_VERIFICATION خاص؛ بيان بيئة الاختبار والسياج والتوقيع.

**حدود التوقف وعدم الاجتهاد:** تعذر B02 لا يمنح PASS لـE0. يسمح فقط بالمهام المحلية المستقلة المصرح بها في جدول الاعتماديات، دون نشر أو إصلاح بيانات حي.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b03"></a>
### B03 — ترقية Room وإضافة مخازن التنظيم والنسخ

**المرحلة:** E1 · **الاعتماديات:** B01  
**اقرأ التصميم قبل التعديل:** C§6.1–6.9، C§9.1، C§12، C§16.2، C§18.2، C§19.1، Cملحق A.14–A.16  
**عيوب العقد المعنية:** R01، R03، R05–R12  
**مناطق العمل:** مخطط Room وEntities/DAOs/migrations؛ P17–P19؛ الحقول المحددة في C§6 والملحق A.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B03.01 | أضف migrations غير مدمرة للجداول والحقول: entity_version، local_generation، mutation_packet، pending_reference، write_batch/member، inbox_group، حقول cursor، migration_evidence_v2، وsnapshot_blob المحدد في C§18.2. | مخطط كامل مطابق للمفاتيح والأنواع والـnullability والفهارس؛ لا صندوق نقل بديل ولا مسح لسجل قديم. | DONE | `AppDatabaseMigrations96To97.kt`؛ schema `97.json`؛ `schema-and-invariants.md` |
| B03.02 | أضف حقول دورة المرفقات وexpense_revision_history، وأصول Minor لجلسات الجرد وفئات النقد؛ نفذ تحويل المال القديم مرة واحدة وفق Money.fromLegacyDouble وscale=2/HALF_UP. | دليل تحويل يحتفظ بالقيم الأصلية؛ NaN/Infinity/overflow أو نقص إثبات يتحول إلى مراجعة لا تقريب مبتكر. | DONE | Room fixture أثبت القيم الدقيقة و`MONEY_MIGRATION_REVIEW_REQUIRED`؛ B03-V01 |
| B03.03 | نفذ DAO للنسخ observed/applied والأجيال والتسلسل؛ اجعل تغييرات النسخة المطبقة والصفوف داخل معاملة التطبيق، لا بمجرد تلقي حدث. | اختبارات فصل observed عن applied، وتمثيل النسخة المجهولة null لا 0، وتسلسلات غير مستنتجة من الوقت. | DONE | `SyncRepairV2Dao.kt`؛ 3 اختبارات Room DAO PASS |
| B03.04 | ثبت القيود المؤسسية ومفاتيح المجموعة وترتيب الأعضاء 0..size-1 وقواعد عدم الحذف المتسلسل للأدلة؛ وفر اختبارات اكتشاف orphan للمراجع متعددة المالكين. | فشل المعاملة عند خرق القيود وعدم إسقاط بيانات مصدر؛ لا FK وهمي لجميع المالكين إلى sync_outbox. | DONE | SQLite constraint/no-cascade + JVM multi-owner orphan tests PASS |
| B03.05 | شغل Room instrumentation لترقية قاعدة قديمة غير فارغة، وافحص سجلات الأعمال والصناديق والأجيال والمؤشرات والمرفقات قبل وبعد. | G-B03 يثبت migration ومعاملات حقيقية؛ اختبار JVM أو قراءة نص migration لا يعادل Room. T48 الكامل يُستكمل في B20. | DONE | Pixel_8 AVD Android 17؛ Room focused 5/5 PASS؛ B03-V01 |

**بوابة الجلسة:** G-B03: المخطط الجديد يعمل بترقية Room فعلية، ويحفظ القديم، وتنجح اختبارات الأنواع والنسخ والمعاملات. غياب Android/Room يمنع DONE لهذا الإثبات.

**اختبارات العقد المرتبطة:** T48

**التسليم المطلوب:** Room schemas وmigrations؛ اختبارات DAO/Room؛ dumps منقحة وبصمات قبل/بعد؛ دليل G-B03.

**حدود التوقف وعدم الاجتهاد:** لا network داخل Migration.migrate؛ لا ملء bytes قديمة من الصف الحالي؛ لا CLEAN أثناء schema migration.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b04"></a>
### B04 — سجل الملكية والحماية الموحدة

**المرحلة:** E1 · **الاعتماديات:** B03  
**اقرأ التصميم قبل التعديل:** C§5، C§6.4 و6.8–6.9، C§7، C§14.2–14.4، Cملحق A.16 وB  
**عيوب العقد المعنية:** R01، R06، R09، R10، R11  
**مناطق العمل:** SyncOwnershipRegistry؛ SyncPendingProtection؛ P02، P08، P15–P19؛ منتجو الصناديق الذين تثبتهم خريطة الاستدعاءات.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B04.01 | استخرج الأنواع الـ35 والصناديق المتخصصة خارج سجلها من المنتج الفعلي؛ وثق لكل نوع producer/owner/identity/protected keys/mutability/serializer/applier/recovery/terminal predicate. | SYNC_OWNERSHIP_MATRIX.csv بأسماء دوال ومواقع استدعاء؛ SERVER_ONLY يحتاج دليلًا؛ غير المحسوم BLOCKED_OWNER_UNPROVEN. | DONE | `docs/sync-repair/evidence/B04/V01/SYNC_OWNERSHIP_MATRIX.csv`؛ 35/35 + attachment |
| B04.02 | نفذ Registry بملكية C§7: المالية في financial_outbox، الأدوار في party_sync_outbox، المخزون والتكلفة في مالكيهما، owner310 في sync_outbox، وOptimal/المرفقات في مالكيهما. | لا نسخ لصناديق أقوى إلى العام؛ PAYMENT جذره invoiceId ويحمل paymentId؛ الدور بمفتاح org/partyId/role. | DONE | `SyncOwnershipRegistry.kt`؛ `SyncOwnershipRegistryTest` 3/3 ضمن sync JVM |
| B04.03 | نفذ pending references ذات التبعيات والبصمات والأجيال، واربط نشاطها بحالة المالك الحقيقية في المعاملة نفسها. | اختبارات لكل حالة معلقة بما فيها REJECTED/BLOCKED/LOCAL_RETAINED المحمي؛ لا Boolean حماية مستقل متقادم. | DONE | `SyncPendingProtection.kt` + `SyncRepairV2Dao.kt`؛ Android/Room 3 حالات حماية ضمن 5/5 |
| B04.04 | اجعل Pull وBootstrap وM03 والصحة والخروج تستخدم SyncPendingProtection نفسها؛ احذف خرائط الحماية المتناقضة بعد ربط بديلها. | استدعاءات مثبتة للمسارات الخمسة؛ حفظ الفاتورة وبنودها وأصل العكس والجرد وفئاته والمرفق وأمه. | DONE | `docs/sync-repair/evidence/B04/V01/call-map.md`؛ app compile PASS |
| B04.05 | أضف تغطية آلية للمنتجين تكشف الكتابة غير المسجلة وorphan references؛ اختبر عدم تبني صف بلا مؤسسة. | G-B04 يختبر الحماية بمحتوى الصف لا هوية outbox فقط؛ T33 الكامل لا يعتمد حتى تشغيل Bootstrap فعلي. | DONE | Registry 35/35؛ orphan/unscoped Party Android assertions؛ G-B04 PASS؛ T10/T33 NOT_RUN |

**بوابة الجلسة:** G-B04: كل producer معروف له مالك مثبت وحماية واحدة فعالة، أو عائق صريح يمنع تفعيل النوع. وجود نوع إنتاجي غير محسوم يمنع اعتماد التغطية الكاملة.

**اختبارات العقد المرتبطة:** T10، T33

**التسليم المطلوب:** SYNC_OWNERSHIP_MATRIX.csv؛ خريطة الاستدعاءات؛ اختبارات registry/protection/orphans.

**حدود التوقف وعدم الاجتهاد:** لا default owner بالاسم، ولا تجاهل FAILED/REJECTED، ولا نسبة الصفوف القديمة إلى المؤسسة النشطة بالتخمين.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b05"></a>
### B05 — الطلبات الثابتة والنسخ والتنظيف المشروط

**المرحلة:** E1 · **الاعتماديات:** B03، B04  
**اقرأ التصميم قبل التعديل:** C§6.1–6.5 و6.8، C§8.7، C§9 كاملًا، C§18.2  
**عيوب العقد المعنية:** R03، R05، R07، R09  
**مناطق العمل:** FrozenMutationStore؛ SyncBatchCoordinatorV2؛ P08، P11، P17، P18؛ adapters المالكين.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B05.01 | نفذ حفظ intent_json وhash/generation/refs وبيان المجموعة وتسلسلها في معاملة الكتابة نفسها؛ فصل intent الدائم عن wire الذي يجهز مرة واحدة. | rollback كامل لأي فشل؛ لا enqueue مستقل بعد حفظ الأعمال؛ الأعضاء تظل في صناديقهم الأصلية. | DONE | B05-V01؛ `captureUnified/captureOwner` و`seal`؛ rollback وmulti-key Room PASS |
| B05.02 | نفذ frozen wire UTF-8 بلا BOM وبصمة النص نفسه وsnapshot blobs الثابتة عند استعمالها؛ retries تعيد البايتات نفسها دون إعادة تسلسل. | golden bytes/hash tests تشمل العضو والمجموعة؛ التوكن وleaseToken خارج النص الثابت. | DONE | B05-V01؛ `FrozenMutationCodec` و`prepareOnce`؛ golden SHA `3c92c39c…5b16` واختبار batch retry PASS |
| B05.03 | اربط baseVersion بالنسخة المطبقة أو إيصال السابقة؛ احفظ predecessor ومحتوى كل تعديل محلي على حدة. | تعديلان Offline لا يتداخل محتواهما؛ لا استخدام observedVersion لتجاوز تعارض ولا NULL→0. | DONE | B05-V01؛ predecessor ينتظر ACK ثم يأخذ serverVersion=6؛ bytes مستقلة وRoom PASS |
| B05.04 | نفذ claim/leaseToken/scopeEpoch بمدة 120 ثانية وتجديد 30 ثانية، وACK بإيصال مطابق ثم CAS على generation/contentHash وعدم وجود نية أحدث. | Room tests لرد حجز قديم وACK لنسخة N بعد تعديل N+1 وبند فقط؛ تأكيد النية القديمة لا ينظف الأحدث. | DONE | B05-V01؛ schema 98 وlease renewal/CAS؛ stale epoch وreceipt mismatch وN→N+1 PASS محليًا |
| B05.05 | نفذ فروع الطلب القديم: bytes محفوظة، إيصال مطابق، أو OUTCOME_UNKNOWN؛ احفظ مراجع الهوية القديمة دون إعادة استعمال mutationId لمحتوى مختلف. | G-B05 يثبت الثبات وCAS والحجز والتبعية؛ اختبارات قبول السيرفر الفعلية T14–T16 تستكمل في B07 لا بمحاكاة نجاحه. | DONE | B05-V01؛ `classifyPreviousMutation`؛ exact bytes/matching receipt/OUTCOME_UNKNOWN JVM PASS؛ Txx لم ترقَّ |

**بوابة الجلسة:** G-B05: اختبارات منطق الإنتاج وRoom تثبت ثبات الطلب والنسخ المتعاقبة والحماية والتنظيف المشروط. لا يعتمد اكتمال payload المالي قبل B06.

**اختبارات العقد المرتبطة:** T08، T15، T17، T23، T39

**التسليم المطلوب:** اختبارات packet/lease/CAS/predecessor؛ fixtures وبصمات نصوص؛ تحديث خريطة الملكية.

**حدود التوقف وعدم الاجتهاد:** لا تحديث wire أو baseVersion بعد التجميد؛ لا ACK بلا receipt/hash؛ لا تنظيف عام بالهوية أو lineCount.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b06"></a>
### B06 — DTO v2 ومصانع اللقطة الكاملة وربط المنتجين

**المرحلة:** E2 · **الاعتماديات:** B05  
**اقرأ التصميم قبل التعديل:** C§9.1–9.3، C§10.1–10.4، C§11، C§18.1–18.2، Cملحق A كاملًا وB  
**عيوب العقد المعنية:** R02، R04، R05، R07، R08  
**مناطق العمل:** FinancialSnapshotFactoryV2؛ P07–P10، P20، P21، P24؛ منسقو معاملات البيع/الشراء/الدفعات.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B06.01 | أنشئ DTOs/serializers وJSON Schema كاملة من حقول الملحق A والقواعد القانونية، مع contractFamily/version وpayloadVersion كما في C§9.3. | SYNC_CONTRACT_V2.schema.json بلا placeholders؛ قواعد enum/null/Minor/overflow والمراجع وأسماء wire مثبتة. | DONE | `SYNC_CONTRACT_V2.schema.json`؛ 40 defs؛ schema gate PASS؛ DTO/validator tests 7/7 |
| B06.02 | نفذ FinancialAggregateSnapshotV2 بكل القوائم وexplicitTombstones/effectReferences؛ احسب hash projection وفق C§10.2 ورتب السجلات كما نص العقد. | بند يتغير مع ثبات الإجمالي/العدد يغير hash؛ لا lineCount بديلًا عن البنود ولا حقل مالي ساقط. | DONE | `FinancialSnapshotFactoryV2`؛ exact wire SHA-256 `8869bb43...f2b7`؛ item-only hash test PASS |
| B06.03 | انقل ختم appendInvoice واللقطات إلى نهاية معاملة حفظ التوابع قبل commit؛ اربط حفظ snapshot والأجيال والنيات وrefs/batch بجميع المنتجين الماليين. | اختبارات rollback تربط الرأس والبنود والأقساط والدفع وFX والمرتجع والآثار بالمعاملة؛ PAYMENT لا يغير جذر invoiceId. | DONE | `FinancialOutboxWriter` + coordinators؛ frozen packet/generation/ref/sealed batch؛ Room commit/rollback 2/2 PASS |
| B06.04 | انقل materialization وpurchaseRequest للمصروف والجرد/الفئات والائتمان والتكلفة ودورة الشراء من prepare إلى معاملة المنتج دون تغيير مخطط المجال غير المعدل بالعقد. | prepare يقرأ اللقطة فقط؛ GOODS_RECEIPT/MATCH/OVERRIDE تحتفظ بتوابعها الفعلية؛ خلاف الحقول = CONTRACT_FIELD_MISMATCH. | DONE | producer snapshots للمصروف/النقد/الجرد/الائتمان/الشراء؛ prepare بلا AppDatabase؛ retry tests 2/2 PASS |
| B06.05 | نفذ خرائط الحركة/التكلفة/CLIENT_CREDIT camelCase ومصدر Minor، وتجنب إعادة تحويل constructors من Double؛ اختبر حقول المعرفة التاريخية والحدود ومراجع الآثار. | G-B06 واختبارات schema/golden/production transaction؛ الربط الفعلي بالخادم وRoom لكل نوع يغلق في B08. | DONE | الحركة/التكلفة/الائتمان DTO v2 وsourcePaymentId؛ schema+producer gates PASS؛ JVM 195/195؛ app compile PASS |

**بوابة الجلسة:** G-B06: كل حقول الملحق A لها حكم منفذ ومختبر، والمنتج يلتقط الحالة الكاملة ذريًا، والمصانع لا تقرأ حالة أحدث عند retry. لا واجهة empty في الإنتاج.

**اختبارات العقد المرتبطة:** T09، T12، T19، T20

**التسليم المطلوب:** SYNC_CONTRACT_V2.schema.json وfixtures؛ snapshots/hashes golden؛ خريطة المنتج→المعاملة→المالك.

**حدود التوقف وعدم الاجتهاد:** لا اختراع أعمدة/عملة/FX/تكلفة تاريخية ولا تسرب privateUri؛ صور الفواتير ليست توسعة لناقل مستندات الشحن.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b07"></a>
### B07 — خادم المجموعات والإيصالات وعدم تكرار الأثر

**المرحلة:** E2 · **الاعتماديات:** B02، B05، B06  
**اقرأ التصميم قبل التعديل:** C§4.3–4.4، C§9.3–9.4، C§10.4، C§18 كاملًا  
**عيوب العقد المعنية:** R02، R05، R07، R13  
**مناطق العمل:** migrations جديدة في بيئة اختبار؛ capabilities/scope/batch/receipts؛ P11، P24؛ triggers المثبتة في B02.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B07.01 | نفذ capabilities وresolve scope وbatch/receipts v2 بالأسماء والمدخلات في C§18.1، مع صلاحيات فعلية مشتقة من Auth وتعريفات منشورة ببصمات. | مطابقة schema typed وSQL الحقيقي؛ لا principal يختاره العميل ولا اسم RPC موجود يستبدل بصمت. | TODO | — |
| B07.02 | نفذ حساب hash من النص قبل JSONB والتحقق من العضو والمجموعة والblobs والتبعيات والنسخ وحجم المجموعة. | reject للbody/baseVersion المختلف مع الهوية نفسها؛ غياب member/blob/أثر يمنع commit المجموعة. | TODO | — |
| B07.03 | نفذ قفل المؤسسة والجذور بترتيب ثابت وإعادة فحص الإيصالات تحت القفل؛ خصص revisions ونسخًا وإيصالات مع الحقائق في معاملة واحدة. | اختبار إرسالين متزامنين وتكرار 10 مرات؛ لا watermark يتجاوز معاملة لم تلتزم ولا إيصال اقتصادي جزئي. | TODO | — |
| B07.04 | وحّد أصحاب الآثار والمشغلات مع business identities ومفاتيح عدم التكرار؛ نفذ reconciliation للحقائق الموجودة دون إعادة عملية البيع. | دفعة/مخزون/نقد/عمولة لا تتضاعف؛ duplicates تاريخية غير محسومة تمنع القيود والنشر ولا تُحذف تلقائيًا. | TODO | — |
| B07.05 | اربط مرسل العميل بالمجموعات المختومة والنيات المستقلة، وأعد إيصالات FOUND/NOT_FOUND ضمن horizon/OUTCOME_UNKNOWN وفق السياج؛ شغل اختبارات SQL الفعلية وإسقاط الرد. | T14–T16 وT47 بالمسار الحقيقي مع سجل الطلب والإيصال؛ اختبارات RLS/receipt horizon/capabilities؛ لا إرسال عضو batch منفردًا. | TODO | — |

**بوابة الجلسة:** G-B07: PostgreSQL اختباري فعلي يثبت ذرية المجموعة، replay/idempotency، صلاحيات الإيصالات، وسلوك الرد المفقود. أي mock وحده لا يغلق البوابة.

**اختبارات العقد المرتبطة:** T14، T15، T16، T23، T45، T47، T49

**التسليم المطلوب:** migrations إضافية؛ تعريفات/بصمات؛ نتائج SQL/عميل؛ receipts/hash/concurrency evidence.

**حدود التوقف وعدم الاجتهاد:** نشر هذه الجلسة اختباري فقط. لا تعديل إنتاجي قبل بوابة الانتقال النهائية، ولا تعطيل شامل triggers أو SECURITY DEFINER لتجاوز RLS.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b08"></a>
### B08 — قراءة الخادم القانونية وDelta وBootstrap

**المرحلة:** E2 · **الاعتماديات:** B07  
**اقرأ التصميم قبل التعديل:** C§10.5–10.6، C§11، C§13.1–13.2، C§14.1، C§15، C§18.1 و18.3–18.4  
**عيوب العقد المعنية:** R02، R04، R05، R11، R12، R13  
**مناطق العمل:** read aggregate/pull/begin bootstrap/bootstrap page v2؛ P12، P13 للأنواع الثلاثة، P24.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B08.01 | نفذ read aggregate والـDelta بDTO قانوني من السجلات المقبولة لا p_payload الخام؛ أعد حقول sequence/recordedAt/serverAcceptedAt من مصدرها الفعلي. | خرائط الحركة والتكلفة والائتمان تطابق C§11 والملحق؛ لا fallback من ساعة الهاتف أو صفر. | TODO | — |
| B08.02 | نفذ المجموعات الكاملة وtouchedKeys/dependsOnTransactionIds وopaque cursors؛ أعد projection manifest حسب رؤية المستخدم دون كشف مفاتيح مخفية. | المجموعة المرئية كاملة أو SCOPE_DEPENDENCY_UNAVAILABLE؛ اختبار رؤية متعددة المؤسسات داخل SQL. | TODO | — |
| B08.03 | نفذ جلسة Bootstrap ثابتة مع highWatermark/Delta token وختم counts/digest/coverage، وصفحات الكيانات المالية الكاملة ونسخها. | اختبارات تغير الخادم بين الصفحات لا تغير لقطة الجلسة؛ لا آخر حدث مالي مختصر بدل snapshot أعمال. | TODO | — |
| B08.04 | طابق قياس 2,097,152 bytes وsoftLimit=1000؛ اختبر مجموعة 999/1000/1001 والحد القانوني وفوقه على واجهة السحب. | لا skip أو loop أو split تجاري للمجموعة القانونية؛ خطأ حجم صريح عند التجاوز. | TODO | — |
| B08.05 | عدل مستقبلات الحركة والتكلفة والائتمان للDTO القانوني، وشغل encode→PostgreSQL→decode→Room؛ اربط handshake/registries/routes دون fallback قديم. | T19 كامل وT20 للحقول/الأرقام؛ اختبارات scope/capabilities. المطبق المالي العام يأتي في B09 وليس inbox بديلًا عنه. | TODO | — |

**بوابة الجلسة:** G-B08: round-trip SQL/Room للأنواع الثلاثة، وعقود aggregate/Delta/Bootstrap متحققة بالبيئة الاختبارية، مع اختبارات النطاق والأحجام.

**اختبارات العقد المرتبطة:** T19، T20، T29، T35، T36، T45، T49

**التسليم المطلوب:** تعريفات قراءة/bootstrap؛ fixtures/DTO parity؛ SQL/Room results؛ تحديث schema وdefinition fingerprints.

**حدود التوقف وعدم الاجتهاد:** لا تعتبر snapshot فارغة إذن مسح؛ لا رفع version ثابت وحده ولا استخدام migration تاريخية لتخمين أعمدة حية.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b09"></a>
### B09 — التطبيق المالي الفعلي داخل Room

**المرحلة:** E3 · **الاعتماديات:** B03، B04، B06  
**اقرأ التصميم قبل التعديل:** C§5، C§10 كاملًا، C§13.2، Cملحق A.1–A.9 وB  
**عيوب العقد المعنية:** R02، R03، R06، R11  
**مناطق العمل:** FinancialMaterializerV2؛ P02، P13؛ DAOs المالية؛ مسار REMOTE_APPLY وربط Hilt.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B09.01 | نفذ التحقق من scope/DTO/hash/financialStreamVersion والمراجع؛ مصدر بيانات العميل/المورد/الصنف يجب أن يكون مرجعًا مثبتًا لا اسم snapshot ينشئ بديلًا. | إدخال عقد ناقص لا يصبح APPLIED؛ الفاتورة بلا عميل تتبع تمثيل المشروع لا سجلًا مخترعًا. | BLOCKED | B09-V02: strict scope/DTO/hash/version محفوظ؛ corrected PAYMENT writeId correlation؛ 12 JVM NOT_RUN؛ evidence/B09/V02/session-report.md |
| B09.02 | نفذ معاملة تطبيق الرأس والبنود والأقساط ثم الدفعات الأصلية والعكس والتخصيصات وFX ثم مستندات/سطور المرتجع وتخصيصاتها وفق التبعيات. | اختبارات Room فعلية لكل جدول وحقل وFK؛ لا نصف مجموعة عند قتل/فشل المعاملة. | BLOCKED | B09-V02: المطبق السابق محفوظ؛ 33 Room test تشمل عكسًا متعددًا لنفس write؛ Room NOT_RUN؛ 612 source/SQLite PASS ليست Room |
| B09.03 | نفذ selective insert/update، وإثبات tombstone وحماية المراجع؛ قارن immutable facts بالهوية/hash وmutable entities بالنسخة. | لا REPLACE لجذر يمسح أبناءه؛ لا حذف غائب من partial/FULL بلا شروطه؛ المطابق no-op والمختلف conflict. | BLOCKED | B09-V02: ABORT/selective وimmutable guards محفوظة؛ اختبار تغيير حقيقة مشتركة writeId أضيف؛ schema98 لم يتغير؛ Room NOT_RUN |
| B09.04 | اربط الآثار بأصحابها داخل المجموعة دون إنشاء نقد أو مخزون أو عمولة ثانية؛ snapshot تتضمن دفعة ورسالة دفع لنفس الحقيقة لا تضاعفها. | assertions على مفاتيح الحقائق ومجاميع Minor/المخزون؛ لا استدعاء منتجي الأعمال المحليين في REMOTE_APPLY. | BLOCKED | B09-V02: fact identity فريد؛ PAYMENT writeId قد يتكرر لحقائق مختلفة؛ 3 semantic checks مع codec stub PASS؛ Room/JSON NOT_RUN |
| B09.05 | حدث applied versions/APPLIED/checkpoint بعد نجاح الأعمال في المعاملة نفسها؛ اربط المطبق الحقيقي في مسار الاستقبال وHilt. | G-B09 يختبر Room ومنطق الإنتاج؛ T11–T13/T31 عبر جهازين تنتظر B20 إن لم تتوفر المنظومة بعد. | BLOCKED | B09-V02: ربط Pull/Hilt والمعاملة السابق محفوظ؛ 12 JVM و33 Room وapp/KSP/Hilt NOT_RUN؛ G-B09 BLOCKED |

**بوابة الجلسة:** G-B09: جميع مكونات اللقطة تطبق فعليًا في Room ذريًا، وتنجح اختبارات الحقول/المراجع/منع التكرار. التخزين في FinancialInbox وحده FAIL.


**زيارة B09-V01:** نفذت أقسام B09.01–B09.05 في الشجرة المرفقة، وأُزيل مسار حفظ FinancialInbox فقط من استقبال INVOICE/PAYMENT. لم تُغلق المهام لأن دليل Room المطلوب لم يعمل في هذه البيئة. `31` اختبار Room و`9` JVM موجودة؛ فحوص المصدر/SQLite (`612`) وKotlin mapping/Money (`347`) ليست بديلًا عن G-B09. تفاصيل الربط والحدود والأوامر في `docs/sync-repair/evidence/B09/V01/session-report.md`. لا تفويض مسجل لتجاوز هذه البوابة، ولا B10 منفذة ضمن هذه الزيارة.


**زيارة B09-V02:** المصدر مطابق لـB09-V01 (1851 ملف منتج). كشف التحقق رفضًا خاطئًا عند اشتراك دفعات عكس مختلفة في `writeId`، وأُصلح مع بقاء `factId` وhash مستقلين. أضيفت 3 اختبارات JVM و2 Room؛ الإجمالي 12/33 غير مشغلة. أُعيد إنتاج الخطأ قبل التصحيح ونجح فحص Kotlin الدلالي بعده مع codec stub صريح، وليس تشغيل JSON/Room. بوابة G-B09 والمهام الخمس BLOCKED؛ لا B10 ولا تغيير سيرفر. الأدلة: `docs/sync-repair/evidence/B09/V02/session-report.md`.

**اختبارات العقد المرتبطة:** T11، T12، T13، T26، T29، T31

**التسليم المطلوب:** materializer وRoom tests؛ dumps/hash/Minor parity محلية؛ خريطة Hilt→المطبق.

**حدود التوقف وعدم الاجتهاد:** يسمح تطويره محليًا إن حُجبت B07/B08؛ لا إعلان E3 أو مزامنة جهازين PASS قبل استيفاء عقد السيرفر والتكامل.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b10"></a>
### B10 — Inbox الدائم والتبعيات والصفحات الكبيرة

**المرحلة:** E3 · **الاعتماديات:** B05، B09  
**اقرأ التصميم قبل التعديل:** C§6.6، C§13.1–13.3، C§15، C§17.1–17.2  
**عيوب العقد المعنية:** R09، R11، R12  
**مناطق العمل:** DurableInboxApplyCoordinator؛ P12، P13، P17؛ sync_inbox/group/cursor.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B10.01 | احفظ المجموعات الكاملة وبيانها وreceivedCursor في معاملة قصيرة بعد تحقق scope/hash/count/order؛ كرر الحدث المطابق no-op وارفض اختلاف revision نفسه. | قتل قبل commit يعيد الصفحة وبعده يستأنف apply؛ لا شبكة داخل المعاملة ولا تقدم رمز بلا payload محفوظ. | BLOCKED | B10-V01: validator/manifests وreceive CAS مع inbox/group/wake ذري؛ migration99؛ SQLite 15 PASS ضمن حدودها؛ Room/kill/JSON NOT_RUN؛ evidence/B10/V01/session-report.md |
| B10.02 | افصل RECEIVED/READY/APPLIED عن WAITING_LOCAL/WAITING_DEPENDENCY/REQUIRES_REVIEW، واستخدم الحماية الموحدة وtouchedKeys/touched DTO keys. | X المتعارضة محفوظة وY المستقلة تطبق؛ مجموعة تمس X أو تعتمد عليها تنتظر ولا تنقسم. | BLOCKED | B10-V01: حالات مستقلة وحماية كل DTO key + dependencies؛ X/Y/shared-key tests مضافة؛ G-B10 Room NOT_RUN؛ evidence/B10/V01/session-report.md |
| B10.03 | نفذ applied checkpoint وفق نهاية مجموعات مغطاة مطبقة، وأيقظ التطبيق بgeneration دائم عند حسم النية/التبعية. | لا MAX(revision) عمياء ولا cursor مصطنع؛ دفعة/عكس قبل الأصل تنتظر ثم تستأنف دون أصل بديل. | BLOCKED | B10-V01: covered APPLIED prefix وdurable generation + post-commit wake؛ اختبار reversal-before-original أضيف؛ لا cursor محلي؛ Room/عملية Android NOT_RUN؛ evidence/B10/V01/session-report.md |
| B10.04 | نفذ ميزانية 1000 المرنة مع السماح بأول مجموعة قانونية 1001؛ طبق maxGroupBytes ومهلة المتابعة عند نهاية مجموعة ملتزمة. | اختبارات الحدود 999/1000/1001 و2MiB وما فوق؛ لا loop MORE_AVAILABLE ولا إعادة جلب انتظار محلي. | BLOCKED | B10-V01: أول مجموعة1001 و2MiB وميزانية عابرة للصفحات؛ 12 policy طرق نجحت مع assertion shim؛ JSON الحقيقي/سيرفر NOT_RUN؛ evidence/B10/V01/session-report.md |
| B10.05 | نفذ حد Inbox غير المطبق 64MiB لكل scope وهامش صفحة قانونية كما C§15 وفحص المساحة؛ لا حذف لأحداث unapplied. | G-B10: حالات امتلاء القرص/الحصة لا تتقدم كنجاح؛ WAITING_STORAGE_OR_REVIEW مع استمرار تطبيق المتاح. | BLOCKED | B10-V01: quota64MiB/هامش2MiB وStatFs وSQLiteFull rollback؛ SQL quota/disk harness PASS؛ Room/قرص Android/B08 NOT_RUN؛ evidence/B10/V01/session-report.md |

**زيارة B10-V01:** نُفّذت الكتابة المحلية للأقسام الخمسة بطلب المستخدم الصريح «نفذ b10» بعد B09-V02؛ ذلك ليس إعفاءً من G-B09 أو G-B10. أضيف مخطط99 ومخازن metadata لا Inbox موازٍ، وتوقف احتساب الاستلام APPLIED. 15 اختبار SQLite و12 سياسة Kotlin نجحت ضمن حدودها؛ 24 JVM جديدة و14 Room Inbox و34 Room مالية تحتاج تشغيلها الحقيقي. غياب Gradle/Android وعقد B08 المختبر يمنع الإغلاق. لم تُنفذ B11 أو أي SQL حي. الدليل: `docs/sync-repair/evidence/B10/V01/session-report.md`.

**بوابة الجلسة:** G-B10: اختبارات Room/process-boundaries والتبعيات وحدود المجموعة/المساحة تثبت الاستلام الدائم والتطبيق الذري والمستقل. عقد SQL يثبت عبر B08/B20 أيضًا.

**اختبارات العقد المرتبطة:** T25، T26، T27، T28، T29، T35، T36، T37

**التسليم المطلوب:** اختبارات Inbox/cursor/groups/quota؛ dumps قبل/بعد مع received/applied؛ دلائل kill/replay.

**حدود التوقف وعدم الاجتهاد:** لا تعطيل حماية pending لتقديم الصفحة، ولا تمييز APPLIED بعد استلام فقط، ولا balance snapshot تفترض أحداثًا ما زالت معلقة.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b11"></a>
### B11 — واجهة حل التعارض والتدقيق

**المرحلة:** E3 · **الاعتماديات:** B10  
**اقرأ التصميم قبل التعديل:** C§6.8، C§8.7، C§9.4، C§13.4، C§17.3  
**عيوب العقد المعنية:** R03، R07، R11  
**مناطق العمل:** واجهة/VM المراجعة الفعلية؛ منطق resolution؛ packet/receipt adapters؛ المطبق الموحد.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B11.01 | اعرض نوع السجل وسبب الخلاف والنسخة المحلية والبعيدة والحقول المختلفة بصورة قابلة للتتبع دون أسرار. | واجهة تقرأ الحالة الدائمة لا رسالة عامة؛ كلا المحتويين وبصمتهما محفوظان. | BLOCKED | B11-V01: UI + durable evidence + redaction مكتوبة؛ static PASS؛ Gradle/Compose/Room NOT_RUN |
| B11.02 | لmutable فقط نفذ اختيار اعتماد السيرفر بصلاحية مستخدم وحفظ النسخة المحلية والنية وسجل القرار. | SUPERSEDED_WITH_PROOF بمصدر القرار لا ACK؛ لا إنهاء نية outcome مجهول قبل إيصالها. | BLOCKED | B11-V01: auth/CAS/audit/superseded proof مكتوبة؛ تحفظ newer local intent؛ runtime NOT_RUN |
| B11.03 | نفذ إعادة إرسال التعديل المحلي باختيار صريح على النسخة البعيدة المعروضة، ومراجعة domain وmutation جديدة تشير إلى supersedes. | الطلب القديم ثابت؛ baseVersion الجديدة من النسخة المعتمدة؛ الإغلاق بعد receipt الجديدة/تخلٍ موثق. | BLOCKED | B11-V01: replacement mutation + exact displayed base + supersedes proof chain مكتوبة؛ runtime NOT_RUN |
| B11.04 | امنع استبدال immutable facts؛ استخدم فقط أمر عكس/تصحيح موجود مثبت في المجال، وإلا DOMAIN_CORRECTION_REQUIRED. | لا last-write-wins/server-wins آلي ولا دمج مبالغ ولا زر يستبدل حقيقة دفع/مخزون. | BLOCKED | B11-V01: غير OPTIMISTIC_VERSION يفشل إلى DOMAIN_CORRECTION_REQUIRED بلا أزرار أو auto-winner؛ runtime NOT_RUN |
| B11.05 | اختبر الخيارين، الصدى المطابق، رفض الصلاحية، وتغير النسخة/مجهول المصير؛ اجعل حسم الحالة يوقظ التطبيق الدائم. | G-B11/T30 ضمن البيئة المستخدمة، مع سجل قبل/بعد وbytes/receipts؛ لا فقد للنسخة التي رفض المستخدم التخلي عنها. | BLOCKED | B11-V01: static/SQLite contract PASS؛ Gradle/Room/UI/T30 actual environment NOT_RUN |

**بوابة الجلسة:** G-B11: مسارا القرار المحددان يعملان على الكود الفعلي ويحتفظان بالدليل؛ حقائق immutable ومجهول المصير محميان.

**اختبارات العقد المرتبطة:** T27، T28، T30

**التسليم المطلوب:** اختبارات resolution/صلاحية/واجهة؛ سجل قرارات منقح؛ ربط المسارات بخريطة التنفيذ.

**حدود التوقف وعدم الاجتهاد:** لا يتخذ المنفذ قرارًا ماليًا نيابة عن المستخدم ولا يوسع قائمة أوامر التصحيح خارج المجال المثبت.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b12"></a>
### B12 — المصروف القابل للتحديث وتاريخ النقد

**المرحلة:** E3 · **الاعتماديات:** B06، B09، B11  
**اقرأ التصميم قبل التعديل:** C§6.9، C§9.1، C§10.4، C§12، Cملحق A.13 وB  
**عيوب العقد المعنية:** R08، R02، R07  
**مناطق العمل:** P20؛ Expense DTO/applier؛ expense_revision_history؛ batch/حقيقة النقد؛ SQL المقابل المثبت.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B12.01 | احتفظ expenseId ونفذ mutable versioned update/history محليًا والخادم، بمفتاح org/expenseId/serverVersion والنسخة السابقة وwriteId وبصمات قبل/بعد. | لا إعادة هوية المصروف ولا IMMUTABLE_EXPENSE_FACT_CONFLICT لملاحظة/مبلغ مشروع. | BLOCKED | B12-V01: implementation + static/SQLite PASS؛ Room/SQL الفعلي NOT_RUN |
| B12.02 | نفذ التقاط النية الكاملة في معاملة تعديل المصروف وربطها بـbaseVersion وbatch النقد، مع حماية pending في المستقبل. | المنتج وحده ينشئ أثر التعديل مرة واحدة؛ retry لا يقرأ مصروفًا أحدث. | BLOCKED | B12-V01: frozen intent/batch/dependency implemented؛ Gradle/Room NOT_RUN |
| B12.03 | حافظ على ExpenseRepository/CashRegisterManager: E=Minor عند ACTIVE وصفر عند VOID، والأثر النقدي -(E_new-E_old). | ملاحظة=0؛ 10000→15000=-5000؛ VOID=+15000؛ لا تكرار إجمالي المصروف أو اختيار سياسة غير نقدية جديدة. | BLOCKED | B12-V01: pure Kotlin/static values PASS؛ Room cash facts NOT_RUN |
| B12.04 | اجعل الخادم يتحقق من السابق والجديد ومراجع فرق النقد ويرفض المجموعة غير المتوازنة؛ قارن التعريفات المنشورة بالمسار. | انحراف المجال BLOCKED_EXPENSE_DOMAIN_DRIFT؛ لا تصحيح معادلة مرتين في الخادم والمطبق. | BLOCKED | B12-V01: SQL/migration written + static PASS؛ nonzero cash fail-closed حتى B07؛ PostgreSQL NOT_RUN |
| B12.05 | شغل تسلسل T21 وتعارض المصروف T27 على التطبيق وRoom ثم SQL الفعلي؛ قارن history/نسخ/حقائق النقد. | G-B12 المحلي كامل؛ بوابة الخادم تبقى BLOCKED إن لم تتوفر B07، ولا يقفل R08 قبل الدليل الشامل. | BLOCKED | B12-V01: T21 arithmetic precursor only؛ T21/T27 app+Room+SQL NOT_RUN؛ G-B12 BLOCKED |

**بوابة الجلسة:** G-B12: سياسة C§12 منفذة ومختبرة في معاملة المنتج والمطبق، وإثبات SQL النقدي مطلوب لاعتماد التكامل. لا تغيير إلى مصروف immutable.

**اختبارات العقد المرتبطة:** T21، T27

**التسليم المطلوب:** expense history schema/migration؛ اختبارات delta/versions/replay؛ reconciliation النقد؛ تحديث DTO/خريطة التنفيذ.

**حدود التوقف وعدم الاجتهاد:** العمل المحلي مستقل عن توفر الإنتاج، لكن تعذر اختبار الخادم لا يتحول إلى PASS للنقد أو إغلاق R08.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b13"></a>
### B13 — Bootstrap الآمن فوق البيانات المعلقة

**المرحلة:** E3 · **الاعتماديات:** B04، B05، B10، B11  
**اقرأ التصميم قبل التعديل:** C§6.1 و6.6، C§8.8، C§14 كاملًا، C§19.1 و19.3  
**عيوب العقد المعنية:** R01، R05، R06، R11  
**مناطق العمل:** P14–P16؛ staging/promotion؛ SyncPendingProtection؛ versions وM03 gate؛ FinancialMaterializerV2.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B13.01 | احفظ snapshot كاملة في staging وتحقق من session/coverage/counts/digest/watermark/token قبل الترقية؛ اجعل مراحل الاستعادة دائمة قابلة للاستئناف. | snapshot منتهية/فارغة/ناقصة أو scope متغير لا تمسح قاعدة غير مؤكدة. | BLOCKED | B13-V01: كود + static/SQLite PASS؛ Room/B08/live acceptance غير متاح |
| B13.02 | التقط بيان hashes للنيات والصفوف والملفات المحمية، وأعد فحص حماية كل صف داخل معاملة promotion لا من نتيجة الجرد القديمة. | الكتابات التي وصلت بعد staging محمية أيضًا؛ جميع المالكين مستخدمون لا financial_outbox فقط. | BLOCKED | B13-V01: content manifest لكل owners + recheck داخل tx؛ runtime acceptance محجوب |
| B13.03 | اترك المحمي محليًا وادخل البعيدة WAITING_LOCAL؛ طبق غير المحمي بـREMOTE_APPLY وحدّث authority النسخ داخل معاملته. | 200/CLOSED تبقى أمام 100/OPEN؛ تعديل بعد Bootstrap يستعمل applied version 9 لا null/observed بديلة. | BLOCKED | B13-V01: WAITING_LOCAL/APPLIED + same-tx authority مكتوبة؛ T18/T32/Room غير مشغلة |
| B13.04 | طبق شروط tombstone/النطاق/المراجع والحذف المسموح لـCRUD فقط؛ لا تنقية مالية/مخزنية من الغياب، ولا حذف صف بلا مؤسسة مثبتة. | اختبارات كل owner والمرفقات وعدم وجود إثبات المؤسسة؛ hashes قبل/بعد متطابقة للبيانات المحمية. | BLOCKED | B13-V01: explicit tombstone fail-closed ولا absence prune؛ T33/Room/live غير مشغلة |
| B13.05 | اختبر قتل العملية في حدود staging/promotion وتغيير المؤسسة وإعادة الاستئناف؛ اربط بوابة M03 بحيث تسمح staging/الدفع المستقل دون ترقية مدمرة. | G-B13 Room كامل؛ T31/T18 عبر خادم فعلي وBootstrap نظيف يُعتمد فقط عند توفر B08 والتكامل. | BLOCKED | B13-V01: SQLite rollback/process boundary + M03 static PASS؛ Android/kill/B08/T31/T18 NOT_RUN |

**بوابة الجلسة:** G-B13: لا يتغير محتوى محمي أو bytes نية، والنسخ تغذى بعد تطبيق فعلي، وكل فشل استعادة قابل للاستئناف. اختلاف hashes يمنع الاكتمال.

**اختبارات العقد المرتبطة:** T18، T31، T32، T33، T34، T46

**التسليم المطلوب:** manifest قبل/بعد؛ اختبارات recovery/owners/scope/kill؛ سجل مراحل الاستعادة.

**حدود التوقف وعدم الاجتهاد:** لا clearAllTables ولا reset cursor لمعالجة نقص قديم، ولا إعادة backup فوق بيانات جديدة دون مصالحة.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b14"></a>
### B14 — مخطط M03 وإصلاح الفواتير والدفعات والأدوار

**المرحلة:** E4 · **الاعتماديات:** B05، B06، B13  
**اقرأ التصميم قبل التعديل:** C§6.7، C§8.1–8.5 و8.7–8.8، C§9.4، C§19.1  
**عيوب العقد المعنية:** R01، R03، R07  
**مناطق العمل:** M03RepairPlannerV2؛ P04–P06؛ migration_evidence_v2؛ source owner adapters.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B14.01 | نفذ جردًا متسقًا للمؤسسة وبياناتها وصناديقها مع raw type/operation/version/هوية/حالة/محاولات/حجز/بصمة وربط الإيصالات؛ لا شبكة داخل الجرد. | fixture 54 بتقسيم المصدر وfixture بعدد مختلف؛ العد مشتق من الصفوف لا constant ولا التاريخ كله. | TODO | — |
| B14.02 | طبق القرارات الخمسة في C§8.3 بدليل كامل، مع سجل evidence v2 ومصادره وبقاء journal v1. | pending/receipt/create/local-retained/review لا تستنتج من اسم الحالة أو history.isNotEmpty. | TODO | — |
| B14.03 | اجمع invoice/bندها بنفس org/invoiceId والتقط مجموعة كاملة؛ استخدم UUID-v5 بnamespace ومدخلات C§8.4 وRECONCILE_EXISTING_FINANCIAL_STATE. | رابطا مصدر إلى نية واحدة بالهوية التجارية القديمة؛ golden vector ثابت؛ لا INVOICE_CREATED مزيف أو أثر بيع جديد. | TODO | — |
| B14.04 | أصلح الدفعات والأدوار في مالكيها بعد إثبات أصل العكس وallocations/FX والمؤسسة؛ حافظ على إشارات المبالغ والهويات والعملة التاريخية المعروفة/المجهولة. | غياب أصل/عملة/مرجع غير مثبت = مراجعة مسماة؛ لا دفعات أو أدوار بديلة ولا تنظيف دون CAS. | TODO | — |
| B14.05 | نفذ التخطيط/dry-run ثم apply المحلي الحتمي مع الاستئناف، واختبر التشغيل مرتين والقتل وحدود المعاملات والطلب القديم مجهول المصير. | G-B14 يثبت T01/T02/T03/T07 والتغطية المرتبطة؛ عينة 54 ليست إثبات إغلاق حادثة جهاز المستخدم. | TODO | — |

**بوابة الجلسة:** G-B14: كل fixture source لها قرار بدليل، والنيات حتمية وذرية، والهوية والبيانات محفوظة. الأجهزة/الإيصالات الفعلية لها إثبات مستقل لاحق.

**اختبارات العقد المرتبطة:** T01، T02، T03، T07، T08، T09، T10، T23

**التسليم المطلوب:** SYNC_M03_RECONCILIATION.md قسم fixtures؛ evidence v2؛ golden repair IDs؛ اختبارات idempotence/kill/dry-run.

**حدود التوقف وعدم الاجتهاد:** لا نسخ إصلاح موجود وإعلان نجاح الحادثة؛ C§8.1 يذكر أن S6 يحتوي إصلاحًا بالفعل ولا يثبت تطابق APK التشخيص معه.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b15"></a>
### B15 — تصنيف Optimal وربط حاجز M03

**المرحلة:** E4 · **الاعتماديات:** B14  
**اقرأ التصميم قبل التعديل:** C§6.7–6.8، C§7، C§8.6–8.8، C§17.1 و17.3  
**عيوب العقد المعنية:** R01، R06، R09  
**مناطق العمل:** P05، P06، P23؛ OptimalBackendContractGate/mappers الفعلية؛ M03 gate وhealth inputs.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B15.01 | طبع raw type فقط بـtrim+uppercase(Locale.ROOT) مع حفظ الأصل؛ طابق type+operation+payloadVersion+المراجع على جدول C§8.6 كاملًا. | VEHICLE/MAINTENANCE/FOLLOW_UP/INVOICE/CONVERSATION ذات بوابات محددة؛ لا تخمين محتوى العمليات الـ26. | TODO | — |
| B15.02 | وجه عمليات الفاتورة/الدفع/الصيانة/المحادثة إلى الجسر المتخصص الأصلي دون إنشاء مالية ثانية أو تحويل الصندوق المالك. | اختبارات لكل صف من جدول التصنيف ولكل operation؛ لا اعتبار INVOICE دائمًا أمر فاتورة مالية. | TODO | — |
| B15.03 | نفذ LOCAL_RETAINED فقط عند نوع معروف وسياسة LOCAL_ONLY مثبتة؛ افصل unresolvedReview/durablePending/verifiedReceipt/localRetained. | المجهول يبقى REQUIRES_REVIEW؛ LOCAL_RETAINED محفوظ بمرفقاته ولا ACK/إرسال قسري، والتفعيل اللاحق يعيد التحقق أولًا. | TODO | — |
| B15.04 | احفظ إثبات v1 وأضف v2 عند إعادة التصنيف، واربط terminal predicates وحماية المحتوى بحالة مالك المصدر. | T06 دون identity conflict مصطنع أو حذف journal؛ لا عدد تاريخي يضاعف مصادر الجرد الحالي. | TODO | — |
| B15.05 | استبدل استثناء M03 العام بنتيجة MIGRATION_BLOCKED بأسباب؛ طبق شروط رفع حاجز promotion مع السماح staging والدفع المستقل، وربط CORE_CAUGHT_UP_WITH_LOCAL_ONLY. | G-B15 يثبت عدم deadlock وعدم نجاح كاذب وإعادة الجرد فقط عند تغير الدليل؛ حماية المجهول لا تزال قائمة. | TODO | — |

**بوابة الجلسة:** G-B15: جدول Optimal كامل باختبارات نوع/عملية/حالة، وحاجز M03 يستند إلى الدليل ويحفظ المجهول ولا يعطل المستقل الآمن.

**اختبارات العقد المرتبطة:** T04، T05، T06، T07، T10، T34

**التسليم المطلوب:** تصنيف وfixtures/اختبارات Optimal؛ تحديث SYNC_M03_RECONCILIATION.md؛ دليل حالات gate.

**حدود التوقف وعدم الاجتهاد:** لا تعلن أن 26 عملية انحلت من fixture؛ بياناتها الفعلية لا يفترضها العقد ولا هذا الـBacklog.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b16"></a>
### B16 — ترميم APPLIED القديمة والتوفيق مع المصادر

**المرحلة:** E4 · **الاعتماديات:** B09، B13، B14، B15  
**اقرأ التصميم قبل التعديل:** C§4.2، C§8.3–8.8، C§9.4، C§10.6، C§19.1–19.2، C§22  
**عيوب العقد المعنية:** R01، R02، R06، R07  
**مناطق العمل:** repair jobs؛ FinancialMaterializerV2؛ evidence/receipts؛ قواعد المصدر المتحققة ونسخ الاختبار.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B16.01 | اجرد أحداث INVOICE/PAYMENT ذات APPLIED قديمة وقارن فعليًا جداول العمل وبصماتها، وأنشئ repair jobs حتمية للprojection الناقصة. | لا اعتماد على الفلاج ولا إعادة cursor للصفر فوق عمل محمي؛ سجل يربط كل نقص بالمصدر. | TODO | — |
| B16.02 | طبّق ترتيب مصادر الاسترجاع: snapshot قانونية كاملة مثبتة، ثم المحلي الأصلي الكامل بعد إثبات النطاق والتعارض، ثم backup متحققة. | توثيق مصدر كل حقل/مجموعة؛ snapshot ناقصة لا تلغي بندًا محليًا لم يكن في الحدث القديم. | TODO | — |
| B16.03 | أعد التطبيق بالمطبق الجديد مع business keys لكل أصحاب الأثر، واحفظ reconciliation receipt بما كان موجودًا وما أضيف وما أصلح. | T22 دون عمولة/مخزون/نقد/دفعة إضافية؛ لا بناء حركة نقدية من مبلغ دون حقيقة مجال مثبتة. | TODO | — |
| B16.04 | نفذ فروع SOURCE_DATA_MISSING وOUTCOME_UNKNOWN والحقائق المتعارضة، مع حفظ البيانات وإظهار سبب عدم الإغلاق. | T23/T24 يختبران الفشل الآمن؛ نجاح الاختبار السلبي لا يعني استرجاع بيانات المستخدم المفقودة. | TODO | — |
| B16.05 | شغل dry-run على نسخة معقمة متحققة من الحالة المتضررة عند توفرها، ثم تنفيذ على النسخة ومقارنة التصنيفات والروابط/البصمات قبل وبعد. | G-B16 يفصل fixtures عن نسخة الحالة؛ غياب بيانات الجهاز يسجل NOT_VERIFIED للحالة الحقيقية لا PASSED بعدد 54. | TODO | — |

**بوابة الجلسة:** G-B16: إصلاح fixtures واكتشاف النقص يعملان دون تكرار الأثر؛ اعتماد ترميم الحالة الحقيقية يحتاج مصادرها وإيصالاتها وبصماتها. لا يسمح غيابها بتسميته إصلاح الجهاز.

**اختبارات العقد المرتبطة:** T07، T22، T23، T24، T34

**التسليم المطلوب:** SYNC_M03_RECONCILIATION.md؛ repair job/receipt evidence؛ قائمة SOURCE_DATA_MISSING/unknown؛ نتائج نسخة الحالة عند توفرها.

**حدود التوقف وعدم الاجتهاد:** لا تعديل بيانات الإنتاج هنا؛ لا تسليم اكتمال حالة لم تتوفر بياناتها ولا إخفاء unknown داخل نجاح اختبارات الخوارزمية.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b17"></a>
### B17 — دورة مستندات الشحن كاملة

**المرحلة:** E5 · **الاعتماديات:** B02، B05، B06، B08، B13  
**اقرأ التصميم قبل التعديل:** C§6.8–6.9، C§9.5، C§16 كاملًا، C§18.1 و18.4، Cملحق B  
**عيوب العقد المعنية:** R10، R06، R09، R13  
**مناطق العمل:** AttachmentTransferCoordinatorV2؛ P19، P22؛ Storage private bucket/prepare/confirm في بيئة اختبار.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B17.01 | نفذ النسخة الخاصة immutable للملف: temporary→checksum/size→atomic rename قبل اعتماد النية؛ مفتاح content-addressed وفق C§16.1. | ملف دائم ومراجع صحيحة؛ تغيير bytes ينتج transfer/version جديدة؛ صور مخزون R2 تبقى كما هي. | TODO | — |
| B17.02 | نفذ claim/lease/nextAttemptAt والحالات حتى REMOTE_VERIFIED/METADATA_PENDING/COMPLETED وفق C§16.2. | consumer دائم فعلي لا count/insert فقط؛ الإلغاء والمفقود والإذن والبصمة لها حالات صريحة. | TODO | — |
| B17.03 | أنشئ bucket private باسم verto-sync-documents وسياسات المؤسسة/document، ونفذ prepare محدود الصلاحية وconfirm يتحقق من bytes الحقيقية وhash/size. | اختبارات تفويض ومنع مؤسسة أخرى؛ لا service key بالعميل ولا تصديق checksum الهاتف أو HTTP 200 وحده. | TODO | — |
| B17.04 | اربط adapter الرفع المدعوم فعليًا بـSDK المشروع مع streaming/resume المتحقق من العقد؛ بعد REMOTE_VERIFIED فقط جهز metadata وانتظر إيصالها المطابق. | فقد رد رفع يعالج بتحقق المفتاح نفسه؛ لا privateUri بعيد ولا duplicate document ولا COMPLETED قبل receipt. | TODO | — |
| B17.05 | نفذ الحذف/tombstone/cancel والتعامل مع object يتيم، وشغل ملفًا حقيقيًا مع انقطاع وإعادة تشغيل وتبديل مؤسسة وتغير المحتوى. | G-B17/T41–T43 وT45/T46 في التخزين الاختباري الحقيقي مع بصمات الملف المحلي/البعيد وmetadata receipt. | TODO | — |

**بوابة الجلسة:** G-B17: نقل ملف حقيقي واستعادته بعد انقطاع والتحقق منه وmetadata ACK، مع حماية الملف والنطاق في جميع الحالات. mocks/رابط وهمي لا يكفيان.

**اختبارات العقد المرتبطة:** T41، T42، T43، T45، T46

**التسليم المطلوب:** Storage definitions/policies/Edge Functions؛ اختبارات ملف حقيقي؛ hashes/size/receipts؛ لا signed URLs أو أسرار في التقرير.

**حدود التوقف وعدم الاجتهاد:** لا إنشاء bucket عامة، ولا تنظيف ملف مشار إليه أو object لم تحسم نيته، ولا إضافة ناقل جديد لصور الفواتير خارج النطاق.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b18"></a>
### B18 — الحجوزات والجدولة وطلب الإيقاظ الدائم

**المرحلة:** E5 · **الاعتماديات:** B05، B10، B15  
**اقرأ التصميم قبل التعديل:** C§9.5، C§13.3، C§15، C§17.1–17.2  
**عيوب العقد المعنية:** R09، R07، R11، R12  
**مناطق العمل:** P01–P03، P11، P17؛ WorkManager/generation/Health inputs؛ بروتوكول جميع المالكين.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B18.01 | احسب nextWakeAt من retry والleases والتبعيات/المرفقات ذات الموعد المعروف لجميع المالكين، وليس من PENDING القابل للحجز فقط. | حجز حي يعني WAITING_LEASE وموعد عند انتهائه؛ لا اعتبار الطابور فارغًا. | TODO | — |
| B18.02 | نفذ استمرار generation طلب المزامنة والمتابعة قبل إنهاء Worker، مع WorkManager فريد ومتابعة فورية bounded للعمل المتاح. | طلب يصل أثناء إغلاق الجولة لا يضيع، حتى مع عامل قائم أو فشل enqueue بعد الحفظ. | TODO | — |
| B18.03 | طبق backoff: min(30s*2^(attempt-1),30min) وjitter ثابت ±20% حسب mutationId وRetry-After الموثوق. | الموعد محفوظ لا يعاد حسابه عشوائيًا؛ validation/conflict/contract/auth الدائم ليس network retry. | TODO | — |
| B18.04 | ادمج استرداد الحجوزات وإعادة الاستعلام عن الإيصالات مع token/epoch، واحفظ pending حتى مصير الطلب المعلوم. | اختبارات عاملين ورد حجز قديم؛ لا ACK للحجز الجديد من العامل القديم ولا تغيير frozen bytes. | TODO | — |
| B18.05 | شغل حالات قتل بعد claim، وإعادة تشغيل قبل/بعد انتهاء الحجز، وطلب جديد أثناء النهاية، وحالات عدم التخزين. | G-B18/T38–T40 بجدولة دائمة وRoom/Worker behavior؛ ناقل الملفات يُدمج نهائيًا في B19 بعد B17. | TODO | — |

**بوابة الجلسة:** G-B18: كل عمل غير محسوم إما قابل للتنفيذ الآن أو له موعد/انتظار مفسر محفوظ، والجيل الجديد لا يسقط ولا دوران سريع.

**اختبارات العقد المرتبطة:** T37، T38، T39، T40

**التسليم المطلوب:** اختبارات Worker/lease/generation؛ nextWake snapshots؛ سجلات حالات منقحة.

**حدود التوقف وعدم الاجتهاد:** نجاح Worker التشغيلي ليس اكتمال بيانات؛ لا background task غير مسجلة ولا الانتظار على مؤقت ذاكرة فقط.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b19"></a>
### B19 — المنسق الموحد وحالة المستخدم والإلغاء

**المرحلة:** E5 · **الاعتماديات:** B08، B11، B12، B13، B15، B16، B17، B18  
**اقرأ التصميم قبل التعديل:** C§5، C§7، C§8.8، C§17 كاملًا، C§18.4، C§19.1  
**عيوب العقد المعنية:** R01، R06، R09، R10، R11، R13  
**مناطق العمل:** P01–P03، P11–P16؛ SyncViewModel/واجهة الحالة؛ Hilt؛ جميع المكونات الجديدة.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B19.01 | اربط drainOrchestration بالترتيب الدقيق C§17.1: session/scope/capabilities/leases→M03→staging/promotion المحمية→batches/مستقل/ملفات→Delta/apply→Health/موعد/generation. | المكونات الحقيقية موصولة؛ لا بديل empty ولا طرف يرسل عضو batch خارج المنسق؛ Realtime إشارة فقط. | TODO | — |
| B19.02 | ابن HealthSnapshot واحدًا من جميع المالكين وM03 وInbox وBootstrap والمرفقات؛ اربط جميع الحالات ومعانيها بالجدول C§17.3. | COMPLETED فقط عند شروطه كلها؛ local-only حالة مميزة؛ lastSuccessfulSync لا يتحدث عند مراجعة/استلام بلا تطبيق. | TODO | — |
| B19.03 | اعرض الأسباب والأعداد والإجراء والموعد الحقيقي؛ افصل Result.success التشغيلي عن نجاح تسليم الأعمال. | حالات auth/contract/migration/dependency/storage/lease لا توصف كلها بخطأ شبكة ولا نجاح مزامنة. | TODO | — |
| B19.04 | أعد رمي CancellationException أول كل catch مرتبط بالشبكة/الاستعادة/التطبيق؛ اجعل تنظيف الملكية القصير فقط token-scoped ولا شبكة في NonCancellable. | T44 في scope/Bootstrap/push/pull: نوع الإلغاء محفوظ ولا log/snackbar شبكة مضلل. | TODO | — |
| B19.05 | اختبر تبديل المؤسسة أثناء RPC/ACK/ملف، وcapabilities mismatches، وتحقق Hilt من المسار الفعلي لكل component. | G-B19/T46/T49 مثبتة؛ لا تطبيق على scope جديد ولا fallback V1. T50 إزالة Legacy نهائية في B21. | TODO | — |

**بوابة الجلسة:** G-B19: دورة عميل/خادم/ملفات مترابطة بحالة صادقة، إلغاء صحيح، وحماية نطاق؛ جميع بوابات E0–E5 اللازمة للتكامل موثقة قبل B20.

**اختبارات العقد المرتبطة:** T27، T38، T40، T41، T44، T46، T49، T50

**التسليم المطلوب:** SYNC_IMPLEMENTATION_MAP.md؛ اختبارات Health/VM/Hilt/cancellation/scope؛ لقطات الحالة من سيناريوهات فعلية.

**حدود التوقف وعدم الاجتهاد:** لا فتح كتابة إنتاجية عند capabilities ناقصة ولا عرض الأخضر لأن Worker انتهى؛ النسخة الجزئية لا تنشر على بيانات المستخدم.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b20"></a>
### B20 — القبول السلوكي وجهازان والترقية الاختبارية

**المرحلة:** E6 · **الاعتماديات:** B19  
**اقرأ التصميم قبل التعديل:** C§19.1–19.3، C§20–22، C§21 كاملًا  
**عيوب العقد المعنية:** R01–R13  
**مناطق العمل:** حزم اختبار المنتج وJVM/Room/PostgreSQL وجهازان A/B؛ SOURCE_BASELINE؛ نتائج/Parity.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B20.01 | تحقق أن E0–E5 اجتازت بواباتها التكاملية، وثبت product commit/tree وRoom/server definitions وجهازين/قاعدتين مستقلتين وبيانات مؤسسة اختبار. | لا قبول على آخر commit مفترض أو بيئة مشتركة تدعي جهازين؛ أوامر ونسخ قابلة لإعادة التشغيل. | TODO | — |
| B20.02 | شغل T01–T24 كاملة بحسب C§21، مع assertions للمجالات والتكرار والإيصالات والـbytes والمصروف والنقص التاريخي. | PASS/FAIL/BLOCKED/NOT_RUN لكل اختبار مع السجل والخروج/الوقت/expected/actual؛ fixture لا يساوي إصلاح الجهاز الحقيقي. | TODO | — |
| B20.03 | شغل T25–T37، وDelta/Bootstrap على B نظيفة ثم فوق pending لكل owner؛ قارن كل الحقول والحقائق وMinor والكميات والنسخ. | SYNC_DATA_PARITY.json يغطي البيع والشراء والدولي والدفعات/العكس/المرتجع/التكلفة/النقد؛ counts وحدها غير كافية. | TODO | — |
| B20.04 | شغل T38–T49 بالملفات الحقيقية وscope/kill/retry؛ T48 على قاعدة قديمة غير فارغة بترقية in-place محفوظة البيانات والتوقيع. | لا uninstall أو توقيع مختلف؛ فقد التوقيع BLOCKED_SIGNING؛ سجلات Android/Room/SQL فعلية لا فحص نصي بديل. | TODO | — |
| B20.05 | أعد اختبارات M04–M07 الموجودة وحوّل REPRODUCED القديمة إلى assertions تمنع العيب؛ حدّث tracker وR evidence دون إغلاق R13 قبل T50. | G-B20: T01–T49 واجتياز وظائف البدائل قبل حذف Legacy؛ T50 الكامل مؤجل صراحةً إلى B21، وليس PASS الآن. | TODO | — |

**بوابة الجلسة:** G-B20: اختبارات T01–T49 المطلوبة في بيئتها الحقيقية واجتياز المقارنة؛ أي FAIL/BLOCKED/NOT_RUN يمنع الانتقال إلى حذف بديل لم يثبت. لا E6 PASS بعد.

**اختبارات العقد المرتبطة:** T01–T49؛ T50 الكامل في B21؛ جميعها يعاد التحقق منها في B22.

**التسليم المطلوب:** SYNC_TEST_RESULTS.md؛ نتائج آلية؛ SYNC_DATA_PARITY.json؛ أوامر وسجلات منقحة وتوقيع الترقية الاختبارية.

**حدود التوقف وعدم الاجتهاد:** لا تجاوز اختبار بسبب صعوبة المحاكي أو البيئة. غياب دليل يظل ظاهرًا؛ لا اختبارات منتج على مؤسسة المستخدم.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b21"></a>
### B21 — إزالة Legacy بعد إثبات البديل

**المرحلة:** E6 · **الاعتماديات:** B20  
**اقرأ التصميم قبل التعديل:** C§19.4، C§20 E6، C§21 T50 و21.5، C§22–23  
**عيوب العقد المعنية:** R02، R13  
**مناطق العمل:** مستدعو/DI/Workers وناقل Legacy المثبت؛ P02–P06 حسب الاستخدام؛ historical read-only adapters.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B21.01 | اجرد ملفات ومداخل النقل القديم ومستدعيها وDI/Workers الفعلية؛ صنف ما سيحذف وما يبقى قارئًا تاريخيًا صرفًا. | SYNC_LEGACY_REMOVAL_MANIFEST.md بأسماء دوال/استدعاءات؛ لا حذف بناءً على كلمة legacy في اسم الملف. | TODO | — |
| B21.02 | احذف مستدعي الإنتاج القديم وارتباطاته المجدولة وتطبيقاته غير المستخدمة بعد تحقق البديل لكل وظيفة. | لا fullSync/participants production path؛ لا حذف وحيد materializer قبل بديله ولا تحريك بيانات مستخدم. | TODO | — |
| B21.03 | احتفظ بقراءة التاريخ التي يحتاجها migration دون شبكة أو enqueue أو transport fallback. | اختبارات تعزل historical readers عن الإنتاج؛ journal والبيانات القديمة باقية بحسب العقد. | TODO | — |
| B21.04 | شغل T50 باختبار Hilt ومسار فعلي/static gate معًا، وأعد اختبارات البدائل التي مسها الحذف وM04–M07 ذات الصلة. | لا empty materializer أو route ميت؛ مراجعة الاستدعاءات تدعم اختبار السلوك لا تستبدله. | TODO | — |
| B21.05 | ثبت commit الحذف وحدّث خريطة التنفيذ والبيانات/الاختبارات المتأثرة؛ علم النتائج القديمة RECHECK_REQUIRED حيث لم تعد تثبت الشجرة الحالية. | G-B21: T50 كامل؛ إبقاء مصفوفة إعادة الاختبار النهائية حتى B22 دون نسخ PASS من commit سابق. | TODO | — |

**بوابة الجلسة:** G-B21: انقطاع النقل القديم مع بقاء القراء التاريخيين المسموحين، وT50/الوظائف البديلة تجتاز بعد الحذف. النشر النهائي ينتظر إعادة القبول.

**اختبارات العقد المرتبطة:** T50

**التسليم المطلوب:** SYNC_LEGACY_REMOVAL_MANIFEST.md؛ commit الحذف؛ T50/Hilt results؛ خريطة اختبارات إعادة التحقق.

**حدود التوقف وعدم الاجتهاد:** لا حذف جداول أو بيانات أو أدلة لمجرد تسميتها Legacy، ولا استخدام بحث نصي وحده لإثبات أن البديل يعمل.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="b22"></a>
### B22 — التحقق النهائي والتسليم والانتقال المحكوم

**المرحلة:** E6 · **الاعتماديات:** B21  
**اقرأ التصميم قبل التعديل:** C§4 كاملًا، C§18.4، C§19 كاملًا، C§21–23  
**عيوب العقد المعنية:** R01–R13  
**مناطق العمل:** الشجرة النهائية؛ اختبار/نشر محكوم؛ أدلة C§22؛ Backlog/Changelog؛ ZIP/APK عند التفويض.

| المهمة | التنفيذ المحدد | دليل اكتمال المهمة | الحالة | الدليل/commit |
|---|---|---|---|---|
| B22.01 | ثبت شجرة المنتج النهائية بعد الحذف وأعد T01–T50 والمقارنات المطلوبة على هذه الشجرة؛ راجع source/definitions/commands لكل نتيجة. | كل Txx كامل حديث الدليل، لا نتيجة قديمة بعد تعديل المنتج؛ كل Rxx له اختبارات إغلاقه كما المصدر. | TODO | — |
| B22.02 | أكمل ملفات C§22 وبصماتها وأزل الأسرار من الحزم والسجلات؛ ميّز نتائج بيئة الاختبار عن نتيجة جهاز المستخدم. | SOURCE_BASELINE، implementation/ownership/schema، M03/server/test/parity/legacy manifest وChangelog كاملة؛ لا SOURCE_DATA_MISSING مخفي. | TODO | — |
| B22.03 | لأي انتقال حي مفوض: تحقق من نسخة جهاز حديثة متسقة ومن بيانات/تعريفات الإنتاج والسياج، ثم انشر الخادم الإضافي المتحقق قبل تفعيل كتابة العميل، وشغل dry-run للحالة قبل إصلاحها. | هذه المهمة CONDITIONAL: لا تنفيذ حي بلا تفويض/مدخلات وبوابات C§19؛ عند التنفيذ تبقى مجهولات الحالة محفوظة ولا تُتجاوز لإغلاق العداد. | TODO | — |
| B22.04 | عند تفويض البناء/التسليم، ابن Release من الشجرة المثبتة بالحزمة والتوقيع نفسهما وversionCode أعلى؛ وثق APK/ZIP وSHA-256 والـcommit/سجل البناء، ثم تحقق من in-place والحالة دون مسح بيانات. | هذه المهمة CONDITIONAL: لا ادعاء APK بلا build فعلي ولا مفتاح جديد يفرض uninstall؛ بعد كتابة v2 حية الرجوع إيقاف نقل وforward-fix. | TODO | — |
| B22.05 | أصدر حكمًا محدد النطاق: نتائج الاختبارات، المعلقات/local-only، إغلاق كل R، حالة جهاز المستخدم، وما نُشر/لم يُنشر؛ حدث NEXT_ACTION وفق الواقع وأغلق السجل. | إغلاق الإصلاح العام فقط بشروط C§22؛ إن بقي تفويض/بيانات/اختبار ناقص فNEXT_ACTION للمطلوب الفعلي لا NONE كنجاح مزيف. | TODO | — |

**بوابة الجلسة:** G-B22: R01–R13 وT01–T50 والأدلة/Parity على المصدر النهائي مكتملة في النطاق المعلن. يظل نطاق الجهاز/الإنتاج غير المتحقق NOT_VERIFIED ولا تنسب له نتائج fixtures.

**اختبارات العقد المرتبطة:** T01–T50 على الشجرة النهائية، مع صلاحية الدليل وحدود بيئته.

**التسليم المطلوب:** جميع ملفات C§22؛ هذا الملف محدث؛ Changelog؛ تقرير إغلاق نهائي؛ ZIP/APK فقط عند طلب/تفويض البناء.

**حدود التوقف وعدم الاجتهاد:** لا استعادة backup قديمة فوق كتابات v2 مقبولة، ولا downgrade schema ولا SQL عشوائي لعكس أموال. الأعمال الشرطية المؤجلة لا تعادل نشرًا منفذًا.

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والعوائق وسجل التسليم. إن بقيت مهمة جزئية، تكون هي NEXT_TASK؛ وإلا اختر التالية المؤهلة من جدول الاعتماديات، دون بدء تنفيذها في الزيارة نفسها.

---

<a id="test-tracker"></a>
## 6. تتبع اختبارات T01–T50 — جميع النتائج ابتدائيًا NOT_RUN

السيناريو و**الإثبات المطلوب الكامل** في C§21 هما المرجع؛ عمود السيناريو أدناه من المصدر نفسه. جلسة الإنشاء/الربط مسؤولة عن بناء اختبار السلوك، وليست وعدًا بإغلاق جميع أجزائه قبل توفر بيئة التكامل. جميع الاختبارات تمر بالقبول العام B20/B22، وT50 بعد الإزالة في B21/B22.

لا تكتب PASS هنا إلا إذا نُفذ سيناريو Txx كاملًا بالأدلة المطلوبة. إثبات محلي جزئي يسجل في G-Bxx وتقرير التشغيل مع بقاء Txx NOT_RUN/BLOCKED حسب الواقع. PASS لا يحوّل نسخة مفقودة من بيانات المستخدم إلى حالة مُصلحة. عدد الاختبارات الفريد خمسون مهما تكرر اختبار بين عيبين أو جلستين.

| الاختبار | السيناريو الأصلي | جلسة الإنشاء/الربط | النتيجة | صلاحية الدليل | evidence/run ID | commit/تعريفات البيئة |
|---|---|---|---|---|---|---|
| T01 | Fixture من 54 مصدرًا بتقسيم S2، ثم fixture بعدد مختلف | B14 | NOT_RUN | NONE | — | — |
| T02 | فاتورة dirty وبند dirty للأصل نفسه | B14 | NOT_RUN | NONE | — | — |
| T03 | تسعة أدوار و17 دفعة مع مصادرها | B14 | NOT_RUN | NONE | — | — |
| T04 | Optimal known LOCAL_ONLY بعقد غير متاح | B15 | NOT_RUN | NONE | — | — |
| T05 | Optimal type/operation/payload مجهول | B15 | NOT_RUN | NONE | — | — |
| T06 | إعادة تصنيف سجل journal قديم OPTIMAL_UNKNOWN | B15 | NOT_RUN | NONE | — | — |
| T07 | تشغيل repair مرتين، ثم قتل العملية عند كل حد معاملة | B14 | NOT_RUN | NONE | — | — |
| T08 | ACK قديم للفاتورة N مع تعديل N+1 | B05 | NOT_RUN | NONE | — | — |
| T09 | تعديل بند مع نفس الإجمالي وعدد البنود | B06 | NOT_RUN | PARTIAL_LOCAL_NOT_TXX | B06-V01 item-only hash precursor PASS | لا PostgreSQL/جهازين؛ السيناريو الكامل لاحق |
| T10 | صف بلا مؤسسة مثبتة أو أصل دفع مفقود | B14 | NOT_RUN | NONE | — | — |
| T11 | بيع A ببندين ودفعة، ثم سحب B | B09 | NOT_RUN | NONE | B09-V02: source/SQLite/Kotlin precursors فقط؛ 33 Room و12 JVM مرفقة | G-B09 محجوبة؛ ليس تشغيل Txx ولا إثبات جهازين |
| T12 | شراء محلي وآخر دولي بعملات/FX مسجلة | B09 | NOT_RUN | NONE | B09-V02: source/SQLite/Kotlin precursors فقط؛ 33 Room و12 JVM مرفقة | G-B09 محجوبة؛ ليس تشغيل Txx ولا إثبات جهازين |
| T13 | إنشاء/تعديل/دفعة/عكس/مرتجع/إبطال | B09 | NOT_RUN | NONE | B09-V02: source/SQLite/Kotlin precursors فقط؛ 33 Room و12 JVM مرفقة | G-B09 محجوبة؛ ليس تشغيل Txx ولا إثبات جهازين |
| T14 | نفس batch عشر مرات، وإرسالان متزامنان | B07 | NOT_RUN | NONE | — | — |
| T15 | يقبل السيرفر الطلب وتسقط الاستجابة ثم يعدّل A السجل | B07 | NOT_RUN | NONE | — | — |
| T16 | نفس mutationId مع body أو baseVersion مختلف | B07 | NOT_RUN | NONE | — | — |
| T17 | تعديلان Offline للكيان نفسه | B05 | NOT_RUN | NONE | — | — |
| T18 | تعديل صنف موجود، وتعديل قالب بعد Bootstrap نسخة 9 | B13 | NOT_RUN | NONE | B13-V01: authority-after-apply static PASS فقط | Room/B08/live scenario غير مشغّل |
| T19 | مخزون وتكلفة وCLIENT_CREDIT عبر الخادم | B08 | NOT_RUN | NONE | — | — |
| T20 | missing field، overflow، currency/cost unknown تاريخية | B06 | NOT_RUN | PARTIAL_LOCAL_NOT_TXX | B06-V01 validator precursor PASS | لا round-trip server/Room؛ السيناريو الكامل لاحق |
| T21 | تعديل ملاحظة مصروف ثم 10000→15000 ثم VOID | B12 | NOT_RUN | PARTIAL_LOCAL_NOT_TXX | B12-V01: pure Kotlin/static deltas 0/-5000/+15000 PASS | التطبيق/Room/SQL الفعلي لم يعمل؛ لا ترقية إلى PASS |
| T22 | repair بعد حدث invoice قديم ناقص البنود | B16 | NOT_RUN | NONE | — | — |
| T23 | bytes قديمة مفقودة وإيصال غير قابل للتحقق | B14 | NOT_RUN | NONE | — | — |
| T24 | نقص البنود في كل المصادر | B16 | NOT_RUN | NONE | — | — |
| T25 | قتل التطبيق بعد حفظ Inbox وقبل apply | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T26 | قتل التطبيق داخل معاملة materialization | B09 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T27 | مجموعة X متعارضة ومجموعة Y مستقلة | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T28 | مجموعة لاحقة تمس X أو تعتمد عليها | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T29 | دفعة قبل أصلها، وعكس قبل الدفع الأصلي | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T30 | مستخدم يحسم mutable conflict بخياري القسم 13.4 | B11 | NOT_RUN | NONE | B11-V01: static/SQLite contract precursors PASS | Gradle/Room/UI والـreceipt/echo الفعلي لم يعمل؛ لا ترقية إلى PASS |
| T31 | Bootstrap B نظيفة من حالة مالية متعددة العمليات | B13 | NOT_RUN | NONE | B13-V01: staging/seal/promotion static+SQLite precursors؛ B09-V02 financial precursors | B08/Room/live integration غير مشغّل؛ ليس PASS |
| T32 | Bootstrap فوق جرد 200/CLOSED محلي و100/OPEN بعيدة | B13 | NOT_RUN | NONE | B13-V01: protection recheck وWAITING_LOCAL static PASS | actual Room scenario غير مشغّل |
| T33 | Bootstrap مع pending في كل owner وبمرفق | B13 | NOT_RUN | NONE | B13-V01: manifest يغطي owners/attachment في static probe | hash-before/after actual Room scenario غير مشغّل |
| T34 | Snapshot فارغة/منتهية/ناقصة أو scope تغير | B13 | NOT_RUN | NONE | B13-V01: seal/state-machine static + SQLite rollback precursor | RPC/scope-change runtime scenario غير مشغّل |
| T35 | 999 و1000 و1001 تغيير في أول مجموعة قانونية | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T36 | مجموعة عند حد 2MiB وأخرى فوقه | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T37 | بلوغ حد Inbox/نفاد القرص | B10 | NOT_RUN | NONE | B10-V01: 24 JVM و14 Room Inbox +34 مالية مرفقة؛ 15 SQLite/12 policy precursors فقط | JSON/Room/kill/B08 NOT_RUN؛ ليس إثبات Txx أو جهازين |
| T38 | توقف بعد claim ثم تشغيل قبل انتهاء lease | B18 | NOT_RUN | NONE | — | — |
| T39 | رد lease قديم بعد إعادة الحجز | B05 | NOT_RUN | NONE | — | — |
| T40 | طلب requestSync جديد أثناء إغلاق الجولة | B18 | NOT_RUN | NONE | — | — |
| T41 | مرفق مع قطع الشبكة وإعادة التشغيل | B17 | NOT_RUN | NONE | — | — |
| T42 | نجاح رفع وضياع الرد؛ ثم تحقق/إعادة | B17 | NOT_RUN | NONE | — | — |
| T43 | ملف مفقود، إذن مرفوض، checksum مختلف، ملف معدل بنفس documentId | B17 | NOT_RUN | NONE | — | — |
| T44 | الإلغاء في scope/Bootstrap/push/pull | B19 | NOT_RUN | NONE | — | — |
| T45 | مؤسسة أخرى تحاول طلب snapshot/receipt/objectKey | B08 | NOT_RUN | NONE | — | — |
| T46 | تبديل المؤسسة أثناء رجوع RPC/رفع ملف | B19 | NOT_RUN | NONE | — | — |
| T47 | نوع عضو صالح يتبعه عضو batch مرفوض | B07 | NOT_RUN | NONE | — | — |
| T48 | ترقية APK in-place لقاعدة قديمة غير فارغة | B20 | NOT_RUN | NONE | — | — |
| T49 | عقد السيرفر لا يطابق capabilities/client | B08 | NOT_RUN | NONE | — | — |
| T50 | فحص مسارات الإنتاج واختبار Hilt | B21 | NOT_RUN | NONE | — | — |

**صلاحية الأدلة بعد B09-V01:** نتائج B03–B06 القديمة محفوظة كتاريخ تنفيذ، وليست Gradle/Room PASS لهذه الشجرة. تغيرت DAOs/cursor/pending protection وربط المطبق وproducer mapper؛ لذلك إعادة تشغيل حزم B04–B06 المتأثرة وapp/Hilt مطلوبة مع B09. هذا لا يحول أي Txx قديم من NOT_RUN إلى PASS.

**صلاحية الأدلة بعد B09-V02:** فحوص 612 source/SQLite و347 mapping و3 semantic checks أعيد تشغيلها ضمن حدودها. الـ3 الدلالية تستخدم validator الحقيقي وcodec stub؛ ليست JSON runtime. اختبارات 12 JVM/33 Room والبناء/حزم B04–B06 المطلوبة تظل NOT_RUN/RECHECK_REQUIRED، ولا Txx أُغلق.

### 6.1 سجل كل تشغيل

يحفظ دليل كل تشغيل في مسار موحد داخل فرع التنفيذ مثل:

```text
docs/sync-repair/evidence/Bxx/Vnn/
  session-report.md
  commands-and-exit-codes.md
  test-results/
  sanitized-logs/
  data-parity/
  hashes-and-receipts/
```

هذا تنظيم أدلة، لا مكانًا لنشر raw backup أو أسرار. تحفظ النسخ/الحمولات الخاصة خارج المستودع وتشار إليها ببيان منقح وبصمات. الملف الموصوف في C§22 يحتفظ باسمه؛ يثبت B01 مكانه الفعلي في EVIDENCE_INDEX.md ولا تتنقل المخرجات بين مسارات متناقضة.

لكل Txx أو بوابة G-Bxx سجّل: مصدر/commit أو tree hash؛ نسختي Room والخادم وبصمات التعريفات؛ مدخلات ومؤسسة اختبار مستعارة؛ أمر التشغيل الحقيقي ووقت البدء/النهاية وexit code؛ expected وactual وassertions؛ السجل المنقح؛ dumps/hash/receipts/received/applied حيث يلزم. JVM/Room/PostgreSQL/جهازان أعمدة إثبات منفصلة، لا كلمة واحدة «اختبارات نجحت».

إذا تغير المنتج أو schema/SQL/serializer/applier/DI المؤثر بعد PASS، احتفظ بالتشغيل القديم وعلّم صلاحيته RECHECK_REQUIRED، وحدد الاختبارات المطلوب إعادة تشغيلها. في B22 تعاد مصفوفة القبول المطلوبة على المنتج النهائي؛ لا إعفاء اعتمادًا على مقارنة لفظية.

### 6.2 تتبع إغلاق العيوب — نفس الربط الملزم في C§21.6

| العيب | اختبارات إغلاقه الأصلية | جلسات العمل الأساسية | الحالة | دليل الإغلاق/العائق |
|---|---|---|---|---|
| R01 | T01–T07، T10، T23، T34 | B02، B13–B16، B20–B22 | OPEN | — |
| R02 | T11–T14، T22، T24–T26، T29، T31، T47 | B06–B10، B13، B16، B20–B22 | IN_PROGRESS | B09-V01: كود materialization/حماية/atomic authority موجود؛ G-B09 BLOCKED وTxx NOT_RUN |
| R03 | T08، T09، T15، T39 | B05–B06، B14، B18، B20–B22 | IN_PROGRESS | B05-V01: receipt/hash + generation/later-intent CAS primitives PASS محليًا؛ B06-V01: full semantic snapshot/item-only hash precursor PASS؛ Txx الكاملة باقية؛ B09-V01: كود materialization/حماية/atomic authority موجود؛ G-B09 BLOCKED وTxx NOT_RUN |
| R04 | T19، T20 | B06–B08، B20–B22 | OPEN | B06-V01: DTO/schema/Minor/missing/overflow precursors PASS محليًا؛ server→Room round-trip في B08 باقٍ |
| R05 | T17، T18، T31 | B03، B05–B08، B13، B20–B22 | OPEN | B05-V01: applied/receipt baseVersion وoffline predecessor PASS محليًا؛ B06–B08/Txx باقية |
| R06 | T10، T32–T34، T46 | B02، B04، B13، B15–B17، B19–B22 | IN_PROGRESS | B09-V01: كود materialization/حماية/atomic authority موجود؛ G-B09 BLOCKED وTxx NOT_RUN |
| R07 | T15–T17، T23 | B05–B07، B11، B14، B20–B22 | OPEN | B05-V01: frozen bytes/hash وold-request OUTCOME_UNKNOWN PASS محليًا؛ قبول السيرفر/repair باقٍ |
| R08 | T21، T27 | B06، B09، B11–B12، B20–B22 | OPEN | — |
| R09 | T38–T40، T41، T37 | B05، B10، B17–B20، B22 | OPEN | B05-V01: lease 120s/renew 30s وscopeEpoch stale CAS PASS محليًا؛ بقية الجدولة/Txx باقية |
| R10 | T41–T43، T46 | B17–B20، B22 | OPEN | — |
| R11 | T25–T30، T37 | B09–B11، B13، B18–B20، B22 | IN_PROGRESS | B10-V01: receive/apply/checkpoint/quota محليًا؛ G-B10 BLOCKED وTxx NOT_RUN |
| R12 | T35–T37 | B08، B10، B18، B20، B22 | IN_PROGRESS | B10-V01: receive/apply/checkpoint/quota محليًا؛ G-B10 BLOCKED وTxx NOT_RUN |
| R13 | T44–T50، T14، T16 | B02، B07–B08، B17، B19–B22 | OPEN | — |

<a id="blockers"></a>
## 7. سجل العوائق — لا تعتبر «غير مفحوص» عائقًا مثبتًا

**عند الإعداد: لا عوائق مفحوصة؛ التنفيذ لم يبدأ.** يضاف صف حقيقي عند اكتشاف عائق فقط. لا تنشئ صفوفًا وهمية تفيد أن الاتصال أو الاختبار فشل دون محاولة.

| Blocker ID | اكتُشف في المهمة | الرمز/السبب المثبت | الدليل | ما يمنعه تحديدًا | أعمال محلية مسموحة | خطوة فك العائق ومن يملكها | الحالة وآخر إعادة فحص |
|---|---|---|---|---|---|---|---|
| B01-BLK-01 | B01.01 | `BLOCKED_SOURCE_ARCHIVE_MISSING`؛ ملف `Verto-425.zip` غير موجود | `SOURCE_DIFF.md`؛ بحث workspace و`/home/aboalftooh` بعمق 6 و`/tmp` و`/mnt` في B01-V01 | كان يمنع اختيار المصدر؛ لا يمنع لاحقًا التحقق الإضافي من حاوية ZIP إن توفرت | جرد التعليمات والبيئة وهيكل الأدلة وتوثيق الشجرة المرشحة فقط؛ لا كود منتج أو SQL | حُسم اختيار المصدر بتوضيح المستخدم أن المجلد الحالي هو المحتوى المفكوك؛ بقي hash الحاوية غير مدعى | CLOSED؛ B01-V02 في 2026-09-10، baseline بهوية tree hash |
| B02-BLK-01 | B02.01 | `BLOCKED_BACKUP_UNVERIFIED`؛ لا نسخة Room/WAL/SHM ومرفقات ولا جهاز متصل | `docs/sync-repair/evidence/B02/V01/BACKUP_VERIFICATION.md` | يمنع إثبات سلامة بيانات الجهاز وأي ترحيل/إصلاح يعتمد عليها | B03–B06 وغيرها وفق استثناء C§4.3 دون ادعاء إصلاح الحالة الحية | مالك الجهاز/البيانات: توفير جهاز مفوض أو export متسق خاص ثم فتح النسخة المنفصلة وفحصها | OPEN؛ فُحص 2026-09-10 |
| B02-BLK-02 | B02.04 | `BLOCKED_TEST_ENVIRONMENT_UNAVAILABLE` + `BLOCKED_CLEAN_REBUILD_SOURCE_MISSING` | `TEST_ENVIRONMENT_AND_FENCE.md` و`SERVER_SOURCE_DRIFT.md`؛ تكلفة branch = 0.01344 USD/hour، لا postgres/initdb، 214 live مقابل 30 ملفات محلية | يمنع clean rebuild واختبار SQL/تكامل ونشر أي migration | كود واختبارات محلية مستقلة فقط؛ لا SQL حي | المستخدم تجاوز الفرع المدفوع بسبب الخطة المجانية؛ لا يعاد عرضه ما لم يغير المستخدم القرار أو تتوفر بيئة مجانية معزولة | OPEN؛ قرار المستخدم 2026-09-10 |
| B02-BLK-03 | B02.05 | `BLOCKED_SIGNING_INSTALLED_PACKAGE_UNAVAILABLE`؛ APK المحلي متحقق لكن لا جهاز لمقارنة المثبت | `TEST_ENVIRONMENT_AND_FENCE.md`؛ apksigner PASS وشهادة SHA-256 محفوظة | كان يمنع إثبات تطابق/ترقية النسخة المثبتة | لا قيود بعد المقارنة | وُصل الجهاز وسُحب base APK إلى مساحة مؤقتة خاصة وقورن signer | CLOSED؛ B02-V02، signer المثبت يطابق المحلي |

| B09-BLK-01 | B09.01–B09.05 | `BLOCKED_BUILD_RUNTIME_ENVIRONMENT`؛ Gradle 8.9 غير مخزن، ولا Android SDK/adb/emulator؛ UnknownHostException محفوظ تاريخيًا في V01 فقط | `docs/sync-repair/evidence/B09/V02/runtime-environment.json` و`runtime-preflight.log`؛ لا شبكة أو wrapper شُغّل في V02 | يمنع Gradle/KSP/Hilt و33 Room و12 JVM وإغلاق G-B09 | تنفيذ محلي وفحوص Kotlin جزئية/SQL على مخطط98 لا تعادل Room | تشغيل أوامر V02/commands-and-results.md في بيئة مجهزة؛ إغلاق بالدليل لا بإعادة المحاولة نفسها | OPEN؛ أعيد فحص البيئة في B09-V02 بتاريخ 2026-09-10 |

| B10-BLK-01 | B10.01–B10.05 | `BLOCKED_BUILD_RUNTIME_ENVIRONMENT`؛ Gradle8.9 غير مخزن ولا SDK/adb/emulator؛ Kotlin1.9 المتاح ليس compiler2.1 المشروع | `evidence/B10/V01/runtime-environment.json` و`commands-and-results.md` | يمنع JSON/Room/KSP/Hilt وexport99 واختبارات kill/device وإغلاق G-B10 | كود محلي وSQLite/policy/signature tests ضمن حدودها | تشغيل الأوامر على بيئة مجهزة ثم إصلاح الفشل وتسجيل الأدلة؛ لا إعادة شبكة هنا | OPEN؛ B10-V01، 2026-09-10 |
| B10-BLK-02 | B10.01/B10.04/B10.05 | `BLOCKED_INBOX_V2_SERVER_INTEGRATION` عائق ربط تنظيمي؛ عميل B10 يطلب RPC/manifest وcanonical bytes جديدة لم تختبر على SQL | `evidence/B10/V01/wire-contract.md`؛ B08 ما زالت غير مغلقة | يمنع التفعيل/قبول round-trip/G-B10 المتكاملة؛ لا ادعاء غياب الدالة حيًا من بحث لم يتم | تنفيذ واجهة العميل والمخطط والاختبارات المحلية فقط | B08/B20 على PostgreSQL معزول: مطابقة التفويض وأسماء المعاملات/البيان و1001/2MiB والاحتفاظ؛ لا fallback قديم | OPEN؛ B10-V01، بلا اتصال أو نشر سيرفر |
| B11-BLK-01 | B11.01–B11.05 | `BLOCKED_BUILD_RUNTIME_ENVIRONMENT` + `BLOCKED_PREDECESSOR_GATE_B10`؛ Gradle8.9 غير مخزن وG-B10 لم تغلق | `docs/sync-repair/evidence/B11/V01/runtime-environment.json` و`commands-and-results.md` | يمنع compile/KSP/Room/schema100/UI وT30 وإغلاق G-B11 | فحص static/SQLite وكتابة الاختبارات والتوثيق فقط | بيئة Gradle/Android مجهزة ثم إغلاق B10 وإعادة B11 actual tests/receipts | OPEN؛ B11-V01، 2026-09-11 |
| B12-BLK-01 | B12.01–B12.05 | `BLOCKED_BUILD_RUNTIME_ENVIRONMENT`؛ Gradle8.9 غير مخزن والـwrapper يحتاج شبكة | `docs/sync-repair/evidence/B12/V01/gradle-first-failure.txt` و`commands-and-results.md` | يمنع compile/KSP/Room واختبارات T21/T27 الفعلية وإغلاق الجزء المحلي من G-B12 | static/SQLite/pure Kotlin وكتابة الاختبارات والتوثيق فقط | بيئة Gradle8.9/Android/Room مجهزة ثم تشغيل أوامر B12 الموثقة | OPEN؛ B12-V01، 2026-09-11 |
| B12-BLK-02 | B12.04–B12.05 | `B12_EXPENSE_BATCH_REQUIRED` / `BLOCKED_PREDECESSOR_B07`؛ atomic expense+cash server batch غير متاح/غير مثبت بعد | `supabase/migrations/20260911001500_b12_expense_versioned_history.sql` و`docs/sync-repair/evidence/B12/V01/session-report.md` | يمنع اعتماد تعديل مبلغ/VOID على الخادم وإثبات SQL النقدي وG-B12/R08 | note-only server path والكود المحلي؛ nonzero cash يفشل مغلقًا قبل أي write | نفذ/ثبت B07 atomic batch في PostgreSQL معزول ثم T21/T27 end-to-end ومطابقة history/cash | OPEN؛ B12-V01، 2026-09-11 |
| B13-BLK-01 | B13.01–B13.05 | `BLOCKED_BUILD_RUNTIME_ENVIRONMENT`؛ Gradle8.9 غير مخزن والـwrapper فشل بـUnknownHostException | `docs/sync-repair/evidence/B13/V02/gradle-first-failure.txt` و`runtime-environment.json` | يمنع compile/KSP/Room schema101 واختبارات process-kill/Android وإغلاق G-B13 | static/native SQLite/regression فقط؛ لا ترقية بوابة | بيئة Gradle8.9/Android/Room مجهزة ثم أوامر B13 الموثقة | OPEN؛ أعيد فحصه B13-V02، 2026-09-11 |
| B13-BLK-02 | B13.01/B13.05 | `BLOCKED_BOOTSTRAP_SEAL_SERVER_INTEGRATION` + predecessors B10/B11؛ B08 لم يثبت حقول digest/coverage/high-watermark/delta-token المطلوبة | `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapWire.kt` و`docs/sync-repair/evidence/B13/V02/session-report.md` | يمنع promotion فعليًا واختبارات T18/T31–T34/live G-B13 | الكود يفشل مغلقًا عند غياب seal؛ staging/local protection implementation مسموح | نفذ B08 contract على PostgreSQL معزول ثم round-trip/Room وتحقق hashes قبل/بعد | OPEN؛ أعيد فحصه B13-V02، 2026-09-11 |

**قواعد التسجيل:** استخدم رموز العقد حيث تنطبق، مثل BLOCKED_BACKUP_UNVERIFIED وBLOCKED_LIVE_CONTRACT_UNVERIFIED وBLOCKED_OWNER_UNPROVEN وM03_ORG_SCOPE_UNPROVEN وSOURCE_DATA_MISSING وOUTCOME_UNKNOWN وBLOCKED_SIGNING. عائق سير عمل جديد يسمى بوضوح عائقًا تنظيميًا، لا رمز خطأ مزامنة مزعوم في التطبيق.

لكل عائق حدد النطاق: نوع بيانات/مؤسسة مستعارة/مسار/تفعيل/اختبار. عدم توفر بيانات الحالة الحقيقية لا يمنع اختبار fixture، لكنه يمنع اعتماد إصلاح الحالة نفسها. عدم قراءة الخادم لا يمنع كل كود محلي، لكنه يمنع نشر SQL اعتمادًا على القديم. سبب أمني/مالي يمنع ما يعتمد عليه، لا مجرد وضع تنبيه والاستمرار فوقه.

لا تغلق العائق بالوقت أو بإعادة المحاولة نفسها أو بتغيير عدد المراجعة. أغلقه بدليل جديد، وسجل ما تغير ومتى، ثم أعد الاختبارات المتأثرة. حافظ على تاريخه بدل حذف صفه.

## 8. سجل القرارات والانحرافات عن العقد

**القرارات التقنية الملزمة موجودة في العقد المضمّن. لا يوجد ملحق تغيير معتمد عند إعداد هذا الملف.**

| القرار/الانحراف | قسم العقد | ما اكتشف بالدليل | أثره على المهام/البيانات | القرار المعتمد ومصدر تفويضه | الحالة |
|---|---|---|---|---|---|
| بدء كتابة B11 مع بقاء G-B10 معلقة | ترتيب اعتماديات B11 وC§4.3؛ لا تعديل التصميم المرجعي | رسالة المستخدم الحالية «نفذ التالي b11» | يسمح بكتابة B11 والاختبارات المحلية؛ لا يغلق B10/B11 أو T30 ولا يبيح نشرًا | تفويض المستخدم الصريح في 2026-09-11؛ المصدر B10-V01 نفسه | ACCEPTED_LOCAL_IMPLEMENTATION_ONLY؛ لا TEST_WAIVER ولا LIVE_WRITE_AUTHORIZATION |
| بدء كتابة B10 مع بقاء تحقق B09 معلقًا | ترتيب اعتماديات B10 وC§4.3؛ لا تعديل التصميم المرجعي | أحدث رسالة المستخدم بعد تسليم B09-V02 هي «نفذ b10» | يسمح بكتابة B10 والاختبارات المحلية، لا إغلاق اعتمادياتها/بوابتها أو نشرها | تفويض المستخدم الصريح في هذه الزيارة؛ المصدر B09-V02 نفسه | ACCEPTED_LOCAL_IMPLEMENTATION_ONLY؛ لا TEST_WAIVER ولا LIVE_WRITE_AUTHORIZATION |
| اعتماد المجلد الحالي بدل إعادة فك حاوية ZIP غير المتوفرة | C§4.1 وB01.01/B01.03 | المستخدم أكد أن `Verto-425.zip` هو مجلد المشروع الحالي؛ بصمة ما قبل التنفيذ محفوظة؛ P01–P24 = 23/24 وP04 غائب | يثبت مصدر التنفيذ ببصمة شجرة لا hash الحاوية؛ يجب ألا تفترض الجلسات اللاحقة وجود P04 | توضيح المستخدم في زيارة B01-V02؛ `SOURCE_BASELINE.json` و`SOURCE_DIFF.md` | ACCEPTED_FOR_EXECUTION؛ hash حاوية ZIP غير مدعى |
| عدم إنشاء Supabase branch أثناء B02 | C§4.3 وB02.04 | المتاح هو default production branch فقط؛ إنشاء فرع تغيير خارجي قد يترتب عليه تكلفة ولا يوجد baseline/fixture كامل | تبقى بيئة الاختبار محجوبة ولا يجري أي SQL حي؛ يسمح بالعمل المحلي المستقل فقط | حدود التفويض الحالية وقاعدة عدم توسيع الأثر؛ `TEST_ENVIRONMENT_AND_FENCE.md` | ACCEPTED_SAFETY_STOP؛ يحتاج مدخلات/تفويضًا جديدًا |
| تجاوز Supabase development branch المدفوع | B02.04 وC§4.3 | السعر المكتشف 0.01344 USD/hour والمستخدم على الخطة المجانية | B02.04/G-B02 تبقيان BLOCKED؛ اختيار أول جلسة محلية مؤهلة B03 | توجيه المستخدم الصريح في 2026-09-10 | ACCEPTED؛ لا تعِد طلب الفرع دون تغير المعطيات |

| مواءمة validator مع هوية الدفع التي ينتجها B06 | C§10 وC§13.2؛ B09.01/B09.04 | InvoiceVoidCoordinator يولد factId مختلفًا لكل عكس مع writeId مشترك، وFinancialSnapshotFactoryV2 يصدره بالفعل؛ خطأ duplicate effect business identity أُعيد إنتاجه | يسمح بقبول الحقائق المختلفة دون دمجها؛ factId وimmutable hash لا يتغيران | إصلاح عدم اتساق تنفيذ محلي لا سياسة مالية أو schema جديدة؛ before/after evidence في B09/V02 | IMPLEMENTED؛ قبول Room/JSON BLOCKED |

الحقل/العمود/نوع operation غير المثبت ليس مساحة للاجتهاد. اكتب المشكلة والبدائل إن لزم لتوضيحها فقط، ولا تختر سياسة مالية/أمنية/معمارية جديدة دون الملحق المعتمد الذي يشترطه C§1.3. يجوز مواءمة أسماء الملفات التنظيمية وأسلوب الكود ضمن ما سمح به العقد، مع بقاء القرار والعقد وschema ثابتة.

<a id="handoff-log"></a>
## 9. سجل الجلسات ونموذج التسليم

### 9.1 سجل الزيارات — يضاف ولا يمحى

| Checkpoint ID | الجلسة/الزيارة | البداية/النهاية | commit المنتج عند البداية/النهاية | المهام المغلقة | نتيجة البوابة/الاختبارات | العائق | NEXT_TASK |
|---|---|---|---|---|---|---|---|
| B01-V01 | B01 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ null / null؛ بصمة مرشح قبل التنفيذ `4c5fa594...e13a0` | B01.02، B01.04 | G-B01 BLOCKED؛ لا Txx شُغّل | B01-BLK-01 | B01.01 |
| B01-V02 | B01 / الزيارة 2 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ tree hash `4c5fa594...e13a0` / لا تغيير منتج | B01.01، B01.03، B01.05 | G-B01 PASS؛ بنية packager كاملة وP paths 23/24؛ لا Txx | أُغلق B01-BLK-01 بتعيين المستخدم للمصدر | B02.01 |
| B02-V01 | B02 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ tree hash المنتج `4c5fa594...e13a0` / لا تغيير منتج | B02.02، B02.03، B02.05 | G-B02 BLOCKED؛ export حي وAPK المحلي PASS؛ لا Txx | B02-BLK-01/02/03 | B03.01 |
| B02-V02 | B02 / الزيارة 2 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ لا تغيير منتج | إعادة تحقق B02.01 وB02.04؛ إغلاق signing blocker لـB02.05 | G-B02 BLOCKED؛ installed signer PASS؛ لا Txx | B02-BLK-01/02؛ أُغلق B02-BLK-03 | B02.04 |
| B03-V01 | B03 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ baseline `4c5fa594...e13a0` / product-scope `e1c4c9a8...95aef` | B03.01–B03.05 | G-B03 PASS؛ JVM 27/27؛ Room 5/5؛ T48 يبقى B20 | B02-BLK-01/02 لا يمنعان العمل المحلي | B04.01 |
| B04-V01 | B04 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ `e1c4c9a8...95aef` / product-scope `a600f4e0...09013` | B04.01–B04.05 | G-B04 PASS؛ JVM 91/91؛ Android 5/5؛ app compile PASS؛ T10/T33 NOT_RUN | B02-BLK-01/02 لا يمنعان العمل المحلي | B05.01 |
| B05-V01 | B05 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ `a600f4e0...09013` / expanded product-scope `e2efa886...8e421` | B05.01–B05.05 | G-B05 PASS؛ JVM 95/95؛ Android 12/12؛ app compile PASS؛ T08/T15/T17/T23/T39 NOT_RUN | B02-BLK-01/02 لا يمنعان العمل المحلي | B06.01 |
| B06-V01 | B06 / الزيارة 1 | 2026-09-10 / 2026-09-10 (Africa/Khartoum) | لا Git؛ `e2efa886...8e421` / expanded product-scope `6b69c2c5...45090` | B06.01–B06.05 | G-B06 PASS محليًا؛ JVM 195/195؛ Room 2/2؛ schema/producer وapp compile PASS؛ T09/T12/T19/T20 NOT_RUN | B02-BLK-01/02؛ feature:invoice tests لها عائق classpath سابق غير متعلق | B09.01 |

| B09-V01 | B09 / الزيارة 1 | 2026-09-10 / 2026-09-10 (UTC؛ وقت الالتقاط في environment.json) | لا Git؛ مصدر ZIP بعد B06 / expanded product `65aacd59b1c7...` | لا إغلاق رسمي؛ تنفيذ B09.01–B09.05 قائم | G-B09 BLOCKED؛ 612 source/SQLite و347 mapping PASS ضمن حدودهما؛ 31 Room و9 JVM NOT_RUN | B09-BLK-01؛ B02-BLK-01/02 مستمران | B09.01 |

| B09-V02 | B09 / الزيارة 2 | 2026-09-10 / 2026-09-10 (UTC؛ أوقات البيئة والسجلات) | لا Git؛ `65aacd59b1c7...` / `2aa1a4e16d9e...` | لا إغلاق؛ تصحيح validator وإضافة 5 اختبارات | G-B09 BLOCKED؛ before FAIL/after semantic PASS مع stubs؛ 347 mapping و612 model PASS؛ 12 JVM/33 Room NOT_RUN | B09-BLK-01؛ B02-BLK-01/02 محفوظان | B09.01 |
| B10-V01 | B10 / الزيارة 1 | 2026-09-10 / 2026-09-10 UTC | لا Git؛ input `2aa1a4e16d9e...`؛ output في product-tree.json | لا إغلاق رسمي؛ كتابة B10.01–B10.05 بطلب صريح | G-B10 BLOCKED؛ 15 SQLite و12 policy PASS؛ 24 JVM/14 Inbox Room و34 مالية NOT_RUN ببيئتها | B10-BLK-01/02؛ B09/B02 محفوظة | B10.01 |
| B11-V01 | B11 / الزيارة 1 | 2026-09-11 / 2026-09-11 UTC | لا Git؛ input `2c937997...` / output `2af04ead...` | لا إغلاق رسمي؛ تنفيذ B11.01–B11.05 محليًا بطلب صريح | G-B11 BLOCKED؛ B11 static وnative SQLite PASS؛ Gradle/Room/UI/T30 NOT_RUN | B11-BLK-01؛ B10/B09/B02 محفوظة | B11.01 |
| B12-V01 | B12 / الزيارة 1 | 2026-09-11 / 2026-09-11 UTC | لا Git؛ input B11 legacy `2af04ead...`؛ expanded baseline `bf610527...` / output `150e580c...` | لا إغلاق رسمي؛ تنفيذ B12.01–B12.05 محليًا بطلب صريح | G-B12 BLOCKED؛ static22/SQLite/schema/pure Kotlin PASS؛ Gradle/Room/T21/T27/PostgreSQL NOT_RUN | B12-BLK-01/02؛ B11/B09/B02 محفوظة | B12.01 |
| B13-V01 | B13 / الزيارة 1 | 2026-09-11 / 2026-09-11 UTC | لا Git؛ input documented B12 `150e580c...` (recomputed under B13 explicit algorithm `4880ac00...`) / output `158fccae...` | لا إغلاق رسمي؛ تنفيذ B13.01–B13.05 محليًا بطلب صريح | G-B13 BLOCKED؛ static48/48 وSQLite/regressions PASS؛ Gradle/Room/B08/T18/T31–T34 NOT_RUN | B13-BLK-01/02؛ B10/B11/B12 blockers محفوظة | B13.01 |
| B13-V02 | B13 / الزيارة 2 | 2026-09-11 / 2026-09-11 UTC | لا Git؛ product hash `158fccae...` / `158fccae...` (لا تغيير منتج) | لا مهام مغلقة؛ إعادة تحقق للمهمة المصرح بها فقط | G-B13 BLOCKED؛ static48/48 وSQLite/B06/B11/B12 regressions PASS؛ Gradle8.9/Android/B08/T18/T31–T34 NOT_RUN | B13-BLK-01/02 مستمران؛ B14 لم يبدأ | B13.01 |

سطر الحالة الابتدائية أعلاه محفوظ تاريخيًا في نص الوثيقة، وأول زيارة تنفيذ فعلية هي B01-V01 المسجلة هنا.

### 9.3 سجل B01-V01

```text
CHECKPOINT_ID: B01-V01
SESSION_ID / VISIT: B01 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: لا فرع؛ SOURCE_BASELINE.json حالته NOT_ACCEPTED
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / بصمة المرشح قبل التنفيذ 4c5fa594f4a4c7c122013dee1fa2184238583ddce2f4ecbbc7666d94e26e13a0
LAST_VERIFIED_PRODUCT_COMMIT: null؛ لم تعتمد شجرة منتج
WORKTREE_REMAINS: ملفات أدلة/توثيق B01 غير ملتزمة؛ لا يمكن إنتاج git diff لأن .git غير موجود؛ بصمات الأدلة الثابتة في artifact-hashes.sha256

COMPLETED_TASKS: B01.02 (source-and-project-discovery.md)، B01.04 (EVIDENCE_INDEX.md وcommands-and-exit-codes.md)
PARTIAL_TASKS: B01.05: Backlog في الجذر وChangelog محدث؛ يبقى فرع التنفيذ وتحويل NEXT إلى B02 بعد إغلاق B01. B01.01/B01.03 محجوبتان.
CHANGED_FILES_AND_SYMBOLS: SOURCE_BASELINE.json؛ SOURCE_DIFF.md؛ EVIDENCE_INDEX.md؛ docs/sync-repair/evidence/B01/V01/*؛ CHANGELOG.md؛ Backlog؛ لا رموز منتج
PRODUCT_IMPACT: لا أثر على Kotlin أو Room أو SQL أو Supabase أو سلوك التطبيق
TEST_RUNS: G-B01 BLOCKED؛ اتساق Backlog وبصمة العقد PASS؛ Gradle --version PASS؛ documentation gate FAIL والمتكاملة 126؛ لا Txx ولا build/test منتج
EVIDENCE_RECHECK_REQUIRED: لا نتائج T سابقة استُخدمت أو غُيرت
SERVER_ACTIONS: NONE
DATA_ACTIONS: لا شيء؛ لا backup أو بيانات جهاز قُرئت
BACKUP / SIGNING / SCOPE STATUS: backup NOT_CHECKED؛ signing غير متوفر محليًا؛ scope الإنتاج غير مفحوص
OPERATIONS_IN_FLIGHT: لا توجد
BLOCKERS: B01-BLK-01؛ الأرشيف الإلزامي مفقود، فيمنع قبول المصدر والفرع
SAFE_LOCAL_WORK_REMAINING: لا مهمة إلزامية أخرى في B01 تغلق دون الأرشيف؛ لا يبدأ B02
DO_NOT_REPEAT: لا تعد جرد README/CONTRIBUTING/settings/scripts أو إنشاء هيكل الأدلة؛ تحقق من الأرشيف عند توفره فقط
NEXT_SESSION / NEXT_TASK: B01 / B01.01
NEXT_ACTION: توفير Verto-425.zip، SHA مطابق، فحص البنية، ثم فك منفصل دون الكتابة فوق المرشح
NEXT_ACTION_PRECONDITIONS: ملف Verto-425.zip نفسه؛ لا بديل باسم أو إصدار مختلف
RESUME_FROM: SOURCE_DIFF.md وSOURCE_BASELINE.json؛ المرشح موثق غير معتمد
SESSION_FINAL_STATE: BLOCKED
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ SOURCE_BASELINE؛ SOURCE_DIFF؛ EVIDENCE_INDEX؛ دليل B01-V01 وبصماته
```

### 9.4 سجل B01-V02

```text
CHECKPOINT_ID: B01-V02
SESSION_ID / VISIT: B01 / 2
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / SOURCE_BASELINE.json ACCEPTED_CURRENT_FOLDER_AS_EXECUTION_SOURCE
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / 4c5fa594f4a4c7c122013dee1fa2184238583ddce2f4ecbbc7666d94e26e13a0
LAST_VERIFIED_PRODUCT_COMMIT: null؛ إثبات المصدر ببصمة شجرة لا commit
WORKTREE_REMAINS: توثيق B01 فقط؛ لا ملفات منتج معدلة؛ لا git diff لغياب .git

COMPLETED_TASKS: B01.01، B01.03، B01.05؛ اكتملت B01 كلها
PARTIAL_TASKS: لا توجد في B01
CHANGED_FILES_AND_SYMBOLS: SOURCE_BASELINE.json؛ SOURCE_DIFF.md؛ EVIDENCE_INDEX.md؛ docs/sync-repair/evidence/B01/V02/*؛ CHANGELOG.md؛ Backlog؛ لا رموز منتج
PRODUCT_IMPACT: لا أثر على Kotlin أو Room أو SQL أو Supabase أو سلوك التطبيق
TEST_RUNS: G-B01 PASS؛ source-packager structure PASS؛ P paths 23/24 مع P04 مفقود مسجل؛ contract SHA PASS؛ لا Txx
EVIDENCE_RECHECK_REQUIRED: لا نتائج T سابقة استُخدمت أو غُيرت
SERVER_ACTIONS: NONE
DATA_ACTIONS: لا شيء
BACKUP / SIGNING / SCOPE STATUS: backup NOT_CHECKED؛ signing غير متوفر محليًا؛ scope الإنتاج غير مفحوص
OPERATIONS_IN_FLIGHT: لا توجد
BLOCKERS: لا عائق نشط في B01؛ أُغلق B01-BLK-01 بتعيين المستخدم للمصدر الحالي
SAFE_LOCAL_WORK_REMAINING: B02 مؤهلة وفق الاعتماديات لكن لم تبدأ في هذه الزيارة
DO_NOT_REPEAT: لا تعِد B01 أو تفك ZIP فوق الشجرة؛ تحقق من حاوية ZIP فقط كدليل إضافي إن ظهرت
NEXT_SESSION / NEXT_TASK: B02 / B02.01
NEXT_ACTION: حصر متطلبات النسخة المتحققة لبيانات Room والمرفقات وتحديد المتاح دون لمس بيانات حية
NEXT_ACTION_PRECONDITIONS: قراءة B02 وC§4 وC§18–23؛ لا افتراض تفويض إنتاج أو توفر جهاز
RESUME_FROM: المصدر الحالي المقبول ببصمة tree؛ P04 غير موجود ومسجل للمرحلة المالكة
SESSION_FINAL_STATE: DONE
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ SOURCE_BASELINE؛ SOURCE_DIFF؛ EVIDENCE_INDEX؛ دليل B01-V02
```

### 9.5 سجل B02-V01

```text
CHECKPOINT_ID: B02-V01
SESSION_ID / VISIT: B02 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / SOURCE_BASELINE.json ACCEPTED_CURRENT_FOLDER_AS_EXECUTION_SOURCE
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / لا تغيير منتج؛ baseline المنتج 4c5fa594f4a4c7c122013dee1fa2184238583ddce2f4ecbbc7666d94e26e13a0
LAST_VERIFIED_PRODUCT_COMMIT: null؛ لا كود منتج تغير ولا Txx شُغّل
WORKTREE_REMAINS: توثيق وأدلة B02 فقط؛ لا git diff لغياب .git؛ بصمات artifacts محفوظة داخل V01

COMPLETED_TASKS: B02.02 export تعريفات حي كامل قراءة فقط؛ B02.03 مقارنة وخريطة owner/scope؛ B02.05 خطة fence/rollback وفحص APK المحلي
PARTIAL_TASKS: لا مهمة جزئية؛ B02.01 وB02.04 محجوبتان بمدخلات خارجية محددة
CHANGED_FILES_AND_SYMBOLS: docs/sync-repair/evidence/B02/V01/*؛ CHANGELOG.md؛ Backlog؛ لا Kotlin/Room/SQL/Supabase mutation
PRODUCT_IMPACT: لا أثر على سلوك التطبيق أو المخطط أو البيانات أو الإنتاج
TEST_RUNS: G-B02 BLOCKED؛ jq JSON validation PASS؛ apksigner local APK verification PASS؛ adb inventory بلا جهاز؛ لا T01–T50
EVIDENCE_RECHECK_REQUIRED: لا نتائج T سابقة تغيرت أو اعتمدت
SERVER_ACTIONS: READ_ONLY على مشروع Verto-app؛ catalog/functions/tables/RLS/grants/migrations/contract/rollout/storage فقط
DATA_ACTIONS: لا شيء؛ لم تُقرأ صفوف أعمال أو مؤسسات أو مستخدمين ولم تُكتب بيانات
BACKUP / SIGNING / SCOPE STATUS: backup BLOCKED_UNVERIFIED؛ local APK signer verified/installed signer blocked؛ live scope contract verified دون tenant identifiers
OPERATIONS_IN_FLIGHT: لا توجد
BLOCKERS: B02-BLK-01 نسخة الجهاز؛ B02-BLK-02 البيئة المعزولة وclean baseline؛ B02-BLK-03 signer النسخة المثبتة
SAFE_LOCAL_WORK_REMAINING: B03 مؤهلة باستثناء C§4.3، مع منع SQL حي أو ادعاء بوابات التكامل
DO_NOT_REPEAT: لا تعِد export الخادم ما لم يتغير migration head؛ لا تنشئ branch أو تصلح بيانات؛ لا تعتبر APK/الكود نسخة Room
NEXT_SESSION / NEXT_TASK: B03 / B03.01
NEXT_ACTION: جرد مخطط Room وتنفيذ migration غير مدمرة للجداول/الحقول التنظيمية والنسخ مع اختبارات محلية متاحة
NEXT_ACTION_PRECONDITIONS: قراءة بطاقة B03 ومراجعها؛ إبقاء B02 blockers؛ عدم لمس الإنتاج
RESUME_FROM: SYNC_SERVER_DEFINITIONS وSERVER_SOURCE_DRIFT مرجعان مثبتان؛ ابدأ B03 من المخطط المحلي لا من إعادة B02
SESSION_FINAL_STATE: BLOCKED
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ BACKUP_VERIFICATION؛ SYNC_SERVER_DEFINITIONS؛ SERVER_SOURCE_DRIFT؛ TEST_ENVIRONMENT_AND_FENCE؛ session-report
```

### 9.6 سجل B02-V02

```text
CHECKPOINT_ID: B02-V02
SESSION_ID / VISIT: B02 / 2
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / المصدر المقبول في B01-V02
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / لا تغيير منتج
LAST_VERIFIED_PRODUCT_COMMIT: null؛ فحص جهاز/سيرفر فقط
WORKTREE_REMAINS: تحديث توثيق وأدلة B02 فقط؛ installed APK مؤقت خارج المستودع بصلاحية 0600

COMPLETED_TASKS: لا مهمة جديدة؛ أُعيد تحقق B02.01/B02.04 وأُغلق عائق التوقيع ضمن B02.05
PARTIAL_TASKS: B02.01 لا يزال BLOCKED؛ B02.04 ينتظر تأكيد تكلفة branch ثم بيئة/fixture
CHANGED_FILES_AND_SYMBOLS: Backlog؛ Changelog؛ B02 evidence docs/hashes؛ لا Kotlin/Room/SQL migration
PRODUCT_IMPACT: لا أثر على التطبيق أو الجهاز أو بيانات السيرفر
TEST_RUNS: adb package/run-as/root/external-files checks؛ installed apksigner PASS؛ server archive aggregate count=0؛ لا Txx
EVIDENCE_RECHECK_REQUIRED: لا توجد نتيجة T تغيرت
SERVER_ACTIONS: READ_ONLY؛ project/cost/catalog/archive aggregate فقط؛ التوكن لم يُكتب أو يُسجل
DATA_ACTIONS: لا شيء؛ لا payload أو tenant identifier قُرئ ولا كتابة نُفذت
BACKUP / SIGNING / SCOPE STATUS: backup BLOCKED؛ installed signing PASS؛ scope unchanged
OPERATIONS_IN_FLIGHT: لا توجد
BLOCKERS: B02-BLK-01؛ B02-BLK-02؛ أُغلق B02-BLK-03
SAFE_LOCAL_WORK_REMAINING: إنشاء branch بعد cost confirmation؛ B03 وفق الاستثناء إن بقي B02 محجوبًا
DO_NOT_REPEAT: لا run-as retries؛ لا root/reinstall/clear data؛ لا استخدام logical v6 backup بدل SQLite/WAL؛ لا create branch قبل تأكيد التكلفة
NEXT_SESSION / NEXT_TASK: B02 / B02.04
NEXT_ACTION: تأكيد 0.01344 USD/hour ثم confirm_cost/create_branch بلا بيانات إنتاج
NEXT_ACTION_PRECONDITIONS: موافقة تكلفة صريحة؛ لا merge/main writes
RESUME_FROM: signer مطابق؛ app-private DB غير قابل للوصول؛ server archive صفر؛ branch cost معروف
SESSION_FINAL_STATE: BLOCKED
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ B02 BACKUP_VERIFICATION؛ TEST_ENVIRONMENT_AND_FENCE؛ commands/results؛ hashes؛ session report
```

### 9.7 سجل B03-V01

```text
CHECKPOINT_ID: B03-V01
SESSION_ID / VISIT: B03 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / المصدر المقبول في B01-V02
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git؛ baseline tree 4c5fa594f4a4c7c122013dee1fa2184238583ddce2f4ecbbc7666d94e26e13a0
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / e1c4c9a86fae95ebf2b1e00fc0df5be15e0041591ba8219730e700eb17f95aef لنطاق app/schemas + data/database/src (268 ملفًا)
LAST_VERIFIED_PRODUCT_COMMIT: null؛ product-scope fingerprint أعلاه ثبت بعد الاختبارات
WORKTREE_REMAINS: تعديلات Kotlin/Room واختبارات ومخطط 97 ووثائق B03 غير ملتزمة؛ المستودع بلا .git؛ بصمات artifacts في B03/V01

COMPLETED_TASKS: B03.01–B03.05؛ أدلتها في schema-and-invariants.md وcommands-and-results.md
PARTIAL_TASKS: لا توجد في B03؛ T48 الكامل ليس مهمة B03 ويبقى NOT_RUN إلى B20
CHANGED_FILES_AND_SYMBOLS: AppDatabase 97؛ MIGRATION_96_97؛ MigrationCatalog؛ SyncRepairV2Entities/Dao؛ UnifiedSyncDao؛ cursor/attachment/cash/expense entities؛ schema 97؛ اختبارات JVM وAndroid
PRODUCT_IMPACT: تخزين محلي إضافي غير هدمي لسلطة النسخ والأجيال والدفعات والأدلة؛ فصل received/applied؛ حقول lifecycle؛ تحويل cash legacy إلى Minor عند الترقية
TEST_RUNS: :data:database:testDebugUnitTest PASS 27/27؛ KSP/schema PASS؛ combined focused connectedDebugAndroidTest PASS 5/5 على Pixel_8 AVD Android 17؛ G-B03 PASS
EVIDENCE_RECHECK_REQUIRED: T48 لا يرقى إلى PASS؛ اختبار APK signed in-place والجهاز الحقيقي مؤجل إلى B20 بعد تحقق backup
SERVER_ACTIONS: NONE؛ لا Supabase branch ولا قراءة/كتابة إنتاجية في B03
DATA_ACTIONS: fixtures محلية مؤقتة فقط؛ لا بيانات مستخدم أو مؤسسة حية
BACKUP / SIGNING / SCOPE STATUS: B02 backup BLOCKED؛ signing PASS المثبت سابقًا؛ test server BLOCKED؛ local Room fixture PASS
OPERATIONS_IN_FLIGHT: لا توجد؛ أُغلق المحاكي بعد الاختبار
BLOCKERS: B02-BLK-01 وB02-BLK-02 باقيان خارج B03؛ لا عائق داخل B03
SAFE_LOCAL_WORK_REMAINING: B04 مؤهلة وفق B03 وC§4.3؛ يمنع SQL حي أو ادعاء إصلاح حالة الجهاز
DO_NOT_REPEAT: لا تعِد B03 أو schema 96→97؛ استخدم schema 97 وDAO الجديدين؛ لا تعتبر G-B03 بديل T48 الكامل
NEXT_SESSION / NEXT_TASK: B04 / B04.01
NEXT_ACTION: إنشاء OwnershipRegistry موحد وربط capture/cleanup به مع اختبارات unknown owner وmulti-owner
NEXT_ACTION_PRECONDITIONS: قراءة بطاقة B04 ومراجع العقد؛ لا تحتاج paid branch للعمل المحلي؛ لا تلمس بيانات الجهاز
RESUME_FROM: docs/sync-repair/evidence/B03/V01؛ AppDatabase schema 97؛ SyncRepairV2Dao
SESSION_FINAL_STATE: DONE
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ B03 session-report؛ commands-and-results؛ schema-and-invariants؛ artifact hashes
```

### 9.8 سجل B04-V01

```text
CHECKPOINT_ID: B04-V01
SESSION_ID / VISIT: B04 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / المصدر المقبول في B01-V02
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git؛ product-scope السابق e1c4c9a86fae95ebf2b1e00fc0df5be15e0041591ba8219730e700eb17f95aef
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / a600f4e0386d3ea6f240cd50ad2efb999ff960a7e96425d8448156ae52309013 لنطاق app/schemas + app/src + data/database/src + data/operations/src + data/sync/src (612 ملفًا)
LAST_VERIFIED_PRODUCT_COMMIT: null؛ product-scope fingerprint أعلاه ثبت بعد الاختبارات وapp compile
WORKTREE_REMAINS: تعديلات Kotlin/Room واختبارات ووثائق B03/B04 غير ملتزمة؛ المستودع بلا .git؛ بصمات artifacts في B04/V01

COMPLETED_TASKS: B04.01–B04.05؛ المصفوفة وخريطة الاستدعاءات والتقرير في docs/sync-repair/evidence/B04/V01
PARTIAL_TASKS: لا توجد في B04؛ T10/T33 الكاملان ليسا بوابة B04 المحلية ويبقيان NOT_RUN
CHANGED_FILES_AND_SYMBOLS: SyncOwnershipRegistry؛ SyncPendingProtection؛ SyncRepairV2Dao؛ UnifiedSyncPullEngine؛ UnifiedSyncSnapshotApplier؛ LegacySyncV2MigrationCoordinator؛ SyncHealthMonitor؛ SyncManager؛ UnifiedSyncRecoveryRegistry؛ اختبارات ownership/protection؛ إصلاح immutable amountMinor compile في ExpenseRepository وExpensesOperationsAdapter
PRODUCT_IMPACT: مالك صريح 35/35؛ قرار حماية واحد fail-closed قائم على حالة المالك والمحتوى/التبعيات؛ منع تبني Party بلا مؤسسة؛ تعطيل prune أعمى إلى جلسة B13
TEST_RUNS: :data:sync:testDebugUnitTest PASS 64/64؛ :data:database:testDebugUnitTest PASS 27/27؛ focused connectedDebugAndroidTest PASS 5/5 على Pixel_8 AVD Android 17؛ :app:compileDebugKotlin PASS؛ G-B04 PASS
EVIDENCE_RECHECK_REQUIRED: T10 وT33 يبقيان NOT_RUN حتى سيناريوهات B14/B13 الكاملة؛ لا نتيجة T رُقيت من fixture جزئي
SERVER_ACTIONS: NONE؛ لا SQL أو Supabase أو نشر
DATA_ACTIONS: Room in-memory fixtures محلية فقط؛ لا بيانات مستخدم/مؤسسة حية
BACKUP / SIGNING / SCOPE STATUS: B02 backup BLOCKED؛ signing PASS المثبت سابقًا؛ test server BLOCKED؛ نطاق B04 محلي فقط
OPERATIONS_IN_FLIGHT: لا توجد
BLOCKERS: B02-BLK-01 وB02-BLK-02 باقيان خارج B04؛ لا عائق داخل B04
SAFE_LOCAL_WORK_REMAINING: B05 مؤهلة وفق B03/B04 وC§4.3؛ يمنع SQL حي أو ادعاء E1 PASS قبل B05
DO_NOT_REPEAT: لا تعِد سجل الملكية ولا خرائط pending خاصة؛ استخدم SyncOwnershipRegistry/SyncPendingProtection؛ لا تُفعّل prune الأعمى قبل B13
NEXT_SESSION / NEXT_TASK: B05 / B05.01
NEXT_ACTION: إضافة local generation ذرية لكل protected key وربط CAS والحفظ بالحماية المركزية مع اختبارات محلية
NEXT_ACTION_PRECONDITIONS: قراءة بطاقة B05 وC§6.2/C§9؛ schema 97 وB04-V01؛ عدم لمس الإنتاج
RESUME_FROM: docs/sync-repair/evidence/B04/V01؛ SyncOwnershipRegistry وSyncPendingProtection وSyncRepairV2Dao
SESSION_FINAL_STATE: DONE
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ B04 session-report؛ commands-and-results؛ ownership matrix؛ call map؛ artifact hashes
```

### 9.9 سجل B05-V01

```text
CHECKPOINT_ID: B05-V01
SESSION_ID / VISIT: B05 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / المصدر المقبول في B01-V02
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git؛ product-scope السابق a600f4e0386d3ea6f240cd50ad2efb999ff960a7e96425d8448156ae52309013
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / e2efa886adc7bc0c248b6d04ca7faf365a098a2a1f389866ac090832f0d8e421 لنطاق موسع يشمل app/schemas + app/src + data/database/src + data/network/src + data/operations/src + data/sync/src (704 ملفات)
LAST_VERIFIED_PRODUCT_COMMIT: null؛ بصمة النطاق أعلاه بعد JVM/Room/app compile
WORKTREE_REMAINS: تعديلات B03–B05 غير ملتزمة لغياب .git؛ ملفات B05 وبصماتها في docs/sync-repair/evidence/B05/V01

COMPLETED_TASKS: B05.01–B05.05؛ أدلة packet/lease/CAS/predecessor وfixtures والخريطة والتقرير في B05/V01
PARTIAL_TASKS: لا توجد في B05؛ ربط DTO/snapshots الكامل B06 وserver v2 الفعلي B07 ليسا جزءًا من هذه الجلسة
CHANGED_FILES_AND_SYMBOLS: schema 98 وMIGRATION_97_98؛ SyncRepairV2Dao/UnifiedSyncOutboxDao؛ FrozenMutationStore/FrozenMutationCodec؛ SyncBatchCoordinatorV2؛ PreviousMutationOutcome؛ UnifiedOutboxWriter؛ Push/Conflict/Pull engines؛ UnifiedSyncPushRemote؛ اختبارات JVM وRoom
PRODUCT_IMPACT: intent/generation/refs ذريًا؛ frozen bytes/hash مرة واحدة؛ baseVersion من applied/receipt predecessor؛ lease token+epoch وتجديد؛ ACK receipt/hash/CAS؛ old outcome fail-closed
TEST_RUNS: database JVM PASS 27/27؛ sync JVM PASS 68/68؛ database Android PASS 4/4؛ sync Android PASS 8/8 على Pixel_8 AVD Android 17؛ app compile PASS؛ G-B05 PASS
EVIDENCE_RECHECK_REQUIRED: T08/T15/T17/T23/T39 تبقى NOT_RUN؛ الدليل المحلي لا يثبت producer/server/repair end-to-end، وB07 يملك قبول السيرفر الفعلي
SERVER_ACTIONS: NONE؛ لا Supabase/SQL/نشر أو قراءة إنتاجية
DATA_ACTIONS: Room in-memory/migration fixtures محلية فقط؛ لا بيانات مستخدم أو مؤسسة حية
BACKUP / SIGNING / SCOPE STATUS: B02 backup BLOCKED؛ signing PASS المثبت سابقًا؛ test server BLOCKED؛ scopeEpoch مختبر محليًا فقط
OPERATIONS_IN_FLIGHT: لا توجد؛ أُغلق المحاكي بعد الاختبارات
BLOCKERS: B02-BLK-01 وB02-BLK-02 باقيان خارج B05؛ لا عائق داخل B05
SAFE_LOCAL_WORK_REMAINING: B06 مؤهلة وفق B05 وC§4.3؛ لا server round-trip أو نشر قبل بيئة B07 القانونية
DO_NOT_REPEAT: لا تعِد schema 97→98 أو packet/frozen/lease CAS؛ لا تغير wire/baseVersion بعد التجميد؛ لا ترقِّ Txx من fixtures المحلية
NEXT_SESSION / NEXT_TASK: B06 / B06.01
NEXT_ACTION: تعريف DTO schema versioned وvalidators عند network/sync مع fixed-point money وتمييز missing/unknown، وإضافة exact serialization fixtures قبل ربط المنتجين
NEXT_ACTION_PRECONDITIONS: قراءة بطاقة B06 وC§6.6–6.8/C§7/C§8.1–8.6؛ schema 98 وB05-V01؛ لا SQL حي ولا ادعاء round-trip قبل B07
RESUME_FROM: FrozenMutationStore.captureOwner وFrozenMutationCodec؛ packet-and-ownership-map.md؛ ابدأ complete snapshots لكل operation/owner في B06
SESSION_FINAL_STATE: DONE
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ EVIDENCE_INDEX؛ B05 commands/results؛ golden fixtures؛ packet/ownership map؛ session report؛ artifact hashes
```

### 9.10 سجل B06-V01

```text
CHECKPOINT_ID: B06-V01
SESSION_ID / VISIT: B06 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10 / Africa/Khartoum
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / المصدر المقبول في B01-V02
START_PRODUCT_COMMIT: null؛ مساحة العمل ليست Git؛ product-scope السابق e2efa886adc7bc0c248b6d04ca7faf365a098a2a1f389866ac090832f0d8e421
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / 6b69c2c5c37ae6e3ceb313d24e97a45f0f1d253d6e94cbbeda8e72b19fb45090 لنطاق موسع يشمل schema/tools وapp/schemas/app/src وdata وfeature invoice/payment/inventory (909 ملفات)
LAST_VERIFIED_PRODUCT_COMMIT: null؛ بصمة النطاق أعلاه بعد schema/producer gates وJVM/app compile
WORKTREE_REMAINS: تعديلات B03–B06 غير ملتزمة لغياب .git؛ أدلة B06 وبصماتها في docs/sync-repair/evidence/B06/V01

COMPLETED_TASKS: B06.01–B06.05؛ DTO/schema واللقطة المالية وربط المنتجين والتجميد والخريطة والتقرير
PARTIAL_TASKS: لا توجد في B06؛ T09/T12/T19/T20 الكاملة وPostgreSQL/Room round-trip ليست بوابة B06 المحلية وتبقى NOT_RUN
CHANGED_FILES_AND_SYMBOLS: SYNC_CONTRACT_V2.schema.json؛ FinancialSyncContractV2؛ FinancialSnapshotFactoryV2؛ FinancialOutboxWriter؛ SpecializedMutationCaptureV2؛ UnifiedStrongerSourceFactory؛ UnifiedFinancialOwner310Route؛ InventoryStockWriter؛ invoice/payment/return coordinators؛ expense/cash/reconciliation/purchase/client-credit producers؛ tests/tools/maps/docs
PRODUCT_IMPACT: full immutable financial snapshots مع hash دلالي؛ capture ذري للpacket/generation/refs/batch؛ owner310 retry بلا قراءة DB؛ movement/cost/credit camelCase وMinor؛ purchase/cash child rows مجمدة
TEST_RUNS: schema gate PASS؛ producer wiring gate PASS؛ network JVM 56/56؛ sync JVM 70/70؛ payment JVM 27/27؛ operations JVM 42/42؛ Room commit/rollback 2/2 على Pixel_8 AVD Android 17؛ app/feature compile PASS؛ G-B06 PASS محليًا
EVIDENCE_RECHECK_REQUIRED: T09/T12/T19/T20 تبقى NOT_RUN حتى PostgreSQL/Room/device scenarios؛ feature:invoice unit test source لا يترجم بسبب test classpath سابق (kotlin.test وMockContext) بينما main compile PASS
SERVER_ACTIONS: NONE؛ لا Supabase/SQL/نشر أو قراءة إنتاجية
DATA_ACTIONS: NONE؛ لا قاعدة Room حية ولا بيانات مستخدم/مؤسسة
BACKUP / SIGNING / SCOPE STATUS: B02 backup BLOCKED؛ signing PASS سابقًا؛ test server BLOCKED؛ B06 نطاق محلي فقط
OPERATIONS_IN_FLIGHT: لا توجد؛ أُغلق المحاكي بعد الاختبار
BLOCKERS: B02-BLK-01 وB02-BLK-02 يمنعان B07؛ عائق feature:invoice test classpath مسجل ولا يغيّر نجاح حزم B06 ذات الصلة
SAFE_LOCAL_WORK_REMAINING: B09 أول جلسة مؤهلة وفق B03/B04/B06 وC§4.3؛ B07/B08 غير مؤهلتين حتى B02/server
DO_NOT_REPEAT: لا تعِد DTO/snapshot/producer freeze؛ لا تجعل prepare يقرأ business tables؛ لا ترقِّ Txx من الاختبارات المحلية
NEXT_SESSION / NEXT_TASK: B09 / B09.01
NEXT_ACTION: تنفيذ FinancialMaterializer الفعلي داخل Room للّقطة الكاملة مع ترتيب التبعيات وCAS/versions واختبارات rollback
NEXT_ACTION_PRECONDITIONS: قراءة بطاقة B09 وC§11–13 وملحق A؛ استخدام B06-V01 وB04/B05؛ لا SQL حي
RESUME_FROM: docs/sync-repair/evidence/B06/V01؛ FinancialAggregateSnapshotV2 وFrozenMutationStore؛ ابدأ materializer في B09
SESSION_FINAL_STATE: DONE
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ EVIDENCE_INDEX؛ SYNC_IMPLEMENTATION_MAP؛ B06 schema/golden؛ producer/ownership maps؛ commands/results؛ session report؛ artifact hashes
```

### 9.11 سجل B09-V01

```text
CHECKPOINT_ID: B09-V01
SESSION_ID / VISIT: B09 / 1
STARTED_AT / ENDED_AT / TIMEZONE: 2026-09-10 / 2026-09-10T20:02:47.647677+00:00 / UTC
BRANCH / SOURCE_BASELINE: NO_GIT_TREE_HASH_MODE / ZIP المستخدم Verto-425(3).zip بعد B06؛ لا baseline reset
START_PRODUCT_COMMIT: null؛ هوية B06 التاريخية 6b69c2c5c37ae6e3ceb313d24e97a45f0f1d253d6e94cbbeda8e72b19fb45090
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: null / 65aacd59b1c71ae3b5f8353841c999ab536770dd13f818147bf3dd84632344c9
LAST_VERIFIED_PRODUCT_COMMIT: null؛ لم تُشغل بوابة Gradle/Room على هذا المنتج
WORKTREE_REMAINS: كود وأدلة B09 كاملة في ZIP؛ product-tree.json وchange.patch وartifact-hashes.sha256 يثبتان المصدر والنطاق
COMPLETED_TASKS: لا مهمة مغلقة رسميًا؛ الأقسام B09.01–B09.05 منفذة ككود واختبارات
PARTIAL_TASKS: B09.01–B09.05 BLOCKED لاعتماد التشغيل الفعلي، لا TODO تصميم مخفي
CHANGED_FILES_AND_SYMBOLS: FinancialMaterializerV2/Contract/EffectVerifier؛ mapping مشترك؛ DAO ABORT؛ pull/stronger/snapshot/Hilt؛ cursor CAS؛ child pending protection؛ tests/tools/docs
PRODUCT_IMPACT: تطبيق مالي فعلي لكل الجداول التسعة؛ لا FinancialInbox-only ولا إعادة أثر نقد/مخزون/عمولة؛ حماية scope/pending/FKs/immutable versions
TEST_RUNS: 612 فحص source/schema-SQLite PASS؛ 347 actual mapping/Money assertions PASS؛ توقيعات selected Kotlin مع stubs فقط؛ B06 schema وproducer static PASS
NOT_RUN: 9 JVM و31 Room instrumentation؛ Gradle/KSP/Hilt/app compile؛ PostgreSQL؛ T01–T50؛ جهازان/production
EVIDENCE_RECHECK_REQUIRED: أدلة B04 pending/B05 cursor/B06 shared producer mapping وapp compile التاريخية لا تثبت الشجرة الحالية؛ rerun في بيئة البناء
SERVER_ACTIONS: NONE
DATA_ACTIONS: SYNTHETIC_IN_MEMORY_SQLITE_ONLY؛ لا بيانات مستخدم أو قاعدة Room حية
BACKUP / SIGNING / SCOPE STATUS: B02 محفوظ تاريخيًا؛ لا قراءة جديدة للسيرفر أو الجهاز ولا ادعاء تحديث signer
OPERATIONS_IN_FLIGHT: NONE
BLOCKERS: B09-BLK-01؛ B02-BLK-01 وB02-BLK-02 محفوظان
SAFE_LOCAL_WORK_REMAINING: تنفيذ كل كود B09 موجود؛ المطلوب تشغيل الاختبارات المرفقة وإصلاح ما تكشفه، لا بدء B10 قبل بوابة B09
DO_NOT_REPEAT: لا تُعد كتابة اللقطة ولا frozen packets؛ لا تستخدم lifecycleVersion كسلطة؛ لا تخترع cash client/owner hashes؛ لا تنقل Python SQLite إلى PASS Room
NEXT_SESSION / NEXT_TASK: B09 / B09.01
NEXT_ACTION: تشغيل FinancialMaterializationContractV2Test وFinancialMaterializerV2InstrumentedTest وcompileDebugKotlin، ثم تقييم B09.01–B09.05 وG-B09 بالدليل
NEXT_ACTION_PRECONDITIONS: Gradle/Android dependencies cached or reachable + connected emulator/device؛ commands-and-results.md
RESUME_FROM: docs/sync-repair/evidence/B09/V01؛ هذه الشجرة؛ المطبق وربطه موجودان بالفعل
SESSION_FINAL_STATE: BLOCKED_VALIDATION (حالة جدول الجلسة BLOCKED)
DOCUMENTS_UPDATED: Backlog؛ Changelog؛ EVIDENCE_INDEX؛ SYNC_IMPLEMENTATION_MAP؛ B09 report/commands/parity/ownership/runtime map/hash/diff
```

### 9.12 سجل B09-V02 — إصلاح رفض writeId المشترك

```text
CHECKPOINT_ID: B09-V02
SESSION_ID / VISIT: B09 / 2
DATE / TIMEZONE: 2026-09-10 / UTC؛ لا ادعاء أوقات بداية/نهاية دقيقة غير مسجلة
INPUT_ARCHIVE: Verto-425-B09-WIP_NOT_RELEASE_READY(1).zip
INPUT_ARCHIVE_SHA256: 33c67ceab661921e0fb0e64e3f3025a195894fba36f503fe4ee0ccd0ee2f122b
INPUT_PRODUCT_SHA256: 65aacd59b1c71ae3b5f8353841c999ab536770dd13f818147bf3dd84632344c9
OUTPUT_PRODUCT_SHA256: 2aa1a4e16d9ea9d1d37fd9b33fc0bc730616a288634da78c7aa56fc3ad97bbc7
SOURCE_VALIDATION: 1851/1851 input manifest rows match؛ العقد المضمن والمستقل دون تغيير
IMPLEMENTED: unique effect fact identity؛ استثناء PAYMENT فقط من uniqueness على businessIdentity=writeId؛ لا دمج/تغيير fact/hash
NEW_TEST_METHODS: 3 JVM + 2 Room؛ الإجمالي 12 JVM و33 Room NOT_RUN
OBSERVED_REGRESSION: actual Kotlin validator rejected shared writeId before fix؛ after fix semantic 3 PASS مع codec stub
OTHER_ACTUAL_CHECKS: 347 mapping/Money PASS؛ 612 source/SQLite-model PASS؛ B06 schema/producers static PASS
NOT_RUN: actual Gradle/JVM JSON/Room/KSP/Hilt/app؛ PostgreSQL؛ T01–T50؛ جهازان
LIVE_OR_DATA_ACTIONS: NONE؛ لا وصول شبكة أو تعديل سيرفر أو بيانات مستخدم
BLOCKERS: B09-BLK-01 OPEN؛ B02-BLK-01/B02-BLK-02 دون تغيير
SESSION_FINAL_STATE: BLOCKED؛ G-B09 BLOCKED؛ B09.01–B09.05 BLOCKED
NEXT_SESSION / NEXT_TASK: B09 / B09.01
NEXT_ACTION: تشغيل 12 JVM و33 Room وcompileDebugKotlin والحزم المتأثرة في بيئة مجهزة؛ إصلاح الفشل ثم توثيق البوابة؛ لا B10 قبل النجاح
DO_NOT_REPEAT: لا ترجع إلى المصدر الأصلي؛ لا تعيد كتابة producer packets؛ لا تحول source/SQLite/stub smoke إلى Room PASS
EVIDENCE: docs/sync-repair/evidence/B09/V02/session-report.md؛ commands-and-results.md؛ tree/hash/patch
LOCAL_GATES: scope PASS؛ documentation FAIL تاريخي مثبت بمقارنة ZIP الأصل؛ integrated documentation exit126 لصلاحية سكربت سابقة؛ لا قبول جودة شامل
DELIVERY: WIP_NOT_RELEASE_READY ZIP؛ ليس APK أو Source-of-Truth admission
```

### 9.13 سجل B10-V01 — الاستلام الدائم والتطبيق المستقل

```text
CHECKPOINT_ID: B10-V01
INPUT_ARCHIVE: Verto-425-B09-V02-WIP_NOT_RELEASE_READY.zip
INPUT_ARCHIVE_SHA256: 5dd0bd9337b4a5cbaa970b2f8835cd082228174ce8228a4f515faa898cd0f059
SOURCE_VALIDATION: 1851/1851 baseline product files match؛ العقد المضمن والمستقل محفوظان
AUTHORIZATION: رسالة المستخدم نفذ b10؛ كتابة محلية رغم G-B09 المعلقة، لا إسقاط بوابة
IMPLEMENTED: scoped manifest receive/CAS؛ states/touched keys/dependencies؛ APPLIED covered prefix؛ generations/wake؛ budget1000/first1001؛ bytes2MiB/quota64+2/disk guard؛ schema99
ACTUAL_CHECKS: 15 SQLite methods على migration/DAO؛ 12 actual policy test methods مع assertion shim؛ B10 signatures؛ B09 612 model +347 mapping +3 semantic مع codec stub؛ B06 source gates
NOT_RUN: Gradle8.9/Kotlin2.1 JSON/JVM؛ 14 Inbox Room +34 financial Room؛ KSP/Hilt/export99/app compile؛ Android process kill؛ B08/B20 server/two-device؛ T01–T50
PRIOR_TESTS: التاريخ محفوظ؛ لا يرث هذا التغيير نتائج Room/Gradle القديمة؛ أعد suites الحماية والـfrozen stores/الجدولة
SESSION_FINAL_STATE: BLOCKED؛ لا مهمة DONE؛ G-B09 وG-B10 BLOCKED
LIVE_OR_DATA_ACTIONS: NONE؛ لا نشر SQL/APK/GitHub/Drive أو تعديل بيانات حية؛ قاعدة fixture محلية فقط
NEXT_SESSION/NEXT_TASK: B10/B10.01
NEXT_ACTION: تشغيل commands-and-results.md والتحقق من migration99/Room/kill والحدود وعقد B08؛ إصلاح الفشل وإثبات بوابتي B09/B10؛ لا بدء B11 تلقائيًا
DO_NOT_REPEAT: لا إعادة الأصل فوق الإصلاحات؛ لا APPLIED/ACK من receipt فقط؛ لا خلط نموذج SQLite/توقيع Kotlin مع قبول Room
EVIDENCE: docs/sync-repair/evidence/B10/V01؛ product-tree.json/change.patch/input-validation.json/session-report.md
DELIVERY: WIP_NOT_RELEASE_READY ZIP؛ لا APK ولا إعلان جاهزية إطلاق
```


### 9.14 سجل B11-V01 — حل تعارض صريح مع دليل دائم

```text
CHECKPOINT_ID: B11-V01
INPUT_ARCHIVE: Verto-425-B10-V01-WIP_NOT_RELEASE_READY(1).zip
INPUT_ARCHIVE_SHA256: db0eb5ae27697a72dd4ce2829128b292334e6380f2a403b3c53f1b76f8a6198f
SOURCE_VALIDATION: product tree input يطابق B10-V01 = 2c9379972d1d9db3f52d96f0a2dbe2325f77558e53bf3ef97265e1f3ee90eda0
AUTHORIZATION: رسالة المستخدم «نفذ التالي b11»؛ كتابة محلية رغم G-B10 المعلقة، لا إسقاط بوابة
IMPLEMENTED: conflict evidence/audit append-only؛ redacted diff UI؛ explicit accept-server/resend-local؛ SUPERSEDED_WITH_PROOF/pending-proof؛ replacement baseVersion exact؛ immutable→DOMAIN_CORRECTION_REQUIRED؛ matching echo/proof chain؛ newer-local preservation
SCHEMA: Room target100؛ migration99→100 توسع outbox states وتضيف supersedes/evidence/audit؛ fresh-open integrity guards؛ لا export100 مولد
ACTUAL_CHECKS: python tools/test_sync_b11_static.py PASS؛ python tools/test_sync_b11_sqlite.py PASS؛ py_compile PASS
NOT_RUN: Gradle8.9/Kotlin2.1 compile؛ KSP/Hilt/Room migration helper/fresh100؛ Compose UI runtime؛ Android/device؛ B10 integration؛ T30 actual receipt/echo
SESSION_FINAL_STATE: BLOCKED؛ لا مهمة DONE؛ G-B10/G-B11 BLOCKED
LIVE_OR_DATA_ACTIONS: NONE؛ لا SQL/APK/GitHub/Drive أو تعديل بيانات حية
NEXT_SESSION/NEXT_TASK: B11/B11.01
NEXT_ACTION: شغّل Gradle/KSP/Room/schema100 ثم سيناريوهات القرار/echo/permission/version-change/outcome-unknown/newer-local ordering؛ أغلق G-B10 ثم G-B11؛ لا B12
DO_NOT_REPEAT: لا auto server-wins/LWW/amount merge؛ لا ACK لنية superseded؛ لا تغيير bytes القديمة؛ لا اعتبار static/SQLite بديل Room/T30
EVIDENCE: docs/sync-repair/evidence/B11/V01؛ product-tree.json/change.patch/changed-files.json/session-report.md
DELIVERY: WIP_NOT_RELEASE_READY ZIP؛ لا APK ولا إعلان جاهزية إطلاق
```

### 9.15 سجل B12-V01 — مصروف mutable/versioned وأثر نقدي مجمد

```text
CHECKPOINT_ID: B12-V01
INPUT_ARCHIVE: Verto-425-B11-V01-WIP_NOT_RELEASE_READY.zip
INPUT_ARCHIVE_SHA256: 0ae6cdbfe810c222300874d3fa3b123a670517a1cefb7fbef37ccb14c4195d27
SOURCE_VALIDATION: legacy B11 hash المضمن 2af04ead...؛ expanded B12 scope baseline bf610527fe6590ec0804825884e8c4baaf301d52cc736bf7a29ed3ef282c8ec4
AUTHORIZATION: رسالة المستخدم «نفذ b12»؛ كتابة محلية رغم B09/B11 المعلقتين؛ لا إسقاط بوابة
IMPLEMENTED: same expenseId mutable/versioned history؛ before/after+hashes+actor+capturedBaseVersion؛ frozen expense intent؛ expense+cash batch/dependency؛ exact cash formula؛ mutable receive applier؛ append-only local history؛ B12 DTO/schema؛ server validator/authority/history مع fail-closed nonzero cash حتى B07
ACTUAL_CHECKS: B12 static 22/22 PASS؛ native SQLite append-only PASS؛ sync schema PASS defs=42؛ selected pure Kotlin contract compile PASS
NOT_RUN: Gradle8.9/Kotlin2.1/KSP/Hilt/Room؛ Android/device؛ T21/T27 actual app+Room؛ PostgreSQL migration/RPC؛ B07 atomic expense+cash batch؛ production/server deploy
SESSION_FINAL_STATE: BLOCKED؛ لا مهمة DONE؛ G-B12 BLOCKED؛ R08 OPEN
LIVE_OR_DATA_ACTIONS: NONE؛ لا SQL حي/APK/GitHub/Drive أو تعديل بيانات حية
NEXT_SESSION/NEXT_TASK: B12/B12.01
NEXT_ACTION: شغّل Gradle/Room ثم T21/T27؛ بعد B07 اختبر PostgreSQL atomic expense+cash وhistory/cash reconciliation؛ لا تغلق G-B12 قبل ذلك
DO_NOT_REPEAT: لا تحويل المصروف immutable؛ لا حساب delta في receiver/server مرتين؛ لا إعادة قراءة مصروف أحدث عند retry؛ لا اعتبار static/SQLite/pure Kotlin بديل Room/SQL
EVIDENCE: docs/sync-repair/evidence/B12/V01؛ product-tree.json/changed-files.json/change.patch/session-report.md/commands-and-results.md
DELIVERY: WIP_NOT_RELEASE_READY ZIP؛ لا APK ولا إعلان جاهزية إطلاق
```

### 9.2 قالب سجل نهاية الجلسة

انسخ هذا القالب وأكمله تحت سجل الزيارات عند التنفيذ؛ العبارات بين الأقواس حقول تعبئة وليست بيانات أو أوامر تم تشغيلها.

```text
CHECKPOINT_ID: [Bxx-Vnn]
SESSION_ID / VISIT: [الجلسة والزيارة]
STARTED_AT / ENDED_AT / TIMEZONE: [الأوقات الفعلية]
BRANCH / SOURCE_BASELINE: [المصدر والفرع]
START_PRODUCT_COMMIT: [commit البداية]
END_WORKING_PRODUCT_COMMIT / TREE_SHA256: [ما انتهت إليه الشجرة]
LAST_VERIFIED_PRODUCT_COMMIT: [آخر كود ثبتت بوابته]
WORKTREE_REMAINS: [ملفات غير ملتزمة + diff/hash، أو لا توجد]

COMPLETED_TASKS: [المعرفات ودليل كل منها]
PARTIAL_TASKS: [المعرف، الجزء المنجز، السطر/الدالة، الباقي تحديدًا]
CHANGED_FILES_AND_SYMBOLS: [المسارات والدوال/الربط وماذا تغير]
PRODUCT_IMPACT: [أثر تغيير هذه الزيارة دون ادعاء نتائج غير مثبتة]
TEST_RUNS: [Txx/G-Bxx؛ الأمر؛ البيئة؛ النتيجة؛ exit؛ evidence]
EVIDENCE_RECHECK_REQUIRED: [اختبارات قديمة تأثرت بالتغيير]
SERVER_ACTIONS: [TEST_ONLY/READ_ONLY/LIVE_AUTHORIZED/NONE + مرجع النتيجة]
DATA_ACTIONS: [نسخة اختبار/جهاز حي مفوض/لا شيء + قبل/بعد]
BACKUP / SIGNING / SCOPE STATUS: [الوضع المثبت]
OPERATIONS_IN_FLIGHT: [عملية/نشر/Action بدأ؛ هويته؛ كيف يفحص؛ أو لا توجد]
BLOCKERS: [المعرفات، السبب، ما يمنعه، وما يفتحها]
SAFE_LOCAL_WORK_REMAINING: [مهام مؤهلة دون خرق العائق]
DO_NOT_REPEAT: [نشر/ترحيل/إصلاح/التقاط نية حصل بالفعل مع دليله]
NEXT_SESSION / NEXT_TASK: [معرفان محددان]
NEXT_ACTION: [فعل واحد أو نطاق صغير + موضعه + اختبار قبوله]
NEXT_ACTION_PRECONDITIONS: [المدخل/الدليل/الاعتمادية اللازمة]
RESUME_FROM: [آخر حد معاملة/ملف/دالة آمن؛ لا تبدأ من الصفر]
SESSION_FINAL_STATE: [DONE/PARTIAL/BLOCKED]
DOCUMENTS_UPDATED: [Backlog/Changelog/Map/Tests/غيرها + مواضعها]
```

**صياغة NEXT_ACTION الجيدة:** معرّف مثل `B05.04` ثم الفعل: استكمال شرط ACK في DAO المحدد من خريطة التنفيذ، واختبار أن إيصال N لا ينظف N+1 على Room. إن كان الموضع لم يثبت بعد، تكون الخطوة إثباته من المصدر لا تسمية دالة متخيلة. صياغة «واصل الإصلاح/نفذ التالي/اختبر لاحقًا» غير كافية.

## 10. قائمة تحقق اتساق الملف قبل تسليم كل جلسة

- NEXT_TASK في لوحة الاستئناف تشير إلى مهمة موجودة وغير مغلقة أو خطوة فك عائق حقيقية، إلا الإغلاق الموثق وفق 3.6؛ وتطابق حالة الجلسة وعدّادات المهام/الاختبارات الصفوف الفعلية.
- لا جلسة DONE بمهام إلزامية ناقصة أو بوابة غير مجتازة؛ لا PASS بلا evidence/commit/بيئة، ولا CLOSED_VERIFIED لعيب باختبار مطلوب غير متحقق أو دليل متقادم.
- الاعتماديات لم تُكسر؛ مصدر الكود المثبت والتغييرات الجزئية محفوظان؛ لا legacy fallback أو تغيير تصميم مخفي؛ العقد المضمّن لم يتغير.
- سجل نهاية الزيارة وChangelog ومخرجات الجلسة محدثة؛ العمليات المعلقة والإجراءات التي لا تعاد مدونة؛ NEXT_ACTION محددة وقابلة للاستئناف.

### 10.1 تحقق بصمة العقد المضمّن

هذا فحص سلامة **للوثيقة فقط**، لا اختبار مزامنة ولا إثبات تنفيذ B01. يحتفظ الملف بـUTF-8 وLF، ولا يعاد تنسيق النص بين علامتي العقد.

```python
from pathlib import Path
import hashlib

path = Path("VERTO_SYNC_REPAIR_BACKLOG_AR.md")
raw = path.read_bytes()
begin = b"<!-- SOURCE_" + b"CONTRACT_BEGIN -->\n"
end = b"<!-- SOURCE_" + b"CONTRACT_END -->"
if raw.count(begin) != 1 or raw.count(end) != 1:
    raise SystemExit("Invalid contract boundary markers")
embedded = raw.split(begin, 1)[1].split(end, 1)[0]
expected = "34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0"
actual = hashlib.sha256(embedded).hexdigest()
if actual != expected:
    raise SystemExit(f"Contract changed: {actual}")
print("Embedded contract is unchanged")
```

## 11. أمر بداية كل جلسة في Codex

> اقرأ `VERTO_SYNC_REPAIR_BACKLOG_AR.md` من فرع التنفيذ الحالي. تحقق من المصدر وآخر checkpoint، ثم نفذ جلسة واحدة فقط يحددها NEXT_ACTION وفق الاعتماديات وأقسام العقد المضمّن. استأنف المهمة الجزئية ولا تعِد العمل المكتمل أو تفك الأرشيف فوق الإصلاحات. لا تغيّر التصميم أو تتجاوز عائق سلامة. اختبر ما نفذت بأدلته، ثم حدّث داخل الملف حالات المهام والاختبارات والعوائق وسجل الجلسة وNEXT_ACTION وChangelog قبل التسليم، حتى عند التوقف الجزئي. ميّز الكود المنفذ عن النتائج المثبتة، ولا تدّعِ إصلاح جهاز المستخدم دون بياناته ودليله.

## 12. مصدر التصميم وبصمته

المصدر المباشر للتحويل هو `VERTO_SYNC_REPAIR_EXECUTION_CONTRACT_AR.md` المرفق، مع أقسامه 1–23 وملحقاته A–C كاملة. النص التالي محفوظ حرفيًا وبصمته:

```text
SHA-256 = 34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0
```

المصادر S1–S6 وبصماتها وتعريف حدود أدلتها موجودة في C§1 وCملحق C. إعداد هذا الـBacklog لا يعيد تدقيق الكود أو اتصال السيرفر، ولا يضيف شهادة نجاح إلى تلك المصادر. لا تعتمد نتائج حديثة على تقارير قديمة دون فحصها أثناء التنفيذ.

<a id="contract-reference"></a>
# المرجع الملزم — نص عقد التنفيذ الأصلي كاملًا

**لا تعدّل النص بين العلامتين ضمن تحديث الحالة اليومية.** أي تغيير تصميم معتمد يسجل بملحق مستقل وبصمته وربطه في سجل القرارات، دون استبدال تاريخ العقد الأصلي أو إخفاء الانحراف.

<!-- SOURCE_CONTRACT_BEGIN -->
# عقد تنفيذ إصلاح مزامنة Verto — تصميم ملزم ومعايير قبول

**معرّف العقد:** `VERTO-SYNC-REPAIR-01`  
**إصدار العقد:** `1.0`  
**التاريخ:** 10 سبتمبر 2026  
**مصدر الكود:** `Verto-425.zip` المرفق، وليس أحدث GitHub بصورة مفترضة.  
**حالة الوثيقة:** تصميم مطلوب تنفيذه؛ ليست تقرير تنفيذ أو شهادة نجاح. لم تُعدَّل ملفات التطبيق أو السيرفر لإعدادها.

> الهدف: حفظ البيانات المحلية أولًا، معالجة حاجز M03 بأدلة، وإكمال نقل بيانات الأعمال وتطبيقها في V2. لا يُقبل جعل المؤشر أخضر، أو إنقاص العداد، أو نجاح Worker بديلًا عن وصول البيانات الصحيحة.

## 1. سلطة العقد وحدود المعلومات

### 1.1 المواد التي بُني عليها العقد

| المرجع | المصدر | ما يثبته بحسب محتواه |
|---|---|---|
| S1 | `VERTO_SYNC_DIAGNOSTIC_REPORT_2026-09-09.md` | محاولة جهاز انتهت بالاستثناء `M03_LOCAL_MIGRATION_REVIEW_REQUIRED:54`، بعد تحميل جلسة Supabase بنجاح. |
| S2 | `VERTO_SYNC_DIAGNOSTIC_REPORT_2026-09-09 (1).md` | الدليل نفسه، مع تفصيل مصادر العناصر الـ54. |
| S3 | `Verto-425-Sync-Audit-AR.md` | ست مجموعات نتائج، منها نقص بنود الفواتير، عدم تطبيق الوارد المالي، تنظيف تعديل أحدث بتأكيد قديم، الحجوزات والمرفقات وحدود الصفحات. |
| S4 | `Verto-425-sync-audit-ar.md` | ثماني مجموعات نتائج، ومنها اختلاف DTO، غياب baseVersion، حماية الصندوق الخطأ، تغيّر الحمولة، وحبس السحب بسبب التعارض. |
| S5 | `Verto-425-sync-audit.md` | ست مجموعات نتائج، ومنها رفض تعديل المصروف في الاستقبال وتصنيف الإلغاء كخطأ شبكة. |
| S6 | `Verto-425.zip` | الكود المستخدم لتحديد مواقع التعديل والحقول والخريطة الحالية، وبصمته أدناه. |

```text
SHA-256(Verto-425.zip)
cb0a1956952376d75bfeb2d3ec71b0e06700024d5b95cd44f6b09db96855e1a0
```

ترقيم F01 وغيره **محلي لكل تقرير**؛ لا يجمع المنفذ أعداد العيوب ولا يعامل الرموز المتشابهة كأنها المشكلة نفسها. الترقيم الموحد الملزم هو R01–R13 في هذا العقد.

التقارير S3–S5 تصف فحص كود واختبارات معزولة، لا اختبار جهازين أو نجاح Android كامل. وهي تصرّح بعدم قراءة تعريفات RPC الحية. لا تُرفع درجة هذا الإثبات داخل تقرير التنفيذ.

### 1.2 التمييز بين الدليل والتصميم

الوقائع المنسوبة إلى المصادر مقيّدة بما أثبتته. أسماء المكونات والجداول وواجهات RPC الجديدة، وسياسات الحالات والأحجام والتعارضات، الواردة بعد قسم النطاق هي **قرارات تصميم لهذا الإصلاح** وليست ادعاءً بأنها موجودة أو تعمل الآن.

الوثائق لا تحتوي قاعدة Room التي أنتجت العناصر الـ54، ولا الحمولة الفعلية للعمليات الـ26، ولا جميع تعريفات السيرفر المنشور، ولا جميع الطلبات التي ربما أُرسلت وضاع ردها. لذلك يحدد العقد فروعًا آمنة صريحة عند غياب الدليل. لا يجوز ملء هذا النقص بتخمين بيانات، ولا إعلان الإغلاق مع بقاء فرع غير محسوم.

### 1.3 ما لا يفوضه العقد

لا يعاد تصميم المصادقة أو المنتج، ولا تستبدل Room، ولا يضاف ناقل Legacy موازٍ، ولا تعدل قواعد التسعير والضرائب والعمولات أو أسماء الكيانات التجارية خارج ما يلزم لتصحيح النقل. أسماء الحقول الداخلية التنظيمية يمكن مواءمتها مع أسلوب المشروع، لكن تغيير قرار معماري أو مالي أو سياسة أمان يحتاج ملحق عقد موثق، وليس اجتهادًا صامتًا.

## 2. نطاق الإصلاح والتتبّع

| المعرّف | المطلوب إغلاقه | مرجع المصدر | شرط الإغلاق المختصر |
|---|---|---|---|
| R01 | M03: جرد الأدلة، إعادة إنشاء النيات المفقودة، تصنيف Optimal، واستئناف آمن | S1، S2 | كل مصدر محفوظ ومربوط بإثبات؛ لا تجاوز للحاجز بعدٍّ مصطنع. |
| R02 | لقطة مالية كاملة، تطبيق فعلي للفواتير والدفعات، وترميم ما سبق تجاوزه | S3/F01,F02؛ S4/F01؛ S5/F01 | تطابق الرأس والبنود والدفعات وتوابعها بين قاعدتين وBootstrap. |
| R03 | تنظيف مشروط بالتأكيد المطابق للحالة المحلية | S3/F03 | ACK للإصدار N لا ينظف N+1 أو تعديل بند فقط. |
| R04 | توحيد حمولة الحركة والتكلفة والرصيد | S4/F02 | round-trip حقيقي بالأسماء والأنواع القانونية دون قيم بديلة مخمّنة. |
| R05 | مصدر دائم للنسخ وربط baseVersion | S4/F03 | تعديل عادي، ثم تعديل بعد Bootstrap، دون تعارض نسخة زائف. |
| R06 | حماية الاستعادة بحسب المالك الحقيقي لكل نية | S4/F04؛ S5/F02 | بقاء المحتوى المحلي المعلق، لا هوية الطابور وحدها. |
| R07 | ثبات حمولة العملية عبر المحاولات | S4/F05؛ S5/F03 | قبول ثم ضياع رد ثم تعديل: يعاد الطلب الأول نفسه، والثاني مستقل. |
| R08 | سياسة واحدة لتعديل المصروف | S5/F04 | تعديل الملاحظة والمبلغ يصل دون رفض immutable أو ازدواج النقد. |
| R09 | الحجوزات والجدولة والاكتمال الصحيح | S3/F04؛ S5/F05 | موعد متابعة للحجز؛ لا نجاح نهائي مع عمل غير محسوم. |
| R10 | دورة نقل مستندات الشحن | S3/F05؛ S4/F06 | رفع متحقق، تأكيد بيانات وصفية، ثم إغلاق النية فقط. |
| R11 | فصل الاستلام الدائم عن التطبيق وحصر التعارض في تبعياته | S4/F07 | تعارض مجموعة لا يمنع استلام وتطبيق مجموعة مستقلة. |
| R12 | تقدم الصفحات الكبيرة دون كسر المعاملة | S3/F06؛ S4/F08 | 999/1000/1001 تغيير تتقدم بصورة ذرية. |
| R13 | الإلغاء، وتثبيت عقد السيرفر المنشور وقابلية إعادة نشره | S5/F06؛ أقسام السيرفر في S3–S5 | الإلغاء يبقى إلغاءً؛ تعريفات منشورة مثبتة واختبارات عقد على بيئة اختبار. |

## 3. ثوابت السلامة غير القابلة للتجاوز

1. لا `clearAllTables`، ولا `fallbackToDestructiveMigration`، ولا حذف للتطبيق أو بياناته أو مؤسسة المستخدم، ولا تصفير للصناديق أو المؤشرات لعلاج العطل.
2. لا تحويل جماعي إلى `CLEAN` أو `ACKNOWLEDGED` أو `SYNCED` أو `APPLIED`. لكل انتقال دليل محدد في هذا العقد.
3. لا حذف أو تعليق شرط M03 ثم اعتبار المشكلة محلولة. يستبدل الاستثناء العام بنتيجة مفسرة، ويبقى منع الترقية المدمرة عند نقص الدليل.
4. لا اعتبار `LOCAL_ONLY` نجاح نقل، ولا اعتبار `REJECTED` أو `BLOCKED` بيانات يمكن التخلص منها.
5. لا إعادة استخدام `mutationId` لحمولة أو baseVersion مختلفة بعد تجميد الطلب. لا توليد معرّفات تجارية جديدة للفواتير والدفعات الموجودة.
6. جميع مفاتيح النسخ والحجوزات والقراءة والتأكيد وحماية الاستعادة مقيدة بالمؤسسة، وبنطاق الرؤية/المستخدم حيث يلزم. لا يتبنى المستخدم الحالي صفًا بلا مؤسسة لمجرد أنه ظاهر محليًا.
7. حفظ بيانات الأعمال ونياتها والروابط اللازمة داخل معاملة Room واحدة. التطبيق البعيد لا يستدعي واجهات الإنتاج المحلي ولا يولد نيات أو آثارًا اقتصادية جديدة.
8. الفاتورة والدفعة والحركة النقدية والمخزنية ليست أربع مناسبات لإعادة حساب الأثر نفسه. يثبت مالك كل حقيقة ومفتاح عدم تكرارها قبل التنفيذ.
9. لا إعادة بناء تكلفة تاريخية من سعر المخزون الحالي، ولا استنتاج دفعات أو عملة أو سعر صرف من الإجمالي. النقص يبقى نقصًا مسمى.
10. لا تشغيل اختبارات تولّد فواتير أو تحرك أرصدة على مؤسسة المستخدم الحقيقية. بيانات الاختبار في مشروع/مؤسسة اختبار معزولة.

## 4. بوابة البدء وحفظ إمكانية الرجوع

### 4.1 تثبيت المصدر

يفك المنفذ S6 ويتحقق من SHA-256. يسجل Commit جديدًا مستخرجًا منه أو بصمة شجرة الملفات. يقرأ تعليمات المشروع إن وجدت؛ لا يدّعي وجود AGENTS/Backlog لم يعثر عليهما. لا يستبدل المصدر تلقائيًا بأحدث GitHub. إذا كان مصدر التنفيذ مختلفًا، ينتج `SOURCE_DIFF.md` ويطبق التعديلات على المصدر المحدد بالعقد؛ اختلاف غير مفسر يمنع اعتماد نتائج هذه النسخة.

### 4.2 نسخة بيانات قابلة للتحقق

قبل ترحيل بيانات الجهاز: تؤخذ نسخة SQLite متسقة بآلية النسخ الاحتياطي أو بعد إيقاف الكتابة وإغلاق القاعدة بصورة سليمة، لا بنسخ ملف `.db` حيّ مع تجاهل WAL. تشمل جداول الأعمال وجميع الصناديق والحجوزات وM03 والمؤشرات وملفات المرفقات المحلية المشار إليها. تحسب بصمات النسخة وبيان محتوياتها وتختبر قراءتها على نسخة منفصلة. لا تشارك توكنات الجلسة أو بيانات المستخدم في تقارير عامة.

**فشل النسخ/القراءة = `BLOCKED_BACKUP_UNVERIFIED`.** لا يبدأ ترحيل إنتاجي، ولا تعالج المشكلة بمسح بيانات. أثناء العمل تحفظ السجلات الأصلية؛ لا تعتمد الأمان على وجود ZIP للكود.

### 4.3 مطابقة السيرفر — قراءة فقط أولًا

يستخرج المنفذ من مشروع Verto الفعلي: تعريفات RPC المستخدمة للإرسال والسحب والنطاق وBootstrap، الجداول والأعمدة والقيود والفهارس المرتبطة، المشغلات التي تنشئ آثارًا مالية/مخزنية/عمولات، RLS والمنح وملكية دوال SECURITY DEFINER، إعدادات النطاق والتفعيل، وسجل migrations. يسجل المشروع والوقت وبصمة كل تعريف.

وجود اسم migration أو حالة `ACTIVE_HEALTHY` ليس بديلًا. إذا تعذرت القراءة يسجل `BLOCKED_LIVE_CONTRACT_UNVERIFIED`؛ يسمح بكتابة كود واختبارات محلية، لكن يمنع نشر SQL أو تشغيل إصلاح بيانات حي بناءً على SQL قديم. ينقل التعريفات اللازمة إلى migrations قابلة لإعادة بناء بيئة اختبار نظيفة؛ لا يعدّل migration تاريخية مطبقة.

### 4.4 سياج النقل أثناء التحويل

يوقف تشغيل النقل القديم لهذا الجهاز داخل المنسق، وينتظر إنهاء/انتهاء حجوزه مع الحفاظ عليها. على السيرفر، ترفض عمليات كتابة بروتوكول قديم للمؤسسة المشمولة بالتحويل بعد نقطة السياج المتفق عليها، مع استمرار قراءة إيصالاتها. الهدف منع وصول طلب قديم متأخر بعد تقرير أنه غير موجود. لا تمنع القراءة المصرح بها ولا تمسح الإيصالات.

يستخدم قفل محلي منطقي `(organizationId, syncPrincipalId, scopeEpoch)`؛ أي رد يعود بعد تغيير المؤسسة/المستخدم/epoch لا يطبق على القاعدة الجديدة. لكل نتيجة متأخرة مسار إعادة استعلام لاحق في نطاقها الأصلي، لا إقرار على نطاق آخر.

## 5. المعمارية المعتمدة

تبقى Room مصدر العرض، وتبقى صناديق الإنتاج القائمة مالكة لعملياتها، ويبقى V2 المنسق الوحيد. لا تنسخ `financial_outbox` أو `inventory_stock_outbox` إلى `sync_outbox` لخلق مالكين للحدث نفسه.

```text
واجهة/حالة استخدام
  → معاملة Room: بيانات أعمال + لقطة نية + روابط التبعيات + بيان المجموعة
  → طلب مزامنة دائم
  → تجهيز الطلب مرة واحدة وتجميده
  → حجز الصندوق المالك
  → RPC واحد للمجموعة التجارية
  → إيصالات مثبتة + تنظيف مشروط

RPC سحب/Bootstrap
  → تحقق نطاق وعقد ومجموعات
  → تخزين المجموعة كاملة في sync_inbox + مؤشر الاستلام
  → مطبّق مجال V2
  → معاملة Room: جداول أعمال + نسخ + APPLIED + نقطة تطبيق
  → عرض الحالة الفعلية
```

المكونات الجديدة المطلوبة: `SyncOwnershipRegistry`، `FrozenMutationStore`، `SyncPendingProtection`، `FinancialSnapshotFactoryV2`، `FinancialMaterializerV2`، `SyncBatchCoordinatorV2`، `DurableInboxApplyCoordinator`، `M03RepairPlannerV2`، `AttachmentTransferCoordinatorV2`. هذه وظائف تصميمية؛ تربط عبر Hilt بالفعل ويمنع وجود تنفيذ فارغ في الإنتاج.

## 6. الجداول التنظيمية وحالاتها

لا يكون أي جدول تنظيمي بديلًا خفيًا عن صندوق المصدر. تُضاف migrations Room صريحة مع اختبار ترقية قاعدة قديمة غير فارغة.

### 6.1 `sync_entity_version`

المفتاح: `(organization_id, scope_id, version_family, aggregate_id)`.

الحقول الملزمة: `applied_server_version`، `observed_server_version`، `last_applied_revision`، `applied_content_hash`، `tombstone`، `updated_at`. القيمتان observed/applied منفصلتان: وصول نسخة إلى Inbox لا يجعلها أساس الحالة المعروضة. تزيد observed عند الاستلام؛ تزيد applied فقط بعد تطبيق الأعمال أو إيصال مطابق يثبت الحالة المحلية. لا يستنتج التسلسل من الساعة.

`version_family=FINANCIAL_INVOICE` للفواتير ودفعاتها المشتركة في مسار الفاتورة؛ معرفه invoiceId. الكيانات القابلة للتحديث الأخرى تستخدم نوعها. الأحداث غير القابلة للتعديل تستخدم هوية حقيقة + بصمة، ولا يعامل رقم lifecycle كتسلسل خادم.

### 6.2 `sync_local_generation`

المفتاح: `(organization_id, aggregate_type, aggregate_id)`؛ الحقول: `generation` و`content_hash`.

أي تعديل محلي دلالي يزيد generation داخل معاملة الكتابة، بما فيه تعديل بند دون تغيير الإجمالي. حالة الاستلام البعيد لا تزيده كتعديل محلي. الجيل لا يساوي `lifecycleVersion`؛ يُستخدم فقط لإثبات أن الحالة المحلية لم تتغير منذ التقاطها.

### 6.3 `sync_mutation_packet`

المفتاح: `(organization_id, mutation_id)`؛ قيد فريد على `(organization_id, source_owner, source_id)` لكل نية إصدار محددة.

الحقول: `source_owner`، `source_id`، `business_identity`، `intent_json`، `intent_hash`، `captured_generation`، `captured_content_hash`، `version_family`، `initial_base_version`، `predecessor_mutation_id`، `batch_id`، `wire_json` القابل لأن يكون null قبل التجهيز، `wire_sha256`، `prepared_at`، `first_dispatch_at`.

`intent_json` لقطة كاملة ثابتة منذ معاملة الحفظ. `wire_json` نص الطلب النهائي بعد حسم التبعيات/baseVersion، ويثبت مرة واحدة قبل أول اتصال. لا يحمل الجدول طابور إعادة محاولات مستقلًا؛ الحالة والحجز في مالك المصدر. تتغير حقول المراقبة فقط بعد التجميد؛ لا يتغير intent أو wire.

### 6.4 `sync_pending_reference`

المفتاح: `(organization_id, source_owner, source_id, protected_type, protected_id)`؛ الحقول: `captured_generation`، `captured_content_hash`، `dependency_kind`.

يسجل كل سجل/مجموعة يمكن أن يستبدلها الوارد بينما هناك نية تعتمد عليها: الفاتورة وبنودها، الدفعة وأصلها عند العكس، جرد الصندوق وتفاصيل فئاته، والمجموعة الأم للمرفق. نشاط المرجع مشتق من حالة مالك المصدر، لا Boolean مستقل قابل للتقادم. يضاف أو ينهى مع انتقال الصندوق في المعاملة نفسها.

### 6.5 `sync_write_batch` و`sync_write_batch_member`

بيان مجموعة الكتابة المحلية؛ لا ينسخ الحمولة ولا يملك إقرارًا بديلًا. المفتاح batchId داخل المؤسسة؛ الأعضاء مراجع إلى الصناديق الأصلية، بترتيب `member_order` فريد، و`member_count` وبصمة manifest. ينشأ البيان ويُختم في معاملة الكتابة نفسها. عضو تابع لمجموعة مختومة لا يرسل منفردًا. الإقرار للمجموعة يؤكد جميع الأعضاء أو لا يؤكد أيًا منهم.

### 6.6 الوارد والمؤشر

يُعاد استخدام `sync_inbox` الموجود، بما يحتويه من الحمولة وهوية المجموعة وترتيبها، بدل صندوق استقبال ثالث. تضاف حالة المجموعة في `sync_inbox_group` بمفتاح `(scope_id, transaction_id)` مع عددها وبصمتها وسبب الانتظار وقائمة التبعيات.

حالات التطبيق: `RECEIVED → READY → APPLIED`؛ أو `WAITING_LOCAL` أو `WAITING_DEPENDENCY` أو `REQUIRES_REVIEW`. الانتقال إلى APPLIED مقصور على نجاح مطبّق الأعمال. لا يساوي حفظ FinancialInbox هذا الانتقال.

يفصل في `sync_cursor` بين `received_cursor_token/received_high_watermark` و`applied_checkpoint`. رمز الاستلام يعاد كما أصدره السيرفر؛ لا يصنع العميل رموزًا من أرقام revision. نقطة التطبيق أعلى نهاية مجموعة استلمت وطبقت وجميع سابقاتها اللازمة في تغطية النطاق مطبقة، وليست `MAX(revision)` عمياء.

### 6.7 سجل ترحيل M03 الجديد

ينشأ `sync_migration_evidence_v2` بمفتاح `(organization_id, source_kind, source_id, source_content_hash, repair_version)`؛ ويحفظ raw type، نوع العملية، حالة المصدر، مرجع السجل القديم، التصنيف الجديد، نوع الإثبات، target mutation/batch، receipt/hash، السبب والأوقات.

لا يعاد كتابة هوية سجل v1 لتغيير `OPTIMAL_UNKNOWN` إلى نوع معروف؛ يبقى التاريخ القديم ويضاف إثبات v2. مصدر المجاميع الحالية هو آخر جرد متسق v2، لا جمع كل السجلات التاريخية. لا يُسمح لاختلاف التصنيف المشروع أن يولّد `M03_SOURCE_IDENTITY_CONFLICT` بسبب تغيير هوية السجل القديم.

### 6.8 حالة المصدر المعلقة

تعتبر `PENDING/RETRY/LEASED/SYNCING/FAILED/BLOCKED/REQUIRES_REVIEW/REJECTED` غير محسومة ما لم يثبت خلاف ذلك بنهاية موثقة. يضاف عند الحاجة `SUPERSEDED_WITH_PROOF` فقط مع رابط لإيصال بديل حقق الغرض نفسه، و`LOCAL_RETAINED` كسجل حفظ محلي لا كنجاح نقل. أسماء enum المحلية المختلفة تترجم عبر سجل الملكية؛ الحالة غير المعروفة لا تُفسر MIGRATED تلقائيًا.


### 6.9 أنواع التخزين والقيود المشتركة

كل الحقول المذكورة في القسم 6 إلزامية وغير nullable إلا الحقول التي لم يقع حدثها بعد: wire قبل التجهيز، predecessor عند عدم وجود سابقة، أزمنة التجهيز/الإرسال/الإقرار قبل وقوعها، نسخة الخادم عند عدم ثبوتها، ومراجع الخطأ/الحل الاختيارية. لا يمثل الصفر نسخة مجهولة؛ NULL الموثق هو تمثيل الجهل، و0 حالة إنشاء ثبت غيابها.

المعرفات والأنواع والحالات وJSON هي TEXT في Room؛ org UUID صحيح نصيًا في التطبيق ويقابل نوع الخادم المثبت؛ المفاتيح التجارية المركبة تبقى TEXT ولا cast لها إلى UUID. hashes هي SHA-256 lowercase hex بطول 64. الأجيال/التسلسلات/النسخ/الأوقات وعدد البايتات Long/SQLite INTEGER ضمن المدى الموقّع؛ الأعداد غير سالبة، والتسلسل موجب بعد أول حدث. الزمن Epoch milliseconds UTC. Boolean يخزن بقيد 0/1. memberOrder وtransactionOrder من **0 حتى size-1** مثل validator المصدر؛ لا مزج لترقيم يبدأ من 1 في عقد النقل.

الفهارس الإلزامية بالإضافة إلى المفاتيح المذكورة: lookup packets بالمالك/sourceId؛ pending references حسب `(org,protected_type,protected_id)`؛ inbox groups حسب `(scope,state,first_revision)` والتبعيات؛ migration evidence حسب `(org,disposition,source_kind)`؛ إصدارات المصروف حسب `(org,expenseId,serverVersion)` الفريد. علاقات metadata الجديدة لا تستخدم ON DELETE CASCADE لمسح صندوق مصدر أو دليل غير محسوم. لا ينفذ تنظيف عام لإيصالات/packets/snapshot blobs في إصدار الإصلاح قبل اعتماد reconciliation وتصدير دليله؛ مراقبة الموارد لا تبرر حذف pending.

المرجع polymorphic إلى مالك صندوق يحقق داخل معاملة adapter المالك ويغطيه اختبار orphan detection؛ لا يوضع FK شكلي إلى sync_outbox لكل الأنواع بينما مالكها جدول آخر.

## 7. ملكية الصناديق والتبعيات

| نوع البيانات | سلطة النية | قاعدة الحماية والتنفيذ |
|---|---|---|
| INVOICE وPAYMENT | `financial_outbox` | PAYMENT يحفظ paymentId في المحتوى؛ aggregateId الحالي لمساره invoiceId. حماية الدفع تشمل الفاتورة، والعكس يشمل الدفعة الأصلية. |
| PARTY_ROLE | `party_sync_outbox` لمسار ROLE | المفتاح الدلالي `(org,partyId,role)`، لا role وحده. |
| INVENTORY_MOVEMENT | `inventory_stock_outbox` | حقيقة بمفتاح الحركة وidempotencyKey؛ لا ينشئها مطبّق الفاتورة مرة أخرى. |
| INVENTORY_COST_REVISION | `inventory_cost_outbox` | حقيقة مستقلة ذات كلفة تاريخية وبصمة. |
| CLIENT_CREDIT، COST_ALLOCATION، EXPENSE، CASH_MOVEMENT، CASH_RECONCILIATION، GOODS_RECEIPT، PURCHASE_MATCH، PURCHASE_PAYMENT_OVERRIDE | `sync_outbox` في المسار العام owner310 المعني | لا يحميها فحص financial_outbox وحده. تُلتقط الحمولات التي كانت تُبنى في prepare داخل معاملة المنتج. |
| بقية CRUD العام، بما فيه قوالب الأسعار | المالك الفعلي المسجل، وغالبه `sync_outbox` | يحتفظ بالـDTO الدلالي القائم إذا لم يعدله هذا العقد؛ يطبق عليه تجميد الطلب والنسخ والحماية. |
| أنواع Optimal المعروفة | `optimal_outbox` مع الجسر المتخصص المناسب | لا تُحوّل نسخ التكامل إلى دفعات/فواتير مالية مكررة في الصندوق العام. |
| مستندات الشحن | `sync_attachment_transfer`، مع ربط أمر البيانات الوصفية | لا ACK للبيانات الوصفية باعتبار الملف متاحًا قبل تأكيد تخزينه. |
| NOTIFICATION للقراءة فقط | السيرفر | لا يخلق ترحيل M03 عملية رفع لمجرد وجود نسخة محلية. |

تكمل `SyncOwnershipRegistry` تغطية الأنواع الـ35 المسجلة في المصدر، إضافة إلى الصناديق المتخصصة خارج ذلك السجل. لكل نوع: producer فعلي، owner، target id، keys protected، mutability، serializer، applier، snapshot handler، terminal predicate. لا تسند الأنواع المتبقية إلى صندوق افتراضي بالاسم. إن لم يوجد منتج للنوع، يسجل `SERVER_ONLY` مع دليل المستدعين؛ وإن تعذر الحسم، `BLOCKED_OWNER_UNPROVEN` لذلك النوع، لا فتح حماية عامة.

هذه خطوة استخراج عقد قائم، لا تفويض لاختيار معمارية جديدة. اختبار آلي يقارن الكتابات الفعلية بالسجل ويمنع أي مصدر إنتاج غير مسجل. تستخدم **الدالة نفسها** للحماية في Pull وBootstrap وM03 وحساب الحالة والخروج؛ لا تبقى خرائط متناقضة في كل مسار.

## 8. خوارزمية إصلاح M03 — R01 وR03

### 8.1 عينة التشخيص ليست ثابت إعداد

| المصدر في S2 | العدد المسجل | القرار الملزم |
|---|---:|---|
| DIRTY_INVOICE | 1 | إثبات/التقاط مجموعة الفاتورة الكاملة. |
| DIRTY_INVOICE_ITEM | 1 | إلحاق البند بمجموعة فاتورته؛ لا إنشاء عملية فاتورة أخرى لمجرد تكرار المصدر. |
| DIRTY_PARTY_ROLE | 9 | إثبات هوية المؤسسة والدور، ثم نية مالك الأدوار. |
| DIRTY_PAYMENT | 17 | إثبات الدفع وأصله وتبعياته، ثم نية مالية محفوظة. |
| OPTIMAL_OUTBOX / OPTIMAL_UNKNOWN / LOCAL_ONLY | 26 | جرد raw type وoperation والحمولة؛ الحالة لا تثبت نوع العملية أو قابلية إرسالها. |
| الإجمالي التاريخي | 54 | يُعاد احتسابه من قاعدة التنفيذ؛ يمنع hard-code العدد. |

المصدر S6 يحتوي بالفعل `LegacySyncV2IntentRepairCoordinator`، كما أن `optimalMigrationAggregateType` يتعرف على INVOICE وCONVERSATION بالإضافة إلى الأنواع الأساسية. لذلك لا يكون الإصلاح إضافة مكررّة لهاتين الحالتين مع ادعاء أن حادثة الجهاز انتهت. لا تثبت المصادر أن APK التشخيص هو بايتات S6 نفسها.

### 8.2 عملية الجرد

تنفذ قراءة متسقة داخل معاملة محلية تشمل بيانات المؤسسة وصناديقها. لكل مصدر تحفظ في السجل الخاص: هوية المصدر، نوعه الخام، نوع العملية، payloadVersion، الهوية الدلالية، حالة المالك، عدد المحاولات والحجز، بصمة محتوى العمل، والصناديق/الإيصالات المطابقة. لا يكتفى بعدّ الجدول.

في التقارير القابلة للمشاركة تستخدم معرّفات مستعارة ثابتة وبصمات؛ الحمولة الكاملة تبقى محليًا في سجل إصلاح محمي. لا تسجل JWT أو بيانات الدفع الحساسة في Logcat.

### 8.3 قرار واحد من هذه القرارات لكل مصدر

| القرار | الدليل المطلوب | النتيجة |
|---|---|---|
| `COVERED_BY_PENDING_INTENT` | نية دائمة تخص المحتوى الحالي ومالكًا قابلًا للتنفيذ؛ تشمل تفاصيل البنود لا عددها | ربط المصدر بالنية، مع إبقائه غير مؤكد وعدم تنظيفه. |
| `COVERED_BY_RECEIPT` | إيصال موثوق يطابق المؤسسة والهوية والنسخة وبصمة الحالة/الحقيقة المقبولة كاملة | تنظيف مشروط وفق 8.7؛ لا تكفي حالة ACK محلية قديمة. |
| `CREATE_REPAIR_INTENT` | سجل أعمال كامل ومثبت المؤسسة، ولا يوجد إثبات مطابق أو خلاف غير محسوم | لقطة جديدة محفوظة وربط ذري؛ لا نقل شبكي داخل معاملة الجرد. |
| `LOCAL_RETAINED` | نوع تكامل معروف وسياسة LOCAL_ONLY مثبتة، دون وعد تسليم بعيد | حفظ محلي موثق، لا ACK ولا إرسال قسري. |
| `REQUIRES_REVIEW` | نقص هوية/مؤسسة/حقول، تعارض تاريخ، نوع مجهول، أو عدم القدرة على إثبات طلب سابق | حفظ المصدر، السبب والتبعية؛ لا إخفاء ولا تغيير دلالي. |

وجود تاريخ للفاتورة ليس دليلًا على تغطية نسختها الحالية، ووجود حالة نشطة على الكيان ليس دليل تطابق محتوى. لا يترك المنفذ فرع `history.isNotEmpty()` دون قرار للحالة الحالية.

### 8.4 الفاتورة والبند

يجمع الجرد DIRTY_INVOICE وDIRTY_INVOICE_ITEM حسب `(organizationId, invoiceId)`. يلتقط header وكل البنود وتوابع الفاتورة الموضحة في القسم 10 من القراءة نفسها. يسجل لكل بند معرفه وجيله وبصمته، ولو كان إجمالي الفاتورة لم يتغير.

تستخدم عملية الإصلاح هوية مستقلة حتمية مشتقة من:

```text
repairIdentityInput = UTF8(JSON-array[
  "VERTO-SYNC-REPAIR-01", organizationId, sourceOwner,
  businessAggregateId, capturedGeneration, fullBusinessContentSha256
])
repairMutationId = UUID-v5(namespace ثابت للعقد، repairIdentityInput)
namespace = 7d573f16-78f8-5c74-a1b5-42e74b972469
```

توحّد طريقة UUID-v5 ومدخلاتها باختبار متجه ثابت بين مكونات التطبيق، ولا تستبدل بـ`hashCode` أو الساعة. لا تعاد تسمية invoiceId أو paymentId. الهوية القديمة `m03-repair-invoice:<id>` تحفظ كمرجع تاريخي ولا تُستخدم كإثبات لكل نسخة مستقبلية.

عملية إصلاح السجل القائم اسمها الدلالي `RECONCILE_EXISTING_FINANCIAL_STATE`، وليست بيعًا جديدًا أو `INVOICE_CREATED` مزيفًا. يوثق السيرفر هل أصلح projection ناقصة أو أثبت تطابقًا أو أضاف حقيقة تاريخية ناقصة بهويتها الأصلية. لا يعيد احتساب المخزون/النقد/العمولة من رأس الفاتورة.

### 8.5 الدفعات والأدوار

**الدفعات:** لكل paymentId تثبت الفاتورة، المبلغ بوحداته الصغيرة، العملة وحالة المعرفة القديمة، سعر الصرف المسجل، allocation وFX المرتبطين، المصدر وهوية الكتابة، وreversedPaymentId. العكس ينتظر وجود الأصل؛ لا يُنشأ مبلغ موجب بدل مبلغ العكس السالب. لا تُسلسل الدفعات حسب الوقت وحده عند وجود علاقة عكس؛ التبعية البنيوية تسبق الترتيب الزمني. يظل `payment.id` وwriteId التاريخي مراجع حقيقة؛ mutation الإصلاح الجديد لا يتحول إلى هوية دفع جديدة.

**الأدوار:** يثبت `(organizationId,partyId,role)` من البيانات المطبّعة. تُلتقط كل حقول الحالة/المراجعة المؤثرة في الدور. يعاد استخدام نية مطابقـة للّقطة أو تنشأ نية للمالك نفسه. استلام ACK لدور قديم لا ينظف دورًا تغيرت حالته؛ لا يكفي تطابق partyId.

**غياب بيانات التبعيات:** لا تستنتج عملة أو allocation أو أصل عكس من الإجمالي. يسجل `M03_FINANCIAL_DEPENDENCY_MISSING` ويحفظ العنصر. البيانات التاريخية التي تحتفظ أصلًا بعلامة عملة/تكلفة غير معروفة تنقل بهذه العلامة؛ لا يحوّلها الإصلاح إلى KNOWN بلا دليل.

### 8.6 Optimal — تصنيف مقيد لا يتوقع محتوى الـ26

التطبيع الوحيد للاسم الخام: `trim + uppercase(Locale.ROOT)`، مع الاحتفاظ بالأصل في الدليل. يلزم تطابق نوع العملية وإصدار الحمولة والمراجع، لا مطابقة كلمة في النص.

| raw aggregate بعد التطبيع | operation/الشروط | المسار المعتمد |
|---|---|---|
| VEHICLE | DTO السيارة الصحيح ومؤسسة مثبتة | جسر OPTIMAL_VEHICLE، والصندوق الأصلي هو المالك. |
| MAINTENANCE | DTO صيانة صحيح ومراجعها مثبتة | جسر OPTIMAL_MAINTENANCE. |
| FOLLOW_UP | DTO متابعة صحيح ومراجعها مثبتة | جسر OPTIMAL_FOLLOW_UP. |
| INVOICE | INVOICE_CREATED / INVOICE_UPDATED / INVOICE_VOIDED | سجل تكامل الفاتورة المتخصص؛ ليس بديل نية INVOICE المالية. |
| INVOICE | PAYMENT_RECORDED / PAYMENT_REVERSED | سجل تكامل الدفع تحت aggregate الفاتورة؛ ليس دفعة مالية ثانية. |
| INVOICE | MAINTENANCE_UPSERTED | سجل الصيانة التابع للفاتورة؛ يحسم من mapper/recordId والـcontract gate، لا من aggregate الخام وحده. |
| CONVERSATION | عملية ورسالة توافقان عقد الجسر الموجود | جسر المحادثة المتخصص؛ لا يعاد توجيهها للجسر المالي. |
| غير ذلك، أو payloadVersion/operation غير مدعوم | مهما كانت الحالة | `M03_OPTIMAL_TYPE_UNRESOLVED` مع إبقاء REQUIRES_REVIEW. |

قواعد الحالة:

- `LOCAL_ONLY` + نوع معروف + سبب محلي مثبت من `OptimalBackendContractGate`/mapper = `LOCAL_RETAINED`. لا تقلب الحالة PENDING فقط لتجاوز M03، ولا تصدر طلب RPC غير متاح. تحتفظ كل بياناته ومرفقاته.
- `LOCAL_ONLY` بلا إثبات سياسة أو بنوع مجهول = مراجعة، لا استثناء تلقائي.
- `PENDING` و`SYNCING` و`FAILED` تعالج وفق إثبات الطلب والحجز وتصنيف الخطأ؛ FAILED ليس دائمًا خطأ شبكة.
- `SYNCED` لا يثبت تسليم المحتوى الحالي بمجرد الاسم؛ تربط remote identity/version أو إيصالًا موثقًا بالمحتوى.
- تفعيل عقد التكامل لاحقًا لا يرسل سجلات LOCAL_RETAINED تلقائيًا ببيانات معاد بناؤها. يعاد تحقق الهوية والتبعيات، وتجهز حمولتها مرة واحدة ثم تدخل المالك المتخصص نفسه.

المجاميع تعرض منفصلة: `unresolvedReviewCount` و`durablePendingCount` و`verifiedReceiptCount` و`localRetainedCount`. نجاح مزامنة Verto الأساسية مع سجلات تكامل محلية يسمى `CORE_CAUGHT_UP_WITH_LOCAL_ONLY`، لا «اكتملت جميع المزامنة».

### 8.7 التنظيف الآمن

التأكيد لا يغير بيانات الأعمال إلى clean إلا داخل معاملة تجمع:

```text
validatedReceipt.org == source.org
AND validatedReceipt.mutationId == packet.mutationId
AND receipt.requestHash == packet.wireSha256
AND accepted business identity / generation snapshot proof matches packet
AND current.localGeneration == packet.capturedGeneration
AND current.businessContentHash == packet.capturedContentHash
AND no later unresolved mutation touches the same rows
```

بالنسبة لمجموعة: تتحقق إيصالات جميع الأعضاء، وتُختبر أجيال/بصمات الصفوف ذات الصلة كل على حدة. عند فشل شرط الحالة الحالية، يؤكد الصندوق القديم فقط؛ يبقى التعديل الحالي dirty وتبقى نيته الأحدث. حالة `REPLAYED` مقبولة فقط إذا عادت بالإيصال الأصلي والبصمة المطابقة. لا يعد `NO_OP` إثباتًا إلا مع نسخة وحمولة/بصمة موثوقة تثبت غرض النية.

### 8.8 متى يرفع حاجز M03؟

يرفع حاجز **ترقية الاستعادة** حين تكون كل مصادر الجرد الحالي إما محفوظة في نيات مطابقة يحميها سجل الملكية، أو مثبتة بإيصال مطابق، أو LOCAL_RETAINED معروف ومحمي من المسح. وجود أي مصدر غير مسند المؤسسة أو ناقص الهوية أو المحتوى يمنع الترقية الاستبدالية.

لا يشترط ACK لكل نية لمجرد بدء مرحلة staging غير مدمرة؛ وإلا ينشأ انتظار دائري بين الحاجز والإرسال. يسمح بإرسال النيات المجهزة والمثبتة المستقلة لحلها، وتظل البيانات المحلية محمية. لا يجوز تطبيق snapshot فوق صف يتوقف إثباته على ACK لم يصل بعد.

لا تعاد رمية IllegalStateException عامة بصورة متكررة. يرجع المنسق `MIGRATION_BLOCKED` مع الأسباب والأعداد والإجراء الممكن، ويترك المصدر والحاجز قائمين. إعادة الضغط تعيد الجرد عند تغير الأدلة، ولا تكرر الكتابة الحتمية نفسها.

## 9. ثبات النية وbaseVersion والإيصالات — R05 وR07

### 9.1 الحفظ المحلي

داخل معاملة الكتابة: ينفذ التعديل التجاري، تزيد الأجيال المتأثرة، تلتقط **حمولة النقل الكاملة**، تخصص التسلسلات من `sync_sequence_state` لا من الساعة أو MAX، تنشأ النية والـpacket وpending references وبيان المجموعة. فشل أي خطوة يعيد المعاملة كلها. ينفذ `requestSync` بعد الالتزام، مع بقاء generation طلب الإيقاظ دائمًا.

تُنقل قراءة المصروف والجرد والائتمان والتكلفة ودورة الشراء من `UnifiedFinancialOwner310Route.prepare` إلى مصنع لقطة داخل المنتج. يصبح prepare قارئًا للّقطة المحفوظة ومتحققًا من العقد، ولا يقرأ جدول الأعمال لإعادة تكوين محتوى النية.

### 9.2 اختيار النسخة السابقة

- إنشاء كيان CRUD مثبت الغياب: `baseVersion=0`، ويقبل السيرفر فقط إذا كان غير موجود. المجهول ليس صفرًا.
- تحديث/حذف كيان موجود: النسخة المطبقة/المؤكدة التي بني عليها التعديل من `sync_entity_version`.
- تعديلات محلية متتابعة: تحفظ نية ثانية بمحتواها، وتنتظر إيصال السابقة في `predecessor_mutation_id`. بعد وصوله تجهز envelope الثانية مرة واحدة باستخدام serverVersion الناتج؛ لا يُعاد بناء محتواها من صف أحدث.
- إن وصل تعديل جهاز آخر ولم يطبق بسبب local pending، لا تستخدم observedServerVersion لتجاوز التعارض. لا يحدث auto-rebase غير منصوص عليه.
- الحقائق immutable مثل حركة مخزون أو دفعة بهويتها: يتحقق الخادم من identity/content؛ تكرار مطابق يعيد الأصل، وتكرار مختلف مراجعة. لا يفسر lifecycleVersion كـbaseVersion عام.
- كل سجل Bootstrap مطبق يغذي authority النسخ في المعاملة نفسها. مرحلة staging ليست مصدر النسخة المعروضة وحدها.

### 9.3 تثبيت البايتات

نسخة بروتوكول الإصلاح في النقل الموحّد هي `contractFamily="verto-unified-sync"` و`contractVersion=2`؛ إنها ترقية عقد داخل مسار V2 نفسه، لا معمارية مزامنة ثالثة. payloads المتغيرة هنا تحمل `payloadVersion=2`، وتتحقق capabilities من دعمها قبل الكتابة.

يشفّر الطلب مرة واحدة كنص JSON UTF-8 بلا BOM. يحفظ النص نفسه و`SHA-256` لبايتاته. يحسب السيرفر البصمة من النص المستلم **قبل** تحويله JSONB؛ لا يختبر hash لإعادة تسلسل مختلفة. جميع retries ترسل النص ذاته؛ تغيّر المسافات أو baseVersion أو حقول الوقت يعامل طلبًا مختلفًا بهوية مكررة ويُرفض. التوكن وleaseToken ومعلومات النقل لا تدخل في النص الثابت.

لا يلزم التخمين حول canonicalization بين PostgreSQL وKotlin: النص المحفوظ نفسه هو مادة hash. يتحقق golden test من نص وبصمة معلومتين، ويثبت السيرفر أن هوية المؤسسة والعملية المقروءة من النص هي نفسها المخوّلة في الجلسة.

### 9.4 الطلب السابق مجهول المصير

إذا كانت bytes الأصلية محفوظة: تعاد كما هي بعد استعلام إيصال/انتهاء الحجز. إذا كان إيصال مطابق متاحًا: يطبق وفق 8.7. إذا غابت bytes ولا يمكن إثبات قبول/رفض العملية القديمة، **لا تبنى حمولة مختلفة تحت mutationId القديم**.

يستخدم مسار reconciliation الجديد بهوية إصلاح مستقلة ومراجع الهويات الأصلية. الخادم يقارن الحقائق التجارية الموجودة أولًا. لا يعتبر NOT_FOUND وحده إذنًا لإعادة إنشاء الأثر إلا إذا كان نطاق تاريخ الإيصالات كاملًا ونقطة سياج البروتوكول القديم مثبتة. عند عدم اكتمال التاريخ يرجع `OUTCOME_UNKNOWN` وتبقى المراجعة محفوظة.

### 9.5 بروتوكول الحجز

claim يتم بتحديث مشروط لحالة المالك ووقت الاستحقاق، مع leaseToken فريد وscopeEpoch. مدة الحجز التصميمية 120 ثانية، وتجديده كل 30 ثانية أثناء النقل الطويل ما دامت الملكية صحيحة. انتهاء العامل لا ينهي نية بلا إيصال.

ACK محلي يستخدم شرط leaseToken/epoch الحاليين. نتيجة طلب بحجز قديم لا تنظف صفًا حجزه عامل جديد؛ تحفظ نتيجة الاستعلام/الإيصال لاستردادها بصورة موثقة. قبل إعادة حجز طلب انتهت ملكيته يمكن استعلام إيصالاته، دون تغيير bytes.

## 10. العقد المالي الكامل — R02

### 10.1 الشكل الإلزامي

حدث الفاتورة يحمل `FinancialAggregateSnapshotV2` وليس `lineCount` فقط. الحقول في ملحق A جزء من العقد؛ القائمة التالية تحدد البنية والعلاقات:

```text
FinancialAggregateSnapshotV2
  schemaVersion: 2
  organizationId
  invoiceId
  financialStreamVersion / expectedFinancialStreamVersion
  snapshotKind: FULL
  header: InvoiceDtoV2
  items: InvoiceItemDtoV2[]
  dueInstallments: InvoiceDueInstallmentDtoV2[]
  payments: PaymentDtoV2[]
  paymentAllocations: PaymentAllocationDtoV2[]
  realizedFxEvents: RealizedFxEventDtoV2[]
  returnDocuments: InvoiceReturnDocumentDtoV2[]
  returnLines: InvoiceReturnLineDtoV2[]
  returnPaymentAllocations: InvoiceReturnPaymentAllocationDtoV2[]
  explicitTombstones: {entityType,id,previousVersion,reason}[]
  effectReferences: {owner,factType,factId,businessIdentity,contentHash}[]
  businessContentHash
```

جميع القوائم موجودة حتى حين تكون فارغة، لكن FULL تعني اكتمال قراءة التجميع في اللقطة المحددة، لا أن حذف ما لم يرد مسموح دائمًا. التوابع المنشأة في معاملة الكتابة تلتقط بعد انتهاء حفظها وقبل commit. إن كان موضع `appendInvoice` الحالي أسبق من حفظ التوابع، ينقل ختم اللقطة إلى نهاية معاملة منسق الكتابة؛ لا يقرأها بعد commit.

في أحداث PAYMENT يبقى invoiceId هو جذر تيار الفاتورة، ويحمل `paymentId` صراحةً مع operation وأصل العكس. تحفظ لقطة التجميع والتبعيات بما يمنع تفسير aggregateId على أنه paymentId. لا يؤدي وصول لقطة دفع إلى overwrite لرأس فاتورة أحدث؛ يحكم financialStreamVersion التسلسل، وتنتظر اللقطة المتعارضة مسار الحل.

### 10.2 ترتيب محتوى اللقطة وبصمتها

ترتب مجموعات السجلات حسب id ترتيبًا نصيًا ثابتًا؛ الأقساط حسب `(sequence,id)`؛ حالات ارتباط الأصل/العكس تحسم أثناء التطبيق وليس بترتيب JSON. أسماء حقول DTO وترتيب تشفيرها ثابتان في serializer الإصدار 2. تحسب businessContentHash على projection دلالية تستبعد حقل businessContentHash نفسه وجميع hashes، وcapturedGeneration، وexpected/server versions/revisions/sequences، وflags المحلية والحجوزات وأوقات نقل الشبكة والأرقام المشتقة للبحث والعرض. تستبعد أيضًا recordedAt/serverAcceptedAt إذا كان السيرفر هو من يولدهما؛ أوقات الحدث وحقول recordedAt التاريخية التي أنتجها المجال أصلًا تبقى محفوظة في DTO ومشمولة دلاليًا. تميز هذه الحقول عبر schema المصدر لكل نوع، لا بالتخمين من اللاحقة وحدها. لا يستبعد أسعار البنود أو تكلفتها التاريخية أو هوياتها أو توابع الدفع.

الحقل الاختياري يحمل null صريحًا. لا تعامل absent/null/empty بوصفها مترادفات إلا في تحويل قديم موثق بحقل بعينه. المعرفات تحفظ كما هي؛ لا UUID عشوائي عند غياب هوية واردة، ولا cast إلى UUID لمفتاح مركب من نوع partyId:role.

### 10.3 المال والمقادير

الحقول المالية ذات اللاحقة Minor هي المصدر العددي؛ لا تستقبل Double ثم تحسب منها المال مرة أخرى. تحافظ هذه النسخة على `Money.MINOR_SCALE=2` كما في S6؛ لا تغير قواعد العملة في هذا الإصلاح. تبقى أسعار الصرف snapshots نصية عشرية كما سجلت، ولا تستبدل بسعر حالي. المقادير الصحيحة لا تمر عبر Double، وعمليات الجمع/الضرب تفحص overflow.

حقول Double التوافقية، عند لزومها للجداول الحالية، تشتق باستخدام Money من القيمة Minor، وليس العكس. إذا كان constructor/init يعيد حساب amountMinor من Double، يعدل ذلك المسار ليقبل القيمة القانونية مباشرةً؛ نجاح التسلسل وحده لا يكفي. لا تخفض دقة Long كبير بصمت.

### 10.4 أصحاب الآثار ومنع الازدواج

القرار الملزم: **نقل الحقائق الموجودة، لا إعادة تشغيل عملية البيع على كل جهاز**.

- رأس الفاتورة وبنودها والأقساط projection مالية يملكها الحدث المالي.
- paymentId حقيقة دفع واحدة، وreversalId حقيقة مستقلة تربط الأصل؛ allocations وFX التابعة تطبق بمفاتيحها الأصلية.
- حركة المخزون يملكها INVENTORY_MOVEMENT؛ تكلفة المخزون يملكها INVENTORY_COST_REVISION؛ الحركة النقدية يملكها CASH_MOVEMENT؛ الائتمان/العمولة يملكان حقائقهما المصرح بها.
- تضم `effectReferences` هويات الآثار الموجودة الناتجة عن معاملة الأعمال، وتضم مجموعة الكتابة payloads هذه الحقائق من صناديقها الفعلية. لا ينشئ مطبّق الرأس حركة ثانية لمجرد وجود إجمالي أو دفعة في snapshot.
- عندما تحتوي snapshot دفعًا موجودًا وتصل رسالة PAYMENT المنفصلة له، تقارن الهوية والبصمة وتكون النتيجة no-op مطابقة؛ لا تزداد الدفعات أو الرصيد مرة ثانية.
- أي trigger منشور ينشئ الأثر ذاته يجب أن يدخل مسار idempotency الواحد بهوية الحقيقة نفسها أو يتوقف عن إعادة إنشائه لهذا الأمر الموثق تحديدًا. لا تستخدم تعطيل triggers شاملًا أو `session_replication_role` كحل.

المجموعة المالية المحلية ذات الآثار التابعة ترسل كـbatch واحد، ولا يمر عضوها عبر مرسل عام مستقل. السيرفر يطبق أعضاء المجموعة وإيصالاتهم وسجل تغييراتهم في معاملة واحدة. لا يسجل ACK ماليًا كاملًا بينما أثر مطلوب في manifest مفقود؛ يرجع `BATCH_DEPENDENCY_MISSING`.

### 10.5 التطبيق المحلي

`FinancialMaterializerV2` يقرأ DTO القانوني، يتحقق من المؤسسة والمراجع والنسخ والبصمة، ثم داخل معاملة مجموعة الوارد:

1. يتحقق من parents وهوية العميل/المورد/الصنف؛ الأسماء snapshot لا تنشئ سجلات عملاء أو أصناف بديلة. الفاتورة النقدية بلا عميل تتبع تمثيل المشروع القانوني نفسه ولا يخترع لها عميلًا.
2. يطبق رأس الفاتورة والبنود والأقساط، ثم الدفعات الأصلية، ثم العكس والتخصيصات وFX، ثم مستندات/سطور المرتجع وتخصيصاتها. ترتيب مؤثرات المخزون والنقد يخضع لتبعيات manifest.
3. يستخدم insert/update انتقائيًا؛ لا `INSERT OR REPLACE` لرأس له أبناء إذا كان يمكن أن يطلق CASCADE. حقل غائب لا يصفر حقلًا آخر.
4. لا يحذف بنودًا أو حقائق immutable لمجرد غيابها من حدث جزئي. حذف مشروع في لقطة FULL يحتاج explicit tombstone موافقًا لعقد المجال؛ إذا تعارض مع مرجع مرتجع أو FK، تحفظ المجموعة للمراجعة ولا تحذف المرجع لتسهيل التطبيق.
5. يقارن fact موجودة غير قابلة للتعديل: نفس id ونفس المحتوى = no-op؛ نفس id ومحتوى مختلف = conflict. كيانات mutable تستخدم versioned update.
6. يحدث نسخ الكيانات وحالة المجموعة إلى APPLIED بعد نجاح جميع خطوات الأعمال، لا بعد insertFinancialInbox فقط.

يمكن إبقاء FinancialInbox كسجل تدقيق مالي ثانوي، لكن يكتب وضعه APPLIED مع نجاح التطبيق الحقيقي في المعاملة نفسها. لا يستدعى `InvoiceSyncParticipant` القديم أو `reconcileFinancialInbox` القديم باعتباره بديلًا عن المطبق الجديد.

### 10.6 إصلاح البيانات التي سبق تجاوزها

بعد ترقية المخطط، يفحص المنفذ أحداث INVOICE/PAYMENT القديمة المعلمة APPLIED خارجيًا، مع حالة جداول العمل وبصماتها. لا يصدّق الفلاج القديم وحده. ينشئ repair jobs حتمية للمجموعات غير المادية، دون إعادة cursor إلى الصفر فوق بيانات غير محمية.

مصادر الاسترجاع بهذا الترتيب: snapshot قانونية كاملة ومثبتة من السيرفر؛ ثم السجلات المحلية الأصلية الكاملة بعد فحص نطاقها وتعارضها؛ ثم النسخة الاحتياطية المتحققة. وجود snapshot بعيدة ناقصة لا يجعلها مرجعًا أعلى من بند محلي لا يوجد في الحدث القديم.

إذا لم تحمل أي نسخة تفاصيل البنود/الحقائق المطلوبة، يسجل `SOURCE_DATA_MISSING` ويوقف إغلاق هذا التجميع. لا يستطيع lineCount إثبات محتوى مفقود. لا يدعي المنفذ استعادة بيانات غير موجودة؛ يسلم قائمة النقص وهوية مصدرها، مع حفظ الباقي.

الاستيراد التاريخي لا يكرر آثارًا موجودة. يقارن business keys في جميع المالكين، ويحفظ reconciliation receipt يعدد ما كان موجودًا وما أضيف وما أصلحت صورته. غياب سجل نقدي لا يبرر اختراعه من مبلغ الدفع دون دليل المجال.

## 11. توحيد DTO للحركة والتكلفة والرصيد — R04

### 11.1 القاعدة

يعيد السيرفر الحمولة القانونية **من السجلات المقبولة**، لا `p_payload` الخام مع إضافة حقل واحد. المصانع والمستقبلات تستخدم DTO الإصدار 2 ذي الحقول نفسها. لا يضاف قارئ متسامح دائم يقبل أسماء عشوائية ويصفر المفقود؛ تحويل v1 القديم محصور في مسار الترحيل وله اختبار مستقل.

الـwire الجديد camelCase. أسماء SQL تظل snake_case داخليًا. الرد يحمل حقول الخادم التي أنشأها فعليًا مثل revision/sequence/recordedAt؛ لا يملؤها الهاتف من ساعته لتمرير reqLong.

### 11.2 جدول التحويل الصريح

| أمر المصدر القديم | DTO القانوني | مصدر القيمة |
|---|---|---|
| movement_id | id | معرف الحركة المقبول؛ يطابق aggregateId. |
| item_id / invoice_id / client_id | itemId / invoiceId / clientId | المراجع المتحققة للمؤسسة. |
| movement_kind / signed_base_quantity | movementKind / signedBaseQuantity | الحقيقة المخزنية المقبولة، بإشارتها. |
| unit_price_minor | unitPriceMinor | قيمة Minor الأصلية. |
| source_type / source_id / source_line_id | sourceType / sourceId / sourceLineId | أصل الحركة، مع null صريح للمرجع الاختياري. |
| command_id / idempotency_key | commandId / idempotencyKey | هوية الأمر والحقيقة، لا UUID جديد في الاستقبال. |
| posting_group_id / reverses_movement_id | postingGroupId / reversesMovementId | المجموعة وأصل العكس. |
| conversion_factor_snapshot / occurred_at | conversionFactorSnapshot / occurredAt | لقطة التحويل ووقت الحدث الأصلي. |
| device_id / contract_version | deviceId / contractVersion | البيانات المسجلة للأمر. |
| حقل يولده السيرفر | serverSequence / recordedAt / serverAcceptedAt | بيانات السجل المقبول على السيرفر. |
| cost_revision_id / revision_kind | costRevisionId / revisionKind | هوية نسخة التكلفة ونوعها. |
| direct_purchase_cost_minor | directPurchaseCostMinor | التكلفة المباشرة المسجلة. |
| landed_cost_per_base_unit_minor | landedCostPerBaseUnitMinor | التكلفة الموزعة المسجلة للوحدة الأساسية. |
| approved_inventory_cost_minor | approvedInventoryCostMinor | التكلفة المعتمدة المسجلة. |
| currency_code / exchange_rate_snapshot | currencyCode / exchangeRateSnapshot | عملة ولقطة النسخة، لا العملة الافتراضية الحالية. |
| allocation_basis / allocation_residual_minor | allocationBasis / allocationResidualMinor | أساس التوزيع والباقي المسجل. |
| is_provisional / reverses_cost_revision_id | isProvisional / reversesCostRevisionId | حالة النسخة والمرجع الأصلي. |
| approved_at | approvedAt | وقت الاعتماد الأصلي. |
| حقل يولده السيرفر للتكلفة | costSequence / recordedAt | تسلسل التكلفة ووقت تسجيلها الحقيقي. |

حقول source/item/command/idempotency/device/contract المشتركة في التكلفة تطبق عليها التحويلات نفسها. الملحق A يسرد جميع حقول سجلّي الحركة والتكلفة؛ ليست هذه القائمة مبررًا لإسقاط حقل آخر.

**CLIENT_CREDIT:** `amountMinor` وحده أصل القيمة النقدية. DTO يحتوي id، clientId، amountMinor، note، sourcePaymentId، createdAt، employeeId، employeeName. يشتق حقل Room القديم `amount` من Money عند الإدراج؛ يزال اشتراط `reqDouble("amount")` في wire الإصدار 2. لا يعيد السيرفر مبلغًا قد يخالف Minor بوصفه أصلًا ثانيًا.

### 11.3 فشل العقد

حقل إلزامي ناقص أو enum غير معروف أو رقم خارج المدى = `CONTRACT_FIELD_MISSING/INVALID` مع اسم الحقل؛ تحفظ مجموعة الوارد للمراجعة. لا null-to-zero للأرصدة، ولا `abs(Long).toInt()` دون فحص المدى. يختبر encode→RPC على PostgreSQL الاختباري→decode→Room لكل من الأنواع الثلاثة.

## 12. سياسة المصروف المختارة — R08

القرار هو **مصروف قابل للتحديث بنسخة مع تاريخ تدقيق**؛ لا تغيير للواجهة إلى مصروف immutable ولا فرض عكس/إنشاء هوية مصروف جديدة لكل تعديل، لأن مسار الكتابة القائم يسمح بالتعديل.

يحتفظ expenseId بهويته؛ كل تعديل ينتج نية جديدة ولقطة كاملة وbaseVersion وتاريخًا في `expense_revision_history` محليًا وعلى السيرفر: `(org,expenseId,serverVersion)` فريد، مع previousVersion وwriteId وبصمتي قبل/بعد والفاعل والوقت. يقبل المستقبل نسخة أحدث متسقة بعد حماية pending المحلية، ولا يرمي `IMMUTABLE_EXPENSE_FACT_CONFLICT` لملاحظة أو مبلغ قابلين للتعديل.

الأثر النقدي لا يكرر إجمالي المصروف. تعرف قيمة المصروف الفعالة `E` بالمقدار Minor إن كان ACTIVE وبصفر إن كان VOID. أثر التعديل على النقد هو `-(E_new - E_old)`، بهوية حقيقة واحدة مرتبطة بwriteId التعديل. تغيير الملاحظة ينتج فرقًا صفرًا ولا حركة نقدية؛ 10000→15000 ينتج خروج 5000؛ إبطال 15000 ينتج عودة 15000 وهي دلالة المسار المفحوص في `ExpenseRepository.applyUpdateCashDelta/voidActiveExpense` داخل S6.

هذا الفرق ينشئه **منسق الكتابة التجاري مرة واحدة** ضمن batch المصروف وحركة النقد، لا مطبّق الوارد. يطابق الخادم السابق والجديد ومراجع الآثار، ويرفض مجموعة غير متوازنة. يحافظ على هوية مصدر الصندوق وسلوك `CashRegisterManager` القائم؛ لا يضيف المنفذ سياسة اختيارية لمصروف غير نقدي. أي تعارض بين التعريفات المنشورة وهذا المسار يسجل `BLOCKED_EXPENSE_DOMAIN_DRIFT` قبل ترحيل بيانات ذلك المسار. لا يسمح اختلاف التنفيذ بتطبيق المعادلة مرتين.

الفواتير والدفعات والمخزون ذات الحقائق immutable لا تصبح mutable بسبب هذا القرار؛ السياسة خاصة بجذر EXPENSE وتاريخه.

## 13. الاستلام الدائم والتطبيق وحل التعارض — R11

### 13.1 استلام الصفحة

يتحقق Pull من scope/contract/البصمات، ومن أن كل مجموعة وصلت كاملة بترتيبها وعددها، وأن حدود الصفحة تغطي ما يقرره رمز الاستمرار. داخل معاملة قصيرة: يسجل جميع تغييرات المجموعات في `sync_inbox`، ويثبت سجل المجموعة، ثم يحفظ receivedCursor. تكرار `(scopeId,revision)` بالمحتوى نفسه no-op؛ اختلافه خطأ عقد يمنع تقدم الرمز.

لا تنفذ عمليات شبكة داخل هذه المعاملة. موت العملية قبل commit يعيد الصفحة؛ موتها بعده يترك payloads قابلة للتطبيق بعد التشغيل. لا تمسح مجموعة RECEIVED بسبب تقدم رمز الاستلام.

### 13.2 اختيار مجموعة قابلة للتطبيق

يبني السيرفر لكل مجموعة `touchedKeys` و`dependsOnTransactionIds` من الحقائق المقبولة؛ يتحقق العميل من المفاتيح المشتقة أيضًا من DTO. المجموعة جاهزة حين تكون كاملة، وتبعياتها مطبقة أو موجودة قانونيًا في المجموعة نفسها، ولا توجد نية محلية غير محسومة تمس مفتاحًا تحميه أو projection مرتبطة به.

أي مجموعة لاحقة تمس كيانًا/تيارًا من مجموعة متوقفة تنتظرها، حتى لو كانت نسخة أعلى. المجموعة المستقلة كليًا تطبق. لا تقسّم مجموعة مالية إلى بعض أسطرها لتجاوز تعارض واحد. مؤشرات الرصيد المشترك تعاد من الحقائق المطبقة، ولا تقفز إلى balance snapshot تفترض مجموعة ما زالت معلقة.

### 13.3 حالات الانتظار

`WAITING_LOCAL` لنية محلية أو مراجعة في أحد المفاتيح؛ `WAITING_DEPENDENCY` للأصل/المجموعة الغائبة؛ `REQUIRES_REVIEW` لتناقض الحقيقة أو عقد الحقول. تحفظ الأسباب والمعرفات الداخلية والنسختان وبصماتهما. لا يرمى `PENDING_LOCAL_MUTATION` لإلغاء استلام الصفحة كاملة، ولا تزال حماية التعديل المحلي.

اكتمال نية/تبعية يزيد generation طلب التطبيق ويوقظ المطبق الدائم. ينتظر نفس الكيان دون polling سريع؛ يستمر استلام المستقل حتى حد التخزين الآمن. قبل كل دورة يعاد فحص حالة المصدر الحقيقي، لا نتائج محفوظة في الذاكرة فقط.

### 13.4 حل التعارض دون قرار مالي تلقائي

الافتراضي لكل اختلاف غير مغطى باختبار مطابقة هو `REQUIRES_REVIEW`؛ لا last-write-wins ولا server-wins ولا دمج مبالغ.

واجهة المراجعة تعرض نوع السجل وسبب الخلاف والنسخة المحلية والبعيدة والحقول المختلفة. توفر للكيانات mutable فقط مسارين صريحين:

- **اعتماد نسخة السيرفر:** اختيار صريح من المستخدم المخوّل، مع حفظ النسخة المحلية والنية في سجل الحل؛ تطبق البعيدة في معاملة، وتنهى النية القديمة كـ`SUPERSEDED_WITH_PROOF` بمعنى استبدال معتمد، لا ACK مزيف. لا تنفذ إذا كانت النية القديمة ذات outcome مجهول قبل حسم إيصالها.
- **إعادة إرسال التعديل المحلي على النسخة الحالية:** اختيار صريح؛ تنشأ mutation جديدة تشير إلى supersedes، وتأخذ baseVersion من النسخة البعيدة المعروضة التي وافق المستخدم عليها، مع فحص domain من جديد. النية القديمة لا تغير payloadها، وتغلق فقط بعد إيصال الجديدة أو قرار تخلي موثق.

الدفعات/المخزون/حقائق الائتمان immutable لا تحصل على زر «استبدال حقيقة»؛ تصحيحها عبر أمر عكس/تصحيح موجود ومثبت في المجال. إذا كان هذا الأمر غير متاح لذلك النوع، يبقى `DOMAIN_CORRECTION_REQUIRED` دون إتلاف البيانات. الصدى المطابق لmutation محلية يحسم آليًا بعد تحقق الهوية والبصمة، ولا يعتبر تعارضًا.

## 14. الاستعادة وBootstrap فوق عمل معلق — R06

### 14.1 لقطة الخادم

يبدأ Bootstrap بجلسة نطاق ثابتة وhighWatermark/رمز متابعة Delta يقابلان نفس اللقطة. صفحاتها لا تجمع قراءات عشوائية من أزمنة مختلفة؛ تحفظ snapshot ثابتة أو تمثيلًا تاريخيًا مثبتًا على السيرفر. ختم الجلسة يتضمن counts وdigest ونطاق التغطية وscopeDefinitionVersion. بعد الاستعادة تُسحب التغييرات التالية لهذا الحد.

يمثل Bootstrap الحالة التجارية الكاملة لا آخر حدث مالي مختصر. تستخدم نفس DTO والمطبق والهوية الدلالية كما في Delta. لا تعتبر snapshot فارغة أمرًا لمسح قاعدة محلية غير مؤكدة.

### 14.2 المرحلة المحلية

يحفظ كامل snapshot في staging أولًا، ثم يتحقق من ختمها وتبعياتها. لا يمسح `sync_outbox` أو الصناديق الأقوى أو المرفقات أو سجل إصلاح M03 أو الإيصالات أو أجيال التعديلات. يلتقط بيانًا للمحتوى المحلي المحمي يشمل hashes الفعلية، لا outbox identity digest وحده.

أثناء الترقية في معاملة محلية/مجموعات آمنة: لكل سجل، تستدعى `SyncPendingProtection` من سجل الملكية الموحد. المحمي يبقى بقيمه المحلية، وتدخل نسخته البعيدة مجموعة WAITING_LOCAL. غير المحمي يطبق بمسار REMOTE_APPLY ويتحدث appliedServerVersion. الكتابات الجديدة التي حدثت بعد الجرد الأول محمية لأن القرار يعاد داخل معاملة الترقية.

### 14.3 الحذف والتنقية

لا تنقية مالية أو مخزنية من مجرد عدم وجود السجل في snapshot. الحذف يحتاج tombstone صريحة وهوية نطاق مؤكدة وعدم pending وعدم مرجع مجال يمنعه. CRUD القابل للحذف يمكن تنقيته فقط مع snapshot مختومة كاملة التغطية، وإثبات ملكية كل صف للمؤسسة، واختبار عدم وجود نية/ملف/مرجع محمي؛ وإلا يؤجل.

السجل في جدول تاريخي بلا organization_id لا ينسب تلقائيًا للمؤسسة النشطة. تستخرج علاقة المؤسسة من مراجع موثوقة/نية قديمة؛ عدم الإثبات = `M03_ORG_SCOPE_UNPROVEN`. لا DELETE على جدول غير مسند المؤسسة.

### 14.4 معيار السلامة

قبل الاستعادة وبعدها: تتساوى هويات وبصمات جميع النيات غير المحسومة، ومحتوى كل صف محلي محمي ومرفقاته. مثال القبول: جرد محلي 200/CLOSED وsnapshot قديمة 100/OPEN؛ تبقى 200/CLOSED وتبقى bytes العملية الأصلية. عدادات pending وحدها لا تكفي.

فشل التحقق يبقي staging للتشخيص ويمنع نشر حالة COMPLETED؛ لا يعالج بإعادة المحاولة فوق قاعدة معدلة جزئيًا. حالات انتقال Bootstrap محفوظة وقابلة للاستئناف بعد قتل العملية في كل حد معاملة.

## 15. المعاملات الكبيرة وحدود الموارد — R12

الحد العددي `1000` ميزانية ميسرة للدورة، لا حد صلاحية لمجموعة ذرية. حد المجموعة القانوني التصميمي هو **2,097,152 بايت من التمثيل القانوني المرسل**؛ يتفق الطرفان على طريقة القياس نفسها عبر DTO serialized UTF-8، وليس تقديرًا من عدد الكائنات. في ترقية العقد يجب مواءمة حساب SQL القديم مع هذا القياس باختبارات حدودية.

الخوارزمية:

```text
لكل مجموعة كاملة بالترتيب:
  إذا bytes > maxGroupBytes: CONTRACT_GROUP_TOO_LARGE؛ لا تقدم ولا MORE_AVAILABLE متكرر.
  إذا appliedGroupsThisTurn == 0: اسمح بالمجموعة القانونية ولو count > 1000.
  وإلا إذا يتجاوز العدد ميزانية الدورة: احفظ جدولة متابعة وانتهِ عند آخر مجموعة ملتزمة.
  طبّق المجموعة أو احفظ انتظارها الدائم؛ لا تقسمها إلى معاملة تجارية جزئية.
```

بعد وصول المجموعة إلى inbox يتقدم receivedCursor مرة واحدة. إذا كان العائق محليًا يتغير وضعها إلى حالة انتظار؛ لا تعاد الصفحة من الشبكة كل مرة. مجموعة 1001 صغيرة يجب أن تنتهي بلا loop. مجموعة >2MiB ترجع خطأ عقد مفسرًا، ولا يُقسّم تاريخ مالي قائم عشوائيًا لعلاجه. لا يتبنى المنفذ تغيير حد الأحجام دون تحديث طرفي العقد واختبارات الذاكرة.

يوقف الجلب الجديد عند امتلاء حصة Inbox غير المطبق **64MiB لكل scope** أو عدم كفاية المساحة لالتزام الصفحة. يسمح بصفحة قانونية ترفع الاستخدام إلى 64MiB+maxGroupBytes إذا كان الاستخدام قبلها أقل من الحد؛ ثم يتوقف. يظهر `WAITING_STORAGE_OR_REVIEW`، ويستمر تطبيق المتاح لتحرير الحالة. لا تحذف أحداثًا غير مطبقة لتقليل الاستخدام، ولا تعرض COMPLETED. هذه حدود تصميم وليست قياسات عن بيانات المستخدم.

## 16. نقل مستندات الشحن — R10

### 16.1 القرار التقني

يضاف مستهلك دائم للجدول الموجود `sync_attachment_transfer`، مرتبط بالمنسق V2 وبحساب الصحة. الوجهة المختارة لهذا الإصلاح هي **Supabase Storage private bucket باسم `verto-sync-documents`** لمستندات الشحن؛ لا يغيّر ذلك آلية صور المخزون R2 القائمة. إنشاء bucket وسياساته ضمن نشر السيرفر المتحقق، وليس كشفًا بأن الوجهة موجودة حاليًا.

مفتاح النسخة الجديدة:

```text
organizations/{organizationId}/shipments/{shipmentId}/documents/{documentId}/{sha256}
```

إدخال sha256 يمنع تصادم نسختين من المستند في objectKey نفسه. الملفات القديمة لا يعاد تسميتها عن بعد بلا جرد؛ يُحفظ مفتاحها الأصلي ويربط بمفتاح النسخة الجديدة بعد تحقق محتواها. لا تغيير in-place لنية ملف تغيرت bytes؛ ينشأ transferId لإصدار جديد.

### 16.2 آلة الحالة

```text
PENDING → LEASED → UPLOADED_UNVERIFIED → REMOTE_VERIFIED
        → METADATA_PENDING → COMPLETED
أي فشل عابر → RETRY مع nextAttemptAt
ملف مفقود/إذن دائم/بصمة مخالفة → REQUIRES_REVIEW
إلغاء محلي صريح آمن → CANCELLED، مع تدقيق وعدم فقد أصل غير محفوظ
```

تضاف الحقول الناقصة: `next_attempt_at`، `last_error_code`، `remote_checksum`، `remote_byte_size`، `remote_verified_at`، `metadata_mutation_id`، `cancel_reason`. تعمل الحيازة بشرط token/epoch مثل باقي المالكين.

### 16.3 خطوات التنفيذ

قبل enqueue، يحفظ الملف في مخزن خاص دائم للتطبيق أو يثبت إذن URI مستمرًا؛ الأفضل المعتمد هو نسخة خاصة immutable حسب checksum. تُكتب إلى اسم مؤقت، تتحقق البصمة/الحجم، ثم rename ذرية قبل اعتماد النية. الفشل في إضافة نية لا يسمح بإظهار المستند كأنه محفوظ للمزامنة؛ الملفات اليتيمة غير المشار إليها تنظف لاحقًا، لا الملفات المشار إليها.

يفحص العامل الملف المحلي وبصمته؛ يطلب تصريح رفع محدودًا لمسار المؤسسة والملف نفسه. يرفع باستخدام adapter Storage المثبت في المشروع، مع stream/resume المدعوم والمتثبت من SDK المستخدم، لا تحميل الملف كله إلى الذاكرة. واجهة تصريح الرفع تعيد طريقة الطلب ومتطلبات رفع الاستئناف فعليًا؛ لا يخمنها المنفذ من عنوان URL.

بعد الرفع يطلب تأكيدًا من خدمة خادم تتحقق من bytes/sha256 والحجم في التخزين، لا من ادعاء الهاتف ولا من HTTP 200 وحده. يمكن تنفيذ prepare/confirm كـEdge Functions مخولة؛ سر التخزين يبقى على السيرفر. نجاح الرفع مع ضياع الرد يعالج باستعلام التحقق على المفتاح المحتوائي، لا إنشاء مستند تجاري آخر.

بعد REMOTE_VERIFIED فقط، تنشأ/تجهز نية بيانات المستند الوصفية التي تتضمن المفتاح البعيد والبصمة والحجم. عند وصول إيصالها المطابق يعلّم transfer COMPLETED. لا يرسل `privateUri` بوصفه رابطًا يصلح لجهاز آخر. يجوز حفظ metadata مبكرة بحالة محلية غير جاهزة، لكن لا تعلن READY عن بعد قبل التحقق.

### 16.4 الحذف والاستعادة

حذف مستند لم يرفع بعد يحتاج إجراء المستخدم؛ ينهي النية بـCANCELLED بعد حفظ التدقيق. المستند الذي تأكدت metadata له يحذف بأمر tombstone موثق؛ لا يحذف العامل الملف البعيد أولًا. الملف المرفوع دون metadata يحتفظ به كيتيم قابل للاسترداد حتى حسم النية، ولا ينشأ صف ثانٍ لتجاوز المشكلة.

لا يُرفع حاجز المسح/الخروج لمجرد تحويل الملف إلى LOCAL_ONLY. يغطي حساب الحماية الملف ونيته والـmetadata. انقطاع الشبكة وإعادة التشغيل وتغيير المؤسسة لا يضيّع URI أو يرفع في مسار مؤسسة أخرى.

## 17. المنسق والجدولة ورسائل الحالة — R09 وR13

### 17.1 دورة التنفيذ الواحدة

ترتيب `drainOrchestration` الملزم:

1. التحقق من الجلسة/النطاق/قدرات العقد وامتلاك scopeEpoch، ثم استعادة الحجوزات المستحقة.
2. جرد/إصلاح M03 محليًا مع حماية البيانات؛ لا شبكة داخل transaction الإصلاح.
3. عند حاجة Bootstrap: staging غير مدمرة أولًا؛ promotion فقط بعد بوابة الأدلة وحماية pending. يسمح بدفع النيات المجهزة المستقلة لحل التعليق دون استبدال محلي.
4. إرسال المجموعات المختومة ثم النيات المستقلة بجسور ملاكها، مع تشغيل ناقل المرفقات؛ لا يرسل عضو batch منفردًا.
5. استلام صفحات Delta إلى inbox، ثم تطبيق المجموعات الجاهزة وإعادة فحص التبعيات التي حُلّت.
6. بناء HealthSnapshot واحد من جميع المالكين وinbox وM03 والمرفقات. حفظ الحالة والموعد وgeneration قبل إنهاء Worker.

تظل Realtime إشارة إيقاظ فقط، ولا تكتب حقائق بديلة في Room. المعالجة متسلسلة لكل scope، مع احترام scopes الأخرى وعدم تسريب ردودها.

### 17.2 الموعد القادم

`nextWakeAt` أصغر موعد بين retry مستقبلي لجميع الصناديق، وانتهاء lease غير محسوم، وموعد قابل للحساب لتبعية/نقل ملف. إذا كان runnable الآن، تحفظ متابعة فورية bounded عبر WorkManager الفريد. تسجيل المتابعة دائم قبل طلب الإيقاظ؛ وجود عامل واحد لا يسقط generation طلب أحدث.

سياسة الخطأ العابر: `min(30 ثانية × 2^(attempt-1), 30 دقيقة)` مع jitter ثابت مشتق من mutationId ضمن ±20%، ويثبت الموعد في القاعدة. `Retry-After` الموثوق يمنع المحاولة قبله. لا تُعاد network retries لأخطاء validation/conflict/contract/auth الدائمة. لا يعد الحجز الذي لم ينته طابورًا فارغًا.

### 17.3 تعريف الحالة المعروضة

| الحالة | معناها المطلوب |
|---|---|
| RUNNING | يوجد تنفيذ جارٍ له ملكية صحيحة. |
| WAITING_NETWORK / WAITING_RETRY / WAITING_LEASE | عمل غير محسوم، والسبب/الموعد معروف. |
| WAITING_DEPENDENCY | مجموعة محفوظة تنتظر أصلًا أو نية أخرى. |
| REQUIRES_REVIEW / MIGRATION_BLOCKED | يحتاج عنصر/مجموعة قرارًا أو دليلًا؛ تُعرض أسباب حقيقية. |
| AUTH_REQUIRED / CONTRACT_BLOCKED | عائق جلسة/عقد لا يوصف بخطأ شبكة. |
| CORE_CAUGHT_UP_WITH_LOCAL_ONLY | اكتمل النقل الأساسي فقط؛ سجلات التكامل المحلية باقية ظاهرة. |
| COMPLETED | لا pending/review/rejected غير محسوم، ولا lease/ملف/مجموعة وارد غير مطبقة، وBootstrap جاهز، والسحب لحق بحد دورة معلن. |

انتهاء دورة العامل ليس COMPLETED تجاريًا. يمكن أن ينتهي Worker بنجاح تشغيلي لأن انتظارًا دائمًا سُجل، مع بقاء واجهة المزامنة WAITING/REVIEW. لا تستنبط الواجهة الاكتمال من `Result.success()`.

عند COMPLETED يعرض وقت آخر تحقق وحده؛ لا يدعي عدم وجود أي تعديل أحدث على السيرفر بعد ذلك الوقت. lastSuccessfulSync لا يتحدث عند REVIEW أو عند تقدم receivedCursor دون تطبيق.

### 17.4 الإلغاء

كل catch حول شبكة/استعادة/تطبيق كوروتين يبدأ بإعادة رمي `CancellationException`. لا يصنف الإلغاء `TRANSIENT_NETWORK`، ولا ينشر snackbar شبكة بسبب تبديل المؤسسة أو إيقاف العمل.

يسمح بتنظيف ملكية محلي قصير في `NonCancellable` عند الحاجة، مشروطًا بالـleaseToken نفسه، دون انتظار شبكة. إذا لم يكن مصير الطلب معلومًا يبقى packet للمصالحة. اختبارات الإلغاء تتحقق من نوع الاستثناء وخلو سجل فشل الشبكة، لا من منع الإلغاء.

## 18. واجهات السيرفر المطلوبة وحدود الثقة

هذه واجهات **مصممة للتنفيذ**، وليست أسماء ثبت وجودها على السيرفر الحالي. تستخدم أسماء versioned غير ملتبسة، ويستدعي الإصدار الجديد مسارًا واحدًا فقط. إذا وجد اسم منها منشورًا بتعريف مختلف، يسجل تعارض المخطط ويمنع الاستبدال الصامت. تبقى قراءة إيصالات البروتوكول السابق لأغراض المصالحة، لا ناقل أعمال Legacy ثانٍ.

### 18.1 العقود

| الواجهة | المدخلات الإلزامية | الناتج والتزامه |
|---|---|---|
| `verto_sync_repair_capabilities_v1` | organizationId | contractFamily/version، payloadVersions لكل نوع، maxGroupBytes، definitionFingerprint، حدود صلاحية الإيصالات، حالة سياج البروتوكول القديم، storage capabilities. |
| `verto_resolve_sync_scope_v2` | organizationId | principal مشتق من Auth، scopeId وscopeDefinitionVersion وتغطية الرؤية؛ لا يقبل principal بديلًا من العميل. |
| `verto_apply_sync_batch_v2` | `p_wire_json:text`، `p_wire_sha256:text` | batch receipt وأصل/إعادة جميع member receipts ونسخ/مراجعات مقبولة أو conflict موثق؛ كل مجموعة ذرية. |
| `verto_get_sync_receipts_v2` | organizationId، قائمة mutationIds/business identities | FOUND بإيصال وبصمة/نسخة مثبتة؛ أو NOT_FOUND ضمن horizon كامل؛ أو OUTCOME_UNKNOWN/LEGACY_UNPROVABLE. الغياب ليس ACK. |
| `verto_read_sync_aggregate_v2` | scopeId، aggregateType، aggregateId | snapshot كاملة بالنسخة والتغطية والبصمة أو NOT_FOUND موثوق في نطاق الرؤية؛ تستخدم لإصلاح النقص وحل المراجعة. |
| `verto_pull_sync_changes_v2` | scopeId، cursorToken، softLimit=1000، maxGroupBytes=2097152 | مجموعات كاملة، DTO قانوني، touchedKeys والتبعيات، nextCursor، highWatermark، hasMore. لا skip لمجموعة كبيرة قانونية. |
| `verto_begin_sync_bootstrap_v2` | scopeId | جلسة snapshot ثابتة وwatermark ورمز Delta لنهاية تلك اللقطة. |
| `verto_pull_sync_bootstrap_page_v2` | scopeId، sessionId، pageToken، maxGroupBytes | سجلات/مجموعات كاملة، نسخة كل كيان، token الصفحة التالية؛ ختم counts/digest عند النهاية. |
| `verto-sync-attachment-prepare-v2` | Auth، org، owner/documentId، sha256، size، mime | صلاحية رفع محدودة لمسار خاص، method وآلية الرفع والحدود الفعلية؛ لا service key إلى الهاتف. |
| `verto-sync-attachment-confirm-v2` | Auth، org، documentId، objectKey، sha256، size | نتيجة فحص الملف الفعلي وبصمته وحجمه، أو خطأ صريح؛ لا تصديق لبيانات العميل وحدها. |

تكتب DTO typed للطلبات والنتائج في وحدة العقد، ويولد منها/يقابلها JSON Schema واختبارات PostgreSQL. أسماء SQL physical columns توثق من التصدير الفعلي؛ لا تسمية عمود متخيلة في migration.


المجموعة المرسلة كاملة **بالنسبة إلى projection النطاق المصرح به**. يعيد السيرفر حساب transactionOrder/size وtouchedKeys من السجلات التي يحق لذلك المستخدم رؤيتها، ولا يكشف مفاتيح سجلات مخفية عبر manifest. إذا كان تطبيق الجزء المرئي يحتاج تبعية لا يسمح العقد بوصولها، يرجع SCOPE_DEPENDENCY_UNAVAILABLE؛ لا يقدم مجموعة ناقصة على أنها قابلة للتطبيق ولا يصنع العميل تبعية وهمية. يختبر ذلك ضمن T45 وT29.

### 18.2 طلب المجموعة وبصماته

لإزالة الالتباس بين بصمة عضو وبصمة المجموعة، يخزن `sync_write_batch` أيضًا `wire_json/wire_sha256/prepared_at` للمجموعة المختومة. ويكون body:

```text
{
  contractFamily, contractVersion, organizationId, batchId,
  memberCount,
  members: [ {memberOrder, memberWireJson, memberWireSha256} ],
  snapshotBlobs: [ {sha256, snapshotJson} ]
}
```

`memberWireJson` نص JSON محفوظ في packet، وليس كائنًا يعاد تسلسله أثناء retry. يتضمن mutationId، aggregateType/id، operationType/domainOperation، baseVersion/financialStreamVersion عند اللزوم، local/aggregateSequence، payloadVersion، snapshotHash أو payload، business identities، التبعيات والوقت الأصلي. يحسب الخادم hash لكل نص عضو كما هو؛ `receipt.requestHash` هو memberWireSha256، بينما `batchReceipt.requestHash` هو hash نص المجموعة.

لتجنب تكرار لقطة فاتورة كبيرة في أعضاء دفعاتها، يجوز داخل **التصميم المحدد هنا** تخزين snapshot مرة واحدة حسب hash: جدول محلي `sync_snapshot_blob(content_hash PRIMARY KEY, snapshot_json)` immutable، وأعضاء المجموعة يشيرون إليها. يحوي batch جميع blobs المشار إليها ولا يقبل مرجعًا مفقودًا. immutable وcontent-addressed هنا طريقتا تخزين محددتان، لا إذن بتغيير اللقطة بعد حفظ العضو. تحسب ميزانية المجموعة بعد تضمين هذه blobs.

### 18.3 تسلسل الخادم الذري

1. يشتق auth.uid()، يتحقق من العضوية والصلاحيات الفعلية للمؤسسة وكل نوع عملية؛ organizationId داخل النص لا يمنح صلاحية.
2. يتحقق من نصوص JSON وبصماتها وإصداراتها وحجمها وعضوية المجموعة وعدم تكرار عضو أو reference غير موجود.
3. يقفل سجل التسلسل للمؤسسة ثم جذور الكيانات بترتيب ثابت؛ يوجد قيد فريد لإيصال `(org,mutationId)` و`(org,batchId)` وللهويات الاقتصادية لكل نوع. يعاد فحص الإيصال **تحت القفل** لمواجهة إرسالين متزامنين.
4. إيصال سابق بالبصمة نفسها يعاد كما هو، بلا آثار جديدة. الهوية نفسها ببصمة مختلفة = IDEMPOTENCY_CONFLICT. لا يحول retry إلى update.
5. يتحقق من baseVersion والتبعيات والحقائق immutable وقواعد المال والمراجع. خلاف واحد يرفض/يعلق المجموعة قبل أي commit اقتصادي.
6. يطبق الحقائق وprojections ويخصص نسخها ويسجل مجموعات التغيير وإيصالات الأعضاء والمجموعة في معاملة واحدة.
7. تخصيص مراجعات السجل لا يسمح بتخطي معاملة غير ملتزمة: قفل مؤسسة transaction-scoped محفوظ حتى commit يضمن ترتيب النشر داخل نطاق المؤسسة. لا يكفي nextval ثم السماح لـwatermark بتجاوز مراجعة ستلتزم لاحقًا. لا تفترض أرقامًا متجاورة عند التصفية.
8. يرسل الرد بعد commit. تعطل الاتصال بعد commit يعالج باسترجاع الإيصال المطابق، لا إعادة توليد الأعمال.

تضيف migrations قيودًا وفهارس لازمة، وتفشل قبل النشر إذا وجدت duplicates تاريخية لا توجد خطة reconciliation مثبتة لها. لا تحذف duplicates مالية تلقائيًا لاجتياز unique index.

### 18.4 الأمن والتوافق

تفحص RLS على snapshots والإيصالات والملفات وليس جداول الأعمال فقط. لا يُمنح استعلام عام عبر org من body. لا يعتمد التفويض على user_metadata القابلة للتعديل. تفحص SECURITY DEFINER وsearch_path والمنح؛ تمنع صلاحية anon غير المقصودة. لا يطبع log طلبًا كاملًا أو signed URL أو مفاتيح.

سياسات Storage تقيد المؤسسة والمستند ونوع العملية؛ لا bucket عامة. تعديلات الأمان ضمن هذا الإصلاح يجب أن تحافظ على وصول Verto/Optimal المصرح به، ويختبر جهاز/مستخدم آخر في مؤسسة مختلفة كحالة منع.

لا يرفع التطبيق contractVersion في ثابت فقط؛ يحدث scope handshake والregistries وDTOs وserver routes وBootstrap معًا. أي اختلاف capabilities = CONTRACT_BLOCKED دون fallback إلى مسار V1. تنشر تغييرات السيرفر الإضافية قبل تفعيل كتابة العميل الجديد، وتبقى الإيصالات القديمة مقروءة للمصالحة.

## 19. ترحيل النسخة الموجودة وخطة الرجوع

### 19.1 الترحيل المحلي

ترتيب Room migration: إضافة الجداول/الأعمدة والفهارس غير المدمرة؛ الحفاظ على المصدر القديم؛ تعبئة authorities الممكنة فقط من أدلة واضحة؛ جرد M03 v2؛ التقاط النيات الناقصة؛ بناء pending references؛ ثم تشغيل consumers الجديدة بعد capability gate.

لا تعاد تعبئة wire قديم من الحالة الحالية إذا احتمل أنه أرسل. لا تحول flags dirty إلى clean أثناء schema migration. إصلاح بيانات الأعمال عملية resumable بعد فتح القاعدة لها سجل، وليس loop شبكة داخل `Migration.migrate`.

الأحداث القديمة ذات flags APPLIED تبقى في السجل، لكن لا يؤخذ اكتمال projection منها وحده. سجل الإصلاح يحدد الأجزاء التي أعيد تطبيقها والنسخ القانونية المستخدمة. لا تختفي صفوف journal v1 بعد إعادة تصنيف v2.

### 19.2 انتقال الإنتاج

يختبر المهاجر على نسخة بيانات معقمة/معزولة تماثل الحالات الحقيقية، ثم على جهاز اختبار بترقية in-place. الإنتاج الحقيقي يبدأ بالنسخ المتحقق، ثم بناء snapshot جرد، ثم dry-run يعرض التصنيفات دون إرسال، ثم التنفيذ المحكوم بالعقد. لا تطلق أوامر reconciliation التي تنشئ حقائق قبل اجتياز طبقة الخادم/العميل واختبار آثارها.

تنشر binary تحت الحزمة نفسها وتوقيع الإصدار المثبت نفسه، مع versionCode أعلى مثبت. فقد ملف التوقيع = BLOCKED_SIGNING؛ لا توقيع جديد ثم طلب حذف التطبيق. لا يمسح logcat قبل حفظ الأدلة اللازمة، وتشارك النسخة المنقحة فقط.

### 19.3 الرجوع

قبل أي كتابة v2 حية يمكن إيقاف feature gate والرجوع الثنائي فقط إذا اجتاز اختبار قراءة المخطط الجديد دون تغيير البيانات. بعد قبول كتابات v2، الرجوع الافتراضي **إيقاف نقل وforward-fix**؛ لا downgrade مخطط ولا إعادة backup قديمة فوق عمليات جديدة مقبولة.

تتوقف الدفعات الجديدة عند اكتشاف خلل، وتحفظ الصناديق والطلبات والإيصالات. لا تعكس آثارًا مالية مقبولة بSQL عشوائي، ولا تعيد ACK إلى PENDING كي تبدو العمليات مرسلة مجددًا. أي استعادة بيانات من backup تحتاج مصالحة بكل العمليات اللاحقة لتاريخ النسخة.

### 19.4 إزالة Legacy

بعد اجتياز بديل V2 واختبارات العودة: يزال مستدعو الإنتاج القديم وDI/Workers المجدولة الخاصة به. تحذف تطبيقات النقل القديم غير المستخدمة ضمن قائمة ملفات موثقة، مع بقاء محولات **قراءة تاريخية صرفة** إن احتاجتها بيانات migration، بلا واجهات شبكة أو enqueue إنتاجية.

لا تحذف دوال كانت وحدها تنشئ جداول الأعمال قبل توفير المطبق البديل. يرفض static gate وجود مدخل إنتاج إلى `fullSync`/participants القديمة، لكنه ليس بديل اختبار وظائف البديل. لا تحذف ملفات بيانات المستخدم أو جداولها لمجرد تسميتها legacy.

## 20. مراحل التنفيذ والتسليم المرحلي

التقسيم تنظيمي داخل التنفيذ؛ لا يبيح إصدار نصف إصلاح على بيانات المستخدم.

| المرحلة | العمل المحدد | بوابة الانتقال |
|---|---|---|
| E0 | تثبيت الأرشيف، نسخ البيانات، تصدير عقد السيرفر والقيود، خريطة المصادر | المصدر والنسخ والتعريفات مثبتة، أو BLOCKED صريح لا PASS. |
| E1 | مخطط Room الآمن، authority النسخ/الأجيال، ownership، packets/refs/batches، توقف تنظيف ACK الخاطئ | اختبارات الهجرة وCAS وثبات المحتوى والحماية تجتاز. |
| E2 | DTO v2، مصانع اللقطات، batch API ومطابقات الحركة/التكلفة/الائتمان، سجل الإيصالات | round-trip PostgreSQL وإسقاط الرد ومنع ازدواج الأثر تجتاز. |
| E3 | FinancialMaterializer، DurableInbox، حل التعارض، expense versioning، Bootstrap | استعادة مالية كاملة فوق قاعدة نظيفة وفوق pending محمي، مع تكرار آمن. |
| E4 | M03RepairPlanner والجرد/الإثبات/Optimal وترميم APPLIED القديمة | لا مصدر مجهول مخفي؛ نتائج dry-run ثم اختبارات قاعدة الحالة متطابقة. |
| E5 | المرفقات والجدولة والlease والإلغاء والواجهة | لا pending مع COMPLETED، وملف حقيقي موثق بعد انقطاع وتشغيل. |
| E6 | اختبار جهازين وkill/retry/upgrade، تنظيف Legacy المستخدم، بناء Release موثق | كل معايير القسم 21 مثبتة؛ التقرير لا يرقّي اختبارات لم تُنفذ. |

الإصلاحات تتراكب في فرع واحد من المصدر المثبت. يحفظ كل Commit المرحلة ونتائج الاختبارات. لا ينشر الإصلاح الجزئي الذي ينشئ نيات كاملة قبل وجود سيرفر يستقبلها أو يعطل guard قبل حماية الاستعادة.

## 21. مصفوفة الاختبارات الملزمة

البيئة المطلوبة: اختبارات JVM للوحدات، Room instrumentation حقيقية للهجرة/المعاملات، PostgreSQL اختباري بالتعريفات المقابلة، وجهازان/محاكيان بقاعدتين مستقلتين A وB. لا تعادل اختبارات bodies مستخرجة أو بحث نصي اختبار المنتج. لا يسجل اختبار لم يشغل كـPASS.

### 21.1 M03 وحماية البيانات

| الاختبار | السيناريو | الإثبات المطلوب |
|---|---|---|
| T01 | Fixture من 54 مصدرًا بتقسيم S2، ثم fixture بعدد مختلف | الجرد الحقيقي يطابق كل صف؛ لا اعتماد للرقم 54 في الكود. |
| T02 | فاتورة dirty وبند dirty للأصل نفسه | رابطا مصدر إلى لقطة/مجموعة واحدة؛ لا إنشاء فاتورتين. |
| T03 | تسعة أدوار و17 دفعة مع مصادرها | هويات أصلية ونيات مالك صحيحة؛ لا تنظيف بلا ACK. |
| T04 | Optimal known LOCAL_ONLY بعقد غير متاح | LOCAL_RETAINED ظاهر؛ لا طلب مالي مكرر ولا ACK مزيف. |
| T05 | Optimal type/operation/payload مجهول | يبقى REQUIRES_REVIEW بمحتواه؛ لا إسقاط من الإجمالي دون تفسير. |
| T06 | إعادة تصنيف سجل journal قديم OPTIMAL_UNKNOWN | يحفظ إثبات v1 ويضاف v2 دون identity conflict أو حذف التاريخ. |
| T07 | تشغيل repair مرتين، ثم قتل العملية عند كل حد معاملة | لا duplication؛ استئناف من نفس الهويات والبصمات. |
| T08 | ACK قديم للفاتورة N مع تعديل N+1 | الفاتورة والبند الأحدث باقيان dirty؛ النية الأحدث موجودة. |
| T09 | تعديل بند مع نفس الإجمالي وعدد البنود | تتغير بصمة المحتوى والنية؛ لا lineCount كدليل تطابق. |
| T10 | صف بلا مؤسسة مثبتة أو أصل دفع مفقود | BLOCKED/REVIEW؛ لا نسبة تلقائية للمستخدم الحالي. |

### 21.2 النقل والمال والنسخ

| الاختبار | السيناريو | الإثبات المطلوب |
|---|---|---|
| T11 | بيع A ببندين ودفعة، ثم سحب B | مقارنة كل حقول DTO والجداول، لا عدد الفواتير فقط. |
| T12 | شراء محلي وآخر دولي بعملات/FX مسجلة | تطابق الكميات والأسعار والتكلفة والأقساط والدفعات/FX. |
| T13 | إنشاء/تعديل/دفعة/عكس/مرتجع/إبطال | توابعها ومراجعها صحيحة، وآثار النقد والمخزون مرة واحدة. |
| T14 | نفس batch عشر مرات، وإرسالان متزامنان | إيصالات replay ثابتة، وعدد/مجموع الحقائق الاقتصادية لا يتغير. |
| T15 | يقبل السيرفر الطلب وتسقط الاستجابة ثم يعدّل A السجل | bytes/hash القديمة مطابقة في retry؛ تعديل جديد بهوية جديدة. |
| T16 | نفس mutationId مع body أو baseVersion مختلف | IDEMPOTENCY_CONFLICT بلا أثر إضافي. |
| T17 | تعديلان Offline للكيان نفسه | الثاني ينتظر الأول ويأخذ نسخته المؤكدة عند التجهيز، مع محتوى محفوظ مستقل. |
| T18 | تعديل صنف موجود، وتعديل قالب بعد Bootstrap نسخة 9 | النسخة الصحيحة مستخدمة؛ لا null يؤدي لتعـارض زائف. |
| T19 | مخزون وتكلفة وCLIENT_CREDIT عبر الخادم | round-trip DTO→SQL→DTO→Room؛ amountMinor وتسلسلات التسجيل سليمة. |
| T20 | missing field، overflow، currency/cost unknown تاريخية | خطأ/حالة معرفة صريحة؛ لا صفر ولا عملة/تكلفة مبتكرة. |
| T21 | تعديل ملاحظة مصروف ثم 10000→15000 ثم VOID | التعديل يصل؛ النقد 0 ثم -5000 ثم +15000 وفق مسار ExpenseRepository المثبت، بلا ازدواج. |
| T22 | repair بعد حدث invoice قديم ناقص البنود | ترميم projection من مصدر كامل دون إعادة عمولة/مخزون/دفعة. |
| T23 | bytes قديمة مفقودة وإيصال غير قابل للتحقق | OUTCOME_UNKNOWN؛ لا إنشاء طلب مختلف بالهوية القديمة ولا PASS. |
| T24 | نقص البنود في كل المصادر | SOURCE_DATA_MISSING واضح؛ لا اختراع بنود للتسليم. |

### 21.3 الوارد والاستعادة والتعارض

| الاختبار | السيناريو | الإثبات المطلوب |
|---|---|---|
| T25 | قتل التطبيق بعد حفظ Inbox وقبل apply | استئناف التطبيق من المخزن دون فقد الحدث أو تكرار الأثر. |
| T26 | قتل التطبيق داخل معاملة materialization | لا نصف فاتورة/دفعة ولا APPLIED أو checkpoint متقدم. |
| T27 | مجموعة X متعارضة ومجموعة Y مستقلة | X محفوظة؛ Y تستلم وتطبق؛ الواجهة لا تقول كل شيء مكتمل. |
| T28 | مجموعة لاحقة تمس X أو تعتمد عليها | تنتظر؛ لا تجاوز سببي ولا overwrite local. |
| T29 | دفعة قبل أصلها، وعكس قبل الدفع الأصلي | انتظار دائم واضح ثم تطبيق عند وصول الأصل دون سجل بديل. |
| T30 | مستخدم يحسم mutable conflict بخياري القسم 13.4 | تدقيق النسختين؛ لا تغيير bytes القديمة ولا حذف نية مجهولة المصير. |
| T31 | Bootstrap B نظيفة من حالة مالية متعددة العمليات | مقارنة كل الجداول/المراجع والنسخ، وليس آخر event فقط. |
| T32 | Bootstrap فوق جرد 200/CLOSED محلي و100/OPEN بعيدة | المحتوى المحلي وpacket محفوظان؛ البعيدة WAITING_LOCAL. |
| T33 | Bootstrap مع pending في كل owner وبمرفق | hashes النيات والصفوف/الملفات المحمية قبل وبعد متطابقة. |
| T34 | Snapshot فارغة/منتهية/ناقصة أو scope تغير | لا مسح بيانات؛ رسالة سبب دقيقة واستئناف آمن. |
| T35 | 999 و1000 و1001 تغيير في أول مجموعة قانونية | تقدم received/apply مرة واحدة وبلا تقسيم تجاري. |
| T36 | مجموعة عند حد 2MiB وأخرى فوقه | الأولى تتقدم؛ الثانية خطأ عقد واضح دون loop MORE_AVAILABLE. |
| T37 | بلوغ حد Inbox/نفاد القرص | لا تقدم رمز دون حفظ ولا حذف unapplied؛ حالة انتظار صريحة. |

### 21.4 التشغيل والمرفقات والأمن

| الاختبار | السيناريو | الإثبات المطلوب |
|---|---|---|
| T38 | توقف بعد claim ثم تشغيل قبل انتهاء lease | WAITING_LEASE، nextWake عند الانتهاء، لا COMPLETED. |
| T39 | رد lease قديم بعد إعادة الحجز | لا ACK/تنظيف للحجز الجديد؛ استعادة الإيصال بصورة متحققة. |
| T40 | طلب requestSync جديد أثناء إغلاق الجولة | generation لا تسقط؛ توجد متابعة مسجلة. |
| T41 | مرفق مع قطع الشبكة وإعادة التشغيل | ملف دائم، نفس object identity، تحقق checksum ثم metadata ACK. |
| T42 | نجاح رفع وضياع الرد؛ ثم تحقق/إعادة | لا duplicate مستند؛ الاستئناف يصل COMPLETED بعد إيصال metadata. |
| T43 | ملف مفقود، إذن مرفوض، checksum مختلف، ملف معدل بنفس documentId | مراجعة واضحة؛ الإصدار الجديد هوية checksum مختلفة، لا استبدال صامت. |
| T44 | الإلغاء في scope/Bootstrap/push/pull | CancellationException تخرج كما هي؛ لا تسجيل خطأ شبكة مضلل. |
| T45 | مؤسسة أخرى تحاول طلب snapshot/receipt/objectKey | منع خادم مثبت بلا تسريب بيانات. |
| T46 | تبديل المؤسسة أثناء رجوع RPC/رفع ملف | لا تطبيق/ACK/رفع في النطاق الجديد؛ ملف الأصل محفوظ. |
| T47 | نوع عضو صالح يتبعه عضو batch مرفوض | لا commit جزئي ولا receipt نجاح جزئي للآثار. |
| T48 | ترقية APK in-place لقاعدة قديمة غير فارغة | البيانات والتوقيع محفوظان؛ لا destructive migration. |
| T49 | عقد السيرفر لا يطابق capabilities/client | CONTRACT_BLOCKED بلا fallback لشبكة Legacy. |
| T50 | فحص مسارات الإنتاج واختبار Hilt | لا empty materializer ولا مستدعي Legacy؛ المكونات المصممة مستخدمة فعليًا. |

### 21.5 أدلة كل اختبار

لكل Txx: المصدر/Commit، نسخة مخطط Room والخادم، المدخلات وهوية المؤسسة الاختبارية، أمر التشغيل، الزمن والخروج، assertions الآلية، expected/actual، والسجل المنقح. عند الحاجة: dumps للجداول المعنية قبل/بعد، hashes الطلبات والإيصالات، ومؤشرا received/applied وعدادات الحالات.

الاختبارات السابقة المسماة M04–M07 تعاد ويضاف إليها ما سبق؛ نجاحها لا يعوض أي Txx سلوكي. نتائج الاختبارات السلبية الحالية في حزم الأدلة تحول إلى assertions تعاكس السلوك المعيب، لا تبقى «REPRODUCED» وتعد PASS للإصلاح.

القيم المالية تقارن بالـMinor وبنفس العملة ومفاتيح الحقائق، والمخزون بالكمية الموقعة وهويات الحركات. لا مقارنة تقريبية بالمظهر أو Double أو إجماليات تختبئ خلفها بنود مختلفة.


### 21.6 ربط العيوب باختبارات إغلاقها

| العيب | الاختبارات الملزمة المرتبطة مباشرةً |
|---|---|
| R01 | T01–T07، T10، T23، T34 |
| R02 | T11–T14، T22، T24–T26، T29، T31، T47 |
| R03 | T08، T09، T15، T39 |
| R04 | T19، T20 |
| R05 | T17، T18، T31 |
| R06 | T10، T32–T34، T46 |
| R07 | T15–T17، T23 |
| R08 | T21، T27 |
| R09 | T38–T40، T41، T37 |
| R10 | T41–T43، T46 |
| R11 | T25–T30، T37 |
| R12 | T35–T37 |
| R13 | T44–T50، T14، T16 |

اشتراك اختبار بين عيبين لا يضاعف عدد الاختبارات؛ العدد الفريد خمسون. نتائج الحالة الحقيقية M03 تبقى مطلوبة فوق fixture المصممة إذا أريد اعتماد إصلاح جهاز المستخدم نفسه.

## 22. التسليم ومعيار الإغلاق

الملفات المطلوب أن يسلمها المنفذ بعد العمل:

| الملف | المحتوى الإلزامي |
|---|---|
| `SOURCE_BASELINE.json` | SHA المصدر، Commit، المصدر المنشور والخطوات التي أنتجت النسخة. |
| `SYNC_IMPLEMENTATION_MAP.md` | UI→use case→transaction→owner→packet→RPC→inbox→materializer، بأسماء دوال واستدعاءات وأسطر بعد الإصلاح. |
| `SYNC_OWNERSHIP_MATRIX.csv` | جميع الأنواع والمالكين والتبعيات وserializer/applier/recovery والاختبارات. |
| `SYNC_CONTRACT_V2.schema.json` + fixtures | جميع حقول/أنواع DTO والrequests/receipts، بلا placeholder لجزء مالي. |
| `SYNC_M03_RECONCILIATION.md` | جرد قبل/بعد حسب المصادر، الروابط والإثباتات، المعلق والمحلي فقط، دون تسريب بيانات. |
| `SYNC_SERVER_DEFINITIONS/` + migrations | التعريفات المقارنة وبصماتها وتاريخ نشرها واختبارات RLS/receipt/batch. |
| `SYNC_TEST_RESULTS.md` + نتائج آلية | T01–T50، PASS/FAIL/BLOCKED/NOT_RUN مع حدود التنفيذ. |
| `SYNC_DATA_PARITY.json` | مقارنة A/B وBootstrap للجداول والحقائق والأرصدة والبصمات والتكرار. |
| `SYNC_LEGACY_REMOVAL_MANIFEST.md` | المحذوف والمحفوظ كقارئ تاريخي، وإثبات انقطاع المسار الإنتاجي القديم. |
| `CHANGELOG.md` وتحديث Backlog إن وجد | ما تغير، أثره، مخاطر الرجوع، NEXT_ACTION الحقيقي. |
| ZIP نهائي وAPK Release عند طلب/تفويض البناء | كود بلا أسرار؛ SHA-256، Commit المضمن وversionCode وبصمة توقيع، وسجل بناء فعلي. |

**الإغلاق العام PASS** مشروط بإغلاق R01–R13 ضمن البيانات/البيئة المعلنة واجتياز الاختبارات المطلوبة، وعدم وجود نقص بيانات مخفي، وتطابق A/B وBootstrap، وبقاء التعديلات غير المؤكدة آمنة. في حالة LOCAL_RETAINED المعتمدة، يفصل التقرير نجاح النقل الأساسي عن التكامل المحلي، ولا يمنحه نجاح تسليم لم يحدث.

**وجود unknown أو نقص مصدر لا يمنع تسليم عمل جزئي صحيح، لكنه يمنع تسميته إصلاحًا مكتملًا للحالة المتضررة.** يذكر المنفذ تحديدًا: ما نُفذ، ما اختُبر، ما بقي محجوبًا، أي بيانات لم يصل لها إثبات، وشرط فتح العائق. لا يسأل عن قرارات حسمها العقد، ولا يملأ الفجوات بقرارات مالية من نفسه.

## 23. جدول الممنوعات البديلة

| الاختصار غير المقبول | البديل الملزم |
|---|---|
| حذف check الخاص بـM03 أو تحويل reviewCount إلى صفر | جرد/نيات/إيصالات/LOCAL_RETAINED مثبتة، مع حماية promotion. |
| تحويل كل LOCAL_ONLY إلى PENDING أو SYNCED | مطابقة النوع/العملية والبوابة أو إبقاؤه محفوظًا للمراجعة. |
| إعادة تشغيل ناقل الفواتير Legacy لتظهر البيانات | إكمال snapshot وFinancialMaterializer V2 واختبار الجداول. |
| إعادة بناء payload في retry من صف الأعمال الحالي | packet ثابت ونسخة جديدة لتعديل جديد. |
| تنظيف invoiceId عند وجود أي ACK قديم | CAS على generation وبصمة محتوى كاملة وربط بالإيصال. |
| تغيير baseVersion لنفس طلب سبق تجميده | نية حل جديدة بهوية جديدة بعد حسم القديمة. |
| تجاهل تعارض المجموعة أو إسقاط تغيير لتقديم cursor | Inbox دائم ومراجعة/تبعيات، مع تطبيق المستقل فقط. |
| REPLACE لجذر فاتورة أو مسح قاعدة قبل Bootstrap | materialization انتقائي وحماية pending وsnapshot مختومة. |
| حساب ربح قديم من سعر شراء اليوم | نقل snapshots التاريخية أو إعلان نقصها. |
| وضع المرفق COMPLETED عند HTTP 200 فقط | checksum/size verification ثم metadata receipt. |
| اعتبار نجاح Gradle/البوابات النصية نجاح مزامنة | Txx سلوكية وجهازان/Room/PostgreSQL ومقارنة بيانات. |
| استخدام test data على مؤسسة المستخدم أو مشاركة secrets | بيئة اختبار معزولة وسجلات/حزم منقحة. |


## ملحق A — مرجع حقول DTO ومواقع المصدر

هذا الملحق جرد لحقول السجلات ذات الصلة من S6، مع **قرار نقل محدد** لكل حقل. لا يعد المصدر الحالي بهذه القائمة DTO مكتملًا؛ هي تعليمات لصنعه. قواعد النوع: String→JSON string، Long/Int→JSON integer مع فحص المدى، Boolean→JSON boolean، enum→اسم القيمة القانونية نفسها في enum المصدر، nullable→نوعه أو null الصريح. لا enum افتراضية عند قيمة غير معروفة.

كل حقل موسوم «ينقل كما هو» إلزامي في snapshot حتى إذا كان يسمح null. لا تستخدم default constructor لإخفاء غياب حقل في الوارد. تعد قيمة فارغة مسجلة في legacy قيمة مختلفة عن حقل لم يصل، وتبقى حالة معرفة العملة/التكلفة القديمة مصاحبة لها.

**حقول Double ذات أصل Minor موجود:** لا ترسل كأصل ثانٍ؛ يشتق حقل التوافق المحلي من Minor. **قيم جرد الصندوق القديمة التي لا تملك Minor:** تضاف لها أعمدة Minor قانونية ويحول تمثيلها القديم مرة واحدة بالمسار `Money.fromLegacyDouble` نفسه، scale=2 وHALF_UP، مع حفظ قيم المصدر ونتيجة التحويل في migration evidence. NaN/Infinity/overflow أو خلاف تحويل غير مفسر = مراجعة، لا تقريب مختلف من المنفذ. تصبح الكتابات والاستقبال التالية Minor أصلًا وحيدًا؛ لا إعادة تحويل أثناء retry.

`imageUri` في الفاتورة حقل مسار/عرض محلي؛ لا يصبح URL صالحًا لجهاز آخر بإرساله. المرفق البعيد ينقل بمرجع objectKey/checksum من مالك ملفات موثق. إن لم يوجد ذلك المرجع، يحتفظ الملف محليًا ويظهر ضمن البيانات غير المسلّمة؛ لا يدعي اختبار تطابق المرفقات نجاحًا. نطاق ناقل القسم 16 المطلوب هنا هو مستندات الشحن؛ نقل صور فواتير جديدة غير موثقة خارج هذا النطاق لا يضاف باجتهاد.

`explicitTombstones.previousVersion` لأبناء الفاتورة يشير إلى نسخة التجميع المالي التي كانت تلك النسخة من الابن جزءًا منها، لا إلى عمود نسخة مخترع في الابن. استبعاد حقل نقل/عرض لا يعني حذفه من النسخة الاحتياطية.

### A.1. `InvoiceEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:52`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceNumber` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceNumberSearch` | `String` | مشتق محليًا بنفس SearchTextNormalizer، لا أصلًا تجاريًا مستقلًا. |
| `clientId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `organizationId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `supplierInvoiceReference` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `supplierInvoiceReferenceNormalized` | `String?` | ينتج من supplierInvoiceReference بنفس normalizer المثبت؛ يتحقق من مطابقته، لا يختار المنفذ تطبيعًا جديدًا. |
| `type` | `InvoiceType` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `category` | `InvoiceCategory` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `description` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `totalAmount` | `Double` | توافقي مشتق من `totalAmountMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `totalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `transactionCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `transactionAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceExchangeRateSnapshot` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `exchangeRateDirection` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `exchangeRateTimestamp` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `exchangeRateSource` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalAmountAtRecognitionMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `legacyCurrencyStatus` | `LegacyCurrencyStatus` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `dueDate` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `notifyDaysBefore` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `notifyRepeatDays` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `notificationsEnabled` | `Boolean` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `notes` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isOwedToMe` | `Boolean` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `imageUri` | `String` | مسار عرض محلي؛ مرجع الملف البعيد الموثق منفصل، ولا تسرب URI الجهاز. |
| `status` | `InvoiceStatus` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `discount` | `Double` | توافقي مشتق من `discountMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `discountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `commission` | `Double` | توافقي مشتق من `commissionMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `commissionMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `commissionBeneficiaryClientId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `commissionSource` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `shipmentId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `purchaseOrderId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `purchaseScope` | `PurchaseScope` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdBy` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `lifecycleStatus` | `InvoiceLifecycleStatus` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `lifecycleVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `postedAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voidedAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voidReason` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voidWriteId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voided` | `Boolean` | projection مشتقة من lifecycleStatus؛ لا سلطة مستقلة متناقضة. |
| `isDirty` | `Boolean` | محلي فقط؛ تحدده قاعدة التأكيد/الحماية، ولا يثق في قيمة واردة. |

كل المراجع الاختيارية مثل purchaseOrderId/shipmentId/commissionBeneficiaryClientId تحفظ null عند عدم وجودها؛ لا تنشأ كيانات بديلة لملء الحقول.

### A.2. `InvoiceItemEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:154`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemType` | `ItemType` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemName` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemCategory` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemSkuSnapshot` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitSnapshot` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `quantity` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `buyPrice` | `Double` | توافقي مشتق من `buyPriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `buyPriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sellPrice` | `Double` | توافقي مشتق من `sellPriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `sellPriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `totalPrice` | `Double` | توافقي مشتق من `totalPriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `totalPriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `description` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isOwedToMe` | `Boolean` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `inventoryItemId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `adjustedPurchasePrice` | `Double` | توافقي مشتق من `adjustedPurchasePriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `adjustedPurchasePriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitSellPrice` | `Double` | توافقي مشتق من `unitSellPriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `unitSellPriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitCostAtSale` | `Double` | توافقي مشتق من `unitCostAtSaleMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `unitCostAtSaleMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `lineRevenueSnapshot` | `Double` | توافقي مشتق من `lineRevenueSnapshotMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `lineRevenueSnapshotMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `lineCostSnapshot` | `Double` | توافقي مشتق من `lineCostSnapshotMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `lineCostSnapshotMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `grossProfitSnapshot` | `Double` | توافقي مشتق من `grossProfitSnapshotMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `grossProfitSnapshotMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `costSnapshotStatus` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isDirty` | `Boolean` | محلي فقط؛ تحدده قاعدة التأكيد/الحماية، ولا يثق في قيمة واردة. |

### A.3. `InvoiceDueInstallmentEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:222`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sequence` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `amountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `currencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `dueDate` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.4. `PaymentEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:247`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `clientId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `amount` | `Double` | توافقي مشتق من `amountMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `amountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `supplierAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentExchangeRate` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentExchangeRateDirection` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentExchangeRateTimestamp` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentExchangeRateSource` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalCashAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `historicalFunctionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `realizedFxDifferenceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `legacyCurrencyStatus` | `LegacyCurrencyStatus` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentMethod` | `PaymentMethod` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `note` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paidAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeName` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reversedPaymentId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isDirty` | `Boolean` | محلي فقط؛ تحدده قاعدة التأكيد/الحماية، ولا يثق في قيمة واردة. |

### A.5. `PaymentAllocationEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:310`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `allocatedTransactionAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `historicalFunctionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `realizedFxDifferenceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.6. `RealizedFxEventEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:333`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `historicalFunctionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalCashAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `differenceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `result` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `occurredAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.7. `InvoiceReturnDocumentEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceReturnEntities.kt:44`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `organizationId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `originalInvoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `clientId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `documentType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `settlementMode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `transactionCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalCurrencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `transactionAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reason` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `occurredAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `recordedAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdBy` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdByName` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.8. `InvoiceReturnLineEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceReturnEntities.kt:91`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `returnId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `originalInvoiceItemId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `inventoryItemId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemNameSnapshot` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `quantity` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitTransactionAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `transactionAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitFunctionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `functionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `unitCostAtSaleMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `historicalCostAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `originalPurchaseUnitCostMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.9. `InvoiceReturnPaymentAllocationEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceReturnEntities.kt:135`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `returnId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `paymentId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `allocatedFunctionalAmountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.10. `InventoryMovementEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt:193`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `invoiceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `clientId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `movementType` | `MovementType` | projection مخزنية متحققة من الحركة/الرصيد بنفس منطق المجال؛ لا تصفير افتراضي. يفحص المدى قبل Long→Int. |
| `quantity` | `Int` | projection مخزنية متحققة من الحركة/الرصيد بنفس منطق المجال؛ لا تصفير افتراضي. يفحص المدى قبل Long→Int. |
| `quantityBefore` | `Int` | projection مخزنية متحققة من الحركة/الرصيد بنفس منطق المجال؛ لا تصفير افتراضي. يفحص المدى قبل Long→Int. |
| `quantityAfter` | `Int` | projection مخزنية متحققة من الحركة/الرصيد بنفس منطق المجال؛ لا تصفير افتراضي. يفحص المدى قبل Long→Int. |
| `unitPrice` | `Double` | توافقي مشتق من `unitPriceMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `unitPriceMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `note` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `shipmentId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `writeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `organizationId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `movementKind` | `InventoryMovementKind?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `signedBaseQuantity` | `Long?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceLineId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `commandId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `idempotencyKey` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `postingGroupId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reversesMovementId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `conversionFactorSnapshot` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `occurredAt` | `Long?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `recordedAt` | `Long?` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `serverAcceptedAt` | `Long?` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `serverSequence` | `Long?` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `createdBy` | `String?` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `deviceId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `contractVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.11. `InventoryCostRevisionEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt:291`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `costRevisionId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `organizationId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `itemId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceType` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourceLineId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `revisionKind` | `InventoryCostRevisionKind` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `directPurchaseCostMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `landedCostPerBaseUnitMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `approvedInventoryCostMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `currencyCode` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `exchangeRateSnapshot` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `allocationBasis` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `allocationResidualMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isProvisional` | `Boolean` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reversesCostRevisionId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `commandId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `idempotencyKey` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `costSequence` | `Long?` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `approvedAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `recordedAt` | `Long` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `createdBy` | `String` | يولد/يثبت على السيرفر من السجل المقبول؛ ليس fallback من ساعة الهاتف. يفرض العقد وجوده في الرد عندما يلزم للمطبق. |
| `deviceId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `contractVersion` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.12. `ClientCreditEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/FinanceAndAccessEntities.kt:90`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `clientId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `amount` | `Double` | توافقي مشتق من `amountMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `amountMinor` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `note` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `sourcePaymentId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `createdAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeName` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isDirty` | `Boolean` | محلي فقط؛ تحدده قاعدة التأكيد/الحماية، ولا يثق في قيمة واردة. |

### A.13. `ExpenseEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt:354`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `category` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `item` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `amount` | `Double` | توافقي مشتق من `amountMinor`؛ لا يرسل كأصل عددي ثانٍ. |
| `note` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `date` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `isDirty` | `Boolean` | محلي فقط؛ تحدده قاعدة التأكيد/الحماية، ولا يثق في قيمة واردة. |
| `lifecycleState` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voidedAt` | `Long?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `voidReason` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reversalWriteId` | `String?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `amountMinor` (موروث) | `Long` | أصل القيمة النقدية، يقرأ/يكتب صراحةً دون إعادة تعيينه من Double في init. |

### A.14. `CashReconciliationEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/CashReconciliationEntity.kt:17`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `employeeName` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `openingBalance` | `Double` | wire باسم `openingBalanceMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `totalSales` | `Double` | wire باسم `totalSalesMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `totalRefunds` | `Double` | wire باسم `totalRefundsMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `totalCashIn` | `Double` | wire باسم `totalCashInMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `totalCashOut` | `Double` | wire باسم `totalCashOutMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `expectedBalance` | `Double` | wire باسم `expectedBalanceMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `actualCountedBalance` | `Double` | wire باسم `actualCountedBalanceMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `variance` | `Double` | wire باسم `varianceMinor: Long`؛ يضاف الأصل Minor محليًا ويشتق Double للتوافق فقط. |
| `varianceReason` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `status` | `ReconciliationStatus` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `startedAt` | `Long` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `endedAt` | `Long?` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `notes` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |

### A.15. `CashDenominationEntity`

المصدر: `data/database/src/main/kotlin/com/verto/app/data/local/entity/CashDenominationEntity.kt:18`.

| الحقل المحلي | النوع | حكم النقل في v2 |
|---|---|---|
| `id` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `reconciliationId` | `String` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `denominationValue` | `Double` | wire باسم `denominationValueMinor: Long`، بنفس قاعدة التحويل التاريخي مرة واحدة. |
| `count` | `Int` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |
| `subtotal` | `Double` | wire باسم `subtotalMinor: Long`، بنفس قاعدة التحويل التاريخي مرة واحدة. |
| `isCoin` | `Boolean` | ينقل كما هو بالاسم والنوع والقيمة المسجلة، مع تحقق المؤسسة/المراجع والقواعد الدلالية. |


### A.16. جرد الصندوق وبقية owner310

لقطة CASH_RECONCILIATION تضم session كاملة أعلاه **وقائمة فئات النقد CashDenomination** المرتبطة بها، لا الحالة والتاريخ فقط. يحدد الانضمام بهوية جلسة الجرد الأصلية؛ لا يقرأ counts جديدة أثناء retry. القيم المغلقة هي لقطة الجلسة؛ لا يعيد المرسل حسابها من حركات نشأت بعدها.

لبقية DTOs القائمة التي يتناولها تجميد owner310، لا يتغير مخططها الدلالي في هذا العقد: ينقل إنشاء `materialization` و`purchaseRequest` الموجود من وقت الإرسال إلى داخل معاملة المنتج، ثم تثبت أسماء الحقول والأنواع من دوال المصنع/المطبق الحالية في ملف schema التسليم. GOODS_RECEIPT تشمل الطلب وبنوده والإيصال وبنوده والمرفقات؛ PURCHASE_MATCH تشمل match/lines/allocations؛ PURCHASE_PAYMENT_OVERRIDE تشمل override المرجعية. لا يعتبر نقل header وحدها لقطة كاملة. أي اختلاف قائم بين طرفي عقد أحدها يسجل CONTRACT_FIELD_MISMATCH ويمنع تفعيله؛ لا يبتكر المنفذ حقولًا أو يملأها من قوائم شاشة أخرى.

تثبت `SYNC_OWNERSHIP_MATRIX.csv` أيضًا الحقول الدلالية التي تمسها النية لأغراض الحماية حتى إذا لم تغير serializer. هذا شرط تغطية للمنتجين الحاليين، وليس تكليفًا بتصميم ميزات شراء جديدة.

## ملحق B — مواقع التعديل الأساسية في المصدر المثبت

أرقام السطور التالية تخص S6 قبل الإصلاح؛ يسجل المنفذ السطور الجديدة في خريطة التسليم.

| الملف | نقطة العمل المطلوبة |
|---|---|
| `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt` | drainOrchestration: نتائج M03، staging/promotion، health والتوقيت والاكتمال. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManagerPorts.kt` | ربط الخدمات الجديدة بدل الإبقاء على منافذ قديمة لا تعمل. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt` | فصل انتهاء العامل عن نجاح تسليم البيانات وحفظ الإلغاء. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2IntentRepairCoordinator.kt` | إزالة إثبات lineCount/ACK العام واعتماد اللقطة والـCAS والجرد القابل للاستئناف. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2MigrationCoordinator.kt` | مصادر M03 والتصنيف والسجل الجديد؛ لا تعديل fingerprint v1 تاريخيًا. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncMigrationPlanner.kt` | آلة حالات صريحة؛ الحالة غير المعروفة ليست MIGRATED افتراضيًا. |
| `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt` | لقطة مالية كاملة وختمها في نهاية معاملة الكتابة. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt` | packets والأجيال والrefs/manifest داخل معاملة المنتج. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedFinancialOwner310Route.kt` | إلغاء قراءة mutable business rows عند retry. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedStrongerSourceFactory.kt` | DTO v2 وربط fact identities بلا إعادة إنشاء. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/push/SyncV2PushCoordinator.kt` | توجيه batches والمالكين والمرفقات وحالة انتظار شاملة. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt` | مؤشرا الاستلام/التطبيق، المجموعات الكبيرة والوارد الدائم. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt` | FinancialMaterializer الفعلي، DTOات الحركة والتكلفة/الائتمان وسياسة المصروف. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt` | حماية المالك الحقيقي والتطبيق المادي للنسخ. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt` | حفظ pending ودقة bootstrap/versions وإعادة رمي الإلغاء. |
| `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt` | استبدال خريطة الحماية المتناقضة بسجل الملكية الواحد. |
| `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOrchestrationDao.kt` | احتساب nextWake بما فيه leases، وpending protection بدلاً من حارس صفحة شامل. |
| `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOutboxDao.kt` | مصدر نسخ دائم وربط الإيصالات/الحجز. |
| `data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncProducerV307Dao.kt` | قراءة/claim/update نقل الملفات، لا count/insert فقط. |
| `data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt` | لقطة وحركة فرق نقدي وbatch/history ثابتة مع الحفاظ على سياسة التعديل. |
| `data/operations/src/main/kotlin/com/verto/app/data/repository/CashReconciliationRepository.kt` | لقطة جلسة كاملة وقيمها/فئاتها أثناء الكتابة. |
| `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsShipmentStoreAdapters.kt` | ربط transfer والmetadata وهوية إصدار الملف. |
| `app/src/main/kotlin/com/verto/app/feature/integration/optimal/bridge/OptimalInvoiceIntegrationOutboxAdapter.kt` | تثبيت تصنيف raw aggregate + operation + gate وربط المالك المالي دون ازدواج. |
| `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt` | قدرات وDTO/receipts/cursors الإصدار 2 مع أسماء endpoints غير الملتبسة. |

## ملحق C — بصمات مصادر الوثيقة

| الملف | SHA-256 |
|---|---|
| `Verto-425.zip` | `cb0a1956952376d75bfeb2d3ec71b0e06700024d5b95cd44f6b09db96855e1a0` |
| `VERTO_SYNC_DIAGNOSTIC_REPORT_2026-09-09.md` | `1524eb4cf588f73ebd9bc5a28e69750fdda9ff988663f85b0a3f21f691a2a403` |
| `VERTO_SYNC_DIAGNOSTIC_REPORT_2026-09-09 (1).md` | `40e7338f8c86799cec54effeb76ed7027ad500c1d90901508b84380fb82e8362` |
| `Verto-425-Sync-Audit-AR.md` | `3808948fd5613f48ea9256ec3b6022ba1ff9318a658616b3735f3d355a720644` |
| `Verto-425-sync-audit-ar.md` | `05c8f4a7e6781aa804b598f0659e3bfe28f326072b4115280ed2961975401400` |
| `Verto-425-sync-audit.md` | `07d31494a8db6ed8e9aaf8c80433ed32c3b9da0ce42c51e207de39b5a4e7b9f9` |


حزم الأدلة المرفقة بالتقارير تستخدم لإعادة تشغيل حالات المصدر، ولا يستبدل هذا العقد سجلات الاختبارات السابقة أو يدعي تنفيذها مجددًا أثناء الكتابة. الروابط إلى logcat/Room داخل التقارير ليست دليل توفر ملفاتها هنا.

---

**نص تكليف المنفذ:** نفّذ هذا العقد على المصدر المثبت، مع حفظ البيانات والهويات والأدلة. لا تغيّر حلًا حسمه العقد، ولا تتجاوز عائقًا يطلب إثباتًا. سلم الكود والتغييرات واختبارات كل R/T، وميّز بدقة PASS عن FAIL وBLOCKED وNOT_RUN. نجاحك يقاس بصحة البيانات واستعادتها وعدم تكرار آثارها، لا بعبارة «تمت المزامنة».
<!-- SOURCE_CONTRACT_END -->
