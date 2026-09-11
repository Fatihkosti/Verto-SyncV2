package com.verto.app.utils

import android.util.Log
import com.verto.app.core.security.SensitiveDataRedactor

interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

class DebugLogger : Logger {
    override fun d(tag: String, message: String) { Log.d(tag, SensitiveDataRedactor.redact(message)) }
    override fun w(tag: String, message: String) { Log.w(tag, SensitiveDataRedactor.redact(message)) }
    override fun e(tag: String, message: String, throwable: Throwable?) {
        val safeMessage = SensitiveDataRedactor.redact(message)
        if (throwable != null) Log.e(tag, safeMessage, RuntimeException("Sanitized ${throwable::class.java.simpleName}"))
        else Log.e(tag, safeMessage)
    }
}

// استخدم في الـ release أو أرسل لـ Crashlytics / Sentry
class ReleaseLogger(
    private val onError: (tag: String, message: String, throwable: Throwable?) -> Unit = { _, _, _ -> }
) : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) =
        onError(tag, message, throwable)
}

// للاختبارات — يبتلع كل الرسائل بصمت
class SilentLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
