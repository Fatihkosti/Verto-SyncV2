package com.verto.app.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryOtpPolicyTest {
    @Test fun `extracts eight digit otp copied alone`() {
        assertEquals("12345678", extractRecoveryOtpFromClipboard("12345678"))
    }

    @Test fun `extracts eight digit otp from copied email text`() {
        assertEquals("12345678", extractRecoveryOtpFromClipboard("رمز التحقق: 12345678"))
    }

    @Test fun `does not accept six digit legacy otp`() {
        assertNull(extractRecoveryOtpFromClipboard("123456"))
    }

    @Test fun `does not take eight digits out of a longer number`() {
        assertNull(extractRecoveryOtpFromClipboard("991234567800"))
    }
}
