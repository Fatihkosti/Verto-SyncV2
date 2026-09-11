package com.verto.app.core.error

/** Structured metadata supplied by a remote gateway. No server message is trusted as UI copy. */
data class RemoteFailureMetadata(
    val statusCode: Int? = null,
    val code: String? = null,
    val target: String? = null,
    val retryAfterMillis: Long? = null,
)

/**
 * Extracts only structured provider fields from a throwable chain.
 *
 * Throwable.message is deliberately never parsed: provider prose is unstable and can produce
 * misleading user diagnoses. Reflection keeps core/common independent from Supabase and Ktor
 * while still reading their stable status/code style accessors when present.
 */
object RemoteFailureMetadataExtractor {
    fun extract(root: Throwable): RemoteFailureMetadata? {
        var statusCode: Int? = null
        var code: String? = null
        var target: String? = null
        var retryAfterMillis: Long? = null

        for (throwable in causeChain(root)) {
            if (throwable is RemoteFailureException) {
                val metadata = throwable.metadata
                statusCode = statusCode ?: metadata.statusCode
                code = code ?: metadata.code
                target = target ?: metadata.target
                retryAfterMillis = retryAfterMillis ?: metadata.retryAfterMillis
                continue
            }

            statusCode = statusCode
                ?: readIntProperty(throwable, "getStatusCode", "getStatus")
                ?: readNestedStatus(throwable)
            code = code
                ?: readStringProperty(throwable, "getErrorCode", "getCode")
                ?: readNestedErrorCode(throwable)
            target = target ?: readStringProperty(throwable, "getTarget")
            retryAfterMillis = retryAfterMillis ?: readLongProperty(
                throwable,
                "getRetryAfterMillis",
                "getRetryAfterMilliseconds",
            )
        }

        return if (statusCode != null || code != null || target != null || retryAfterMillis != null) {
            RemoteFailureMetadata(
                statusCode = statusCode,
                code = code,
                target = target,
                retryAfterMillis = retryAfterMillis,
            )
        } else {
            null
        }
    }

    private fun readNestedStatus(target: Throwable): Int? {
        val response = readProperty(target, "getResponse") ?: return null
        val status = readProperty(response, "getStatus") ?: return null
        return valueAsInt(status)
    }

    private fun readNestedErrorCode(target: Throwable): String? {
        val error = readProperty(target, "getError") ?: return null
        return readStringProperty(error, "getErrorCode", "getCode")
    }

    private fun readIntProperty(target: Any, vararg getters: String): Int? =
        readProperty(target, *getters)?.let(::valueAsInt)

    private fun valueAsInt(value: Any): Int? = when (value) {
        is Number -> value.toInt()
        else -> value.toString().toIntOrNull()
            ?: (readProperty(value, "getValue") as? Number)?.toInt()
    }

    private fun readLongProperty(target: Any, vararg getters: String): Long? =
        readProperty(target, *getters)?.let { value ->
            when (value) {
                is Number -> value.toLong()
                else -> value.toString().toLongOrNull()
            }
        }

    private fun readStringProperty(target: Any, vararg getters: String): String? =
        readProperty(target, *getters)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun readProperty(target: Any, vararg getters: String): Any? {
        getters.forEach { getter ->
            val value = runCatching {
                val method = target.javaClass.methods
                    .firstOrNull { it.name == getter && it.parameterCount == 0 }
                    ?: target.javaClass.declaredMethods
                        .firstOrNull { it.name == getter && it.parameterCount == 0 }
                method?.also { it.isAccessible = true }?.invoke(target)
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

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

    private const val MAX_CAUSE_DEPTH = 12
}

/** Wraps a provider exception only when structured remote metadata is actually available. */
object RemoteFailureBoundary {
    fun wrap(throwable: Throwable): Throwable {
        if (
            throwable is RemoteFailureException ||
            throwable is ClassifiedFailureException ||
            throwable is BusinessRuleFailureException
        ) {
            return throwable
        }
        val metadata = RemoteFailureMetadataExtractor.extract(throwable) ?: return throwable
        return RemoteFailureException(metadata = metadata, cause = throwable)
    }
}

/**
 * Boundary exception for network/data adapters after they have parsed a structured remote error.
 * Feature code can migrate to this without depending on a specific HTTP/Supabase exception type.
 */
class RemoteFailureException(
    val metadata: RemoteFailureMetadata,
    cause: Throwable? = null,
) : RuntimeException("Remote failure ${metadata.code ?: metadata.statusCode ?: "unknown"}", cause)

/** Explicit domain/business failure. The code is stable; localized UI text belongs elsewhere. */
class BusinessRuleFailureException(
    val code: String,
    val target: String? = null,
    cause: Throwable? = null,
) : RuntimeException("Business rule $code", cause)

/** Adapter for code paths that already have an AppFailure but must return Result.failure(Throwable). */
class ClassifiedFailureException(
    val failure: AppFailure,
    cause: Throwable? = null,
) : RuntimeException("Classified failure ${failure.diagnosticCode}", cause)
