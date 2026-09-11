package com.verto.app.data.sync

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verto.app.data.remote.dto.LOGISTICS_V2_TABLE_CONTRACTS
import com.verto.app.data.remote.dto.LogisticsV2TableContract
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Logistics V2 transport implementation.
 *
 * v211 keeps this transport inert even if [activateAfterVerifiedServerContract] is called.
 * SQL 008/009 are package-only until a later server session verifies them and explicitly
 * changes the hard gate. Missing remote tables are never caught and treated as success.
 */
class SyncLogisticsV2(
    private val runtime: SyncRuntime,
) {
    private companion object {
        // v211 ships the server contract only. A later verified server session must
        // deliberately flip this gate after SQL 008/009 and live RLS checks pass.
        const val SERVER_CONTRACT_VERIFIED = false
    }
    @Volatile
    private var verifiedOrganizations: Set<String> = emptySet()

    fun isRemoteEnabled(organizationId: String): Boolean =
        SERVER_CONTRACT_VERIFIED && organizationId.isNotBlank() && organizationId in verifiedOrganizations

    fun activateAfterVerifiedServerContract(organizationId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        check(SERVER_CONTRACT_VERIFIED) {
            "Logistics V2 remote activation is locked in v211 pending live SQL/RLS verification"
        }
        verifiedOrganizations = verifiedOrganizations + organizationId
    }

    suspend fun push(organizationId: String) {
        requireVerified(organizationId)
        LOGISTICS_V2_TABLE_CONTRACTS.forEach { contract ->
            val rows = readLocalRows(organizationId, contract)
            if (rows.isNotEmpty()) {
                runtime.supabase.postgrest[contract.tableName].upsert(rows) {
                    onConflict = contract.primaryKeyColumns.joinToString(",")
                }
            }
        }
    }

    suspend fun pull(organizationId: String) {
        requireVerified(organizationId)
        val remoteRows = LinkedHashMap<LogisticsV2TableContract, List<JsonObject>>()
        LOGISTICS_V2_TABLE_CONTRACTS.forEach { contract ->
            remoteRows[contract] = runtime.supabase.postgrest[contract.tableName].select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<JsonObject>()
        }

        val sqlite = runtime.db.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            remoteRows.forEach { (contract, rows) ->
                rows.forEach { row -> upsertLocalRow(sqlite, organizationId, contract, row) }
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun requireVerified(organizationId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        check(isRemoteEnabled(organizationId)) {
            "Logistics V2 remote is disabled until SQL/RLS server verification passes"
        }
    }

    private fun readLocalRows(
        organizationId: String,
        contract: LogisticsV2TableContract,
    ): List<JsonObject> {
        val projection = contract.columns.joinToString(",") { "`${it.name}`" }
        val cursor = runtime.db.openHelper.readableDatabase.query(
            "SELECT $projection FROM `${contract.tableName}` WHERE `organization_id` = ?",
            arrayOf(organizationId),
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(it.toRemoteJson(contract))
            }
        }
    }

    private fun Cursor.toRemoteJson(contract: LogisticsV2TableContract): JsonObject =
        JsonObject(
            buildMap {
                contract.columns.forEachIndexed { index, column ->
                    put(
                        column.name,
                        when {
                            isNull(index) -> JsonNull
                            column.integer -> JsonPrimitive(getLong(index))
                            else -> JsonPrimitive(getString(index))
                        },
                    )
                }
            },
        )

    private fun upsertLocalRow(
        sqlite: SupportSQLiteDatabase,
        organizationId: String,
        contract: LogisticsV2TableContract,
        remote: JsonObject,
    ) {
        val remoteOrganizationId = remote["organization_id"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.content
            ?: error("${contract.tableName}: organization_id missing")
        require(remoteOrganizationId == organizationId) {
            "${contract.tableName}: cross-organization row rejected"
        }

        val columns = contract.columns
        val names = columns.joinToString(",") { "`${it.name}`" }
        val placeholders = columns.joinToString(",") { "?" }
        val conflict = contract.primaryKeyColumns.joinToString(",") { "`${it}`" }
        val mutableColumns = columns.filterNot { it.name in contract.primaryKeyColumns }
        val updateClause = mutableColumns.joinToString(",") { "`${it.name}` = excluded.`${it.name}`" }
        val sql = if (updateClause.isBlank()) {
            "INSERT OR IGNORE INTO `${contract.tableName}` ($names) VALUES ($placeholders)"
        } else {
            "INSERT INTO `${contract.tableName}` ($names) VALUES ($placeholders) " +
                "ON CONFLICT($conflict) DO UPDATE SET $updateClause"
        }

        val args: Array<Any?> = columns.map { column ->
            val element = remote[column.name] ?: error("${contract.tableName}: ${column.name} missing")
            val value: Any? = when {
                element is JsonNull -> null
                column.integer -> element.jsonPrimitive.content.toLong()
                else -> element.jsonPrimitive.content
            }
            value
        }.toTypedArray()

        sqlite.execSQL(sql, args)
    }
}
