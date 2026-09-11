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
import com.verto.app.utils.SearchTextNormalizer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushInvoiceDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingInvoiceDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching { deleteInvoiceCascadeOnSupabase(id, orgId) }
                .onFailure { e ->
                    android.util.Log.e("SyncManager", "Remote deletion failed")
                    failed += "$id (${e.message?.take(80)})"
                }
            if (isGoneFromSupabase("invoices", id, orgId)) {
                userPrefs.removePendingInvoiceDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.deleteInvoiceCascadeOnSupabase(invoiceId: String, orgId: String) {
        runCatching {
            supabase.postgrest["invoice_items"].delete {
                filter {
                    eq("invoice_id", invoiceId)
                    eq("organization_id", orgId)
                }
            }
        }.onFailure { android.util.Log.w("SyncManager", "Remote deletion failed") }

        runCatching {
            supabase.postgrest["payments"].delete {
                filter {
                    eq("invoice_id", invoiceId)
                    eq("organization_id", orgId)
                }
            }
        }.onFailure { android.util.Log.w("SyncManager", "Remote deletion failed") }

        supabase.postgrest["invoices"].delete {
            filter {
                eq("id", invoiceId)
                eq("organization_id", orgId)
            }
        }
    }
