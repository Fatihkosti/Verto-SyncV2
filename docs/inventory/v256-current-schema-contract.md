# Verto v256 — Current Inventory Schema & Sync Contract

## Room

- `ROOM_SCHEMA_VERSION = 72`.
- آخر migration: `71 → 72`; تخص فهارس التقارير ولا تغيّر عقد المخزون.
- schema export 72 موجود، حجمه 415,148 bytes.

### `inventory_items`
- `quantity: Int` Snapshot قابل للتعديل والمزامنة.
- أسعار Legacy `Double` موجودة مع minor-unit حقول مالية.
- `quantityPerUnit: Double` ما زال مستخدماً للوحدات.
- لا يوجد `companyId` محلي داخل كيان المخزون.

### `inventory_movements`
- الحقول الحالية: id/item/invoice/client/type/quantity/before/after/price/note/shipment/sourceType/sourceId/sourceVersion/writeId/createdAt.
- `quantity` موجبة + معنى IN/OUT، ولا يوجد `signedBaseQuantity`.
- لا توجد `idempotencyKey`, `commandId`, `postingGroupId`, `reversesMovementId`, `serverSequence`, `contractVersion`.
- FK للصنف يستخدم `CASCADE`.
- `writeId` ليس unique.

### التكلفة
- يوجد `inventory_cost_revaluation_events` من F247.
- يوجد `landed_cost_adjustment_events`.
- كلاهما append-style جزئياً، لكن لا يوجد `costSequence` مركزي ولا عقد `inventory_cost_revisions` المستهدف.
- تحديد "الأحدث" محلياً يعتمد `occurred_at DESC, id DESC`.
- Landed Cost الحالي يستطيع تعديل سعر حركة الاستلام الأصلية؛ إذن Movement ليست immutable بالكامل.

## Sync الحالي

### Items
- Push: `inventory_items` upsert يتضمن `quantity` وbuy/sell price.
- Pull: يطبق Snapshot البعيد عند `APPLY_REMOTE`.
- Conflict policy: dirty المحلي يفوز؛ وإلا مقارنة `updatedAt`.

### Movements
- Push منفصل عبر `inventory_movements` upsert على `id`.
- Pull منفصل باستخدام timestamp `created_at` marker ثم `insertMovementIgnore`.
- Pull الحركة لا يعيد بناء `inventory_items.quantity`.

### Sync v2
- يوجد cursor protocol عام (`SyncV2Coordinator`) للتحقق/ack، لكن Inventory نفسه ما زال يستخدم readers/writers Legacy بعده؛ Ledger inventory ليس بعد مبنياً على server sequence.

## أهم التعارضات مع الخطة المستهدفة

1. Snapshot quantity ما زال يُرفع ويُسحب كحقيقة متنافسة.
2. لا signed quantity ولا company identity محلية داخل Movement.
3. Retry منخفض المستوى ليس مضموناً لكل عمليات الفواتير؛ `writeId` لا يمنع التكرار بنفسه.
4. opening quantity لا تنتج `OPENING_BALANCE`.
5. تعديل الفاتورة وحذف/عكس الشحنة يمكن أن يحذف Movements تاريخية.
6. حذف الصنف CASCADE يمحو Ledger.
7. Landed Cost يعدل Movement سابقة.
8. conversion factor لا يُثبت snapshot على سطر Movement الحالي.
9. تكلفة "الأحدث" لا تحسمها `costSequence` من السيرفر.
10. Sync يستخدم timestamps الجهاز/السيرفر بدلاً من cursor خاص بحركات المخزون.
11. `Double` ما زال موجوداً في التحويل والأسعار Legacy.
12. Legacy expense landed-cost path يكتب السعر والحركة مباشرة خارج عقد موحد.

هذه التعارضات ليست تغييرات 256؛ هي Baseline ملزم للجلسات التالية.
