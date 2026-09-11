# Verto v258 — Session Report

## النتيجة

**PASS لنطاق الجلسة 258 على مستوى التنفيذ الساكن، عقد Kotlin المستقل، ومحاكاة SQLite.**

تم رفع Room من Schema 73 إلى 74، وإضافة مصالحة مخزون مركزية قابلة للاستئناف، مع Marker/Checksum/Quarantine وحماية مؤقتة للكتابة حتى اكتمال المصالحة لكل صنف.

> لم يُدّعَ نجاح Build/Room KSP أو تنفيذ PostgreSQL فعلياً: Gradle 8.9 غير موجود محلياً ومحاولة Wrapper لتنزيله فشلت بسبب حجب الشبكة، ولا توجد خدمة Postgres/Supabase متصلة في البيئة.

## ما تم تنفيذه

1. **Migration 73→74 قصيرة وSchema-only**: لا مسح للأصناف ولا Backfill ثقيل أثناء فتح Room.
2. إضافة جداول محلية:
   - `inventory_reconciliation_control`
   - `inventory_reconciliation_markers`
   - `inventory_reconciliation_quarantine`
   - `inventory_reconciliation_apply_context`
3. إضافة Quantity Guard محلي يمنع تغيير كمية الصنف غير المكتمل Backfill الخاص به، مع bypass محدود داخل Transaction المصالحة فقط.
4. تعريف عقد `AuthoritativeInventoryReconciliationMarker` مع:
   - مفتاح حتمي `inventory-reconcile:<contractVersion>:<organizationId>:<itemId>`.
   - SHA-256 checksum.
   - تحقق من Authority/Delta/Server sequence/approval metadata.
5. Backfill بعد فتح القاعدة، مقسم دفعات 200 وقابل للاستئناف؛ لا يعيد العناصر التي تحمل Marker مكتمل.
6. Full hydration للأصناف والحركات القديمة قبل مقارنة Ledger؛ كمية الصنف الجديد المسحوب تحفظ مؤقتاً بصفر حتى يثبت Marker المرجع المركزي.
7. الحساب المعتمد:
   - `legacyLedgerBalance = Σ signedBaseQuantity` للصفوف canonical المتاحة.
   - fallback للLegacy: `quantityAfter - quantityBefore`.
   - تجاهل `MIGRATION_RECONCILIATION` عند إعادة حساب Legacy.
   - `reconciliationDelta = canonicalSnapshot - legacyLedgerBalance`.
8. لا ينشئ Android أي حركة مصالحة من نفسه؛ يقبل فقط Movement ID/Sequence/Checksum المعتمدة من السيرفر.
9. أي اختلاف محلي عن Marker المركزي يذهب إلى Quarantine بدلاً من التخمين أو تعديل الرصيد بصمت.
10. المصالحة تُنفّذ قبل Push للأصناف والحركات **وقبل حذف صنف بعيد**.
11. SQL v258 للسيرفر يضيف Authority واحداً ثابتاً لكل شركة/Contract:
    - `CENTRAL` عند اكتمال المرجع المركزي.
    - `OWNER_DEVICE` مع staging/finalize صريحين عند اعتماد جهاز مالك.
12. السيرفر يجمد مؤقتاً تغييرات `inventory_items` و`inventory_movements` للصنف غير المكتمل لمنع تغير Legacy baseline أثناء الحساب.
13. نفس الشركة/الصنف يُسلسل بـ advisory transaction lock؛ وUnique idempotency/marker يمنعان المصالحة المزدوجة بين جهازين.
14. عند OWNER_DEVICE تُثبّت الكمية المركزية إلى canonical snapshot داخل نفس Transaction التي تثبت Marker/Movement.
15. لا تُعلن Authority `COMPLETE` إلا بعد وجود Marker لكل canonical item؛ Quarantine يبقي Cutover غير مكتمل.
16. نجاح Marker لاحق يحل Quarantine محلياً بدلاً من ترك إنذار قديم مفتوحاً.
17. أضيف اختبار Migration لمسارات Schemas المصدّرة المتاحة: 39–55 (المتاح منها)، 60، 61، 68، 72 وصولاً إلى 74.

## ملفات الإنتاج المعدلة/المضافة

- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations73To74.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/InventoryReconciliationContract.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryReconciliationEntities.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryReconciliationDao.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/dto/InventoryDtos.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryReconciliation.kt`
- `docs/sql/v258_inventory_reconciliation.sql`

## Tests / Verification المضافة

- `data/database/src/test/kotlin/com/verto/app/data/local/InventoryReconciliationContractV258Test.kt`
- `data/database/src/androidTest/kotlin/com/verto/app/data/local/InventoryReconciliationMigration258Test.kt`
- `tools/verify_v258_inventory_reconciliation.py`
- `Verto-v258-tests-report.md`
- `verification-inventory-F258.md`

## التشغيل المطلوب قبل الإنتاج

1. تطبيق `docs/sql/v257_inventory_ledger_contract.sql` إن لم يكن مطبقاً على Supabase.
2. تطبيق `docs/sql/v258_inventory_reconciliation.sql`.
3. لكل شركة، قفل Authority صراحةً إلى `CENTRAL` فقط بعد إثبات اكتمال Snapshot، أو `OWNER_DEVICE` ثم stage/finalize لذلك الجهاز.
4. تشغيل Build + Unit/Room migration tests في بيئة Gradle 8.9.
5. تشغيل Contract/Concurrency tests على PostgreSQL/Supabase فعلي قبل الانتقال إلى v259.

## معيار الإغلاق

تحقق عقد الجلسة ساكناً: الرصيد السابق محفوظ كـcanonical snapshot، فرق المصالحة حتمي، ولا يقبل Android أكثر من مصالحة مركزية معتمدة واحدة لكل شركة/صنف/Contract.
