package com.verto.app.feature.invoice.application.port

import com.verto.app.feature.invoice.application.InvoiceSummary
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class InvoicePresentationPortContractTest {
    @Test
    fun `unknown invoice summary remains not found`() = runTest {
        val subject: InvoicePresentationPort = contractProxy<InvoicePresentationPort> { method ->
            when (method) {
                "observeSummary" -> flowOf<InvoiceSummary?>(null)
                else -> error("Unexpected contract method: $method")
            }
        }

        assertNull(subject.observeSummary("missing-invoice").first())
    }

    private inline fun <reified T> contractProxy(crossinline response: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            response(method.name)
        } as T
}
