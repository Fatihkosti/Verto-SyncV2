package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** F254 local-only recoverable invoice editor draft. Never synced or posted as accounting data. */
@Entity(
    tableName = "invoice_editor_drafts",
    indices = [Index(value = ["organization_id", "updated_at"], name = "index_invoice_editor_drafts_org_updated")],
)
data class InvoiceEditorDraftEntity(
    @PrimaryKey @ColumnInfo(name = "draft_key") val draftKey: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @Embedded val route: InvoiceEditorDraftRouteColumns,
    @Embedded val financial: InvoiceEditorDraftFinancialColumns,
    @Embedded val composer: InvoiceEditorDraftComposerColumns,
    @Embedded val maintenance: InvoiceEditorDraftMaintenanceColumns,
)

data class InvoiceEditorDraftModeColumns(
    @ColumnInfo(name = "is_international") val isInternational: Boolean,
    @ColumnInfo(name = "is_sale") val isSale: Boolean,
    @ColumnInfo(name = "payment_mode") val paymentMode: String,
)

data class InvoiceEditorDraftRouteColumns(
    @ColumnInfo(name = "existing_invoice_id") val existingInvoiceId: String? = null,
    @ColumnInfo(name = "route_client_id") val routeClientId: String = "",
    @Embedded val mode: InvoiceEditorDraftModeColumns,
    @ColumnInfo(name = "selected_client_id") val selectedClientId: String = "",
    @ColumnInfo(name = "selected_date_millis") val selectedDateMillis: Long? = null,
)

data class InvoiceEditorDraftPersistenceColumns(
    @ColumnInfo(name = "write_id") val writeId: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class InvoiceEditorDraftFinancialColumns(
    @ColumnInfo(name = "due_days") val dueDays: String = "",
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "paid_amount") val paidAmount: String = "",
    @ColumnInfo(name = "discount") val discount: String = "",
    @ColumnInfo(name = "referrer_client_id") val referrerClientId: String = "",
    @ColumnInfo(name = "due_installments_json") val dueInstallmentsJson: String = "[]",
    @ColumnInfo(name = "transaction_currency_code") val transactionCurrencyCode: String = "",
    @ColumnInfo(name = "exchange_rate") val exchangeRate: String = "1",
    @Embedded val persistence: InvoiceEditorDraftPersistenceColumns,
)

data class InvoiceEditorDraftComposerColumns(
    @ColumnInfo(name = "draft_item_name") val draftItemName: String = "",
    @ColumnInfo(name = "draft_item_quantity") val draftItemQuantity: String = "1",
    @ColumnInfo(name = "draft_item_sell_price") val draftItemSellPrice: String = "",
    @ColumnInfo(name = "draft_item_buy_price") val draftItemBuyPrice: String = "",
    @ColumnInfo(name = "draft_inventory_item_id") val draftInventoryItemId: String = "",
)

data class InvoiceEditorDraftMaintenanceColumns(
    @ColumnInfo(name = "maintenance_enabled") val enabled: Boolean = false,
    @ColumnInfo(name = "maintenance_expanded") val expanded: Boolean = false,
    @Embedded val owner: InvoiceEditorDraftMaintenanceOwnerColumns = InvoiceEditorDraftMaintenanceOwnerColumns(),
    @Embedded val vehicle: InvoiceEditorDraftMaintenanceVehicleColumns = InvoiceEditorDraftMaintenanceVehicleColumns(),
    @ColumnInfo(name = "maintenance_notes") val notes: String = "",
)

data class InvoiceEditorDraftMaintenanceOwnerColumns(
    @ColumnInfo(name = "maintenance_owner_organization_id") val organizationId: String = "",
    @ColumnInfo(name = "maintenance_owner_client_id") val clientId: String = "",
    @ColumnInfo(name = "maintenance_record_id") val recordId: String = "",
    @ColumnInfo(name = "maintenance_created_at") val createdAt: Long = 0L,
)

data class InvoiceEditorDraftMaintenanceVehicleColumns(
    @ColumnInfo(name = "maintenance_vehicle_query") val query: String = "",
    @Embedded val selected: InvoiceEditorDraftVehicleSuggestionColumns? = null,
    @ColumnInfo(name = "maintenance_plate_number") val plateNumber: String = "",
    @ColumnInfo(name = "maintenance_driver_or_delegate") val driverOrDelegate: String = "",
)

data class InvoiceEditorDraftVehicleIdentityColumns(
    @ColumnInfo(name = "maintenance_vehicle_org_id") val organizationId: String? = null,
    @ColumnInfo(name = "maintenance_vehicle_client_id") val clientId: String? = null,
    @ColumnInfo(name = "maintenance_vehicle_remote_id") val remoteId: String? = null,
)

data class InvoiceEditorDraftVehicleSuggestionColumns(
    @Embedded val identity: InvoiceEditorDraftVehicleIdentityColumns = InvoiceEditorDraftVehicleIdentityColumns(),
    @ColumnInfo(name = "maintenance_vehicle_name") val name: String? = null,
    @ColumnInfo(name = "maintenance_vehicle_type") val type: String? = null,
    @ColumnInfo(name = "maintenance_vehicle_plate") val plate: String? = null,
    @ColumnInfo(name = "maintenance_vehicle_updated_at") val updatedAt: Long? = null,
)

@Entity(
    tableName = "invoice_editor_draft_lines",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEditorDraftEntity::class,
            parentColumns = ["draft_key"],
            childColumns = ["draft_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("draft_key"),
        Index(value = ["draft_key", "sort_order"], unique = true, name = "index_invoice_editor_draft_lines_order"),
    ],
)
data class InvoiceEditorDraftLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "draft_key") val draftKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @Embedded val item: InvoiceEditorDraftLineValues,
)

data class InvoiceEditorDraftLineValues(
    val name: String,
    val quantity: String,
    @ColumnInfo(name = "sell_price") val sellPrice: String,
    @ColumnInfo(name = "buy_price") val buyPrice: String,
    @ColumnInfo(name = "item_category") val itemCategory: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String,
)

@Entity(
    tableName = "invoice_editor_draft_maintenance_images",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEditorDraftEntity::class,
            parentColumns = ["draft_key"],
            childColumns = ["draft_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("draft_key"),
        Index(value = ["draft_key", "sort_order"], unique = true, name = "index_invoice_editor_draft_images_order"),
    ],
)
data class InvoiceEditorDraftMaintenanceImageEntity(
    @PrimaryKey @ColumnInfo(name = "image_id") val imageId: String,
    @ColumnInfo(name = "draft_key") val draftKey: String,
    @ColumnInfo(name = "local_uri") val localUri: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
