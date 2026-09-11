package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Local, tenant-scoped link between a Verto client and an Optimal company.
 *
 * No foreign key targets clients because the legacy clients table has no organization column.
 * Cleanup must therefore be performed through the organization-scoped DAO methods.
 */
@Entity(
    tableName = "optimal_company_links",
    primaryKeys = ["organization_id", "client_id"],
    indices = [
        Index(
            value = ["organization_id", "optimal_company_id"],
            unique = true,
            name = "index_optimal_company_links_org_company",
        ),
        Index(
            value = ["organization_id", "linked_at"],
            name = "index_optimal_company_links_org_linked_at",
        ),
    ],
)
data class OptimalCompanyLinkEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "optimal_company_id") val optimalCompanyId: String,
    @ColumnInfo(name = "linked_at") val linkedAt: Long,
)

/** Active registration-code cache. One row is retained per client and organization. */
@Entity(
    tableName = "optimal_registration_codes",
    primaryKeys = ["organization_id", "client_id"],
    indices = [
        Index(
            value = ["organization_id", "code"],
            unique = true,
            name = "index_optimal_registration_codes_org_code",
        ),
        Index(
            value = ["organization_id", "expires_at"],
            name = "index_optimal_registration_codes_org_expires_at",
        ),
    ],
)
data class OptimalRegistrationCodeEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    val code: String,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "used_at") val usedAt: Long? = null,
)

/** One Optimal company binding to one generic Messages-owned conversation per tenant. */
@Entity(
    tableName = "optimal_conversation_bindings",
    primaryKeys = ["organization_id", "client_id"],
    indices = [
        Index(
            value = ["organization_id", "conversation_id"],
            unique = true,
            name = "index_optimal_conversation_bindings_org_conversation",
        ),
        Index(
            value = ["organization_id", "bound_at"],
            name = "index_optimal_conversation_bindings_org_bound_at",
        ),
    ],
)
data class OptimalConversationBindingEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "bound_at") val boundAt: Long,
)

enum class OptimalOutboxStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    BLOCKED,
}

/** Durable, tenant-scoped record for every Optimal operation that may be synchronized. */
@Entity(
    tableName = "optimal_outbox",
    primaryKeys = ["organization_id", "event_id"],
    indices = [
        Index(
            value = ["organization_id", "idempotency_key"],
            unique = true,
            name = "index_optimal_outbox_org_idempotency",
        ),
        Index(
            value = ["organization_id", "aggregate_type", "aggregate_id", "sequence"],
            unique = true,
            name = "index_optimal_outbox_org_aggregate_sequence",
        ),
        Index(
            value = ["organization_id", "status", "created_at"],
            name = "index_optimal_outbox_org_status_created_at",
        ),
        Index(
            value = ["organization_id", "status", "next_attempt_at", "created_at"],
            name = "index_optimal_outbox_org_status_next_attempt",
        ),
    ],
)
data class OptimalOutboxEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    val operation: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    val sequence: Long,
    val status: OptimalOutboxStatus = OptimalOutboxStatus.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_owner") val leaseOwner: String? = null,
    @ColumnInfo(name = "lease_token") val leaseToken: String? = null,
    @ColumnInfo(name = "lease_expires_at") val leaseExpiresAt: Long? = null,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)
