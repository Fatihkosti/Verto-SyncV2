package com.verto.app.data.remote

import com.verto.app.utils.BigDecimalSerializer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class PostPaymentV2Request(
    @SerialName("p_invoice_id") val invoiceId: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("p_amount") val amount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("p_cash_amount") val cashAmount: BigDecimal,
    @SerialName("p_payment_method") val paymentMethod: String,
    @SerialName("p_note") val note: String,
    @SerialName("p_paid_at") val paidAt: String,
    @SerialName("p_client_request_id") val requestId: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("p_original_fx_rate") val originalFxRate: BigDecimal
)

@Serializable
data class ReversePaymentV2Request(
    @SerialName("p_payment_id") val paymentId: String,
    @SerialName("p_client_request_id") val requestId: String
)

@Serializable
data class FinancialPostingResult(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("request_id") val requestId: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("cash_amount") val cashAmount: BigDecimal = BigDecimal.ZERO,
    val replayed: Boolean = false
)

interface FinancialPostingRemote {
    suspend fun postPayment(request: PostPaymentV2Request): FinancialPostingResult
    suspend fun reversePayment(paymentId: String, requestId: String): FinancialPostingResult
}

class SupabaseFinancialPostingRemote : FinancialPostingRemote {
    override suspend fun postPayment(request: PostPaymentV2Request): FinancialPostingResult =
        VertoSupabase.client.postgrest.rpc("post_payment_v2", request).decodeAs()

    override suspend fun reversePayment(paymentId: String, requestId: String): FinancialPostingResult =
        VertoSupabase.client.postgrest.rpc(
            "reverse_payment_v2",
            ReversePaymentV2Request(paymentId, requestId)
        ).decodeAs()
}
