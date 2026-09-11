package com.verto.app.data.remote

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AllocateLogisticsShipmentNumberRequest(
    @SerialName("p_org") val organizationId: String,
)

/** Server-authoritative when the v176 RPC exists; returns null offline/unavailable. */
class LogisticsShipmentNumberAllocator @Inject constructor() {
    suspend fun allocate(organizationId: String): Int? = withContext(Dispatchers.IO) {
        if (organizationId.isBlank()) return@withContext null
        runCatching {
            VertoSupabase.client.postgrest
                .rpc(
                    "allocate_logistics_shipment_number",
                    AllocateLogisticsShipmentNumberRequest(organizationId),
                )
                .data
                .trim()
                .toIntOrNull()
        }.getOrNull()
    }
}
