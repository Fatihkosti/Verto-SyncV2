package com.verto.app.data.local

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable

import androidx.room.TypeConverter
import com.verto.app.data.local.entity.*

class Converters {
    @TypeConverter fun fromInvoiceType(value: InvoiceType): String = value.name
    @TypeConverter fun toInvoiceType(value: String): InvoiceType =
        runCatching { InvoiceType.valueOf(value) }.getOrDefault(InvoiceType.GOODS)

    @TypeConverter fun fromInvoiceCategory(value: InvoiceCategory): String = value.name
    @TypeConverter fun toInvoiceCategory(value: String): InvoiceCategory =
        runCatching { InvoiceCategory.valueOf(value) }.getOrDefault(InvoiceCategory.SALE)

    @TypeConverter fun fromInvoiceStatus(value: InvoiceStatus): String = value.name
    @TypeConverter fun toInvoiceStatus(value: String): InvoiceStatus =
        runCatching { InvoiceStatus.valueOf(value) }.getOrDefault(InvoiceStatus.CLOSED_CASH)

    @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toPaymentMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.CASH)

    @TypeConverter fun fromLegacyCurrencyStatus(value: LegacyCurrencyStatus): String = value.name
    @TypeConverter fun toLegacyCurrencyStatus(value: String): LegacyCurrencyStatus =
        runCatching { LegacyCurrencyStatus.valueOf(value) }.getOrDefault(LegacyCurrencyStatus.REVIEW_REQUIRED)

    @TypeConverter fun fromItemType(value: ItemType): String = value.name
    @TypeConverter fun toItemType(value: String): ItemType =
        runCatching { ItemType.valueOf(value) }.getOrDefault(ItemType.GOODS)

    @TypeConverter fun fromMovementType(value: MovementType): String = value.name
    @TypeConverter fun toMovementType(value: String): MovementType =
        runCatching { MovementType.valueOf(value) }.getOrDefault(MovementType.ADJUST)

    @TypeConverter fun fromInventoryMovementKind(value: InventoryMovementKind): String = value.name
    @TypeConverter fun toInventoryMovementKind(value: String): InventoryMovementKind =
        InventoryMovementKind.valueOf(value)

    @TypeConverter fun fromInventoryCostRevisionKind(value: InventoryCostRevisionKind): String = value.name
    @TypeConverter fun toInventoryCostRevisionKind(value: String): InventoryCostRevisionKind =
        InventoryCostRevisionKind.valueOf(value)

    @TypeConverter fun fromUnitType(value: UnitType): String = value.name
    @TypeConverter fun toUnitType(value: String): UnitType =
        runCatching { UnitType.valueOf(value) }.getOrDefault(UnitType.COUNT)

    @TypeConverter fun fromCashMovementType(value: CashMovementType): String = value.name
    @TypeConverter fun toCashMovementType(value: String): CashMovementType =
        runCatching { CashMovementType.valueOf(value) }.getOrDefault(CashMovementType.MANUAL_ADD)

    @TypeConverter fun fromAuditAction(value: AuditAction): String = value.name
    @TypeConverter fun toAuditAction(value: String): AuditAction =
        runCatching { AuditAction.valueOf(value) }.getOrDefault(AuditAction.UPDATE)

    @TypeConverter fun fromAuditTable(value: AuditTable): String = value.name
    @TypeConverter fun toAuditTable(value: String): AuditTable =
        runCatching { AuditTable.valueOf(value) }.getOrDefault(AuditTable.INVOICE)

    @TypeConverter fun fromNotificationAudience(value: NotificationAudience): String = value.name
    @TypeConverter fun toNotificationAudience(value: String): NotificationAudience =
        runCatching { NotificationAudience.valueOf(value) }.getOrDefault(NotificationAudience.ALL_EMPLOYEES)

    @TypeConverter fun fromNotificationType(value: NotificationType): String = value.name
    @TypeConverter fun toNotificationType(value: String): NotificationType =
        runCatching { NotificationType.valueOf(value) }.getOrDefault(NotificationType.UNKNOWN)
    @TypeConverter fun fromOptimalOutboxStatus(value: OptimalOutboxStatus): String = value.name
    @TypeConverter fun toOptimalOutboxStatus(value: String): OptimalOutboxStatus =
        runCatching { OptimalOutboxStatus.valueOf(value) }.getOrDefault(OptimalOutboxStatus.BLOCKED)

    @TypeConverter
    fun fromOptimalMaintenanceFollowUpStatus(value: OptimalMaintenanceFollowUpStatus): String = value.name

    @TypeConverter
    fun toOptimalMaintenanceFollowUpStatus(value: String): OptimalMaintenanceFollowUpStatus =
        runCatching { OptimalMaintenanceFollowUpStatus.valueOf(value) }
            .getOrDefault(OptimalMaintenanceFollowUpStatus.CANCELLED)

}
