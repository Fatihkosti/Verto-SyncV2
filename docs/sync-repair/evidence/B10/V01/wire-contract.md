---
status: supporting
scope: system
owner: "data:sync"
last_verified_against: "B10-V01 local source/SQLite/policy only; G-B10 BLOCKED"
---
# عقد B10 المطلوب من B08 — لم يُنشر أو يُتحقق حيًا

## حدود الربط

المستقبل الفعلي هو `SupabaseUnifiedSyncPullRemote.pull`، ويطلب RPC واحدة فقط: `verto_pull_sync_changes_v2`. هذا **تعريف واجهة العميل المطلوبة** وليس دليل وجود الدالة على السيرفر. B08/B20 يجب أن ينفذا ويختبرا الشكل نفسه قبل التفعيل. لا fallback إلى الاستقبال القديم ذي المجموعات المجزأة أو ترتيب الأعضاء ذي البداية 1. غياب الدالة/بيان المجموعة خطأ عقد واضح، وليس نجاحًا أو ذريعة لتجاوز الحماية.

النطاق يُستخرج من resolver الموثوق الموجود `verto_resolve_sync_scope`؛ لا ينشئ العميل scope أو cursor من revision. وسم النقل القائم `contractFamily=verto-unified-sync` و`contractVersion=1` محفوظ؛ تسمية خوارزمية Inbox V2 لا تغيّر وسم النقل تلقائيًا، وpayloadVersion المالي يبقى 2.

## معاملات RPC والشكل المرجعي

```json
{"scopeId":"test-scope","cursorToken":"server-issued-opaque-start","softLimit":33,"maxGroupBytes":2097152}
```

أسماء JSON المطابقة حرفيًا: `scopeId`, `cursorToken`, `softLimit`, `maxGroupBytes`. يعيد RPC كائن `SyncPullPage` واحدًا، لا صفوف wire القديمة ولا مصفوفة صفحات. `wire-reference-page.json` مثال تركيبي كامل لا يحتوي بيانات حقيقية. من الضروري تثبيت أسماء معاملات PostgreSQL/طريقة PostgREST المطابقة أثناء B08؛ لم نضف migration أو ننشر SQL لتخمينها.

يجب أن تحمل كل صفحة هوية النطاق كاملة، `fromCursor`, `nextCursor`, `coveredThroughRevision`, `pageHighWatermark`, `endsAtTransactionBoundary=true`, وبيانًا واحدًا مرتبًا لكل مجموعة مرئية كاملة. `GLOBAL_SCOPE` هنا تغطية **النطاق المصرح به** وليس تفويضًا بقراءة منظمات أخرى. يجب أن تتحقق دالة السيرفر من المستخدم والمنظمة ورؤية كل صف ولا تثق في scopeId المرسل وحده.

المجموعة المرئية هي الإسقاط المصرح به للمعاملة: لا تُسرّب أعدادًا أو مفاتيح لصفوف مخفية. أعضاؤها مرقّمون `0..count-1`، والمراجعات تصاعدية دون تكرار أو تداخل مجموعات. أول مراجعة جديدة تتجاوز received coverage السابقة؛ يمكن وجود فجوات scope مشروعة. `coveredThroughRevision` يساوي نهاية آخر مجموعة مرئية مكتملة؛ `pageHighWatermark` مجرد أكبر مراجعة لاحظها السيرفر، ولا يصبح applied checkpoint.

الصفحة الفارغة لا تحرك الرمز، `hasMore=false`، وcoveredThrough يطابق الاستلام الدائم. عدم وجود بيانات مرئية لا يسمح باختراع cursor محلي.

## Canonical bytes وSHA-256

الجسم المحدد هو `SyncInboxGroupBodyV2`: `transactionId`, `changes`, `touchedKeys`, `dependsOnTransactionIds`. لا يُدرج حقل hash أو عدد bytes داخل الجسم حتى لا تنشأ علاقة دائرية.

ترتب مفاتيح كل كائن JSON ترتيبًا معجميًا متكررًا وفق مقارنة Kotlin String، وتحفظ المصفوفات وترتيب التغييرات. تبقى الأنواع الأصلية: الأعداد ليست نصوصًا؛ nulls والحقول ذات القيم الافتراضية في SyncChange موجودة وفق codec المشروع. يتولى مصدر السيرفر نفس serialization المتفق عليها؛ لا تعتمد على إخراج PostgreSQL jsonb::text ذي المسافات أو إعادة تمثيل الأعداد. الهوية المالية وminor لا تُحوّلان إلى floating-point. ترتيب touchedKeys حسب type ثم id، دون تكرار؛ dependencies مرتبة وفريدة ولا تشير للمجموعة نفسها.

يحسب `serializedBytes` على UTF-8 للجسم القانوني كله، و`contentSha256` من SHA-256 لنفس bytes بصيغة hex صغيرة. `wire-reference-canonical.json` بلا newline أخيرة: **521 bytes**، SHA-256 **`9145f76971009144586f03c79550d368c157655c712c2a5cfd4d8df9a46582f3`**. أُنتج هذا المتجه وفُحص بـPython فقط؛ مقارنة Kotlin serialization وPostgreSQL **NOT_RUN**. توجد اختبارات JVM فعلية للمقارنة والحدود، لم تعمل ببيئة Gradle هنا.

يشمل manifest جميع الجذور ومفاتيح الأطفال والحقائق التي سيكتبها DTO؛ العميل يعيد اشتقاق مفاتيح كتابته ويرفض البيان الناقص. يجب أن تحمل `dependsOnTransactionIds` التبعيات المطلوبة لتطبيق آمن، ضمن نطاق الرؤية نفسه. وصول عكس قبل أصله يحتفظ به WAITING_DEPENDENCY؛ لا ينشئ أصلًا بديلًا ولا يخفض نسخة مالية مطبقة.

## حدود السيرفر والعميل

`softLimit` ليس حدًا لتجزئة مجموعة. قد يعيد السيرفر أول مجموعة قانونية من 1001 تغيير كاملة رغم softLimit الأصغر. كل مجموعة <=2,097,152 byte، والمجموعة الأكبر تعيد خطأ عقد دائمًا مع تشخيص مختصر لا payload؛ لا MORE_AVAILABLE فارغ.

العميل يقيد ميزانية التطبيق إلى 1000 تغيير لكل استدعاء مع استثناء أول مجموعة. يحتسب محاولات المجموعات أيضًا لمنع دوران مجموعات الانتظار. يحسب حصة 64MiB على مجموع bytes للمجموعات غير APPLIED في النطاق، ويمكن استلام صفحة قانونية من تحت الحصة حتى 66MiB فقط. تخفيض softLimit قبل الطلب محافظ: من قاعدة فارغة غالبًا 33، لا 1000، حتى يتسع أسوأ احتمال أحجام الأعضاء/المجموعات. حجز القرص تقديري محافظ 4×bytes+1MiB، لكن SQLiteFull ما زال يتطلب rollback فعليًا.

استلام الصفحة لا يثبت تطبيقها؛ لا تسقط retention اعتمادًا على receivedCursor وحده. إثبات الاحتفاظ وتعارض النسخ والـhash على SQL الفعلي وإعادة الاتصال وجهازين مطلوب في B08/B20.
