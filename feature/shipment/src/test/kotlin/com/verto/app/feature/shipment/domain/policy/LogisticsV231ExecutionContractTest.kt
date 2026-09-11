package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.validation.LogisticsAttachmentPolicy
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV231ExecutionContractTest {
    @Test
    fun `open and damaged carton counts may overlap and each is bounded by received count`() {
        val handoff = handoff(received = 10, opened = 4, damaged = 4)
        LogisticsValidation.validateConfirmedCustodyHandoff(handoff)

        val invalid = handoff(received = 3, opened = 4, damaged = 0)
        assertTrue(runCatching { LogisticsValidation.validateConfirmedCustodyHandoff(invalid) }.isFailure)
    }

    @Test
    fun `count discrepancy requires reason`() {
        assertTrue(runCatching {
            LogisticsValidation.validateConfirmedCustodyHandoff(handoff(sent = 10, received = 9))
        }.isFailure)
        LogisticsValidation.validateConfirmedCustodyHandoff(handoff(sent = 10, received = 9, note = "كرتونة ناقصة"))
    }

    @Test
    fun `customs timestamps preserve arrival start completion order`() {
        LogisticsValidation.validateMilestones(
            listOf(
                LogisticsMilestone(
                    id = "customs", shipmentId = "shipment", type = LogisticsMilestoneType.CUSTOMS,
                    order = 1, location = "وادي حلفا", arrivedAt = 100L,
                    handlingStatus = LogisticsMilestoneHandlingStatus.UNLOADED,
                    unloadedAt = 110L, customsStartedAt = 120L, customsCompletedAt = 200L,
                ),
            ),
        )
        assertTrue(runCatching {
            LogisticsValidation.validateMilestones(
                listOf(LogisticsMilestone("bad", "shipment", LogisticsMilestoneType.CUSTOMS, 1, "حلفا", arrivedAt = 100L, customsStartedAt = 90L)),
            )
        }.isFailure)
    }

    @Test
    fun `occurred time and recorded time remain independent facts`() {
        val event = LogisticsEvent(
            id = "event", organizationId = "org", shipmentId = "shipment",
            type = LogisticsEventType.MILESTONE_ARRIVED, occurredAt = 100L, recordedAt = 500L, requestId = "request",
        )
        assertTrue(event.recordedAt > event.occurredAt)
    }

    @Test
    fun `friday is excluded from customs calendar`() {
        val zone = ZoneId.of("Africa/Khartoum")
        assertFalse(FridayOffLogisticsCalendarPolicy.isWorkingDay(Instant.parse("2026-08-21T10:00:00Z").toEpochMilli(), zone))
        assertTrue(FridayOffLogisticsCalendarPolicy.isWorkingDay(Instant.parse("2026-08-20T10:00:00Z").toEpochMilli(), zone))
    }

    @Test
    fun `attachment policy accepts v231 formats and caps mp4 at 100 MiB`() {
        val types = listOf(
            "image/jpeg", "image/png", "video/mp4", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/markdown",
        )
        assertTrue(types.all(LogisticsAttachmentPolicy::isSupported))
        assertTrue(LogisticsAttachmentPolicy.maxBytes("video/mp4") == 100L * 1024L * 1024L)
        assertTrue(LogisticsAttachmentPolicy.signatureMatches("application/pdf", "%PDF-1.7".encodeToByteArray()))
        assertFalse(LogisticsAttachmentPolicy.signatureMatches("application/pdf", "not-pdf".encodeToByteArray()))
    }

    private fun handoff(
        sent: Int = 10,
        received: Int = sent,
        opened: Int = 0,
        damaged: Int = 0,
        note: String = "",
    ) = LogisticsCustodyHandoff(
        id = "handoff", organizationId = "org", shipmentId = "shipment",
        fromHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, fromHolderId = "carrier", fromHolderNameSnapshot = "ناقل",
        toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, toHolderId = "broker", toHolderNameSnapshot = "مخلص",
        transferredAt = 100L, receivedAt = 100L, requestId = "request",
        handoverPackageCount = sent, receivedPackageCount = received, openedPackageCount = opened, damagedPackageCount = damaged,
        packageChangeNote = note.takeIf { sent != received },
    )
}
