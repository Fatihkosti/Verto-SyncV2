package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageDeliveryStatus
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMediaDraft
import com.verto.app.feature.messages.domain.port.OwnedMessage
import com.verto.app.feature.messages.domain.port.OwnedMessageDeliveryStatus
import com.verto.app.feature.messages.domain.port.OwnedMessageKind
import com.verto.app.feature.messages.domain.port.OwnedMessageMedia
import com.verto.app.feature.messages.domain.port.OwnedSenderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptimalMessagingBridgeMappingTest {
    @Test fun `all message kinds map into provider contract`() {
        OptimalMessageKind.entries.forEach { kind ->
            assertEquals(OwnedMessageKind.valueOf(kind.name), kind.toOwnedKind())
        }
    }

    @Test fun `media draft preserves identifiers nullable locations and duration`() {
        val mapped = OptimalMessageMediaDraft("m", null, "https://x", "audio/mp4", 44L, 1200L).toOwnedDraft()
        assertEquals("m", mapped.mediaId)
        assertNull(mapped.localUri)
        assertEquals("https://x", mapped.remoteUrl)
        assertEquals("audio/mp4", mapped.mimeType)
        assertEquals(44L, mapped.sizeBytes)
        assertEquals(1200L, mapped.durationMs)
    }

    @Test fun `owned message maps back without losing status sender media or identifiers`() {
        val owned = OwnedMessage(
            organizationId = "org", messageId = "msg", conversationId = "conv", clientId = "client", remoteId = "remote",
            sender = OwnedSenderSnapshot("u", "User", "OWNER"), kind = OwnedMessageKind.VOICE, body = "body",
            deliveryStatus = OwnedMessageDeliveryStatus.RECEIVED, isRead = true, createdAt = 10L, updatedAt = 11L,
            media = listOf(OwnedMessageMedia("media", "msg", "file://x", null, "audio/mp4", 100L, 900L, 12L)),
        )
        val mapped = owned.toOptimalMessage()
        assertEquals("org", mapped.organizationId)
        assertEquals("msg", mapped.messageId)
        assertEquals("conv", mapped.conversationId)
        assertEquals("client", mapped.clientId)
        assertEquals("remote", mapped.remoteId)
        assertEquals("u", mapped.sender.senderId)
        assertEquals(OptimalMessageKind.VOICE, mapped.kind)
        assertEquals(OptimalMessageDeliveryStatus.RECEIVED, mapped.deliveryStatus)
        assertEquals(1, mapped.media.size)
        assertEquals("media", mapped.media.single().mediaId)
        assertEquals(900L, mapped.media.single().durationMs)
    }
}
