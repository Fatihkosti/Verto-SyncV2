# تقرير تنفيذ M07 — توحيد المنسق والجدولة والواجهة

التاريخ: 2026-09-09  
المصدر المنفذ عليه: `Verto-sync-v2-m06-final.zip`  
SHA-256 للمصدر: `abff7cbe3b1202b798891a461142a3864f9d8800cf4ae8c2393da8831dd65f72`  
الخطة: `Verto-v2-cutover-plan-ar.md`  
SHA-256 للخطة: `5530bb393556d753e4877ce25d58b5b0188ee708613a6f6a52a514538fb9fe41`

## الحكم التنفيذي

**M07 IMPLEMENTED — STATIC GATE PASS (18/18).**  
**الإغلاق الرسمي للمرحلة: NOT YET CLOSED** لأن Gradle/runtime tests لم تبدأ في هذه البيئة؛ Gradle 8.9 غير مخزّن ومحاولة wrapper فشلت أثناء تنزيل التوزيعة بسبب `UnknownHostException: services.gradle.org`.

لا توجد ادعاءات PASS لاختبارات لم تُنفذ.

## نتائج التدقيق قبل التعديل

1. `SyncManager.requestOrRun` كان يختار V2 أو Legacy حسب rollout، لذلك لم يكن V2 منسقًا وحيدًا.
2. `SyncWorker` كان يملك الفرع نفسه ويمكنه تشغيل `fullSync` القديم.
3. شاشة المزامنة كانت تعرض نجاحًا مكتملًا فور قبول/جدولة الطلب.
4. بدء الجلسة والعودة للتطبيق لم يكونا موحدين على durable V2 request.
5. تبديل المؤسسة/تسجيل الخروج كان يمكن أن يصل إلى مسح المحلي دون gate صريح يحمي V2 work غير المؤكد.
6. التقرير المحفوظ لم يفصل pending / requires-review / rejected ولم يعلن phase المنسق بوضوح.

## التنفيذ

### 1. منسق V2 واحد

- إزالة runtime routing عبر `requestOrRun` و`usesV2Orchestration`.
- إضافة عقد واضح `SyncManager.request(reason): Result<SyncRequestReceipt>`.
- `SyncRequestReceipt` يفرق بين **قبول الطلب الدائم** وبين **اكتمال المزامنة**، ويعرض generation و`wakeEnqueued`.
- فشل wake بعد حفظ generation لا يلغي الطلب المقبول.
- `SyncWorker` يشغل `drainOrchestration` فقط؛ لا يوجد استدعاء إنتاجي لـ`syncManager.fullSync`.

### 2. توحيد نقاط البدء

| نقطة البدء | المسار بعد M07 |
|---|---|
| شاشة المزامنة | `SyncOperations.requestSync -> SyncManager.request(MANUAL)` |
| الرئيسية | `FOREGROUND / MANUAL / OUTBOX_WRITE -> SyncManager.request` |
| الفواتير | post-commit + compatibility bridge -> durable V2 request |
| الإعدادات | `syncNow -> SyncManager.request(MANUAL)` |
| بدء/استعادة الجلسة | `STARTUP -> SyncManager.request` |
| العودة للتطبيق | `VertoApplication ActivityLifecycleCallbacks -> FOREGROUND` |
| Realtime | `requestSync(scope, REALTIME)`؛ إشارة تسريع فقط |
| الجدولة الدورية | Worker يسجل `ensurePeriodicIntent` ثم يدخل نفس drain |
| Party / Team observations / Optimal | `OUTBOX_WRITE` إلى المنسق نفسه |

### 3. عدم ضياع الطلب أثناء Drain

الـdrain يقرأ `requestedGeneration` قبل pass ثم يعيد القراءة بعد Push/Pull. إذا وصل طلب أحدث أثناء التشغيل لا يُعلن drained للجيل القديم؛ يبدأ pass آخر. أضيف اختبار صريح لهذا السباق.

### 4. Scope / epoch وتبديل الجلسة

- كل drain يتحقق من organization + user + session epoch.
- scope قديم بعد تغيير epoch يفشل مغلقًا قبل Push.
- `prepareSessionForOrg` يلغي أعمال الجدولة القديمة، ثم يمنع تبديل المؤسسة إذا بقي عمل V2 غير مؤكد للمؤسسة السابقة.
- `clearLocalData` وlogout يمنعان المسح الصامت عند وجود pending/review/rejected.
- أضيفت رسائل مستخدم قابلة للتصرف للحالتين `SYNC_PENDING_SESSION_END` و`SYNC_PENDING_ORG_SWITCH`.

### 5. التقرير والواجهة

أضيفت مراحل المنسق:

`IDLE / REQUESTED / RUNNING / CONTINUATION_PENDING / NEEDS_REVIEW / AUTH_BLOCKED / COMPLETED / FAILED`.

وأضيفت إلى التقرير/Health والدرج أعداد مستقلة:

