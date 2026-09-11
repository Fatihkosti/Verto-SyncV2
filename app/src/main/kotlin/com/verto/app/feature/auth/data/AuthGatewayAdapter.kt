package com.verto.app.feature.auth.data

import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.data.remote.AuthRepository
import com.verto.app.feature.auth.domain.model.AuthInviteDetails
import com.verto.app.feature.auth.domain.model.AuthenticatedUser
import com.verto.app.feature.auth.domain.repository.AuthGateway
import javax.inject.Inject

/** Bridges the data layer into the typed authentication contract using the canonical error system. */
class AuthGatewayAdapter @Inject constructor(
    private val repository: AuthRepository
) : AuthGateway {

    override suspend fun login(
        email: String,
        password: String
    ): Result<AuthenticatedUser> {
        val signInResult = repository.signIn(email, password)
            .asAuthResult(AuthOperation.LOGIN)
        signInResult.exceptionOrNull()?.let { return Result.failure(it) }

        val profileResult = repository.fetchActiveProfile()
            .asAuthResult(AuthOperation.LOAD_PROFILE)
        profileResult.exceptionOrNull()?.let { return Result.failure(it) }

        val user = profileResult.getOrNull()
            ?: return Result.failure(BusinessRuleFailureException(AuthErrorCodes.PROFILE_MISSING))
        return Result.success(
            AuthenticatedUser(
                userId = user.id,
                organizationId = user.organizationId,
                name = user.name,
                role = user.role
            )
        )
    }

    override suspend fun registerWithOrganization(
        email: String,
        password: String,
        ownerName: String,
        organizationName: String,
        ownerPhone: String,
        organizationAddress: String
    ): Result<String> = repository.registerWithOrg(
        email = email,
        password = password,
        ownerName = ownerName,
        orgName = organizationName,
        ownerPhone = ownerPhone,
        orgAddress = organizationAddress
    ).asAuthResult(AuthOperation.REGISTER)

    override suspend fun joinOrganization(
        inviteCode: String,
        email: String,
        password: String,
        memberPhone: String
    ): Result<String> {
        val joinResult = repository.joinOrganization(
            inviteCode = inviteCode,
            email = email,
            password = password,
            memberPhone = memberPhone
        ).asAuthResult(AuthOperation.JOIN_ORGANIZATION)
        joinResult.exceptionOrNull()?.let { return Result.failure(it) }

        val profileResult = repository.fetchActiveProfile()
            .asAuthResult(AuthOperation.LOAD_PROFILE)
        profileResult.exceptionOrNull()?.let { return Result.failure(it) }

        val organizationId = profileResult.getOrNull()?.organizationId.orEmpty()
        return if (organizationId.isBlank()) {
            Result.failure(
                BusinessRuleFailureException(AuthErrorCodes.MEMBERSHIP_INVALID)
            )
        } else {
            Result.success(organizationId)
        }
    }

    override suspend fun getInviteDetails(inviteCode: String): Result<AuthInviteDetails> =
        repository.getInviteCodeDetails(inviteCode)
            .map { details ->
                AuthInviteDetails(
                    code = details.code,
                    employeeName = details.employeeName,
                    jobTitle = details.jobTitle,
                    actualJoinDate = details.actualJoinDate
                )
            }
            .asAuthResult(AuthOperation.INVITE_LOOKUP)

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        repository.resetPassword(email).asAuthResult(AuthOperation.REQUEST_PASSWORD_RESET)

    override suspend fun verifyPasswordResetOtp(email: String, otp: String): Result<Unit> =
        repository.verifyPasswordResetOtp(email, otp)
            .asAuthResult(AuthOperation.VERIFY_PASSWORD_RESET_OTP)

    override suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String): Result<Unit> =
        repository.setNewPasswordAfterVerifiedOtp(newPassword)
            .asAuthResult(AuthOperation.SET_NEW_PASSWORD)

    private fun <T> Result<T>.asAuthResult(operation: AuthOperation): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AuthErrorTranslator.translate(it, operation)) },
    )
}
