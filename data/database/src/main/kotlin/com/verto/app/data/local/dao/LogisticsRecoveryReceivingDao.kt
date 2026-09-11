package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.verto.app.data.local.entity.LogisticsAssignmentEntity
import com.verto.app.data.local.entity.LogisticsCustodyHandoffEntity
import com.verto.app.data.local.entity.LogisticsCostEntity
import com.verto.app.data.local.entity.LogisticsCustomsPlanEntity
import com.verto.app.data.local.entity.LogisticsCustomsPlanDocumentEntity
import com.verto.app.data.local.entity.LogisticsPlanRevisionEntity
import com.verto.app.data.local.entity.LogisticsPlanRevisionChangeEntity
import com.verto.app.data.local.entity.LogisticsCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsDocumentEntity
import com.verto.app.data.local.entity.LogisticsEventEntity
import com.verto.app.data.local.entity.LogisticsMilestoneEntity
import com.verto.app.data.local.entity.LogisticsReceivingLineEntity
import com.verto.app.data.local.entity.LogisticsRecoveryEntity
import com.verto.app.data.local.entity.LogisticsRecoveryLineEntity
import com.verto.app.data.local.entity.LogisticsRecoveryPostingEntity
import com.verto.app.data.local.entity.LogisticsShortageEntity
import com.verto.app.data.local.entity.LogisticsShortageSettlementEntity
import com.verto.app.data.local.entity.LogisticsLateCostAdjustmentEntity
import com.verto.app.data.local.entity.LogisticsLateCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsReceivingBatchEntity
import com.verto.app.data.local.entity.LogisticsInventoryPostingEntity
import com.verto.app.data.local.entity.LogisticsPartnerEntity
import com.verto.app.data.local.entity.LogisticsShipmentEntity
import com.verto.app.data.local.entity.LogisticsRouteTemplateStopEntity
import com.verto.app.data.local.entity.LogisticsRouteTemplateEntity
import com.verto.app.data.local.entity.LogisticsPaymentEntity
import com.verto.app.data.local.entity.LogisticsShipmentLegEntity
import com.verto.app.data.local.entity.LogisticsShipmentPartnerLinkEntity
import com.verto.app.data.local.entity.LogisticsShipmentLineEntity
import com.verto.app.data.local.entity.LogisticsShipmentSourceEntity
import com.verto.app.data.local.entity.LogisticsTransportDetailsEntity
import kotlinx.coroutines.flow.Flow

interface LogisticsRecoveryReceivingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecovery(entity: LogisticsRecoveryEntity)

    @Query(
        "SELECT * FROM logistics_recoveries " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY recovered_at ASC, id ASC",
    )
    suspend fun getRecoveries(organizationId: String, shipmentId: String): List<LogisticsRecoveryEntity>

    @Query(
        "SELECT * FROM logistics_recoveries " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND request_id = :requestId LIMIT 1",
    )
    suspend fun findRecoveryByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsRecoveryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecoveryLines(entities: List<LogisticsRecoveryLineEntity>)

    @Query(
        "SELECT * FROM logistics_recovery_lines " +
            "WHERE organization_id = :organizationId AND recovery_id = :recoveryId " +
            "ORDER BY id ASC",
    )
    suspend fun getRecoveryLines(organizationId: String, recoveryId: String): List<LogisticsRecoveryLineEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecoveryPosting(entity: LogisticsRecoveryPostingEntity)

    @Query(
        "SELECT * FROM logistics_recovery_postings " +
            "WHERE organization_id = :organizationId AND recovery_line_id = :recoveryLineId LIMIT 1",
    )
    suspend fun findRecoveryPostingForLine(
        organizationId: String,
        recoveryLineId: String,
    ): LogisticsRecoveryPostingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceivingBatch(entity: LogisticsReceivingBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceivingLines(entities: List<LogisticsReceivingLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryPostings(entities: List<LogisticsInventoryPostingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCostAllocations(entities: List<LogisticsCostAllocationEntity>)

    @Query(
        "SELECT COUNT(*) FROM logistics_inventory_postings " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId",
    )
    suspend fun countInventoryPostings(organizationId: String, shipmentId: String): Int

    @Query(
        "SELECT * FROM logistics_cost_allocations " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY shipment_line_id ASC, id ASC",
    )
    suspend fun getCostAllocations(organizationId: String, shipmentId: String): List<LogisticsCostAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLateCostAdjustment(entity: LogisticsLateCostAdjustmentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLateCostAllocations(entities: List<LogisticsLateCostAllocationEntity>)
    @Query("SELECT * FROM logistics_late_cost_adjustments WHERE organization_id = :organizationId AND shipment_id = :shipmentId ORDER BY recorded_at ASC, id ASC")
    suspend fun getLateCostAdjustments(organizationId: String, shipmentId: String): List<LogisticsLateCostAdjustmentEntity>
    @Query("SELECT * FROM logistics_late_cost_allocations WHERE organization_id = :organizationId AND shipment_id = :shipmentId ORDER BY adjustment_id ASC, shipment_line_id ASC, id ASC")
    suspend fun getLateCostAllocations(organizationId: String, shipmentId: String): List<LogisticsLateCostAllocationEntity>
    @Query("SELECT * FROM logistics_late_cost_adjustments WHERE organization_id = :organizationId AND shipment_id = :shipmentId AND request_id = :requestId LIMIT 1")
    suspend fun findLateCostAdjustmentByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsLateCostAdjustmentEntity?

    @Query(
        "SELECT * FROM logistics_receiving_batches " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY received_at ASC, id ASC",
    )
    suspend fun getReceivingBatches(organizationId: String, shipmentId: String): List<LogisticsReceivingBatchEntity>

    @Query(
        "SELECT * FROM logistics_receiving_lines " +
            "WHERE organization_id = :organizationId AND batch_id = :batchId " +
            "ORDER BY id ASC",
    )
    suspend fun getReceivingLines(organizationId: String, batchId: String): List<LogisticsReceivingLineEntity>

}
