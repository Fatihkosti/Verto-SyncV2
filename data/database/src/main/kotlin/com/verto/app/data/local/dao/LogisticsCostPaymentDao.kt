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

interface LogisticsCostPaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCost(entity: LogisticsCostEntity)

    @Update
    suspend fun updateCost(entity: LogisticsCostEntity): Int

    @Query(
        "SELECT * FROM logistics_costs " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY id ASC",
    )
    suspend fun getCosts(organizationId: String, shipmentId: String): List<LogisticsCostEntity>

    @Query("SELECT * FROM logistics_costs WHERE organization_id = :organizationId ORDER BY shipment_id ASC, id ASC")
    fun observeCosts(organizationId: String): Flow<List<LogisticsCostEntity>>

    @Query(
        "SELECT * FROM logistics_costs " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND request_id = :requestId LIMIT 1",
    )
    suspend fun findCostByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsCostEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(entity: LogisticsPaymentEntity)

    @Update
    suspend fun updatePayment(entity: LogisticsPaymentEntity): Int

    @Query(
        "SELECT * FROM logistics_payments WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId ORDER BY created_at ASC, id ASC",
    )
    suspend fun getPayments(organizationId: String, shipmentId: String): List<LogisticsPaymentEntity>

    @Query(
        "SELECT * FROM logistics_payments WHERE organization_id = :organizationId " +
            "AND cost_id = :costId ORDER BY created_at ASC, id ASC",
    )
    suspend fun getPaymentsForCost(organizationId: String, costId: String): List<LogisticsPaymentEntity>

    @Query("SELECT * FROM logistics_payments WHERE organization_id = :organizationId AND request_id = :requestId LIMIT 1")
    suspend fun findPaymentByRequest(organizationId: String, requestId: String): LogisticsPaymentEntity?
}
