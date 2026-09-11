package com.verto.app.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPresentationPolicyTest {

    @Test
    fun `offline screen load is full screen and retryable`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.NetworkUnavailable(),
            ErrorPresentationContext.SCREEN_LOAD,
        )
        assertEquals(UserErrorMessageKey.NETWORK_UNAVAILABLE, value.messageKey)
        assertEquals(ErrorSurface.FULL_SCREEN, value.surface)
        assertEquals(ErrorRecoveryAction.RETRY, value.recoveryAction)
    }

    @Test
    fun `offline action is banner and retryable`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.NetworkUnavailable(),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(ErrorSurface.BANNER, value.surface)
        assertEquals(ErrorRecoveryAction.RETRY, value.recoveryAction)
    }

    @Test
    fun `targeted validation is field error`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Validation(target = "amount"),
            ErrorPresentationContext.FORM,
        )
        assertEquals(UserErrorMessageKey.VALIDATION_FIELD, value.messageKey)
        assertEquals(ErrorSurface.FIELD, value.surface)
        assertEquals(ErrorRecoveryAction.EDIT, value.recoveryAction)
        assertEquals("amount", value.target)
    }

    @Test
    fun `untargeted validation is neutral inline form error`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Validation(),
            ErrorPresentationContext.FORM,
        )
        assertEquals(UserErrorMessageKey.VALIDATION, value.messageKey)
        assertEquals(ErrorSurface.INLINE, value.surface)
        assertEquals(ErrorRecoveryAction.EDIT, value.recoveryAction)
    }

    @Test
    fun `service failure cannot be forced onto field surface`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Server(statusCode = 503),
            ErrorPresentationContext.FIELD,
        )
        assertEquals(ErrorSurface.BANNER, value.surface)
    }

    @Test
    fun `postgres duplicate conflict is distinct`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Conflict(remoteCode = "23505"),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.CONFLICT_DUPLICATE, value.messageKey)
    }

    @Test
    fun `postgres related record conflict is distinct`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Conflict(remoteCode = "23503"),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.CONFLICT_RELATED_RECORD, value.messageKey)
    }

    @Test
    fun `version conflict alone gets stale data copy`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Conflict(remoteCode = "VERSION_CONFLICT"),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.CONFLICT_VERSION, value.messageKey)
    }

    @Test
    fun `unknown conflict stays generic`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Conflict(remoteCode = "unknown_conflict"),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.CONFLICT, value.messageKey)
    }

    @Test
    fun `full storage is distinct from corrupt storage`() {
        assertEquals(
            UserErrorMessageKey.LOCAL_STORAGE_FULL,
            ErrorPresentationPolicy.from(
                AppFailure.LocalStorage(LocalStorageKind.FULL),
                ErrorPresentationContext.TRANSIENT_ACTION,
            ).messageKey,
        )
        assertEquals(
            UserErrorMessageKey.LOCAL_STORAGE_CORRUPT,
            ErrorPresentationPolicy.from(
                AppFailure.LocalStorage(LocalStorageKind.CORRUPT),
                ErrorPresentationContext.TRANSIENT_ACTION,
            ).messageKey,
        )
    }

    @Test
    fun `expired session asks for login`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Unauthorized(),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.SESSION_EXPIRED, value.messageKey)
        assertEquals(ErrorRecoveryAction.LOGIN, value.recoveryAction)
    }

    @Test
    fun `automatic backoff does not expose immediate retry`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Server(statusCode = 503),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertEquals(UserErrorMessageKey.SERVER_UNAVAILABLE, value.messageKey)
        assertEquals(ErrorRecoveryAction.NONE, value.recoveryAction)
    }

    @Test
    fun `unknown financial outcome never exposes retry`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Timeout(),
            ErrorPresentationContext.TRANSIENT_ACTION,
            outcome = OperationOutcome.OUTCOME_UNKNOWN,
            incidentId = "E-TEST123456",
        )
        assertEquals(UserErrorMessageKey.OPERATION_OUTCOME_UNKNOWN, value.messageKey)
        assertEquals(ErrorRecoveryAction.NONE, value.recoveryAction)
        assertEquals(OperationOutcome.OUTCOME_UNKNOWN, value.outcome)
        assertEquals("E-TEST123456", value.incidentId)
    }

    @Test
    fun `generated incident reference is short and opaque`() {
        val value = ErrorPresentationPolicy.from(
            AppFailure.Server(statusCode = 503),
            ErrorPresentationContext.TRANSIENT_ACTION,
        )
        assertTrue(value.incidentId.startsWith("E-"))
        assertTrue(value.incidentId.length in 8..24)
    }

    @Test
    fun `payment business codes keep actionable message keys`() {
        assertEquals(
            UserErrorMessageKey.PAYMENT_EXCEEDS_REMAINING,
            ErrorPresentationPolicy.messageKeyFor(
                AppFailure.BusinessRule(code = "PAYMENT_EXCEEDS_REMAINING", target = "amount")
            ),
        )
        assertEquals(
            UserErrorMessageKey.PAYMENT_EXCHANGE_RATE_REQUIRED,
            ErrorPresentationPolicy.messageKeyFor(
                AppFailure.BusinessRule(code = "PAYMENT_EXCHANGE_RATE_REQUIRED", target = "exchangeRate")
            ),
        )
    }
}
