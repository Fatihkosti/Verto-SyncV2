package com.verto.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import com.verto.app.data.local.entity.*
import java.util.UUID
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushExpenses(orgId: String, userId: String) {
        val list = db.expenseDao().getDirtyExpensesSync()  // SYNC-012
        if (list.isEmpty()) return

        supabase.postgrest["expenses"].upsert(
            list.map { e ->
                ExpenseDto(
                    id             = e.id,
                    organizationId = orgId,
                    createdBy      = userId,
                    category       = e.category ?: "",
                    item           = e.item,
                    amount         = e.amount.toRemoteDecimal(),
                    note           = e.note ?: "",
                    expenseDate    = SupabaseDateParser.format(e.date),
                    updatedAt      = SupabaseDateParser.format(System.currentTimeMillis())
                )
            }
        ) { onConflict = "id" }
        db.expenseDao().markExpensesClean(list.map { it.id })  // SYNC-012
    }

    suspend fun SyncRuntime.pushExpenseDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingExpenseDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching {
                supabase.postgrest["expenses"].delete {
                    filter {
                        eq("id", id)
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
                failed += "$id (${e.message?.take(80)})"
            }
            if (isGoneFromSupabase("expenses", id, orgId)) {
                userPrefs.removePendingExpenseDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.pullExpenses(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_EXPENSES)
        val pullStartedAt = System.currentTimeMillis()

        // SYNC-011: الفلتر التدريجي على updated_at (لا created_at) ليصل تعديل المبلغ.
        // insertExpense (REPLACE) يُحدِّث الصف الموجود؛ ExpenseEntity بلا حقول محلية خارج الـ DTO.
        val remote = supabase.postgrest["expenses"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<ExpenseDto>()
        if (remote.isEmpty()) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_EXPENSES, pullStartedAt)
            return
        }

        // SYNC-006 guard: لا تُعِد إدراج مصروف محذوف محلياً لم يُحذف من Supabase بعد (فشل push)
        val pendingDeletions = userPrefs.getPendingExpenseDeletions() + alreadyDeletedIds

        remote.filter { it.id !in pendingDeletions }.forEach { dto ->
            try {
                db.expenseDao().insertExpense(
                    ExpenseEntity(
                        id       = dto.id,
                        category = dto.category,
                        item     = dto.item,
                        amount   = dto.amount.toRemoteDouble(),
                        note     = dto.note,
                        date     = SupabaseDateParser.parse(dto.expenseDate),
                        isDirty  = false   // SYNC-012: مسحوب = نظيف
                    )
                )
            } catch (e: SQLiteConstraintException) {
                android.util.Log.w("SyncManager", "Expense skipped because dependency is not ready")
            }
        }

        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_EXPENSES, pullStartedAt)
    }

    suspend fun SyncRuntime.pushNotes(orgId: String, userId: String) {
        val list = db.noteDao().getAllNotesSync()
        if (list.isEmpty()) return

        supabase.postgrest["notes"].upsert(
            list.map { n ->
                NoteDto(
                    id             = n.id,
                    organizationId = orgId,
                    createdBy      = userId,
                    clientId       = localClientIdToUuid(n.clientId, orgId),
                    text           = n.text,
                    createdAt      = SupabaseDateParser.format(n.createdAt)
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullNotes(orgId: String) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_NOTES)
        val pullStartedAt = System.currentTimeMillis()

        val remote = supabase.postgrest["notes"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("created_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<NoteDto>()
        if (remote.isEmpty()) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_NOTES, pullStartedAt)
            return
        }

        // FK guard: تجاهل الملاحظات التي عميلها غير موجود محلياً (أو بلا عميل)
        val existingClientIds = db.clientDao().getAllClientsSyncForOrganization(orgId).map { it.id }.toSet()
        remote.forEach { dto ->
            val localClientId = dto.clientId?.let { resolveClientId(it, orgId) }
            if (localClientId != null && localClientId in existingClientIds) {
                db.noteDao().insertNoteFromRemote(
                    NoteEntity(
                        id        = dto.id,
                        clientId  = localClientId,
                        text      = dto.text,
                        createdAt = SupabaseDateParser.parse(dto.createdAt)
                    )
                )
            }
        }

        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_NOTES, pullStartedAt)
    }

    suspend fun SyncRuntime.pushReminders(orgId: String) {
        val list = db.clientReminderDao().getAllRemindersSync()
        if (list.isEmpty()) return

        supabase.postgrest["client_reminders"].upsert(
            list.map { r ->
                ClientReminderDto(
                    id             = r.id,
                    organizationId = orgId,
                    clientId       = localClientIdToUuid(r.clientId, orgId),
                    note           = r.note,
                    reminderAt     = SupabaseDateParser.format(r.reminderAt),
                    isDone         = r.isDone,
                    createdAt      = SupabaseDateParser.format(r.createdAt)
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullReminders(orgId: String) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_REMINDERS)
        val pullStartedAt = System.currentTimeMillis()

        val remote = supabase.postgrest["client_reminders"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("created_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<ClientReminderDto>()
        if (remote.isEmpty()) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_REMINDERS, pullStartedAt)
            return
        }

        // FK guard: تجاهل التذكيرات التي عميلها غير موجود محلياً (أو بلا عميل)
        val existingClientIds = db.clientDao().getAllClientsSyncForOrganization(orgId).map { it.id }.toSet()
        remote.forEach { dto ->
            val localClientId = dto.clientId?.let { resolveClientId(it, orgId) }
            if (localClientId != null && localClientId in existingClientIds) {
                db.clientReminderDao().insertReminderFromRemote(
                    ClientReminderEntity(
                        id         = dto.id,
                        clientId   = localClientId,
                        note       = dto.note,
                        reminderAt = SupabaseDateParser.parse(dto.reminderAt),
                        isDone     = dto.isDone,
                        createdAt  = SupabaseDateParser.parse(dto.createdAt)
                    )
                )
            }
        }

        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_REMINDERS, pullStartedAt)
    }

suspend fun SyncRuntime.pullCommissions(orgId: String) {
    val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_COMMISSIONS)
    val pullStartedAt = System.currentTimeMillis()

    val remote = supabase.postgrest["commission_payments"].select {
        filter {
            eq("organization_id", orgId)
            // commission_payments لا يحوي created_at؛ الفلتر التدريجي على paid_at.
            if (lastPulledAt > 0L) gte("paid_at", SupabaseDateParser.format(lastPulledAt))
        }
    }.decodeList<CommissionPaymentDto>()
    if (remote.isEmpty()) {
        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_COMMISSIONS, pullStartedAt)
        return
    }

    remote.forEach { dto ->
        db.commissionPaymentDao().insertFromRemote(
            CommissionPaymentEntity(
                id = dto.id,
                clientId = dto.clientId,
                clientName = dto.clientName,
                invoiceIds = dto.invoiceIds,
                totalAmount = dto.totalAmount.toRemoteDouble(),
                bankName = dto.bankName,
                transactionRef = dto.transactionRef,
                paidAt = SupabaseDateParser.parse(dto.paidAt)
            )
        )
    }

    userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_COMMISSIONS, pullStartedAt)
}
