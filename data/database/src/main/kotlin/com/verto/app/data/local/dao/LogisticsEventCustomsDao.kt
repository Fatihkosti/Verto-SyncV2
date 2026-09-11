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

interface LogisticsEventCustomsDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(entity: LogisticsEventEntity)

    @Query(
        "SELECT * FROM logistics_events " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY occurred_at ASC, recorded_at ASC, id ASC",
    )
    suspend fun getEvents(organizationId: String, shipmentId: String): List<LogisticsEventEntity>

    @Query(
        "SELECT * FROM logistics_events " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "AND request_id = :requestId AND type = :type LIMIT 1",
    )
    suspend fun findEventByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        type: String,
    ): LogisticsEventEntity?
    @Query(
        "SELECT * FROM logistics_customs_plans WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId LIMIT 1",
    )
    suspend fun getCustomsPlan(organizationId: String, shipmentId: String): LogisticsCustomsPlanEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomsPlan(entity: LogisticsCustomsPlanEntity)

    @Update
    suspend fun updateCustomsPlan(entity: LogisticsCustomsPlanEntity): Int

    @Query(
        "SELECT * FROM logistics_customs_plan_documents WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId ORDER BY created_at ASC, id ASC",
    )
    suspend fun getCustomsPlanDocuments(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsCustomsPlanDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomsPlanDocuments(entities: List<LogisticsCustomsPlanDocumentEntity>)

    @Query(
        "DELETE FROM logistics_customs_plan_documents WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId AND id = :documentId",
    )
    suspend fun deleteCustomsPlanDocument(
        organizationId: String,
        shipmentId: String,
        documentId: String,
    ): Int

    @Query(
        "SELECT * FROM logistics_plan_revisions WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId ORDER BY revision_number ASC",
    )
    suspend fun getPlanRevisions(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsPlanRevisionEntity>

    @Query(
        "SELECT * FROM logistics_plan_revision_changes WHERE organization_id = :organizationId " +
            "AND revision_id = :revisionId ORDER BY id ASC",
    )
    suspend fun getPlanRevisionChanges(
        organizationId: String,
        revisionId: String,
    ): List<LogisticsPlanRevisionChangeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlanRevision(entity: LogisticsPlanRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlanRevisionChanges(entities: List<LogisticsPlanRevisionChangeEntity>)
}
