package com.verto.app.data.local.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.verto.app.data.local.entity.*

/** B09 remote-only selective writes. Caller owns the entire business + inbox + version transaction. */
interface FinancialMaterializationDao {
    @Query("SELECT * FROM invoices WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoice(id: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoice(row: InvoiceEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRemoteInvoice(row: InvoiceEntity): Int

    @Query("SELECT * FROM invoice_items WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoiceItem(id: String): InvoiceItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoiceItem(row: InvoiceItemEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRemoteInvoiceItem(row: InvoiceItemEntity): Int

    @Query("SELECT * FROM invoice_due_installments WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoiceDueInstallment(id: String): InvoiceDueInstallmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoiceDueInstallment(row: InvoiceDueInstallmentEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRemoteInvoiceDueInstallment(row: InvoiceDueInstallmentEntity): Int

    @Query("SELECT * FROM payments WHERE id=:id LIMIT 1")
    suspend fun readRemotePayment(id: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemotePayment(row: PaymentEntity): Long

    @Query("SELECT * FROM payment_allocations WHERE id=:id LIMIT 1")
    suspend fun readRemotePaymentAllocation(id: String): PaymentAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemotePaymentAllocation(row: PaymentAllocationEntity): Long

    @Query("SELECT * FROM realized_fx_events WHERE id=:id LIMIT 1")
    suspend fun readRemoteRealizedFxEvent(id: String): RealizedFxEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteRealizedFxEvent(row: RealizedFxEventEntity): Long

    @Query("SELECT * FROM invoice_return_documents WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoiceReturnDocument(id: String): InvoiceReturnDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoiceReturnDocument(row: InvoiceReturnDocumentEntity): Long

    @Query("SELECT * FROM invoice_return_lines WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoiceReturnLine(id: String): InvoiceReturnLineEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoiceReturnLine(row: InvoiceReturnLineEntity): Long

    @Query("SELECT * FROM invoice_return_payment_allocations WHERE id=:id LIMIT 1")
    suspend fun readRemoteInvoiceReturnPaymentAllocation(id: String): InvoiceReturnPaymentAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemoteInvoiceReturnPaymentAllocation(row: InvoiceReturnPaymentAllocationEntity): Long

    @Query("""SELECT EXISTS(SELECT 1 FROM clients p JOIN party_roles r ON r.party_id=p.id
        WHERE p.id=:partyId AND r.organization_id=:organizationId
          AND (:role IS NULL OR r.role=:role))""")
    suspend fun hasRemotePartyReference(organizationId: String, partyId: String, role: String?): Boolean

    @Query("""SELECT EXISTS(SELECT 1 FROM inventory_items i WHERE i.id=:itemId AND (
        EXISTS(SELECT 1 FROM sync_entity_version v WHERE v.organization_id=:organizationId
          AND v.scope_id=:scopeId AND v.version_family='INVENTORY_ITEM' AND v.aggregate_id=i.id
          AND v.applied_server_version>0 AND v.applied_content_hash IS NOT NULL AND v.tombstone=0)
        OR EXISTS(SELECT 1 FROM sync_inbox b WHERE b.organization_id=:organizationId
          AND b.scope_id=:scopeId AND b.aggregate_type='INVENTORY_ITEM' AND b.aggregate_id=i.id
          AND b.operation_type='UPSERT' AND b.apply_state='APPLIED')
        OR EXISTS(SELECT 1 FROM invoice_items l JOIN invoices f ON f.id=l.invoiceId
          JOIN sync_entity_version v ON v.organization_id=f.organization_id AND v.aggregate_id=f.id
            AND v.version_family='FINANCIAL_INVOICE' AND v.scope_id=:scopeId
          WHERE l.inventoryItemId=i.id AND f.organization_id=:organizationId
            AND v.applied_server_version>0 AND v.applied_content_hash IS NOT NULL AND v.tombstone=0)
        OR EXISTS(SELECT 1 FROM inventory_movements m WHERE m.itemId=i.id
          AND m.organization_id=:organizationId AND m.server_sequence IS NOT NULL)
        OR EXISTS(SELECT 1 FROM inventory_cost_revisions c WHERE c.item_id=i.id
          AND c.organization_id=:organizationId AND c.cost_sequence IS NOT NULL)))""")
    suspend fun hasRemoteInventoryReference(organizationId: String, scopeId: String, itemId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM logistics_shipments WHERE id=:id AND organization_id=:organizationId)")
    suspend fun hasRemoteShipmentReference(organizationId: String, id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM purchase_orders WHERE id=:id AND organization_id=:organizationId)")
    suspend fun hasRemotePurchaseOrderReference(organizationId: String, id: String): Boolean

    @Query("""SELECT * FROM sync_entity_version WHERE organization_id=:organizationId
        AND version_family='FINANCIAL_INVOICE' AND aggregate_id=:invoiceId
        AND applied_server_version IS NOT NULL ORDER BY scope_id""")
    suspend fun readRemoteFinancialAuthorities(organizationId: String, invoiceId: String): List<SyncEntityVersionEntity>

    @Query("""DELETE FROM invoice_items WHERE id=:id AND invoiceId=:invoiceId
        AND EXISTS(SELECT 1 FROM invoices i WHERE i.id=:invoiceId AND i.organization_id=:organizationId)
        AND NOT EXISTS(SELECT 1 FROM invoice_return_lines r WHERE r.original_invoice_item_id=:id)
        AND NOT EXISTS(SELECT 1 FROM purchase_invoice_match_lines m WHERE m.invoice_item_id=:id)""")
    suspend fun deleteRemoteInvoiceItem(organizationId: String, invoiceId: String, id: String): Int

    @Query("""DELETE FROM invoice_due_installments WHERE id=:id AND invoice_id=:invoiceId
        AND EXISTS(SELECT 1 FROM invoices i WHERE i.id=:invoiceId AND i.organization_id=:organizationId)""")
    suspend fun deleteRemoteDueInstallment(organizationId: String, invoiceId: String, id: String): Int

    @Query("""SELECT EXISTS(SELECT 1 FROM invoice_return_lines WHERE original_invoice_item_id=:id)
        OR EXISTS(SELECT 1 FROM purchase_invoice_match_lines WHERE invoice_item_id=:id)""")
    suspend fun remoteInvoiceItemHasProtectedReference(id: String): Boolean

    // A sequence swap must not collide with the unique (invoice_id,sequence) index. Negative temporary
    // slots exist only inside the caller's transaction; all committed DTO sequences remain positive.
    @Query("UPDATE invoice_due_installments SET sequence=:temporarySequence WHERE id=:id AND invoice_id=:invoiceId")
    suspend fun stageRemoteDueSequence(invoiceId: String, id: String, temporarySequence: Int): Int

    @Query("SELECT * FROM inventory_movements WHERE id=:id LIMIT 1")
    suspend fun readRemoteEffectMovement(id: String): InventoryMovementEntity?

    @Query("SELECT * FROM inventory_cost_revisions WHERE cost_revision_id=:id LIMIT 1")
    suspend fun readRemoteEffectCost(id: String): InventoryCostRevisionEntity?

    @Query("SELECT * FROM cash_register_movements WHERE id=:id LIMIT 1")
    suspend fun readRemoteEffectCash(id: String): CashRegisterMovementEntity?

    @Query("SELECT * FROM client_credits WHERE id=:id LIMIT 1")
    suspend fun readRemoteEffectCredit(id: String): ClientCreditEntity?

    @Query("SELECT * FROM commission_payments WHERE id=:id LIMIT 1")
    suspend fun readRemoteEffectCommission(id: String): CommissionPaymentEntity?

    @Query("""SELECT invoiceId FROM invoice_items WHERE :type='INVOICE_ITEM' AND id=:id
        UNION ALL SELECT invoice_id FROM invoice_due_installments WHERE :type='INVOICE_DUE_INSTALLMENT' AND id=:id
        UNION ALL SELECT invoiceId FROM payments WHERE :type='PAYMENT' AND id=:id
        UNION ALL SELECT invoice_id FROM payment_allocations WHERE :type='PAYMENT_ALLOCATION' AND id=:id
        UNION ALL SELECT invoice_id FROM realized_fx_events WHERE :type='REALIZED_FX_EVENT' AND id=:id
        UNION ALL SELECT original_invoice_id FROM invoice_return_documents WHERE :type='INVOICE_RETURN' AND id=:id
        UNION ALL SELECT r.original_invoice_id FROM invoice_return_lines l
          JOIN invoice_return_documents r ON r.id=l.return_id WHERE :type='INVOICE_RETURN_LINE' AND l.id=:id
        UNION ALL SELECT r.original_invoice_id FROM invoice_return_payment_allocations a
          JOIN invoice_return_documents r ON r.id=a.return_id
          WHERE :type='INVOICE_RETURN_PAYMENT_ALLOCATION' AND a.id=:id
        LIMIT 1""")
    suspend fun readRemoteFinancialRoot(type: String, id: String): String?

}
