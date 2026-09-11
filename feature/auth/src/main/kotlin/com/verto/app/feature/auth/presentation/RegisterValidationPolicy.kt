package com.verto.app.feature.auth.presentation

enum class RegisterInputError {
    Required,
    InvalidPhone,
    InvalidEmail,
    PasswordTooShort,
    PasswordMismatch,
}

data class RegisterValidationResult(
    val organizationNameError: RegisterInputError? = null,
    val ownerNameError: RegisterInputError? = null,
    val phoneError: RegisterInputError? = null,
    val emailError: RegisterInputError? = null,
    val passwordError: RegisterInputError? = null,
    val confirmationError: RegisterInputError? = null,
) {
    val isStepOneValid: Boolean
        get() = organizationNameError == null

    val isStepTwoValid: Boolean
        get() = ownerNameError == null && phoneError == null && emailError == null &&
            passwordError == null && confirmationError == null

    val isValid: Boolean
        get() = isStepOneValid && isStepTwoValid
}

/** Pure registration validation policy, independent from Compose and Android APIs. */
object RegisterValidationPolicy {
    private val phonePattern = Regex("^\\+?[0-9]+$")

    fun validate(
        organizationName: String,
        ownerName: String,
        phone: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ): RegisterValidationResult = RegisterValidationResult(
        organizationNameError = requiredError(organizationName),
        ownerNameError = requiredError(ownerName),
        phoneError = validatePhone(phone),
        emailError = when (LoginValidationPolicy.validateEmail(email)) {
            LoginInputError.Required -> RegisterInputError.Required
            LoginInputError.InvalidEmail -> RegisterInputError.InvalidEmail
            null -> null
        },
        passwordError = when {
            password.isBlank() -> RegisterInputError.Required
            !PasswordPolicy.isAcceptable(password) -> RegisterInputError.PasswordTooShort
            else -> null
        },
        confirmationError = when {
            passwordConfirmation.isBlank() -> RegisterInputError.Required
            passwordConfirmation != password -> RegisterInputError.PasswordMismatch
            else -> null
        },
    )

    fun validatePhone(phone: String): RegisterInputError? {
        val normalized = phone.trim()
        return when {
            normalized.isEmpty() -> null
            !phonePattern.matches(normalized) -> RegisterInputError.InvalidPhone
            else -> null
        }
    }

    private fun requiredError(value: String): RegisterInputError? =
        if (value.trim().isEmpty()) RegisterInputError.Required else null

}
