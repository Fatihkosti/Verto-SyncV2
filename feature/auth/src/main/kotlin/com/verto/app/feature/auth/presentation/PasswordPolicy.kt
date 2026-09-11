package com.verto.app.feature.auth.presentation

object PasswordPolicy {
    const val LENGTH = 6

    fun isAcceptable(password: String): Boolean {
        if (password.codePointCount(0, password.length) != LENGTH) return false

        var offset = 0
        repeat(LENGTH) {
            val codePoint = password.codePointAt(offset)
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return false
            offset += Character.charCount(codePoint)
        }
        return true
    }
}
