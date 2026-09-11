package com.verto.app.feature.dashboard.data.education

import androidx.room.withTransaction
import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.dao.EducationalContentDao
import com.verto.app.data.local.dao.EducationalTopicTargetRow
import com.verto.app.data.local.entity.EducationalTopicEntity
import com.verto.app.data.local.entity.EducationalTopicTargetEntity
import com.verto.feature.dashboard.api.EducationalAudienceContext
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.EducationalContentCategory
import com.verto.feature.dashboard.api.EducationalContentDraft
import com.verto.feature.dashboard.api.EducationalContentRepository
import com.verto.feature.dashboard.api.EducationalContentTarget
import com.verto.feature.dashboard.api.EducationalTargetType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomEducationalContentRepository @Inject constructor(
    private val dao: EducationalContentDao,
    private val sessionReader: SessionReader,
    private val audit: WriteAuditPort,
    private val database: AppDatabase,
    private val outbox: UnifiedOutboxWriter,
) : EducationalContentRepository {

    override fun observeVisible(context: EducationalAudienceContext): Flow<List<EducationalContent>> =
        combine(
            sessionReader.organizationId,
            sessionReader.userId,
            sessionReader.role,
        ) { organizationId, userId, role ->
            Triple(organizationId.trim(), userId.trim(), role.trim().lowercase())
        }.distinctUntilChanged().flatMapLatest { (organizationId, userId, role) ->
            val requestedOrganization = context.organizationId.trim()
            val requestedUser = context.userId.trim()
            if (
                organizationId.isBlank() || userId.isBlank() ||
                organizationId != requestedOrganization || userId != requestedUser
            ) {
                emptyFlow()
            } else {
                // The trusted session role is used; callers cannot broaden their own audience.
                dao.observeVisibleRows(
                    organizationId = organizationId,
                    userId = userId,
                    role = role,
                ).map(::aggregateRows)
            }
        }

    override fun observeOrganizationTopics(organizationId: String): Flow<List<EducationalContent>> =
        combine(sessionReader.organizationId, sessionReader.role) { currentOrganization, role ->
            currentOrganization.trim() to role.trim().lowercase()
        }.distinctUntilChanged().flatMapLatest { (currentOrganization, role) ->
            val requestedOrganization = organizationId.trim()
            if (
                requestedOrganization.isBlank() || currentOrganization != requestedOrganization ||
                role != ADMIN_ROLE
            ) {
                emptyFlow()
            } else {
                dao.observeOrganizationTopicRows(currentOrganization).map(::aggregateRows)
            }
        }

    override suspend fun getById(organizationId: String, topicId: String): EducationalContent? {
        val actor = requireAdmin()
        require(actor.organizationId == organizationId.trim()) { "cross-organization access denied" }
        return aggregateRows(dao.getByIdRows(actor.organizationId, topicId.trim())).singleOrNull()
    }

    override suspend fun create(draft: EducationalContentDraft): EducationalContent {
        val actor = requireAdmin()
        val now = System.currentTimeMillis()
        val topic = EducationalTopicEntity(
            organizationId = actor.organizationId,
            topicId = UUID.randomUUID().toString(),
            title = draft.title.trim(),
            summary = draft.summary.trim(),
            fullContent = draft.fullContent.trim(),
            category = draft.category.name,
            isActive = draft.isActive,
            createdByUserId = actor.userId,
            createdAt = now,
            updatedAt = now,
            isDirty = true,
            deletedAt = null,
        )
        val targets = draft.targets.toEntities(actor.organizationId, topic.topicId)
        database.withTransaction {
            dao.replaceLocal(topic, targets)
            outbox.enqueue(
                actor.organizationId, "EDUCATIONAL_CONTENT", topic.topicId, "UPSERT",
                educationalPayload(topic, draft.targets),
            )
        }
        audit.log(
            action = AuditAction.INSERT,
            table = AuditTable.EDUCATIONAL_TOPIC,
            recordId = topic.topicId,
            summary = "إضافة موضوع تعليمي: ${topic.title}",
            employeeId = actor.userId,
            employeeName = actor.userName,
            canUndo = false,
        )
        return topic.toDomain(draft.targets)
    }

    override suspend fun update(topicId: String, draft: EducationalContentDraft): EducationalContent {
        val actor = requireAdmin()
        val id = topicId.trim().also { require(it.isNotBlank()) { "topic id is required" } }
        val existing = aggregateRows(dao.getByIdRows(actor.organizationId, id)).singleOrNull()
            ?: error("الموضوع غير موجود")
        val updated = EducationalTopicEntity(
            organizationId = actor.organizationId,
            topicId = id,
            title = draft.title.trim(),
            summary = draft.summary.trim(),
            fullContent = draft.fullContent.trim(),
            category = draft.category.name,
            isActive = draft.isActive,
            createdByUserId = existing.createdByUserId,
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis(),
            isDirty = true,
            deletedAt = null,
        )
        val targets = draft.targets.toEntities(actor.organizationId, id)
        database.withTransaction {
            dao.replaceLocal(updated, targets)
            outbox.enqueue(
                actor.organizationId, "EDUCATIONAL_CONTENT", id, "UPSERT",
                educationalPayload(updated, draft.targets),
            )
        }
        audit.log(
            action = AuditAction.UPDATE,
            table = AuditTable.EDUCATIONAL_TOPIC,
            recordId = id,
            summary = "تعديل موضوع تعليمي: ${updated.title}",
            oldValue = existing.title,
            newValue = updated.title,
            employeeId = actor.userId,
            employeeName = actor.userName,
            canUndo = false,
        )
        return updated.toDomain(draft.targets)
    }

    override suspend fun delete(topicId: String) {
        val actor = requireAdmin()
        val id = topicId.trim().also { require(it.isNotBlank()) { "topic id is required" } }
        val existing = aggregateRows(dao.getByIdRows(actor.organizationId, id)).singleOrNull()
            ?: return
        val deletedAt = System.currentTimeMillis()
        val tombstone = EducationalTopicEntity(
            organizationId = actor.organizationId,
            topicId = id,
            title = existing.title,
            summary = existing.summary,
            fullContent = existing.fullContent,
            category = existing.category.name,
            isActive = false,
            createdByUserId = existing.createdByUserId,
            createdAt = existing.createdAt,
            updatedAt = deletedAt,
            isDirty = true,
            deletedAt = deletedAt,
        )
        database.withTransaction {
            dao.replaceLocal(topic = tombstone, targets = emptyList())
            outbox.enqueue(
                actor.organizationId, "EDUCATIONAL_CONTENT", id, "DELETE",
                educationalPayload(tombstone, emptySet()) + mapOf("deleted" to true),
                createdAt = deletedAt,
            )
        }
        audit.log(
            action = AuditAction.DELETE,
            table = AuditTable.EDUCATIONAL_TOPIC,
            recordId = id,
            summary = "حذف موضوع تعليمي: ${existing.title}",
            oldValue = existing.title,
            employeeId = actor.userId,
            employeeName = actor.userName,
            canUndo = false,
        )
    }

    override suspend fun setActive(topicId: String, active: Boolean): EducationalContent {
        val actor = requireAdmin()
        val current = aggregateRows(dao.getByIdRows(actor.organizationId, topicId.trim())).singleOrNull()
            ?: error("الموضوع غير موجود")
        return update(
            topicId = current.id,
            draft = EducationalContentDraft(
                title = current.title,
                summary = current.summary,
                fullContent = current.fullContent,
                category = current.category,
                isActive = active,
                targets = current.targets,
            ),
        )
    }

    private fun educationalPayload(
        topic: EducationalTopicEntity,
        targets: Set<EducationalContentTarget>,
    ) = mapOf(
        "category" to topic.category,
        "createdAt" to topic.createdAt,
        "createdByUserId" to topic.createdByUserId,
        "deletedAt" to topic.deletedAt,
        "fullContent" to topic.fullContent,
        "isActive" to topic.isActive,
        "summary" to topic.summary,
        "targets" to targets.sortedWith(compareBy({ it.type.name }, { it.value }))
            .joinToString("|") { "${it.type.name}\u001F${it.value.trim()}" },
        "title" to topic.title,
        "updatedAt" to topic.updatedAt,
    )

    private suspend fun requireAdmin(): Actor {
        val snapshot = sessionReader.snapshot()
        if (snapshot.role.trim().lowercase() != ADMIN_ROLE) {
            throw SecurityException("إدارة المواضيع التعليمية متاحة للمدير فقط")
        }
        val organizationId = snapshot.organization.id.trim()
        val userId = snapshot.user.id.trim()
        require(organizationId.isNotBlank()) { "organization session is unavailable" }
        require(userId.isNotBlank()) { "user session is unavailable" }
        return Actor(organizationId, userId, snapshot.user.name.trim())
    }


    private data class Actor(
        val organizationId: String,
        val userId: String,
        val userName: String,
    )

    private companion object {
        const val ADMIN_ROLE = "admin"
    }
}

