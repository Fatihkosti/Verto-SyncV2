package com.verto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * audience يفصل "من يحق له رؤية الإشعار" عن نوع الحدث نفسه.
 * DIRECT_EMPLOYEE لموظف محدد، ALL_EMPLOYEES لكل موظفي المؤسسة،
 * و MANAGER_ONLY لا يظهر للموظفين.
 */
enum class NotificationAudience {
    DIRECT_EMPLOYEE,
    ALL_EMPLOYEES,
    MANAGER_ONLY
}

enum class NotificationType {
    GENERIC_NOTIFICATION,
    TEST_NOTIFICATION,
    MORNING_GREETING,
    CREDIT_SALE_INVOICE_CREATED,
    PAYMENT_RECORDED,
    PAYMENT_DUE_REMINDER,
    PAYMENT_OVERDUE,
    LOW_STOCK,
    PURCHASE_INVOICE_CREATED,
    GOODS_RECEIVED,
    NEW_COMMISSION,
    COMMISSION_WITHDRAWABLE,
    COMMISSION_PAID,
    BALANCE_CREDITED,
    NEW_INVOICE,
    WEEK_ENDING_SOON,
    INACTIVITY,
    WEEKLY_GOAL_ACHIEVED,
    NEW_CHAT_MESSAGE,
    WITHDRAWAL_APPROVED,
    WITHDRAWAL_REJECTED,
    WITHDRAWAL_COMPLETED,
    PROFILE_INCOMPLETE,
    WELCOME,
    ADMIN_COMMISSION_NEEDED,
    WITHDRAWAL_REQUESTED,
    MARKETER_REGISTERED,
    ADMIN_REMINDER,
    AUTODRIVE_JOIN_REQUEST,
    UNKNOWN,
}

@Serializable
@Entity(
    tableName = "notifications",
    indices = [
        Index("organizationId"),
        Index("branchId"),
        Index("targetUserId"),
        Index("audience"),
        Index("type"),
        Index("createdAt")
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val branchId: String? = null,
    val targetUserId: String? = null,
    val audience: NotificationAudience,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedEntityId: String? = null,
    val relatedEntityType: String? = null,
    val navigationRoute: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null
)
