package com.verto.app.data.sync.pull

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.BudgetEntity
import com.verto.app.data.local.entity.BudgetPeriodType
import com.verto.app.data.local.entity.BudgetType
import com.verto.app.data.local.entity.CategoryEntity
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.ClientReminderEntity
import com.verto.app.data.local.entity.CustomerProfileEntity
import com.verto.app.data.local.entity.EducationalTopicEntity
import com.verto.app.data.local.entity.EducationalTopicTargetEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InventoryUnitEntity
import com.verto.app.data.local.entity.ItemCategoryEntity
import com.verto.app.data.local.entity.LogisticsShipmentEntity
import com.verto.app.data.local.entity.NoteEntity
import com.verto.app.data.local.entity.NotificationAudience
import com.verto.app.data.local.entity.NotificationEntity
import com.verto.app.data.local.entity.NotificationType
import com.verto.app.data.local.entity.OrganizationSettingsLocalEntity
import com.verto.app.data.local.entity.PartyRoleEntity
import com.verto.app.data.local.entity.PriceListTemplateEntity
import com.verto.app.data.local.entity.PriceListTemplateItemEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import com.verto.app.data.local.entity.PurchaseOrderLineEntity
import com.verto.app.data.local.entity.SupplierProfileEntity
import com.verto.app.data.local.entity.TeamObservationEntity
import com.verto.app.data.local.entity.UnitType
import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.SyncMutationOperation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Session 308 remote-only domain adapter. It never invokes producer/outbox APIs.
 * The caller owns the outer AppDatabase.withTransaction page boundary.
 */
