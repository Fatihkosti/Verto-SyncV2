package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class RegisteredMarketerDto(
    @SerialName("client_id") val clientId: String = "",
    @SerialName("user_id")   val userId: String   = ""
)

// ── بحث مستخدم AutoDrive بـ client_id ─────────────────────────────────────────
@Serializable
data class AutodriveUserLookupDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("client_id") val clientId: String = ""
)

// ── العمولات: مصدر الحقيقة السيرفري (المرحلة 2.1) ────────────────────────────
// view commission_eligibility — صفّ لكل فاتورة SALE بعمولة>0، مع تصنيف eligibility
// (PAID / WITHDRAWABLE / PENDING) محسوب سيرفرياً عبر last_friday_9am() واكتمال السداد.
@Serializable
data class CommissionEligibilityDto(
    @SerialName("invoice_id")      val invoiceId: String = "",
    @SerialName("client_id")       val clientId: String = "",
    @SerialName("org_id")          val orgId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val commission: BigDecimal = BigDecimal.ZERO,
    @SerialName("invoice_number")  val invoiceNumber: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_amount")    val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("invoice_status")  val invoiceStatus: String = "",
    val category: String = "",
    @SerialName("created_at")      val createdAt: String? = null,
    @SerialName("ledger_status")   val ledgerStatus: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("paid_out_amount") val paidOutAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("payments_sum")    val paymentsSum: BigDecimal = BigDecimal.ZERO,
    @SerialName("week_start")      val weekStart: String? = null,
    val eligibility: String = ""   // PAID | WITHDRAWABLE | PENDING
)

// رصيد المسوّق المحجوز (marketer_balance) — يُستخدم في 2.2
@Serializable
data class MarketerBalanceDto(
    @SerialName("client_id")  val clientId: String = "",
    @SerialName("org_id")     val orgId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal = BigDecimal.ZERO,
    @SerialName("updated_at") val updatedAt: String? = null
)

// سجل العمولات (commission_ledger) — يُستخدم في 2.3 (قسم «قيد التحصيل»)
@Serializable
data class CommissionLedgerDto(
    @SerialName("invoice_id")      val invoiceId: String = "",
    @SerialName("client_id")       val clientId: String = "",
    @SerialName("org_id")          val orgId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("credited_amount") val creditedAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("paid_out_amount") val paidOutAmount: BigDecimal = BigDecimal.ZERO,
    val status: String = "",
    @SerialName("last_event_at")   val lastEventAt: String? = null
)

// ── إحصائيات المسوّق — لوحة مستخدمي AutoDrive (المرحلة 4.4) ────────────────────
// RPC get_marketer_stats() — صفّ لكل مسوّق في المنشأة (مُحصَّن: admin فقط سيرفرياً).
@Serializable
data class MarketerStatsDto(
    @SerialName("client_id")                    val clientId: String = "",
    @SerialName("full_name")                    val fullName: String = "",
    val phone: String = "",
    @SerialName("account_type")                 val accountType: String = "",
    @SerialName("workshop_name")                val workshopName: String? = null,
    @SerialName("joined_at")                    val joinedAt: String? = null,
    @SerialName("last_seen_at")                 val lastSeenAt: String? = null,
    @SerialName("invoices_count")               val invoicesCount: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("purchases_total")              val purchasesTotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("commission_total")             val commissionTotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal = BigDecimal.ZERO,
    @SerialName("pending_withdrawals_count")    val pendingWithdrawalsCount: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("pending_withdrawals_amount")   val pendingWithdrawalsAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("completed_withdrawals_amount") val completedWithdrawalsAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("active_weeks")                 val activeWeeks: Int = 0,
    @SerialName("streak_weeks")                 val streakWeeks: Int = 0,
    @SerialName("last_message_body")            val lastMessageBody: String? = null,
    @SerialName("last_message_at")              val lastMessageAt: String? = null
)

// ── RPC get_or_create_conversation ────────────────────────────────────────────
@Serializable
data class GetOrCreateConversationParams(
    @SerialName("p_client_id") val clientId: String
)
