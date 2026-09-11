package com.verto.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.verto.app.core.error.ErrorRecoveryAction
import com.verto.app.core.error.ErrorSurface
import com.verto.app.core.error.UserErrorMessageKey
import com.verto.app.core.error.UserErrorPresentation
import com.verto.core.common.R as CommonR

/** Resolved localized representation of a structured user error. */
data class ResolvedVertoError(
    val title: String,
    val message: String,
    val tone: VertoStatusTone,
    val icon: ImageVector,
    val actionLabel: String?,
)

@Composable
fun UserErrorPresentation.resolveVertoError(): ResolvedVertoError {
    val title = when (messageKey) {
        UserErrorMessageKey.NETWORK_UNAVAILABLE -> stringResource(CommonR.string.common_error_offline_title)
        UserErrorMessageKey.PERMISSION_DENIED -> stringResource(CommonR.string.common_error_permission_title)
        UserErrorMessageKey.SESSION_EXPIRED -> stringResource(CommonR.string.common_error_session_title)
        else -> stringResource(CommonR.string.common_error_title)
    }
    val baseMessage = when (messageKey) {
        UserErrorMessageKey.NETWORK_UNAVAILABLE -> stringResource(CommonR.string.common_error_network_unavailable)
        UserErrorMessageKey.CONNECTION_FAILED -> stringResource(CommonR.string.common_error_connection_failed)
        UserErrorMessageKey.TIMEOUT -> stringResource(CommonR.string.common_error_timeout)
        UserErrorMessageKey.SESSION_EXPIRED -> stringResource(CommonR.string.common_error_session_expired)
        UserErrorMessageKey.PERMISSION_DENIED -> stringResource(CommonR.string.common_error_permission_denied)
        UserErrorMessageKey.VALIDATION -> stringResource(CommonR.string.common_error_validation)
        UserErrorMessageKey.VALIDATION_FIELD -> stringResource(CommonR.string.common_error_validation_field)
        UserErrorMessageKey.BUSINESS_RULE -> stringResource(CommonR.string.common_error_business_rule)
        UserErrorMessageKey.NOT_FOUND -> stringResource(CommonR.string.common_error_not_found)
        UserErrorMessageKey.CONFLICT -> stringResource(CommonR.string.common_error_conflict)
        UserErrorMessageKey.CONFLICT_DUPLICATE -> stringResource(CommonR.string.common_error_conflict_duplicate)
        UserErrorMessageKey.CONFLICT_RELATED_RECORD -> stringResource(CommonR.string.common_error_conflict_related_record)
        UserErrorMessageKey.CONFLICT_VERSION -> stringResource(CommonR.string.common_error_conflict_version)
        UserErrorMessageKey.RATE_LIMITED -> stringResource(CommonR.string.common_error_rate_limited)
        UserErrorMessageKey.SERVER_UNAVAILABLE -> stringResource(CommonR.string.common_error_server_unavailable)
        UserErrorMessageKey.REQUEST_REJECTED -> stringResource(CommonR.string.common_error_request_rejected)
        UserErrorMessageKey.LOCAL_STORAGE -> stringResource(CommonR.string.common_error_local_storage)
        UserErrorMessageKey.LOCAL_STORAGE_FULL -> stringResource(CommonR.string.common_error_local_storage_full)
        UserErrorMessageKey.LOCAL_STORAGE_CORRUPT -> stringResource(CommonR.string.common_error_local_storage_corrupt)
        UserErrorMessageKey.LOCAL_STORAGE_CONSTRAINT -> stringResource(CommonR.string.common_error_local_storage_constraint)
        UserErrorMessageKey.OPERATION_OUTCOME_UNKNOWN -> stringResource(CommonR.string.common_error_operation_outcome_unknown)
        UserErrorMessageKey.FINANCIAL_MUTATIONS_DISABLED -> stringResource(CommonR.string.common_error_financial_mutations_disabled)
        UserErrorMessageKey.BALANCE_UNAVAILABLE -> stringResource(CommonR.string.common_error_balance_unavailable)
        UserErrorMessageKey.PAYMENT_AMOUNT_INVALID -> stringResource(CommonR.string.common_error_payment_amount_invalid)
        UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_UNKNOWN -> stringResource(CommonR.string.common_error_payment_invoice_currency_unknown)
        UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_INCOMPLETE -> stringResource(CommonR.string.common_error_payment_invoice_currency_incomplete)
        UserErrorMessageKey.PAYMENT_CURRENCY_UNSUPPORTED -> stringResource(CommonR.string.common_error_payment_currency_unsupported)
        UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_REQUIRED -> stringResource(CommonR.string.common_error_payment_invoice_exchange_rate_required)
        UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_INVALID -> stringResource(CommonR.string.common_error_payment_invoice_exchange_rate_invalid)
        UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_REQUIRED -> stringResource(CommonR.string.common_error_payment_exchange_rate_required)
        UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_INVALID -> stringResource(CommonR.string.common_error_payment_exchange_rate_invalid)
        UserErrorMessageKey.PAYMENT_CASH_AMOUNT_RATE_MISMATCH -> stringResource(CommonR.string.common_error_payment_cash_amount_rate_mismatch)
        UserErrorMessageKey.PAYMENT_EXCEEDS_REMAINING -> stringResource(CommonR.string.common_error_payment_exceeds_remaining)
        UserErrorMessageKey.PAYMENT_DUPLICATE -> stringResource(CommonR.string.common_error_payment_duplicate)
        UserErrorMessageKey.PAYMENT_INVOICE_VOIDED -> stringResource(CommonR.string.common_error_payment_invoice_voided)
        UserErrorMessageKey.AUTH_FORM_INCOMPLETE -> stringResource(CommonR.string.common_error_auth_form_incomplete)
        UserErrorMessageKey.AUTH_VERIFY_INVITE_FIRST -> stringResource(CommonR.string.common_error_auth_verify_invite_first)
        UserErrorMessageKey.AUTH_RECOVERY_EMAIL_REQUIRED -> stringResource(CommonR.string.common_error_auth_recovery_email_required)
        UserErrorMessageKey.AUTH_OTP_FORMAT -> stringResource(CommonR.string.common_error_auth_otp_format)
        UserErrorMessageKey.AUTH_INVALID_OTP -> stringResource(CommonR.string.common_error_auth_invalid_otp)
        UserErrorMessageKey.AUTH_EXPIRED_OTP -> stringResource(CommonR.string.common_error_auth_expired_otp)
        UserErrorMessageKey.AUTH_PASSWORD_TOO_SHORT -> stringResource(CommonR.string.common_error_auth_password_too_short)
        UserErrorMessageKey.AUTH_PASSWORD_MISMATCH -> stringResource(CommonR.string.common_error_auth_password_mismatch)
        UserErrorMessageKey.AUTH_INVALID_CREDENTIALS -> stringResource(CommonR.string.common_error_auth_invalid_credentials)
        UserErrorMessageKey.AUTH_EMAIL_NOT_CONFIRMED -> stringResource(CommonR.string.common_error_auth_email_not_confirmed)
        UserErrorMessageKey.AUTH_ALREADY_REGISTERED -> stringResource(CommonR.string.common_error_auth_already_registered)
        UserErrorMessageKey.AUTH_INVITE_USED -> stringResource(CommonR.string.common_error_auth_invite_used)
        UserErrorMessageKey.AUTH_INVALID_INVITE -> stringResource(CommonR.string.common_error_auth_invalid_invite)
        UserErrorMessageKey.AUTH_ACCOUNT_BLOCKED -> stringResource(CommonR.string.common_error_auth_account_blocked)
        UserErrorMessageKey.AUTH_ACCOUNT_UNAVAILABLE -> stringResource(CommonR.string.common_error_auth_account_unavailable)
        UserErrorMessageKey.AUTH_RECOVERY_SESSION_REQUIRED -> stringResource(CommonR.string.common_error_auth_recovery_session_required)
        UserErrorMessageKey.AUTH_RECOVERY_VERIFICATION_UNAVAILABLE -> stringResource(CommonR.string.common_error_auth_recovery_unavailable)
        UserErrorMessageKey.UNEXPECTED -> stringResource(CommonR.string.common_error_unexpected)
    }
    val message = buildString {
        append(baseMessage)
        if (incidentId.isNotBlank()) {
            append('\n')
            append(stringResource(CommonR.string.common_error_support_reference, incidentId))
        }
    }
    val tone = when (messageKey) {
        UserErrorMessageKey.NETWORK_UNAVAILABLE,
        UserErrorMessageKey.CONNECTION_FAILED,
        UserErrorMessageKey.TIMEOUT,
        -> VertoStatusTone.Offline
        UserErrorMessageKey.PERMISSION_DENIED,
        UserErrorMessageKey.SESSION_EXPIRED,
        -> VertoStatusTone.Permission
        UserErrorMessageKey.CONFLICT,
        UserErrorMessageKey.CONFLICT_DUPLICATE,
        UserErrorMessageKey.CONFLICT_RELATED_RECORD,
        UserErrorMessageKey.CONFLICT_VERSION,
        UserErrorMessageKey.RATE_LIMITED,
        UserErrorMessageKey.VALIDATION,
        UserErrorMessageKey.VALIDATION_FIELD,
        UserErrorMessageKey.BUSINESS_RULE,
        UserErrorMessageKey.LOCAL_STORAGE_FULL,
        UserErrorMessageKey.LOCAL_STORAGE_CONSTRAINT,
        UserErrorMessageKey.FINANCIAL_MUTATIONS_DISABLED,
        UserErrorMessageKey.BALANCE_UNAVAILABLE,
        UserErrorMessageKey.PAYMENT_AMOUNT_INVALID,
        UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_UNKNOWN,
        UserErrorMessageKey.PAYMENT_INVOICE_CURRENCY_INCOMPLETE,
        UserErrorMessageKey.PAYMENT_CURRENCY_UNSUPPORTED,
        UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_REQUIRED,
        UserErrorMessageKey.PAYMENT_INVOICE_EXCHANGE_RATE_INVALID,
        UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_REQUIRED,
        UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_INVALID,
        UserErrorMessageKey.PAYMENT_CASH_AMOUNT_RATE_MISMATCH,
        UserErrorMessageKey.PAYMENT_EXCEEDS_REMAINING,
        UserErrorMessageKey.PAYMENT_DUPLICATE,
        UserErrorMessageKey.PAYMENT_INVOICE_VOIDED,
        UserErrorMessageKey.AUTH_FORM_INCOMPLETE,
        UserErrorMessageKey.AUTH_VERIFY_INVITE_FIRST,
        UserErrorMessageKey.AUTH_RECOVERY_EMAIL_REQUIRED,
        UserErrorMessageKey.AUTH_OTP_FORMAT,
        UserErrorMessageKey.AUTH_INVALID_OTP,
        UserErrorMessageKey.AUTH_EXPIRED_OTP,
        UserErrorMessageKey.AUTH_PASSWORD_TOO_SHORT,
        UserErrorMessageKey.AUTH_PASSWORD_MISMATCH,
        UserErrorMessageKey.AUTH_INVALID_CREDENTIALS,
        UserErrorMessageKey.AUTH_EMAIL_NOT_CONFIRMED,
        UserErrorMessageKey.AUTH_ALREADY_REGISTERED,
        UserErrorMessageKey.AUTH_INVITE_USED,
        UserErrorMessageKey.AUTH_INVALID_INVITE,
        UserErrorMessageKey.AUTH_ACCOUNT_BLOCKED,
        UserErrorMessageKey.AUTH_ACCOUNT_UNAVAILABLE,
        UserErrorMessageKey.AUTH_RECOVERY_SESSION_REQUIRED,
        UserErrorMessageKey.AUTH_RECOVERY_VERIFICATION_UNAVAILABLE,
        -> VertoStatusTone.Warning
        else -> VertoStatusTone.Error
    }
    val icon = when (tone) {
        VertoStatusTone.Offline -> Icons.Filled.CloudOff
        VertoStatusTone.Permission -> Icons.Filled.Lock
        VertoStatusTone.Warning -> Icons.Filled.Warning
        else -> Icons.Filled.ErrorOutline
    }
    val actionLabel = when (recoveryAction) {
        ErrorRecoveryAction.RETRY -> stringResource(CommonR.string.common_action_retry)
        ErrorRecoveryAction.LOGIN -> stringResource(CommonR.string.common_action_login)
        ErrorRecoveryAction.EDIT -> stringResource(CommonR.string.common_action_edit)
        ErrorRecoveryAction.NONE -> null
    }
    return ResolvedVertoError(title, message, tone, icon, actionLabel)
}

