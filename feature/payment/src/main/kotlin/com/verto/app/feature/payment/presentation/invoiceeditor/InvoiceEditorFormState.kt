package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.feature.payment.application.model.*
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verto.app.feature.payment.application.model.InvoiceLineView
import com.verto.app.feature.payment.application.model.InvoiceStatus
import com.verto.app.feature.payment.application.model.InvoiceViewData
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.money.Money
import com.verto.app.money.Quantity

@Stable
internal class InvoiceEditorFormState(
    clientId: String,
    isInternational: Boolean,
    initialIsSale: Boolean,
) {
    var isSale by mutableStateOf(initialIsSale && !isInternational)
    var paymentMode by mutableStateOf(PaymentMode.CASH)
    var selectedClientId by mutableStateOf(clientId)
    var clientSearch by mutableStateOf("")
    var invoiceItems by mutableStateOf(listOf(InvoiceItemData()))
    var selectedDateMillis by mutableStateOf<Long?>(null)
    var showDatePicker by mutableStateOf(false)
    var showExtraSettings by mutableStateOf(true)
    var dueDays by mutableStateOf("")
    var notes by mutableStateOf("")
    var maintenance by mutableStateOf(InvoiceMaintenanceFormState.Empty)
    var paidAmount by mutableStateOf("")
    var invoiceDiscount by mutableStateOf("")
    var referrerClientId by mutableStateOf("")
    var saveError by mutableStateOf("")
    var showPreview by mutableStateOf(false)
    var pendingDue by mutableStateOf(0L)
    var dueInstallments by mutableStateOf<List<PaymentDueInstallmentDraft>>(emptyList())

    // Draft item fields are intentionally kept separate from invoiceItems. This
    // makes the add-item form predictable and prevents an empty row from being
    // persisted when the user is still typing.
    var draftItemName by mutableStateOf("")
    var draftItemQuantity by mutableStateOf("1")
    var draftItemSellPrice by mutableStateOf("")
    var draftItemBuyPrice by mutableStateOf("")
    var draftInventoryItemId by mutableStateOf("")
    var itemError by mutableStateOf("")

    fun load(invoice: InvoiceViewData, items: List<InvoiceLineView>, installments: List<PaymentDueInstallmentDraft> = emptyList(), totalPaid: Double = 0.0) {
        isSale = invoice.isOwedToMe
        paymentMode = if (invoice.status == InvoiceStatus.CLOSED_CREDIT) PaymentMode.CREDIT else PaymentMode.CASH
        selectedDateMillis = invoice.createdAt
        selectedClientId = invoice.clientId
        notes = invoice.notes
        invoiceDiscount = invoice.discount.toBigDecimal().stripTrailingZeros().toPlainString().takeUnless { it == "0" }.orEmpty()
        referrerClientId = invoice.commissionBeneficiaryClientId.takeIf { invoice.commissionSource == "REFERRER" }.orEmpty()
        dueDays = if (invoice.dueDate > 0L) {
            ((invoice.dueDate - invoice.createdAt) / 86_400_000L).takeIf { it > 0 }?.toString().orEmpty()
        } else ""
        dueInstallments = installments
        paidAmount = Money.fromLegacyDouble(totalPaid).toPlainString()
        invoiceItems = items.map { item ->
            InvoiceItemData(
                name = item.itemName,
                quantity = item.quantity.toString(),
                sellPrice = WhatsAppUtils.formatAmount(item.sellPrice),
                buyPrice = if (item.buyPrice > 0) WhatsAppUtils.formatAmount(item.buyPrice) else "",
                itemCategory = item.itemCategory,
                inventoryItemId = item.inventoryItemId
            )
        }.ifEmpty { listOf(InvoiceItemData()) }
        draftItemName = ""
        draftItemQuantity = "1"
        draftItemSellPrice = ""
        draftItemBuyPrice = ""
        draftInventoryItemId = ""
        itemError = ""
    }


    fun restoreDraft(draft: InvoiceEditorDraftData) {
        isSale = draft.isSale
        paymentMode = draft.paymentMode
        selectedClientId = draft.selectedClientId
        selectedDateMillis = draft.selectedDateMillis
        dueDays = draft.dueDays
        notes = draft.notes
        paidAmount = draft.paidAmount
        invoiceDiscount = draft.discount
        referrerClientId = draft.referrerClientId
        dueInstallments = draft.dueInstallments
        pendingDue = draft.dueInstallments.minOfOrNull { it.dueDate } ?: 0L
        invoiceItems = draft.items.ifEmpty { listOf(InvoiceItemData()) }
        draftItemName = draft.draftItem.name
        draftItemQuantity = draft.draftItem.quantity.ifBlank { "1" }
        draftItemSellPrice = draft.draftItem.sellPrice
        draftItemBuyPrice = draft.draftItem.buyPrice
        draftInventoryItemId = draft.draftItem.inventoryItemId
        maintenance = draft.maintenance?.toFormState() ?: InvoiceMaintenanceFormState.Empty
        saveError = ""
        itemError = ""
    }

    fun toDraftData(target: InvoiceDraftSnapshotTarget): InvoiceEditorDraftData = InvoiceEditorDraftData(
        identity = target.identity,
        editor = InvoiceDraftEditorContext(
            existingInvoiceId = target.existingInvoiceId,
            routeClientId = target.routeClientId,
            mode = InvoiceDraftMode(
                isInternational = target.isInternational,
                isSale = isSale,
                paymentMode = paymentMode,
            ),
            selectedClientId = selectedClientId,
            selectedDateMillis = selectedDateMillis,
        ),
        financial = InvoiceDraftFinancialState(
            dueDays = dueDays,
            notes = notes,
            paidAmount = paidAmount,
            discount = invoiceDiscount,
            referrerClientId = referrerClientId,
            dueInstallments = dueInstallments,
            transactionCurrencyCode = target.transactionCurrencyCode,
            exchangeRate = target.exchangeRate.toBigDecimal().stripTrailingZeros().toPlainString(),
        ),
        items = invoiceItems.filter { it.name.isNotBlank() },
        draftItem = InvoiceItemData(
            name = draftItemName,
            quantity = draftItemQuantity,
            sellPrice = draftItemSellPrice,
            buyPrice = draftItemBuyPrice,
            inventoryItemId = draftInventoryItemId,
        ),
        maintenance = maintenance.toDraftDataOrNull(),
    )

    fun addDraftItem(isInternational: Boolean) {
        val quantity = runCatching { Quantity.parse(draftItemQuantity) }.getOrNull()
        val activePriceText = if (isSale) draftItemSellPrice else if (isInternational) draftItemSellPrice else draftItemBuyPrice
        val price = Money.parseOrNull(activePriceText)
        when {
            draftItemName.isBlank() -> itemError = "اكتب اسم الصنف أو اختره من القائمة"
            quantity == null -> itemError = "أدخل كمية صحيحة أكبر من صفر"
            price == null || !price.isPositive() -> {
                itemError = if (isSale) "أدخل سعر البيع قبل الإضافة" else "أدخل سعر الشراء قبل الإضافة"
            }
            else -> {
                invoiceItems = invoiceItems.filter { it.name.isNotBlank() } + InvoiceItemData(
                    name = draftItemName,
                    quantity = quantity.units.toString(),
                    sellPrice = if (isInternational) "" else draftItemSellPrice,
                    buyPrice = if (isInternational) draftItemSellPrice else draftItemBuyPrice,
                    inventoryItemId = draftInventoryItemId,
                )
                draftItemName = ""
                draftItemQuantity = "1"
                draftItemSellPrice = ""
                draftItemBuyPrice = ""
                draftInventoryItemId = ""
                itemError = ""
            }
        }
    }


    fun contentSnapshot(): InvoiceEditorContentSnapshot = InvoiceEditorContentSnapshot(
        isSale = isSale,
        paymentMode = paymentMode,
        selectedClientId = selectedClientId,
        selectedDateMillis = selectedDateMillis,
        dueDays = dueDays,
        notes = notes,
        paidAmount = paidAmount,
        invoiceDiscount = invoiceDiscount,
        referrerClientId = referrerClientId,
        dueInstallments = dueInstallments,
        invoiceItems = invoiceItems,
        draftItem = InvoiceItemData(
            name = draftItemName,
            quantity = draftItemQuantity,
            sellPrice = draftItemSellPrice,
            buyPrice = draftItemBuyPrice,
            inventoryItemId = draftInventoryItemId,
        ),
        maintenance = maintenance.toDraftDataOrNull(),
    )

    fun hasMeaningfulChanges(): Boolean =
        selectedClientId.isNotBlank() ||
            invoiceItems.any { it.name.isNotBlank() } ||
            draftItemName.isNotBlank() ||
            draftItemSellPrice.isNotBlank() || draftItemBuyPrice.isNotBlank() ||
            notes.isNotBlank() || paidAmount.isNotBlank() || dueDays.isNotBlank() ||
            dueInstallments.isNotEmpty() || invoiceDiscount.isNotBlank() || referrerClientId.isNotBlank() ||
            selectedDateMillis != null || maintenance.toDomainOrNull() != null

    fun validate(isInternational: Boolean, validItems: List<InvoiceItemData>): Boolean {
        if (paymentMode == PaymentMode.CREDIT && selectedClientId.isBlank()) {
            saveError = "يجب اختيار ${if (isSale) "عميل" else "مورد"} للفاتورة الآجلة"
            return false
        }
        if (validItems.isEmpty()) {
            saveError = "يجب إضافة صنف واحد على الأقل"
            return false
        }
        val invalidItem = validItems.any { item ->
            val quantity = runCatching { Quantity.parse(item.quantity) }.getOrNull()
            val activePrice = Money.parseOrNull(if (isSale) item.sellPrice else item.buyPrice)
            quantity == null || activePrice == null || !activePrice.isPositive()
        }
        if (invalidItem) {
            saveError = if (isSale) "تحقق من الكمية وسعر البيع لكل بند" else "تحقق من الكمية وسعر الشراء لكل بند"
            return false
        }
        saveError = ""
        return true
    }
}


