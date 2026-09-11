package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceReturnLineEntity
import com.verto.app.data.local.entity.InvoiceReturnPaymentAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InvoiceReturnDao {
    @Query("SELECT * FROM invoice_return_documents ORDER BY occurred_at ASC, id ASC")
    abstract fun observeAllDocuments(): Flow<List<InvoiceReturnDocumentEntity>>

    @Query("SELECT * FROM invoice_return_lines ORDER BY return_id ASC, id ASC")
    abstract fun observeAllLines(): Flow<List<InvoiceReturnLineEntity>>

    @Query("SELECT * FROM invoice_return_documents WHERE id = :returnId LIMIT 1")
    abstract suspend fun getDocument(returnId: String): InvoiceReturnDocumentEntity?

    @Query(
        "SELECT * FROM invoice_return_documents " +
            "WHERE organization_id = :organizationId AND write_id = :writeId LIMIT 1"
    )
    abstract suspend fun getByWriteId(organizationId: String, writeId: String): InvoiceReturnDocumentEntity?

    @Query(
        "SELECT * FROM invoice_return_documents WHERE original_invoice_id = :invoiceId " +
            "ORDER BY occurred_at ASC, id ASC"
    )
    abstract suspend fun getForInvoice(invoiceId: String): List<InvoiceReturnDocumentEntity>

    @Query("SELECT COUNT(*) FROM invoice_return_documents WHERE original_invoice_id = :invoiceId")
    abstract suspend fun countForInvoice(invoiceId: String): Int

    @Query("SELECT * FROM invoice_return_lines WHERE return_id = :returnId ORDER BY id ASC")
    abstract suspend fun getLines(returnId: String): List<InvoiceReturnLineEntity>

    @Query(
        "SELECT COALESCE(SUM(quantity), 0) FROM invoice_return_lines " +
            "WHERE original_invoice_item_id = :originalInvoiceItemId"
    )
    abstract suspend fun getReturnedQuantity(originalInvoiceItemId: String): Int

    @Query(
        "SELECT COALESCE(SUM(l.quantity), 0) FROM invoice_return_lines l " +
            "INNER JOIN invoice_return_documents d ON d.id = l.return_id " +
            "WHERE d.original_invoice_id = :invoiceId AND l.inventory_item_id = :inventoryItemId"
    )
    abstract suspend fun getReturnedQuantityForInvoiceInventoryItem(
        invoiceId: String,
        inventoryItemId: String,
    ): Int

    @Query(
        "SELECT * FROM invoice_return_payment_allocations WHERE return_id = :returnId " +
            "ORDER BY created_at ASC, id ASC"
    )
    abstract suspend fun getPaymentAllocations(returnId: String): List<InvoiceReturnPaymentAllocationEntity>

    @Query(
        "SELECT COALESCE(SUM(allocated_functional_amount_minor), 0) " +
            "FROM invoice_return_payment_allocations WHERE payment_id = :paymentId"
    )
    abstract suspend fun getAllocatedFunctionalForPayment(paymentId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertDocumentRaw(document: InvoiceReturnDocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertLinesRaw(lines: List<InvoiceReturnLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPaymentAllocationsRaw(rows: List<InvoiceReturnPaymentAllocationEntity>)

    @Transaction
    open suspend fun insertAggregate(
        document: InvoiceReturnDocumentEntity,
        lines: List<InvoiceReturnLineEntity>,
        allocations: List<InvoiceReturnPaymentAllocationEntity>,
    ) {
        require(lines.isNotEmpty()) { "return document requires at least one line" }
        check(insertDocumentRaw(document) != -1L) { "return document insert failed" }
        insertLinesRaw(lines)
        if (allocations.isNotEmpty()) insertPaymentAllocationsRaw(allocations)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertDocumentRemote(document: InvoiceReturnDocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertLinesRemote(lines: List<InvoiceReturnLineEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAllocationsRemote(rows: List<InvoiceReturnPaymentAllocationEntity>)

    /** Event-feed materialization is idempotent and never rewrites an existing immutable document. */
    @Transaction
    open suspend fun materializeRemote(
        document: InvoiceReturnDocumentEntity,
        lines: List<InvoiceReturnLineEntity>,
        allocations: List<InvoiceReturnPaymentAllocationEntity>,
    ) {
        val existing = getDocument(document.id)
        if (existing != null) {
            require(existing == document) { "remote return id conflicts with immutable local document" }
            require(getLines(document.id) == lines.sortedBy { it.id }) {
                "remote return lines conflict with immutable local document"
            }
            require(getPaymentAllocations(document.id) == allocations.sortedWith(compareBy<InvoiceReturnPaymentAllocationEntity> { it.createdAt }.thenBy { it.id })) {
                "remote return allocations conflict with immutable local document"
            }
            return
        }
        check(insertDocumentRemote(document) != -1L) { "remote return insert lost" }
        insertLinesRemote(lines)
        if (allocations.isNotEmpty()) insertAllocationsRemote(allocations)
        require(getLines(document.id) == lines.sortedBy { it.id }) {
            "remote return lines were not materialized exactly"
        }
        require(getPaymentAllocations(document.id) == allocations.sortedWith(
            compareBy<InvoiceReturnPaymentAllocationEntity> { it.createdAt }.thenBy { it.id }
        )) {
            "remote return allocations were not materialized exactly"
        }
    }
}
