package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.EducationalTopicEntity
import com.verto.app.data.local.entity.EducationalTopicTargetEntity
import kotlinx.coroutines.flow.Flow

data class EducationalTopicTargetRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "full_content") val fullContent: String,
    val category: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "target_type") val targetType: String?,
    @ColumnInfo(name = "target_value") val targetValue: String?,
)

@Dao
interface EducationalContentDao {
    @Query(
        """
        SELECT topic.*, target.target_type, target.target_value
        FROM educational_topics AS topic
        INNER JOIN educational_topic_targets AS target
          ON target.organization_id = topic.organization_id
         AND target.topic_id = topic.topic_id
        WHERE topic.organization_id = :organizationId
          AND topic.deleted_at IS NULL
          AND topic.is_active = 1
          AND EXISTS (
              SELECT 1 FROM educational_topic_targets AS audience
              WHERE audience.organization_id = topic.organization_id
                AND audience.topic_id = topic.topic_id
                AND (
                    (audience.target_type = 'ALL' AND audience.target_value = '*')
                    OR (audience.target_type = 'ROLE' AND audience.target_value = :role)
                    OR (audience.target_type = 'USER' AND audience.target_value = :userId)
                )
          )
        ORDER BY topic.topic_id ASC, target.target_type ASC, target.target_value ASC
        """,
    )
    fun observeVisibleRows(
        organizationId: String,
        userId: String,
        role: String,
    ): Flow<List<EducationalTopicTargetRow>>

    @Query(
        """
        SELECT topic.*, target.target_type, target.target_value
        FROM educational_topics AS topic
        INNER JOIN educational_topic_targets AS target
          ON target.organization_id = topic.organization_id
         AND target.topic_id = topic.topic_id
        WHERE topic.organization_id = :organizationId
          AND topic.deleted_at IS NULL
        ORDER BY topic.updated_at DESC, topic.topic_id ASC,
                 target.target_type ASC, target.target_value ASC
        """,
    )
    fun observeOrganizationTopicRows(organizationId: String): Flow<List<EducationalTopicTargetRow>>

    @Query(
        """
        SELECT topic.*, target.target_type, target.target_value
        FROM educational_topics AS topic
        INNER JOIN educational_topic_targets AS target
          ON target.organization_id = topic.organization_id
         AND target.topic_id = topic.topic_id
        WHERE topic.organization_id = :organizationId
          AND topic.topic_id = :topicId
          AND topic.deleted_at IS NULL
        ORDER BY target.target_type ASC, target.target_value ASC
        """,
    )
    suspend fun getByIdRows(organizationId: String, topicId: String): List<EducationalTopicTargetRow>

    @Query(
        """
        SELECT topic.*, target.target_type, target.target_value
        FROM educational_topics AS topic
        LEFT JOIN educational_topic_targets AS target
          ON target.organization_id = topic.organization_id
         AND target.topic_id = topic.topic_id
        WHERE topic.organization_id = :organizationId
          AND topic.is_dirty = 1
        ORDER BY topic.updated_at ASC, topic.topic_id ASC,
                 target.target_type ASC, target.target_value ASC
        """,
    )
    suspend fun getDirtyRows(organizationId: String): List<EducationalTopicTargetRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopic(topic: EducationalTopicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTargets(targets: List<EducationalTopicTargetEntity>)

    @Query(
        """
        DELETE FROM educational_topic_targets
        WHERE organization_id = :organizationId AND topic_id = :topicId
        """,
    )
    suspend fun deleteTargets(organizationId: String, topicId: String): Int

    @Query(
        """
        DELETE FROM educational_topics
        WHERE organization_id = :organizationId AND topic_id = :topicId
        """,
    )
    suspend fun hardDelete(organizationId: String, topicId: String): Int

    @Query(
        """
        UPDATE educational_topics
        SET is_dirty = 0
        WHERE organization_id = :organizationId AND topic_id IN (:topicIds)
        """,
    )
    suspend fun markClean(organizationId: String, topicIds: List<String>): Int

    @Query(
        """
        DELETE FROM educational_topics
        WHERE organization_id = :organizationId
          AND is_dirty = 0
        """,
    )
    suspend fun deleteCleanSnapshot(organizationId: String): Int

    @Transaction
    suspend fun replaceLocal(
        topic: EducationalTopicEntity,
        targets: List<EducationalTopicTargetEntity>,
    ) {
        upsertTopic(topic)
        deleteTargets(topic.organizationId, topic.topicId)
        if (targets.isNotEmpty()) upsertTargets(targets)
    }

    /** Remote pull replaces only clean rows; pending local edits and tombstones remain authoritative. */
    @Transaction
    suspend fun replaceCleanRemoteSnapshot(
        organizationId: String,
        topics: List<EducationalTopicEntity>,
        targets: List<EducationalTopicTargetEntity>,
    ) {
        deleteCleanSnapshot(organizationId)
        topics.forEach { upsertTopic(it.copy(isDirty = false)) }
        if (targets.isNotEmpty()) upsertTargets(targets)
    }
}
