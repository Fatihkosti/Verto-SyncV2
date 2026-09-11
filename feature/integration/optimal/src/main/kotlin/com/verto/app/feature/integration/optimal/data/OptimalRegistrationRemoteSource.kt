package com.verto.app.feature.integration.optimal.data

data class OptimalRegistrationRemoteCode(
    val organizationId: String,
    val clientId: String,
    val companyName: String,
    val code: String,
    val expiresAt: String,
)

interface OptimalRegistrationRemoteSource {
    suspend fun issueCompanyJoinCode(clientId: String): OptimalRegistrationRemoteCode
}
