package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ExpenseDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    val category: String = "",
    val item: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    @SerialName("expense_date") val expenseDate: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    // SYNC-011: انتشار تعديل المبلغ بين الأجهزة (يُدار سيرفرياً عبر trg_expenses_updated_at)
    @SerialName("updated_at") val updatedAt: String? = null
)

// ── ملاحظة العميل (SYNC-013) ──────────────────────────────────
@Serializable
data class NoteDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val text: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

// ── تذكير العميل (SYNC-013) ───────────────────────────────────
@Serializable
data class ClientReminderDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("client_id") val clientId: String? = null,
    val note: String = "",
    @SerialName("reminder_at") val reminderAt: String = "",
    @SerialName("is_done") val isDone: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RemoteNotificationDto(
    val id: String = "",
    @SerialName("org_id") val orgId: String = "",
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("target_user_id") val targetUserId: String? = null,
    val audience: String = "DIRECT_EMPLOYEE",
    val type: String = "GENERIC_NOTIFICATION",
    val title: String = "",
    val body: String = "",
    @SerialName("related_entity_id") val relatedEntityId: String? = null,
    @SerialName("related_entity_type") val relatedEntityType: String? = null,
    @SerialName("navigation_route") val navigationRoute: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)

@Serializable
data class CreateDirectEmployeeNotificationRequest(
    @SerialName("p_employee_id") val employeeId: String,
    @SerialName("p_title") val title: String,
    @SerialName("p_body") val body: String,
    @SerialName("p_type") val type: String = "GENERIC_NOTIFICATION",
    @SerialName("p_related_entity_id") val relatedEntityId: String? = null,
    @SerialName("p_related_entity_type") val relatedEntityType: String? = null,
    @SerialName("p_navigation_route") val navigationRoute: String? = null
)

@Serializable
data class CreateAudienceNotificationRequest(
    @SerialName("p_title") val title: String,
    @SerialName("p_body") val body: String,
    @SerialName("p_type") val type: String = "GENERIC_NOTIFICATION",
    @SerialName("p_related_entity_id") val relatedEntityId: String? = null,
    @SerialName("p_related_entity_type") val relatedEntityType: String? = null,
    @SerialName("p_navigation_route") val navigationRoute: String? = null
)

@Serializable
data class MarkNotificationReadRequest(
    @SerialName("p_notification_id") val notificationId: String
)

@Serializable
data class NotificationPageRequest(
    @SerialName("p_limit") val limit: Int,
    @SerialName("p_before_created_at") val beforeCreatedAt: String? = null,
    @SerialName("p_before_id") val beforeId: String? = null,
)

// ── جلسة جرد الصندوق (SYNC-014.c) ─────────────────────────────
