package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CategoryDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val name: String = ""
)

// ── حركات الصندوق ──────────────────────────────────────────────────────────────
@Serializable
data class CashMovementDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("movement_type") val movementType: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("balance_before") val balanceBefore: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("balance_after") val balanceAfter: BigDecimal = BigDecimal.ZERO,
    @SerialName("reference_id") val referenceId: String = "",
    val note: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

// ── طلبات السحب (AutoDrive) ────────────────────────────────────────────────────
@Serializable
data class WithdrawalRequestDto(
    val id: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("org_id") val orgId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    val status: String = "PENDING",
    @SerialName("bank_name") val bankName: String = "",
    @SerialName("bank_account") val bankAccount: String = "",
    @SerialName("transaction_ref") val transactionRef: String? = null,
    val note: String? = null,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("requested_at") val requestedAt: String = "",
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("processed_by") val processedBy: String? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null
)

// ── رسائل داخلية (AutoDrive ↔ Admin) ──────────────────────────────────────────
@Serializable
data class InternalMessageDto(
    val id: String = "",
    @SerialName("org_id") val orgId: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("sender_type") val senderType: String = "",
    val type: String = "TEXT",
    val body: String = "",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_duration_ms") val mediaDurationMs: Long? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

// ── ملخص المحادثات (جدول conversations في Supabase) ───────────────────────────
@Serializable
data class ConversationDto(
    val id: String = "",
    @SerialName("client_id")        val clientId: String      = "",
    @SerialName("org_id")           val orgId: String         = "",
    val subject: String             = "",
    @SerialName("last_message")     val lastMessage: String?  = null,
    @SerialName("last_message_at")  val lastMessageAt: String = "",
    @SerialName("admin_unread")     val adminUnread: Int      = 0,
    @SerialName("marketer_unread")  val marketerUnread: Int   = 0
)

// ── مسوّق مسجّل في AutoDrive ────────────────────────────────────────────────────
