Warning: truncated output (original token count: 94931)
Total output lines: 2866

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
EXECUTION_BRANCH: b07-b08-release-20260912
LAST_WORKING_PRODUCT_COMMIT: ad9c2887a33fafb3de45abd7a7cd58e1a710c63d
LAST_VERIFIED_PRODUCT_COMMIT: ad9c2887a33fafb3de45abd7a7cd58e1a710c63d
LAST_PRODUCT_TREE_SHA256: 158fccae5d886f47b1101453d64406522512e8666a2b5cbcf0be6cdfad5a9711
WORKTREE_CHECKPOINT: B07-B08-V02
OVERALL_STATUS: IN_PROGRESS_WITH_BLOCKERS
EXECUTION_MODE: EXECUTION_TREE_HASH_MODE
CURRENT_SESSION: B08
CURRENT_TASK: B08.05
CURRENT_VISIT: 2
LAST_COMPLETED_SESSION: B08
LAST_CHECKPOINT_ID: B07-B08-V02
NEXT_SESSION: B09
NEXT_TASK: B09.01
NEXT_ACTION: 'B09.01: أعد تشغيل بوابة B09 المالية الكاملة على بيئة Actions/Android المتاحة الآن، ثم قيّم migration98→الحالي وFinancialMaterializerV2 وT11–T13 دون وراثة PASS من بوابة B08.'
NEXT_ACTION_PRECONDITIONS: 'ابدأ من فرع b07-b08-release-20260912 بعد commit التسليم؛ استخدم أوامر B09/V02 الموثقة وبيئة Android المجهزة؛ B07/B08 مغلقتان، لكن Txx المالية وجهازان لا ترث PASS.'
RESUME_FROM: 'B07-B08-V02: G-B07 وG-B08 PASS على Supabase الفعلي وRoom؛ تطبيق/ترحيلات/بوابات العميل fail-closed؛ تشغيل Actions النهائي موثق في تقريري V02.'
COMPLETED_SESSIONS: 7
TOTAL_SESSIONS: 22
COMPLETED_TASKS: 38
TOTAL_TASKS: 110
DEFERRED_CONDITIONAL_TASKS: 0
TESTS_PASS: 3
TESTS_FAIL: 0
TESTS_BLOCKED: 0
TESTS_NOT_RUN: 47
TESTS_RECHECK_REQUIRED: 0
ACTIVE_BLOCKER_IDS: [B02-BLK-01, B02-BLK-02, B10-BLK-02, B11-BLK-01, B12-BLK-02, B13-BLK-02]
WORK_IN_FLIGHT: []
BACKUP_GATE: WAIVED_BY_USER_FOR_CURRENT_EXPERIMENTAL_SERVER_DATA_ONLY
LIVE_SERVER_CONTRACT_GATE: PASS_READ_ONLY_CATALOG_EXPORT
TEST_ENVIRONMENT_GATE: PASS_CONNECTED_EXPERIMENTAL_SUPABASE_FOR_B07_B08
SIGNING_GATE: PASS_PRIOR_RELEASE_KEY_ACTIONS_PROTECTED_INPUTS_INCOMPLETE
LIVE_WRITE_AUTHORIZATION: RECORDED_B07_B08_2026_09_12
RELEASE_DELIVERY_AUTHORIZATION: RECORDED_B07_B08_2026_09_12
USER_DEVICE_CASE_STATUS: NOT_VERIFIED
PRODUCTION_DEPLOYMENT_STATUS: B07_B08_MIGRATIONS_APPLIED_TO_CONNECTED_EXPERIMENTAL_PROJECT
LAST_UPDATED_AT: '2026-09-12'
LAST_UPDATED_BY: 'B07-B08-V02؛ G-B07/G-B08 PASS بأدلة PostgreSQL/Room/Actions؛ T14/T16/T47 PASS فقط، وبقية Txx لا ترث النتيجة'
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
| [B07](#b07) | E2 | خادم المجموعات والإيصالات وعدم تكرار الأثر | B02، B05، B06 | DONE | 5/5 | PASS | B07-V02؛ concurrent×2 + replay×10، ذرية/إيصالات/عميل مختوم |
| [B08](#b08) | E2 | قراءة الخادم القانونية وDelta وBootstrap | B07 | DONE | 5/5 | PASS | B08-V02؛ aggregate/delta/bootstrap/limits + SQL→Room للأنواع الثلاثة |
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
| E2 | B06–B08 | round-trip PostgreSQL وإسقاط الرد ومنع ازدواج الأثر تجتاز. | PASS | B06 DTO/producer PASS؛ B07-V02 ذرية/replay/receipt + recovery؛ B08-V02 PostgreSQL/Room والحدود والنطاق PASS |
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
| B07.01 | نفذ capabilities وresolve scope وbatch/receipts v2 بالأسماء والمدخلات في C§18.1، مع صلاحيات فعلية مشتقة من Auth وتعريفات منشورة ببصمات. | مطابقة schema typed وSQL الحقيقي؛ لا principal يختاره العميل ولا اسم RPC موجود يستبدل بصمت. | DONE | B07/V01 + V02؛ تعريفات Supabase الفعلية وAuth/RLS/grants |
| B07.02 | نفذ حساب hash من النص قبل JSONB والتحقق من العضو والمجموعة والblobs والتبعيات والنسخ وحجم المجموعة. | reject للbody/baseVersion المختلف مع الهوية نفسها؛ غياب member/blob/أثر يمنع commit المجموعة. | DONE | B07/V01؛ raw-text hash وT16/T47 PASS |
| B07.03 | نفذ قفل المؤسسة والجذور بترتيب ثابت وإعادة فحص الإيصالات تحت القفل؛ خصص revisions ونسخًا وإيصالات مع الحقائق في معاملة واحدة. | اختبار إرسالين متزامنين وتكرار 10 مرات؛ لا watermark يتجاوز معاملة لم تلتزم ولا إيصال اقتصادي جزئي. | DONE | B07/V02؛ جلستان مستقلتان متزامنتان + 10 replays؛ receipt/fact واحد |
| B07.04 | وحّد أصحاب الآثار والمشغلات مع business identities ومفاتيح عدم التكرار؛ نفذ reconciliation للحقائق الموجودة دون إعادة عملية البيع. | دفعة/مخزون/نقد/عمولة لا تتضاعف؛ duplicates تاريخية غير محسومة تمنع القيود والنشر ولا تُحذف تلقائيًا. | DONE | migrations B07 الفعلية؛ قيود الحقائق/الإيصالات وduplicate fail-closed |
| B07.05 | اربط مرسل العميل بالمجموعات المختومة والنيات المستقلة، وأعد إيصالات FOUND/NOT_FOUND ضمن horizon/OUTCOME_UNKNOWN وفق السياج؛ شغل اختبارات SQL الفعلية وإسقاط الرد. | T14–T16 وT47 بالمسار الحقيقي مع سجل الطلب والإيصال؛ اختبارات RLS/receipt horizon/capabilities؛ لا إرسال عضو batch منفردًا. | DONE | SyncV2AtomicBatchPushEngine + FrozenMutationStore؛ B07/V02 وActions |

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
| B08.01 | نفذ read aggregate والـDelta بDTO قانوني من السجلات المقبولة لا p_payload الخام؛ أعد حقول sequence/recordedAt/serverAcceptedAt من مصدرها الفعلي. | خرائط الحركة والتكلفة والائتمان تطابق C§11 والملحق؛ لا fallback من ساعة الهاتف أو صفر. | DONE | B08/V01 + V02؛ accepted-record DTO وauthoritative ordering |
| B08.02 | نفذ المجموعات الكاملة وtouchedKeys/dependsOnTransactionIds وopaque cursors؛ أعد projection manifest حسب رؤية المستخدم دون كشف مفاتيح مخفية. | المجموعة المرئية كاملة أو SCOPE_DEPENDENCY_UNAVAILABLE؛ اختبار رؤية متعددة المؤسسات داخل SQL. | DONE | B08/V01؛ 88 changes/4 complete groups، opaque cursor، tenant denial |
| B08.03 | نفذ جلسة Bootstrap ثابتة مع highWatermark/Delta token وختم counts/digest/coverage، وصفحات الكيانات المالية الكاملة ونسخها. | اختبارات تغير الخادم بين الصفحات لا تغير لقطة الجلسة؛ لا آخر حدث مالي مختصر بدل snapshot أعمال. | DONE | B08/V01؛ 1179 rows = 1000+179، coverage35، digest/seal |
| B08.04 | طابق قياس 2,097,152 bytes وsoftLimit=1000؛ اختبر مجموعة 999/1000/1001 والحد القانوني وفوقه على واجهة السحب. | لا skip أو loop أو split تجاري للمجموعة القانونية؛ خطأ حجم صريح عند التجاوز. | DONE | B08/V01؛ 999/1000/1001 والحد 2MiB/+1 PASS |
| B08.05 | عدل مستقبلات الحركة والتكلفة والائتمان للDTO القانوني، وشغل encode→PostgreSQL→decode→Room؛ اربط handshake/registries/routes دون fallback قديم. | T19 كامل وT20 للحقول/الأرقام؛ اختبارات scope/capabilities. المطبق المالي العام يأتي في B09 وليس inbox بديلًا عنه. | DONE | UnifiedStrongerSyncChangeApplier + InventorySyncDao؛ SQL + focused Room gate B08/V02 |

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

**نهاية هذه الزيارة:** طبق بروتوكول 3.4 وحدّث صف الجلسة والمهام والاختبارات والع…34931 tokens truncated…ق A يسرد جميع حقول سجلّي الحركة والتكلفة؛ ليست هذه القائمة مبررًا لإسقاط حقل آخر.

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
