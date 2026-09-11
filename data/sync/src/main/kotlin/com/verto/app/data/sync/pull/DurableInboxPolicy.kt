package com.verto.app.data.sync.pull

/** Pure scheduling/resource rules shared by production and boundary tests. No wall-clock ordering. */
object DurableInboxPolicy {
    const val SOFT_CHANGE_BUDGET = 1_000
    const val MAX_GROUP_BYTES = 2_097_152L
    const val UNAPPLIED_QUOTA_BYTES = 64L * 1024L * 1024L
    const val MAX_PAGE_BYTES = UNAPPLIED_QUOTA_BYTES + MAX_GROUP_BYTES
    const val DISK_RESERVE_BYTES = 1024L * 1024L
    const val CONTINUATION_DELAY_MILLIS = 1_000L

    fun mayProcessGroup(processedGroups: Int, processedChanges: Int, nextCount: Int, budget: Int): Boolean {
        require(processedGroups >= 0 && processedChanges >= 0 && nextCount > 0 && budget > 0)
        return processedGroups == 0 || processedChanges.toLong() + nextCount <= budget
    }

    fun groupSizeIsLegal(bytes: Long): Boolean = bytes in 1L..MAX_GROUP_BYTES

    fun mayReceive(used: Long, additional: Long, availableDisk: Long): Boolean {
        require(used >= 0 && additional >= 0)
        if (additional == 0L) return true // verified replay does not consume a second reservation
        if (used >= UNAPPLIED_QUOTA_BYTES || additional > MAX_PAGE_BYTES - used) return false
        // SQLite may write the database, WAL, indexes and a rollback copy. This is admission control,
        // not a disk guarantee; SQLiteFullException still rolls back the complete receive transaction.
        return availableDisk >= diskBytesRequired(additional)
    }

    fun diskBytesRequired(bytes: Long): Long {
        require(bytes in 0L..MAX_PAGE_BYTES)
        return bytes * 4L + DISK_RESERVE_BYTES
    }

    /** Bound the worst case (one maximum-size group per change) before requesting a page. */
    fun receiveSoftLimit(used: Long, requested: Int): Int {
        require(used >= 0 && requested in 1..SOFT_CHANGE_BUDGET)
        if (used >= UNAPPLIED_QUOTA_BYTES) return 0
        return minOf(requested, ((MAX_PAGE_BYTES - used) / MAX_GROUP_BYTES).toInt().coerceAtLeast(1))
    }

    data class CoveredGroup(val firstRevision: Long, val lastRevision: Long, val applied: Boolean)

    /** Revision gaps are legal scope gaps. An unapplied covered group, not a missing integer, stops us. */
    fun appliedCheckpoint(baseline: Long?, received: Long?, groups: List<CoveredGroup>): Long? {
        var checkpoint = baseline
        var previous = baseline ?: 0L
        for (group in groups) {
            require(group.firstRevision > previous && group.lastRevision >= group.firstRevision) {
                "INBOX_COVERAGE_INVALID"
            }
            require(received != null && group.lastRevision <= received) { "INBOX_COVERAGE_INVALID" }
            if (!group.applied) break
            checkpoint = group.lastRevision
            previous = group.lastRevision
        }
        return checkpoint
    }
}
