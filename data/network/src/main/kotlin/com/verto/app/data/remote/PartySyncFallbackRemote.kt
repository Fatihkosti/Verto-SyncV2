package com.verto.app.data.remote

import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.dto.ClientDto
import com.verto.app.data.remote.dto.InvoiceDto
import com.verto.app.data.remote.dto.PaymentDto
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartySyncFallbackRemote @Inject constructor(
    private val sessionReader: SessionReader
) {
    data class Snapshot(
        val clients: List<ClientDto>,
        val invoices: List<InvoiceDto>,
        val payments: List<PaymentDto>
    )

    suspend fun pull(): Snapshot {
        val orgId = TenantIsolationPolicy.requireTenantId(sessionReader.organizationId.first())
        val clients = VertoSupabase.client.postgrest["clients"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<ClientDto>()
            .onEach { TenantIsolationPolicy.requireSameTenant(orgId, it.organizationId) }
        val invoices = VertoSupabase.client.postgrest["invoices"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<InvoiceDto>()
            .onEach { TenantIsolationPolicy.requireSameTenant(orgId, it.organizationId) }
        val payments = VertoSupabase.client.postgrest["payments"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<PaymentDto>()
            .onEach { TenantIsolationPolicy.requireSameTenant(orgId, it.organizationId) }
        return Snapshot(clients, invoices, payments)
    }
}
