package com.verto.app.feature.invoice.application.search

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.utils.SearchTextNormalizer
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import javax.inject.Inject

enum class InvoiceHomeSearchCategory { SALE, PURCHASE }
enum class InvoiceHomeSearchStatus { CASH, CREDIT }

data class InvoiceHomeSearchRecord(
    val id: String,
    val clientId: String,
    val invoiceNumber: Int,
    val highlightedItem: String,
    val totalAmount: Double,
    val category: InvoiceHomeSearchCategory,
    val status: InvoiceHomeSearchStatus,
    val voided: Boolean,
    val createdAtEpochMillis: Long,
)

interface InvoiceHomeSearchSource {
    suspend fun search(
        organizationId: String,
        numberQuery: String,
        includeSales: Boolean,
        includePurchases: Boolean,
        limit: Int,
    ): List<InvoiceHomeSearchRecord>
}

class InvoiceHomeSearchProvider @Inject constructor(
    private val source: InvoiceHomeSearchSource,
    private val sessionReader: SessionReader,
) : HomeSearchProvider {
    override val providerId: String = "invoice.business"

    override suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        if (!context.matchesActiveSession()) return emptyList()
        val includeSales = context.allows(HomePermissionKeys.SALES_VIEW)
        val includePurchases = context.allows(HomePermissionKeys.PURCHASES_VIEW)
        if (!includeSales && !includePurchases) return emptyList()
        val numberQuery = SearchTextNormalizer.query(query.text)
            ?.identifier
            ?.filter(Char::isDigit)
            ?.takeIf { it.length >= SearchTextNormalizer.MIN_PREFIX_LENGTH }
            ?: return emptyList()
        return source.search(
            organizationId = context.organizationId,
            numberQuery = numberQuery,
            includeSales = includeSales,
            includePurchases = includePurchases,
            limit = query.limit,
        ).filter { record -> context.allows(record.viewPermission()) }
            .map { record -> record.toResult(context) }
    }

    private fun InvoiceHomeSearchRecord.toResult(context: HomePermissionContext): HomeSearchResult {
        val viewPermission = viewPermission()
        val details = HomeDestination(
            id = HomeDestinationIds.INVOICE_DETAILS,
            arguments = mapOf("invoiceId" to id),
        )
        val actions = buildList {
            add(HomeAction("open_invoice", "فتح", details, viewPermission))
            val editPermission = editPermission()
            if (!voided && context.allows(editPermission)) {
                add(
                    HomeAction(
                        id = "edit_invoice",
                        label = "تعديل",
                        destination = HomeDestination(
                            id = HomeDestinationIds.INVOICE_EDIT,
                            arguments = mapOf("invoiceId" to id),
                        ),
                        requiredPermission = editPermission,
                    ),
                )
            }
            val paymentPermission = paymentPermission()
            if (!voided && status == InvoiceHomeSearchStatus.CREDIT && context.allows(paymentPermission)) {
                add(
                    HomeAction(
                        id = "record_invoice_payment",
                        label = "تسجيل دفعة",
                        destination = HomeDestination(
                            id = HomeDestinationIds.INVOICE_PAYMENT,
                            arguments = mapOf("invoiceId" to id, "partyId" to clientId),
                        ),
                        requiredPermission = paymentPermission,
                    ),
                )
            }
        }
        return HomeSearchResult(
            key = id,
            title = "فاتورة #$invoiceNumber",
            subtitle = listOf(
                highlightedItem.ifBlank { "بدون بنود" },
                CurrencyFormatter.formatNoSymbol(totalAmount),
                statusLabel(),
            ).joinToString(" • "),
            kind = HomeSearchKind.INVOICE,
            destination = details,
            actions = actions,
            requiredPermission = viewPermission,
            updatedAtEpochMillis = createdAtEpochMillis,
        )
    }

    private fun InvoiceHomeSearchRecord.viewPermission(): String = when (category) {
        InvoiceHomeSearchCategory.SALE -> HomePermissionKeys.SALES_VIEW
        InvoiceHomeSearchCategory.PURCHASE -> HomePermissionKeys.PURCHASES_VIEW
    }

    private fun InvoiceHomeSearchRecord.editPermission(): String = when (category) {
        InvoiceHomeSearchCategory.SALE -> HomePermissionKeys.SALES_EDIT
        InvoiceHomeSearchCategory.PURCHASE -> HomePermissionKeys.PURCHASES_EDIT
    }

    private fun InvoiceHomeSearchRecord.paymentPermission(): String = when (category) {
        InvoiceHomeSearchCategory.SALE -> HomePermissionKeys.CLIENTS_ADD_PAYMENT
        InvoiceHomeSearchCategory.PURCHASE -> HomePermissionKeys.SUPPLIERS_ADD_PAYMENT
    }

    private fun InvoiceHomeSearchRecord.statusLabel(): String = when {
        voided -> "ملغاة"
        status == InvoiceHomeSearchStatus.CREDIT -> "آجلة"
        else -> "نقدية"
    }

    private suspend fun HomePermissionContext.matchesActiveSession(): Boolean {
        val session = sessionReader.snapshot()
        return organizationId == session.organization.id && userId == session.user.id
    }
}
