package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity

data class InvoiceWithPaid(
    @Embedded val invoice: InvoiceEntity,
    val totalPaid: Double,
)

data class InvoiceSearchCard(
    @Embedded val invoice: InvoiceEntity,
    val highlightedItem: String?,
)

data class InvoiceActivityRow(
    @Embedded val invoice: InvoiceEntity,
    val partyName: String,
)

data class InvoiceFinancialPendingRow(
    val invoiceId: String,
    val invoiceNumber: Int,
    val partyId: String,
    val partyName: String,
    val partyPhone: String,
    val totalAmount: Double,
    val totalPaid: Double,
    val dueDate: Long,
    val createdAt: Long,
    val category: InvoiceCategory,
)

@Dao
interface InvoiceDao :
    InvoiceReadDao,
    InvoiceWriteDao,
    InvoiceReportingDao,
    InvoiceSyncDao,
    InvoiceItemDao,
    InvoiceAggregateTransactionDao,
    InvoiceFinancialEventDao,
    FinancialMaterializationDao

