package com.verto.app.data.remote

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.core.error.RemoteFailureBoundary
import com.verto.app.core.error.RemoteFailureMetadataExtractor
import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.remote.dto.AppUserDto
import com.verto.app.data.remote.dto.CreateOrgRequest
import com.verto.app.utils.PreferencesManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal class AuthAccountRemoteSource(
    private val prefs: PreferencesManager,
    private val pushTokens: PushTokenRepository,
    private val sessionWriter: SessionWriter
) {
    private val client by lazy { VertoSupabase.client }

suspend fun signIn(email: String, password: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        val result = runCatching {
            signInInternal(email, password)
            prefs.clearPasswordRecoveryState()
            Unit
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result
    }

/** Legacy combined entrypoint retained for data-layer callers; UI gateway uses staged sign-in. */
suspend fun login(email: String, password: String): Result<AppUserDto> =
    withContext(Dispatchers.IO) {
        val result = runCatching {
            signInInternal(email, password)
            prefs.clearPasswordRecoveryState()
            fetchActiveProfile().getOrThrow()
                ?: throw BusinessRuleFailureException(AuthErrorCodes.PROFILE_MISSING)
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result
    }

private suspend fun signInInternal(email: String, password: String) {
    try {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    } catch (cause: Throwable) {
        throw RemoteFailureBoundary.wrap(cause)
    }
}

// ── إنشاء مؤسسة جديدة ────────────────────────────────────
suspend fun registerWithOrg(
    email: String,
    password: String,
    ownerName: String,
    orgName: String,
    ownerPhone: String = "",
    orgAddress: String = ""
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val uid = client.auth.currentSessionOrNull()?.user?.id
            ?: client.auth.currentUserOrNull()?.id
            ?: throw BusinessRuleFailureException(AuthErrorCodes.PROVISIONING_INCOMPLETE)

        val orgId = client.postgrest.rpc(
            "create_organization_with_admin",
            CreateOrgRequest(orgName = orgName, adminName = ownerName, adminUid = uid)
        ).data

        prefs.setShopName(orgName)
        prefs.setOwnerName(ownerName)
        sessionWriter.setUserId(uid)
        sessionWriter.setUserName(ownerName)
        if (ownerPhone.isNotBlank()) sessionWriter.setUserPhone(ownerPhone)
        if (orgAddress.isNotBlank()) prefs.setOrgAddress(orgAddress)

        orgId
    }
}

suspend fun changePassword(
    currentPassword: String,
    newPassword: String
): Result<Unit> = withContext(Dispatchers.IO) {
    val result = runCatching {
        val currentUser = client.auth.currentUserOrNull()
            ?: throw ClassifiedFailureException(
                AppFailure.Unauthorized(diagnosticCode = "AUTH_SESSION_REQUIRED")
            )
        val email = currentUser.email
            ?: throw BusinessRuleFailureException(AuthErrorCodes.PROFILE_MISSING, target = "email")

        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = currentPassword
            }
        } catch (cause: Throwable) {
            throw reauthenticationFailure(cause)
        }

        try {
            client.auth.updateUser { password = newPassword }
        } catch (cause: Throwable) {
            throw RemoteFailureBoundary.wrap(cause)
        }
        Unit
    }
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    result
}

private fun reauthenticationFailure(cause: Throwable): Throwable {
    if (cause is CancellationException) return cause
    val code = RemoteFailureMetadataExtractor.extract(cause)?.code
        ?.substringAfterLast('.')
        ?.replace('-', '_')
        ?.trim()
        ?.lowercase()
    return if (code in INVALID_CREDENTIAL_CODES) {
        BusinessRuleFailureException(
            code = AuthErrorCodes.INVALID_CREDENTIALS,
            target = "currentPassword",
            cause = cause,
        )
    } else {
        RemoteFailureBoundary.wrap(cause)
    }
}

suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        prefs.clearPasswordRecoveryState()
        // OTP-only recovery: no redirect/deep-link is registered or requested by the app.
        client.auth.resetPasswordForEmail(email = email.trim().lowercase())
        Unit
    }
}

/**
 * يتحقق من Recovery OTP لدى Supabase Auth. نجاح التحقق ينشئ جلسة recovery صالحة
 * لدى المزود؛ العلامة المحلية أدناه قيد إضافي فقط وليست مصدر الثقة.
 */
suspend fun verifyPasswordResetOtp(email: String, otp: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val normalizedEmail = email.trim().lowercase()
            client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = normalizedEmail,
                token = otp,
            )
            if (client.auth.currentSessionOrNull() == null) {
                throw BusinessRuleFailureException(AuthErrorCodes.RECOVERY_SESSION_REQUIRED)
            }
            prefs.markPasswordRecoveryVerified(normalizedEmail)
            Unit
        }
    }

