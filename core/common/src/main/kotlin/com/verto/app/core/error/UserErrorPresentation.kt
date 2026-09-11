package com.verto.app.core.error

import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Presentation semantics for a classified failure.
 *
 * The UI receives structured semantics, never raw provider prose. [incidentId] is a short,
 * non-sensitive support reference. [outcome] describes what is known about the operation itself.
 */
data class UserErrorPresentation(
    val messageKey: UserErrorMessageKey,
    val surface: ErrorSurface,
    val recoveryAction: ErrorRecoveryAction,
    val diagnosticCode: String,
    val target: String? = null,
    val outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    val incidentId: String = IncidentReference.create(),
)

enum class OperationOutcome {
    NOT_APPLIED,
    APPLIED,
    OUTCOME_UNKNOWN,
    SAVED_LOCALLY_PENDING_SYNC,
}

/** Short opaque reference safe to show and copy. It contains no tenant/user/provider data. */
object IncidentReference {
    fun create(): String = "E-" + UUID.randomUUID().toString()
        .replace("-", "")
        .take(10)
        .uppercase(Locale.US)
}

enum class UserErrorMessageKey {
    NETWORK_UNAVAILABLE,
    CONNECTION_FAILED,
    TIMEOUT,
    SESSION_EXPIRED,
    PERMISSION_DENIED,
    VALIDATION,
    VALIDATION_FIELD,
    BUSINESS_RULE,
    NOT_FOUND,
    CONFLICT,
    CONFLICT_DUPLICATE,
    CONFLICT_RELATED_RECORD,
    CONFLICT_VERSION,
    RATE_LIMITED,
    SERVER_UNAVAILABLE,
    REQUEST_REJECTED,
    LOCAL_STORAGE,
    LOCAL_STORAGE_FULL,
    LOCAL_STORAGE_CORRUPT,
    LOCAL_STORAGE_CONSTRAINT,
    OPERATION_OUTCOME_UNKNOWN,
    FINANCIAL_MUTATIONS_DISABLED,
    BALANCE_UNAVAILABLE,
    PAYMENT_AMOUNT_INVALID,
    PAYMENT_INVOICE_CURRENCY_UNKNOWN,
    PAYMENT_INVOICE_CURRENCY_INCOMPLETE,
    PAYMENT_CURRENCY_UNSUPPORTED,
    PAYMENT_INVOICE_EXCHANGE_RATE_REQUIRED,
    PAYMENT_INVOICE_EXCHANGE_RATE_INVALID,
    PAYMENT_EXCHANGE_RATE_REQUIRED,
    PAYMENT_EXCHANGE_RATE_INVALID,
    PAYMENT_CASH_AMOUNT_RATE_MISMATCH,
    PAYMENT_EXCEEDS_REMAINING,
    PAYMENT_DUPLICATE,
    PAYMENT_INVOICE_VOIDED,
    AUTH_FORM_INCOMPLETE,
    AUTH_VERIFY_INVITE_FIRST,
    AUTH_RECOVERY_EMAIL_REQUIRED,
    AUTH_OTP_FORMAT,
    AUTH_INVALID_OTP,
    AUTH_EXPIRED_OTP,
    AUTH_PASSWORD_TOO_SHORT,
    AUTH_PASSWORD_MISMATCH,
    AUTH_INVALID_CREDENTIALS,
    AUTH_EMAIL_NOT_CONFIRMED,
    AUTH_ALREADY_REGISTERED,
    AUTH_INVITE_USED,
    AUTH_INVALID_INVITE,
    AUTH_ACCOUNT_BLOCKED,
    AUTH_ACCOUNT_UNAVAILABLE,
    AUTH_RECOVERY_SESSION_REQUIRED,
    AUTH_RECOVERY_VERIFICATION_UNAVAILABLE,
    UNEXPECTED,
}

enum class ErrorSurface {
    FIELD,
    INLINE,
    BANNER,
    FULL_SCREEN,
}

enum class ErrorRecoveryAction {
    NONE,
    RETRY,
    LOGIN,
    EDIT,
}

enum class ErrorPresentationContext {
    FIELD,
    FORM,
    TRANSIENT_ACTION,
    SCREEN_LOAD,
}

/** Single policy for turning [AppFailure] into UI semantics. */
object ErrorPresentationPolicy {

