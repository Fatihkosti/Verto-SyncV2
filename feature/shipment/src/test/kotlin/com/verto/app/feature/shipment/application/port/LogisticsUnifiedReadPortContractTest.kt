package com.verto.app.feature.shipment.application.port

import com.verto.app.feature.shipment.application.model.LogisticsUnifiedReadRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsUnifiedReadPortContractTest {
    @Test
    fun `empty shipment read remains an empty collection`() = runTest {
        val subject: LogisticsUnifiedReadPort = EmptyReadPort()

        assertTrue(subject.observe("org").first().isEmpty())
    }

    @Test
    fun `default events for unknown shipment are empty`() = runTest {
        val subject: LogisticsUnifiedReadPort = EmptyReadPort()

        assertTrue(subject.events("org", "missing-shipment").isEmpty())
    }

    private class EmptyReadPort : LogisticsUnifiedReadPort {
        override fun observe(organizationId: String): Flow<List<LogisticsUnifiedReadRecord>> = flowOf(emptyList())
    }
}
