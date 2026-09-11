package com.verto.app.utils

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first



data class PersistedSyncRunState(
    val organizationId: String,
    val runId: String,
    val startingRevision: Long,
    val completedOperationKeys: Set<String>,
    val resumed: Boolean
)

internal class SyncPreferencesStore(private val context: Context) {
    suspend fun getPendingInvoiceDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_INVOICE_DELETIONS] ?: emptySet()

    suspend fun addPendingInvoiceDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_INVOICE_DELETIONS] = (prefs[KEY_PENDING_INVOICE_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingInvoiceDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_INVOICE_DELETIONS] = (prefs[KEY_PENDING_INVOICE_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun clearPendingInvoiceDeletions() {
        context.dataStore.edit { it.remove(KEY_PENDING_INVOICE_DELETIONS) }
    }

    suspend fun getPendingClientDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_CLIENT_DELETIONS] ?: emptySet()

    suspend fun addPendingClientDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_CLIENT_DELETIONS] = (prefs[KEY_PENDING_CLIENT_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingClientDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_CLIENT_DELETIONS] = (prefs[KEY_PENDING_CLIENT_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingInventoryDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_INVENTORY_DELETIONS] ?: emptySet()

    suspend fun addPendingInventoryDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_INVENTORY_DELETIONS] = (prefs[KEY_PENDING_INVENTORY_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingInventoryDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_INVENTORY_DELETIONS] = (prefs[KEY_PENDING_INVENTORY_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingExpenseDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_EXPENSE_DELETIONS] ?: emptySet()

    suspend fun addPendingExpenseDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_EXPENSE_DELETIONS] = (prefs[KEY_PENDING_EXPENSE_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingExpenseDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_EXPENSE_DELETIONS] = (prefs[KEY_PENDING_EXPENSE_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingCategoryDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_CATEGORY_DELETIONS] ?: emptySet()

    suspend fun addPendingCategoryDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_CATEGORY_DELETIONS] = (prefs[KEY_PENDING_CATEGORY_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingCategoryDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_CATEGORY_DELETIONS] = (prefs[KEY_PENDING_CATEGORY_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingCommissionDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_COMMISSION_DELETIONS] ?: emptySet()

    suspend fun addPendingCommissionDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_COMMISSION_DELETIONS] = (prefs[KEY_PENDING_COMMISSION_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingCommissionDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_COMMISSION_DELETIONS] = (prefs[KEY_PENDING_COMMISSION_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingUnitDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_UNIT_DELETIONS] ?: emptySet()

    suspend fun addPendingUnitDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_UNIT_DELETIONS] = (prefs[KEY_PENDING_UNIT_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingUnitDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_UNIT_DELETIONS] = (prefs[KEY_PENDING_UNIT_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingBudgetDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_BUDGET_DELETIONS] ?: emptySet()

    suspend fun addPendingBudgetDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_BUDGET_DELETIONS] = (prefs[KEY_PENDING_BUDGET_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingBudgetDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_BUDGET_DELETIONS] = (prefs[KEY_PENDING_BUDGET_DELETIONS] ?: emptySet()) - id
        }
    }

    suspend fun getPendingReconciliationDeletions(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_RECONCILIATION_DELETIONS] ?: emptySet()

    suspend fun addPendingReconciliationDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_RECONCILIATION_DELETIONS] = (prefs[KEY_PENDING_RECONCILIATION_DELETIONS] ?: emptySet()) + id
        }
    }

    suspend fun removePendingReconciliationDeletion(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PENDING_RECONCILIATION_DELETIONS] = (prefs[KEY_PENDING_RECONCILIATION_DELETIONS] ?: emptySet()) - id
        }
    }

    // ── Incremental Sync: last_pulled_at ──────────────────────────────────────────

    suspend fun getLastPulledAt(key: Preferences.Key<Long>): Long =
        context.dataStore.data.first()[key] ?: 0L

    suspend fun setLastPulledAt(key: Preferences.Key<Long>, timestamp: Long) {
        context.dataStore.edit { it[key] = timestamp }
    }

    suspend fun getSyncV2Cursor(orgId: String): Long {
        val prefs = context.dataStore.data.first()
        return if (prefs[KEY_SYNC_V2_ORG_ID] == orgId) prefs[KEY_SYNC_V2_CURSOR] ?: 0L else 0L
    }

    suspend fun setSyncV2Cursor(orgId: String, revision: Long) {
        require(orgId.isNotBlank()) { "organization id is required for sync cursor" }
        require(revision >= 0L) { "sync revision cannot be negative" }
        context.dataStore.edit { prefs ->
            val current = if (prefs[KEY_SYNC_V2_ORG_ID] == orgId) {
                prefs[KEY_SYNC_V2_CURSOR] ?: 0L
            } else {
                0L
            }
            prefs[KEY_SYNC_V2_ORG_ID] = orgId
            prefs[KEY_SYNC_V2_CURSOR] = maxOf(current, revision)
        }
    }

    // ── Crash-safe sync operation checkpoint ─────────────────────────────────────

    suspend fun beginOrResumeSyncRun(orgId: String, startingRevision: Long): PersistedSyncRunState {
        require(orgId.isNotBlank()) { "organization id is required for sync run" }
        require(startingRevision >= 0L) { "starting revision cannot be negative" }

        var state: PersistedSyncRunState? = null
        context.dataStore.edit { prefs ->
            val storedOrgId = prefs[KEY_SYNC_RUN_ORG_ID].orEmpty()
            val storedRunId = prefs[KEY_SYNC_RUN_ID].orEmpty()
            val storedRevision = prefs[KEY_SYNC_RUN_START_REVISION] ?: -1L
            val canResume = storedOrgId == orgId && storedRunId.isNotBlank() && storedRevision == startingRevision

            val runId = if (canResume) storedRunId else java.util.UUID.randomUUID().toString()
            val completed = if (canResume) {
                prefs[KEY_SYNC_RUN_COMPLETED_KEYS] ?: emptySet()
            } else {
                emptySet()
            }

            prefs[KEY_SYNC_RUN_ORG_ID] = orgId
            prefs[KEY_SYNC_RUN_ID] = runId
            prefs[KEY_SYNC_RUN_START_REVISION] = startingRevision
            prefs[KEY_SYNC_RUN_COMPLETED_KEYS] = completed
            state = PersistedSyncRunState(orgId, runId, startingRevision, completed, canResume)
        }
        return checkNotNull(state)
    }

    suspend fun markSyncOperationCompleted(orgId: String, runId: String, operationKey: String) {
        require(orgId.isNotBlank() && runId.isNotBlank() && operationKey.isNotBlank())
        context.dataStore.edit { prefs ->
            check(prefs[KEY_SYNC_RUN_ORG_ID] == orgId && prefs[KEY_SYNC_RUN_ID] == runId) {
                "sync checkpoint does not belong to the active run"
            }
            prefs[KEY_SYNC_RUN_COMPLETED_KEYS] =
                (prefs[KEY_SYNC_RUN_COMPLETED_KEYS] ?: emptySet()) + operationKey
        }
    }

    suspend fun completeSyncRun(orgId: String, runId: String) {
        context.dataStore.edit { prefs ->
            check(prefs[KEY_SYNC_RUN_ORG_ID] == orgId && prefs[KEY_SYNC_RUN_ID] == runId) {
                "sync checkpoint does not belong to the active run"
            }
            prefs.remove(KEY_SYNC_RUN_ORG_ID)
            prefs.remove(KEY_SYNC_RUN_ID)
            prefs.remove(KEY_SYNC_RUN_START_REVISION)
            prefs.remove(KEY_SYNC_RUN_COMPLETED_KEYS)
        }
    }


    // ── آخر تقرير مزامنة مختصر، معزول حسب المؤسسة والمستخدم ───────────────

    suspend fun getPersistedSyncReport(organizationId: String, userId: String): String? {
        if (organizationId.isBlank() || userId.isBlank()) return null
        val scope = reportScope(organizationId, userId)
        return context.dataStore.data.first()[KEY_SYNC_REPORT_SUMMARIES]
            .orEmpty()
            .firstOrNull { it.startsWith("$scope|") }
            ?.substringAfter('|')
            ?.takeIf(String::isNotBlank)
    }

    suspend fun setPersistedSyncReport(organizationId: String, userId: String, reportJson: String) {
        require(organizationId.isNotBlank()) { "organization id is required for sync report" }
        require(userId.isNotBlank()) { "user id is required for sync report" }
        require(reportJson.isNotBlank()) { "sync report cannot be blank" }
        val scope = reportScope(organizationId, userId)
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_SYNC_REPORT_SUMMARIES].orEmpty()
                .filterNot { it.startsWith("$scope|") }
                .toSet()
            prefs[KEY_SYNC_REPORT_SUMMARIES] = current + "$scope|$reportJson"
        }
    }

    private fun reportScope(organizationId: String, userId: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$organizationId\u0000$userId".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    // ── هوية الجلسة ──────────────────────────────────────────────────────────────

    suspend fun getLastOrgId(): String =
        context.dataStore.data.first()[KEY_LAST_ORG_ID] ?: ""

    suspend fun setLastOrgId(orgId: String) {
        context.dataStore.edit { it[KEY_LAST_ORG_ID] = orgId }
    }

    /** Session 311 stale-work authority. This key is deliberately not removed by clearSessionData(). */
    suspend fun getSessionEpoch(): Long =
        context.dataStore.data.first()[KEY_SYNC_SESSION_EPOCH] ?: 0L

    suspend fun activateNextSessionEpoch(): Long {
        var next = 0L
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_SYNC_SESSION_EPOCH] ?: 0L
            check(current < Long.MAX_VALUE) { "session epoch exhausted" }
            next = current + 1L
            prefs[KEY_SYNC_SESSION_EPOCH] = next
        }
        return next
    }

    /** يمسح هوية الحساب وكل بيانات المؤسسة، مع إبقاء تفضيلات العرض العامة فقط. */
    suspend fun clearSessionData() {
        context.dataStore.edit { p ->
            // الهوية والجلسة: لا تبقى أي بيانات شخصية بعد الخروج.
            p.remove(KEY_USER_ID);              p.remove(KEY_USER_NAME)
            p.remove(KEY_USER_PHONE);           p.remove(KEY_USER_ROLE)
            p.remove(KEY_USER_PERMISSIONS);     p.remove(KEY_LAST_ORG_ID)
            p.remove(KEY_OWNER_NAME);            p.remove(KEY_SHOP_NAME)
            p.remove(KEY_SHOP_PHONE);            p.remove(KEY_ORG_ADDRESS)
            p.remove(KEY_PIN_ENABLED);           p.remove(KEY_PIN_HASH)
            p.remove(KEY_PIN_SALT);              p.remove(KEY_RECOVERY_CODE)
            p.remove(KEY_RECOVERY_CODE_SALT)

            // كاش بيانات المؤسسة.
            p.remove(KEY_ORG_SHOP_NAME);         p.remove(KEY_ORG_SHOP_PHONE)
            p.remove(KEY_ORG_CITY);              p.remove(KEY_ORG_ADDRESS2)
            p.remove(KEY_ORG_CURRENCY);          p.remove(KEY_ORG_INVOICE_FOOTER)
            p.remove(KEY_ORG_TAX_NUMBER);        p.remove(KEY_ORG_LOGO_URL)
            p.remove(KEY_ORG_SIGNATURE_URL);     p.remove(KEY_SUBSCRIPTION_TIER)
            p.remove(KEY_ALLOW_NEGATIVE_STOCK);  p.remove(KEY_ALLOW_CASH_OVERDRAFT)

            // طابور الحذف المؤجل.
            p.remove(KEY_PENDING_INVOICE_DELETIONS)
            p.remove(KEY_PENDING_CLIENT_DELETIONS)
            p.remove(KEY_PENDING_INVENTORY_DELETIONS)
            p.remove(KEY_PENDING_EXPENSE_DELETIONS)
            p.remove(KEY_PENDING_CATEGORY_DELETIONS)
            p.remove(KEY_PENDING_COMMISSION_DELETIONS)
            p.remove(KEY_PENDING_UNIT_DELETIONS)
            p.remove(KEY_PENDING_BUDGET_DELETIONS)
            p.remove(KEY_PENDING_RECONCILIATION_DELETIONS)
            p.remove(KEY_LAST_PULLED_BUDGETS)

            // مؤشرات المزامنة والتتبع.
            p.remove(KEY_LAST_PULLED_CLIENTS);       p.remove(KEY_LAST_PULLED_INVOICES)
            p.remove(KEY_LAST_PULLED_INVENTORY);     p.remove(KEY_LAST_PULLED_PAYMENTS)
            p.remove(KEY_LAST_PULLED_EXPENSES);      p.remove(KEY_LAST_PULLED_CASH_MVTS)
            p.remove(KEY_LAST_PULLED_ORG_SETTINGS);  p.remove(KEY_LAST_PULLED_INV_MOVEMENTS)
            p.remove(KEY_LAST_PULLED_COMMISSIONS);   p.remove(KEY_LAST_PULLED_SHIPMENTS)
            p.remove(KEY_LAST_PULLED_CATEGORIES);    p.remove(KEY_LAST_PULLED_NOTES)
            p.remove(KEY_LAST_PULLED_REMINDERS);     p.remove(KEY_SYNC_V2_ORG_ID)
            p.remove(KEY_SYNC_V2_CURSOR);            p.remove(KEY_SYNC_RUN_ORG_ID)
            p.remove(KEY_SYNC_RUN_ID);               p.remove(KEY_SYNC_RUN_START_REVISION)
            p.remove(KEY_SYNC_RUN_COMPLETED_KEYS);      p.remove(KEY_SYNC_REPORT_SUMMARIES)
        }
    }

    suspend fun migrateFromSharedPreferences() {
        val alreadyMigrated = context.dataStore.data.first()[KEY_SP_MIGRATED] ?: false
        if (alreadyMigrated) return
        val sharedPrefs = context.getSharedPreferences("verto_prefs", android.content.Context.MODE_PRIVATE)
        context.dataStore.edit { prefs ->
            sharedPrefs.getStringSet("pending_invoice_deletions", null)?.let {
                prefs[KEY_PENDING_INVOICE_DELETIONS] = it
            }
            sharedPrefs.getStringSet("pending_client_deletions", null)?.let {
                prefs[KEY_PENDING_CLIENT_DELETIONS] = it
            }
            sharedPrefs.getStringSet("pending_inventory_deletions", null)?.let {
                prefs[KEY_PENDING_INVENTORY_DELETIONS] = it
            }
            prefs[KEY_SP_MIGRATED] = true
        }
    }
}
