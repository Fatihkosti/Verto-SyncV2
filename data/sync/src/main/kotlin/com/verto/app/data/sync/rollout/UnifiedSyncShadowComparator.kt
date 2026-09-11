package com.verto.app.data.sync.rollout

import java.security.MessageDigest

/** Privacy-safe shadow evidence. This class has no Room, cursor, outbox or remote mutation dependency. */
data class UnifiedSyncShadowComparison(
    val aggregateType: String,
    val scopeId: String,
    val serverRevisionRange: String,
    val rowCount: Int,
    val canonicalDigest: String,
    val comparisonStatus: String,
    val mismatchCategory: String?,
)

object UnifiedSyncShadowComparator {
    fun digestCanonicalLines(lines: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        lines.sorted().forEach { line ->
            digest.update(line.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun compareDigests(
        aggregateType: String,
        scopeId: String,
        serverRevisionRange: String,
        authoritativeRowCount: Int,
        authoritativeDigest: String,
        shadowRowCount: Int,
        shadowDigest: String,
        pendingLocalIntent: Boolean = false,
        visibilityDifferenceExpected: Boolean = false,
        conflictReviewExpected: Boolean = false,
    ): UnifiedSyncShadowComparison {
        val equal = authoritativeRowCount == shadowRowCount && authoritativeDigest == shadowDigest
        val category = when {
            equal -> null
            pendingLocalIntent -> "EXPECTED_PENDING_LOCAL"
            visibilityDifferenceExpected -> "EXPECTED_VISIBILITY_DIFFERENCE"
            conflictReviewExpected -> "EXPECTED_CONFLICT_REVIEW"
            else -> "UNEXPLAINED_DOMAIN_MISMATCH"
        }
        return UnifiedSyncShadowComparison(
            aggregateType = aggregateType,
            scopeId = scopeId,
            serverRevisionRange = serverRevisionRange,
            rowCount = shadowRowCount,
            canonicalDigest = shadowDigest,
            comparisonStatus = if (equal) "MATCH" else "MISMATCH",
            mismatchCategory = category,
        )
    }
}
