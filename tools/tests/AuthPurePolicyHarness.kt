package com.verto.app.feature.auth.presentation

private var passed = 0

private fun checkCase(name: String, block: () -> Unit) {
    try {
        block()
        passed += 1
        println("PASS | $name")
    } catch (t: Throwable) {
        println("FAIL | $name | ${t.message}")
        throw t
    }
}

private fun assertEq(expected: Any?, actual: Any?) {
    check(expected == actual) { "expected=$expected actual=$actual" }
}

fun main() {
    checkCase("login trims valid email") {
        check(LoginValidationPolicy.validate(" user@example.com ", "secret").isValid)
    }
    checkCase("login rejects blank email") {
        assertEq(LoginInputError.Required, LoginValidationPolicy.validateEmail("  "))
    }
    checkCase("login rejects malformed email") {
        assertEq(LoginInputError.InvalidEmail, LoginValidationPolicy.validateEmail("x@"))
    }
    checkCase("registration phone is optional") {
        assertEq(null, RegisterValidationPolicy.validatePhone(""))
    }
    checkCase("registration rejects alphabetic phone") {
        assertEq(RegisterInputError.InvalidPhone, RegisterValidationPolicy.validatePhone("+249abc"))
    }
    checkCase("password below 15 rejected") {
        check(!PasswordPolicy.isAcceptable("a".repeat(14)))
    }
    checkCase("password length 15 accepted") {
        check(PasswordPolicy.isAcceptable("a".repeat(15)))
    }
    checkCase("recovery otp contract is 8 digits") {
        assertEq(8, RECOVERY_OTP_LENGTH)
    }
    checkCase("clipboard extracts standalone 8 digit otp") {
        assertEq("12345678", extractRecoveryOtpFromClipboard("12345678"))
    }
    checkCase("clipboard extracts otp from email text") {
        assertEq("12345678", extractRecoveryOtpFromClipboard("رمز التحقق: 12345678"))
    }
    checkCase("clipboard rejects old 6 digit otp") {
        assertEq(null, extractRecoveryOtpFromClipboard("123456"))
    }
    checkCase("clipboard rejects 8 digits embedded in longer number") {
        assertEq(null, extractRecoveryOtpFromClipboard("991234567800"))
    }
    println("SUMMARY | $passed passed | 0 failed")
}
