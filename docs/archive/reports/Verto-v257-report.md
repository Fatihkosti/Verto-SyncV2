# Verto v257 — Session Report

## النتيجة

**PASS لنطاق الجلسة 257 على مستوى التنفيذ الساكن/SQLite.** تم تثبيت عقد Ledger/Cost Revision ورفع Room إلى Schema 73 دون Backfill أو Cutover لمسارات الكتابة القديمة.

> ملاحظة تحقق: Gradle/Room KSP لم يُشغّلا لأن Gradle 8.9 غير موجود في البيئة ومحاولة تنزيله فشلت بسبب عدم توفر الشبكة. لذلك لا يوجد ادعاء Build ناجح أو Schema export 73 مولّد من Room في هذه البيئة.

## ما تم تنفيذه

1. تعريف أنواع الحركة الرسمية:
   - `OPENING_BALANCE`
   - `PURCHASE`
   - `SALE`
   - `SALES_RETURN`
   - `PURCHASE_RETURN`
   - `MANUAL_ADJUSTMENT`
   - `SHIPMENT_RECEIPT`
   - `REVERSAL`
   - `MIGRATION_RECONCILIATION`
2. إضافة العقد الانتقالي إلى `InventoryMovementEntity`:
   - organization / movement kind / signed base quantity
   - source line / command / idempotency / posting group
   - reversal identity
   - conversion factor snapshot placeholder exact-text
   - occurred/recorded/server accepted times
   - server sequence
   - createdBy/deviceId/contractVersion
3. إبقاء الصفوف القديمة `contractVersion=1` وحقول العقد الجديد `NULL`؛ **لا Backfill في 257**.
4. إنشاء `InventoryCostRevisionEntity` وجدول `inventory_cost_revisions` منفصل عن حركات الكمية.
5. تعريف أنواع مراجعات التكلفة الرسمية وإضافة `costSequence` وIdempotency/Reversal identity.
6. إضافة قيود/فهارس Room للهوية:
   - unique `(organization_id, idempotency_key)`
   - unique `(organization_id, reverses_movement_id)`
   - فهارس item/server sequence, kind/time, source
   - القيود المناظرة لـCost Revision
7. إضافة Validator صريح للعقد canonical:
   - يرفض المصدر/الهوية الناقصة.
   - يرفض حركة كمية صفرية؛ تغير التكلفة يذهب إلى Cost Revision.
   - يفرض اتجاه الكمية على SALE/PURCHASE/RETURNS/RECEIPT.
   - يفرض أصل العكس لـREVERSAL.
8. إضافة إدخال canonical idempotent عبر DAO بـ`OnConflictStrategy.IGNORE` مع Validator قبل الإدخال.
9. إزالة اشتقاق معنى الحركة من `note.startsWith()` / `note LIKE` في Inventory DAO، واستبداله بـ`source_type/source_id` حيث كان مستخدماً في منطق الشحن/العكس/price batch.
10. إضافة SQL عقد Postgres/Supabase لـv257:
    - أعمدة Ledger الجديدة.
    - Check contract v2 للصفوف الجديدة دون إجبار Backfill القديم.
    - unique idempotency/reversal indexes.
    - organization/item isolation FK.
    - canonical `inventory_cost_revisions`.
11. رفع Room `72 -> 73` عبر Migration additive قصيرة فقط؛ لا مصالحة بيانات كبيرة.

## ما لم يُنفّذ عمداً

- لا Backfill/Migration reconciliation؛ هذا نطاق v258.
- لا تحويل كل Quantity writes إلى Writer موحد؛ هذا نطاق v259.
- لا تغيير Sync wire contract أو server atomic ingestion؛ هذا نطاق v262.
- لا حذف التاريخ القديم أو إصلاح CASCADE الشامل؛ مواضع دورة العكس/الأرشفة لها جلساتها اللاحقة.
- SQL Postgres أُنشئ واختُبر ساكناً فقط؛ لم يُطبّق على خدمة Supabase فعلية داخل هذه البيئة.

## الملفات المعدلة/المضافة

### Production
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/InventoryLedgerContract.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/Converters.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations72To73.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt`
- `docs/sql/v257_inventory_ledger_contract.sql`

### Tests / Verification
- `data/database/src/test/kotlin/com/verto/app/data/local/InventoryLedgerContractV257Test.kt`
- `data/database/src/androidTest/kotlin/com/verto/app/data/local/InventoryLedgerMigration257Test.kt`
- `tools/verify_v257_inventory_ledger_contract.py`
- `Verto-v257-tests-report.md`
- `verification-inventory-F257.md`

## معيار الإغلاق

العقد الجديد قادر على تمثيل تدفقات الكمية والتكلفة دون الاعتماد على `note` أو ساعة الجهاز، ويملك هوية Idempotency/Reversal وعزل منظمة، مع بقاء Legacy v1 صالحاً حتى تنفيذ v258/v259.