internal data class InvoiceEditorContentSnapshot(
    val isSale: Boolean,
    val paymentMode: PaymentMode,
    val selectedClientId: String,
    val selectedDateMillis: Long?,
    val dueDays: String,
    val notes: String,
    val paidAmount: String,
    val invoiceDiscount: String,
    val referrerClientId: String,
    val dueInstallments: List<PaymentDueInstallmentDraft>,
    val invoiceItems: List<InvoiceItemData>,
    val draftItem: InvoiceItemData,
    val maintenance: InvoiceMaintenanceDraftData?,
)

internal data class InvoiceDraftSnapshotTarget(
    val identity: InvoiceDraftIdentity,
    val existingInvoiceId: String?,
    val routeClientId: String,
    val isInternational: Boolean,
    val transactionCurrencyCode: String,
    val exchangeRate: Double,
)

private fun InvoiceMaintenanceFormState.toDraftDataOrNull(): InvoiceMaintenanceDraftData? =
    takeIf { it.isEnabled || it.recordId.isNotBlank() || it.images.isNotEmpty() }?.let {
        InvoiceMaintenanceDraftData(
            flags = InvoiceMaintenanceDraftFlags(it.isEnabled, it.isExpanded),
            owner = InvoiceMaintenanceDraftOwner(
                organizationId = it.ownerOrganizationId,
                clientId = it.ownerClientId,
                recordId = it.recordId,
                createdAt = it.createdAt,
            ),
            vehicle = InvoiceMaintenanceDraftVehicle(
                query = it.vehicleQuery,
                selectedOfficialVehicle = it.selectedOfficialVehicle,
                plateNumber = it.plateNumber,
                driverOrDelegate = it.driverOrDelegate,
            ),
            notes = it.notes,
            images = it.images,
        )
    }

private fun InvoiceMaintenanceDraftData.toFormState() = InvoiceMaintenanceFormState(
    isEnabled = isEnabled,
    isExpanded = isExpanded,
    ownerOrganizationId = ownerOrganizationId,
    ownerClientId = ownerClientId,
    recordId = recordId,
    createdAt = createdAt,
    vehicleQuery = vehicleQuery,
    selectedOfficialVehicle = selectedOfficialVehicle,
    plateNumber = plateNumber,
    driverOrDelegate = driverOrDelegate,
    notes = notes,
    images = images,
)
