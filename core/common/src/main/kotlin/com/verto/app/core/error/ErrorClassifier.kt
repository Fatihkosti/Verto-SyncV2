package com.verto.app.core.error

import com.verto.app.data.model.PermissionDeniedException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/**
 * Single source of truth for classifying technical failures.
 *
 * Important: this classifier never parses Throwable.message to guess user intent. Structured
 * server metadata must enter through [RemoteFailureException]. Coroutine cancellation is control
 * flow and is rethrown rather than converted to a visible error.
 */
object ErrorClassifier {

    fun classify(throwable: Throwable): AppFailure {
        val chain = causeChain(throwable)

        chain.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
        chain.filterIsInstance<ClassifiedFailureException>().firstOrNull()?.let { return it.failure }
        chain.filterIsInstance<RemoteFailureException>().firstOrNull()?.let {
            return classifyRemote(it.metadata)
        }
        chain.filterIsInstance<BusinessRuleFailureException>().firstOrNull()?.let {
            return AppFailure.BusinessRule(code = it.code, target = it.target)
        }

        if (chain.any(::isTimeoutType)) return AppFailure.Timeout()
        if (chain.any { it is UnknownHostException || it is NoRouteToHostException }) {
            return AppFailure.NetworkUnavailable()
        }
        if (chain.any { it is ConnectException || it is SocketException }) {
            return AppFailure.ConnectionFailed()
        }
        if (chain.any { it is PermissionDeniedException || it is SecurityException }) {
            return AppFailure.PermissionDenied()
        }

        classifyLocalStorage(chain)?.let { return it }

        return AppFailure.Unknown(diagnosticCode = diagnosticCodeFor(throwable))
    }

    fun classifyRemote(metadata: RemoteFailureMetadata): AppFailure = when (metadata.statusCode) {
        400, 422 -> AppFailure.Validation(
            remoteCode = metadata.code,
            target = metadata.target,
        )
        401 -> AppFailure.Unauthorized(remoteCode = metadata.code)
        403 -> AppFailure.PermissionDenied(
            remoteCode = metadata.code,
            target = metadata.target,
        )
        404 -> AppFailure.NotFound(
            remoteCode = metadata.code,
            target = metadata.target,
        )
        408 -> AppFailure.Timeout(diagnosticCode = metadata.code ?: "REMOTE_TIMEOUT")
        409 -> AppFailure.Conflict(
            remoteCode = metadata.code,
            target = metadata.target,
        )
        429 -> AppFailure.RateLimited(
            retryAfterMillis = metadata.retryAfterMillis,
            remoteCode = metadata.code,
        )
        in 500..599 -> AppFailure.Server(
            statusCode = metadata.statusCode,
            remoteCode = metadata.code,
        )
        else -> AppFailure.RemoteRejected(
            statusCode = metadata.statusCode,
            remoteCode = metadata.code,
            target = metadata.target,
        )
    }

    fun isConnectivityFailure(throwable: Throwable): Boolean = when (classify(throwable)) {
        is AppFailure.NetworkUnavailable,
        is AppFailure.ConnectionFailed,
        is AppFailure.Timeout,
        -> true
        else -> false
    }

    private fun classifyLocalStorage(chain: List<Throwable>): AppFailure.LocalStorage? {
        val sqliteNames = chain.map { it.javaClass.simpleName }.toSet()
        return when {
            "SQLiteDatabaseCorruptException" in sqliteNames ->
                AppFailure.LocalStorage(LocalStorageKind.CORRUPT)
            "SQLiteFullException" in sqliteNames ->
                AppFailure.LocalStorage(LocalStorageKind.FULL)
            "SQLiteDiskIOException" in sqliteNames ->
                AppFailure.LocalStorage(LocalStorageKind.WRITE)
            "SQLiteConstraintException" in sqliteNames ->
                AppFailure.LocalStorage(LocalStorageKind.CONSTRAINT)
            sqliteNames.any { it == "SQLiteException" || it.endsWith("SQLiteException") } ->
                AppFailure.LocalStorage(LocalStorageKind.UNKNOWN)
            chain.any { it is FileNotFoundException } ->
                AppFailure.LocalStorage(LocalStorageKind.MISSING)
            // Generic IOException is intentionally left unknown. It is broader than both
            // network and local-storage I/O, so assigning either source would mislead users.
            chain.any { it is IOException } -> null
            else -> null
        }
    }

    private fun isTimeoutType(t: Throwable): Boolean =
        t is SocketTimeoutException ||
            t.javaClass.simpleName == "HttpRequestTimeoutException" ||
            t.javaClass.simpleName == "ConnectTimeoutException" ||
            t.javaClass.simpleName == "SocketTimeoutException"

    private fun causeChain(root: Throwable): List<Throwable> {
        val result = ArrayList<Throwable>(6)
        val seen = HashSet<Throwable>()
        var current: Throwable? = root
        while (current != null && result.size < MAX_CAUSE_DEPTH && seen.add(current)) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun diagnosticCodeFor(t: Throwable): String =
        t.javaClass.simpleName
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .uppercase()
            .ifBlank { "UNKNOWN" }

    private const val MAX_CAUSE_DEPTH = 12
}
