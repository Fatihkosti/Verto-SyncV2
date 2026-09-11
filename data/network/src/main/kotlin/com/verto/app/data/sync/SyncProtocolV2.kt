package com.verto.app.data.sync

data class SyncV2Event(
    val revision: Long,
    val table: String,
    val rowId: String,
    val operation: SyncV2Operation,
    val payload: Map<String, String?>
)

data class SyncV2Tombstone(
    val revision: Long,
    val table: String,
    val rowId: String
)

enum class SyncV2Operation {
    INSERT,
    UPDATE,
    DELETE
}

/**
 * Meaning of a row after an incremental pull. Absence is deliberately UNKNOWN, never deletion.
 * Physical deletion is authoritative only when the server emits an explicit tombstone/delete event.
 */
enum class SyncPullRowSemantics {
    UPSERTED,
    UNCHANGED,
    EXPLICITLY_DELETED,
    UNKNOWN_NOT_RETURNED
}

data class SyncPullRowResult(
    val table: String,
    val rowId: String,
    val semantics: SyncPullRowSemantics,
    val revision: Long? = null
)

/** Extension point for the server-owned deletion feed selected by SERVER_PLAN. */
fun interface SyncTombstoneFeed {
    suspend fun pull(organizationId: String, sinceRevision: Long): List<SyncV2Tombstone>
}

object NoOpSyncTombstoneFeed : SyncTombstoneFeed {
    override suspend fun pull(organizationId: String, sinceRevision: Long): List<SyncV2Tombstone> = emptyList()
}

data class SyncV2Snapshot(
    val rows: Map<String, Map<String, Map<String, String?>>> = emptyMap(),
    val appliedRevision: Long = 0L
)

object SyncProtocolV2Convergence {

    fun apply(
        snapshot: SyncV2Snapshot,
        events: List<SyncV2Event>,
        tombstones: List<SyncV2Tombstone>
    ): SyncV2Snapshot {
        val rows = snapshot.rows
            .mapValues { (_, byId) -> byId.toMutableMap() }
            .toMutableMap()

        // Replayed pages/events are normal after a crash. Collapse exact logical changes deterministically.
        val changes = buildList {
            events.forEach { add(Change.Event(it.revision, it)) }
            tombstones.forEach { add(Change.Tombstone(it.revision, it)) }
        }.filter { it.revision > snapshot.appliedRevision }
            .distinctBy { it.deduplicationKey }
            .sortedWith(compareBy<Change> { it.revision }.thenBy { it.kindOrder }.thenBy { it.deduplicationKey })

        var latest = snapshot.appliedRevision
        changes.forEach { change ->
            latest = maxOf(latest, change.revision)
            when (change) {
                is Change.Event -> applyEvent(rows, change.event)
                is Change.Tombstone -> rows[change.tombstone.table]?.remove(change.tombstone.rowId)
            }
        }

        return SyncV2Snapshot(rows = rows.mapValues { it.value.toMap() }, appliedRevision = latest)
    }

    private fun applyEvent(
        rows: MutableMap<String, MutableMap<String, Map<String, String?>>>,
        event: SyncV2Event
    ) {
        if (event.operation == SyncV2Operation.DELETE) {
            rows[event.table]?.remove(event.rowId)
            return
        }

        val tableRows = rows.getOrPut(event.table) { mutableMapOf() }
        val previous = tableRows[event.rowId].orEmpty()
        tableRows[event.rowId] = previous + event.payload + mapOf("id" to event.rowId)
    }

    private sealed class Change {
        abstract val revision: Long
        abstract val kindOrder: Int
        abstract val deduplicationKey: String

        data class Event(
            override val revision: Long,
            val event: SyncV2Event
        ) : Change() {
            override val kindOrder: Int = 0
            override val deduplicationKey: String =
                "event:${event.revision}:${event.table}:${event.rowId}:${event.operation}"
        }

        data class Tombstone(
            override val revision: Long,
            val tombstone: SyncV2Tombstone
        ) : Change() {
            override val kindOrder: Int = 1
            override val deduplicationKey: String =
                "tombstone:${tombstone.revision}:${tombstone.table}:${tombstone.rowId}"
        }
    }
}
