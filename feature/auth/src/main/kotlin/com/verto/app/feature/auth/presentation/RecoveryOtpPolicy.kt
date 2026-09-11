package com.verto.app.feature.auth.presentation

/** Must match the hosted Supabase Recovery OTP length. */
internal const val RECOVERY_OTP_LENGTH = 8

private val recoveryOtpPattern = Regex("(?<!\\d)\\d{$RECOVERY_OTP_LENGTH}(?!\\d)")

/** Extracts one complete recovery OTP from copied email text without accepting partial codes. */
internal fun extractRecoveryOtpFromClipboard(text: String?): String? =
    text
        ?.let(recoveryOtpPattern::find)
        ?.value
