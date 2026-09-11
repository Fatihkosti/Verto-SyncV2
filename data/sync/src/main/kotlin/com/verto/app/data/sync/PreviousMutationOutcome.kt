package com.verto.app.data.sync

enum class PreviousMutationDisposition {
    RETRY_EXACT_FROZEN_BYTES,
    APPLY_MATCHING_RECEIPT,
    OUTCOME_UNKNOWN,
}

data class PreviousMutationEvidence(
    val originalMutationId: String,
    val originalBusinessIdentity: String,
    val frozenWireJson: String?,
    val frozenWireSha256: String?,
    val receiptMutationId: String?,
    val receiptRequestHash: String?,
)

/** B05 fail-closed decision; it never authorizes rebuilding content under an old mutation id. */
fun classifyPreviousMutation(evidence: PreviousMutationEvidence): PreviousMutationDisposition {
    require(evidence.originalMutationId.isNotBlank() && evidence.originalBusinessIdentity.isNotBlank())
    val wire = evidence.frozenWireJson
    val originalHash = evidence.frozenWireSha256
    if (wire != null && originalHash != null && sha256Utf8(wire) == originalHash) {
        return PreviousMutationDisposition.RETRY_EXACT_FROZEN_BYTES
    }
    if (evidence.receiptMutationId == evidence.originalMutationId &&
        originalHash != null && evidence.receiptRequestHash == originalHash
    ) return PreviousMutationDisposition.APPLY_MATCHING_RECEIPT
    return PreviousMutationDisposition.OUTCOME_UNKNOWN
}
