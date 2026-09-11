# تقرير فحص «خطأ غير متوقع» بعد تسجيل الدخول

## معلومات الفحص

- التاريخ: 2026-08-24
- الجهاز: `BRP_NX1`
- معرّف الجهاز: `AB3SVB5319000909`
- حزمة التطبيق: `com.verto.app`
- مصدر الأدلة: Logcat بعد تنظيف السجل ثم تنفيذ محاولة تسجيل الدخول
- حالة الجهاز بعد الفحص: انفصل بعد اكتمال التقاط السجل

## الملخص التنفيذي

وصل طلب تسجيل الدخول إلى مسار النجاح، وبدأ التطبيق أعمال ما بعد المصادقة، ومنها جدولة المزامنة. لكن شاشة الدخول عرضت «خطأ غير متوقع» لأن الاستثناء الصادر أثناء `completeAuthentication` يُلتقط داخل `AuthViewModel` ثم يُحوّل إلى رسالة عامة من دون تسجيل الاستثناء الأصلي في Logcat.

كشف Logcat أيضاً خللاً مؤكداً ومستقلاً في المزامنة: بدأت مهمتا `SyncWorker` متقاربتان، ثم تعارضتا على حالة checkpoint الدائمة وظهرت الرسالة:

```text
sync checkpoint does not belong to the active run
```

هذا الخلل مهم ويجب إصلاحه، لكنه لا يثبت وحده أنه السبب المباشر لرسالة شاشة الدخول؛ تشغيل المزامنة مجدول بصورة غير متزامنة، بينما رسالة الشاشة تنتج من فشل متزامن داخل `completeAuthentication`.

## التسلسل الزمني من Logcat

```text
08-24 14:15:17.436  WM-WorkerWrapper: Starting work for com.verto.app.data.sync.SyncWorker
08-24 14:15:17.969  WM-WorkerWrapper: Starting work for com.verto.app.data.sync.SyncWorker

08-24 14:15:30.974  SyncManager: Sync operation failed type=IllegalStateException
    reason=IllegalStateException: sync checkpoint does not belong to the active run

08-24 14:15:30.991  WM-WorkerWrapper: Worker result SUCCESS

08-24 14:15:31.357  SyncManager: Sync operation failed type=IllegalStateException
    reason=IllegalStateException: sync checkpoint does not belong to the active run

08-24 14:15:31.558  SyncManager: Sync operation failed type=IllegalStateException
    reason=IllegalStateException: sync checkpoint does not belong to the active run

08-24 14:15:31.558  SyncManager: Sync run failed with 3 operation errors
08-24 14:15:31.559  SyncManager: Sync failed type=IllegalStateException
    reason=IllegalStateException: تعذّرت مزامنة بعض البيانات، ستُعاد المحاولة تلقائياً

08-24 14:15:31.590  WM-WorkerWrapper: Worker result FAILURE
```

## تحليل رسالة شاشة الدخول

ينفذ `AuthViewModel.login` ما يلي بعد نجاح `authGateway.login`:

```kotlin
val completion = runCatching {
    sessionCoordinator.completeAuthentication(user.organizationId)
}
```

وعند فشل هذا الاستدعاء، يُستبدل الاستثناء مباشرة بالرسالة العامة:

```kotlin
it.copy(isLoading = false, error = AuthUiMessage.Unknown)
```

الموضع:

```text
feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/AuthViewModel.kt:51-61
```

لا يوجد في هذا الفرع تسجيل لـ `completion.exceptionOrNull()`؛ لذلك لا يحتوي Logcat الملتقط على نوع الاستثناء المباشر أو stack trace الخاص برسالة الشاشة.

يحتوي `completeAuthentication` على الخطوات التالية:

1. إلغاء أعمال المزامنة السابقة.
2. إيقاف Realtime.
3. تجهيز جلسة المزامنة للمؤسسة.
4. جدولة المزامنة الدورية والفورية.
5. تشغيل رفع FCM token.
6. تشغيل تحديث الدور والصلاحيات.

الموضع:

```text
app/src/main/kotlin/com/verto/app/feature/auth/integration/DefaultAuthSessionCoordinator.kt:26-34
```

وجود مهمتي `SyncWorker` في السجل يعني أن التنفيذ وصل إلى مرحلة جدولة المزامنة على الأقل. أما الخطوة التي رمت الاستثناء المتزامن بعد ذلك فلا يمكن إثباتها من السجل الحالي بسبب ابتلاع الاستثناء في `AuthViewModel`.

## تحليل تعارض المزامنة

تُحفظ هوية تشغيل المزامنة في DataStore باستخدام:

- `KEY_SYNC_RUN_ORG_ID`
- `KEY_SYNC_RUN_ID`
- `KEY_SYNC_RUN_START_REVISION`
- `KEY_SYNC_RUN_COMPLETED_KEYS`

