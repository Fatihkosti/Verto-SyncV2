package com.verto.app.data.sync.recovery

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verto.app.data.local.entity.SyncRecoveryProtectionManifestEntity
import java.security.MessageDigest

/**
 * B13 full-content seal for unresolved local work. Unlike OutboxIdentityDigest this hashes every
 * persisted column of each unresolved owner row, frozen mutation/batch bytes, pending references,
 * local content-generation hashes and attachment metadata/checksums.
 */
object RecoveryProtectionManifest {
    fun capture(
        db: SupportSQLiteDatabase,
        scopeId: String,
        sessionId: String,
        organizationId: String,
        capturedAt: Long,
    ): SyncRecoveryProtectionManifestEntity {
        val org = safe(organizationId)
        val unified = digestQuery(db,
            "SELECT * FROM sync_outbox WHERE organization_id='$org' AND state NOT IN ('ACKNOWLEDGED','SUPERSEDED_WITH_PROOF') ORDER BY mutation_id",
            "sync_outbox")
        val party = digestQuery(db,
            "SELECT o.* FROM party_sync_outbox o WHERE o.state NOT IN ('ACKNOWLEDGED','SYNCED') " +
                "AND EXISTS (SELECT 1 FROM party_roles r WHERE r.organization_id='$org' AND r.party_id=o.aggregate_id) ORDER BY o.id",
            "party_sync_outbox")
        val financial = digestQuery(db,
            "SELECT * FROM financial_outbox WHERE organization_id='$org' AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED') ORDER BY event_id",
            "financial_outbox")
        val inventoryStock = digestQuery(db,
            "SELECT * FROM inventory_stock_outbox WHERE organization_id='$org' AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED') ORDER BY id",
            "inventory_stock_outbox")
        val inventoryCost = digestQuery(db,
            "SELECT * FROM inventory_cost_outbox WHERE organization_id='$org' AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED') ORDER BY id",
            "inventory_cost_outbox")
        val optimal = digestQuery(db,
            "SELECT * FROM optimal_outbox WHERE organization_id='$org' AND status NOT IN ('ACKNOWLEDGED','SYNCED') ORDER BY event_id",
            "optimal_outbox")
        val attachment = digestQuery(db,
            "SELECT * FROM sync_attachment_transfer WHERE organization_id='$org' AND state NOT IN ('COMPLETED','CANCELLED') ORDER BY transfer_id",
            "sync_attachment_transfer")
        val pending = digestQuery(db,
            "SELECT * FROM sync_pending_reference WHERE organization_id='$org' ORDER BY source_owner,source_id,protected_type,protected_id",
            "sync_pending_reference")
        val packets = digestMany(db, listOf(
            "sync_mutation_packet" to "SELECT * FROM sync_mutation_packet WHERE organization_id='$org' ORDER BY mutation_id",
            "sync_write_batch" to "SELECT * FROM sync_write_batch WHERE organization_id='$org' ORDER BY batch_id",
            "sync_write_batch_member" to "SELECT * FROM sync_write_batch_member WHERE organization_id='$org' ORDER BY batch_id,member_order",
        ))
        // sync_local_generation.content_hash is captured transactionally from the producer's actual
        // protected business snapshot; hashing the complete rows proves those content seals survive.
        val generations = digestQuery(db,
            "SELECT * FROM sync_local_generation WHERE organization_id='$org' ORDER BY aggregate_type,aggregate_id",
            "sync_local_generation")
        val combined = sha256(listOf(
            unified, party, financial, inventoryStock, inventoryCost, optimal, attachment,
            pending, packets, generations,
        ).joinToString("\n"))
        return SyncRecoveryProtectionManifestEntity(
            scopeId, sessionId, organizationId, combined, unified, party, financial,
            inventoryStock, inventoryCost, optimal, attachment, pending, packets, generations, capturedAt,
        )
    }

    fun sameContent(a: SyncRecoveryProtectionManifestEntity, b: SyncRecoveryProtectionManifestEntity): Boolean =
        a.scopeId == b.scopeId && a.bootstrapSessionId == b.bootstrapSessionId && a.organizationId == b.organizationId &&
            a.combinedSha256 == b.combinedSha256 && a.unifiedSha256 == b.unifiedSha256 &&
            a.partySha256 == b.partySha256 && a.financialSha256 == b.financialSha256 &&
            a.inventoryStockSha256 == b.inventoryStockSha256 && a.inventoryCostSha256 == b.inventoryCostSha256 &&
            a.optimalSha256 == b.optimalSha256 && a.attachmentSha256 == b.attachmentSha256 &&
            a.pendingReferenceSha256 == b.pendingReferenceSha256 && a.mutationPacketSha256 == b.mutationPacketSha256 &&
            a.localGenerationSha256 == b.localGenerationSha256

    private fun digestMany(db: SupportSQLiteDatabase, queries: List<Pair<String, String>>): String {
        val lines = mutableListOf<String>()
        queries.forEach { (label, sql) -> collect(db, sql, label, lines) }
        return sha256(lines.joinToString("\n"))
    }

    private fun digestQuery(db: SupportSQLiteDatabase, sql: String, label: String): String {
        val lines = mutableListOf<String>()
        collect(db, sql, label, lines)
        return sha256(lines.joinToString("\n"))
    }

    private fun collect(db: SupportSQLiteDatabase, sql: String, label: String, out: MutableList<String>) {
        db.query(sql).use { cursor ->
            val columns = cursor.columnNames.toList()
            while (cursor.moveToNext()) {
                val encoded = columns.indices.joinToString("|") { i -> "${columns[i]}=${encodeCell(cursor, i)}" }
                out += "$label|$encoded"
            }
        }
    }

    private fun encodeCell(cursor: Cursor, index: Int): String = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "N:"
        Cursor.FIELD_TYPE_INTEGER -> "I:${cursor.getLong(index)}"
        Cursor.FIELD_TYPE_FLOAT -> "F:${java.lang.Double.toHexString(cursor.getDouble(index))}"
        Cursor.FIELD_TYPE_STRING -> "S:${escape(cursor.getString(index))}"
        Cursor.FIELD_TYPE_BLOB -> "B:${cursor.getBlob(index).joinToString("") { "%02x".format(it.toInt() and 0xff) }}"
        else -> error("UNSUPPORTED_SQLITE_STORAGE_CLASS")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '|' -> append("\\|")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }

    private fun safe(value: String): String = value.replace("'", "''")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
