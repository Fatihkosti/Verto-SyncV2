package com.verto.app.feature.dashboard.data.education

import com.verto.app.data.local.dao.EducationalContentDao
import com.verto.app.data.local.dao.EducationalTopicTargetRow
import com.verto.app.data.local.entity.EducationalTopicEntity
import com.verto.app.data.local.entity.EducationalTopicTargetEntity
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class EducationalContentSyncParticipant @Inject constructor(
    private val dao: EducationalContentDao,
) : SyncParticipant {
    override val key: String = "educational_content"

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(
            slot = SyncOperationSlot.PUSH_EDUCATIONAL_CONTENT,
            label = "رفع المواضيع التعليمية",
            failureMode = SyncFailureMode.COLLECT,
            execute = { skipWhenEducationalTablesAreUnavailable { pushTopics(context.organizationId) } },
        ),
        SyncOperation(
            slot = SyncOperationSlot.DELETE_EDUCATIONAL_CONTENT,
            label = "حذف المواضيع التعليمية",
            failureMode = SyncFailureMode.COLLECT,
            execute = { skipWhenEducationalTablesAreUnavailable { pushDeletions(context.organizationId) } },
        ),
        SyncOperation(
            slot = SyncOperationSlot.PULL_EDUCATIONAL_CONTENT,
            label = "تنزيل المواضيع التعليمية",
            failureMode = SyncFailureMode.COLLECT,
            execute = { skipWhenEducationalTablesAreUnavailable { pullTopics(context.organizationId) } },
        ),
    )

    private suspend fun skipWhenEducationalTablesAreUnavailable(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (!failure.isMissingEducationalTable()) throw failure
            android.util.Log.w(
                "EducationalContentSync",
                "Skipping educational sync: remote educational tables are unavailable",
            )
        }
    }

    private suspend fun pushTopics(organizationId: String) {
        val dirty = dao.getDirtyRows(organizationId).toSyncAggregates()
            .filter { it.topic.deletedAt == null }
        if (dirty.isEmpty()) return

        VertoSupabase.client.postgrest[TOPICS_TABLE].upsert(
            dirty.map { it.topic.toRemote() },
        ) { onConflict = "organization_id,topic_id" }

        dirty.forEach { aggregate ->
            VertoSupabase.client.postgrest[TARGETS_TABLE].delete {
                filter {
                    eq("organization_id", organizationId)
                    eq("topic_id", aggregate.topic.topicId)
                }
            }
            if (aggregate.targets.isNotEmpty()) {
                VertoSupabase.client.postgrest[TARGETS_TABLE].upsert(
                    aggregate.targets.map(EducationalTopicTargetEntity::toRemote),
                ) { onConflict = "organization_id,topic_id,target_type,target_value" }
            }
        }
        dao.markClean(organizationId, dirty.map { it.topic.topicId })
    }

    private suspend fun pushDeletions(organizationId: String) {
        val tombstones = dao.getDirtyRows(organizationId).toSyncAggregates()
            .filter { it.topic.deletedAt != null }
        tombstones.forEach { aggregate ->
            VertoSupabase.client.postgrest[TARGETS_TABLE].delete {
                filter {
                    eq("organization_id", organizationId)
                    eq("topic_id", aggregate.topic.topicId)
                }
            }
            VertoSupabase.client.postgrest[TOPICS_TABLE].delete {
                filter {
                    eq("organization_id", organizationId)
                    eq("topic_id", aggregate.topic.topicId)
                }
            }
            // Legacy compatibility push is not a unified ACK. Preserve the local tombstone until later reconciliation/cutover.
            dao.markClean(organizationId, listOf(aggregate.topic.topicId))
        }
    }

    private suspend fun pullTopics(organizationId: String) {
        val dirtyIds = dao.getDirtyRows(organizationId)
            .map(EducationalTopicTargetRow::topicId)
            .toSet()
        val remoteTopics = VertoSupabase.client.postgrest[TOPICS_TABLE].select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<EducationalTopicRemoteDto>()
            .filterNot { it.topicId in dirtyIds }
        val acceptedIds = remoteTopics.map(EducationalTopicRemoteDto::topicId).toSet()
        val remoteTargets = if (acceptedIds.isEmpty()) {
            emptyList()
        } else {
            VertoSupabase.client.postgrest[TARGETS_TABLE].select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<EducationalTargetRemoteDto>()
                .filter { it.topicId in acceptedIds }
        }
        dao.replaceCleanRemoteSnapshot(
            organizationId = organizationId,
            topics = remoteTopics.map(EducationalTopicRemoteDto::toEntity),
            targets = remoteTargets.map(EducationalTargetRemoteDto::toEntity),
        )
    }

    private companion object {
        const val TOPICS_TABLE = "educational_topics"
        const val TARGETS_TABLE = "educational_topic_targets"
    }
}

