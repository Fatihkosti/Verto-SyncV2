package com.verto.app.feature.auth.data

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.RemoteFailureMetadataExtractor
import java.util.concurrent.CancellationException

internal enum class AuthOperation {
    LOGIN,
    LOAD_PROFILE,
    REGISTER,
    JOIN_ORGANIZATION,
    INVITE_LOOKUP,
    REQUEST_PASSWORD_RESET,
    VERIFY_PASSWORD_RESET_OTP,
    SET_NEW_PASSWORD,
    SESSION_STATUS,
}

/**
 * Converts provider/data exceptions into Verto's canonical structured failure contract.
 *
 * User-input diagnoses require a stable provider code. HTTP status, exception package/name and
 * Throwable.message are never sufficient evidence that credentials or OTP are wrong.
 */
internal object AuthErrorTranslator {

    fun translate(throwable: Throwable, operation: AuthOperation): Throwable {
        if (throwable is CancellationException) throw throwable
        if (throwable is ClassifiedFailureException || throwable is BusinessRuleFailureException) {
            return throwable
        }

        val structured = RemoteFailureMetadataExtractor.extract(throwable)
        val normalizedCode = structured?.code?.let(::normalizeCode)
        val mappedByCode = normalizedCode?.let { mapStableAuthCode(it, operation, throwable) }
        if (mappedByCode != null) return mappedByCode

        if (structured != null) {
            return ClassifiedFailureException(ErrorClassifier.classifyRemote(structured), throwable)
        }

        val generic = ErrorClassifier.classify(throwable)
        if (generic !is AppFailure.Unknown) {
            return ClassifiedFailureException(generic, throwable)
        }

        return ClassifiedFailureException(
            AppFailure.Unknown(diagnosticCode = "AUTH_${operation.name}_${diagnosticType(throwable)}"),
            throwable,
        )
    }

    private fun mapStableAuthCode(
        code: String,
        operation: AuthOperation,
        cause: Throwable,
    ): Throwable? = when (code) {
        "invalid_credentials", "invalid_login_credentials" ->
            if (operation == AuthOperation.LOGIN) {
                business(AuthErrorCodes.INVALID_CREDENTIALS, "credentials", cause)
            } else {
                null
            }
        "otp_expired", "expired_otp" ->
            if (operation == AuthOperation.VERIFY_PASSWORD_RESET_OTP) {
                business(AuthErrorCodes.EXPIRED_OTP, "otp", cause)
            } else {
                null
            }
        "invalid_otp", "otp_invalid", "verification_failed" ->
            if (operation == AuthOperation.VERIFY_PASSWORD_RESET_OTP) {
                business(AuthErrorCodes.INVALID_OTP, "otp", cause)
            } else {
                null
            }
        "email_not_confirmed" -> business(AuthErrorCodes.EMAIL_NOT_CONFIRMED, "email", cause)
        "user_already_exists", "email_exists", "user_already_registered" ->
            business(AuthErrorCodes.ALREADY_REGISTERED, "email", cause)
        "invite_used" -> business(AuthErrorCodes.INVITE_USED, "invite", cause)
        "invalid_invite", "invite_not_found", "invite_expired" ->
            business(AuthErrorCodes.INVALID_INVITE, "invite", cause)
        "user_banned", "account_blocked", "user_blocked" ->
            business(AuthErrorCodes.ACCOUNT_BLOCKED, cause = cause)
        "over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit" ->
            ClassifiedFailureException(AppFailure.RateLimited(remoteCode = code), cause)
        "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "bad_jwt" ->
            ClassifiedFailureException(AppFailure.Unauthorized(remoteCode = code), cause)
        else -> null
    }

    private fun business(code: String, target: String? = null, cause: Throwable): Throwable =
        BusinessRuleFailureException(code = code, target = target, cause = cause)

    private fun normalizeCode(raw: String): String = raw
        .substringAfterLast('.')
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace('-', '_')
        .replace(' ', '_')
        .lowercase()

    private fun diagnosticType(throwable: Throwable): String = throwable.javaClass.simpleName
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .uppercase()
        .ifBlank { "UNKNOWN" }
}