    fun from(
        throwable: Throwable,
        context: ErrorPresentationContext,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
        incidentId: String = IncidentReference.create(),
    ): UserErrorPresentation {
        val failure = try {
            ErrorClassifier.classify(throwable)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
        return from(failure, context, outcome, incidentId)
    }

    fun from(
        failure: AppFailure,
        context: ErrorPresentationContext,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
        incidentId: String = IncidentReference.create(),
    ): UserErrorPresentation = UserErrorPresentation(
        messageKey = if (outcome == OperationOutcome.OUTCOME_UNKNOWN) {
            UserErrorMessageKey.OPERATION_OUTCOME_UNKNOWN
        } else {
            messageKeyFor(failure)
        },
        surface = surfaceFor(failure, context),
        recoveryAction = if (outcome == OperationOutcome.OUTCOME_UNKNOWN) {
            ErrorRecoveryAction.NONE
        } else {
            recoveryFor(failure)
        },
        diagnosticCode = failure.diagnosticCode,
        target = targetFor(failure),
        outcome = outcome,
        incidentId = incidentId,
    )

    /** Stable semantic message key shared by Compose and compatibility renderers. */
    fun messageKeyFor(failure: AppFailure): UserErrorMessageKey = when (failure) {
        is AppFailure.NetworkUnavailable -> UserErrorMessageKey.NETWORK_UNAVAILABLE
        is AppFailure.ConnectionFailed -> UserErrorMessageKey.CONNECTION_FAILED
        is AppFailure.Timeout -> UserErrorMessageKey.TIMEOUT
        is AppFailure.Unauthorized -> UserErrorMessageKey.SESSION_EXPIRED
        is AppFailure.PermissionDenied -> UserErrorMessageKey.PERMISSION_DENIED
        is AppFailure.Validation -> if (failure.target.isNullOrBlank()) {
            UserErrorMessageKey.VALIDATION
        } else {
            UserErrorMessageKey.VALIDATION_FIELD
        }
        is AppFailure.BusinessRule -> businessMessageKey(failure.code)
        is AppFailure.NotFound -> UserErrorMessageKey.NOT_FOUND
        is AppFailure.Conflict -> conflictMessageKey(failure.remoteCode)
        is AppFailure.RateLimited -> UserErrorMessageKey.RATE_LIMITED
        is AppFailure.Server -> UserErrorMessageKey.SERVER_UNAVAILABLE
        is AppFailure.RemoteRejected -> UserErrorMessageKey.REQUEST_REJECTED
        is AppFailure.LocalStorage -> when (failure.kind) {
            LocalStorageKind.FULL -> UserErrorMessageKey.LOCAL_STORAGE_FULL
            LocalStorageKind.CORRUPT -> UserErrorMessageKey.LOCAL_STORAGE_CORRUPT
            LocalStorageKind.CONSTRAINT -> UserErrorMessageKey.LOCAL_STORAGE_CONSTRAINT
            else -> UserErrorMessageKey.LOCAL_STORAGE
        }
        is AppFailure.Unknown -> UserErrorMessageKey.UNEXPECTED
    }

    private fun businessMessageKey(code: String): UserErrorMessageKey = when (code) {
        "FINANCIAL_MUTATIONS_DISABLED" -> UserErrorMessageKey.FINANCIAL_MUTATIONS_DISABLED
        "MARKETER_BALANCE_UNAVAILABLE" -> UserErrorMessageKey.BALANCE_UNAVAILABLE
        "PAYMENT_AMOUNT_INVALID" -> UserErrorMessageKey.PAYMENT_AMOUNT_INVALID
        "PAYMENT_INVOICE_CURRENCY_UNKNOWN" -> UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_UNKNOWN
        "PAYMENT_INVOICE_CURRENCY_INCOMPLETE" -> UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_INCOMPLETE
        "PAYMENT_CURRENCY_UNSUPPORTED" -> UserErrorMessageKey.PAYMENT_CURRENCY_UNSUPPORTED
        "PAYMENT_INVOICE_EXCHANGE_RATE_REQUIRED" -> UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_REQUIRED
        "PAYMENT_INVOICE_EXCHANGE_RATE_INVALID" -> UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_INVALID
        "PAYMENT_EXCHANGE_RATE_REQUIRED" -> UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_REQUIRED
        "PAYMENT_EXCHANGE_RATE_INVALID" -> UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_INVALID
        "PAYMENT_CASH_AMOUNT_RATE_MISMATCH" -> UserErrorMessageKey.PAYMENT_CASH_AMOUNT_RATE_MISMATCH
        "PAYMENT_EXCEEDS_REMAINING" -> UserErrorMessageKey.PAYMENT_EXCEEDS_REMAINING
        "PAYMENT_DUPLICATE" -> UserErrorMessageKey.PAYMENT_DUPLICATE
        "PAYMENT_INVOICE_VOIDED" -> UserErrorMessageKey.PAYMENT_INVOICE_VOIDED
        else -> authMessageKey(code) ?: UserErrorMessageKey.BUSINESS_RULE
    }

    private fun conflictMessageKey(remoteCode: String?): UserErrorMessageKey =
        when (normalizeRemoteCode(remoteCode)) {
            "23505", "unique_violation", "duplicate" -> UserErrorMessageKey.CONFLICT_DUPLICATE
            "23503", "foreign_key_violation", "related_record" -> UserErrorMessageKey.CONFLICT_RELATED_RECORD
            "version_conflict", "stale_version" -> UserErrorMessageKey.CONFLICT_VERSION
            else -> UserErrorMessageKey.CONFLICT
        }

    private fun normalizeRemoteCode(code: String?): String = code.orEmpty()
        .substringAfterLast('.')
        .replace('-', '_')
        .trim()
        .lowercase()

    private fun authMessageKey(code: String): UserErrorMessageKey? = when (code) {
        AuthErrorCodes.FORM_INCOMPLETE -> UserErrorMessageKey.AUTH_FORM_INCOMPLETE
        AuthErrorCodes.VERIFY_INVITE_FIRST -> UserErrorMessageKey.AUTH_VERIFY_INVITE_FIRST
        AuthErrorCodes.RECOVERY_EMAIL_REQUIRED -> UserErrorMessageKey.AUTH_RECOVERY_EMAIL_REQUIRED
        AuthErrorCodes.OTP_FORMAT -> UserErrorMessageKey.AUTH_OTP_FORMAT
        AuthErrorCodes.INVALID_OTP -> UserErrorMessageKey.AUTH_INVALID_OTP
        AuthErrorCodes.EXPIRED_OTP -> UserErrorMessageKey.AUTH_EXPIRED_OTP
        AuthErrorCodes.PASSWORD_TOO_SHORT -> UserErrorMessageKey.AUTH_PASSWORD_TOO_SHORT
        AuthErrorCodes.PASSWORD_MISMATCH -> UserErrorMessageKey.AUTH_PASSWORD_MISMATCH
        AuthErrorCodes.INVALID_CREDENTIALS -> UserErrorMessageKey.AUTH_INVALID_CREDENTIALS
        AuthErrorCodes.EMAIL_NOT_CONFIRMED -> UserErrorMessageKey.AUTH_EMAIL_NOT_CONFIRMED
        AuthErrorCodes.ALREADY_REGISTERED -> UserErrorMessageKey.AUTH_ALREADY_REGISTERED
        AuthErrorCodes.INVITE_USED -> UserErrorMessageKey.AUTH_INVITE_USED
        AuthErrorCodes.INVALID_INVITE -> UserErrorMessageKey.AUTH_INVALID_INVITE
        AuthErrorCodes.ACCOUNT_BLOCKED -> UserErrorMessageKey.AUTH_ACCOUNT_BLOCKED
        AuthErrorCodes.PROFILE_MISSING,
        AuthErrorCodes.MEMBERSHIP_INVALID,
        AuthErrorCodes.PROVISIONING_INCOMPLETE -> UserErrorMessageKey.AUTH_ACCOUNT_UNAVAILABLE
        AuthErrorCodes.RECOVERY_SESSION_REQUIRED -> UserErrorMessageKey.AUTH_RECOVERY_SESSION_REQUIRED
        AuthErrorCodes.RECOVERY_VERIFICATION_UNAVAILABLE -> UserErrorMessageKey.AUTH_RECOVERY_VERIFICATION_UNAVAILABLE
        else -> null
    }

    private fun surfaceFor(
        failure: AppFailure,
        context: ErrorPresentationContext,
    ): ErrorSurface {
        val target = targetFor(failure)
        val correctable = failure is AppFailure.Validation || failure is AppFailure.BusinessRule
        val targetedCorrectable = correctable && !target.isNullOrBlank()

        if (context == ErrorPresentationContext.FIELD) {
            return if (targetedCorrectable) ErrorSurface.FIELD else ErrorSurface.BANNER
        }
        if (correctable && context == ErrorPresentationContext.FORM) {
            return if (target.isNullOrBlank()) ErrorSurface.INLINE else ErrorSurface.FIELD
        }
        if (context == ErrorPresentationContext.SCREEN_LOAD) return ErrorSurface.FULL_SCREEN
        return ErrorSurface.BANNER
    }

    private fun recoveryFor(failure: AppFailure): ErrorRecoveryAction = when (failure.retryAdvice) {
        RetryAdvice.USER_RETRY -> ErrorRecoveryAction.RETRY
        RetryAdvice.AFTER_REAUTH -> ErrorRecoveryAction.LOGIN
        RetryAdvice.AFTER_CORRECTION -> ErrorRecoveryAction.EDIT
        RetryAdvice.AUTOMATIC_BACKOFF,
        RetryAdvice.NEVER,
        -> ErrorRecoveryAction.NONE
    }

    private fun targetFor(failure: AppFailure): String? = when (failure) {
        is AppFailure.PermissionDenied -> failure.target
        is AppFailure.Validation -> failure.target
        is AppFailure.BusinessRule -> failure.target
        is AppFailure.NotFound -> failure.target
        is AppFailure.Conflict -> failure.target
        is AppFailure.RemoteRejected -> failure.target
        else -> null
    }
}
