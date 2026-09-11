package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncPullV2Request(
    @SerialName("p_since_revision") val sinceRevision: Long,
    @SerialName("p_limit") val limit: Int
)

@Serializable
data class SyncAckV2Request(@SerialName("p_revision") val revision: Long)

@Serializable
data class SyncV2WireEvent(
    val revision: Long,
    @SerialName("table") val tableName: String,
    @SerialName("row_id") val rowId: String,
    val operation: String,
    val payload: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class SyncV2WireTombstone(
    val revision: Long,
    @SerialName("table") val tableName: String,
    @SerialName("row_id") val rowId: String
)

@Serializable
data class SyncV2Page(
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("since_revision") val sinceRevision: Long,
    @SerialName("next_revision") val nextRevision: Long,
    val events: List<SyncV2WireEvent> = emptyList(),
    val tombstones: List<SyncV2WireTombstone> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean
)

interface SyncV2Remote {
    suspend fun pull(sinceRevision: Long, limit: Int): SyncV2Page
    suspend fun acknowledge(revision: Long)
}

class SupabaseSyncV2Remote : SyncV2Remote {
    override suspend fun pull(sinceRevision: Long, limit: Int): SyncV2Page =
        VertoSupabase.client.postgrest.rpc(
            function = "sync_pull_v2",
            parameters = SyncPullV2Request(sinceRevision, limit)
        ).decodeAs()

    override suspend fun acknowledge(revision: Long) {
        VertoSupabase.client.postgrest.rpc(
            function = "sync_ack_v2",
            parameters = SyncAckV2Request(revision)
        )
    }
}

data class SyncV2PullTicket(
    val startingRevision: Long,
    val nextRevision: Long,
    val eventCount: Int,
    val tombstoneCount: Int
)

/**
 * Drains and validates the server cursor before the legacy readers run. The caller only invokes
 * [acknowledge] after every Room pull succeeds, so a crash or partial failure replays the page.
 */
class SyncV2Coordinator(
    private val remote: SyncV2Remote,
    private val pageSize: Int = 500,
    private val maxPages: Int = 100
) {
    init {
        require(pageSize in 1..1000)
        require(maxPages > 0)
    }

    suspend fun prepare(startingRevision: Long): SyncV2PullTicket {
        require(startingRevision >= 0L)
        var cursor = startingRevision
        var eventCount = 0
        var tombstoneCount = 0

        repeat(maxPages) {
            val page = remote.pull(cursor, pageSize)
            validate(page, cursor)
            eventCount += page.events.size
            tombstoneCount += page.tombstones.size
            cursor = page.nextRevision
            if (!page.hasMore) {
                return SyncV2PullTicket(startingRevision, cursor, eventCount, tombstoneCount)
            }
        }
        error("sync v2 exceeded the bounded page limit")
    }

    suspend fun acknowledge(ticket: SyncV2PullTicket) {
        if (ticket.nextRevision > ticket.startingRevision) {
            remote.acknowledge(ticket.nextRevision)
        }
    }

    private fun validate(page: SyncV2Page, requestedCursor: Long) {
        require(page.protocolVersion == 2) { "unsupported sync protocol ${page.protocolVersion}" }
        require(page.sinceRevision == requestedCursor) { "sync cursor response mismatch" }
        require(page.nextRevision >= requestedCursor) { "sync cursor moved backwards" }
        if (page.hasMore) require(page.nextRevision > requestedCursor) { "sync cursor did not advance" }

        val revisions = (page.events.map { it.revision } + page.tombstones.map { it.revision })
        require(revisions.all { it > requestedCursor && it <= page.nextRevision }) {
            "sync page contains an event outside its cursor window"
        }
        require(page.events.all { it.operation in setOf("INSERT", "UPDATE", "DELETE") }) {
            "sync page contains an unsupported operation"
        }
    }
}
