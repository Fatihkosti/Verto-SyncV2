# Verto v256 — Inventory Write-path Map

مصدر الحقيقة: `Verto-v255.zip` قبل أي تغيير إنتاجي في 256. هذه الخريطة تصف السلوك الحالي فقط، ولا تعتبره العقد النهائي.

| المسار | نقطة الدخول الحالية | كتابة Room الفعلية | الأثر الحالي |
|---|---|---|---|
| إنشاء/تعديل صنف | `SaveInventoryItemCoordinator` / `SaveInventoryUnitItemCoordinator` | `RoomInventoryStoreAdapter.saveItem` → `InventoryDao.insertItem/updateItem` | يحفظ `quantity` كجزء من Snapshot؛ الكمية الافتتاحية لا تنشئ Movement. |
| شاشة/Bridge دفع ينشئ صنفاً | `PaymentPresentationBridge.saveInventoryItem` | `InventoryRepository.saveItem` → Store adapter | يمكنه حفظ Snapshot كامل للصنف. |
| بيع | `InvoiceInventoryWriter.writeSale` | `deductStockAtomic` | خصم SQLite ذري + Movement `OUT`; `writeId` metadata وليس قيد uniqueness. |
| شراء محلي مباشر | `InvoiceInventoryWriter.writePurchase` | `receivePurchaseAtLatestPriceAtomic` | إضافة الكمية + تحديث آخر سعر + `IN` + Cost revaluation عند تغير السعر. |
| شراء محلي عبر PO/GRN | `PurchaseCycleCoordinator.receive` | `receivePurchaseAtLatestPriceAtomic` بمصدر `GOODS_RECEIPT` | المقبول فقط يدخل المخزون. |
| شراء دولي | `InvoiceInventoryWriter.skipInventory` / Purchase cycle | لا كتابة كمية عند الفاتورة/GRN | الملكية مؤجلة إلى Logistics. |
| مرتجع بيع | `InvoiceReturnCoordinator` | `restoreSalesReturnAtomic` | `IN` بتكلفة البيع التاريخية، دون تغيير آخر سعر شراء. |
| مرتجع شراء | `InvoiceReturnCoordinator` | `deductPurchaseReturnAtomic` | `OUT`; قد يعيد آخر تكلفة سابقة وفق التاريخ الحالي للأحداث. |
| تعديل فاتورة | `InvoiceInventoryWriter.reverseForEdit` | `deleteMovements(invoiceId)` ثم `addStock/deductStock` | يحذف تاريخ حركات الفاتورة القديمة ثم يعيد تكوين الأثر. |
| Void فاتورة | `InvoiceVoidCoordinator` | `reverseInvoiceMovementsAtomic` داخل transaction الفاتورة | ينشئ حركات عكس، والحماية الأساسية من retry موجودة أعلى المسار عبر lifecycle/write guard. الدالة المنخفضة نفسها ليست idempotent. |
| تعديل يدوي | `InventoryRepository.adjustStock` | `adjustStockAtomic` | يكتب Snapshot ثم Movement `ADJUST`. |
| استلام شحنة | `PostAcceptedShipmentStockUseCase` / `RoomReceiveShipmentStockAdapter` | `receiveShipmentStockAtomic` | `postingId` هو Movement id؛ retry بنفس الهوية idempotent. يحدث آخر تكلفة أيضاً. |
| Landed Cost للشحنة | `RoomApplyShipmentLandedCostAdapter` | `applyShipmentLandedCostAtomic` | يعدّل `inventory_movements.unitPrice` للحركة الأصلية ويحدث آخر تكلفة + أحداث تكلفة. |
| حذف/عكس استلامات شحنة | `RoomReverseShipmentReceiptsAdapter` | `reverseShipmentReceiptsAtomic` | يضيف `ADJUST` ثم يحذف حركات الاستلام الأصلية. |
| مصروف مرتبط بفاتورة | `ExpensesOperationsAdapter.distributeLandedCost` | `updateBuyPrice` + `insertMovement(ADJUST quantity=0)` | مسار Legacy مباشر خارج بوابة تكلفة موحدة. |
| فتح وحدة تلقائياً عند نفاد القطع | داخل `deductStockAtomic` | خصم صنف الوحدة + إضافة القطع + حركتا OUT/IN | ينشئ حركات تحويل مخزون داخلية. |
| Backup restore | `BackupManager.restoreBackupData` | `insertItem` ثم `insertMovement` | Snapshot والحركات يستعادان كبيانات منفصلة؛ لا مصالحة. |
| Sync push item | `InventorySyncParticipant` → `pushInventoryItems` | Supabase upsert `inventory_items` | يرفع `quantity` النهائي ضمن الصف. |
| Sync push movements | `pushInventoryMovements` | Supabase upsert `inventory_movements` | قناة منفصلة عن Snapshot. |
| Sync pull item | `pullInventoryItems` | `insertItem/updateItem` | يطبق `quantity` البعيد حسب dirty + `updatedAt`. |
| Sync pull movements | `pullInventoryMovements` | `insertMovementIgnore` | يضيف الحركة فقط ولا يعيد حساب Snapshot منها. |
| حذف صنف | `DeleteInventoryCoordinator` → Store | `InventoryDao.deleteItem` | FK الحالي `CASCADE` يحذف حركات الصنف، ثم يسجل deletion queue للـSync. |

## نقاط الكتابة المنخفضة التي يجب إغلاقها في 259+

- `InventoryDao.updateQuantity` مكشوف مباشرة.
- `InventoryDao.updateBuyPrice` مكشوف مباشرة.
- `InventoryDao.insertMovement` مكشوف مباشرة.
- `InventoryDao.deleteMovementsByInvoiceId`, `deleteAllMovements`, وحذف حركات الشحنة موجودة.
- `InventoryStorePort.saveItem` و`RoomInventoryStoreAdapter.saveItem` يسمحان بتمرير `quantity` ضمن تحديث metadata كامل.

## النتيجة

لا يوجد مسار كمية/تكلفة مجهول بعد فحص v255. توجد عدة مسارات معروفة تخالف العقد المستهدف، لكنها مسجلة صراحةً لتُعالج في الجلسات 257–265.
