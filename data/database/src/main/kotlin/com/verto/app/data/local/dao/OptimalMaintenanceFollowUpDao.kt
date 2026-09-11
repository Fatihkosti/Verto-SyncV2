package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpEntity
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpStatus
import kotlinx.coroutines.flow.Flow

/** Local read model prepared for the v107 maintenance pending-action provider. */
data class OperationalMaintenanceFollowUpRow(
    val organizationId: String,
    val recordId: String,
    val invoiceId: String,
    val status: OptimalMaintenanceFollowUpStatus,
    val startedAt: Long,
    val expectedAt: Long?,
    val updatedAt: Long,
    val overdue: Boolean,
    val vehicleName: String,
    val vehicleType: String,
    val plateNumber: String,
)

@Dao
abstract class OptimalMaintenanceFollowUpDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertOrIgnore(entity: OptimalMaintenanceFollowUpEntity): Long

    @Query(
        """
        SELECT *
        FROM optimal_maintenance_follow_ups
        WHERE organization_id = :organizationId
          AND record_id = :recordId
        LIMIT 1
        """,
    )
    abstract suspend fun get(
        organizationId: String,
        recordId: String,
    ): OptimalMaintenanceFollowUpEntity?

    @Query(
        """
        UPDATE optimal_maintenance_follow_ups
        SET status = :status,
            updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND record_id = :recordId
          AND updated_at <= :updatedAt
        """,
    )
    protected abstract suspend fun updateStatusRow(
        organizationId: String,
        recordId: String,
        status: OptimalMaintenanceFollowUpStatus,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE optimal_maintenance_follow_ups
        SET expected_at = :expectedAt,
            updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND record_id = :recordId
          AND updated_at <= :updatedAt
        """,
    )
    protected abstract suspend fun updateExpectedAtRow(
        organizationId: String,
        recordId: String,
        expectedAt: Long?,
        updatedAt: Long,
    ): Int

    /** Idempotent creation; an existing lifecycle is never silently reset. */
    @Transaction
    open suspend fun start(entity: OptimalMaintenanceFollowUpEntity): OptimalMaintenanceFollowUpEntity {
        validateIdentity(entity.organizationId, entity.recordId)
        require(entity.status == OptimalMaintenanceFollowUpStatus.IN_PROGRESS) {
            "new maintenance follow-up must start in progress"
        }
        validateTimes(entity.startedAt, entity.expectedAt, entity.updatedAt)
        insertOrIgnore(entity)
        return requireNotNull(get(entity.organizationId, entity.recordId)) {
            "maintenance record does not exist or follow-up could not be created"
        }
    }

    @Transaction
    open suspend fun updateStatus(
        organizationId: String,
        recordId: String,
        status: OptimalMaintenanceFollowUpStatus,
        updatedAt: Long,
    ): OptimalMaintenanceFollowUpEntity? {
        validateIdentity(organizationId, recordId)
        val existing = get(organizationId, recordId) ?: return null
        require(updatedAt >= existing.updatedAt) { "updatedAt cannot move backwards" }
        require(existing.status.canTransitionTo(status)) {
            "terminal maintenance follow-up cannot be reopened"
        }
        if (existing.status == status) return existing
        check(updateStatusRow(organizationId, recordId, status, updatedAt) == 1) {
            "maintenance follow-up changed concurrently"
        }
        return get(organizationId, recordId)
    }

    @Transaction
    open suspend fun updateExpectedAt(
        organizationId: String,
        recordId: String,
        expectedAt: Long?,
        updatedAt: Long,
    ): OptimalMaintenanceFollowUpEntity? {
        validateIdentity(organizationId, recordId)
        val existing = get(organizationId, recordId) ?: return null
        require(existing.status == OptimalMaintenanceFollowUpStatus.IN_PROGRESS) {
            "terminal maintenance follow-up cannot change its expected date"
        }
        validateTimes(existing.startedAt, expectedAt, updatedAt)
        require(updatedAt >= existing.updatedAt) { "updatedAt cannot move backwards" }
        if (existing.expectedAt == expectedAt) return existing
        check(updateExpectedAtRow(organizationId, recordId, expectedAt, updatedAt) == 1) {
            "maintenance follow-up changed concurrently"
        }
        return get(organizationId, recordId)
    }

    /**
     * Excludes completed/cancelled follow-ups and invoices that were voided or hard-deleted.
     * This is the trusted local source for "in maintenance" and "overdue" discovery.
     */
    @Query(
        """
        SELECT
            f.organization_id AS organizationId,
            f.record_id AS recordId,
            r.invoice_id AS invoiceId,
            f.status AS status,
            f.started_at AS startedAt,
            f.expected_at AS expectedAt,
            f.updated_at AS updatedAt,
            r.vehicle_name_snapshot AS vehicleName,
            r.vehicle_type_snapshot AS vehicleType,
            r.plate_number_snapshot AS plateNumber,
            CASE
                WHEN f.expected_at IS NOT NULL AND f.expected_at < :nowMillis THEN 1
                ELSE 0
            END AS overdue
        FROM optimal_maintenance_follow_ups f
        INNER JOIN optimal_maintenance_records r
            ON r.organization_id = f.organization_id
           AND r.record_id = f.record_id
        INNER JOIN invoices i
            ON i.id = r.invoice_id
           AND i.organization_id = f.organization_id
        WHERE f.organization_id = :organizationId
          AND f.status = 'IN_PROGRESS'
          AND i.voided = 0
        ORDER BY overdue DESC, f.started_at ASC, f.record_id ASC
        """,
    )
    abstract fun observeOperational(
        organizationId: String,
        nowMillis: Long,
    ): Flow<List<OperationalMaintenanceFollowUpRow>>

    private fun validateIdentity(organizationId: String, recordId: String) {
        require(organizationId.isNotBlank() && organizationId == organizationId.trim()) {
            "organizationId is required and must be normalized"
        }
        require(recordId.isNotBlank() && recordId == recordId.trim()) {
            "recordId is required and must be normalized"
        }
    }

    private fun validateTimes(startedAt: Long, expectedAt: Long?, updatedAt: Long) {
        require(startedAt >= 0L) { "startedAt cannot be negative" }
        require(updatedAt >= startedAt) { "updatedAt cannot precede startedAt" }
        require(expectedAt == null || expectedAt >= startedAt) {
            "expectedAt cannot precede startedAt"
        }
    }
}

private fun OptimalMaintenanceFollowUpStatus.canTransitionTo(
    target: OptimalMaintenanceFollowUpStatus,
): Boolean = this == target || this == OptimalMaintenanceFollowUpStatus.IN_PROGRESS