- `pendingCount`
- `requiresReviewCount`
- `rejectedCount`

الواجهة لم تعد تقول «تمت المزامنة بنجاح» عند wake؛ أصبحت تقول «تم تسجيل طلب المزامنة»، بينما نجاح التشغيل الحقيقي يصدر فقط عند `COMPLETED`.

## ما بقي Legacy فعليًا

تنفيذ `SyncManager.fullSync` القديم ما زال موجودًا داخليًا ومعلّمًا Deprecated **للحذف في M09**، لكن لا يوجد له مستدعٍ إنتاجي بعد M07. كذلك بقيت أسماء/تجريدات توافق قديمة وفق خطة M09؛ لم تُحذف خارج نطاق هذه المرحلة.

## اختبارات M07 المضافة

في `SyncManagerTest` أضيف/ثبت ما يلي:

- durable request قبل wake، وفشل wake لا يفقد الطلب.
- كل public request يمر V2 ولا يشغل Legacy participant operations.
- طلب يصل أثناء drain لا يضيع، ويُستنزف الجيل الأحدث.
- scope قديم بعد تغيير epoch لا يصل إلى Push.
- تبديل المؤسسة يُمنع مع unconfirmed work.
- manual drain يعمل حتى مع Realtime disabled.
- terminal push issue يظهر `NEEDS_REVIEW` ولا يسجل نجاحًا كاذبًا.

## التحقق المنفذ

### M07 static gate

الأمر:

`python3 tools/verify_sync_m07.py`

النتيجة: **PASS 18/18**.

يشمل التحقق: durable request contract، Worker V2-only، نقاط البدء، UI acceptance semantics، session safeguards، report/health counts، وجود closure tests، وصفر مستدعين إنتاجيين لـ`requestOrRun` أو `syncManager.fullSync`.

الدليل: `evidence/m07/M07_STATIC_VERIFICATION.txt`.

### Kotlin parser smoke check

تم تمرير الملفات المعدلة إلى `kotlinc` دون classpath المشروع. لم تظهر parser errors من نوع `expecting/unexpected tokens/missing }`. هذه **ليست عملية build** ولا تُحسب PASS للترجمة.

### Gradle runtime gate

الأمر المجرب:

`./gradlew :data:sync:testDebugUnitTest --no-daemon`

النتيجة: **NOT RUN / ENVIRONMENT BLOCKED** قبل بدء Gradle، لأن wrapper حاول تنزيل `gradle-8.9-bin.zip` وفشل بـ`UnknownHostException: services.gradle.org`.

الدليل: `evidence/m07/M07_GRADLE_ATTEMPT.txt`.

## مطابقة شرط إغلاق M07 في الخطة

| شرط M07 | الحالة |
|---|---|
| كل نقاط البدء تصل إلى المنسق نفسه | PASS static 18/18 gate؛ runtime NOT RUN |
| طلب أثناء تشغيل آخر لا يضيع | اختبار مصدر مضاف + منطق generation مثبت؛ test execution NOT RUN |
| العمل القديم لا يطبق على جلسة جديدة | epoch validation + اختبار مصدر؛ test execution NOT RUN |
| إغلاق Realtime لا يمنع التقارب | drain لا يعتمد عليه + اختبار مصدر؛ test execution NOT RUN |
| لا نجاح UI فور wake | PASS static |
| pending/rejected/review ظاهر في Health/UI | PASS static |
| logout/org switch لا يمسح غير المؤكد | PASS static؛ runtime NOT RUN |

## الملفات الأساسية المعدلة

- `data/sync/.../SyncManager.kt`
- `data/sync/.../SyncWorker.kt`
- `data/sync/.../SyncOperations.kt`
- `data/sync/.../SyncReliability.kt`
- `data/sync/.../SyncManagerPorts.kt`
- `data/sync/.../recovery/SyncHealthSnapshot.kt`
- `data/database/.../SyncRecoveryDao.kt`
- `app/.../VertoApplication.kt`
- `app/.../feature/auth/integration/DefaultAuthSessionCoordinator.kt`
- `app/.../feature/settings/bridge/SettingsOperationsGatewayAdapter.kt`
- `app/.../feature/sync/presentation/SyncViewModel.kt`
- `app/.../ui/components/NavigationDrawerContent.kt`
- `app/.../ui/screens/home/HomeViewModel.kt`
- bridges الخاصة بـInvoice / Party / Optimal / Team Observations
- `data/sync/src/test/.../SyncManagerTest.kt`
- `tools/verify_sync_m07.py`

## الخلاصة

التنفيذ الكودي لـM07 مكتمل: V2 أصبح مسار التشغيل الوحيد لنقاط البدء الإنتاجية، durable request منفصل عن completion، Scope محمي بالـepoch، والواجهة تعرض الحالة الفعلية للمعلّق/المراجعة/الرفض. لا يُعلن **M07 CLOSED** حتى تنجح Gradle/runtime tests على بيئة قادرة على البناء.
