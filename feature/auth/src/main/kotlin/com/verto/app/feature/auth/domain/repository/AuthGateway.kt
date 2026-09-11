package com.verto.app.feature.auth.domain.repository

import com.verto.app.feature.auth.domain.model.AuthInviteDetails
import com.verto.app.feature.auth.domain.model.AuthenticatedUser

interface AuthGateway {
    suspend fun login(email: String, password: String): Result<AuthenticatedUser>

    suspend fun registerWithOrganization(
        email: String,
        password: String,
        ownerName: String,
        organizationName: String,
        ownerPhone: String = "",
        organizationAddress: String = ""
    ): Result<String>

    suspend fun joinOrganization(
        inviteCode: String,
        email: String,
        password: String,
        memberPhone: String = ""
    ): Result<String>

    suspend fun getInviteDetails(inviteCode: String): Result<AuthInviteDetails>

    suspend fun requestPasswordReset(email: String): Result<Unit>

    suspend fun verifyPasswordResetOtp(email: String, otp: String): Result<Unit>

    suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String): Result<Unit>
}
