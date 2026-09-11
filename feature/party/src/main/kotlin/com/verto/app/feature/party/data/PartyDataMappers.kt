package com.verto.app.feature.party.data

import com.verto.app.data.local.dao.ClientWithBalance
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.ClientStatus
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.InvoiceType
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentMethod
import com.verto.app.feature.party.domain.model.*
import com.verto.app.utils.MoneyMath

fun PartyIdentityEntity.toPartyClient(
    customerSegment: String? = null,
    supplierScope: String? = null,
): PartyClient = PartyClient(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount, specialty = specialty,
    secondaryPhones = secondaryPhones, createdAt = createdAt, createdBy = createdBy, isDirty = isDirty,
    customerSegment = customerSegmentFromStorage(customerSegment),
    supplierScope = supplierScope?.let { runCatching { SupplierScope.valueOf(it) }.getOrNull() },
)

fun PartyClient.toPartyIdentityEntity(): PartyIdentityEntity = PartyIdentityEntity(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount, specialty = specialty,
    secondaryPhones = secondaryPhones, createdAt = createdAt, createdBy = createdBy, isDirty = isDirty
)

fun ClientWithBalance.toPartyClientSummary(): PartyClientSummary {
    val status = when {
        MoneyMath.isEffectivelyZero(remaining) || remaining < 0 -> PartyClientStatus.GREY
        hasOverdue -> PartyClientStatus.RED
        else -> PartyClientStatus.GREEN
    }
    return PartyClientSummary(
        client = client.toPartyClient(customerSegment, supplierScope),
        totalDebt = totalDebt,
        totalPaid = totalPaid,
        status = status,
        competitorBalance = competitorBalance,
        remaining = remaining
    )
}

private fun ClientStatus.toPartyStatus(): PartyClientStatus = when (this) {
    ClientStatus.RED -> PartyClientStatus.RED
    ClientStatus.GREEN -> PartyClientStatus.GREEN
    ClientStatus.GREY -> PartyClientStatus.GREY
}

fun InvoiceEntity.toPartyInvoice(): PartyInvoice = PartyInvoice(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    type = when (type) { InvoiceType.GOODS -> PartyInvoiceType.GOODS },
    category = when (category) {
        InvoiceCategory.SALE -> PartyInvoiceCategory.SALE
        InvoiceCategory.PURCHASE -> PartyInvoiceCategory.PURCHASE
    },
    description = description,
    totalAmount = totalAmount,
    createdAt = createdAt,
    dueDate = dueDate,
    notifyDaysBefore = notifyDaysBefore,
    notifyRepeatDays = notifyRepeatDays,
    notificationsEnabled = notificationsEnabled,
    notes = notes,
    isOwedToMe = isOwedToMe,
    imageUri = imageUri,
    status = when (status) {
        InvoiceStatus.CLOSED_CASH -> PartyInvoiceStatus.CLOSED_CASH
        InvoiceStatus.CLOSED_CREDIT -> PartyInvoiceStatus.CLOSED_CREDIT
    },
    commission = commission,
    shipmentId = shipmentId,
    createdBy = createdBy,
    voided = voided,
    isDirty = isDirty,
    transactionCurrencyCode = transactionCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    commissionMinor = commissionMinor,
    functionalCurrencyCode = functionalCurrencyCode,
    functionalAmountAtRecognitionMinor = functionalAmountAtRecognitionMinor,
    legacyCurrencyKnown = legacyCurrencyStatus.name == "KNOWN"
)

fun PaymentEntity.toPartyPayment(): PartyPayment = PartyPayment(
    id = id, invoiceId = invoiceId, clientId = clientId, amount = amount,
    amountMinor = amountMinor,
    currencyCode = paymentCurrencyCode,
    currencyKnown = legacyCurrencyStatus.name == "KNOWN" && paymentCurrencyCode.isNotBlank(),
    paymentMethod = when (paymentMethod) {
        PaymentMethod.CASH -> PartyPaymentMethod.CASH
        PaymentMethod.TRANSFER -> PartyPaymentMethod.TRANSFER
        PaymentMethod.CHECK -> PartyPaymentMethod.CHECK
    },
    note = note, paidAt = paidAt, employeeId = employeeId, employeeName = employeeName,
    reversedPaymentId = reversedPaymentId, isDirty = isDirty
)

fun InvoiceEntity.toPartyInvoiceSummary(
    payments: List<PaymentEntity>,
    now: Long = System.currentTimeMillis()
): PartyInvoiceSummary {
    val totalPaid = payments.sumOf { it.amount }
    val remaining = MoneyMath.subtract(totalAmount, totalPaid)
    val isPaid = MoneyMath.isEffectivelyZero(remaining) || remaining < 0
    val isCredit = status == InvoiceStatus.CLOSED_CREDIT
    return PartyInvoiceSummary(
        invoice = toPartyInvoice(),
        totalPaid = totalPaid,
        payments = payments.map(PaymentEntity::toPartyPayment),
        financial = PartyInvoiceFinancialState(
            remaining = remaining,
            isOverdue = isCredit && !isPaid && dueDate > 0L && dueDate < now,
            isPaid = isPaid,
            progressPercent = if (totalAmount > 0) (totalPaid / totalAmount).toFloat().coerceIn(0f, 1f) else 0f,
            isCredit = isCredit
        )
    )
}

fun InvoiceItemEntity.toPartyInvoiceItem(): PartyInvoiceItem = PartyInvoiceItem(
    id = id, invoiceId = invoiceId, itemName = itemName, itemCategory = itemCategory,
    quantity = quantity, buyPrice = buyPrice, sellPrice = sellPrice, totalPrice = totalPrice,
    description = description, isOwedToMe = isOwedToMe, inventoryItemId = inventoryItemId,
    adjustedPurchasePrice = adjustedPurchasePrice, isDirty = isDirty,
    lineRevenueSnapshotMinor = lineRevenueSnapshotMinor,
    lineCostSnapshotMinor = lineCostSnapshotMinor,
    grossProfitSnapshotMinor = grossProfitSnapshotMinor,
    costSnapshotStatus = costSnapshotStatus
)
