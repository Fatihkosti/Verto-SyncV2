package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.money.Money
import javax.inject.Inject

internal data class InvoiceEditSnapshot(
    val oldInvoice: InvoiceRecord?,
    val oldItems: List<InvoiceLine>,
    /** Legacy persistence projection; converted to Money at the policy boundary. */
    val totalPaid: Double,
)

internal data class InvoiceNonFinancialEdit(
    val updatedInvoice: InvoiceRecord,
    val fullyPaid: Boolean,
)

/**
 * v351 UX rule: a posted sale may be edited naturally, while the write coordinator
 * records/reconciles the financial correction. Pure descriptive edits keep the cheap path.
 */
internal class InvoiceEditPolicy @Inject constructor() {
    fun nonFinancialEditOrNull(
        snapshot: InvoiceEditSnapshot,
        draft: PreparedInvoiceDraft,
    ): InvoiceNonFinancialEdit? {
        val oldInvoice = snapshot.oldInvoice ?: return null
        when (oldInvoice.lifecycleStatus) {
            InvoiceLifecycleStatus.VOID -> throw IllegalStateException("لا يمكن تعديل فاتورة ملغاة")
            InvoiceLifecycleStatus.DRAFT -> return null
            InvoiceLifecycleStatus.POSTED -> Unit
        }

        val paid = Money.fromLegacyDouble(snapshot.totalPaid)
        val remaining = Money.ofMinor(oldInvoice.totalAmountMinor) - paid
        val fullyPaid = remaining.amountMinor <= 1L
        if (hasFinancialChange(oldInvoice, snapshot.oldItems, draft)) return null
        if (fullyPaid && draft.invoice.dueDate != oldInvoice.dueDate) {
            throw IllegalStateException("الفاتورة مسددة بالكامل — لا يمكن تعديل تاريخ الاستحقاق")
        }
        return InvoiceNonFinancialEdit(
            updatedInvoice = oldInvoice.copy(
                notes = draft.invoice.notes,
                dueDate = if (fullyPaid) oldInvoice.dueDate else draft.invoice.dueDate,
            ),
            fullyPaid = fullyPaid,
        )
    }

    private fun hasFinancialChange(
        oldInvoice: InvoiceRecord,
        oldItems: List<InvoiceLine>,
        draft: PreparedInvoiceDraft,
    ): Boolean {
        val oldSignatures = oldItems.map { itemSignature(it, oldInvoice.isOwedToMe) }.sorted()
        val newSignatures = draft.lines.map { itemSignature(it, draft.invoice.isOwedToMe) }.sorted()
        return oldInvoice.clientId != draft.invoice.clientId ||
            oldInvoice.status != draft.invoice.status ||
            oldInvoice.isOwedToMe != draft.invoice.isOwedToMe ||
            oldInvoice.totalAmountMinor != draft.invoice.totalAmountMinor ||
            oldInvoice.discountMinor != draft.invoice.discountMinor ||
            oldInvoice.commissionMinor != draft.invoice.commissionMinor ||
            oldInvoice.commissionBeneficiaryClientId != draft.invoice.commissionBeneficiaryClientId ||
            oldInvoice.commissionSource != draft.invoice.commissionSource ||
            oldSignatures != newSignatures
    }

    private fun itemSignature(line: InvoiceLine, sale: Boolean): String =
        "${line.inventoryItemId.trim()}|${line.itemName.trim().lowercase()}|${line.quantity}|${if (sale) line.sellPriceMinor else line.buyPriceMinor}"
}
