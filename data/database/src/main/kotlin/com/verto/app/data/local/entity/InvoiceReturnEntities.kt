package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * F252 immutable correction document. The posted invoice remains untouched; returns are additive facts.
 * documentType values: SALES_RETURN_CREDIT_NOTE | PURCHASE_RETURN_DEBIT_NOTE
 * settlementMode values: CREDIT_BALANCE | CASH_REFUND
 */
@Entity(
    tableName = "invoice_return_documents",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["original_invoice_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PartyIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["original_invoice_id"], name = "index_invoice_returns_original_invoice"),
        Index(value = ["client_id"], name = "index_invoice_returns_client"),
        Index(
            value = ["organization_id", "write_id"],
            unique = true,
            name = "index_invoice_returns_write_identity",
        ),
        Index(
            value = ["organization_id", "occurred_at", "id"],
            name = "index_invoice_returns_timeline",
        ),
    ],
)
data class InvoiceReturnDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "original_invoice_id") val originalInvoiceId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "document_type") val documentType: String,
    @ColumnInfo(name = "settlement_mode") val settlementMode: String,
    @ColumnInfo(name = "transaction_currency_code") val transactionCurrencyCode: String,
    @ColumnInfo(name = "functional_currency_code") val functionalCurrencyCode: String,
    @ColumnInfo(name = "transaction_amount_minor") val transactionAmountMinor: Long,
    @ColumnInfo(name = "functional_amount_minor") val functionalAmountMinor: Long,
    val reason: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "created_by_name") val createdByName: String,
    @ColumnInfo(name = "write_id") val writeId: String,
    @ColumnInfo(name = "source_version", defaultValue = "1") val sourceVersion: Int = 1,
)

@Entity(
    tableName = "invoice_return_lines",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceReturnDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["return_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InvoiceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["original_invoice_item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["return_id"], name = "index_invoice_return_lines_return"),
        Index(value = ["original_invoice_item_id"], name = "index_invoice_return_lines_original_item"),
        Index(value = ["inventory_item_id"], name = "index_invoice_return_lines_inventory_item"),
        Index(
            value = ["return_id", "original_invoice_item_id"],
            unique = true,
            name = "index_invoice_return_lines_identity",
        ),
    ],
)
data class InvoiceReturnLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "return_id") val returnId: String,
    @ColumnInfo(name = "original_invoice_item_id") val originalInvoiceItemId: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String,
    @ColumnInfo(name = "item_name_snapshot") val itemNameSnapshot: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_transaction_amount_minor") val unitTransactionAmountMinor: Long,
    @ColumnInfo(name = "transaction_amount_minor") val transactionAmountMinor: Long,
    @ColumnInfo(name = "unit_functional_amount_minor") val unitFunctionalAmountMinor: Long,
    @ColumnInfo(name = "functional_amount_minor") val functionalAmountMinor: Long,
    /** Sales return reverses historical COGS using this immutable snapshot; never currentBuyPrice. */
    @ColumnInfo(name = "unit_cost_at_sale_minor") val unitCostAtSaleMinor: Long,
    @ColumnInfo(name = "historical_cost_amount_minor") val historicalCostAmountMinor: Long,
    /** Original supplier unit price; useful for debit-note traceability. */
    @ColumnInfo(name = "original_purchase_unit_cost_minor") val originalPurchaseUnitCostMinor: Long,
)

@Entity(
    tableName = "invoice_return_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceReturnDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["return_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["return_id"], name = "index_invoice_return_allocations_return"),
        Index(value = ["payment_id"], name = "index_invoice_return_allocations_payment"),
        Index(
            value = ["return_id", "payment_id"],
            unique = true,
            name = "index_invoice_return_allocations_identity",
        ),
    ],
)
data class InvoiceReturnPaymentAllocationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "return_id") val returnId: String,
    @ColumnInfo(name = "payment_id") val paymentId: String,
    @ColumnInfo(name = "allocated_functional_amount_minor") val allocatedFunctionalAmountMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
