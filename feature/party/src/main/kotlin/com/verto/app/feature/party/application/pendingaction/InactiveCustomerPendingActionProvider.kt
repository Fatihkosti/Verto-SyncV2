package com.verto.app.feature.party.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
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

data class InactiveCustomerRecord(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val createdAtEpochMillis: Long,
    val lastSaleAtEpochMillis: Long?,
    val saleCount: Int,
)

interface InactiveCustomerPendingActionSource {
    fun observeCustomers(
        organizationId: String,
        inactiveCutoffEpochMillis: Long,
        highPriorityCutoffEpochMillis: Long,
    ): Flow<List<InactiveCustomerRecord>>
}

interface InactiveCustomerPendingActionClock {
    fun observeNowEpochMillis(): Flow<Long>
}

class InactiveCustomerPendingActionProvider @Inject constructor(
    private val source: InactiveCustomerPendingActionSource,
    private val clock: InactiveCustomerPendingActionClock,
    private val sessionReader: SessionReader,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id) {
            emit(emptyList())
            return@flow
        }
        if (!context.allows(HomePermissionKeys.CLIENTS_VIEW)) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            clock.observeNowEpochMillis().flatMapLatest { now ->
                require(now >= 0L) { "nowEpochMillis must not be negative" }
                source.observeCustomers(
                    organizationId = context.organizationId,
                    inactiveCutoffEpochMillis = (now - INACTIVE_AFTER_MS).coerceAtLeast(0L),
                    highPriorityCutoffEpochMillis = (now - INACTIVE_AFTER_MS - CRITICAL_AFTER_MS).coerceAtLeast(0L),
                ).map { customers ->
                    customers.mapNotNull { it.toPendingAction(now) }
                        .sortedWith(PROVIDER_RANKING)
                }
            },
        )
    }

    private fun InactiveCustomerRecord.toPendingAction(nowEpochMillis: Long): PendingAction? {
        val lastSaleAt = lastSaleAtEpochMillis ?: return null
        if (saleCount <= 0) return null
        val inactiveAt = lastSaleAt + INACTIVE_AFTER_MS
        if (lastSaleAt < 0L || inactiveAt > nowEpochMillis) return null
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_CUSTOMER_INACTIVE, customerId)
        val destination = HomeDestination(
            id = HomeDestinationIds.PARTY_DETAILS,
            arguments = mapOf("partyId" to customerId, "isSupplier" to "false"),
        )
        return PendingAction(
            eventKey = eventKey,
            title = "عميل توقف عن الشراء",
            summary = "$customerName • لم يسجل شراءً منذ 90 يومًا",
            occurredAtEpochMillis = inactiveAt,
            priority = if (nowEpochMillis - inactiveAt >= CRITICAL_AFTER_MS) {
                PendingActionPriority.HIGH
            } else {
                PendingActionPriority.NORMAL
            },
            destination = destination,
            section = PendingActionSection.CUSTOMER,
            actions = buildList {
                if (phone.isNotBlank()) {
                    add(
                        HomeAction(
                            id = "call_customer",
                            label = "اتصال",
                            destination = HomeDestination(
                                id = HomeDestinationIds.PARTY_CALL,
                                arguments = mapOf("partyId" to customerId, "phone" to phone),
                            ),
                            requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                        ),
                    )
                    add(
                        HomeAction(
                            id = "whatsapp_customer",
                            label = "واتساب",
                            destination = HomeDestination(
                                id = HomeDestinationIds.PARTY_WHATSAPP,
                                arguments = mapOf("partyId" to customerId, "phone" to phone),
                            ),
                            requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                        ),
                    )
                }
                add(
                    HomeAction(
                        id = "open_customer",
                        label = "فتح",
                        destination = destination,
                        requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                    ),
                )
                add(
                    HomeAction(
                        id = "remind_customer",
                        label = "تذكير",
                        destination = HomeDestination(
                            id = HomeDestinationIds.PENDING_ACTION_REMIND,
                            arguments = mapOf("eventKey" to eventKey),
                        ),
                        requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                    ),
                )
            },
            requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
        )
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
        const val PROVIDER_ID = "party.inactive.customer"
        const val EVENT_CUSTOMER_INACTIVE = "customer_inactive_90_days"
        const val INACTIVE_AFTER_MS: Long = 90L * 86_400_000L
        private const val CRITICAL_AFTER_MS: Long = 90L * 86_400_000L
    }
}
