package com.verto.app.feature.management.domain.model

data class BenzineJoinCodeCandidate(
    val clientId: String,
    val name: String,
    val phone: String,
    val accountType: String,
)

data class BenzineUserHealth(
    val userId: String?,
    val clientId: String,
    val clientName: String,
    val accountType: String,
    val appVersion: String,
    val platform: String,
    val lastSeenAt: String?,
    val lastSuccessfulSyncAt: String?,
    val serverRevision: Long?,
    val pendingCommands: Int,
    val failedCommands: Int,
    val pushStatus: String,
    val healthStatus: String,
)

data class BenzineClientError(
    val errorId: String,
    val clientId: String,
    val clientName: String,
    val userId: String,
    val errorCode: String,
    val severity: String,
    val category: String,
    val operation: String,
    val safeMessage: String,
    val appVersion: String,
    val retryCount: Int,
    val fingerprint: String?,
    val occurredAt: String,
    val receivedAt: String,
)
