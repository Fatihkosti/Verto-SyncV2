package com.verto.app.feature.integration.optimal.domain.model

import java.security.MessageDigest

/**
 * Builds a compact, deterministic idempotency key from tenant, operation, local identity, and
 * contract/payload version. The length-prefixed canonical form prevents delimiter collisions.
 */
object OptimalIdempotencyKeyFactory {
    private const val SCHEME_VERSION = 1
    private const val PREFIX = "optimal-idem-v$SCHEME_VERSION"

    fun create(
        organizationId: String,
        operation: String,
        localIdentity: String,
        operationVersion: Int,
    ): String {
        val organization = normalized("organizationId", organizationId)
        val normalizedOperation = normalized("operation", operation).uppercase()
        val identity = normalized("localIdentity", localIdentity)
        require(operationVersion > 0) { "operationVersion must be positive" }

        val canonical = listOf(
            organization,
            normalizedOperation,
            identity,
            operationVersion.toString(),
        ).joinToString(separator = "|") { value -> "${value.length}:$value" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$PREFIX:$digest"
    }

    private fun normalized(name: String, value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$name is required" }
        require(normalized.none(Char::isISOControl)) { "$name contains a control character" }
        return normalized
    }
}
