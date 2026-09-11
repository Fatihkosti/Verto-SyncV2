package com.verto.app.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFailuresTest {

    @Test
    fun `extractor reads structured status code without parsing message`() {
        val source = FakeProviderException(
            statusCode = 429,
            errorCode = "rate_limited",
            message = "this prose must never be classified",
        )

        val metadata = RemoteFailureMetadataExtractor.extract(source)

        assertEquals(429, metadata?.statusCode)
        assertEquals("rate_limited", metadata?.code)
    }

    @Test
    fun `extractor reads ktor style response status value`() {
        val source = FakeResponseException(FakeResponse(FakeStatus(503)))

        val metadata = RemoteFailureMetadataExtractor.extract(source)

        assertEquals(503, metadata?.statusCode)
    }

    @Test
    fun `boundary wraps structured failure and classifier sees server status`() {
        val source = FakeProviderException(503, "upstream", "ignored")

        val wrapped = RemoteFailureBoundary.wrap(source)

        assertTrue(wrapped is RemoteFailureException)
        assertTrue(ErrorClassifier.classify(wrapped) is AppFailure.Server)
        assertSame(source, wrapped.cause)
    }

    @Test
    fun `boundary leaves unstructured failure untouched`() {
        val source = IllegalStateException("unknown")
        assertSame(source, RemoteFailureBoundary.wrap(source))
    }

    private class FakeProviderException(
        private val statusCode: Int,
        private val errorCode: String,
        message: String,
    ) : RuntimeException(message) {
        fun getStatusCode(): Int = statusCode
        fun getErrorCode(): String = errorCode
    }

    private class FakeResponseException(private val response: FakeResponse) : RuntimeException() {
        fun getResponse(): FakeResponse = response
    }

    private class FakeResponse(private val status: FakeStatus) {
        fun getStatus(): FakeStatus = status
    }

    private class FakeStatus(private val value: Int) {
        fun getValue(): Int = value
    }
}
