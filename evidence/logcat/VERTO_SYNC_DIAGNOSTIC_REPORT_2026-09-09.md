# تقرير تشخيص فشل مزامنة Verto

التاريخ: 2026-09-09  
الحزمة: `com.verto.app`  
الإصدار: `1.0` (versionCode `1`)  
الجهاز: Honor BRP-NX1، ADB serial `AB3SVB5319000909`

## ما تم تنفيذه

- أضيف تسجيل الاستثناء الكامل في `SyncWorker` و`SyncManager` و`SyncViewModel` فقط.
- لم تتغير المعمارية أو الإصدارات أو الاعتماديات أو وظائف المزامنة.
- بُنيت نسخة Release بالأمر المطلوب:
  `./gradlew assembleRelease --offline --build-cache`
- نجح البناء: `BUILD SUCCESSFUL in 4m 36s`، مع `91 executed` و`1126 up-to-date`.
- استُخدم ملف التوقيع المرفق، وتحققت صلاحية توقيع APK عبر APK Signature Scheme v2.
- ثُبّت التحديث باستخدام `adb install -r`، ونتيجة التثبيت `Success`.
- لم تُمسح بيانات التطبيق، ولم يحدث إلغاء تثبيت أو تسجيل خروج.
- بدأ تسجيل Logcat دون مسح السجل السابق، ثم أُعيدت محاولة المزامنة بعد ضغط «مزامنة الآن».
- بُني وثُبّت تحديث تشخيصي إضافي لتجميع أسباب M03، باستخدام نفس أمر البناء والتوقيع، دون تغيير منطق الحماية أو البيانات.

## الدليل الزمني

في محاولة التشخيص الحالية ظهر تحميل جلسة Supabase بنجاح عند `21:33:59.155`، ثم بدأ `SyncWorker` عند `21:33:59.336`.

أول فشل مثبت:

```text
21:34:06.889 SyncManager: Sync drain failed
21:34:06.889 SyncManager: java.lang.IllegalStateException: M03_LOCAL_MIGRATION_REVIEW_REQUIRED:54
21:34:06.889 SyncManager:     at I4.y0.c(SourceFile:1498)
21:34:06.889 SyncManager:     at I4.h0.v(SourceFile:12)
21:34:06.889 SyncManager:     at ba.a.n(SourceFile:8)
21:34:06.889 SyncManager:     at Eb.N.run(SourceFile:109)
21:34:06.889 SyncManager:     at Lb.a.run(SourceFile:127)
21:34:07.798 SyncWorker: Sync execution failed
21:34:07.798 SyncWorker: java.lang.IllegalStateException: M03_LOCAL_MIGRATION_REVIEW_REQUIRED:54
21:34:07.812 WM-WorkerWrapper: Worker result FAILURE
```

تكرر الاستثناء نفسه عند `21:34:09.872` و`21:34:16.036`، وتبعته نتيجة WorkManager هي `FAILURE` في كل مرة.

## السبب المثبت

السبب محلي داخل مسار المزامنة: في `SyncManager.drainOrchestration` تُحسب `migrationReviewCount`، ثم يمنع الشرط استمرار الاسترداد عندما تكون هناك مراجعة ترحيل مطلوبة:

```kotlin
check(preflightRecovery == null || migrationReviewCount == 0) {
    "M03_LOCAL_MIGRATION_REVIEW_REQUIRED:$migrationReviewCount"
}
```

القيمة `54` هي عدد عناصر مراجعة الترحيل المحلي التي منعت العملية. لذلك يفشل `SyncWorker` بالاستثناء الأصلي أعلاه، وليس بسبب استثناء HTTP أو مصادقة Supabase في هذه المحاولة.

لا توجد سلسلة `Caused by` تابعة لهذا الاستثناء؛ فهو الاستثناء الجذري المسجل. أسماء الأصناف في Stack Trace مختصرة بسبب Release/R8، لكن رسالة الاستثناء الأصلية واضحة ومكررة زمنيًا. رسائل Room أو استجابة خطأ من Supabase غير موجودة قبل هذا الفشل في نافذة المحاولة الحالية؛ الموجود هو إنشاء عميل Supabase وتحميل الجلسة بنجاح.

## الملفات

- السجل المنقح، بعد إزالة التوكنات والبيانات الحساسة: [verto-sync-diagnostic-20260909-213334.sanitized.log](verto-sync-diagnostic-20260909-213334.sanitized.log)
- السجل الخام محفوظ محليًا لأغراض التدقيق ولا يُشارك لأنه قد يحتوي على بيانات الجهاز.
- APK التشخيص الموقع: [Verto-1.0.apk](../../Verto-1.0.apk)

## الخلاصة

تم تثبيت تحديث تشخيصي بنفس توقيع التطبيق مع الحفاظ على بيانات المستخدم، وسجل التحديث الاستثناء الأصلي. إعادة المزامنة ما زالت تفشل بسبب حاجز مراجعة الترحيل المحلي `M03_LOCAL_MIGRATION_REVIEW_REQUIRED:54`. لم يتم تخمين سبب أبعد من الدليل المسجل، ولم تُجرَ أي تغييرات لإزالة عناصر المراجعة أو تعديل البيانات.

## استخراج أسباب عناصر M03

بعد تثبيت تحديث تشخيصي إضافي بنفس التوقيع وإعادة الضغط على «مزامنة الآن»، سجّل مسار إعداد سجل M03 هذا التجميع من عناصر `sync_legacy_migration_entry` دون تسجيل المعرّفات أو الحمولة:

| المصدر | النوع | الحالة | رمز السبب | العدد |
|---|---|---|---|---:|
| `DIRTY_INVOICE` | `INVOICE` | `DIRTY` | `M03_DIRTY_WITHOUT_DURABLE_INTENT` | 1 |
| `DIRTY_INVOICE_ITEM` | `INVOICE` | `DIRTY` | `M03_DIRTY_WITHOUT_DURABLE_INTENT` | 1 |
| `DIRTY_PARTY_ROLE` | `PARTY_ROLE` | `DIRTY` | `M03_DIRTY_WITHOUT_DURABLE_INTENT` | 9 |
| `DIRTY_PAYMENT` | `PAYMENT` | `DIRTY` | `M03_DIRTY_WITHOUT_DURABLE_INTENT` | 17 |
| `OPTIMAL_OUTBOX` | `OPTIMAL_UNKNOWN` | `LOCAL_ONLY` | `M03_OPTIMAL_AGGREGATE_UNKNOWN` | 26 |
| **الإجمالي** |  |  |  | **54** |

المعنى التشخيصي المثبت من كود المُنسّق: عناصر `DIRTY_*` ليس لها intent دائم قابل للإثبات في outbox الأقوى، بينما عناصر `OPTIMAL_OUTBOX` لا يمكن تحديد نوع aggregate لها. لذلك بقيت جميعها `REQUIRES_REVIEW`. لم تُعدّل أي صفوف، ولم تُعطّل الحماية، واستمرت إعادة المزامنة في الفشل بالحاجز نفسه.

السجل الكامل المنقح لهذه المحاولة: [verto-m03-breakdown-20260909-215132.sanitized.log](verto-m03-breakdown-20260909-215132.sanitized.log)
