package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Read-only server projection for completed commission payouts. */
@Serializable
data class CommissionPaymentDto(
    val id: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("client_name") val clientName: String = "",
    @SerialName("invoice_ids") val invoiceIds: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("bank_name") val bankName: String = "",
    @SerialName("transaction_ref") val transactionRef: String = "",
    @SerialName("paid_at") val paidAt: String? = null,
)

@Serializable
data class CommissionPaymentInvoiceDto(
    @SerialName("commission_payment_id") val commissionPaymentId: String = "",
    @SerialName("invoice_id") val invoiceId: String = "",
)
