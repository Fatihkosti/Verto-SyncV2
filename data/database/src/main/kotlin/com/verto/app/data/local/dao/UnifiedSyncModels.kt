package com.verto.app.data.local.dao

/** Producer-facing immutable draft intentionally has no sequence or delivery-metadata fields. */
data class UnifiedSyncMutationDraft(
    val mutationId: String,
    val organizationId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operationType: String,
    val baseVersion: Long?,
    val payloadVersion: Int,
    val payloadJson: String,
    val commandBatchId: String? = null,
    val commandOrder: Int? = null,
    val dependsOnMutationId: String? = null,
    val createdAt: Long,
)

enum class UnifiedInboxInsertResult { INSERTED, DUPLICATE }
enum class UnifiedConflictInsertResult { INSERTED, DUPLICATE }


data class OrchestrationGenerationState(val requested: Long, val drained: Long)
