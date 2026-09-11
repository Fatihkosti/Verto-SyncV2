package com.verto.app.data.repository

import com.verto.app.core.error.RemoteFailureException
import com.verto.app.core.error.RemoteFailureMetadata
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialCommandFailureTest {

    @Test
    fun `legacy financial message is never exposed`() {
        val cause = IllegalStateException("sensitive server detail")

        val error = FinancialCommandException(
            command = "approve_withdrawal",
            message = "legacy user-facing server prose",
            cause = cause,
        )

        assertEquals("Financial command failed: approve_withdrawal", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun `structured remote metadata survives financial wrapper`() {
        val remote = RemoteFailureException(
            metadata = RemoteFailureMetadata(statusCode = 503, code = "upstream_unavailable"),
        )

        val error = financialCommandFailure("approve_withdrawal", remote)

        assertTrue(error is FinancialCommandException)
        val wrappedCause = (error as FinancialCommandException).cause
        assertTrue(wrappedCause is RemoteFailureException)
        assertEquals(503, (wrappedCause as RemoteFailureException).metadata.statusCode)
        assertEquals("upstream_unavailable", wrappedCause.metadata.code)
    }

    @Test(expected = CancellationException::class)
    fun `coroutine cancellation crosses financial boundary unchanged`() {
        val cancellation = CancellationException("cancel")
        try {
            financialCommandFailure("approve_withdrawal", cancellation)
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            throw actual
        }
    }
}
