package com.verto.app.data.sync.recovery

import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

/** Payload-free identity digest used to prove recovery cannot erase or rewrite unacked intent. */
object OutboxIdentityDigest {
    fun compute(db: SupportSQLiteDatabase, organizationId: String): String {
        val rows = mutableListOf<String>()
        collect(db, "SELECT mutation_id,organization_id,aggregate_type,aggregate_id,state,semantic_fingerprint FROM sync_outbox WHERE organization_id='${safe(organizationId)}' AND state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF') ORDER BY mutation_id", "unified", rows)
        collect(db, "SELECT operation_id,aggregate_type,aggregate_id,state FROM party_sync_outbox WHERE state IN ('PENDING','RETRY') ORDER BY operation_id", "party", rows)
        collect(db, "SELECT event_id,organization_id,write_id,aggregate_id,sync_state FROM financial_outbox WHERE organization_id='${safe(organizationId)}' AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED') ORDER BY event_id", "financial", rows)
        collect(db, "SELECT command_id,organization_id,movement_id,item_id,sync_state FROM inventory_stock_outbox WHERE organization_id='${safe(organizationId)}' AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED') ORDER BY command_id", "inventory_stock", rows)
        collect(db, "SELECT command_id,organization_id,cost_revision_id,sync_state FROM inventory_cost_outbox WHERE organization_id='${safe(organizationId)}' AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED') ORDER BY command_id", "inventory_cost", rows)
        collect(db, "SELECT event_id,organization_id,aggregate_type,aggregate_id,status,idempotency_key FROM optimal_outbox WHERE organization_id='${safe(organizationId)}' AND status NOT IN ('SYNCED','ACKNOWLEDGED') ORDER BY event_id", "optimal", rows)
        collect(db, "SELECT transfer_id,organization_id,mutation_id,aggregate_type,aggregate_id,state,content_checksum FROM sync_attachment_transfer WHERE organization_id='${safe(organizationId)}' AND state NOT IN ('COMPLETED','CANCELLED') ORDER BY transfer_id", "attachment", rows)
        return sha256(rows.joinToString("\n"))
    }

    private fun collect(db: SupportSQLiteDatabase, sql: String, owner: String, out: MutableList<String>) {
        db.query(sql).use { c ->
            while (c.moveToNext()) {
                val values = (0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "∅" else c.getString(i) }
                out += "$owner|$values"
            }
        }
    }

    private fun safe(value: String): String = value.replace("'", "''")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
