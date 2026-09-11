package com.verto.app.feature.invoice.data.sync

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.pullClientCredits
import com.verto.app.data.sync.pullInvoiceItems
import com.verto.app.data.sync.pullInvoices
import com.verto.app.data.sync.pullPayments
import com.verto.app.data.sync.pushFinancialOutbox
import com.verto.app.data.sync.pullFinancialInbox
import com.verto.app.data.sync.reconcileFinancialInbox
import com.verto.app.data.sync.pushClientCredits
import com.verto.app.data.sync.pushInvoiceDeletions
import com.verto.app.data.sync.pushInvoiceItems
import com.verto.app.data.sync.pushInvoices
import com.verto.app.data.sync.pushPayments
import com.verto.app.data.sync.pullPurchaseCyclePreInvoices
import com.verto.app.data.sync.pullPurchaseCyclePostInvoices
import com.verto.app.data.sync.pushPurchaseCyclePreInvoices
import com.verto.app.data.sync.pushPurchaseCyclePostInvoices

class InvoiceSyncParticipant(
    private val runtime: SyncRuntime
) : SyncParticipant {
    override val key: String = "invoices"

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(SyncOperationSlot.PUSH_PURCHASE_CYCLE_PRE_INVOICES, "push أوامر واستلامات المشتريات", SyncFailureMode.ABORT, execute = {
            runtime.pushPurchaseCyclePreInvoices(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_FINANCIAL_OUTBOX, "push أحداث الفواتير المالية", SyncFailureMode.ABORT, execute = {
            runtime.pushFinancialOutbox(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_INVOICES, "push الفواتير", SyncFailureMode.ABORT, execute = {
            runtime.pushInvoices(context.organizationId, context.userId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_INVOICE_ITEMS, "push بنود الفواتير", SyncFailureMode.ABORT, execute = {
            runtime.pushInvoiceItems(context.organizationId, context.userId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_PURCHASE_CYCLE_POST_INVOICES, "push مطابقة واستثناءات المشتريات", SyncFailureMode.ABORT, execute = {
            runtime.pushPurchaseCyclePostInvoices(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_PAYMENTS, "push المدفوعات", SyncFailureMode.ABORT, execute = {
            runtime.pushPayments(context.organizationId, context.userId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_CLIENT_CREDITS, "push الرصيد المقدَّم", SyncFailureMode.COLLECT, execute = {
            runtime.pushClientCredits(context.organizationId, context.userId)
        }),
        SyncOperation(SyncOperationSlot.DELETE_INVOICES, "حذف الفواتير", SyncFailureMode.COLLECT, execute = {
            runtime.pushInvoiceDeletions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_PURCHASE_CYCLE_PRE_INVOICES, "pull أوامر واستلامات المشتريات", SyncFailureMode.ABORT, execute = {
            runtime.pullPurchaseCyclePreInvoices(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_FINANCIAL_INBOX, "pull صندوق وارد الأحداث المالية", SyncFailureMode.ABORT, execute = {
            runtime.pullFinancialInbox(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVOICES, "pull الفواتير", SyncFailureMode.ABORT, execute = {
            runtime.pullInvoices(context.organizationId, context.deletions.invoiceIds)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVOICE_ITEMS, "pull بنود الفواتير", SyncFailureMode.ABORT, execute = {
            runtime.pullInvoiceItems(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_PURCHASE_CYCLE_POST_INVOICES, "pull مطابقة واستثناءات المشتريات", SyncFailureMode.ABORT, execute = {
            runtime.pullPurchaseCyclePostInvoices(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_PAYMENTS, "pull المدفوعات", SyncFailureMode.ABORT, execute = {
            runtime.pullPayments(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_FINANCIAL_RECONCILIATION, "تسوية تبعيات الأحداث المالية", SyncFailureMode.ABORT, execute = {
            runtime.reconcileFinancialInbox(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_CLIENT_CREDITS, "pull الرصيد المقدَّم", SyncFailureMode.COLLECT, execute = {
            runtime.pullClientCredits(context.organizationId)
        })
    )
}