@Singleton
class UnifiedSyncChangeApplier @Inject constructor(
    private val database: AppDatabase,
    private val strongerApplier: UnifiedStrongerSyncChangeApplier,
) {
    suspend fun apply(change: SyncChange) = applyMaterialization(ChangeMaterialization(change))

    internal suspend fun apply(change: SyncChange, batch: FinancialApplyBatchV2) =
        applyMaterialization(ChangeMaterialization(change), batch)

    internal fun beginFinancialBatch(organizationId: String, scopeId: String) =
        strongerApplier.beginFinancialBatch(organizationId, scopeId)

    internal suspend fun completeFinancialBatch(batch: FinancialApplyBatchV2) = strongerApplier.completeFinancialBatch(batch)

    suspend fun applySnapshot(snapshot: SyncSnapshotMaterialization) = applyMaterialization(snapshot)

    internal suspend fun applySnapshot(snapshot: SyncSnapshotMaterialization, batch: FinancialApplyBatchV2) =
        applyMaterialization(snapshot, batch)

    private suspend fun applyMaterialization(
        change: UnifiedRemoteMaterialization,
        financialBatch: FinancialApplyBatchV2? = null,
    ) {
        when (change.aggregateType) {
            "PARTY_IDENTITY" -> applyPartyIdentity(change)
            "PARTY_ROLE" -> applyPartyRole(change)
            "CUSTOMER_PROFILE" -> applyCustomerProfile(change)
            "SUPPLIER_PROFILE" -> applySupplierProfile(change)
            "NOTE" -> applyNote(change)
            "REMINDER" -> applyReminder(change)
            "PURCHASE_ORDER" -> applyPurchaseOrder(change)
            "INVENTORY_ITEM" -> applyInventoryItem(change)
            "INVENTORY_UNIT" -> applyInventoryUnit(change)
            "CATEGORY" -> applyCategory(change)
            "ITEM_CATEGORY" -> applyItemCategory(change)
            "BUDGET" -> applyBudget(change)
            "PRICE_LIST" -> applyPriceList(change)
            "ORGANIZATION_SETTINGS" -> applyOrganizationSettings(change)
            "SHIPMENT" -> applyShipment(change)
            "EDUCATIONAL_CONTENT" -> applyEducationalContent(change)
            "NOTIFICATION" -> applyNotification(change)
            "TEAM_OBSERVATION" -> applyTeamObservation(change)
            "INVOICE", "PAYMENT", "CLIENT_CREDIT", "GOODS_RECEIPT", "PURCHASE_MATCH",
            "PURCHASE_PAYMENT_OVERRIDE", "INVENTORY_MOVEMENT", "INVENTORY_COST_REVISION",
            "COST_ALLOCATION", "EXPENSE", "CASH_REGISTER", "CASH_MOVEMENT",
            "CASH_RECONCILIATION", "COMMISSION_PAYMENT", "OPTIMAL_VEHICLE",
            "OPTIMAL_MAINTENANCE", "OPTIMAL_FOLLOW_UP" -> strongerApplier.applyMaterialization(change, financialBatch)
            else -> throw UnifiedSyncPullFailure("CONTRACT_UNSUPPORTED", change.aggregateType)
        }
        if (financialBatch != null && change.aggregateType == "INVENTORY_ITEM" &&
            change.operationType == SyncMutationOperation.UPSERT) {
            check(database.inTransaction() && financialBatch.organizationId == change.organizationId &&
                financialBatch.scopeId == change.scopeId && !financialBatch.completed) { "SCOPE_MISMATCH" }
            check(database.inventoryDao().getItemByIdSync(change.aggregateId) != null) { "LOCAL_APPLY_FAILURE" }
            // This scoped predecessor proves identity only; it does not claim the inventory owner's
            // payload hash/version or run stock effects on behalf of that owner.
            financialBatch.inventoryReferenceWitnesses += change.aggregateId
        }
    }

    private suspend fun applyPartyIdentity(c: UnifiedRemoteMaterialization) {
        when (c.operationType) {
            SyncMutationOperation.UPSERT -> {
                val existing = database.clientDao().getClientIdentityByIdSync(c.aggregateId)
                val entity = PartyIdentityEntity(
                    id = c.aggregateId,
                    name = c.payload.reqString("name"),
                    phone = c.payload.string("phone"),
                    address = c.payload.string("address"),
                    workplace = c.payload.string("workplace"),
                    generalNote = c.payload.string("generalNote"),
                    carType = c.payload.string("carType"),
                    bankAccount = c.payload.string("bankAccount"),
                    specialty = c.payload.string("specialty"),
                    secondaryPhones = c.payload.string("secondaryPhones"),
                    createdAt = c.payload.long("createdAt") ?: existing?.createdAt ?: c.changedAtEpochMillis,
                    createdBy = c.payload.string("createdBy", existing?.createdBy ?: ""),
                    isDirty = false,
                )
                if (existing == null) database.clientDao().insertClient(entity)
                else database.clientDao().updateClient(entity)
            }
            SyncMutationOperation.DELETE -> database.clientDao().deleteClientById(c.aggregateId)
            else -> unsupported(c)
        }
    }

    private suspend fun applyPartyRole(c: UnifiedRemoteMaterialization) {
        val role = c.payload.reqString("role")
        when (c.operationType) {
            SyncMutationOperation.UPSERT -> database.partyRoleDao().upsertRoleFromRemote(
                PartyRoleEntity(
                    id = c.payload.string("id", c.aggregateId),
                    partyId = c.payload.string("partyId", c.aggregateId),
                    organizationId = c.organizationId,
                    role = role,
                    status = c.payload.string("status", "ACTIVE"),
                    createdAt = c.payload.long("createdAt") ?: c.changedAtEpochMillis,
                    updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                    archivedAt = c.payload.long("archivedAt"),
                    archivedBy = c.payload.nullableString("archivedBy"),
                    archiveReason = c.payload.nullableString("archiveReason"),
                    syncRevision = c.entityVersion ?: c.revision,
                    dirty = false,
                    deletedAt = c.deletedAtEpochMillis,
                )
            )
            SyncMutationOperation.DELETE -> database.partyRoleDao().deleteRoleFromRemote(
                c.organizationId, c.payload.string("partyId", c.aggregateId), role,
            )
            else -> unsupported(c)
        }
    }

    private suspend fun applyCustomerProfile(c: UnifiedRemoteMaterialization) {
        val partyId = c.payload.string("partyId", c.aggregateId)
        when (c.operationType) {
            SyncMutationOperation.UPSERT -> database.partyRoleDao().saveCustomerProfile(
                CustomerProfileEntity(
                    organizationId = c.organizationId,
                    partyId = partyId,
                    segment = c.payload.reqString("segment"),
                    ageYears = c.payload.int("ageYears"),
                    purchaseContactName = c.payload.string("purchaseContactName"),
                    businessActivity = c.payload.string("businessActivity"),
                    workplaceName = c.payload.string("workplaceName"),
                    shopName = c.payload.string("shopName"),
                    workshopName = c.payload.string("workshopName"),
                    vehicleModels = c.payload.string("vehicleModels"),
                    workshopWorkerCount = c.payload.int("workshopWorkerCount"),
                    updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                    syncRevision = c.entityVersion ?: c.revision,
                    dirty = false,
                )
            )
            SyncMutationOperation.DELETE -> database.partyRoleDao().deleteCustomerProfileFromRemote(c.organizationId, partyId)
            else -> unsupported(c)
        }
    }

    private suspend fun applySupplierProfile(c: UnifiedRemoteMaterialization) {
        val partyId = c.payload.string("partyId", c.aggregateId)
        when (c.operationType) {
            SyncMutationOperation.UPSERT -> database.partyRoleDao().saveSupplierProfile(
                SupplierProfileEntity(
                    organizationId = c.organizationId,
                    partyId = partyId,
                    scope = c.payload.reqString("scope"),
                    country = c.payload.string("country"),
                    currencyCode = c.payload.string("currencyCode"),
                    specialty = c.payload.string("specialty"),
                    updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                    syncRevision = c.entityVersion ?: c.revision,
                    dirty = false,
                )
            )
            SyncMutationOperation.DELETE -> database.partyRoleDao().deleteSupplierProfileFromRemote(c.organizationId, partyId)
            else -> unsupported(c)
        }
    }

    private suspend fun applyNote(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.noteDao().insertNote(
            NoteEntity(c.aggregateId, c.payload.reqString("clientId"), c.payload.reqString("text"), c.payload.long("createdAt") ?: c.changedAtEpochMillis)
        ).let { Unit }
        SyncMutationOperation.DELETE -> database.noteDao().deleteNoteById(c.aggregateId).let { Unit }
        else -> unsupported(c)
    }

    private suspend fun applyReminder(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.clientReminderDao().insertReminder(
            ClientReminderEntity(
                id = c.aggregateId,
                clientId = c.payload.reqString("clientId"),
                note = c.payload.string("note"),
                reminderAt = c.payload.reqLong("reminderAt"),
                isDone = c.payload.bool("isDone", false),
                createdAt = c.payload.long("createdAt") ?: c.changedAtEpochMillis,
            )
        ).let { Unit }
        SyncMutationOperation.DELETE -> database.clientReminderDao().deleteReminderById(c.aggregateId).let { Unit }
        else -> unsupported(c)
    }

    private suspend fun applyPurchaseOrder(c: UnifiedRemoteMaterialization) {
        val dao = database.purchaseCycleDao()
        if (c.operationType == SyncMutationOperation.CANCEL) {
            val existing = requireNotNull(dao.getOrder(c.aggregateId)) { "LOCAL_APPLY_FAILURE: missing PO for CANCEL" }
            check(existing.organizationId == c.organizationId) { "SCOPE_MISMATCH: PO tenant" }
            check(dao.updateOrderStatus(c.aggregateId, "CANCELLED", c.deletedAtEpochMillis ?: c.changedAtEpochMillis, c.payload.string("closeReason", "REMOTE_CANCEL")) == 1)
            return
        }
        if (c.operationType != SyncMutationOperation.UPSERT) unsupported(c)
        val existing = dao.getOrder(c.aggregateId)
        val order = PurchaseOrderEntity(
            id = c.aggregateId,
            organizationId = c.organizationId,
            orderNumber = c.payload.string("orderNumber", existing?.orderNumber ?: c.payload.reqString("orderNumber")),
            supplierId = c.payload.string("supplierId", existing?.supplierId ?: c.payload.reqString("supplierId")),
            purchaseScope = c.payload.string("purchaseScope", existing?.purchaseScope ?: "LOCAL"),
            currencyCode = c.payload.string("currencyCode", existing?.currencyCode ?: "SDG"),
            status = c.payload.string("status", existing?.status ?: "OPEN"),
            createdAt = c.payload.long("createdAt") ?: existing?.createdAt ?: c.changedAtEpochMillis,
            promisedDeliveryAt = c.payload.long("promisedDeliveryAt") ?: existing?.promisedDeliveryAt,
            createdBy = c.payload.string("createdBy", existing?.createdBy ?: "REMOTE"),
            createdByName = c.payload.string("createdByName", existing?.createdByName ?: ""),
            closedAt = c.payload.long("closedAt"),
            closeReason = c.payload.nullableString("closeReason"),
            note = c.payload.string("note", existing?.note ?: ""),
            writeId = c.payload.string("writeId", existing?.writeId ?: "remote:${c.aggregateId}"),
        )
        val lines = parsePurchaseOrderLines(c.payload, c.aggregateId)
        dao.applyRemotePreCycle(c.organizationId, listOf(order), lines, emptyList(), emptyList(), emptyList())
    }

    private fun parsePurchaseOrderLines(payload: JsonObject, orderId: String): List<PurchaseOrderLineEntity> {
        val arr = payload["lines"] as? JsonArray ?: return emptyList()
        return arr.mapIndexed { index, e ->
            val o = e as? JsonObject ?: throw UnifiedSyncPullFailure("VALIDATION", "PO line must be object")
            PurchaseOrderLineEntity(
                id = o.reqString("id"), purchaseOrderId = orderId,
                lineNumber = o.int("lineNumber") ?: index,
                inventoryItemId = o.nullableString("inventoryItemId"),
                itemNameSnapshot = o.reqString("itemNameSnapshot"),
                orderedQuantity = o.reqInt("orderedQuantity"),
                unitPriceMinor = o.reqLong("unitPriceMinor"),
            )
        }
    }

    private suspend fun applyInventoryItem(c: UnifiedRemoteMaterialization) {
        val dao = database.inventoryDao()
        if (c.operationType == SyncMutationOperation.ARCHIVE) {
            check(dao.archiveItemFromRemote(c.aggregateId, c.deletedAtEpochMillis ?: c.changedAtEpochMillis, c.payload.nullableString("archivedBy"), c.changedAtEpochMillis) == 1) {
                "LOCAL_APPLY_FAILURE: inventory item archive missing"
            }
            return
        }
        if (c.operationType != SyncMutationOperation.UPSERT) unsupported(c)
        val old = dao.getItemByIdSync(c.aggregateId)
        val item = InventoryItemEntity(
            id = c.aggregateId,
            partNumber = c.payload.string("partNumber", old?.partNumber ?: ""),
            name = c.payload.string("name", old?.name ?: c.payload.reqString("name")),
            barcode = c.payload.string("barcode", old?.barcode ?: ""),
            unitId = c.payload.nullableString("unitId") ?: old?.unitId,
            linkedUnitItemId = c.payload.nullableString("linkedUnitItemId") ?: old?.linkedUnitItemId,
            isUnitItem = c.payload.bool("isUnitItem", old?.isUnitItem ?: false),
            quantityPerUnit = c.payload.double("quantityPerUnit") ?: old?.quantityPerUnit ?: 0.0,
            isService = c.payload.bool("isService", old?.isService ?: false),
            buyPrice = c.payload.double("buyPrice") ?: old?.buyPrice ?: 0.0,
            sellPrice = c.payload.double("sellPrice") ?: old?.sellPrice ?: 0.0,
            quantity = old?.quantity ?: 0,
            minQuantity = c.payload.int("minQuantity") ?: old?.minQuantity ?: 5,
            location = c.payload.string("location", old?.location ?: ""),
            note = c.payload.string("note", old?.note ?: ""),
            createdAt = old?.createdAt ?: c.payload.long("createdAt") ?: c.changedAtEpochMillis,
            updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
            isDirty = false,
            isArchived = c.payload.bool("isArchived", old?.isArchived ?: false),
            archivedAt = c.payload.long("archivedAt") ?: old?.archivedAt,
            archivedBy = c.payload.nullableString("archivedBy") ?: old?.archivedBy,
        )
        if (old == null) dao.insertItem(item) else dao.updateItem(item)
    }

    private suspend fun applyInventoryUnit(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.inventoryUnitDao().insertUnit(
            InventoryUnitEntity(
                id = c.aggregateId,
                name = c.payload.reqString("name"),
                quantityPerUnit = c.payload.double("quantityPerUnit") ?: c.payload.reqLong("quantityPerUnitBase").toDouble(),
                unitType = enumValue(c.payload.string("unitType", "COUNT")),
                quantityPerUnitBase = c.payload.long("quantityPerUnitBase") ?: (c.payload.double("quantityPerUnit") ?: 0.0).toLong(),
            )
        )
        SyncMutationOperation.DELETE -> database.inventoryUnitDao().deleteUnit(c.aggregateId)
        else -> unsupported(c)
    }

    private suspend fun applyCategory(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.categoryDao().insertCategory(CategoryEntity(c.aggregateId, c.payload.reqString("name")))
        SyncMutationOperation.DELETE -> database.categoryDao().deleteCategory(c.aggregateId)
        else -> unsupported(c)
    }

    private suspend fun applyItemCategory(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.itemCategoryDao().insertCategory(
            ItemCategoryEntity(c.aggregateId, c.payload.reqString("itemId"), c.payload.reqString("category"))
        )
        SyncMutationOperation.DELETE -> database.itemCategoryDao().deleteCategoriesByIds(listOf(c.aggregateId))
        else -> unsupported(c)
    }

    private suspend fun applyBudget(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> {
            val old = database.budgetDao().getById(c.aggregateId)
            database.budgetDao().insert(
                BudgetEntity(
                    id = c.aggregateId,
                    periodType = enumValue(c.payload.string("periodType", old?.periodType?.name ?: "MONTHLY")),
                    periodStart = c.payload.long("periodStart") ?: old?.periodStart ?: throw missing("periodStart"),
                    periodEnd = c.payload.long("periodEnd") ?: old?.periodEnd ?: throw missing("periodEnd"),
                    budgetType = enumValue(c.payload.string("budgetType", old?.budgetType?.name ?: "SALES_TARGET")),
                    category = c.payload.string("category", old?.category ?: ""),
                    targetAmount = c.payload.double("targetAmount") ?: old?.targetAmount ?: throw missing("targetAmount"),
                    note = c.payload.string("note", old?.note ?: ""),
                    isActive = c.payload.bool("isActive", old?.isActive ?: true),
                    createdAt = c.payload.long("createdAt") ?: old?.createdAt ?: c.changedAtEpochMillis,
                    updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                )
            )
        }
        SyncMutationOperation.DELETE -> database.budgetDao().deleteById(c.aggregateId)
        else -> unsupported(c)
    }

    private suspend fun applyPriceList(c: UnifiedRemoteMaterialization) {
        // v376 changes PRICE_LIST from a persisted quote snapshot to reusable inventory-linked templates.
        // Older unified rows used aggregateId="default" and item-level payloads; they must never be
        // materialized as templates during bootstrap/recovery.
        val isTemplatePayload = c.payload.string("kind") == "TEMPLATE" || c.payload.string("itemIds").isNotBlank()
        if (!isTemplatePayload && c.operationType == SyncMutationOperation.UPSERT)
            throw UnifiedSyncPullFailure("CONTRACT_UNSUPPORTED", "legacy price-list payload is not a V2 template")

        when (c.operationType) {
            SyncMutationOperation.UPSERT -> {
                val name = c.payload.reqString("name")
                val itemIds = c.payload.string("itemIds")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                require(itemIds.isNotEmpty()) { "PRICE_LIST_TEMPLATE_EMPTY" }

                val validIds = itemIds.filter { database.inventoryDao().getItemByIdSync(it) != null }
                if (validIds.size != itemIds.size)
                    throw UnifiedSyncPullFailure("WAITING_DEPENDENCY", "price-list inventory items have not been applied")
                val existing = database.priceListDao().getTemplate(c.aggregateId)
                database.priceListDao().upsertTemplate(
                    PriceListTemplateEntity(
                        id = c.aggregateId,
                        organizationId = c.organizationId,
                        name = name,
                        isFavorite = c.payload.string("isFavorite", "false").toBoolean(),
                        createdAt = c.payload.long("createdAt") ?: existing?.createdAt ?: c.changedAtEpochMillis,
                        updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                    )
                )
                database.priceListDao().deleteTemplateItems(c.aggregateId)
                if (validIds.isNotEmpty()) {
                    database.priceListDao().upsertTemplateItems(
                        validIds.mapIndexed { index, itemId ->
                            PriceListTemplateItemEntity(c.aggregateId, itemId, index)
                        }
                    )
                }
            }
            SyncMutationOperation.DELETE -> database.priceListDao().deleteTemplate(c.aggregateId, c.organizationId)
            else -> unsupported(c)
        }
    }

    private suspend fun applyOrganizationSettings(c: UnifiedRemoteMaterialization) {
        if (c.operationType != SyncMutationOperation.UPSERT) unsupported(c)
        database.unifiedSyncProducerV307Dao().upsertOrganizationSettings(
            OrganizationSettingsLocalEntity(
                organizationId = c.organizationId,
                shopName = c.payload.string("shopName"), shopPhone = c.payload.string("shopPhone"),
                city = c.payload.string("city"), address = c.payload.string("address"),
                currency = c.payload.string("currency"), invoiceFooter = c.payload.string("invoiceFooter"),
                taxNumber = c.payload.string("taxNumber"), logoUrl = c.payload.string("logoUrl"),
                signatureUrl = c.payload.string("signatureUrl"), updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
                isDirty = false,
            )
        )
    }

    private suspend fun applyEducationalContent(c: UnifiedRemoteMaterialization) {
        val deleted = c.operationType == SyncMutationOperation.DELETE
        if (c.operationType !in setOf(SyncMutationOperation.UPSERT, SyncMutationOperation.DELETE)) unsupported(c)
        val topic = EducationalTopicEntity(
            organizationId = c.organizationId, topicId = c.aggregateId,
            title = c.payload.string("title"), summary = c.payload.string("summary"),
            fullContent = c.payload.string("fullContent"), category = c.payload.string("category"),
            isActive = if (deleted) false else c.payload.bool("isActive", true),
            createdByUserId = c.payload.string("createdByUserId"),
            createdAt = c.payload.long("createdAt") ?: c.changedAtEpochMillis,
            updatedAt = c.payload.long("updatedAt") ?: c.changedAtEpochMillis,
            isDirty = false,
            deletedAt = if (deleted) c.deletedAtEpochMillis ?: c.changedAtEpochMillis else c.payload.long("deletedAt"),
        )
        val targets = if (deleted) emptyList() else parseTargets(c.payload["targets"], c.organizationId, c.aggregateId)
        database.educationalContentDao().replaceLocal(topic, targets)
    }

    private fun parseTargets(element: JsonElement?, org: String, topic: String): List<EducationalTopicTargetEntity> =
        when (element) {
            null -> emptyList()
            is JsonArray -> element.map { e ->
                val o = e as? JsonObject ?: throw UnifiedSyncPullFailure("VALIDATION", "education target must be object")
                EducationalTopicTargetEntity(org, topic, o.reqString("targetType"), o.reqString("targetValue"))
            }
            is JsonPrimitive -> element.content.split('|').filter { it.isNotBlank() }.map { encoded ->
                val parts = encoded.split('\u001f', limit = 2)
                if (parts.size != 2) throw UnifiedSyncPullFailure("VALIDATION", "invalid education target encoding")
                EducationalTopicTargetEntity(org, topic, parts[0], parts[1])
            }
            else -> throw UnifiedSyncPullFailure("VALIDATION", "invalid education targets")
        }

    private suspend fun applyNotification(c: UnifiedRemoteMaterialization) = when (c.operationType) {
        SyncMutationOperation.UPSERT -> database.notificationDao().insertNotification(
            NotificationEntity(
                id = c.aggregateId, organizationId = c.organizationId,
                branchId = c.payload.nullableString("branchId"), targetUserId = c.payload.nullableString("targetUserId"),
                audience = enumValue(c.payload.reqString("audience")),
                type = runCatching { NotificationType.valueOf(c.payload.reqString("type")) }
                    .getOrDefault(NotificationType.UNKNOWN),
                title = c.payload.reqString("title"), body = c.payload.string("body"),
                relatedEntityId = c.payload.nullableString("relatedEntityId"), relatedEntityType = c.payload.nullableString("relatedEntityType"),
                navigationRoute = c.payload.nullableString("navigationRoute"), isRead = c.payload.bool("isRead", false),
                createdAt = c.payload.long("createdAt") ?: c.changedAtEpochMillis, createdBy = c.payload.nullableString("createdBy"),
            )
        )
        SyncMutationOperation.DELETE -> database.notificationDao().deleteNotificationById(c.organizationId, c.aggregateId).let { Unit }
        else -> unsupported(c)
    }

    private suspend fun applyTeamObservation(c: UnifiedRemoteMaterialization) {
        if (c.operationType != SyncMutationOperation.UPSERT) unsupported(c)
        database.teamObservationDao().upsertRemote(
            listOf(
                TeamObservationEntity(
                    organizationId = c.organizationId,
                    observationId = c.payload.string("observationId", c.aggregateId),
                    text = c.payload.reqString("text"),
                    category = c.payload.reqString("category"),
                    authorUserId = c.payload.reqString("authorUserId"),
                    authorName = c.payload.reqString("authorName"),
                    status = c.payload.reqString("status"),
                    isImportant = c.payload.bool("isImportant", false),
                    createdAt = c.payload.reqLong("createdAt"),
                    updatedAt = c.payload.reqLong("updatedAt"),
                    updatedByUserId = c.payload.reqString("updatedByUserId"),
                    isDirty = false,
                )
            )
        )
    }

    private suspend fun applyShipment(c: UnifiedRemoteMaterialization) {
        val dao = database.logisticsDao()
        val old = dao.getShipment(c.organizationId, c.aggregateId)
        if (c.operationType == SyncMutationOperation.CANCEL) {
            val current = requireNotNull(old) { "LOCAL_APPLY_FAILURE: shipment missing for CANCEL" }
            check(dao.updateShipment(current.copy(state = "CANCELLED", cancelledAt = c.deletedAtEpochMillis ?: c.changedAtEpochMillis, cancelReason = c.payload.string("cancelReason", "REMOTE_CANCEL"))) == 1)
            return
        }
        if (c.operationType != SyncMutationOperation.UPSERT) unsupported(c)
        val entity = LogisticsShipmentEntity(
            organizationId = c.organizationId, id = c.aggregateId,
            shipmentNumber = c.payload.string("shipmentNumber", old?.shipmentNumber ?: c.payload.reqString("shipmentNumber")),
            sourceLocation = c.payload.string("sourceLocation", old?.sourceLocation ?: ""),
            destinationLocation = c.payload.string("destinationLocation", old?.destinationLocation ?: ""),
            state = c.payload.string("state", old?.state ?: "PLANNED"),
            createdAt = c.payload.long("createdAt") ?: old?.createdAt ?: c.changedAtEpochMillis,
            transportMode = c.payload.nullableString("transportMode") ?: old?.transportMode,
            assignedEmployeeId = c.payload.nullableString("assignedEmployeeId") ?: old?.assignedEmployeeId,
            assignedEmployeeNameSnapshot = c.payload.nullableString("assignedEmployeeNameSnapshot") ?: old?.assignedEmployeeNameSnapshot,
            startedAt = c.payload.long("startedAt") ?: old?.startedAt,
            expectedDepartureAt = c.payload.long("expectedDepartureAt") ?: old?.expectedDepartureAt,
            expectedArrivalAt = c.payload.long("expectedArrivalAt") ?: old?.expectedArrivalAt,
            notes = c.payload.string("notes", old?.notes ?: ""),
            cancelledAt = c.payload.long("cancelledAt") ?: old?.cancelledAt,
            cancelReason = c.payload.string("cancelReason", old?.cancelReason ?: ""),
            customsMilestoneId = c.payload.nullableString("customsMilestoneId") ?: old?.customsMilestoneId,
            customsCalendarPolicyId = c.payload.string("customsCalendarPolicyId", old?.customsCalendarPolicyId ?: "FRIDAY_OFF"),
            eventTimezoneId = c.payload.string("eventTimezoneId", old?.eventTimezoneId ?: "UTC"),
            originCountryKey = c.payload.nullableString("originCountryKey") ?: old?.originCountryKey,
            originCountryNameSnapshot = c.payload.nullableString("originCountryNameSnapshot") ?: old?.originCountryNameSnapshot,
            originCity = c.payload.nullableString("originCity") ?: old?.originCity,
            destinationCountryKey = c.payload.nullableString("destinationCountryKey") ?: old?.destinationCountryKey,
            destinationCountryNameSnapshot = c.payload.nullableString("destinationCountryNameSnapshot") ?: old?.destinationCountryNameSnapshot,
            destinationCity = c.payload.nullableString("destinationCity") ?: old?.destinationCity,
            routeTransportPlanKind = c.payload.nullableString("routeTransportPlanKind") ?: old?.routeTransportPlanKind,
            unifiedTransportMode = c.payload.nullableString("unifiedTransportMode") ?: old?.unifiedTransportMode,
            currentPlanRevision = c.payload.int("currentPlanRevision") ?: old?.currentPlanRevision ?: 0,
            planApprovedAt = c.payload.long("planApprovedAt") ?: old?.planApprovedAt,
        )
        if (old == null) dao.insertShipment(entity) else check(dao.updateShipment(entity) == 1)
    }

    private fun unsupported(c: UnifiedRemoteMaterialization): Nothing =
        throw UnifiedSyncPullFailure("VALIDATION", "operation ${c.operationType} not supported for ${c.aggregateType}")

    private fun missing(name: String) = UnifiedSyncPullFailure("VALIDATION", "missing payload field $name")

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        runCatching { enumValueOf<T>(value) }.getOrElse { throw UnifiedSyncPullFailure("VALIDATION", "invalid enum ${T::class.simpleName}=$value") }
}

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive
private fun JsonObject.string(name: String, default: String = ""): String = primitive(name)?.content ?: default
private fun JsonObject.nullableString(name: String): String? = primitive(name)?.content?.takeUnless { it == "null" }
private fun JsonObject.reqString(name: String): String = primitive(name)?.content?.takeIf { it.isNotBlank() }
    ?: throw UnifiedSyncPullFailure("VALIDATION", "missing payload field $name")
private fun JsonObject.long(name: String): Long? = primitive(name)?.longOrNull
private fun JsonObject.reqLong(name: String): Long = long(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing payload field $name")
private fun JsonObject.int(name: String): Int? = primitive(name)?.intOrNull
private fun JsonObject.reqInt(name: String): Int = int(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing payload field $name")
private fun JsonObject.double(name: String): Double? = primitive(name)?.doubleOrNull
private fun JsonObject.reqDouble(name: String): Double = double(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing payload field $name")
private fun JsonObject.bool(name: String, default: Boolean): Boolean = primitive(name)?.booleanOrNull ?: default
