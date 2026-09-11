package com.verto.app.feature.auth.data

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ClassifiedFailureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorTranslatorTest {

    @Test fun `refresh token failure is session failure never otp failure`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(401, "refresh_token_not_found"),
            AuthOperation.LOGIN,
        )
        assertTrue(translated is ClassifiedFailureException)
        val failure = (translated as ClassifiedFailureException).failure
        assertTrue(failure is AppFailure.Unauthorized)
        assertEquals("refresh_token_not_found", (failure as AppFailure.Unauthorized).remoteCode)
    }

    @Test fun `invalid login credentials require explicit provider code`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(400, "invalid_credentials"),
            AuthOperation.LOGIN,
        )
        assertTrue(translated is BusinessRuleFailureException)
        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, (translated as BusinessRuleFailureException).code)
    }

    @Test fun `opaque login 400 is validation not invalid credentials`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(400, "provider_request_rejected"),
            AuthOperation.LOGIN,
        )
        assertTrue(translated is ClassifiedFailureException)
        assertTrue((translated as ClassifiedFailureException).failure is AppFailure.Validation)
    }

    @Test fun `invalid otp maps only inside otp operation`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(400, "invalid_otp"),
            AuthOperation.VERIFY_PASSWORD_RESET_OTP,
        )
        assertTrue(translated is BusinessRuleFailureException)
        assertEquals(AuthErrorCodes.INVALID_OTP, (translated as BusinessRuleFailureException).code)
    }

    @Test fun `otp code outside otp operation is not blamed on otp`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(400, "invalid_otp"),
            AuthOperation.LOAD_PROFILE,
        )
        assertTrue(translated is ClassifiedFailureException)
        assertTrue((translated as ClassifiedFailureException).failure is AppFailure.Validation)
    }

    @Test fun `opaque otp 401 is session failure not invalid otp`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(401, "provider_request_rejected"),
            AuthOperation.VERIFY_PASSWORD_RESET_OTP,
        )
        assertTrue(translated is ClassifiedFailureException)
        assertTrue((translated as ClassifiedFailureException).failure is AppFailure.Unauthorized)
    }

    @Test fun `profile load 400 cannot become invalid credentials`() {
        val translated = AuthErrorTranslator.translate(
            FakeProviderException(400, "profile_contract_error"),
            AuthOperation.LOAD_PROFILE,
        )
        assertTrue(translated is ClassifiedFailureException)
        assertTrue((translated as ClassifiedFailureException).failure is AppFailure.Validation)
    }

    private class FakeProviderException(
        private val rawStatusCode: Int,
        private val rawErrorCode: String,
    ) : RuntimeException("ignored provider message") {
        fun getStatusCode(): Int = rawStatusCode
        fun getErrorCode(): String = rawErrorCode
    }
}
