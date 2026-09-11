package com.verto.app.feature.party.application.search

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
import kotlin.math.abs

data class PartyHomeSearchRecord(
    val id: String,
    val name: String,
    val phone: String,
    val balance: Double,
    val isSupplier: Boolean,
    val isCompetitor: Boolean,
    val updatedAtEpochMillis: Long,
)

interface PartyHomeSearchSource {
    suspend fun search(
        organizationId: String,
        textQuery: String,
        phoneQuery: String,
        limit: Int,
    ): List<PartyHomeSearchRecord>
}

class PartyHomeSearchProvider @Inject constructor(
    private val source: PartyHomeSearchSource,
    private val sessionReader: SessionReader,
) : HomeSearchProvider {
    override val providerId: String = "party.business"

    override suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        if (!context.allows(HomePermissionKeys.CLIENTS_VIEW) || !context.matchesActiveSession()) {
            return emptyList()
        }
        val keys = SearchTextNormalizer.query(query.text) ?: return emptyList()
        return source.search(context.organizationId, keys.text, keys.phone, query.limit).map { record ->
            val details = HomeDestination(
                id = HomeDestinationIds.PARTY_DETAILS,
                arguments = mapOf(
                    "partyId" to record.id,
                    "isSupplier" to record.isSupplier.toString(),
                ),
            )
            val paymentPermission = if (record.isSupplier) {
                HomePermissionKeys.SUPPLIERS_ADD_PAYMENT
            } else {
                HomePermissionKeys.CLIENTS_ADD_PAYMENT
            }
            val actions = buildList {
                add(
                    HomeAction(
                        id = "open_party",
                        label = "فتح",
                        destination = details,
                        requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                    ),
                )
                if (context.allows(paymentPermission)) {
                    add(
                        HomeAction(
                            id = "record_party_payment",
                            label = "تسجيل دفعة",
                            destination = HomeDestination(
                                id = HomeDestinationIds.PARTY_PAYMENT,
                                arguments = mapOf(
                                    "partyId" to record.id,
                                    "isSupplier" to record.isSupplier.toString(),
                                ),
                            ),
                            requiredPermission = paymentPermission,
                        ),
                    )
                }
            }
            HomeSearchResult(
                key = record.id,
                title = record.name,
                subtitle = listOfNotNull(
                    record.phone.takeIf(String::isNotBlank),
                    record.balanceLabel(),
                ).joinToString(" • "),
                kind = HomeSearchKind.PARTY,
                destination = details,
                actions = actions,
                requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                updatedAtEpochMillis = record.updatedAtEpochMillis,
            )
        }
    }

    private fun PartyHomeSearchRecord.balanceLabel(): String {
        if (abs(balance) < 0.005) return "الرصيد 0"
        val amount = CurrencyFormatter.formatNoSymbol(abs(balance))
        val owner = when {
            isCompetitor && balance > 0 -> "عليه"
            isCompetitor -> "له"
            isSupplier && balance > 0 -> "له"
            isSupplier -> "عليه"
            balance > 0 -> "عليه"
            else -> "له"
        }
        return "$owner $amount"
    }

    private suspend fun HomePermissionContext.matchesActiveSession(): Boolean {
        val session = sessionReader.snapshot()
        return organizationId == session.organization.id && userId == session.user.id
    }
}
