package com.verto.app.data.sync

import com.verto.app.data.sync.SyncRealtimeHint

/**
 * Bounded in-memory accelerator metadata for Realtime hints.
 *
 * Correctness never depends on this state: accepted batches first request the durable generation
 * owned by session 311, and process death may safely discard this metadata.
 */
internal class RealtimeHintCoalescer(
    private val maxTargets: Int = DEFAULT_MAX_TARGETS,
) {
    init { require(maxTargets > 0) }

    private val lock = Any()
    private var pending = false
    private var flushScheduled = false
    private var maxServerRevision: Long? = null
    private var overflowed = false
    private val targets = linkedSetOf<RealtimeTarget>()

    /** Returns true only when the caller must schedule a new trailing-edge flush. */
    fun offer(hint: SyncRealtimeHint): Boolean = synchronized(lock) {
        pending = true
        hint.serverRevision?.let { revision ->
            if (revision > 0L) maxServerRevision = maxOf(maxServerRevision ?: revision, revision)
        }
        val type = hint.aggregateType
        val id = hint.aggregateId
        if (type != null && id != null && !overflowed) {
            if (targets.size < maxTargets || RealtimeTarget(type, id) in targets) {
                targets += RealtimeTarget(type, id)
            } else {
                overflowed = true
                targets.clear()
            }
        }
        if (!flushScheduled) {
            flushScheduled = true
            true
        } else {
            false
        }
    }

    /** Snapshot one bounded batch; cursor/apply semantics are deliberately absent. */
    fun drain(): RealtimeHintBatch? = synchronized(lock) {
        if (!pending) return@synchronized null
        val batch = RealtimeHintBatch(
            maxServerRevision = maxServerRevision,
            targets = targets.toSet(),
            overflowed = overflowed,
        )
        pending = false
        maxServerRevision = null
        overflowed = false
        targets.clear()
        batch
    }

    /** Returns true if another batch arrived while the previous durable request was committing. */
    fun completeFlushAndShouldContinue(): Boolean = synchronized(lock) {
        if (pending) {
            true
        } else {
            flushScheduled = false
            false
        }
    }

    fun reset() = synchronized(lock) {
        pending = false
        flushScheduled = false
        maxServerRevision = null
        overflowed = false
        targets.clear()
    }

    companion object { const val DEFAULT_MAX_TARGETS = 32 }
}

internal data class RealtimeHintBatch(
    val maxServerRevision: Long?,
    val targets: Set<RealtimeTarget>,
    val overflowed: Boolean,
)

internal data class RealtimeTarget(
    val aggregateType: String,
    val aggregateId: String,
)
