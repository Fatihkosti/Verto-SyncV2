package com.verto.app.application.presentationboundary

import com.verto.app.feature.invoice.data.toFinancialState


/**
 * Compatibility boundary for presentation-facing data shapes.
 *
 * Presentation imports this package instead of Room entities/DAO projections directly.
 * The aliases preserve runtime behavior while the remaining domain migration is completed
 * in the next refactor stages. New presentation code must prefer feature-owned UI models.
 */
typealias AuditLogItem = com.verto.app.data.local.entity.AuditLogEntity
typealias BudgetItem = com.verto.app.feature.reports.domain.model.ReportBudget
typealias CashDenominationItem = com.verto.app.feature.reports.domain.model.ReportCashDenomination
typealias CashReconciliationItem = com.verto.app.feature.reports.domain.model.ReportCashSession
typealias CategoryItem = com.verto.app.data.local.entity.CategoryEntity
typealias ClientItem = com.verto.app.data.local.entity.PartyIdentityEntity
typealias ClientReminderItem = com.verto.app.data.local.entity.ClientReminderEntity
typealias CostAllocationItem = com.verto.app.data.local.entity.CostAllocationEntity
typealias InventoryItemView = com.verto.app.data.local.entity.InventoryItemEntity
typealias InventoryMovementItem = com.verto.app.data.local.entity.InventoryMovementEntity
typealias InventoryUnitView = com.verto.app.data.local.entity.InventoryUnitEntity
typealias ItemCategoryItem = com.verto.app.data.local.entity.ItemCategoryEntity
typealias InvoiceViewData = com.verto.app.data.local.entity.InvoiceEntity
typealias InvoiceLineView = com.verto.app.data.local.entity.InvoiceItemEntity
typealias PaymentItem = com.verto.app.data.local.entity.PaymentEntity
typealias RfmClientMetrics = com.verto.app.data.local.entity.RfmCacheEntity

typealias BankAccount = com.verto.app.data.local.entity.BankAccount
typealias BudgetPeriodType = com.verto.app.feature.reports.domain.model.ReportBudgetPeriod
typealias BudgetType = com.verto.app.feature.reports.domain.model.ReportBudgetType
typealias ClientGroup = com.verto.app.data.local.entity.ClientGroup
typealias ClientType = com.verto.app.data.local.entity.ClientType
typealias InvoiceCategory = com.verto.app.data.local.entity.InvoiceCategory
typealias InvoiceStatus = com.verto.app.data.local.entity.InvoiceStatus
typealias InvoiceType = com.verto.app.data.local.entity.InvoiceType
typealias ItemType = com.verto.app.data.local.entity.ItemType
typealias MovementType = com.verto.app.data.local.entity.MovementType
typealias PaymentMethod = com.verto.app.data.local.entity.PaymentMethod
typealias RfmSegment = com.verto.app.data.local.entity.RfmSegment

typealias LowStockSupplierItem = com.verto.app.data.local.dao.LowStockSupplierRow
typealias InvoicePaymentSummary = com.verto.app.data.local.dao.InvoiceWithPaid

val InvoicePaymentSummary.financial: com.verto.app.feature.invoice.domain.model.InvoiceFinancialState
    get() = invoice.toFinancialState(totalPaid)

fun String.toClientTypeSet(): Set<ClientType> =
    split(',')
        .mapNotNull { name -> runCatching { ClientType.valueOf(name.trim()) }.getOrNull() }
        .toSet()
        .ifEmpty { setOf(ClientType.INDIVIDUAL) }

fun Set<ClientType>.toStorageString(): String = joinToString(",") { it.name }

fun String.containsClientType(type: ClientType): Boolean = toClientTypeSet().contains(type)

fun String.clientTypesLabel(): String = toClientTypeSet().joinToString(" + ") { it.label }

fun String.isSupplier(): Boolean =
    containsClientType(ClientType.SUPPLIER) || containsClientType(ClientType.GLOBAL_SUPPLIER)

fun String.toBankAccountList(): List<BankAccount> =
    if (isBlank()) emptyList()
    else split("||")
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { entry ->
            val parts = entry.split("::")
            BankAccount(
                bankName = parts.getOrElse(0) { "" }.trim(),
                accountNumber = parts.getOrElse(1) { "" }.trim()
            )
        }

fun List<BankAccount>.toBankAccountsString(): String =
    joinToString("||") { "${it.bankName}::${it.accountNumber}" }

fun String.toSecondaryPhoneList(): List<String> =
    if (isBlank()) emptyList()
    else split("||").map(String::trim).filter(String::isNotBlank)

fun List<String>.toSecondaryPhonesString(): String = joinToString("||")
fun com.verto.app.feature.inventory.domain.model.InventoryItem.toPresentationInventoryItem(): InventoryItemView =
    InventoryItemView(
        id = id,
        partNumber = partNumber,
        name = name,
        barcode = barcode,
        unitId = unitId,
        linkedUnitItemId = linkedUnitItemId,
        isUnitItem = isUnitItem,
        quantityPerUnit = quantityPerUnit,
        isService = isService,
        buyPrice = buyPrice,
        sellPrice = sellPrice,
        quantity = quantity,
        minQuantity = minQuantity,
        location = location,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDirty = isDirty
    )

fun InventoryItemView.toInventoryDomainItem(): com.verto.app.feature.inventory.domain.model.InventoryItem =
    com.verto.app.feature.inventory.domain.model.InventoryItem(
        id = id,
        partNumber = partNumber,
        name = name,
        barcode = barcode,
        unitId = unitId,
        linkedUnitItemId = linkedUnitItemId,
        isUnitItem = isUnitItem,
        quantityPerUnit = quantityPerUnit,
        isService = isService,
        buyPrice = buyPrice,
        sellPrice = sellPrice,
        quantity = quantity,
        minQuantity = minQuantity,
        location = location,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDirty = isDirty
    )

