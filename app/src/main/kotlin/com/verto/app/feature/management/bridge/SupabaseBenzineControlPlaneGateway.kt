package com.verto.app.feature.management.bridge

import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.feature.management.domain.model.BenzineClientError
import com.verto.app.feature.management.domain.model.BenzineJoinCodeCandidate
import com.verto.app.feature.management.domain.model.BenzineUserHealth
import com.verto.app.feature.management.domain.repository.BenzineControlPlaneGateway
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseBenzineControlPlaneGateway @Inject constructor(
    private val authRepository: AuthRepository,
) : BenzineControlPlaneGateway {

    private val remote get() = VertoSupabase.client.postgrest

    override suspend fun loadJoinCodeCandidates(): Result<List<BenzineJoinCodeCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                remote.rpc("verto_autodrive_join_code_candidates_v2")
                    .decodeList<JoinCodeCandidateWire>()
                    .map(JoinCodeCandidateWire::toDomain)
            }
        }

    override suspend fun issueJoinCode(clientId: String, accountType: String): Result<String> =
        authRepository.issueAutodriveJoinCode(clientId, accountType)

    override suspend fun loadUserHealth(): Result<List<BenzineUserHealth>> =
        withContext(Dispatchers.IO) {
            runCatching {
                remote.rpc("verto_autodrive_health_v1")
                    .decodeList<UserHealthWire>()
                    .map(UserHealthWire::toDomain)
            }
        }

    override suspend fun loadRecentErrors(limit: Int): Result<List<BenzineClientError>> =
        withContext(Dispatchers.IO) {
            runCatching {
                remote.rpc(
                    "verto_autodrive_errors_v1",
                    ErrorsRequest(limit = limit),
                ).decodeList<ClientErrorWire>().map(ClientErrorWire::toDomain)
            }
        }
}

@Serializable
private data class ErrorsRequest(
    @SerialName("p_limit") val limit: Int,
    @SerialName("p_before") val before: String? = null,
    @SerialName("p_severity") val severity: String? = null,
)

@Serializable
private data class JoinCodeCandidateWire(
    @SerialName("client_id") val clientId: String,
    val name: String,
    val phone: String = "",
    @SerialName("account_type") val accountType: String,
) {
    fun toDomain() = BenzineJoinCodeCandidate(
        clientId = clientId,
        name = name,
        phone = phone,
        accountType = accountType,
    )
}

@Serializable
private data class UserHealthWire(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("app_version") val appVersion: String = "",
    val platform: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_successful_sync_at") val lastSuccessfulSyncAt: String? = null,
    @SerialName("server_revision") val serverRevision: Long? = null,
    @SerialName("pending_commands") val pendingCommands: Int = 0,
    @SerialName("failed_commands") val failedCommands: Int = 0,
    @SerialName("push_status") val pushStatus: String = "UNKNOWN",
    @SerialName("health_status") val healthStatus: String,
) {
    fun toDomain() = BenzineUserHealth(
        userId = userId,
        clientId = clientId,
        clientName = clientName,
        accountType = accountType,
        appVersion = appVersion,
        platform = platform,
        lastSeenAt = lastSeenAt,
        lastSuccessfulSyncAt = lastSuccessfulSyncAt,
        serverRevision = serverRevision,
        pendingCommands = pendingCommands,
        failedCommands = failedCommands,
        pushStatus = pushStatus,
        healthStatus = healthStatus,
    )
}

@Serializable
private data class ClientErrorWire(
    @SerialName("error_id") val errorId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("user_id") val userId: String,
    @SerialName("error_code") val errorCode: String,
    val severity: String,
    val category: String,
    val operation: String,
    @SerialName("safe_message") val safeMessage: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("retry_count") val retryCount: Int,
    val fingerprint: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("received_at") val receivedAt: String,
) {
    fun toDomain() = BenzineClientError(
        errorId = errorId,
        clientId = clientId,
        clientName = clientName,
        userId = userId,
        errorCode = errorCode,
        severity = severity,
        category = category,
        operation = operation,
        safeMessage = safeMessage,
        appVersion = appVersion,
        retryCount = retryCount,
        fingerprint = fingerprint,
        occurredAt = occurredAt,
        receivedAt = receivedAt,
    )
}
