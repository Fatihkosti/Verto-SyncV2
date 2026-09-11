package com.verto.app.feature.invoice.application.pendingaction

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.money.Money

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.invoice.domain.model.InvoiceFinancialState
import com.verto.app.utils.CurrencyFormatter
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionEventKey
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionSection
import com.verto.feature.dashboard.api.PendingActionProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class FinancialPendingInvoiceCategory { SALE, PURCHASE }

data class FinancialPendingInvoiceRecord(
    val invoiceId: String,
    val invoiceNumber: Int,
    val partyId: String,
    val partyName: String,
    val partyPhone: String,
    val totalAmount: Double,
    val totalPaid: Double,
    val dueDateEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val category: FinancialPendingInvoiceCategory,
    val isCredit: Boolean,
    val voided: Boolean,
)

interface FinancialPendingActionSource {
    fun observeDueCreditInvoices(
        organizationId: String,
        nowEpochMillis: Long,
        includeSales: Boolean,
        includePurchases: Boolean,
    ): Flow<List<FinancialPendingInvoiceRecord>>
}

interface FinancialPendingActionClock {
    fun observeNowEpochMillis(): Flow<Long>
}

class FinancialPendingActionProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val source: FinancialPendingActionSource,
    private val clock: FinancialPendingActionClock,
    private val sessionReader: SessionReader,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id) {
            emit(emptyList())
            return@flow
        }

        val includeSales = context.allows(HomePermissionKeys.SALES_VIEW)
        val includePurchases = context.allows(HomePermissionKeys.PURCHASES_VIEW)
        if (!includeSales && !includePurchases) {
            emit(emptyList())
            return@flow
        }

        emitAll(
            clock.observeNowEpochMillis().flatMapLatest { now ->
                require(now >= 0L) { "nowEpochMillis must not be negative" }
                source.observeDueCreditInvoices(
                    organizationId = context.organizationId,
                    nowEpochMillis = now,
                    includeSales = includeSales,
                    includePurchases = includePurchases,
                ).map { invoices ->
                    invoices.mapNotNull { invoice -> invoice.toPendingAction(context, now) }
                        .sortedWith(PROVIDER_RANKING)
                }
            },
        )
    }

    private fun FinancialPendingInvoiceRecord.toPendingAction(
        context: HomePermissionContext,
        nowEpochMillis: Long,
    ): PendingAction? {
        val viewPermission = viewPermission()
        if (!context.allows(viewPermission) || voided || !isCredit || dueDateEpochMillis <= 0L) return null

        val financialState = InvoiceFinancialState(
            totalMinor = Money.fromLegacyDouble(totalAmount).amountMinor,
            paidMinor = Money.fromLegacyDouble(totalPaid).amountMinor,
            isCredit = isCredit,
            dueDate = dueDateEpochMillis,
            now = nowEpochMillis,
        )
        if (!financialState.isActiveDebt || dueDateEpochMillis > nowEpochMillis) return null

        val eventType = when {
            financialState.isPartiallyPaid -> EVENT_PARTIAL_PAYMENT_DUE
            category == FinancialPendingInvoiceCategory.SALE -> EVENT_SALE_INVOICE_DUE
            else -> EVENT_SUPPLIER_INVOICE_DUE
        }
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, eventType, invoiceId)
        return PendingAction(
            eventKey = eventKey,
            title = when (eventType) {
                EVENT_PARTIAL_PAYMENT_DUE -> appContext.getString(com.verto.feature.invoice.R.string.invoice_v298_e2cf0f68a15b)
                EVENT_SALE_INVOICE_DUE -> appContext.getString(com.verto.feature.invoice.R.string.invoice_v298_50ca84ef8c18)
                else -> appContext.getString(com.verto.feature.invoice.R.string.invoice_v298_ab8f80ce1095)
            },
            summary = "فاتورة #$invoiceNumber • $partyName • المتبقي ${CurrencyFormatter.formatNoSymbol(financialState.remaining)}",
            occurredAtEpochMillis = dueDateEpochMillis,
            priority = when {
                nowEpochMillis - dueDateEpochMillis >= CRITICAL_OVERDUE_MS -> PendingActionPriority.CRITICAL
                dueDateEpochMillis < nowEpochMillis -> PendingActionPriority.HIGH
                else -> PendingActionPriority.NORMAL
            },
            destination = invoiceDestination(),
            actions = actions(context, eventKey, viewPermission),
            section = when (category) {
                FinancialPendingInvoiceCategory.SALE -> PendingActionSection.CUSTOMER
                FinancialPendingInvoiceCategory.PURCHASE -> PendingActionSection.SUPPLIER
            },
            requiredPermission = viewPermission,
        )
    }

    private fun FinancialPendingInvoiceRecord.actions(
        context: HomePermissionContext,
        eventKey: String,
        viewPermission: String,
    ): List<HomeAction> = buildList {
        if (partyPhone.isNotBlank()) {
            add(
                HomeAction(
                    id = "call_party",
                    label = "اتصال",
                    destination = HomeDestination(
                        id = HomeDestinationIds.PARTY_CALL,
                        arguments = mapOf("partyId" to partyId, "phone" to partyPhone),
                    ),
                    requiredPermission = viewPermission,
                ),
            )
            add(
                HomeAction(
                    id = "whatsapp_party",
                    label = "واتساب",
                    destination = HomeDestination(
                        id = HomeDestinationIds.PARTY_WHATSAPP,
                        arguments = mapOf("partyId" to partyId, "phone" to partyPhone),
                    ),
                    requiredPermission = viewPermission,
                ),
            )
        }

        val paymentPermission = paymentPermission()
        // Defense in depth: the action is not contributed unless permission is currently granted.
        // The destination screen and payment coordinator independently re-check this permission.
        if (context.allows(paymentPermission)) {
            add(
                HomeAction(
                    id = if (category == FinancialPendingInvoiceCategory.SALE) "record_payment" else "pay_supplier",
                    label = if (category == FinancialPendingInvoiceCategory.SALE) appContext.getString(com.verto.feature.invoice.R.string.invoice_v298_15deffe23699_2) else appContext.getString(com.verto.feature.invoice.R.string.invoice_v298_15deffe23699),
                    destination = paymentDestination(),
                    requiredPermission = paymentPermission,
                ),
            )
        }

        add(HomeAction("open_invoice", "فتح", invoiceDestination(), viewPermission))
        add(
            HomeAction(
                id = "remind_later",
                label = "تذكير",
                destination = HomeDestination(
                    id = HomeDestinationIds.PENDING_ACTION_REMIND,
                    arguments = mapOf("eventKey" to eventKey),
                ),
                requiredPermission = viewPermission,
            ),
        )
    }

    private fun FinancialPendingInvoiceRecord.invoiceDestination(): HomeDestination =
        HomeDestination(
            id = HomeDestinationIds.INVOICE_DETAILS,
            arguments = mapOf("invoiceId" to invoiceId),
        )

    private fun FinancialPendingInvoiceRecord.paymentDestination(): HomeDestination =
        if (category == FinancialPendingInvoiceCategory.SALE) {
            HomeDestination(
                id = HomeDestinationIds.INVOICE_PAYMENT,
                arguments = mapOf("invoiceId" to invoiceId, "partyId" to partyId),
            )
        } else {
            HomeDestination(
                id = HomeDestinationIds.PARTY_PAYMENT,
                arguments = mapOf("partyId" to partyId, "isSupplier" to "true", "invoiceId" to invoiceId),
            )
        }

    private fun FinancialPendingInvoiceRecord.viewPermission(): String = when (category) {
        FinancialPendingInvoiceCategory.SALE -> HomePermissionKeys.SALES_VIEW
        FinancialPendingInvoiceCategory.PURCHASE -> HomePermissionKeys.PURCHASES_VIEW
    }

    private fun FinancialPendingInvoiceRecord.paymentPermission(): String = when (category) {
        FinancialPendingInvoiceCategory.SALE -> HomePermissionKeys.CLIENTS_ADD_PAYMENT
        FinancialPendingInvoiceCategory.PURCHASE -> HomePermissionKeys.SUPPLIERS_ADD_PAYMENT
    }


    private val PROVIDER_RANKING: Comparator<PendingAction> =
        compareByDescending<PendingAction> { action -> action.priority.localRank }
            .thenBy { action -> action.occurredAtEpochMillis }
            .thenBy { action -> action.eventKey }

    private val PendingActionPriority.localRank: Int
        get() = when (this) {
            PendingActionPriority.LOW -> 0
            PendingActionPriority.NORMAL -> 1
            PendingActionPriority.HIGH -> 2
            PendingActionPriority.CRITICAL -> 3
        }

    companion object {
        const val PROVIDER_ID: String = "invoice.financial.pending"
        const val EVENT_SALE_INVOICE_DUE: String = "sale_invoice_due"
        const val EVENT_SUPPLIER_INVOICE_DUE: String = "supplier_invoice_due"
        const val EVENT_PARTIAL_PAYMENT_DUE: String = "partial_payment_due"
        private const val CRITICAL_OVERDUE_MS: Long = 7L * 86_400_000L
    }
}
