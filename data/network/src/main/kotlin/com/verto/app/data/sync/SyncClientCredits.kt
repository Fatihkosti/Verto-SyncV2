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
import com.verto.app.money.Money
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushClientCredits(orgId: String, userId: String) {
        val list = db.clientCreditDao().getDirtyCreditsSync()
        if (list.isEmpty()) return

        supabase.postgrest["client_credits"].upsert(
            list.map { c ->
                ClientCreditDto(
                    id              = c.id,
                    organizationId  = orgId,
                    clientId        = localClientIdToUuid(c.clientId, orgId),
                    amount          = Money.ofMinor(c.amountMinor).toMajorDecimal(),
                    note            = c.note,
                    sourcePaymentId = c.sourcePaymentId.ifBlank { null },
                    employeeId      = c.employeeId,
                    employeeName    = c.employeeName,
                    createdAt       = SupabaseDateParser.format(c.createdAt)
                )
            }
        ) { onConflict = "id" }
        db.clientCreditDao().markCreditsClean(list.map { it.id })
    }

    suspend fun SyncRuntime.pullClientCredits(orgId: String) {
        val remote = supabase.postgrest["client_credits"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<ClientCreditDto>()
        if (remote.isEmpty()) return

        remote.forEach { dto ->
            db.clientCreditDao().insertCreditFromRemote(
                ClientCreditEntity(
                    id              = dto.id,
                    clientId        = resolveClientId(dto.clientId, orgId),
                    amount          = Money.fromMajor(dto.amount).toLegacyDouble(),
                    amountMinor     = Money.fromMajor(dto.amount).amountMinor,
                    note            = dto.note,
                    sourcePaymentId = dto.sourcePaymentId ?: "",
                    createdAt       = dto.createdAt?.let { SupabaseDateParser.parse(it) } ?: System.currentTimeMillis(),
                    employeeId      = dto.employeeId,
                    employeeName    = dto.employeeName,
                    isDirty         = false   // مسحوب = نظيف
                )
            )
        }
    }

