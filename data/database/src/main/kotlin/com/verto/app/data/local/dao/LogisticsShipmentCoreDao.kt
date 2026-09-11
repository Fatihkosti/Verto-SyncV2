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


/** Lightweight Home-only projections. They deliberately exclude logistics aggregates and write state. */
data class ActivityShipmentRow(
    val shipmentId: String,
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val createdAt: Long,
)

data class HomeOperationalShipmentRow(
    val organizationId: String,
    val shipmentId: String,
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val state: String,
    val createdAt: Long,
    val startedAt: Long?,
    val expectedArrivalAt: Long?,
    val customsMilestoneId: String?,
    val customsCalendarPolicyId: String,
    val eventTimezoneId: String,
)

data class HomeOperationalMilestoneRow(
    val shipmentId: String,
    val milestoneId: String,
    val type: String,
    val milestoneOrder: Int,
    val location: String,
    val arrivedAt: Long?,
    val departedAt: Long?,
    val placeName: String,
    val expectedStayDays: Int?,
    val customsBrokerPartnerId: String?,
    val customsBrokerNameSnapshot: String?,
    val customsBrokerPhoneSnapshot: String?,
    val customsStartedAt: Long?,
    val customsCompletedAt: Long?,
)

data class HomeOperationalLegRow(
    val organizationId: String,
    val shipmentId: String,
    val legId: String,
    val sequence: Int,
    val fromMilestoneId: String,
    val toMilestoneId: String,
    val mode: String,
    val carrierPartnerId: String?,
    val status: String,
    val actualDepartureAt: Long?,
    val actualArrivalAt: Long?,
    val expectedTransitDays: Int?,
    val expectedTransitMinutes: Int?,
    val representativeNameSnapshot: String?,
    val representativePhoneSnapshot: String?,
    val supersededAt: Long?,
)

