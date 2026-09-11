package com.verto.app.data.remote

import com.verto.app.core.security.TenantIsolationPolicy
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class AutoDriveJoinCodeRemoteSource(
    private val accountRemoteSource: AuthAccountRemoteSource
) {
    private val client by lazy { VertoSupabase.client }

suspend fun issueAutodriveJoinCode(
    clientId: String,
    accountType: String,
): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            val organizationId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
            val targetClientId = TenantIsolationPolicy.requireIdentifier(clientId, "clientId")
            val targetAccountType = accountType.trim().uppercase()
            require(targetAccountType == "MARKETER" || targetAccountType == "WORKSHOP_OWNER") {
                "invalid_join_account_type"
            }

            val remote = client.postgrest.rpc(
                "verto_issue_autodrive_join_code",
                IssueAutodriveJoinCodeRequest(
                    organizationId = organizationId,
                    clientId = targetClientId,
                    accountType = targetAccountType,
                    expiresInMinutes = 1_440,
                ),
            ).decodeList<AutodriveJoinCodeRemoteDto>().singleOrNull()
                ?: error("autodrive_join_code_invalid_row_count")

            check(remote.organizationId == organizationId) { "autodrive_join_code_org_mismatch" }
            check(remote.clientId == targetClientId) { "autodrive_join_code_client_mismatch" }
            check(remote.accountType == targetAccountType) { "autodrive_join_code_type_mismatch" }
            remote.code.trim().takeIf { it.isNotBlank() }
                ?: error("autodrive_join_code_empty")
        }
    }
}

@Serializable
private data class IssueAutodriveJoinCodeRequest(
    @SerialName("p_org_id") val organizationId: String,
    @SerialName("p_client_id") val clientId: String,
    @SerialName("p_account_type") val accountType: String,
    @SerialName("p_expires_in_minutes") val expiresInMinutes: Int,
)

@Serializable
private data class AutodriveJoinCodeRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("code") val code: String,
    @SerialName("expires_at") val expiresAt: String,
)
