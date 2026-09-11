package com.verto.app.core.error

/**
 * Canonical, presentation-agnostic failure contract for Verto.
 *
 * A failure is data, not a user-facing sentence. Feature/presentation layers decide the
 * localized copy and recovery action. This prevents technical exception text from becoming UI.
 */
sealed interface AppFailure {
    val source: FailureSource
    val retryAdvice: RetryAdvice
    val diagnosticCode: String

    data class NetworkUnavailable(
        override val diagnosticCode: String = "NETWORK_UNAVAILABLE",
    ) : AppFailure {
        override val source = FailureSource.NETWORK
        override val retryAdvice = RetryAdvice.USER_RETRY
    }

    data class ConnectionFailed(
        override val diagnosticCode: String = "CONNECTION_FAILED",
    ) : AppFailure {
        override val source = FailureSource.NETWORK
        override val retryAdvice = RetryAdvice.USER_RETRY
    }

    data class Timeout(
        override val diagnosticCode: String = "TIMEOUT",
    ) : AppFailure {
        override val source = FailureSource.NETWORK
        override val retryAdvice = RetryAdvice.USER_RETRY
    }

    data class Unauthorized(
        val remoteCode: String? = null,
        override val diagnosticCode: String = "UNAUTHORIZED",
    ) : AppFailure {
        override val source = FailureSource.SECURITY
        override val retryAdvice = RetryAdvice.AFTER_REAUTH
    }

    data class PermissionDenied(
        val remoteCode: String? = null,
        val target: String? = null,
        override val diagnosticCode: String = "PERMISSION_DENIED",
    ) : AppFailure {
        override val source = FailureSource.SECURITY
        override val retryAdvice = RetryAdvice.NEVER
    }

    data class Validation(
        val remoteCode: String? = null,
        val target: String? = null,
        override val diagnosticCode: String = "VALIDATION",
    ) : AppFailure {
        override val source = FailureSource.BUSINESS
        override val retryAdvice = RetryAdvice.AFTER_CORRECTION
    }

    data class BusinessRule(
        val code: String,
        val target: String? = null,
        override val diagnosticCode: String = code,
    ) : AppFailure {
        override val source = FailureSource.BUSINESS
        override val retryAdvice = RetryAdvice.AFTER_CORRECTION
    }

    data class NotFound(
        val remoteCode: String? = null,
        val target: String? = null,
        override val diagnosticCode: String = "NOT_FOUND",
    ) : AppFailure {
        override val source = FailureSource.REMOTE
        override val retryAdvice = RetryAdvice.NEVER
    }

    data class Conflict(
        val remoteCode: String? = null,
        val target: String? = null,
        override val diagnosticCode: String = "CONFLICT",
    ) : AppFailure {
        override val source = FailureSource.REMOTE
        override val retryAdvice = RetryAdvice.AFTER_CORRECTION
    }

    data class RateLimited(
        val retryAfterMillis: Long? = null,
        val remoteCode: String? = null,
        override val diagnosticCode: String = "RATE_LIMITED",
    ) : AppFailure {
        override val source = FailureSource.REMOTE
        override val retryAdvice = RetryAdvice.AUTOMATIC_BACKOFF
    }

    data class Server(
        val statusCode: Int? = null,
        val remoteCode: String? = null,
        override val diagnosticCode: String = "SERVER_ERROR",
    ) : AppFailure {
        override val source = FailureSource.REMOTE
        override val retryAdvice = RetryAdvice.AUTOMATIC_BACKOFF
    }

    data class RemoteRejected(
        val statusCode: Int? = null,
        val remoteCode: String? = null,
        val target: String? = null,
        override val diagnosticCode: String = "REMOTE_REJECTED",
    ) : AppFailure {
        override val source = FailureSource.REMOTE
        override val retryAdvice = RetryAdvice.NEVER
    }

    data class LocalStorage(
        val kind: LocalStorageKind,
        override val diagnosticCode: String = "LOCAL_STORAGE_${kind.name}",
    ) : AppFailure {
        override val source = FailureSource.DEVICE
        override val retryAdvice = when (kind) {
            LocalStorageKind.MISSING -> RetryAdvice.NEVER
            LocalStorageKind.CONSTRAINT -> RetryAdvice.AFTER_CORRECTION
            LocalStorageKind.FULL -> RetryAdvice.AFTER_CORRECTION
            LocalStorageKind.READ,
            LocalStorageKind.WRITE,
            LocalStorageKind.CORRUPT,
            LocalStorageKind.UNKNOWN,
            -> RetryAdvice.USER_RETRY
        }
    }

    data class Unknown(
        override val diagnosticCode: String = "UNKNOWN",
    ) : AppFailure {
        override val source = FailureSource.UNKNOWN
        override val retryAdvice = RetryAdvice.USER_RETRY
    }
}

enum class FailureSource {
    NETWORK,
    REMOTE,
    DEVICE,
    BUSINESS,
    SECURITY,
    UNKNOWN,
}

enum class RetryAdvice {
    NEVER,
    USER_RETRY,
    AUTOMATIC_BACKOFF,
    AFTER_REAUTH,
    AFTER_CORRECTION,
}

enum class LocalStorageKind {
    MISSING,
    READ,
    WRITE,
    FULL,
    CONSTRAINT,
    CORRUPT,
    UNKNOWN,
}
