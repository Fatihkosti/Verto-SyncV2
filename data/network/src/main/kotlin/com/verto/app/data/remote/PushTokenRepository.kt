package com.verto.app.data.remote

import android.content.Context
import com.verto.app.notifications.FcmTokenStore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRepository @Inject constructor(
    private val appContext: Context
) {

    private val client by lazy { VertoSupabase.client }

    suspend fun upsertCurrentUserToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = FcmTokenStore.deviceId(appContext)
            require(client.auth.currentUserOrNull() != null) { "لا يوجد مستخدم مسجّل" }
            require(token.isNotBlank()) { "توكن الإشعار مطلوب" }
            require(deviceId.isNotBlank()) { "معرف الجهاز مطلوب" }
            val request = RegisterPushTokenRequest(
                token = token,
                deviceId = deviceId,
                platform = "android",
            )

            withRetry {
                client.postgrest.rpc("register_push_token_v2", request)
            }
            Unit
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun deleteCurrentUserToken(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = FcmTokenStore.deviceId(appContext)
            if (client.auth.currentUserOrNull() == null) return@runCatching
            client.postgrest.rpc("revoke_push_token_v2", RevokePushTokenRequest(deviceId))
            Unit
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun <T> withRetry(maxAttempts: Int = 3, block: suspend () -> T): T {
        var lastError: Throwable? = null
        var delayMs = 500L
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
                if (attempt < maxAttempts - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        throw lastError ?: IllegalStateException("upsert push_token failed")
    }
}

@Serializable
private data class RegisterPushTokenRequest(
    @SerialName("p_token") val token: String,
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_platform") val platform: String,
)

@Serializable
private data class RevokePushTokenRequest(
    @SerialName("p_device_id") val deviceId: String,
)
