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

interface LogisticsJourneyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustodyHandoff(entity: LogisticsCustodyHandoffEntity)

    @Query(
        "SELECT * FROM logistics_custody_handoffs " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY received_at ASC, id ASC",
    )
    suspend fun getCustodyHandoffs(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsCustodyHandoffEntity>

    @Query(
        "SELECT * FROM logistics_custody_handoffs " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND received_package_count IS NOT NULL " +
            "ORDER BY received_at DESC, id DESC LIMIT 1",
    )
    suspend fun getLatestConfirmedCargoHandoff(
        organizationId: String,
        shipmentId: String,
    ): LogisticsCustodyHandoffEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssignment(entity: LogisticsAssignmentEntity)

    @Update
    suspend fun updateAssignment(entity: LogisticsAssignmentEntity): Int

    @Query(
        "UPDATE logistics_assignments SET ended_at = :endedAt " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND ended_at IS NULL",
    )
    suspend fun endActiveAssignments(organizationId: String, shipmentId: String, endedAt: Long): Int

    @Query(
        "SELECT * FROM logistics_assignments " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY assigned_at ASC, id ASC",
    )
    suspend fun getAssignments(organizationId: String, shipmentId: String): List<LogisticsAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransportDetails(entity: LogisticsTransportDetailsEntity)

    @Update
    suspend fun updateTransportDetails(entity: LogisticsTransportDetailsEntity): Int

    @Query(
        "DELETE FROM logistics_transport_details WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId",
    )
    suspend fun deleteTransportDetails(organizationId: String, shipmentId: String): Int

    @Query(
        "SELECT * FROM logistics_transport_details " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId LIMIT 1",
    )
    suspend fun getTransportDetails(organizationId: String, shipmentId: String): LogisticsTransportDetailsEntity?
}
