package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationResult
import javax.inject.Inject

/** Issues a join code only for an explicitly selected Verto COMPANY client. */
class IssueOptimalCompanyJoinCodeUseCase @Inject constructor(
    private val guard: OptimalOperationGuard,
    private val repository: OptimalRegistrationRepository,
) {
    suspend operator fun invoke(clientId: String): OptimalRegistrationResult {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return OptimalRegistrationResult.CompanyUnavailable

        return when (
            guard.check(
                operation = OptimalOperation.REQUEST_COMPANY_JOIN_CODE,
                layer = OptimalGuardLayer.USE_CASE,
                details = "clientId=$normalizedClientId,scope=company_join",
            )
        ) {
            OptimalAccessDecision.Granted -> repository.issueCompanyJoinCode(normalizedClientId)
            OptimalAccessDecision.PermissionDenied -> OptimalRegistrationResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> OptimalRegistrationResult.BackendContractBlocked
        }
    }
}
