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

interface LogisticsPartnerDocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPartner(entity: LogisticsPartnerEntity)

    @Update
    suspend fun updatePartner(entity: LogisticsPartnerEntity): Int

    @Query(
        "SELECT * FROM logistics_partners " +
            "WHERE organization_id = :organizationId AND id = :partnerId LIMIT 1",
    )
    suspend fun getPartner(organizationId: String, partnerId: String): LogisticsPartnerEntity?

    @Query(
        "SELECT * FROM logistics_partners WHERE organization_id = :organizationId " +
            "ORDER BY name COLLATE NOCASE ASC, id ASC",
    )
    suspend fun getPartners(organizationId: String): List<LogisticsPartnerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPartnerLink(entity: LogisticsShipmentPartnerLinkEntity)

    @Query(
        "SELECT * FROM logistics_shipment_partner_links " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY id ASC",
    )
    suspend fun getPartnerLinks(organizationId: String, shipmentId: String): List<LogisticsShipmentPartnerLinkEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(entity: LogisticsDocumentEntity)

    @Query(
        "SELECT * FROM logistics_documents " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY created_at ASC, id ASC",
    )
    suspend fun getDocuments(organizationId: String, shipmentId: String): List<LogisticsDocumentEntity>

    @Query(
        "DELETE FROM logistics_documents " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId AND id = :documentId",
    )
    suspend fun deleteDocument(organizationId: String, shipmentId: String, documentId: String): Int
}
