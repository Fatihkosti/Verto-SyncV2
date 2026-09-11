package com.verto.app.data.sync

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.utils.KEY_LAST_PULLED_BUDGETS
import com.verto.app.utils.KEY_LAST_PULLED_CASH_MVTS
import com.verto.app.utils.KEY_LAST_PULLED_CATEGORIES
import com.verto.app.utils.KEY_LAST_PULLED_CLIENTS
import com.verto.app.utils.KEY_LAST_PULLED_COMMISSIONS
import com.verto.app.utils.KEY_LAST_PULLED_EXPENSES
import com.verto.app.utils.KEY_LAST_PULLED_INVOICES
import com.verto.app.utils.KEY_LAST_PULLED_INV_MOVEMENTS
import com.verto.app.utils.KEY_LAST_PULLED_INVENTORY
import com.verto.app.utils.KEY_LAST_PULLED_NOTES
import com.verto.app.utils.KEY_LAST_PULLED_PAYMENTS
import com.verto.app.utils.KEY_LAST_PULLED_REMINDERS
import com.verto.app.utils.KEY_LAST_PULLED_SHIPMENTS
import com.verto.app.utils.PreferencesManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


data class SyncRunCheckpoint(
    val organizationId: String,
    val runId: String,
    val startingRevision: Long,
    val completedOperationKeys: Set<String>,
    val resumed: Boolean
)

object InitialSyncPolicy {
    fun shouldRun(roomIsEmpty: Boolean): Boolean = roomIsEmpty
}

/**
 * بوابة البنية التحتية المشتركة لعمليات المزامنة.
 *
 * Participants تستخدمها للوصول إلى Room وSupabase والتفضيلات، بينما يبقى SyncManager منسقًا فقط.
 */
class SyncRuntime(
    internal val db: AppDatabase,
    internal val userPrefs: PreferencesManager
) {
    internal val supabase by lazy { VertoSupabase.client }
    var syncV2Remote: SyncV2Remote = SupabaseSyncV2Remote()

    @Volatile
    private var initialSyncRequired = false

    /** Detects a fresh/cleared Room database without treating lastSync as an error. */
    suspend fun isLocalDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.readableDatabase
        val tables = sqlite.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')"
        )
        tables.use {
            while (it.moveToNext()) {
                val tableName = it.getString(0).replace("`", "``")
                val rows = sqlite.query("SELECT 1 FROM `$tableName` LIMIT 1")
                val hasRows = rows.use { cursor -> cursor.moveToFirst() }
                if (hasRows) return@withContext false
            }
        }
        true
    }

    /** Forces every legacy reader to start at the beginning for an empty Room database. */
    suspend fun prepareForSync() {
        if (!initialSyncRequired && !isLocalDatabaseEmpty()) return

        initialSyncRequired = true
        LEGACY_PULL_MARKERS.forEach { key -> userPrefs.setLastPulledAt(key, 0L) }
    }

    fun markSyncCompleted() {
        initialSyncRequired = false
    }

    suspend fun clearLocalData() {
        db.clearAllTables()
        userPrefs.clearSessionData()
        initialSyncRequired = false
    }

    suspend fun getLastOrganizationId(): String = userPrefs.getLastOrgId()

    suspend fun setLastOrganizationId(orgId: String) {
        userPrefs.setLastOrgId(orgId)
    }

    suspend fun getSessionEpoch(): Long = userPrefs.getSessionEpoch()

    suspend fun activateNextSessionEpoch(): Long = userPrefs.activateNextSessionEpoch()

    suspend fun getSyncCursor(orgId: String): Long = userPrefs.getSyncV2Cursor(orgId)

    suspend fun setSyncCursor(orgId: String, revision: Long) {
        userPrefs.setSyncV2Cursor(orgId, revision)
    }

    suspend fun beginOrResumeSyncRun(orgId: String, startingRevision: Long): SyncRunCheckpoint {
        val state = userPrefs.beginOrResumeSyncRun(orgId, startingRevision)
        return SyncRunCheckpoint(
            organizationId = state.organizationId,
            runId = state.runId,
            startingRevision = state.startingRevision,
            completedOperationKeys = state.completedOperationKeys,
            resumed = state.resumed
        )
    }

    suspend fun markSyncOperationCompleted(
        orgId: String,
        runId: String,
        operationKey: String
    ) {
        userPrefs.markSyncOperationCompleted(orgId, runId, operationKey)
    }

    suspend fun completeSyncRun(orgId: String, runId: String) {
        userPrefs.completeSyncRun(orgId, runId)
    }

    suspend fun getPersistedSyncReport(orgId: String, userId: String): String? =
        userPrefs.getPersistedSyncReport(orgId, userId)

    suspend fun setPersistedSyncReport(orgId: String, userId: String, reportJson: String) {
        userPrefs.setPersistedSyncReport(orgId, userId, reportJson)
    }


    suspend fun currentOrganizationDisplayName(): String =
        userPrefs.orgShopName.first().ifBlank { userPrefs.shopName.first() }

    suspend fun pendingInventoryDeletions(): Set<String> =
        userPrefs.getPendingInventoryDeletions()

    suspend fun captureDeletionSnapshot(): SyncDeletionSnapshot = SyncDeletionSnapshot(
        clientIds = userPrefs.getPendingClientDeletions(),
        invoiceIds = userPrefs.getPendingInvoiceDeletions(),
        inventoryIds = userPrefs.getPendingInventoryDeletions(),
        expenseIds = userPrefs.getPendingExpenseDeletions(),
        categoryIds = userPrefs.getPendingCategoryDeletions(),
        commissionIds = userPrefs.getPendingCommissionDeletions(),
        unitIds = userPrefs.getPendingUnitDeletions(),
        budgetIds = userPrefs.getPendingBudgetDeletions(),
        reconciliationIds = userPrefs.getPendingReconciliationDeletions()
    )

    internal suspend fun isGoneFromSupabase(table: String, id: String, orgId: String): Boolean =
        runCatching {
            supabase.postgrest[table].select {
                filter {
                    eq("id", id)
                    eq("organization_id", orgId)
                }
            }.decodeList<Map<String, Any>>().isEmpty()
        }.getOrDefault(false)

    private companion object {
        val LEGACY_PULL_MARKERS = listOf(
            KEY_LAST_PULLED_CLIENTS,
            KEY_LAST_PULLED_INVOICES,
            KEY_LAST_PULLED_PAYMENTS,
            KEY_LAST_PULLED_INVENTORY,
            KEY_LAST_PULLED_INV_MOVEMENTS,
            KEY_LAST_PULLED_CATEGORIES,
            KEY_LAST_PULLED_EXPENSES,
            KEY_LAST_PULLED_NOTES,
            KEY_LAST_PULLED_REMINDERS,
            KEY_LAST_PULLED_COMMISSIONS,
            KEY_LAST_PULLED_BUDGETS,
            KEY_LAST_PULLED_CASH_MVTS,
            KEY_LAST_PULLED_SHIPMENTS,
        )
    }
}
