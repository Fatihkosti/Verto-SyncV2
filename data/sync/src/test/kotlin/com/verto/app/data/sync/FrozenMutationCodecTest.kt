package com.verto.app.data.sync

import com.verto.app.data.local.entity.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.verto.app.data.sync.push.UnifiedSyncPushEngine
import org.junit.Test

class FrozenMutationCodecTest {
    @Test
    fun `lease duration and renewal cadence match repair contract`() {
        assertEquals(120_000L, UnifiedSyncPushEngine.LEASE_MILLIS)
        assertEquals(30_000L, UnifiedSyncPushEngine.LEASE_RENEWAL_MILLIS)
    }

    @Test
    fun `golden member wire is exact utf8 without transport lease fields`() {
        val row = SyncOutboxEntity(
            mutationId = "m-1",
            organizationId = "org-1",
            aggregateType = "EXPENSE",
            aggregateId = "expense-1",
            operationType = "UPSERT",
            baseVersion = null,
            localSequence = 11,
            aggregateSequence = 3,
            payloadVersion = 1,
            payloadJson = "{\"amountMinor\":123,\"note\":\"x\"}",
            semanticFingerprint = "fingerprint",
            commandBatchId = null,
            commandOrder = null,
            dependsOnMutationId = null,
            createdAt = 99,
        )

        val wire = FrozenMutationCodec.encodeWire(FrozenMutationCodec.encodeIntent(row), 7)

        assertEquals(
            "{\"contractFamily\":\"verto-unified-sync\",\"contractVersion\":2,\"mutationId\":\"m-1\"," +
                "\"organizationId\":\"org-1\",\"aggregateType\":\"EXPENSE\",\"aggregateId\":\"expense-1\"," +
                "\"operationType\":\"UPSERT\",\"baseVersion\":7,\"localSequence\":11,\"aggregateSequence\":3," +
                "\"payloadVersion\":1,\"payload\":{\"amountMinor\":123,\"note\":\"x\"},\"createdAtEpochMillis\":99," +
                "\"commandBatchId\":null,\"commandOrder\":null,\"dependsOnMutationId\":null}",
            wire,
        )
        assertEquals("3c92c39cedace92bc86f0c76ef368958e2b552bcbc39adbdd13ad33677835b16", sha256Utf8(wire))
        assertFalse(wire.toByteArray(Charsets.UTF_8).take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        assertFalse(wire.contains("leaseToken"))
    }
}