/** تغيير كلمة المرور مسموح فقط بعد Recovery OTP موثّق من Supabase لنفس البريد. */
suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        val pending = prefs.isPasswordRecoveryPending()
        val expectedEmail = prefs.passwordRecoveryEmail()
        val currentEmail = client.auth.currentUserOrNull()?.email?.trim()?.lowercase().orEmpty()
        if (!pending || expectedEmail.isBlank() || currentEmail != expectedEmail) {
            return@withContext Result.failure(
                BusinessRuleFailureException(AuthErrorCodes.RECOVERY_SESSION_REQUIRED)
            )
        }
        runCatching {
            client.auth.updateUser { password = newPassword }
            // Password change is authoritative once Supabase accepts it; local cleanup is best-effort.
            runCatching { prefs.clearPasswordRecoveryState() }
            // تنتهي جلسة recovery بعد التغيير؛ تسجيل الدخول التالي يكون بكلمة المرور الجديدة فقط.
            runCatching { client.auth.signOut() }
            Unit
        }
    }

suspend fun isPasswordRecoveryPending(): Boolean = withContext(Dispatchers.IO) {
    prefs.isPasswordRecoveryPending()
}

suspend fun clearPasswordRecoveryState() = withContext(Dispatchers.IO) {
    prefs.clearPasswordRecoveryState()
}

// ── جلب ملف المستخدم الحالي ───────────────────────────────
suspend fun getMyProfile(): AppUserDto? = withContext(Dispatchers.IO) {
    val uid = client.auth.currentUserOrNull()?.id ?: return@withContext null
    // خزّن معرّف المستخدم محلياً لاستخدامه offline (نسبة الفواتير/العملاء للموظف).
    runCatching { sessionWriter.setUserId(uid) }
    val profile = runCatching {
        client.postgrest["app_users"]
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<AppUserDto>()
            ?.also { TenantIsolationPolicy.requireTenantId(it.organizationId) }
    }.getOrNull()
    if (profile != null && profile.name.isNotBlank()) {
        runCatching { sessionWriter.setUserName(profile.name.trim()) }
    }
    profile
}

suspend fun updateMyName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "الاسم مطلوب" }
        val profile = getMyProfile() ?: error("المستخدم غير مسجل")
        TenantIsolationPolicy.requireAdmin(profile.role)
        val organizationId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
        client.postgrest["app_users"].update(AppUserNameUpdate(normalized)) {
            filter {
                eq("id", profile.id)
                eq("organization_id", organizationId)
            }
        }
        sessionWriter.setUserName(normalized)
        Unit
    }
}

/**
 * جلب الملف الشخصي مع **تمييز** الحالات (الجلسة 7):
 *  - `success(dto)` نجح الاستعلام؛ `dto=null` يعني **لا صف للمستخدم** (مُزال من المؤسسة).
 *  - `success(null)` أيضاً عند غياب جلسة.
 *  - `failure` يعني **تعذّر التحقق** (شبكة/خطأ) — لا يُساوي «مُزال».
 * يفرّق عن [getMyProfile] الذي يبتلع كلا الحالتين في `null`.
 */
suspend fun fetchActiveProfile(): Result<AppUserDto?> = withContext(Dispatchers.IO) {
    val uid = client.auth.currentUserOrNull()?.id
        ?: return@withContext Result.success(null)
    runCatching { sessionWriter.setUserId(uid) }
    runCatching {
        client.postgrest["app_users"]
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<AppUserDto>()
            ?.also { TenantIsolationPolicy.requireTenantId(it.organizationId) }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(RemoteFailureBoundary.wrap(it)) },
    )
}

// ── تسجيل الخروج ──────────────────────────────────────────
suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        // Local recovery residue must never block remote sign-out.
        runCatching { prefs.clearPasswordRecoveryState() }
        // احذف توكن FCM قبل signOut حتى لا تمنع الـ RLS العملية
        runCatching { pushTokens.deleteCurrentUserToken() }
        client.auth.signOut()
        Unit
    }
}

/** يُستدعى بعد كل login/registration ناجح وعند بدء التطبيق وهو مسجَّل دخول. */
suspend fun syncPushToken(token: String): Result<Unit> = pushTokens.upsertCurrentUserToken(token)

// ── هل المستخدم مسجل دخول؟ ────────────────────────────────
fun isLoggedIn(): Boolean =
    client.auth.currentUserOrNull() != null

private companion object {
    val INVALID_CREDENTIAL_CODES = setOf("invalid_credentials", "invalid_login_credentials")
}
}

@Serializable
private data class AppUserNameUpdate(val name: String)
