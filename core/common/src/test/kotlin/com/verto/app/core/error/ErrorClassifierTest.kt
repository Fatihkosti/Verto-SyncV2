package com.verto.app.core.error

import com.verto.app.data.model.PermissionDeniedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class ErrorClassifierTest {

    @Test
    fun unknownHost_isNetworkUnavailable() {
        assertTrue(ErrorClassifier.classify(UnknownHostException()) is AppFailure.NetworkUnavailable)
    }

    @Test
    fun connectException_isConnectionFailed_notOfflineGuess() {
        assertTrue(ErrorClassifier.classify(ConnectException()) is AppFailure.ConnectionFailed)
    }

    @Test
    fun timeout_isTimeout() {
        assertTrue(ErrorClassifier.classify(SocketTimeoutException()) is AppFailure.Timeout)
    }

    @Test
    fun genericIOException_isUnknown_notNetworkOrStorage() {
        val result = ErrorClassifier.classify(IOException("ambiguous io failure"))
        assertTrue(result is AppFailure.Unknown)
    }

    @Test
    fun fileNotFound_isLocalStorageMissing() {
        val result = ErrorClassifier.classify(FileNotFoundException("x"))
        assertEquals(AppFailure.LocalStorage(LocalStorageKind.MISSING), result)
    }

    @Test
    fun sqliteFull_isLocalStorageFull_notGenericWrite() {
        val result = ErrorClassifier.classify(SQLiteFullException())
        assertEquals(AppFailure.LocalStorage(LocalStorageKind.FULL), result)
    }

    @Test
    fun permissionException_isPermissionDenied_withoutReadingMessage() {
        val result = ErrorClassifier.classify(PermissionDeniedException("رسالة عربية تقنية"))
        assertTrue(result is AppFailure.PermissionDenied)
    }

    @Test
    fun remote409_isConflict_fromStructuredStatus() {
        val result = ErrorClassifier.classify(
            RemoteFailureException(
                RemoteFailureMetadata(statusCode = 409, code = "VERSION_CONFLICT", target = "invoice")
            )
        )
        assertEquals(
            AppFailure.Conflict(remoteCode = "VERSION_CONFLICT", target = "invoice"),
            result,
        )
    }

    @Test
    fun remote500_isRetryableServerFailure() {
        val result = ErrorClassifier.classifyRemote(RemoteFailureMetadata(statusCode = 503, code = "UPSTREAM"))
        assertTrue(result is AppFailure.Server)
        assertEquals(RetryAdvice.AUTOMATIC_BACKOFF, result.retryAdvice)
    }

    @Test(expected = CancellationException::class)
    fun cancellation_isRethrown() {
        ErrorClassifier.classify(CancellationException("cancel"))
    }

    @Test
    fun arabicText_doesNotMakeUnknownExceptionTrusted() {
        val result = ErrorClassifier.classify(IllegalStateException("خطأ قاعدة البيانات SQL relation missing"))
        assertTrue(result is AppFailure.Unknown)
    }

    @Test
    fun nestedUnknownHost_isStillNetworkUnavailable() {
        val result = ErrorClassifier.classify(RuntimeException("outer", UnknownHostException()))
        assertTrue(result is AppFailure.NetworkUnavailable)
    }

    private class SQLiteFullException : RuntimeException()
}
