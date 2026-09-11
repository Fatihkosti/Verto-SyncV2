package com.verto.app.utils

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.verto.app.core.security.SensitiveDataRedactor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مركز تقارير الأعطال.
 * يرسل الأعطال إلى Firebase Crashlytics ويحتفظ بنسخة محلية مختصرة للتشخيص السريع على الجهاز.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val MAX_LOG_FILES = 10
    private const val MAX_STACK_FRAMES = 80
    private val BLOCKED_KEYS = setOf(
        "email", "phone", "password", "token", "access_token", "refresh_token",
        "organization_id", "user_id", "invite_code", "recovery_code"
    )

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        installUncaughtExceptionHandler()
    }

    fun setUserId(userId: String) {
        val pseudonym = SensitiveDataRedactor.pseudonymousId(userId)
        FirebaseCrashlytics.getInstance().setUserId(pseudonym)
    }

    fun clearUser() {
        FirebaseCrashlytics.getInstance().setUserId("")
    }

    fun setKey(key: String, value: String) {
        val safeKey = key.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .take(40)
        if (safeKey.isBlank() || safeKey in BLOCKED_KEYS) return
        FirebaseCrashlytics.getInstance().setCustomKey(safeKey, SensitiveDataRedactor.redact(value))
    }

    fun log(message: String) {
        val safe = SensitiveDataRedactor.redact(message)
        if (safe.isBlank()) return
        FirebaseCrashlytics.getInstance().log(safe)
    }

    fun recordException(
        throwable: Throwable,
        operation: String = "",
        incidentId: String = "",
    ) {
        val sanitized = sanitizedThrowable(throwable)
        val safeOperation = SensitiveDataRedactor.redact(operation).ifBlank { "UNSPECIFIED" }
        val safeIncidentId = incidentId
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9-]"), "")
            .take(24)
        val safeMessage = SensitiveDataRedactor.redact(throwable.message).ifBlank { "<no-message>" }
        Log.e(
            TAG,
            "Non-fatal exception operation=$safeOperation incident=${safeIncidentId.ifBlank { "NONE" }} " +
                "type=${throwable::class.java.simpleName} message=$safeMessage",
            sanitized,
        )
        logLocally(sanitized, safeOperation, safeIncidentId)
        FirebaseCrashlytics.getInstance().setCustomKey("failure_operation", safeOperation.take(80))
        if (safeIncidentId.isNotBlank()) {
            FirebaseCrashlytics.getInstance().setCustomKey("failure_incident_id", safeIncidentId)
        }
        FirebaseCrashlytics.getInstance().recordException(sanitized)
    }

    private fun installUncaughtExceptionHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sanitized = sanitizedThrowable(throwable)
            logLocally(sanitized)
            default?.uncaughtException(thread, sanitized)
        }
    }

    private fun logLocally(
        throwable: Throwable,
        operation: String = "",
        incidentId: String = "",
    ) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "crash_logs").apply { mkdirs() }
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val file = File(dir, "crash_${sdf.format(Date())}.txt")
            file.writeText(buildString {
                appendLine("Date: ${Date()}")
                appendLine("Operation: ${SensitiveDataRedactor.redact(operation)}")
                if (incidentId.isNotBlank()) appendLine("Incident: $incidentId")
                appendLine("Type: ${throwable::class.java.name}")
                appendLine("Message: ${SensitiveDataRedactor.redact(throwable.message)}")
                appendLine("Stack:")
                throwable.stackTrace.take(MAX_STACK_FRAMES).forEach { appendLine(it.toString()) }
            })
            pruneOldLogs(dir)
        } catch (_: Exception) { }
    }

    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
    }

    private fun sanitizedThrowable(source: Throwable): Throwable {
        val safeMessage = SensitiveDataRedactor.redact(source.message).ifBlank { "<no-message>" }
        return RuntimeException("Sanitized ${source::class.java.simpleName}: $safeMessage").also {
            it.stackTrace = source.stackTrace.take(MAX_STACK_FRAMES).toTypedArray()
        }
    }

    fun getCrashLogs(): List<File> {
        val ctx = appContext ?: return emptyList()
        val dir = File(ctx.filesDir, "crash_logs")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
