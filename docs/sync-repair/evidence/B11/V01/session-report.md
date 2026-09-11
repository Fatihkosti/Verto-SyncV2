---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B11-V01 local implementation; G-B10/G-B11 BLOCKED"
---
# B11-V01 — تقرير التنفيذ المحلي

تم تنفيذ B11 على مصدر B10-V01 الذي طابق بصمة المنتج السابقة `2c9379972d1d9db3f52d96f0a2dbe2325f77558e53bf3ef97265e1f3ee90eda0`. بصمة نطاق المنتج الجديدة `2af04ead841b8dc8cf8234250caa88f2785131261f422bd5266672bad6c912a9` بعد إضافة مسار مراجعة التعارض؛ لا Git مستخدم.

## المنفذ

- أزيل الحسم الآلي من `UnifiedSyncConflictEngine`: التعارض العام القابل للتحديث ينتقل إلى مراجعة بشرية، وغير ذلك إلى `DOMAIN_CORRECTION_REQUIRED`.
- تحفظ `sync_conflict_review_evidence` نسختي local/remote كاملتين وبصماتهما ونسخة الأساس، ويحفظ `sync_conflict_resolution_audit` القرار كمسار append-only.
- واجهة المراجعة تعرض النوع والسبب والنسخ والبصمات والحقول المختلفة بعد تنقيح الأسرار؛ OUTCOME_UNKNOWN أو immutable لا يملكان أزرار حسم.
- اعتماد الخادم يتطلب صلاحية ونسخة خادم غير متغيرة، ويحوّل الطلب القديم إلى `SUPERSEDED_WITH_PROOF` لا ACK. إذا وُجد تعديل محلي أحدث لا يُستبدل materialization الحالي.
- إعادة المحلي تنشئ mutation جديدة بنفس payload القديم الثابت وعلى `baseVersion` المعروضة حرفيًا، وتسجل `supersedes_mutation_id`. يبقى القديم `SUPERSEDED_PENDING_PROOF` حتى receipt/echo مثبت للبديل.
- أضيف matching-echo resolution وتسلسل predecessor/order يمنع تجاوز نية أحدث أو ضياعها أثناء إثبات البديل.
- Room target أصبح100 مع migration99→100 لتوسيع states وإضافة supersedes/evidence/audit. الحراس تمنع update/delete للأدلة/السجل وتحمي frozen outbox، بما في fresh install عبر callback.

## التحقق الفعلي

`tools/test_sync_b11_static.py` و`tools/test_sync_b11_sqlite.py` نجحا، وPython py_compile نجح. هذه أدلة source/host-SQLite فقط. محاولة Gradle الفعلية فشلت قبل البناء لأن Gradle8.9 غير مخزن والـwrapper حاول تنزيله من `services.gradle.org` دون شبكة؛ لذلك Kotlin2.1/KSP/Hilt/Room/Compose لم تعمل.

G-B10 وG-B11 وT30 تبقى BLOCKED/NOT_RUN. لا export schema100 مولد ولا ادعاء runtime PASS. لا خادم أو بيانات إنتاج أو APK أو GitHub أو Drive عُدلت.

## الاستئناف

NEXT=B11.01 للتحقق الفعلي: شغل Gradle/KSP وRoom migration/fresh100، ثم سيناريوهات القرار والصدى/receipt والصلاحية وتغير النسخة وOUTCOME_UNKNOWN وnewer-local. أغلق G-B10 أولًا/بالتوازي بالدليل المطلوب، ثم G-B11. لا تبدأ B12 تلقائيًا.

## بوابات التسليم

Change-contract PASS. بوابة التوثيق العامة ما زالت FAIL بـ141 خطأ قائمًا من baseline؛ فحص B11 لم يضف خطأ inventory جديدًا. لا تتحول النتيجة إلى جودة/إطلاق PASS.