private fun Throwable.isMissingEducationalTable(): Boolean =
    generateSequence(this) { it.cause }.any { failure ->
        val message = failure.message.orEmpty()
        "public.educational_topics" in message || "public.educational_topic_targets" in message
    }

private data class EducationalSyncAggregate(
    val topic: EducationalTopicEntity,
    val targets: List<EducationalTopicTargetEntity>,
)

private fun List<EducationalTopicTargetRow>.toSyncAggregates(): List<EducationalSyncAggregate> =
    groupBy { it.organizationId to it.topicId }.values.map { rows ->
        val first = rows.first()
        EducationalSyncAggregate(
            topic = EducationalTopicEntity(
                organizationId = first.organizationId,
                topicId = first.topicId,
                title = first.title,
                summary = first.summary,
                fullContent = first.fullContent,
                category = first.category,
                isActive = first.isActive,
                createdByUserId = first.createdByUserId,
                createdAt = first.createdAt,
                updatedAt = first.updatedAt,
                isDirty = first.isDirty,
                deletedAt = first.deletedAt,
            ),
            targets = rows.mapNotNull { row ->
                val type = row.targetType ?: return@mapNotNull null
                val value = row.targetValue ?: return@mapNotNull null
                EducationalTopicTargetEntity(row.organizationId, row.topicId, type, value)
            }.distinct(),
        )
    }

@Serializable
private data class EducationalTopicRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("topic_id") val topicId: String,
    val title: String,
    val summary: String,
    @SerialName("full_content") val fullContent: String,
    val category: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_by_user_id") val createdByUserId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class EducationalTargetRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("topic_id") val topicId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_value") val targetValue: String,
)

private fun EducationalTopicEntity.toRemote() = EducationalTopicRemoteDto(
    organizationId = organizationId,
    topicId = topicId,
    title = title,
    summary = summary,
    fullContent = fullContent,
    category = category,
    isActive = isActive,
    createdByUserId = createdByUserId,
    createdAt = SupabaseDateParser.format(createdAt),
    updatedAt = SupabaseDateParser.format(updatedAt),
)

private fun EducationalTopicRemoteDto.toEntity() = EducationalTopicEntity(
    organizationId = organizationId,
    topicId = topicId,
    title = title,
    summary = summary,
    fullContent = fullContent,
    category = category,
    isActive = isActive,
    createdByUserId = createdByUserId,
    createdAt = SupabaseDateParser.parse(createdAt),
    updatedAt = SupabaseDateParser.parse(updatedAt),
    isDirty = false,
    deletedAt = null,
)

private fun EducationalTopicTargetEntity.toRemote() = EducationalTargetRemoteDto(
    organizationId = organizationId,
    topicId = topicId,
    targetType = targetType,
    targetValue = targetValue,
)

private fun EducationalTargetRemoteDto.toEntity() = EducationalTopicTargetEntity(
    organizationId = organizationId,
    topicId = topicId,
    targetType = targetType,
    targetValue = targetValue,
)
