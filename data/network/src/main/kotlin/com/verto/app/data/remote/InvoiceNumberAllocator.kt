package com.verto.app.data.remote

import com.verto.app.utils.PreferencesManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AllocateInvoiceNumberRequest(@SerialName("p_org") val pOrg: String)

/**
 * مُخصِّص أرقام الفواتير server-authoritative (الجلسة 4 من fixbacklog).
 *
 * يطلب الرقم النهائي من RPC ‏`allocate_invoice_number(p_org)` (ذرّي بقفل صف العدّاد
 * لكل مؤسسة على Supabase) فلا يتكرر رقم بين الأجهزة. عند عدم الاتصال/الفشل يُرجِع
 * `null` فيستخدم المتصل ترقيماً محلياً مؤقتاً (`MAX(local)+1`) — يُسوَّى لاحقاً.
 *
 * قرار المالك (2026-06-30): server أولاً عند الاتصال.
 */
class InvoiceNumberAllocator(
    private val prefs: PreferencesManager
) {
    /** الرقم النهائي من السيرفر، أو `null` إن تعذّر (offline/خطأ) → ترقيم محلي مؤقت. */
    suspend fun allocate(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val orgId = prefs.getLastOrgId()
            if (orgId.isBlank()) return@runCatching null
            VertoSupabase.client.postgrest
                .rpc("allocate_invoice_number", AllocateInvoiceNumberRequest(orgId))
                .data
                .trim()
                .toIntOrNull()
        }.getOrNull()
    }
}
