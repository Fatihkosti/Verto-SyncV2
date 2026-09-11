# Verto v256 — Session Report

## النتيجة

**PASS لنطاق الجلسة 256 (Baseline/Discovery فقط).** لم تُعدّل ملفات Production. تم إعادة تأسيس خطة المخزون على v255 الفعلية وتثبيت السلوك الحالي باختبارات Characterization وتقارير قابلة للمراجعة.

## ما تم اكتشافه

- Room الحالي **72**، وليس أي Schema قديمة من v228.
- المخزون يملك بالفعل `InventoryCostRevaluationEventEntity` و`LandedCostAdjustmentEventEntity` من أعمال الفواتير السابقة.
- مسارات البيع/الشراء/المرتجعات والشحنات الأساسية تملك معاملات Room ذرية جزئياً.
- استلام الشحنة يملك retry جيداً عبر `postingId`.
- Concurrent sale على آخر وحدة محمي عبر UPDATE شرطي.
- مع ذلك، Ledger المستهدف غير موجود بعد: `inventory_movements` ما زالت unsigned + before/after، وSnapshot quantity ما زال يُزامن كصف قابل للتنافس.

## الفجوات الحرجة المسجلة للجلسات التالية

1. إنشاء صنف بكمية افتتاحية يحفظ Snapshot بلا Movement.
2. `writeId` في حركة المخزون ليس idempotency constraint؛ إعادة استدعاء writer منخفض المستوى قد تضاعف الأثر.
3. تعديل الفاتورة يحذف حركاتها القديمة قبل إعادة تكوينها.
4. حذف الصنف يستخدم `CASCADE` على حركات المخزون.
5. عكس حذف الشحنة يحذف حركات الاستلام الأصلية بعد إنشاء ADJUST.
6. Landed Cost يعدّل unit price داخل Movement موجودة.
7. Sync يرفع `inventory_items.quantity` ويزامن Movements في قناة مستقلة؛ Pull movement لا يعيد حساب Snapshot.
8. Conflict للـSnapshot قائم على dirty + `updatedAt`، وليس server sequence.
9. `quantityPerUnit` ما زال `Double`، ولا يوجد conversion snapshot داخل Movement.
10. مسار `ExpensesOperationsAdapter` يحدث buy price ويسجل ADJUST=0 مباشرة خارج عقد تكلفة موحد.
11. `InventoryDao.updateQuantity/updateBuyPrice/insertMovement/deleteMovements...` ما زالت APIs منخفضة المستوى متاحة.
12. Inventory entities المحلية لا تحمل `companyId`; العزل يضاف حالياً على DTO/Remote level.

## Characterization المضافة

### Android/Room
`data/database/src/androidTest/kotlin/com/verto/app/data/local/InventoryBaselineF256Test.kt`

يغطي:
- Opening quantity الحالية بلا Movement.
- Sale + Sales Return مع ثبات آخر سعر.
- Shipment receipt retry بنفس posting id.
- Concurrent sale على آخر وحدة.
- توثيق defect الحالي: low-level retry بنفس `writeId` يكرر الخصم.

### JVM Sync
`data/network/src/test/kotlin/com/verto/app/data/sync/InventorySyncBaselineF256Test.kt`

يغطي:
- وجود `quantity` داخل wire DTO الحالي.
- dirty-local wins.
- `updatedAt` last-writer semantics للـSnapshot النظيف.

### Static executable baseline
`tools/verify_v256_inventory_baseline.py`

النتيجة: **37/37 PASS**.

## Build/Test execution

حاولت تشغيل:

`./gradlew :feature:inventory:testDebugUnitTest :feature:invoice:testDebugUnitTest :feature:shipment:testDebugUnitTest :data:network:testDebugUnitTest :data:database:testDebugUnitTest --no-daemon`

لكن Gradle 8.9 غير cached في البيئة، ومحاولة تنزيل `services.gradle.org` فشلت بسبب عدم وجود شبكة. لذلك:

- Static verifier: **PASS 37/37**.
- JVM tests الجديدة: **NOT RUN** في هذه البيئة.
- Android instrumentation الجديدة: **NOT RUN**؛ تحتاج Emulator/device.
- لا ادعاء بBuild ناجح لهذه الجلسة.

## Baseline حجمي

- Kotlin production files: 1,122.
- Kotlin test files قبل إضافات 256: 60.
- Inventory/invoice/shipment/sync related test files قبل إضافات 256: 36.
- `InventoryDao.kt`: 1,394 lines.
- Room schema 72 export: 415,148 bytes.
- المشروع المفكوك قبل artifacts الجديدة: قرابة 23.9 MB.
- 4 مواضع SQL/Room منخفضة المستوى تغيّر quantity مباشرة داخل Inventory DAO family.

لا توجد قاعدة بيانات مستخدم فعلية داخل ZIP، لذلك لم أختلق أرقام rows أو latency تشغيلية لبيانات الإنتاج.

## الملفات الإنتاجية المسموح لمسها لاحقاً حسب الحاجة

النطاق المتوقع فقط، مع إعادة التحقق في بداية كل جلسة:

- `data/database/.../InventoryEntity.kt`, `InventoryDao.kt`, `AppDatabase.kt`, `MigrationCatalog.kt` وmigration الجديدة.
- `feature/inventory/domain/port`, `feature/inventory/data/InventoryRoomAdapters.kt`, repository/coordinators.
- `feature/invoice/application/InvoiceInventoryWriter.kt`, `PurchaseCycleCoordinator.kt`, `InvoiceReturnCoordinator.kt`, `InvoiceVoidCoordinator.kt` وعقود/bridges اللازمة.
- `feature/shipment` posting/receiving/landed-cost use cases والـports المرتبطة.
- `data/network/.../SyncInventory.kt`, `InventoryDtos.kt`, و`feature/inventory/.../InventorySyncParticipant.kt`.
- `data/sync` فقط إذا احتاج العقد المركزي تعديل orchestration/cursor.
- `app/.../ExpensesOperationsAdapter.kt`, `BackupManager.kt`, shipment/invoice bridges فقط لإزالة write paths المباشرة.
- SQL/Supabase contract files المرتبطة بالمخزون.

UI وباقي المجالات خارج النطاق ما لم يثبت dependency مباشر.

## المخرجات

- `docs/inventory/v256-write-path-map.md`
- `docs/inventory/v256-current-schema-contract.md`
- `verification-inventory-F256.md`
- Characterization tests أعلاه.
- `tools/verify_v256_inventory_baseline.py`
- نسخة الخطة `VERTO_INVENTORY_REPAIR_PLAN_v256-v267.md` داخل Source of Truth.

## معيار الإغلاق

تحقق: كل مسار كمية/تكلفة المعروف في v255 مصنف، Schema/Sync الحاليان موثقان، التعارضات مع العقد المستهدف مسجلة، ولا يوجد اعتماد تنفيذي في 256 على أسماء/Schema v228.
