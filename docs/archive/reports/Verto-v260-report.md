# Verto v260 — Session Report

## النتيجة

**PASS لنطاق تنفيذ الجلسة 260، مع بقاء Build/Android tests غير منفذة لأن Gradle 8.9 غير متاح محليًا والشبكة محجوبة.**

## العيب الحرج المكتشف في v259

كان ترحيل الفاتورة متعددة السطور يمرر `writeId` نفسه لكل السطور إلى `InventoryStockWriter`. حارس Idempotency يعتبر السطر الثاني وما بعده Retry لنفس الأمر، لذلك قد يؤثر أول سطر فقط على المخزون.

تم إصلاح ذلك في v260 بهوية ثابتة على مستوى السطر:

- `postingGroupId = invoice-post:<invoiceId>`
- `sourceLineId = <persisted invoice line id>`
- `writeId = invoice-post:<invoiceId>:<sourceLineId>`

ونفس العقد طُبق على Goods Receipt المحلي باستخدام Receipt Line ID ثابت.

## ما تم تنفيذه

1. تثبيت أن Editor Draft محلي فقط ولا ينشئ حركة مخزون.
2. إزالة محاولة Reverse عند ترحيل سجل `DRAFT`؛ المسودة لم تمتلك حركة أصلًا.
3. تمرير أسطر الفاتورة المحفوظة إلى Inventory posting وربط كل Movement بسطرها الحقيقي.
4. إضافة Posting Group موحد لكل فاتورة مع Command ID مستقل لكل سطر بيع/شراء.
5. تمرير `sourceLineId` و`postingGroupId` عبر Invoice → Inventory adapter → `InventoryStockWriter` → `InventoryDao`.
6. حفظ هوية السطر/المجموعة أيضًا في حركات استلام الشحنات.
7. إصلاح GRN المحلي ليستخدم مفتاح Idempotency مستقلًا لكل Receipt Line بدل مفتاح الطلب كله.
8. الشراء الدولي لا يدخل المخزون من الفاتورة/GRN؛ الإدخال يبقى عند Warehouse Receipt فقط.
9. الاستلام الجزئي يبقى accepted-only مع منع cumulative receipt من تجاوز expected quantity.
10. دفع تكلفة الشحنة منفصل عن كمية المخزون ولا يستدعي stock mutation.
11. التعديل المالي لـ `POSTED` يبقى ممنوعًا in-place؛ النظام يفرض الإلغاء/الاستبدال بدل تعديل الحركات القديمة. استكمال عقد Reversal التفصيلي هو نطاق 261.
12. إضافة اختبارات انحدار لسطرين في البيع والشراء، ومحاكي Transaction/Retry/Rollback للجلسة.

## Transaction contract

ترحيل الفاتورة يعمل داخل `InvoiceTransactionPort` → `RoomDatabaseTransactionRunner` → `AppDatabase.withTransaction`. كتابات `InventoryStockWriter` المتداخلة تستخدم نفس Room database، لذلك فشل أي سطر يعيد الفاتورة والحركات والحراس كلها.

## Schema

لا توجد Migration جديدة في 260. Schema يبقى **75**؛ حقول `sourceLineId` و`postingGroupId` موجودة أصلًا في Movement contract من v257.

## Quality Delta مقابل v259

لا زيادة في الدين المقاس:

- architecture violations: **18 → 18**
- broad catches: **21 → 21**
- dependency cycles: **0 → 0**
- excessive parameter lists: **570 → 570**
- large files >500: **21 → 21**
- long functions: **442 → 442**
- not-null assertions: **1 → 1**

## Build

الأمر:

```text
./gradlew :feature:invoice:testDebugUnitTest :feature:shipment:testDebugUnitTest --offline --no-daemon
```

النتيجة: **NOT REACHED**؛ Gradle Wrapper حاول تنزيل `gradle-8.9-bin.zip` وفشل بـ `UnknownHostException: services.gradle.org`.

## معيار الإغلاق

عقد 260 تحقق ساكنًا ومحاكاةً: كل سطر يملك هوية مستقلة، Retry لا يضاعف الأثر، وفشل أحد السطور يعيد Posting Group كاملًا.
