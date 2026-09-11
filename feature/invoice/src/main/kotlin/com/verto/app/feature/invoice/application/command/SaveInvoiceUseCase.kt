package com.verto.app.feature.invoice.application.command

import com.verto.app.feature.invoice.application.InvoiceWriteCoordinator
import com.verto.app.feature.invoice.domain.model.CompanyMaintenanceData
import com.verto.app.feature.invoice.domain.model.InvoiceDraftItem
import com.verto.app.feature.invoice.domain.model.InvoiceDueInstallmentDraft
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.model.InvoiceItemData
import com.verto.app.feature.invoice.domain.model.PaymentMode
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.money.ExchangeRate
import com.verto.app.money.Money
import java.util.UUID
import javax.inject.Inject

typealias InvoiceSaveResult = com.verto.app.feature.invoice.domain.model.InvoiceSaveResult

/**
 * Compatibility entry point for existing screens.
 * Business rules and cross-feature orchestration live in [InvoiceWriteCoordinator].
 */
class SaveInvoiceUseCase @Inject constructor(
    private val coordinator: InvoiceWriteCoordinator
) {
    suspend operator fun invoke(
        existingInvoiceId: String?,
        clientId: String,
        items: List<InvoiceItemData>,
        paymentMode: PaymentMode,
        dueDate: Long,
        dueInstallments: List<Pair<Double, Long>> = emptyList(),
        notes: String,
        isSale: Boolean = true,
        originalCreatedAt: Long? = null,
        originalInvoiceNumber: Int? = null,
        initialPayment: Double = 0.0,
        shipmentId: String? = null,
        purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
        discount: Double = 0.0,
        commission: Double = 0.0,
        commissionBeneficiaryClientId: String? = null,
        commissionSource: String = "NONE",
        exchangeRate: Double = 1.0,
        transactionCurrencyCode: String = "",
        exchangeRateTimestamp: Long = 0L,
        exchangeRateSource: String = "USER_INPUT",
        organizationId: String = "",
        companyClient: Boolean = false,
        maintenance: CompanyMaintenanceData? = null,
        supplierInvoiceReference: String? = null,
        purchaseOrderId: String? = null,
        purchaseQuantityToleranceUnits: Int = 0,
        purchasePriceToleranceMinor: Long = 0L,
        purchaseVarianceReason: String? = null,
        unreceivedPaymentOverrideReason: String? = null,
        writeId: String = UUID.randomUUID().toString(),
        requestedAt: Long = System.currentTimeMillis(),
    ): InvoiceSaveResult = coordinator.save(
        SaveInvoiceCommand(
            existingInvoiceId = existingInvoiceId,
            clientId = clientId,
            items = items.map {
                InvoiceDraftItem(
                    name = it.name,
                    quantity = it.quantity,
                    sellPrice = it.sellPrice,
                    buyPrice = it.buyPrice,
                    itemCategory = it.itemCategory,
                    inventoryItemId = it.inventoryItemId
                )
            },
            paymentMode = when (paymentMode) {
                PaymentMode.CASH -> InvoicePaymentMode.CASH
                PaymentMode.CREDIT -> InvoicePaymentMode.CREDIT
            },
            dueDate = dueDate,
            dueInstallments = dueInstallments.map { (amount, date) -> InvoiceDueInstallmentDraft(Money.fromLegacyDouble(amount), date) },
            notes = notes,
            isSale = isSale,
            originalCreatedAt = originalCreatedAt,
            originalInvoiceNumber = originalInvoiceNumber,
            initialPayment = Money.fromLegacyDouble(initialPayment),
            shipmentId = shipmentId,
            purchaseScope = purchaseScope,
            discount = Money.fromLegacyDouble(discount),
            commission = Money.fromLegacyDouble(commission),
            commissionBeneficiaryClientId = commissionBeneficiaryClientId?.trim()?.takeIf { it.isNotEmpty() },
            commissionSource = commissionSource.trim().uppercase().ifBlank { "NONE" },
            exchangeRate = ExchangeRate.fromLegacyDouble(
                exchangeRate,
                baseCurrency = Money.TRANSACTION_CURRENCY,
                quoteCurrency = "FUNCTIONAL",
            ),
            transactionCurrencyCode = transactionCurrencyCode.trim(),
            exchangeRateTimestamp = exchangeRateTimestamp,
            exchangeRateSource = exchangeRateSource,
            organizationId = organizationId.trim(),
            companyClient = companyClient,
            maintenance = maintenance,
            supplierInvoiceReference = supplierInvoiceReference,
            purchaseOrderId = purchaseOrderId,
            purchaseQuantityToleranceUnits = purchaseQuantityToleranceUnits,
            purchasePriceToleranceMinor = purchasePriceToleranceMinor,
            purchaseVarianceReason = purchaseVarianceReason,
            unreceivedPaymentOverrideReason = unreceivedPaymentOverrideReason,
            writeId = writeId,
            requestedAt = requestedAt,
        )
    )
}
