package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UnifiedSyncGenesisWatermarkTest {
    private val scope = SyncScope(
        organizationId = "org-empty",
        syncPrincipalId = "user-1",
        scopeId = "scope-empty",
        scopeDefinitionVersion = 1,
    )

    @Test
    fun `empty organization accepts genesis page high watermark zero`() {
        val page = SyncPullPage(
            changes = emptyList(),
            nextCursor = "opaque-genesis-cursor",
            hasMore = false,
            minAvailableRevision = 0L,
            pageHighWatermark = 0L,
            endsAtTransactionBoundary = true,
            scopeIdentity = scope,
            coverage = SyncPullCoverage.GLOBAL_SCOPE,
            advancesGlobalCursor = true,
        )

        UnifiedSyncContractRules.requireValidPullPage(page, scope)
    }

    @Test
    fun `negative page high watermark remains invalid`() {
        val page = SyncPullPage(
            changes = emptyList(),
            nextCursor = "opaque-genesis-cursor",
            hasMore = false,
            minAvailableRevision = 0L,
            pageHighWatermark = -1L,
            endsAtTransactionBoundary = true,
            scopeIdentity = scope,
            coverage = SyncPullCoverage.GLOBAL_SCOPE,
            advancesGlobalCursor = true,
        )

        try {
            UnifiedSyncContractRules.requireValidPullPage(page, scope)
            fail("negative high watermark must be rejected")
        } catch (violation: SyncContractViolation) {
            assertEquals("VALIDATION", violation.code)
        }
    }
}