internal fun aggregateRows(rows: List<EducationalTopicTargetRow>): List<EducationalContent> =
    rows.groupBy { it.organizationId to it.topicId }
        .values
        .mapNotNull { group ->
            val first = group.firstOrNull() ?: return@mapNotNull null
            val targets = group.mapNotNull { row ->
                val type = row.targetType?.let { runCatching { EducationalTargetType.valueOf(it) }.getOrNull() }
                val value = row.targetValue
                if (type == null || value.isNullOrBlank()) null else EducationalContentTarget(type, value)
            }.toSet()
            if (targets.isEmpty() || first.deletedAt != null) return@mapNotNull null
            EducationalContent(
                id = first.topicId,
                organizationId = first.organizationId,
                title = first.title,
                summary = first.summary,
                fullContent = first.fullContent,
                category = runCatching { EducationalContentCategory.valueOf(first.category) }
                    .getOrDefault(EducationalContentCategory.OPERATIONS),
                isActive = first.isActive,
                targets = targets,
                createdByUserId = first.createdByUserId,
                createdAt = first.createdAt,
                updatedAt = first.updatedAt,
                isDirty = first.isDirty,
            )
        }

private fun Set<EducationalContentTarget>.toEntities(
    organizationId: String,
    topicId: String,
): List<EducationalTopicTargetEntity> = distinctBy { it.type to it.value.trim() }.map { target ->
    EducationalTopicTargetEntity(
        organizationId = organizationId,
        topicId = topicId,
        targetType = target.type.name,
        targetValue = target.value.trim(),
    )
}

private fun EducationalTopicEntity.toDomain(targets: Set<EducationalContentTarget>) = EducationalContent(
    id = topicId,
    organizationId = organizationId,
    title = title,
    summary = summary,
    fullContent = fullContent,
    category = runCatching { EducationalContentCategory.valueOf(category) }
        .getOrDefault(EducationalContentCategory.OPERATIONS),
    isActive = isActive,
    targets = targets,
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDirty = isDirty,
)
