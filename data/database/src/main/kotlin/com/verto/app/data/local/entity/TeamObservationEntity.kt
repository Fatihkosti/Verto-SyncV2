package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "team_observations",
    primaryKeys = ["organization_id", "observation_id"],
    indices = [
        Index(
            name = "index_team_observations_org_created",
            value = ["organization_id", "created_at"],
        ),
        Index(
            name = "index_team_observations_org_status_important",
            value = ["organization_id", "status", "is_important", "created_at"],
        ),
        Index(
            name = "index_team_observations_author",
            value = ["organization_id", "author_user_id", "created_at"],
        ),
    ],
)
data class TeamObservationEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "observation_id") val observationId: String,
    val text: String,
    val category: String,
    @ColumnInfo(name = "author_user_id") val authorUserId: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    val status: String,
    @ColumnInfo(name = "is_important") val isImportant: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "updated_by_user_id") val updatedByUserId: String,
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean,
)
