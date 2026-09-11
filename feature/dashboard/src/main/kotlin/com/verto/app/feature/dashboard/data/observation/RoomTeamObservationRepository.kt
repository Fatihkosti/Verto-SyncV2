package com.verto.app.feature.dashboard.data.observation

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.TeamObservationDao
import com.verto.app.data.local.entity.TeamObservationEntity
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.sync.rollout.SyncAggregateOwnership
import com.verto.app.data.sync.rollout.SyncRolloutPolicy
import com.verto.app.utils.SupabaseDateParser
import com.verto.feature.dashboard.api.TeamObservation
import com.verto.feature.dashboard.api.TeamObservationRepository
import com.verto.feature.dashboard.api.TeamObservationCategory
import com.verto.feature.dashboard.api.TeamObservationStatus
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomTeamObservationRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: TeamObservationDao,
    private val outboxWriter: UnifiedOutboxWriter,
    private val sessionReader: SessionReader,
) : TeamObservationRepository {

    override fun observeCurrentOrganization(): Flow<List<TeamObservation>> =
        sessionReader.organizationId.flatMapLatest { organizationId ->
            dao.observeForOrganization(organizationId).map { rows -> rows.map(TeamObservationEntity::toModel) }
        }

    override suspend fun submit(text: String, category: TeamObservationCategory): TeamObservation {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "observation text is required" }
        val session = sessionReader.snapshot()
        require(session.organization.id.isNotBlank()) { "organization id is required" }
        require(session.user.id.isNotBlank()) { "user id is required" }
        val now = System.currentTimeMillis()
        val entity = TeamObservationEntity(
            organizationId = session.organization.id,
            observationId = UUID.randomUUID().toString(),
            text = normalized,
            category = category.name,
            authorUserId = session.user.id,
            authorName = session.user.name.trim().ifBlank { "موظف" },
            status = TeamObservationStatus.NEW.name,
            isImportant = false,
            createdAt = now,
            updatedAt = now,
            updatedByUserId = session.user.id,
            isDirty = true,
        )
        persistWithUnifiedOutbox(entity)
        return entity.toModel()
    }

    override suspend fun refreshFromRemote() {
        val organizationId = sessionReader.snapshot().organization.id
        if (organizationId.isBlank()) return
        pullRemote(organizationId)
    }

    override suspend fun setStatus(observationId: String, status: TeamObservationStatus) {
        update(observationId) { current, userId, now ->
            current.copy(
                status = status.name,
                updatedAt = now,
                updatedByUserId = userId,
                isDirty = true,
            )
        }
    }

    override suspend fun setImportant(observationId: String, important: Boolean) {
        update(observationId) { current, userId, now ->
            current.copy(
                isImportant = important,
                updatedAt = now,
                updatedByUserId = userId,
                isDirty = true,
            )
        }
    }

    private suspend fun update(
        observationId: String,
        transform: (TeamObservationEntity, String, Long) -> TeamObservationEntity,
    ) {
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id
        require(organizationId.isNotBlank()) { "organization id is required" }
        val current = dao.getById(organizationId, observationId)
            ?: error("team observation not found")
        persistWithUnifiedOutbox(transform(current, session.user.id, System.currentTimeMillis()))
    }

    private suspend fun persistWithUnifiedOutbox(entity: TeamObservationEntity) {
        val ownership = SyncRolloutPolicy.ownership(
            snapshot = SyncRolloutPolicy.snapshot(entity.organizationId),
            aggregateType = "TEAM_OBSERVATION",
        ).ownership
        val v2OwnsTransport = ownership in setOf(
            SyncAggregateOwnership.V2_AUTHORITATIVE,
            SyncAggregateOwnership.V2_PAUSED_SAFE,
            SyncAggregateOwnership.RETIRED_LEGACY,
        )

        database.withTransaction {
            // M04 captures one durable V2 intent for every producer action. Until M05 retires the
            // legacy transport, its dirty marker is only a compatibility mirror and never V2 authority.
            val capturedEntity = entity.copy(isDirty = !v2OwnsTransport)
            dao.upsert(capturedEntity)
            outboxWriter.enqueue(
                organizationId = capturedEntity.organizationId,
                aggregateType = "TEAM_OBSERVATION",
                aggregateId = capturedEntity.observationId,
                operationType = "UPSERT",
                payload = capturedEntity.toUnifiedPayload(),
                createdAt = capturedEntity.updatedAt,
            )
        }
    }

    internal suspend fun pushDirty(organizationId: String) {
        val dirty = dao.getDirty(organizationId)
        if (dirty.isEmpty()) return
        VertoSupabase.client.postgrest[TABLE].upsert(
            dirty.map(TeamObservationEntity::toRemote),
        ) { onConflict = "organization_id,observation_id" }
        dao.markClean(organizationId, dirty.map(TeamObservationEntity::observationId))
    }

    internal suspend fun pullRemote(organizationId: String) {
        val dirtyIds = dao.getDirty(organizationId).map(TeamObservationEntity::observationId).toSet()
        val remote = VertoSupabase.client.postgrest[TABLE].select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<TeamObservationRemoteDto>()
            .filterNot { it.observationId in dirtyIds }
            .map(TeamObservationRemoteDto::toEntity)
        if (remote.isNotEmpty()) dao.upsertRemote(remote)
    }

    companion object {
        internal const val TABLE: String = "team_observations"
    }
}

private fun TeamObservationEntity.toUnifiedPayload(): Map<String, Any?> = mapOf(
    "organizationId" to organizationId,
    "observationId" to observationId,
    "text" to text,
    "category" to category,
    "authorUserId" to authorUserId,
    "authorName" to authorName,
    "status" to status,
    "isImportant" to isImportant,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "updatedByUserId" to updatedByUserId,
)

private fun TeamObservationEntity.toModel(): TeamObservation = TeamObservation(
    id = observationId,
    text = text,
    category = runCatching { TeamObservationCategory.valueOf(category) }.getOrDefault(TeamObservationCategory.IDEA),
    authorUserId = authorUserId,
    authorName = authorName,
    status = runCatching { TeamObservationStatus.valueOf(status) }.getOrDefault(TeamObservationStatus.NEW),
    isImportant = isImportant,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
)

@Serializable
internal data class TeamObservationRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("observation_id") val observationId: String,
    val text: String,
    val category: String = TeamObservationCategory.IDEA.name,
    @SerialName("author_user_id") val authorUserId: String,
    @SerialName("author_name") val authorName: String,
    val status: String,
    @SerialName("is_important") val isImportant: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("updated_by_user_id") val updatedByUserId: String,
)

private fun TeamObservationEntity.toRemote() = TeamObservationRemoteDto(
    organizationId = organizationId,
    observationId = observationId,
    text = text,
    category = category,
    authorUserId = authorUserId,
    authorName = authorName,
    status = status,
    isImportant = isImportant,
    createdAt = SupabaseDateParser.format(createdAt),
    updatedAt = SupabaseDateParser.format(updatedAt),
    updatedByUserId = updatedByUserId,
)

private fun TeamObservationRemoteDto.toEntity() = TeamObservationEntity(
    organizationId = organizationId,
    observationId = observationId,
    text = text,
    category = category,
    authorUserId = authorUserId,
    authorName = authorName,
    status = status,
    isImportant = isImportant,
    createdAt = SupabaseDateParser.parse(createdAt),
    updatedAt = SupabaseDateParser.parse(updatedAt),
    updatedByUserId = updatedByUserId,
    isDirty = false,
)
