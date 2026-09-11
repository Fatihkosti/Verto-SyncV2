package com.verto.app.data.sync

import com.verto.app.core.error.RemoteFailureException
import com.verto.app.core.error.RemoteFailureMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRetryPolicyTest {

    @Test
    fun `403 is permission not authentication`() {
        val failure = remote(status = 403, code = "permission_denied")
        assertEquals(SyncFailureKind.PERMISSION, SyncRetryPolicy.classify(failure))
        assertFalse(SyncRetryPolicy.decide(failure, 0).retry)
    }

    @Test
    fun `429 stays rate limited and retries with backoff`() {
        val failure = remote(status = 429, code = "rate_limited")
        assertEquals(SyncFailureKind.RATE_LIMITED, SyncRetryPolicy.classify(failure))
        assertTrue(SyncRetryPolicy.decide(failure, 0).retry)
    }

    @Test
    fun `503 is server failure not network unavailable`() {
        val failure = remote(status = 503, code = "service_unavailable")
        assertEquals(SyncFailureKind.SERVER_FAILURE, SyncRetryPolicy.classify(failure))
        assertTrue(SyncRetryPolicy.decide(failure, 0).retry)
    }

    @Test
    fun `message containing 403 cannot manufacture authentication failure`() {
        val failure = IllegalStateException("403 forbidden jwt")
        assertEquals(SyncFailureKind.PERMANENT_PROTOCOL, SyncRetryPolicy.classify(failure))
    }

    @Test
    fun `structured validation remains validation`() {
        val failure = remote(status = 422, code = "payload_invalid")
        assertEquals(SyncFailureKind.VALIDATION, SyncRetryPolicy.classify(failure))
    }

    private fun remote(status: Int, code: String) = RemoteFailureException(
        metadata = RemoteFailureMetadata(statusCode = status, code = code),
        cause = IllegalStateException("ignored provider prose"),
    )
}
