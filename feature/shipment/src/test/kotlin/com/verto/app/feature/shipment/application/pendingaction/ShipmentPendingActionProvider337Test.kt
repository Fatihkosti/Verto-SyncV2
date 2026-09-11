package com.verto.app.feature.shipment.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.feature.shipment.application.EvaluateLogisticsDelayUseCase
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentPendingActionProvider337Test {
    @Test
    fun deniedPermission_doesNotCollectOperationalSourceOrClock() = runBlocking {
        val source = CountingOperationalSource(emptyList())
        val clock = CountingClock()
        val provider = ShipmentOperationalPendingActionProvider(source, clock, session(), null)

        assertTrue(provider.observePendingActions(context()).first().isEmpty())
        assertEquals(0, source.collections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun homePath_for1_20_100Shipments_neverCallsHeavyStoreHydration() = runBlocking {
        listOf(1, 20, 100).forEach { count ->
            val calls = mutableMapOf<String, Int>()
            val store = Proxy.newProxyInstance(
                LogisticsShipmentStorePort::class.java.classLoader,
                arrayOf(LogisticsShipmentStorePort::class.java),
            ) { _, method, _ ->
                calls[method.name] = (calls[method.name] ?: 0) + 1
                when (method.name) {
                    "toString" -> "CountingStore"
                    "hashCode" -> 1
                    "equals" -> false
                    else -> error("Home pending path must not invoke ${method.name}")
                }
            } as LogisticsShipmentStorePort
            val evaluator = EvaluateLogisticsDelayUseCase(
                store,
                object : LogisticsClockPort {
                    override fun now(): Long = 0L
                },
            )
            val records = (1..count).map { shipmentRecord(it) }
            val provider = ShipmentOperationalPendingActionProvider(
                CountingOperationalSource(records),
                CountingClock(),
                session(),
                evaluator,
            )

            provider.observePendingActions(context(setOf(HomePermissionKeys.SHIPMENTS_VIEW))).first()
            assertEquals("count=$count getShipment", 0, calls["getShipment"] ?: 0)
            assertEquals("count=$count listPartners", 0, calls["listPartners"] ?: 0)
        }
    }

    @Test
    fun receiptProviderDeniedPermission_doesNotCollectSource() = runBlocking {
        val source = CountingReceiptSource()
        val provider = ShipmentReceiptIssuePendingActionProvider(source, session())

        assertTrue(provider.observePendingActions(context()).first().isEmpty())
        assertEquals(0, source.collections)
    }

    private fun shipmentRecord(index: Int): ShipmentOperationalRecord {
        val id = "s-$index"
        val shipment = LogisticsShipment(
            id = id,
            organizationId = "org-a",
            shipmentNumber = index.toString(),
            sourceLocation = "A",
            destinationLocation = "B",
            state = LogisticsShipmentState.CUSTOMS,
            createdAt = index.toLong(),
        )
        return ShipmentOperationalRecord(
            shipmentId = id,
            shipmentTitle = "Shipment $index",
            state = LogisticsShipmentState.CUSTOMS,
            createdAtEpochMillis = index.toLong(),
            expectedArrivalDateEpochMillis = null,
            delaySnapshot = LogisticsShipmentAggregate(shipment = shipment),
        )
    }

    private class CountingOperationalSource(private val rows: List<ShipmentOperationalRecord>) : ShipmentOperationalPendingActionSource {
        var collections = 0
        override fun observeOperationalShipments(organizationId: String): Flow<List<ShipmentOperationalRecord>> = flow {
            collections++
            emit(rows)
        }
    }
    private class CountingReceiptSource : ShipmentReceiptIssuePendingActionSource {
        var collections = 0
        override fun observeReceiptIssues(organizationId: String): Flow<List<ShipmentReceiptIssueRecord>> = flow {
            collections++
            emit(emptyList())
        }
    }
    private class CountingClock : ShipmentOperationalPendingActionClock {
        var collections = 0
        override fun observeNowEpochMillis(): Flow<Long> = flow { collections++; emit(1_000_000_000_000L) }
    }
    private fun context(permissions: Set<String> = emptySet()) = HomePermissionContext("org-a", "user-a", permissions)
    private fun session() = FakeSessionReader()
    private class FakeSessionReader : SessionReader {
        private val user = CurrentUser("user-a", "User", "")
        private val org = CurrentOrganization("org-a")
        override val userId = flowOf(user.id); override val userName = flowOf(user.name); override val userPhone = flowOf(user.phone)
        override val role = flowOf("admin"); override val permissionsJson = flowOf(""); override val organizationId = flowOf(org.id)
        override val currentUser = flowOf(user); override val currentOrganization = flowOf(org)
        override suspend fun snapshot() = SessionState(user, org, "admin", "")
    }
}
