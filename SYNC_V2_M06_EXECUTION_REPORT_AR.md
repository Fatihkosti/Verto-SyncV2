# تقرير تنفيذ M06 — Sync V2 Pull / Delete / Recovery

التاريخ: 2026-09-09  
المصدر المنفذ عليه: `Verto-sync-v2-m05-final.zip`  
SHA-256 للمصدر: `26327bdd03c2a3458e0aaad102a4814dbc3d6120b4a7f2e728b25e98cf158494`  
الخطة: `Verto-v2-cutover-plan-ar.md`  
SHA-256 للخطة: `5530bb393556d753e4877ce25d58b5b0188ee708613a6f6a52a514538fb9fe41`

## الحكم التنفيذي

**M06 IMPLEMENTED — STATIC/CONTRACT GATE PASS (65/65).**  
**الإغلاق الرسمي للمرحلة: NOT YET CLOSED** لأن Gradle/runtime tests لم تُنفذ في هذه البيئة؛ Gradle 8.9 غير مخزّن ومحاولة wrapper فشلت بسبب عدم توفر DNS/الشبكة.

لا توجد ادعاءات PASS لاختبارات لم تُنفذ.

## ما تم فحصه فعليًا

تم تتبع المسار الكامل: Server Pull contract → `UnifiedSyncPullEngine` → `UnifiedSyncPullRegistry` → direct/stronger appliers → Room Inbox/domain state → Cursor CAS، وكذلك Bootstrap/Recovery من server snapshot إلى stage ثم cutover الذري.

الـRegistry الحي يحتوي **35 كيانًا** وليس 34 كما في النسخة الأصلية من الخطة، بسبب إضافة `TEAM_OBSERVATION` في M02. التغطية الحالية:

- Pull Registry: **18 direct + 17 stronger = 35/35**.
- Recovery Registry: **35/35** وبمطابقة حرفية مع `UnifiedSyncAggregateRegistry`.
- Direct applier routes: **18/18**.
- Stronger applier routes: **17/17**.

## نتائج التدقيق قبل التعديل

### 1. Cursor النهائي كان يسمح بصفحة غير فارغة بلا تقدم

كان `UnifiedSyncPullEngine` يرفض ثبات الـCursor فقط عندما `hasMore=true`. بذلك كان يمكن نظريًا قبول صفحة أخيرة غير فارغة مع `nextCursor == currentCursor` ثم إعادة نفس الصفحة في تشغيل لاحق.

**الإصلاح:** كل صفحة غير فارغة يجب أن تغيّر الـopaque server cursor، سواء كانت نهائية أو يتبعها صفحات.

### 2. Price List كان ناقصًا في authoritative-absence recovery

السحب العادي يعالج `PRICE_LIST DELETE`، لكن Bootstrap يعيد الحالة الحالية فقط. إذا حُذف قالب أثناء غياب جهاز حتى انتهى Cursor، فقد لا يظهر القالب في Snapshot، وكان Recovery Registry لا يملك prune لـ`PRICE_LIST`، فيبقى قالب محلي قديم.

**الإصلاح:** إضافة prune سلطوي لـ`price_list_templates` عند غياب القالب من Snapshot، مع حماية أي Intent محلي بحالات:

`PENDING / RETRY / LEASED / REQUIRES_REVIEW`.

علاقة `price_list_template_items` تستخدم `ON DELETE CASCADE`، لذلك لا تترك عناصر يتيمة.

### 3. توحيد دفاع حذف VOID/REVERSE

الـstronger bridge كان بالفعل يمنع hard DELETE للأوامر المالية. أضيف guard مركزي كذلك في Pull Registry لكل `VOID_OR_REVERSE` كدفاع إضافي، وليس باعتباره bypass سابقًا مثبتًا.

## خصائص M06 التي ثبتت من الكود والعقد

- Cursor مصدره السيرفر، وليس `updated_at` أو ساعة الجهاز.
- الصفحة لا تُطبق قبل التحقق من Scope والعقد وترتيب Revision وحدود المعاملة.
- Server page يحمل `transactionId / transactionOrder / transactionSize` ويعلن نهاية transaction boundary.
- تطبيق Inbox + بيانات المجال + حالة APPLIED + Cursor يتم داخل Room transaction واحدة.
- Cursor لا يتقدم قبل نجاح materialization لكل تغييرات الصفحة.
- `CURSOR_EXPIRED` ينقل النظام إلى Recovery بدل تجاوز الفجوة.
- Anchor الخاص بالـCursor يجب أن يوجد كـAPPLIED في Inbox.
- Pending local mutation يمنع overwrite القادم من السيرفر ولا يتقدم Cursor فوقه.
- Bootstrap pages تُخزن staging ذريًا ويمكن استئنافها بعد الانقطاع.
- Bootstrap session مربوط بـScope ويُعاد فحص Scope حول استدعاءات الشبكة والـcommit.
- Cutover يطبق Snapshot/prune ويثبت baseline cursor داخل transaction واحدة.
- Outbox identity digest قبل/بعد Recovery يجب أن يبقى مطابقًا.
- Server bootstrap يثبت baseline تحت advisory revision lock، ويأخذ Snapshot عند `updated_revision <= baseline`؛ التغييرات اللاحقة تُلتقط بالـDelta.
- Inbox الحالي محمي من الحذف، لذلك recovery anchors لا تُنظف بصمت في هذا الإصدار.

