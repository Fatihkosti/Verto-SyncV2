package com.verto.app.data.remote

import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.model.EmployeeWithPermissions
import com.verto.app.data.remote.dto.AppUserDto
import com.verto.app.utils.PreferencesManager

class AuthRepository(
    prefs: PreferencesManager,
    pushTokens: PushTokenRepository,
    sessionWriter: SessionWriter
) {
    private val accountRemoteSource = AuthAccountRemoteSource(prefs, pushTokens, sessionWriter)
    private val organizationAccessRemoteSource = OrganizationAccessRemoteSource(accountRemoteSource)
    private val inviteRemoteSource = InviteRemoteSource(prefs, sessionWriter, accountRemoteSource)
    private val autoDriveJoinCodeRemoteSource = AutoDriveJoinCodeRemoteSource(accountRemoteSource)

    suspend fun signIn(email: String, password: String): Result<Unit> =
        accountRemoteSource.signIn(email, password)

    suspend fun login(email: String, password: String): Result<AppUserDto> =
        accountRemoteSource.login(email, password)

    suspend fun registerWithOrg(
        email: String,
        password: String,
        ownerName: String,
        orgName: String,
        ownerPhone: String = "",
        orgAddress: String = ""
    ): Result<String> = accountRemoteSource.registerWithOrg(
        email, password, ownerName, orgName, ownerPhone, orgAddress
    )

    suspend fun joinOrganization(
        inviteCode: String,
        email: String,
        password: String,
        memberPhone: String = ""
    ): Result<Unit> = inviteRemoteSource.joinOrganization(inviteCode, email, password, memberPhone)

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        accountRemoteSource.changePassword(currentPassword, newPassword)

    suspend fun getOrgEmployees(): Result<List<EmployeeWithPermissions>> =
        organizationAccessRemoteSource.getOrgEmployees()

    suspend fun getEmployeePermissions(userId: String): Result<EmployeePermissions> =
        organizationAccessRemoteSource.getEmployeePermissions(userId)

    suspend fun fetchMyPermissions(): Result<EmployeePermissions> =
        organizationAccessRemoteSource.fetchMyPermissions()

    suspend fun saveEmployeePermissions(permissions: EmployeePermissions): Result<Unit> =
        organizationAccessRemoteSource.saveEmployeePermissions(permissions)

    suspend fun removeEmployee(userId: String): Result<Unit> =
        organizationAccessRemoteSource.removeEmployee(userId)

    suspend fun generateInviteCode(
        employeeName: String,
        jobTitle: String,
        actualJoinDate: String,
        permissions: EmployeePermissions = EmployeePermissions.defaultEmployee()
    ): Result<String> = inviteRemoteSource.generateInviteCode(employeeName, jobTitle, actualJoinDate, permissions)

    suspend fun revokeInviteCode(inviteCode: String): Result<Unit> =
        inviteRemoteSource.revokeInviteCode(inviteCode)

    suspend fun deactivateMember(memberUserId: String, reason: String = ""): Result<Unit> =
        organizationAccessRemoteSource.deactivateMember(memberUserId, reason)

    suspend fun getInviteCodeDetails(inviteCode: String): Result<InviteCodeDetails> =
        inviteRemoteSource.getInviteCodeDetails(inviteCode)

    suspend fun issueAutodriveJoinCode(clientId: String, accountType: String): Result<String> =
        autoDriveJoinCodeRemoteSource.issueAutodriveJoinCode(clientId, accountType)

    suspend fun resetPassword(email: String): Result<Unit> = accountRemoteSource.resetPassword(email)

    suspend fun verifyPasswordResetOtp(email: String, otp: String): Result<Unit> =
        accountRemoteSource.verifyPasswordResetOtp(email, otp)

    suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String): Result<Unit> =
        accountRemoteSource.setNewPasswordAfterVerifiedOtp(newPassword)

    suspend fun isPasswordRecoveryPending(): Boolean =
        accountRemoteSource.isPasswordRecoveryPending()

    suspend fun clearPasswordRecoveryState() =
        accountRemoteSource.clearPasswordRecoveryState()

    suspend fun getMyProfile(): AppUserDto? = accountRemoteSource.getMyProfile()

    suspend fun updateMyName(name: String): Result<Unit> = accountRemoteSource.updateMyName(name)

    suspend fun fetchActiveProfile(): Result<AppUserDto?> = accountRemoteSource.fetchActiveProfile()

    suspend fun logout(): Result<Unit> = accountRemoteSource.logout()

    suspend fun syncPushToken(token: String): Result<Unit> = accountRemoteSource.syncPushToken(token)

    fun isLoggedIn(): Boolean = accountRemoteSource.isLoggedIn()
}
