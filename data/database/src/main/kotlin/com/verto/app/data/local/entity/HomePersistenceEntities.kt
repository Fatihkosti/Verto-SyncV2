package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "home_quick_action_order",
    primaryKeys = ["organization_id", "user_id", "action_id"],
    indices = [
        Index(
            name = "index_home_quick_action_order_scope_position",
            value = ["organization_id", "user_id", "position"],
            unique = true,
        ),
        Index(
            name = "index_home_quick_action_order_scope",
            value = ["organization_id", "user_id"],
        ),
    ],
)
data class HomeQuickActionOrderEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "action_id") val actionId: String,
    val position: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "home_event_states",
    primaryKeys = ["organization_id", "user_id", "event_key"],
    indices = [
        Index(
            name = "index_home_event_states_scope_snoozed_until",
            value = ["organization_id", "user_id", "snoozed_until"],
        ),
        Index(
            name = "index_home_event_states_scope_dismissed_at",
            value = ["organization_id", "user_id", "dismissed_at"],
        ),
    ],
)
data class HomeEventStateEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "seen_at") val seenAt: Long? = null,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long? = null,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Long? = null,
)
