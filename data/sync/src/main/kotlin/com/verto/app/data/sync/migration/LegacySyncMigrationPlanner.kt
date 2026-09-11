package com.verto.app.data.sync.migration

import java.security.MessageDigest

internal object LegacySyncMigrationDisposition {
    const val MIGRATED = "MIGRATED"
    const val RECEIPT_CONFIRMED = "RECEIPT_CONFIRMED"
    const val REQUIRES_REVIEW = "REQUIRES_REVIEW"
}

internal object LegacySyncMigrationTarget {
    const val UNIFIED_OUTBOX = "UNIFIED_OUTBOX"
    const val STRONGER_SOURCE = "STRONGER_SOURCE"
    const val NONE = "NONE"
}

internal data class LegacySyncMigrationCandidate(
    val sourceKind: String,
    val sourceId: String,
    val aggregateType: String,
    val aggregateId: String,
    val sourceState: String,
    val businessIdentity: String,
    val sourceSequence: Long? = null,
    val commandBatchId: String? = null,
    val commandOrder: Int? = null,
    val dependsOnSourceId: String? = null,
    val targetKind: String,
    val targetMutationId: String? = null,
    val disposition: String,
    val reasonCode: String? = null,
    val createdAt: Long,
) {
    val fingerprint: String
        get() = LegacySyncMigrationPlanner.fingerprint(this)
}

internal object LegacySyncMigrationPlanner {
    private val receiptStates = setOf("ACKNOWLEDGED", "SYNCED")
    private val reviewStates = setOf("REQUIRES_REVIEW", "REJECTED", "BLOCKED")

    fun dispositionForState(state: String): String = when (state.trim().uppercase()) {
        in receiptStates -> LegacySyncMigrationDisposition.RECEIPT_CONFIRMED
        in reviewStates -> LegacySyncMigrationDisposition.REQUIRES_REVIEW
        else -> LegacySyncMigrationDisposition.MIGRATED
    }

    fun fingerprint(candidate: LegacySyncMigrationCandidate): String = sha256(
        listOf(
            "m03-v1",
            candidate.sourceKind,
            candidate.sourceId,
            candidate.aggregateType,
            candidate.aggregateId,
            candidate.businessIdentity,
        )
    )

    fun sourceDigest(candidates: Collection<LegacySyncMigrationCandidate>): String = sha256(
        candidates
            .sortedWith(compareBy(LegacySyncMigrationCandidate::sourceKind, LegacySyncMigrationCandidate::sourceId))
            .map { it.fingerprint }
    )

    private fun sha256(fields: List<String?>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
            digest.update(byteArrayOf(
                (bytes.size ushr 24).toByte(),
                (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(),
                bytes.size.toByte(),
            ))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
