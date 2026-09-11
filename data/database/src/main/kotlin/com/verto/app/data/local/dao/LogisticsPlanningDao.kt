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

interface LogisticsPlanningDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRouteTemplate(entity: LogisticsRouteTemplateEntity)

    @Update
    suspend fun updateRouteTemplate(entity: LogisticsRouteTemplateEntity): Int

    @Query("SELECT * FROM logistics_route_templates WHERE organization_id = :organizationId ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getRouteTemplates(organizationId: String): List<LogisticsRouteTemplateEntity>

    @Query("SELECT * FROM logistics_route_templates WHERE organization_id = :organizationId AND id = :templateId LIMIT 1")
    suspend fun getRouteTemplate(organizationId: String, templateId: String): LogisticsRouteTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRouteTemplateStops(entities: List<LogisticsRouteTemplateStopEntity>)

    @Query("DELETE FROM logistics_route_template_stops WHERE organization_id = :organizationId AND template_id = :templateId")
    suspend fun deleteRouteTemplateStops(organizationId: String, templateId: String): Int

    @Query("SELECT * FROM logistics_route_template_stops WHERE organization_id = :organizationId AND template_id = :templateId ORDER BY stop_order ASC, id ASC")
    suspend fun getRouteTemplateStops(organizationId: String, templateId: String): List<LogisticsRouteTemplateStopEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShortage(entity: LogisticsShortageEntity)

    @Update
    suspend fun updateShortage(entity: LogisticsShortageEntity): Int

    @Query(
        "SELECT * FROM logistics_shortages " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY shipment_line_id ASC, id ASC",
    )
    suspend fun getShortages(organizationId: String, shipmentId: String): List<LogisticsShortageEntity>

    @Query(
        "SELECT * FROM logistics_shortages " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND shipment_line_id = :shipmentLineId LIMIT 1",
    )
    suspend fun findShortageForLine(
        organizationId: String,
        shipmentId: String,
        shipmentLineId: String,
    ): LogisticsShortageEntity?

    @Query(
        "SELECT * FROM logistics_shortages " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND request_id = :requestId LIMIT 1",
    )
    suspend fun findShortageByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsShortageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShortageSettlement(entity: LogisticsShortageSettlementEntity)
    @Query("SELECT * FROM logistics_shortage_settlements WHERE organization_id = :organizationId AND shipment_id = :shipmentId ORDER BY occurred_at ASC, id ASC")
    suspend fun getShortageSettlements(organizationId: String, shipmentId: String): List<LogisticsShortageSettlementEntity>
    @Query("SELECT * FROM logistics_shortage_settlements WHERE organization_id = :organizationId AND shipment_id = :shipmentId AND request_id = :requestId LIMIT 1")
    suspend fun findShortageSettlementByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsShortageSettlementEntity?
}
