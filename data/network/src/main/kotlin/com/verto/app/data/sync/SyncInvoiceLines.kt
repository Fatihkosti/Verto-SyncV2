package com.verto.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import com.verto.app.data.local.entity.*
import java.util.UUID
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.SupabaseDateParser
import com.verto.app.utils.SearchTextNormalizer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushInvoiceItems(orgId: String, userId: String) {
        val blocked = financiallyBlockedAggregateIds(orgId)
        val list = db.invoiceDao().getAllInvoiceItemsSync()

        // ── حذف البنود اليتيمة من Supabase ────────────────────────────────────
        // السبب: عند تعديل فاتورة، تُحذف بنودها محلياً وتُستبدل ببنود جديدة بـ UUIDs جديدة.
        // البنود القديمة تظل في Supabase مما يسبب تكرارها عند الـ pull التالي.
        //
        // Session 9 (§11.1): نحصر الحذف على الفواتير التي **عدّلها هذا الجهاز فعلاً**
        // (لها بند متسخ محلياً). جهاز قديم بنسخة بنود قديمة لم يعد بإمكانه حذف بنداً
        // أضافه جهاز آخر — لأنه لم يلمس تلك الفاتورة (لا بند متسخ لها).
        runCatching {
            val dirtyInvoiceIds = db.invoiceDao().getDirtyInvoiceItemsSync()
                .map { it.invoiceId }.filterNot { it in blocked }.toSet()
            val localItemIds    = list.map { it.id }.toSet()

            if (dirtyInvoiceIds.isNotEmpty()) {
                val remoteItems = supabase.postgrest["invoice_items"]
                    .select { filter { eq("organization_id", orgId) } }
                    .decodeList<InvoiceItemDto>()

                // البنود اليتيمة: في Supabase لفاتورة عدّلها هذا الجهاز، لكن ID-ها غير موجود محلياً
                // FIX-5.2: حذف البنود بـ ID محدد بدل حذف كل بنود الفاتورة
                val staleItemIds = remoteItems
                    .filter { it.invoiceId in dirtyInvoiceIds && it.id !in localItemIds }
                    .map { it.id }

                if (staleItemIds.isNotEmpty()) {
                    runCatching {
                        supabase.postgrest["invoice_items"].delete {
                            filter { isIn("id", staleItemIds) }
                        }
                    }.onFailure { e ->
                        android.util.Log.e("SyncManager", "Orphan invoice item cleanup failed")
                    }
                }
            }
        }.onFailure { e ->
            android.util.Log.e("SyncManager", "Orphan invoice item cleanup failed")
        }
        // ──────────────────────────────────────────────────────────────────────

        // SYNC-012: حذف اليتيمة (أعلاه) يحتاج كل البنود المحلية؛ أمّا الرفع فالمتسخ فقط
        val dirty = db.invoiceDao().getDirtyInvoiceItemsSync().filter { it.invoiceId !in blocked }
        if (dirty.isEmpty()) return

        supabase.postgrest["invoice_items"].upsert(
            dirty.map { item ->
                InvoiceItemDto(
                    id              = item.id,
                    invoiceId       = item.invoiceId,
                    organizationId  = orgId,
                    itemType        = item.itemType.name,
                    itemName        = item.itemName,
                    itemCategory    = item.itemCategory,  // FIX-06
                    itemSkuSnapshot = item.itemSkuSnapshot,
                    unitSnapshot = item.unitSnapshot,
                    quantity        = item.quantity,
                    buyPrice        = item.buyPrice.toRemoteDecimal(),
                    sellPrice       = item.sellPrice.toRemoteDecimal(),
                    totalPrice      = item.totalPrice.toRemoteDecimal(),
                    description     = item.description,
                    isOwedToMe      = item.isOwedToMe,
                    inventoryItemId = item.inventoryItemId,
                    adjustedPurchasePrice = item.adjustedPurchasePrice.toRemoteDecimal(),   // SYNC-017
                    unitSellPrice = item.unitSellPrice.toRemoteDecimal(),
                    unitSellPriceMinor = item.unitSellPriceMinor,
                    unitCostAtSale = item.unitCostAtSale.toRemoteDecimal(),
                    unitCostAtSaleMinor = item.unitCostAtSaleMinor,
                    lineRevenueSnapshot = item.lineRevenueSnapshot.toRemoteDecimal(),
                    lineRevenueSnapshotMinor = item.lineRevenueSnapshotMinor,
                    lineCostSnapshot = item.lineCostSnapshot.toRemoteDecimal(),
                    lineCostSnapshotMinor = item.lineCostSnapshotMinor,
                    grossProfitSnapshot = item.grossProfitSnapshot.toRemoteDecimal(),
                    grossProfitSnapshotMinor = item.grossProfitSnapshotMinor,
                    costSnapshotStatus = item.costSnapshotStatus,
                )
            }
        ) { onConflict = "id" }
        db.invoiceDao().markInvoiceItemsClean(dirty.map { it.id })  // SYNC-012
    }


    suspend fun SyncRuntime.pullInvoiceItems(orgId: String) {
        val remote = supabase.postgrest["invoice_items"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<InvoiceItemDto>()

        if (remote.isEmpty()) return

        // فلتر: احتفظ فقط بالبنود التي فاتورتها موجودة في Room
        val allInvoices = db.invoiceDao().getAllInvoicesSync()
        val existingInvoiceIds = allInvoices.map { it.id }.toSet()
        // Session 5 (fixbacklog): فواتير عليها تعديل محلي غير مرفوع — بنودها لا تُلمَس
        // (لا REPLACE ولا حذف يتيم) حتى تنجح دورة رفعٍ تالية. يحمي حالة فشل push ثم pull.
        val dirtyInvoiceIds = allInvoices.filter { it.isDirty }.mapTo(mutableSetOf()) { it.id }

        val validItems = remote.filter {
            it.invoiceId in existingInvoiceIds && it.invoiceId !in dirtyInvoiceIds
        }

        // احفظ adjustedPurchasePrice المحلي (غير موجود في الـ DTO) قبل REPLACE
        val localItems = db.invoiceDao().getAllInvoiceItemsSync()
        val localAdjustedById = localItems.associate { it.id to it.adjustedPurchasePrice }

        if (validItems.isEmpty()) return

        // الفواتير التي وصلتنا بنودها فعلياً (وموجودة محلياً وغير متسخة) — نطاق المطابقة/الحذف
        val pulledInvoiceIds = validItems.map { it.invoiceId }.toSet()

        // REPLACE (upsert): يحدّث البنود الموجودة ويُدرج الجديدة
        db.invoiceDao().insertInvoiceItems(
            validItems.map { dto ->
                InvoiceItemEntity(
                    id              = dto.id,
                    invoiceId       = dto.invoiceId,
                    itemType        = runCatching { ItemType.valueOf(dto.itemType) }.getOrDefault(ItemType.GOODS),
                    itemName        = dto.itemName,
                    itemCategory    = dto.itemCategory,  // FIX-06
                    itemSkuSnapshot = dto.itemSkuSnapshot,
                    unitSnapshot = dto.unitSnapshot,
                    quantity        = dto.quantity,
                    buyPrice        = dto.buyPrice.toRemoteDouble(),
                    sellPrice       = dto.sellPrice.toRemoteDouble(),
                    totalPrice      = dto.totalPrice.toRemoteDouble(),
                    description     = dto.description,
                    isOwedToMe      = dto.isOwedToMe,
                    inventoryItemId = dto.inventoryItemId,
                    // SYNC-017: اقرأ من السيرفر؛ لا تمسح بصفر (احفظ المحلي إن كان السيرفر 0)
                    adjustedPurchasePrice = if (dto.adjustedPurchasePrice.toRemoteDouble() != 0.0) dto.adjustedPurchasePrice.toRemoteDouble()
                                            else (localAdjustedById[dto.id] ?: 0.0),
                    unitSellPrice = dto.unitSellPrice.toRemoteDouble(),
                    unitSellPriceMinor = dto.unitSellPriceMinor,
                    unitCostAtSale = dto.unitCostAtSale.toRemoteDouble(),
                    unitCostAtSaleMinor = dto.unitCostAtSaleMinor,
                    lineRevenueSnapshot = dto.lineRevenueSnapshot.toRemoteDouble(),
                    lineRevenueSnapshotMinor = dto.lineRevenueSnapshotMinor,
                    lineCostSnapshot = dto.lineCostSnapshot.toRemoteDouble(),
                    lineCostSnapshotMinor = dto.lineCostSnapshotMinor,
                    grossProfitSnapshot = dto.grossProfitSnapshot.toRemoteDouble(),
                    grossProfitSnapshotMinor = dto.grossProfitSnapshotMinor,
                    costSnapshotStatus = dto.costSnapshotStatus,
                    isDirty = false   // SYNC-012: مسحوب = نظيف
                )
            }
        )

        // حذف البنود اليتيمة محلياً: لكل فاتورة مسحوبة، احذف بنودها التي لم تَعُد على السيرفر.
        // Session 5: لا تحذف بنداً محلياً غير مرفوع (isDirty) — قد يكون بنداً جديداً لم يُرفَع بعد
        // (فاتورة dirty مستبعَدة أصلاً من pulledInvoiceIds عبر dirtyInvoiceIds أعلاه).
        val remoteIdsByInvoice = validItems.groupBy { it.invoiceId }
            .mapValues { (_, items) -> items.mapTo(mutableSetOf<String>()) { it.id } }
        val staleIds = InvoiceItemsSyncPlanner.staleItemIdsToDelete(
            localItems = localItems.map {
                InvoiceItemsSyncPlanner.LocalItem(it.id, it.invoiceId, it.isDirty)
            },
            pulledInvoiceIds = pulledInvoiceIds,
            remoteIdsByInvoice = remoteIdsByInvoice
        )
        if (staleIds.isNotEmpty()) {
            db.invoiceDao().deleteInvoiceItemsByIds(staleIds)
        }

        android.util.Log.d("SYNC_DEBUG", "بنود الفاتورة: REPLACE ${validItems.size} (من ${remote.size}) · حذف يتيم ${staleIds.size}")
    }

