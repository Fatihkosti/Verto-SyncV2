package com.verto.app.feature.auth.domain.repository

enum class SessionAuthorizationStatus {
    AUTHORIZED,
    PROFILE_INACTIVE,
    PROFILE_MISSING,
    ACCOUNT_BLOCKED,
    MEMBERSHIP_INVALID,
    PROVISIONING_INCOMPLETE,
}

interface SplashSessionGateway {
    suspend fun awaitInitialization(timeoutMillis: Long = 3_000L)
    fun hasSession(): Boolean
    suspend fun hasPendingPasswordRecovery(): Boolean = false
    suspend fun fetchSessionAuthorizationStatus(): Result<SessionAuthorizationStatus>
    fun hasTrustedOfflineSession(): Boolean = false
    suspend fun refreshAccess()
    suspend fun wipeAndSignOut()
}
