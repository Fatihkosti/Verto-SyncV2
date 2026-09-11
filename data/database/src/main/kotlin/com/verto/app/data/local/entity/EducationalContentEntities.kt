package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "educational_topics",
    primaryKeys = ["organization_id", "topic_id"],
    indices = [
        Index(name = "index_educational_topics_organization_id", value = ["organization_id"]),
        Index(name = "index_educational_topics_org_active_deleted", value = ["organization_id", "is_active", "deleted_at"]),
        Index(name = "index_educational_topics_dirty", value = ["is_dirty"]),
    ],
)
data class EducationalTopicEntity(
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
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "educational_topic_targets",
    primaryKeys = ["organization_id", "topic_id", "target_type", "target_value"],
    foreignKeys = [
        ForeignKey(
            entity = EducationalTopicEntity::class,
            parentColumns = ["organization_id", "topic_id"],
            childColumns = ["organization_id", "topic_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_educational_topic_targets_organization_id", value = ["organization_id"]),
        Index(name = "index_educational_topic_targets_topic_id", value = ["topic_id"]),
        Index(name = "index_educational_topic_targets_type", value = ["target_type"]),
        Index(name = "index_educational_topic_targets_value", value = ["target_value"]),
        Index(
            name = "index_educational_topic_targets_lookup",
            value = ["organization_id", "target_type", "target_value", "topic_id"],
        ),
    ],
)
data class EducationalTopicTargetEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_value") val targetValue: String,
)
