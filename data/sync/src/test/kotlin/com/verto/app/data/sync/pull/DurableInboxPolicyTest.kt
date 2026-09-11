package com.verto.app.data.sync.pull

import org.junit.Assert.*
import org.junit.Test

class DurableInboxPolicyTest {
    @Test fun firstGroupMayBe9991000Or1001WithoutSplitting() {
        for (count in listOf(999, 1000, 1001)) assertTrue(DurableInboxPolicy.mayProcessGroup(0, 0, count, 1000))
    }
    @Test fun laterGroupMustFitAndIntegerOverflowCannotGrantAdmission() {
        assertTrue(DurableInboxPolicy.mayProcessGroup(1, 999, 1, 1000))
        assertFalse(DurableInboxPolicy.mayProcessGroup(1, 999, 2, 1000))
        assertFalse(DurableInboxPolicy.mayProcessGroup(1, 1001, 1, 1000))
        assertFalse(DurableInboxPolicy.mayProcessGroup(1, Int.MAX_VALUE, Int.MAX_VALUE, 1000))
    }
    @Test fun firstGroupExceptionDoesNotResetAtNextPage() {
        assertFalse(DurableInboxPolicy.mayProcessGroup(1, 0, 1001, 500))
        assertTrue(DurableInboxPolicy.mayProcessGroup(1, 0, 500, 500))
    }
    @Test fun canonicalGroupLimitIsInclusive() {
        assertFalse(DurableInboxPolicy.groupSizeIsLegal(0))
        assertTrue(DurableInboxPolicy.groupSizeIsLegal(1))
        assertTrue(DurableInboxPolicy.groupSizeIsLegal(2_097_152))
        assertFalse(DurableInboxPolicy.groupSizeIsLegal(2_097_153))
    }
    @Test fun scopeCanCrossQuotaOnlyWhileBeforeQuotaAndByOneLegalGroup() {
        val q = DurableInboxPolicy.UNAPPLIED_QUOTA_BYTES
        val g = DurableInboxPolicy.MAX_GROUP_BYTES
        assertTrue(DurableInboxPolicy.mayReceive(q - 1, g, Long.MAX_VALUE))
        assertTrue(DurableInboxPolicy.mayReceive(q - 1, g + 1, Long.MAX_VALUE))
        assertFalse(DurableInboxPolicy.mayReceive(q - 1, g + 2, Long.MAX_VALUE))
        assertFalse(DurableInboxPolicy.mayReceive(q, 1, Long.MAX_VALUE))
        assertFalse(DurableInboxPolicy.mayReceive(q + g, 1, Long.MAX_VALUE))
    }
    @Test fun identicalReplayConsumesNoQuotaEvenWhenDiskIsFull() {
        assertTrue(DurableInboxPolicy.mayReceive(DurableInboxPolicy.MAX_PAGE_BYTES, 0, 0))
    }
    @Test fun admissionIncludesWalIndexAndRollbackReserve() {
        val need = DurableInboxPolicy.diskBytesRequired(2_097_152)
        assertFalse(DurableInboxPolicy.mayReceive(0, 2_097_152, need - 1))
        assertTrue(DurableInboxPolicy.mayReceive(0, 2_097_152, need))
    }
    @Test fun fetchLimitStopsAtQuotaAndRemainsConservative() {
        assertEquals(33, DurableInboxPolicy.receiveSoftLimit(0, 1000))
        assertEquals(1, DurableInboxPolicy.receiveSoftLimit(DurableInboxPolicy.UNAPPLIED_QUOTA_BYTES - 1, 1000))
        assertEquals(0, DurableInboxPolicy.receiveSoftLimit(DurableInboxPolicy.UNAPPLIED_QUOTA_BYTES, 1000))
        assertEquals(2, DurableInboxPolicy.receiveSoftLimit(0, 2))
    }
    @Test fun independentAppliedYDoesNotJumpOverWaitingX() {
        val groups = listOf(group(10, 12, false), group(20, 22, true))
        assertEquals(7L, DurableInboxPolicy.appliedCheckpoint(7, 22, groups))
    }
    @Test fun settlingXReleasesTheCoveredAppliedPrefixIncludingScopedRevisionGaps() {
        assertEquals(22L, DurableInboxPolicy.appliedCheckpoint(7, 22, listOf(group(10, 12, true), group(20, 22, true))))
    }
    @Test fun lastCompleteGroupNotLargestObservedRevisionDefinesCheckpoint() {
        assertEquals(12L, DurableInboxPolicy.appliedCheckpoint(null, 999, listOf(group(10, 12, true), group(20, 22, false))))
        assertNull(DurableInboxPolicy.appliedCheckpoint(null, 22, listOf(group(10, 12, false))))
    }
    @Test fun invalidCoverageAndOverlappingGroupsFailClosed() {
        rejects { DurableInboxPolicy.appliedCheckpoint(null, 9, listOf(group(10, 12, true))) }
        rejects { DurableInboxPolicy.appliedCheckpoint(null, 12, listOf(group(10, 12, true), group(11, 12, true))) }
        rejects { DurableInboxPolicy.appliedCheckpoint(10, 12, listOf(group(10, 12, true))) }
    }
    private fun group(first: Long, last: Long, applied: Boolean) = DurableInboxPolicy.CoveredGroup(first, last, applied)
    private fun rejects(block: () -> Unit) { try { block(); fail("must reject") } catch (_: IllegalArgumentException) { } }
}