/** Canonical renderer for structured failures. */
@Composable
fun VertoUserError(
    error: UserErrorPresentation,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onLogin: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    val resolved = error.resolveVertoError()
    val action = when (error.recoveryAction) {
        ErrorRecoveryAction.RETRY -> onRetry
        ErrorRecoveryAction.LOGIN -> onLogin
        ErrorRecoveryAction.EDIT -> onEdit
        ErrorRecoveryAction.NONE -> null
    }
    val actionLabel = resolved.actionLabel.takeIf { action != null }

    when (error.surface) {
        ErrorSurface.FULL_SCREEN -> VertoEmptyState(
            title = resolved.title,
            message = resolved.message,
            icon = resolved.icon,
            variant = VertoEmptyStateVariant.Plain,
            actionLabel = actionLabel,
            onAction = action,
            modifier = modifier,
        )
        ErrorSurface.BANNER -> VertoStatusBanner(
            title = resolved.title,
            message = resolved.message,
            tone = resolved.tone,
            actionLabel = actionLabel,
            onAction = action,
            modifier = modifier,
        )
        ErrorSurface.INLINE,
        ErrorSurface.FIELD,
        -> VertoInlineStatus(
            message = resolved.message,
            tone = resolved.tone,
            modifier = modifier,
        )
    }
}