## التغييرات المنفذة

1. `UnifiedSyncPullEngine.kt`
   - منع أي non-empty page بلا تقدم في Cursor.

2. `UnifiedSyncPullRegistry.kt`
   - منع hard DELETE مركزيًا لسياسة `VOID_OR_REVERSE`.

3. `UnifiedSyncRecoveryRegistry.kt`
   - إضافة authoritative absence pruning لـ`PRICE_LIST` مع حماية Pending/Review.

4. `tools/m06/verify_m06.py`
   - بوابة M06 مستقلة تغطي registries، appliers، cursor، transaction pages، scope، bootstrap، recovery، delete، server migration invariants.
   - تتضمن اختبار SQLite تنفيذيًا لاستعلام Price List prune.

## نتائج التحقق

### M06 static/contract gate

**PASS 65/65**.

أهم النتائج:

- Aggregate coverage: PASS 35/35.
- Pull partition: PASS 18 + 17.
- Recovery registry: PASS 35/35.
- Applier routing: PASS 35/35.
- Transaction-boundary contract: PASS.
- Atomic page commit: PASS.
- Cursor progress/expiry/anchor: PASS.
- Fixed-baseline bootstrap: PASS.
- Scope guards: PASS.
- Pending preservation + outbox digest: PASS.
- Price List prune SQLite semantics: PASS:
  - الغائب عن Snapshot يُحذف.
  - الموجود في Snapshot يبقى.
  - الغائب مع `REQUIRES_REVIEW` يبقى محفوظًا.

الدليل: `evidence/m06/verification/M06_STATIC_GATE.txt` و`.json`.

### Gradle / Kotlin runtime gate

الأمر المجرب:

```text
./gradlew :data:sync:testDebugUnitTest --offline --no-daemon
```

النتيجة: **NOT RUN / ENVIRONMENT BLOCKED** قبل بدء Gradle، لأن wrapper حاول تنزيل `gradle-8.9-bin.zip` ثم فشل بـ`UnknownHostException: services.gradle.org`.

الدليل: `evidence/m06/runtime/M06_GRADLE_ATTEMPT.txt`.

## مطابقة شرط إغلاق M06 في الخطة

| سيناريو M06 | الحالة في هذه البيئة |
|---|---|
| صفحات متعددة وحدود transaction | PASS عقديًا/ساكنًا؛ runtime NOT RUN |
| تغيير أثناء Bootstrap | PASS بالعقد الثابت baseline+Delta؛ runtime NOT RUN |
| Cursor منتهٍ | PASS كوديًا؛ runtime NOT RUN |
| انقطاع أثناء تطبيق صفحة | PASS بالذرية البنيوية؛ crash/runtime NOT RUN |
| حذف من جهاز آخر | PASS للمسار incremental؛ Price List bootstrap gap أُغلق واختبر SQLite |
| Pending local operations أثناء Recovery | PASS كوديًا + Price List SQLite؛ Android runtime NOT RUN |
| تقارب جهازين فعليًا | NOT RUN |

## الملفات والأدلة

- `evidence/m06/M06_SOURCE_PATCH.diff`
- `evidence/m06/M06_CHANGED_PATHS.txt`
- `evidence/m06/verification/M06_STATIC_GATE.txt`
- `evidence/m06/verification/M06_STATIC_GATE.json`
- `evidence/m06/runtime/M06_GRADLE_ATTEMPT.txt`
- `tools/m06/verify_m06.py`

## الخلاصة

التنفيذ الكودي المطلوب لـM06 مكتمل في النسخة المرفقة، مع إغلاق فجوة Recovery حقيقية في Price List وتشديد Cursor invariants. لا أوصي باعتبار **M06 CLOSED** حتى تنجح Gradle tests واختبارات runtime/جهازين المحددة في الخطة على بيئة قادرة على البناء.
