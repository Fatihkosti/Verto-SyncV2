package com.verto.app.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationPolicyTest {
    @Test fun `valid login accepts trimmed email`() {
        assertTrue(LoginValidationPolicy.validate(" user@example.com ", "secret").isValid)
    }

    @Test fun `login rejects blank password and malformed email`() {
        val result = LoginValidationPolicy.validate("x@", "")
        assertEquals(LoginInputError.InvalidEmail, result.emailError)
        assertEquals(LoginInputError.Required, result.passwordError)
    }

    @Test fun `registration phone is optional`() {
        assertNull(RegisterValidationPolicy.validatePhone(""))
    }

    @Test fun `password policy accepts exactly six characters`() {
        assertFalse(PasswordPolicy.isAcceptable("99009"))
        assertTrue(PasswordPolicy.isAcceptable("990099"))
        assertFalse(PasswordPolicy.isAcceptable("9900990"))
    }

    @Test fun `password policy rejects whitespace and control characters`() {
        assertFalse(PasswordPolicy.isAcceptable("990 99"))
        assertFalse(PasswordPolicy.isAcceptable("990\n99"))
    }
}
