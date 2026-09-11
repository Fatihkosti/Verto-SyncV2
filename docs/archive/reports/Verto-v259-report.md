# Verto v259 — Session Report

## النتيجة

**PASS لنطاق الجلسة 259 على مستوى التنفيذ الساكن ومحاكاة SQLite.**

تم رفع Room من Schema 74 إلى 75 وتثبيت `InventoryStockWriter` كبوابة موحدة لكتابات المخزون المحلية، مع Idempotency وTransactional Outbox في Transaction واحدة.

> لم يُدّعَ نجاح Build/KSP أو Android instrumentation: Gradle 8.9 غير موجود محليًا، والـWrapper حاول تنزيله وفشل بسبب عدم توفر الشبكة.

## ما تم تنفيذه

1. إضافة `InventoryStockWriter` بعمليات: `open`, `receive`, `issue`, `returnStock`, `adjust`, `reverse`، إضافة لمسارات الشحن والمرتجعات الموجودة.
2. كل أمر Writer يمر داخل `AppDatabase.withTransaction` عبر:
   - تثبيت هوية الشركة والمستخدم.
   - Claim حتمي للأمر لمنع التكرار.
   - تنفيذ تغير Snapshot وحركة المخزون.
   - Canonicalization للحركة إلى عقد v257.
   - إنشاء Outbox لكل Movement قبل Commit.
3. إضافة جدولي Schema 75:
   - `inventory_write_guards`
   - `inventory_stock_outbox`
4. جعل `InventoryDao.updateQuantity` محميًا ومنع استدعاء mutators الذرية من Production خارج Writer.
5. تحويل البيع/الشراء/المرتجعات/استلام الشحنات/عكس الشحنات/التعديل اليدوي إلى Writer.
6. حفظ Metadata لا يستطيع استبدال `quantity` للصنف الموجود.
7. الصنف الجديد يُحفظ أولًا بكمية صفر؛ أي كمية افتتاحية تمر عبر `open` وتنتج `OPENING_BALANCE`.
8. التعديل اليدوي يرفض السبب الفارغ ويتطلب `inventoryEdit` وهوية المستخدم.
9. حركات فتح الوحدات التلقائي أصبحت موسومة `UNIT_CONVERSION` بدل نسبتها إلى البيع/الشراء خطأً.
10. إضافة اختبارات Instrumentation للتزامن، Retry، فشل Movement، فشل Outbox، وصلاحية/سبب Adjustment.
11. إضافة Migration test لمسار 72→75 للتأكد من حفظ Snapshot وإنشاء جداول v259.
12. تجميع أوامر Writer في Command objects لتجنب إضافة أي Excessive Parameter List جديدة.

## حدود بوابة الكتابة

المنع يخص **كتابات الأعمال المحلية**. تطبيق Snapshot وارد من Sync واستعادة Backup الكاملة يظلان مساري Infrastructure منفصلين لتجنب Outbox loop أو إعادة إنشاء تاريخ وهمي.

## Quality Delta مقابل v258

لا زيادة في مؤشرات الدين المقاسة:

- architecture violations: **18 → 18**
- broad catches: **21 → 21**
- dependency cycles: **0 → 0**
- excessive parameter lists: **570 → 570**
- large files >500: **21 → 21**
- long functions: **442 → 442**
- not-null assertions: **1 → 1**

البوابة العامة للجودة ما زالت **FAIL تاريخيًا** مقابل baseline القديم، لكنها لم تتراجع في v259.

## Build

الأمر:

```text
./gradlew :feature:inventory:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon
```

النتيجة: **NOT RUN TO COMPILATION**؛ Gradle Wrapper حاول تنزيل `gradle-8.9-bin.zip` وانتهى بـ `UnknownHostException: services.gradle.org`.

## معيار الإغلاق

تحقق عقد الجلسة ساكنًا: لا مسار أعمال Production يستدعي mutators المخزون مباشرة، والـWriter يملك Idempotency + Movement + Snapshot + Outbox داخل نفس Transaction.
