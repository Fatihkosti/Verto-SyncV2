package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationCode
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationResult
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GuardedOptimalRegistrationRepository @Inject constructor(
    private val guard: OptimalOperationGuard,
    private val remoteSource: OptimalRegistrationRemoteSource,
    private val sessionReader: SessionReader,
    private val companyRepository: OptimalCompanyRepository,
    private val clock: OptimalClock,
) : OptimalRegistrationRepository {
    private val issueMutex = Mutex()

    override suspend fun issueCompanyJoinCode(clientId: String): OptimalRegistrationResult {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return OptimalRegistrationResult.CompanyUnavailable

        when (
            guard.check(
                operation = OptimalOperation.REQUEST_COMPANY_JOIN_CODE,
                layer = OptimalGuardLayer.REPOSITORY,
                details = "clientId=$normalizedClientId,scope=company_join",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return OptimalRegistrationResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> return OptimalRegistrationResult.BackendContractBlocked
            OptimalAccessDecision.Granted -> Unit
        }

        return issueMutex.withLock { issueLocked(normalizedClientId) }
    }

    private suspend fun issueLocked(clientId: String): OptimalRegistrationResult {
        val organizationId = sessionReader.organizationId.first().trim()
        if (organizationId.isBlank()) return OptimalRegistrationResult.SessionUnavailable

        val company = companyRepository.observeCompanies(
            organizationId = organizationId,
            searchTerm = "",
            linkFilter = OptimalCompanyLinkFilter.ALL,
        ).first().firstOrNull { it.clientId == clientId }
            ?: return OptimalRegistrationResult.CompanyUnavailable

        if (company.organizationId != organizationId ||
            company.customerSegment != "COMPANY" ||
            company.name.isBlank()
        ) {
            return OptimalRegistrationResult.CompanyUnavailable
        }
        if (company.linkStatus == OptimalCompanyLinkStatus.LINKED) {
            return OptimalRegistrationResult.CompanyAlreadyLinked
        }

        when (
            guard.check(
                operation = OptimalOperation.ISSUE_COMPANY_JOIN_CODE,
                layer = OptimalGuardLayer.REPOSITORY,
                details = "organizationId=$organizationId,clientId=$clientId,scope=company_join",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return OptimalRegistrationResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> return OptimalRegistrationResult.BackendContractBlocked
            OptimalAccessDecision.Granted -> Unit
        }

        return try {
            val remote = remoteSource.issueCompanyJoinCode(clientId)
            if (
                remote.organizationId != organizationId ||
                remote.clientId != clientId ||
                remote.companyName.trim() != company.name.trim()
            ) {
                return OptimalRegistrationResult.RemoteResponseRejected
            }
            val expiresAtMillis = runCatching { Instant.parse(remote.expiresAt).toEpochMilli() }
                .getOrNull()
                ?: return OptimalRegistrationResult.RemoteResponseRejected
            if (!remote.code.matches(Regex("^[0-9]{8}$")) || expiresAtMillis <= clock.nowMillis()) {
                return OptimalRegistrationResult.RemoteResponseRejected
            }

            OptimalRegistrationResult.Success(
                OptimalRegistrationCode(
                    organizationId = remote.organizationId,
                    clientId = remote.clientId,
                    companyName = remote.companyName,
                    code = remote.code,
                    expiresAt = remote.expiresAt,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toOptimalRegistrationResult()
        }
    }
}

internal fun Throwable.toOptimalRegistrationResult(): OptimalRegistrationResult {
    val normalizedMessage = message.orEmpty().lowercase()
    return when {
        "optimal_registration_backend_not_ready" in normalizedMessage ||
            "optimal_backend_contract" in normalizedMessage ||
            ("verto_issue_optimal_registration_code" in normalizedMessage &&
                ("pgrst" in normalizedMessage || "not find" in normalizedMessage)) -> {
            OptimalRegistrationResult.BackendContractBlocked
        }
        "auth_session_required" in normalizedMessage -> OptimalRegistrationResult.SessionUnavailable
        "optimal_company_client_id_required" in normalizedMessage ||
            "company_client_required" in normalizedMessage ||
            "client_not_company" in normalizedMessage ||
            "client_not_found" in normalizedMessage -> OptimalRegistrationResult.CompanyUnavailable
        "company_already_linked" in normalizedMessage -> OptimalRegistrationResult.CompanyAlreadyLinked
        "clients_edit_required" in normalizedMessage ||
            "permission" in normalizedMessage ||
            "unauthorized" in normalizedMessage ||
            "forbidden" in normalizedMessage -> OptimalRegistrationResult.PermissionDenied
        "supabase" in normalizedMessage && "config" in normalizedMessage -> {
            OptimalRegistrationResult.BackendNotConfigured
        }
        else -> OptimalRegistrationResult.NetworkError
    }
}
