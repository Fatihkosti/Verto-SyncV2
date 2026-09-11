package com.verto.app.feature.shipment.bridge

import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import com.verto.app.feature.shipment.domain.port.LogisticsPermanentDeletePort
import javax.inject.Inject

/** Session 307: SHIPMENT uses CANCEL_STATE_TRANSITION; client-side hard delete is deliberately disabled. */
class RoomLogisticsPermanentDeleteAdapter @Inject constructor() : LogisticsPermanentDeletePort {
    override suspend fun deleteDraft(organizationId: String, shipmentId: String): Nothing =
        error("FAIL_DELETE_POLICY: SHIPMENT hard delete disabled; use cancellation")

    override suspend fun deleteExecuted(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        actor: LogisticsCashActor,
    ): Nothing = error("FAIL_DELETE_POLICY: executed SHIPMENT hard delete disabled")
}
