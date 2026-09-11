package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.model.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import javax.inject.Inject

internal data class InvoiceActor(
    val id: String,
    val name: String,
)
internal data class InvoiceDraftSeed(
    val invoiceId: String,
    val invoiceNumber: Int,
    val createdAt: Long,
    val createdBy: String,
)
internal data class PreparedInvoiceDraft(
    val invoice: InvoiceRecord,
    val lines: List<InvoiceLine>,
    val description: String,
)
internal class InvoiceDraftFactory @Inject constructor() {
    fun create(
        command: SaveInvoiceCommand,
        validated: ValidatedInvoiceSave,
        seed: InvoiceDraftSeed, lineIdProvider: () -> String,
    ): PreparedInvoiceDraft {
        val status = when (command.paymentMode) {
            InvoicePaymentMode.CASH -> InvoiceStatus.CLOSED_CASH
            InvoicePaymentMode.CREDIT -> InvoiceStatus.CLOSED_CREDIT
        }
        val category = if (command.isSale) InvoiceCategory.SALE else InvoiceCategory.PURCHASE
        val description = command.items.firstOrNull()?.name?.ifBlank {
            if (command.isSale) "مبيعات" else "مشتريات"
        } ?: "فاتورة"

        val invoice = InvoiceRecord(
            id = seed.invoiceId,
            invoiceNumber = seed.invoiceNumber,
            clientId = command.clientId,
            organizationId = command.organizationId,
            supplierInvoiceReference = command.supplierInvoiceReference
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            category = category,
            description = description,
            totalAmount = validated.total.toLegacyDouble(),
            totalAmountMinor = validated.total.amountMinor,
            transactionCurrencyCode = command.transactionCurrencyCode,
            functionalCurrencyCode = command.functionalCurrencyCode,
            transactionAmountMinor = validated.total.amountMinor,
            invoiceExchangeRateSnapshot = command.exchangeRate.asDecimal().toPlainString(),
            exchangeRateTimestamp = command.exchangeRateTimestamp,
            exchangeRateSource = command.exchangeRateSource,
            functionalAmountAtRecognitionMinor = command.exchangeRate.convert(
                com.verto.app.money.Money.ofMinor(validated.total.amountMinor, command.transactionCurrencyCode),
            ).amountMinor,
            legacyCurrencyStatus = com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus.KNOWN,
            dueDate = resolveDueDate(command, seed.createdAt),
            createdAt = seed.createdAt,
            notes = command.notes.trim(),
            isOwedToMe = command.isSale,
            status = status,
            shipmentId = command.shipmentId,
            purchaseOrderId = command.purchaseOrderId,
            purchaseScope = command.purchaseScope,
            discount = command.discount.toLegacyDouble(),
            discountMinor = command.discount.amountMinor,
            commission = command.commission.toLegacyDouble(),
            commissionMinor = command.commission.amountMinor,
            commissionBeneficiaryClientId = command.commissionBeneficiaryClientId?.trim()?.takeIf { it.isNotEmpty() },
            commissionSource = command.commissionSource.trim().uppercase().ifBlank { "NONE" },
            createdBy = seed.createdBy,
            lifecycleStatus = InvoiceLifecycleStatus.POSTED,
            lifecycleVersion = 1,
            postedAt = seed.createdAt,
        )
        return PreparedInvoiceDraft(
            invoice = invoice,
            lines = createLines(command, validated, lineIdProvider),
            description = description,
        )
    }

    private fun resolveDueDate(command: SaveInvoiceCommand, createdAt: Long): Long = when {
        command.paymentMode != InvoicePaymentMode.CREDIT -> 0L
        command.dueInstallments.isNotEmpty() -> command.dueInstallments.minOf { it.dueDate }
        command.dueDate > 0L -> command.dueDate
        command.isSale -> createdAt + 7L * 86_400_000L // legacy sales compatibility only
        else -> error("حدد تاريخ استحقاق أو جدول دفعات لفاتورة الشراء الآجلة")
    }

    private fun createLines(
        command: SaveInvoiceCommand,
        validated: ValidatedInvoiceSave, lineIdProvider: () -> String,
    ): List<InvoiceLine> = validated.items.map { item ->
        InvoiceLine(
            id = lineIdProvider(),
            invoiceId = "",
            itemName = item.draft.name.trim(),
            itemCategory = item.draft.itemCategory,
            quantity = item.quantity.units,
            sellPrice = item.sellPrice.toLegacyDouble(),
            sellPriceMinor = item.sellPrice.amountMinor,
            buyPrice = item.buyPrice.toLegacyDouble(),
            buyPriceMinor = item.buyPrice.amountMinor,
            totalPrice = item.lineTotal.toLegacyDouble(),
            totalPriceMinor = item.lineTotal.amountMinor,
            isOwedToMe = command.isSale,
            inventoryItemId = item.draft.inventoryItemId,
        )
    }
}
