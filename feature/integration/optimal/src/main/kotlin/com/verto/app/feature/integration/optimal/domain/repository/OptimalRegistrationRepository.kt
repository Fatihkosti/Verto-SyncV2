package com.verto.app.feature.integration.optimal.domain.repository

data class OptimalRegistrationCode(
    val organizationId: String,
    val clientId: String,
    val companyName: String,
    val code: String,
    val expiresAt: String,
)

sealed interface OptimalRegistrationResult {
    data class Success(val code: OptimalRegistrationCode) : OptimalRegistrationResult
    data object PermissionDenied : OptimalRegistrationResult
    data object BackendContractBlocked : OptimalRegistrationResult
    data object SessionUnavailable : OptimalRegistrationResult
    data object CompanyUnavailable : OptimalRegistrationResult
    data object CompanyAlreadyLinked : OptimalRegistrationResult
    data object RemoteResponseRejected : OptimalRegistrationResult
    data object BackendNotConfigured : OptimalRegistrationResult
    data object NetworkError : OptimalRegistrationResult
}

interface OptimalRegistrationRepository {
    /** Issues a one-time company join code for the selected Verto COMPANY client. */
    suspend fun issueCompanyJoinCode(clientId: String): OptimalRegistrationResult
}

fun interface OptimalClock {
    fun nowMillis(): Long
}
