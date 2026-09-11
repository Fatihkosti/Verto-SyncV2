# تقرير تنفيذ M08 — التحويل التشغيلي الكامل

التاريخ: 2026-09-09

## الحالة الحالية

**M08 IN PROGRESS — BUILD/UNIT/PRE-FLIGHT PASS / TWO-DEVICE TRIAL REQUIRED.**

لا يُعلن M08 CLOSED ولا يُحوّل السيرفر نهائيًا قبل نجاح تجربة مؤسسة معزولة على جهازين، كما تشترط خطة التحول.

## مصدر التطبيق

- المستودع: `aboalftooh/verto`
- تم فك `Verto-sync-v2-m07-final.zip` إلى جذر المستودع.
- commit المصدر المفكوك: `5a856ba164860219f7c347aa34f4b03c129fc085`
- commit بوابة M05-M07: `a093f54af63b3f40b893ac03c9b09d8aa5ae80e7`
- commit تقرير/مرشح M08: `843ee6b9b75843fffab9c47192fbe58b3c7d3bb2`

## M05–M07 re-verification

أعيد التحقق على GitHub Actions بدل الاعتماد على نتائج البيئة المحلية القديمة:

- M05 static gate: **PASS 36/36**.
- M06 static/contract gate: **PASS 65/65**.
- M07 static gate: **PASS 18/18**.
- `:data:sync:testDebugUnitTest`: **PASS**.
- `:app:assembleDebug`: **PASS**.
- Debug APK SHA-256: `c1a275d8fac93cea0bf6bd70557aa852511a61e94946c55fec63b8bc11187a73`.
- GitHub Actions verification run: https://github.com/aboalftooh/verto/actions/runs/34332359880

## Release candidate

تم بناء Release APK الموقّع من commit `843ee6b9b75843fffab9c47192fbe58b3c7d3bb2` باستخدام إعداد Supabase الفعلي ومادة التوقيع المعتمدة.

- Release workflow: **PASS**.
- Run: https://github.com/aboalftooh/verto/actions/runs/34332967204
- Artifact: `Verto-current-release-apk`.
- APK SHA-256: `93e05fe5d9f87e7928a466139c64d93676b8673321218854fda363d05a1dc82a`.
- APK Signature Scheme v2: **verified**.
- signer certificate SHA-256: `9b3279d33b56797153e2dad6c9c1d8e10fb31f24b35ded77b35677d06907c9c7`.

هذه هي النسخة التي يجب استخدامها في تجربة الجهازين لـM08.

## M08 server pre-flight — Verto-app

تم الفحص الحي قراءة فقط على Supabase project `madkfvggyolmdberzmtb`:

- `verto_apply_sync_mutation`: موجود.
- `verto_pull_sync_changes`: موجود.
- `verto_resolve_sync_scope`: موجود.
- bootstrap coverage: **35 aggregate**.
- stronger adapter registry: **17 aggregate**.
- sync change log: **8336 row** وقت الفحص.
- sync receipts: **5** وقت الفحص.
- active scopes: **2** وقت الفحص.
- لا توجد aggregate types في change log خارج bootstrap coverage.

العقد الحي:

- family: `verto-unified-sync`
- contract version: 1
- schema version: 1
- scope definition version: 1
- status: `EXPAND_ONLY`
- production pruning: disabled

## أول كتابة V2 مؤكدة

تم العثور على سجل دائم في `verto_internal.sync_v2_migration_state` للمؤسسة:

`c0871b45-2d1b-4579-9dd0-5563f85a8f27`

ويحتوي:

- first V2 write: `2026-08-31T04:10:15.535338Z`
- marker: `receipt:v393-smoke-invoice-1`
- `safe_to_project_to_legacy = false`

كما توجد Receipts حية مثبتة لـ:

- INVOICE: APPLIED
- INVOICE: CONFLICT
- PAYMENT: APPLIED
- CLIENT_CREDIT: APPLIED

بالتالي يوجد أثر V2 مؤكد بالفعل. وفق خطة التحول، أي تعطل لاحق يُعالج بإصلاح V2 أو إيقاف الإرسال المتأثر مع حفظ الطلبات؛ لا يُستخدم Legacy fallback لاستعادة الكتابة لهذه المؤسسة.

## Rollout الحالي وبواباته

إعداد `PRODUCTION` الحالي:

- default wave: 0
- kill switch: true
- legacy fallback enabled: true

لكن الانتقال المباشر محمي على السيرفر، والترقية لا تتم إلا عبر دوال transition وبأدلة مسجلة في `sync_rollout_transition_evidence`.

وقت الفحص لا توجد أدلة transition مسجلة. لذلك لم يتم تغيير wave أو kill switch أو fallback ضمن M08.

بوابات السيرفر تتدرج حتى Wave 6، وتشمل device runtime verification، عدد تشغيلات ناجحة، صفر failures/recovery/pending، حدود pull lag، وفي Wave 6 صفر realtime disconnects. القفز بين الموجات ممنوع.

## فجوة الدليل التشغيلي الحالية

هناك 15 aggregate مغطاة بالعقد لكنها لم تظهر بعد في `verto_sync_change_log` الحي، لذلك لا يجوز اعتبار التغطية التشغيلية الكاملة مثبتة من بيانات السيرفر الحالية وحدها.

## ما يقوم به المستخدم في M08

على **مؤسسة اختبار معزولة** وجهازين بنفس Release APK:

1. اترك الجهاز B غير متصل.
2. من الجهاز A: أنشئ صنفًا بكمية معروفة، ثم فاتورة بيع ودفعة، ثم زامن حتى `COMPLETED`.
3. شغّل الجهاز B وزامن؛ تحقق من الصنف والفاتورة والدفعة والكمية المخزنية.
4. عدّل من B، زامن، ثم زامن A وتحقق من تطابق الحالتين.
5. نفذ حذف/أرشفة/عكسًا قانونيًا حسب الكيان، ثم تحقق من وصول الأثر للجهاز الآخر.
6. أثناء مزامنة واحدة أغلق التطبيق إجباريًا، افتحه مجددًا وزامن؛ لا يجب أن يحدث فقد أو ازدواج.

## ما يقوم به المساعد بعد تجربة الجهازين

بعد تنفيذ التجربة، يتم فحص Supabase مباشرةً للتحقق من:

- mutation identities والـreceipts وعدم الازدواج.
- revisions/change log والتقارب بين الجهازين.
- الأثر المالي والمخزني بالأرقام الدقيقة.
- pending/review/rejected والحالة النهائية الصحيحة.
- Scope وعدم وجود كتابة عبر مؤسسة خاطئة.
- ثبات أول كتابة V2 وحالة migration.
- إنشاء/تثبيت transition evidence الحقيقي ثم ترقية rollout تدريجيًا فقط إذا تحققت بوابات السيرفر.

## قرار M08 الحالي

**المتاح آليًا قبل تجربة الجهازين اكتمل ونجح.**

**المتبقي لإغلاق M08: تجربة الجهازين والأدلة التشغيلية الناتجة عنها.**

لا يتم تعطيل آليات الانتقال أو حذف Legacy ضمن M08 قبل نجاح هذه البوابة. إزالة Legacy نفسها تبقى M09.