وعند تسجيل اكتمال عملية، يتحقق الكود من تطابق `runId` الحالي:

```kotlin
check(prefs[KEY_SYNC_RUN_ORG_ID] == orgId && prefs[KEY_SYNC_RUN_ID] == runId) {
    "sync checkpoint does not belong to the active run"
}
```

الموضع:

```text
data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt:230-244
```

يحتوي `SyncManager` على `Mutex` لمنع تشغيل مزامنتين بالتوازي، لكن الصنف نفسه غير معلّم بـ `@Singleton`:

```kotlin
class SyncManager @Inject constructor(...) {
    private val syncMutex = Mutex()
}
```

الموضع:

```text
data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt:25-32
```

وبالتالي تستطيع Hilt إنشاء أكثر من نسخة من `SyncManager`. كل نسخة تمتلك `Mutex` مختلفاً، بينما جميع النسخ تشترك في checkpoint واحد داخل DataStore. هذا يفسر كيف تبدأ أكثر من مزامنة وتستبدل إحداهما `runId` الذي تعتمد عليه الأخرى.

## النتائج

### نتائج مؤكدة

1. مسار المصادقة وصل إلى أعمال ما بعد تسجيل الدخول وجدولة المزامنة.
2. الاستثناء الأصلي الخاص برسالة «خطأ غير متوقع» لا يُسجل في `AuthViewModel`.
3. بدأت مهمتا مزامنة بفارق يقارب 533 مللي ثانية.
4. فشلت المزامنة بسبب عدم تطابق checkpoint مع التشغيل النشط.
5. `SyncManager` غير محدد النطاق رغم احتوائه على قفل يفترض أنه عام للمزامنة.

### استنتاج مرجّح

غياب `@Singleton` عن `SyncManager` يسمح بوجود أقفال مستقلة وعمليات متزامنة تتنافس على checkpoint المشترك. هذا هو التفسير الأقوى لأخطاء المزامنة المسجلة.

### ما لم يثبت بعد

لم يُحدد الاستدعاء الدقيق داخل `completeAuthentication` الذي أدى إلى رسالة شاشة الدخول؛ الاستثناء ابتُلِع قبل تسجيله. كما أن خطأ المزامنة المجدولة لا ينتقل عادةً إلى `AuthViewModel`، لذلك يجب عدم اعتباره السبب المباشر للرسالة من دون التقاط إضافي بعد إضافة logging.

## الإصلاحات المقترحة

### أولوية 1: إظهار السبب الحقيقي لخطأ الدخول في السجل

- تسجيل `completion.exceptionOrNull()` داخل `AuthViewModel` مع stack trace آمن.
- عدم تسجيل البريد أو كلمة المرور أو التوكنات.
- إضافة اسم المرحلة داخل `completeAuthentication` أو تغليف كل خطوة برسالة تشخيصية واضحة.

### أولوية 2: عدم إفشال تسجيل الدخول بسبب خدمات ثانوية

- اعتبار FCM وتحديث الدور والصلاحيات وجدولة العمل خدمات best-effort بعد تثبيت الجلسة الأساسية.
- عزل فشل كل خدمة ثانوية وتسجيله بدلاً من إعادة المستخدم إلى خطأ دخول عام.
- إبقاء فشل `prepareSessionForOrg` فقط كفشل حرج إذا كان استمرار الجلسة بعده غير آمن.

### أولوية 3: إصلاح سباق المزامنة

- جعل `SyncManager` ذا نطاق واحد، مثل إضافة `@Singleton`، حتى تتشارك جميع الاستدعاءات في `syncMutex` نفسه.
- مراجعة إلغاء وجدولة WorkManager؛ الإلغاء غير المتزامن قد يسمح لعمل قديم بالاستمرار مؤقتاً.
- الحفاظ على فحص session epoch داخل التشغيل، بما في ذلك قبل حفظ كل checkpoint عند الحاجة.

### أولوية 4: الاختبارات

- اختبار يثبت أن حقن `SyncManager` من مستهلكين مختلفين يعيد النسخة نفسها.
- اختبار تشغيل مهمتي مزامنة متزامنتين وعدم استبدال `runId` النشط.
- اختبار أن فشل FCM أو تحديث الدور أو الصلاحيات لا يعرض خطأ تسجيل دخول بعد نجاح المصادقة.
- اختبار أن الاستثناء الحرج في تهيئة الجلسة يُسجل ويُعرض برسالة مناسبة.

## ملاحظة الخصوصية

لم يتضمن هذا التقرير بيانات اعتماد، كلمات مرور، access tokens أو refresh tokens. تم الاكتفاء بالرسائل التشخيصية ومعرّفات العمل اللازمة للتحليل.
