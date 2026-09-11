package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialSyncContractF251Test {
    @Test
    fun `out of order older remote aggregate never overwrites local`() {
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.KEEP_LOCAL,
            FinancialAggregateConflictPolicy.resolve(
                localLifecycle = "POSTED",
                localVersion = 7,
                localDirty = false,
                remoteLifecycle = "POSTED",
                remoteVersion = 6,
            )
        )
    }

    @Test
    fun `equal version posted echo is idempotent and keeps local`() {
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.KEEP_LOCAL,
            FinancialAggregateConflictPolicy.resolve(
                localLifecycle = "POSTED",
                localVersion = 7,
                localDirty = false,
                remoteLifecycle = "POSTED",
                remoteVersion = 7,
            )
        )
    }

    @Test
    fun `posted to void next version applies only when local clean`() {
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.APPLY_REMOTE,
            FinancialAggregateConflictPolicy.resolve("POSTED", 7, false, "VOID", 8)
        )
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.REQUIRES_REVIEW,
            FinancialAggregateConflictPolicy.resolve("POSTED", 7, true, "VOID", 8)
        )
    }

    @Test
    fun `void cannot be resurrected by remote posted`() {
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.REQUIRES_REVIEW,
            FinancialAggregateConflictPolicy.resolve("VOID", 8, false, "POSTED", 9)
        )
    }

    @Test
    fun `version jump requires review rather than blind merge`() {
        assertEquals(
            FinancialAggregateConflictPolicy.Decision.REQUIRES_REVIEW,
            FinancialAggregateConflictPolicy.resolve("POSTED", 7, false, "VOID", 10)
        )
    }

    @Test
    fun `financial retry backoff is monotonic and capped`() {
        val delays = (1..20).map(::retryDelayMillis)
        delays.zipWithNext().forEach { (left, right) ->
            check(right >= left) { "retry backoff decreased: $left -> $right" }
        }
        assertEquals(30_000L, delays.first())
        assertEquals(6L * 60L * 60L * 1000L, delays.last())
    }
}
