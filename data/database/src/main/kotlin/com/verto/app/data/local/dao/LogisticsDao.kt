package com.verto.app.data.local.dao

import androidx.room.Dao

/**
 * Stable Room entry point for logistics persistence.
 *
 * The cohesive contracts mirror workflow ownership while preserving the existing Room accessor
 * and all outer transaction boundaries.
 */
@Dao
interface LogisticsDao :
    LogisticsShipmentCoreDao,
    LogisticsJourneyDao,
    LogisticsPartnerDocumentDao,
    LogisticsCostPaymentDao,
    LogisticsPlanningDao,
    LogisticsRecoveryReceivingDao,
    LogisticsEventCustomsDao