data class HomeOperationalCustomsPlanRow(
    val organizationId: String,
    val shipmentId: String,
    val planId: String,
    val checkpointName: String,
    val afterStationId: String,
    val expectedDurationMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class HomeReceiptIssueRow(
    val receiptId: String,
    val shipmentId: String,
    val shipmentNumber: String,
    val remainingMissingQuantity: Int,
    val detectedAt: Long,
)

interface LogisticsShipmentCoreDao {
    @Query(
        """
        SELECT organization_id AS organizationId,
               id AS shipmentId,
               shipment_number AS shipmentNumber,
               source_location AS sourceLocation,
               destination_location AS destinationLocation,
               state AS state,
               created_at AS createdAt,
               started_at AS startedAt,
               expected_arrival_at AS expectedArrivalAt,
               customs_milestone_id AS customsMilestoneId,
               customs_calendar_policy_id AS customsCalendarPolicyId,
               event_timezone_id AS eventTimezoneId
        FROM logistics_shipments
        WHERE organization_id = :organizationId
          AND state NOT IN ('CLOSED', 'CANCELLED')
        ORDER BY created_at ASC, id ASC
        """,
    )
    fun observeHomeOperationalShipments(
        organizationId: String,
    ): Flow<List<HomeOperationalShipmentRow>>

    @Query(
        """
        SELECT m.shipment_id AS shipmentId,
               m.id AS milestoneId,
               m.type AS type,
               m.milestone_order AS milestoneOrder,
               m.location AS location,
               m.arrived_at AS arrivedAt,
               m.departed_at AS departedAt,
               m.place_name AS placeName,
               m.expected_stay_days AS expectedStayDays,
               m.customs_broker_partner_id AS customsBrokerPartnerId,
               m.customs_broker_name_snapshot AS customsBrokerNameSnapshot,
               m.customs_broker_phone_snapshot AS customsBrokerPhoneSnapshot,
               m.customs_started_at AS customsStartedAt,
               m.customs_completed_at AS customsCompletedAt
        FROM logistics_milestones m
        INNER JOIN logistics_shipments s
                ON s.organization_id = m.organization_id
               AND s.id = m.shipment_id
        WHERE m.organization_id = :organizationId
          AND s.state NOT IN ('CLOSED', 'CANCELLED')
        ORDER BY m.shipment_id ASC, m.milestone_order ASC, m.id ASC
        """,
    )
    fun observeHomeOperationalMilestones(
        organizationId: String,
    ): Flow<List<HomeOperationalMilestoneRow>>

    @Query(
        """
        SELECT l.organization_id AS organizationId,
               l.shipment_id AS shipmentId,
               l.id AS legId,
               l.sequence AS sequence,
               l.from_milestone_id AS fromMilestoneId,
               l.to_milestone_id AS toMilestoneId,
               l.mode AS mode,
               l.carrier_partner_id AS carrierPartnerId,
               l.status AS status,
               l.actual_departure_at AS actualDepartureAt,
               l.actual_arrival_at AS actualArrivalAt,
               l.expected_transit_days AS expectedTransitDays,
               l.expected_transit_minutes AS expectedTransitMinutes,
               l.representative_name_snapshot AS representativeNameSnapshot,
               l.representative_phone_snapshot AS representativePhoneSnapshot,
               l.superseded_at AS supersededAt
        FROM logistics_shipment_legs l
        INNER JOIN logistics_shipments s
                ON s.organization_id = l.organization_id
               AND s.id = l.shipment_id
        WHERE l.organization_id = :organizationId
          AND s.state NOT IN ('CLOSED', 'CANCELLED')
          AND l.status = 'IN_TRANSIT'
          AND l.superseded_at IS NULL
        ORDER BY l.shipment_id ASC, l.sequence ASC, l.id ASC
        """,
    )
    fun observeHomeOperationalLegs(
        organizationId: String,
    ): Flow<List<HomeOperationalLegRow>>

    @Query(
        """
        SELECT p.organization_id AS organizationId,
               p.shipment_id AS shipmentId,
               p.id AS planId,
               p.checkpoint_name AS checkpointName,
               p.after_station_id AS afterStationId,
               p.expected_duration_minutes AS expectedDurationMinutes,
               p.created_at AS createdAt,
               p.updated_at AS updatedAt
        FROM logistics_customs_plans p
        INNER JOIN logistics_shipments s
                ON s.organization_id = p.organization_id
               AND s.id = p.shipment_id
        WHERE p.organization_id = :organizationId
          AND s.state NOT IN ('CLOSED', 'CANCELLED')
        ORDER BY p.shipment_id ASC, p.id ASC
        """,
    )
    fun observeHomeOperationalCustomsPlans(
        organizationId: String,
    ): Flow<List<HomeOperationalCustomsPlanRow>>

    @Query(
        """
        SELECT sh.id AS receiptId,
               sh.shipment_id AS shipmentId,
               s.shipment_number AS shipmentNumber,
               sh.remaining_missing_quantity AS remainingMissingQuantity,
               sh.detected_at AS detectedAt
        FROM logistics_shortages sh
        INNER JOIN logistics_shipments s
                ON s.organization_id = sh.organization_id
               AND s.id = sh.shipment_id
        WHERE sh.organization_id = :organizationId
          AND sh.remaining_missing_quantity > 0
        ORDER BY sh.detected_at ASC, sh.id ASC
        """,
    )
    fun observeHomeReceiptIssues(
        organizationId: String,
    ): Flow<List<HomeReceiptIssueRow>>
    @Query(
        """
        SELECT id AS shipmentId,
               shipment_number AS shipmentNumber,
               source_location AS sourceLocation,
               destination_location AS destinationLocation,
               created_at AS createdAt
        FROM logistics_shipments
        WHERE organization_id = :organizationId
          AND created_at >= :sinceEpochMillis
        ORDER BY created_at DESC, id ASC
        LIMIT :limit
        """,
    )
    fun observeActivityShipments(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<ActivityShipmentRow>>

    @Query(
        "SELECT * FROM logistics_shipments " +
            "WHERE organization_id = :organizationId " +
            "ORDER BY created_at DESC, id ASC",
    )
    fun observeShipments(organizationId: String): Flow<List<LogisticsShipmentEntity>>

    @Query("SELECT * FROM logistics_shortages WHERE organization_id = :organizationId ORDER BY detected_at DESC, id ASC")
    fun observeShortages(organizationId: String): Flow<List<LogisticsShortageEntity>>

    @Query(
        "SELECT * FROM logistics_shipments " +
            "WHERE organization_id = :organizationId AND id = :shipmentId LIMIT 1",
    )
    suspend fun getShipment(organizationId: String, shipmentId: String): LogisticsShipmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShipment(entity: LogisticsShipmentEntity)

    @Query(
        "SELECT COALESCE(SUM(line.expected_quantity), 0) FROM logistics_shipment_lines AS line " +
            "INNER JOIN logistics_shipments AS shipment " +
            "ON shipment.organization_id = line.organization_id AND shipment.id = line.shipment_id " +
            "WHERE line.organization_id = :organizationId " +
            "AND line.source_invoice_id = :invoiceId " +
            "AND line.source_invoice_item_id = :invoiceItemId " +
            "AND (shipment.state != 'CANCELLED' OR shipment.started_at IS NOT NULL) " +
            "AND (:excludeShipmentId IS NULL OR line.shipment_id != :excludeShipmentId)",
    )
    suspend fun getAllocatedQuantityForInvoiceLine(
        organizationId: String,
        invoiceId: String,
        invoiceItemId: String,
        excludeShipmentId: String? = null,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM logistics_shipment_sources AS source " +
            "INNER JOIN logistics_shipments AS shipment " +
            "ON shipment.organization_id = source.organization_id AND shipment.id = source.shipment_id " +
            "WHERE source.organization_id = :organizationId AND source.invoice_id = :invoiceId " +
            "AND (:excludeShipmentId IS NULL OR source.shipment_id != :excludeShipmentId) " +
            "AND (shipment.state NOT IN ('CANCELLED', 'CLOSED') OR (shipment.state = 'CANCELLED' AND shipment.started_at IS NOT NULL))",
    )
    suspend fun countConflictingActiveShipmentsForInvoice(
        organizationId: String,
        invoiceId: String,
        excludeShipmentId: String? = null,
    ): Int

    @Update
    suspend fun updateShipment(entity: LogisticsShipmentEntity): Int

    @Query("DELETE FROM logistics_shipments WHERE organization_id = :organizationId AND id = :shipmentId")
    suspend fun deleteShipment(organizationId: String, shipmentId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(entities: List<LogisticsShipmentSourceEntity>)

    @Update
    suspend fun updateSources(entities: List<LogisticsShipmentSourceEntity>): Int

    @Query(
        "DELETE FROM logistics_shipment_sources WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId AND id = :sourceId",
    )
    suspend fun deleteSourceForPlanning(organizationId: String, shipmentId: String, sourceId: String): Int

    @Query(
        "SELECT * FROM logistics_shipment_sources " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY id ASC",
    )
    suspend fun getSources(organizationId: String, shipmentId: String): List<LogisticsShipmentSourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(entities: List<LogisticsShipmentLineEntity>)

    @Update
    suspend fun updateLines(entities: List<LogisticsShipmentLineEntity>): Int

    @Query(
        "DELETE FROM logistics_shipment_lines WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId AND id = :lineId",
    )
    suspend fun deleteLineForPlanning(organizationId: String, shipmentId: String, lineId: String): Int

    @Query(
        "SELECT * FROM logistics_shipment_lines " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY id ASC",
    )
    suspend fun getLines(organizationId: String, shipmentId: String): List<LogisticsShipmentLineEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMilestone(entity: LogisticsMilestoneEntity)

    @Update
    suspend fun updateMilestone(entity: LogisticsMilestoneEntity): Int

    @Query(
        "SELECT * FROM logistics_milestones " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY milestone_order ASC, id ASC",
    )
    suspend fun getMilestones(organizationId: String, shipmentId: String): List<LogisticsMilestoneEntity>

    @Query(
        "UPDATE logistics_milestones SET milestone_order = milestone_order + 1000000 " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId",
    )
    suspend fun offsetMilestoneOrdersForRouteSave(organizationId: String, shipmentId: String): Int

    @Query(
        "DELETE FROM logistics_milestones WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId AND id = :milestoneId",
    )
    suspend fun deleteMilestoneForRouteSave(organizationId: String, shipmentId: String, milestoneId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLegs(entities: List<LogisticsShipmentLegEntity>)

    @Update
    suspend fun updateLeg(entity: LogisticsShipmentLegEntity): Int

    @Query(
        "UPDATE logistics_shipment_legs SET sequence = sequence + 1000000 " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId",
    )
    suspend fun offsetLegSequencesForRouteSave(organizationId: String, shipmentId: String): Int

    @Query(
        "DELETE FROM logistics_shipment_legs WHERE organization_id = :organizationId " +
            "AND shipment_id = :shipmentId AND id = :legId",
    )
    suspend fun deleteLegForRouteSave(organizationId: String, shipmentId: String, legId: String): Int

    @Query(
        "SELECT * FROM logistics_shipment_legs " +
            "WHERE organization_id = :organizationId AND shipment_id = :shipmentId " +
            "ORDER BY sequence ASC, id ASC",
    )
    suspend fun getLegs(organizationId: String, shipmentId: String): List<LogisticsShipmentLegEntity>
}
