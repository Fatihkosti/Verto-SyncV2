package com.verto.app.feature.auth.presentation

enum class LoginInputError {
    Required,
    InvalidEmail,
}

data class LoginValidationResult(
    val emailError: LoginInputError? = null,
    val passwordError: LoginInputError? = null,
) {
    val isValid: Boolean
        get() = emailError == null && passwordError == null
}

/** Pure validation policy so login rules stay testable outside Compose. */
object LoginValidationPolicy {
    private val emailPattern = Regex(
        pattern = "^[A-Za-z0-9.!#\\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
    )

    fun validate(email: String, password: String): LoginValidationResult = LoginValidationResult(
        emailError = validateEmail(email),
        passwordError = if (password.isBlank()) LoginInputError.Required else null,
    )

    fun validateEmail(email: String): LoginInputError? {
        val normalized = email.trim()
        return when {
            normalized.isEmpty() -> LoginInputError.Required
            !emailPattern.matches(normalized) -> LoginInputError.InvalidEmail
            else -> null
        }
    }
}
