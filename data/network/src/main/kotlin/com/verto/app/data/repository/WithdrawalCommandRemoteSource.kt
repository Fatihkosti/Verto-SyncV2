package com.verto.app.data.repository

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.AppUserDto
import com.verto.app.data.remote.dto.WithdrawalRequestDto
import com.verto.app.data.remote.dto.WithdrawalStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WithdrawalCommandRemoteSource(
    private val authRepository: AuthRepository
) {
    private val client by lazy { VertoSupabase.client }

suspend fun getWithdrawalRequests(): Result<List<WithdrawalRequestDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = requireActiveProfile()
            client.postgrest["withdrawal_requests"]
                .select { filter { eq("org_id", profile.organizationId) } }
                .decodeList<WithdrawalRequestDto>()
                .sortedByDescending { it.requestedAt }
        }
    }

suspend fun getPendingWithdrawalRequestsCount(): Result<Int> =
    getWithdrawalRequests().map { requests ->
        requests.count { WithdrawalStatus.fromWire(it.status) == WithdrawalStatus.PENDING }
    }

private suspend fun requireActiveProfile(): AppUserDto =
    authRepository.fetchActiveProfile().getOrThrow()
        ?: throw BusinessRuleFailureException(AuthErrorCodes.PROFILE_MISSING, target = "profile")

private suspend fun getWithdrawalRequestFromServer(id: String): WithdrawalRequestDto? {
    val profile = requireActiveProfile()
    return client.postgrest["withdrawal_requests"]
        .select {
            filter {
                eq("id", id)
                eq("org_id", profile.organizationId)
            }
        }
        .decodeSingleOrNull<WithdrawalRequestDto>()
}

private fun isTimeout(error: Throwable): Boolean =
    ErrorClassifier.classify(error) is AppFailure.Timeout

/**
 * Runs a withdrawal command only through its server RPC, then reconciles the
 * authoritative row. An RPC timeout is deliberately not treated as a final
 * result: the row is read again before returning.
 */
private suspend fun executeWithdrawalCommand(
    command: String,
    id: String,
    successfulStatuses: Set<WithdrawalStatus>,
    rpc: suspend (WithdrawalRequestDto) -> Unit
): Result<Unit> {
    val before = runCatching { getWithdrawalRequestFromServer(id) }.getOrElse {
        return Result.failure(financialCommandFailure(command, it))
    } ?: return Result.failure(
        financialCommandFailure(
            command,
            BusinessRuleFailureException(
                code = WithdrawalFailureCodes.NOT_FOUND,
                target = "withdrawalId",
            ),
        )
    )
    val beforeStatus = WithdrawalStatus.fromWire(before.status)
    if (beforeStatus in successfulStatuses) return Result.success(Unit)
    if (beforeStatus == WithdrawalStatus.REJECTED) {
        return Result.failure(
            financialCommandFailure(
                command,
                BusinessRuleFailureException(
                    code = WithdrawalFailureCodes.REJECTED,
                    target = "status",
                ),
            )
        )
    }

    val invocationFailure = runCatching { rpc(before) }.exceptionOrNull()
    val after = runCatching { getWithdrawalRequestFromServer(id) }.getOrElse {
        val cause = invocationFailure ?: it
        return Result.failure(
            if (invocationFailure != null && isTimeout(invocationFailure)) {
                FinancialCommandException(command, cause)
            } else financialCommandFailure(command, cause)
        )
    } ?: return Result.failure(
        financialCommandFailure(
            command,
            BusinessRuleFailureException(
                code = WithdrawalFailureCodes.NOT_FOUND_AFTER_COMMAND,
                target = "withdrawalId",
            ),
        )
    )
    val afterStatus = WithdrawalStatus.fromWire(after.status)
    if (afterStatus in successfulStatuses) return Result.success(Unit)

    if (invocationFailure != null && isTimeout(invocationFailure)) {
        return Result.failure(FinancialCommandException(command, invocationFailure))
    }
    val cause = invocationFailure ?: BusinessRuleFailureException(
        code = WithdrawalFailureCodes.STATUS_NOT_CONFIRMED,
        target = "status",
    )
    return Result.failure(financialCommandFailure(command, cause))
}

suspend fun approveRequest(id: String, transactionRef: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        executeWithdrawalCommand(
            command = "approve_withdrawal",
            id = id,
            successfulStatuses = setOf(
                WithdrawalStatus.APPROVED,
                WithdrawalStatus.COMPLETED
            )
        ) { current ->
            client.postgrest.rpc(
                "approve_withdrawal",
                ApproveWithdrawalParams(
                    withdrawalId = id,
                    transactionRef = transactionRef,
                    clientRequestId = current.clientRequestId
                )
            )
        }
    }

suspend fun rejectRequest(id: String, adminNote: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val uid = client.auth.currentUserOrNull()?.id
                ?: throw ClassifiedFailureException(
                    AppFailure.Unauthorized(diagnosticCode = "AUTH_SESSION_REQUIRED")
                )
            val updated = client.postgrest["withdrawal_requests"]
                .update(WithdrawalRejectUpdate(
                    status      = WithdrawalStatus.REJECTED.wireValue,
                    adminNote   = adminNote,
                    processedAt = nowIso(),
                    processedBy = uid
                )) {
                    filter {
                        eq("id", id)
                        eq("status", WithdrawalStatus.PENDING.wireValue)
                    }
                    select()
                }
                .decodeList<WithdrawalRequestDto>()
            if (updated.isEmpty()) {
                throw BusinessRuleFailureException(
                    code = WithdrawalFailureCodes.ALREADY_PROCESSED,
                    target = "status",
                )
            }
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(financialCommandFailure("reject_withdrawal", it)) },
        )
    }

suspend fun completeRequest(id: String, adminNote: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        executeWithdrawalCommand(
            command = "complete_withdrawal",
            id = id,
            successfulStatuses = setOf(WithdrawalStatus.COMPLETED)
        ) {
            client.postgrest.rpc(
                "complete_withdrawal",
                CompleteWithdrawalParams(withdrawalId = id, adminNote = adminNote)
            )
        }
    }

private object WithdrawalFailureCodes {
    const val NOT_FOUND = "WITHDRAWAL_NOT_FOUND"
    const val REJECTED = "WITHDRAWAL_REJECTED"
    const val NOT_FOUND_AFTER_COMMAND = "WITHDRAWAL_NOT_FOUND_AFTER_COMMAND"
    const val STATUS_NOT_CONFIRMED = "WITHDRAWAL_STATUS_NOT_CONFIRMED"
    const val ALREADY_PROCESSED = "WITHDRAWAL_ALREADY_PROCESSED"
}
}
