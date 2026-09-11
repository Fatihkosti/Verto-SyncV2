package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceWriteGuardEntity
import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.FinancialInboxEntity
import com.verto.app.data.local.entity.withSearchKeys
import kotlinx.coroutines.flow.Flow


interface InvoiceWriteDao {
    // Local invoice creation must fail on duplicate id/external supplier reference; never REPLACE financial history.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInvoiceRaw(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertInvoicesRaw(invoices: List<InvoiceEntity>)

    @Transaction
    open suspend fun insertInvoice(invoice: InvoiceEntity): Long =
        insertInvoiceRaw(invoice.withSearchKeys())

    @Transaction
    open suspend fun insertInvoices(invoices: List<InvoiceEntity>) =
        insertInvoicesRaw(invoices.map(InvoiceEntity::withSearchKeys))

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ إصلاح Bug المزامنة — REPLACE يُشغّل CASCADE ويمحو invoice_items
    //
    // pullInvoices في SyncManager يستخدم REPLACE ← يحذف الفاتورة ← CASCADE
    // يمحو كل بنودها ← pullInvoiceItems يحاول الاستعادة لكن قد يفشل بصمت
    //
    // الحل: دالتان مخصصتان للـ PULL فقط:
    //   • insertInvoicesFromRemote  ← تتجاهل الموجودة (لا CASCADE)
    //   • updateInvoiceStatusFromRemote ← تُحدِّث الحقول المهمة بدون حذف
    // ─────────────────────────────────────────────────────────────────────────

    /** مخصص للـ PULL فقط — يتجاهل الفواتير الموجودة محلياً (لا CASCADE) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInvoicesFromRemoteRaw(invoices: List<InvoiceEntity>)

    @Transaction
    open suspend fun insertInvoicesFromRemote(invoices: List<InvoiceEntity>) =
        insertInvoicesFromRemoteRaw(invoices.map(InvoiceEntity::withSearchKeys))

    /** يُحدِّث الحقول الجوهرية بدون حذف الصف — لا يُشغّل CASCADE على invoice_items. */
    @Query("""
    UPDATE invoices SET
        status               = :status,
        totalAmount          = :totalAmount,
        total_amount_minor    = CAST(ROUND(:totalAmount * 100.0) AS INTEGER),
        description          = :description,
        notes                = :notes,
        dueDate              = :dueDate,
        imageUri             = :imageUri,
        category             = :category,
        isOwedToMe           = :isOwedToMe,
        invoiceNumber        = :invoiceNumber,
        invoiceNumberSearch  = :invoiceNumberSearch,
        notifyDaysBefore     = :notifyDaysBefore,
        notifyRepeatDays     = :notifyRepeatDays,
        notificationsEnabled = :notificationsEnabled,
        createdAt            = :createdAt,
        discount             = :discount,
        discount_minor        = CAST(ROUND(:discount * 100.0) AS INTEGER),
        commission           = :commission,
        commission_minor      = CAST(ROUND(:commission * 100.0) AS INTEGER),
        commission_beneficiary_client_id = :commissionBeneficiaryClientId,
        commission_source     = :commissionSource,
        createdBy            = CASE WHEN :createdBy != '' THEN :createdBy ELSE createdBy END,
        lifecycle_status     = CASE WHEN :voided = 1 THEN 'VOID' ELSE :lifecycleStatus END,
        lifecycle_version    = :lifecycleVersion,
        posted_at            = :postedAt,
        voided_at            = :voidedAt,
        void_reason          = :voidReason,
        void_write_id        = :voidWriteId,
        voided               = :voided,
        isDirty              = 0
    WHERE id = :id
""")
    abstract suspend fun updateInvoiceCoreFields(
        id                   : String,
        status               : String,
        totalAmount          : Double,
        description          : String,
        notes                : String,
        dueDate              : Long,
        imageUri             : String?,
        category             : String,
        isOwedToMe           : Boolean,
        invoiceNumber        : Int,
        invoiceNumberSearch  : String,
        notifyDaysBefore     : String,
        notifyRepeatDays     : Int,
        notificationsEnabled : Boolean,
        createdAt            : Long,
        discount             : Double,
        commission           : Double,
        commissionBeneficiaryClientId: String?,
        commissionSource     : String,
        createdBy            : String,
        lifecycleStatus      : String,
        lifecycleVersion     : Int,
        postedAt             : Long,
        voidedAt             : Long,
        voidReason           : String,
        voidWriteId          : String,
        voided               : Boolean
    )

    /** SYNC-017: تحديث ربط الشحنة فقط (يُستدعى من pull عند توفّر قيمة على السيرفر). */
    @Query("UPDATE invoices SET shipmentId = :shipmentId WHERE id = :id")
    abstract suspend fun updateInvoiceShipmentId(id: String, shipmentId: String?)

    @Query("UPDATE invoices SET purchase_scope = :purchaseScope WHERE id = :id")
    abstract suspend fun updateInvoicePurchaseScope(id: String, purchaseScope: String)

    @Query("UPDATE invoices SET supplier_invoice_ref = :supplierInvoiceReference, supplier_invoice_ref_normalized = :supplierInvoiceReferenceNormalized, purchase_order_id = :purchaseOrderId WHERE id = :id")
    abstract suspend fun updateInvoicePurchaseCycleLink(
        id: String,
        supplierInvoiceReference: String?,
        supplierInvoiceReferenceNormalized: String?,
        purchaseOrderId: String?,
    )

    @Query("""
        UPDATE invoices SET
            transaction_currency_code = :transactionCurrencyCode,
            functional_currency_code = :functionalCurrencyCode,
            transaction_amount_minor = :transactionAmountMinor,
            invoice_exchange_rate_snapshot = :invoiceExchangeRateSnapshot,
            exchange_rate_direction = :exchangeRateDirection,
            exchange_rate_timestamp = :exchangeRateTimestamp,
            exchange_rate_source = :exchangeRateSource,
            functional_amount_at_recognition_minor = :functionalAmountAtRecognitionMinor,
            legacy_currency_status = :legacyCurrencyStatus
        WHERE id = :id
    """)
    abstract suspend fun updateInvoiceCurrencySnapshot(
        id: String,
        transactionCurrencyCode: String,
        functionalCurrencyCode: String,
        transactionAmountMinor: Long,
        invoiceExchangeRateSnapshot: String,
        exchangeRateDirection: String,
        exchangeRateTimestamp: Long,
        exchangeRateSource: String,
        functionalAmountAtRecognitionMinor: Long,
        legacyCurrencyStatus: String,
    )


    @Query("SELECT * FROM invoices WHERE legacy_currency_status IN ('UNKNOWN','REVIEW_REQUIRED') ORDER BY createdAt DESC")
    abstract suspend fun getCurrencyReviewInvoicesSync(): List<InvoiceEntity>

    @Update
    abstract suspend fun updateInvoiceRaw(invoice: InvoiceEntity): Int

    @Transaction
    open suspend fun updateInvoice(invoice: InvoiceEntity) {
        val affected = updateInvoiceRaw(invoice.withSearchKeys())
        check(affected == 1) { "invoice update affected $affected rows; expected exactly one" }
    }

    /** F248: hard delete is legal only for drafts that never posted financial effects. */
    @Query("DELETE FROM invoices WHERE id = :id AND lifecycle_status = 'DRAFT'")
    abstract suspend fun deleteDraftInvoiceById(id: String): Int

    @Query("SELECT * FROM invoices WHERE clientId = :clientId")
    abstract suspend fun getInvoicesForClientSync(clientId: String): List<InvoiceEntity>

}
