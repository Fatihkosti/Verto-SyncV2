package com.verto.app.feature.payment.application.search

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.utils.DateUtils
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
import kotlin.math.abs

enum class PaymentHomeSearchCategory { SALE, PURCHASE }

data class PaymentHomeSearchRecord(
    val id: String,
    val invoiceId: String,
    val invoiceNumber: Int,
    val partyName: String,
    val amount: Double,
    val paidAtEpochMillis: Long,
    val reversedPaymentId: String?,
    val invoiceCategory: PaymentHomeSearchCategory,
    val invoiceVoided: Boolean,
)

interface PaymentHomeSearchSource {
    suspend fun search(
        organizationId: String,
        textQuery: String,
        identifierQuery: String,
        includeSales: Boolean,
        includePurchases: Boolean,
        limit: Int,
    ): List<PaymentHomeSearchRecord>
}

class PaymentHomeSearchProvider @Inject constructor(
    private val source: PaymentHomeSearchSource,
    private val sessionReader: SessionReader,
) : HomeSearchProvider {
    override val providerId: String = "payment.business"

    override suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        if (!context.matchesActiveSession()) return emptyList()
        val includeSales = context.allows(HomePermissionKeys.SALES_VIEW)
        val includePurchases = context.allows(HomePermissionKeys.PURCHASES_VIEW)
        if (!includeSales && !includePurchases) return emptyList()
        val keys = SearchTextNormalizer.query(query.text) ?: return emptyList()
        return source.search(
            organizationId = context.organizationId,
            textQuery = keys.text,
            identifierQuery = keys.identifier,
            includeSales = includeSales,
            includePurchases = includePurchases,
            limit = query.limit,
        ).filter { record -> context.allows(record.viewPermission()) }
            .map { record -> record.toResult(context) }
    }

    private fun PaymentHomeSearchRecord.toResult(context: HomePermissionContext): HomeSearchResult {
        val viewPermission = viewPermission()
        val invoiceDetails = HomeDestination(
            id = HomeDestinationIds.INVOICE_DETAILS,
            arguments = mapOf("invoiceId" to invoiceId, "paymentId" to id),
        )
        val isReversal = reversedPaymentId != null || amount < 0.0
        val actions = buildList {
            add(HomeAction("open_payment_invoice", "فتح الفاتورة", invoiceDetails, viewPermission))
            if (!isReversal && !invoiceVoided && context.allows(HomePermissionKeys.PAYMENTS_REVERSE)) {
                add(
                    HomeAction(
                        id = "reverse_payment",
                        label = "عكس الدفعة",
                        destination = HomeDestination(
                            id = HomeDestinationIds.PAYMENT_REVERSE,
                            arguments = mapOf("paymentId" to id, "invoiceId" to invoiceId),
                        ),
                        requiredPermission = HomePermissionKeys.PAYMENTS_REVERSE,
                    ),
                )
            }
        }
        return HomeSearchResult(
            key = id,
            title = "${if (isReversal) "عكس دفعة" else "دفعة"} ${CurrencyFormatter.formatNoSymbol(abs(amount))}",
            subtitle = "$partyName • ${DateUtils.formatDate(paidAtEpochMillis)} • فاتورة #$invoiceNumber",
            kind = HomeSearchKind.PAYMENT,
            destination = invoiceDetails,
            actions = actions,
            requiredPermission = viewPermission,
            updatedAtEpochMillis = paidAtEpochMillis,
        )
    }

    private fun PaymentHomeSearchRecord.viewPermission(): String = when (invoiceCategory) {
        PaymentHomeSearchCategory.SALE -> HomePermissionKeys.SALES_VIEW
        PaymentHomeSearchCategory.PURCHASE -> HomePermissionKeys.PURCHASES_VIEW
    }

    private suspend fun HomePermissionContext.matchesActiveSession(): Boolean {
        val session = sessionReader.snapshot()
        return organizationId == session.organization.id && userId == session.user.id
    }
}
