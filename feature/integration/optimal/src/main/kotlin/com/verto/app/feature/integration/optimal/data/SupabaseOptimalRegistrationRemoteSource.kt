package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class IssueOptimalCompanyJoinCodeRequest(
    @SerialName("p_client_id") val clientId: String,
    @SerialName("p_expires_in_minutes") val expiresInMinutes: Int = 1_440,
)

@Serializable
private data class OptimalBackendContractRemoteDto(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("verto_registration_ready") val vertoRegistrationReady: Boolean,
)

@Serializable
private data class OptimalCompanyJoinCodeRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("company_name") val companyName: String,
    val code: String,
    @SerialName("expires_at") val expiresAt: String,
)

class SupabaseOptimalRegistrationRemoteSource @Inject constructor() : OptimalRegistrationRemoteSource {
    override suspend fun issueCompanyJoinCode(clientId: String): OptimalRegistrationRemoteCode {
        val normalizedClientId = clientId.trim()
        require(normalizedClientId.isNotBlank()) { "optimal_company_client_id_required" }

        val backendContract = VertoSupabase.client.postgrest
            .rpc(function = "optimal_backend_contract")
            .decodeList<OptimalBackendContractRemoteDto>()
            .singleOrNull()
            ?: error("optimal_backend_contract_invalid_row_count")
        check(backendContract.vertoRegistrationReady) { "optimal_registration_backend_not_ready" }

        val remote = VertoSupabase.client.postgrest
            .rpc(
                function = "verto_issue_optimal_registration_code",
                parameters = IssueOptimalCompanyJoinCodeRequest(clientId = normalizedClientId),
            )
            .decodeList<OptimalCompanyJoinCodeRemoteDto>()
            .singleOrNull()
            ?: error("optimal_company_join_code_invalid_row_count")
        return OptimalRegistrationRemoteCode(
            organizationId = remote.organizationId,
            clientId = remote.clientId,
            companyName = remote.companyName,
            code = remote.code,
            expiresAt = remote.expiresAt,
        )
    }
}
