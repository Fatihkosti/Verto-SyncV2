package com.verto.app.core.security

import java.security.MessageDigest
import java.util.Locale

class SecurityPolicyViolation(message: String) : SecurityException(message)

/**
 * قواعد عزل المؤسسة المشتركة. كل قيمة فارغة أو اختلاف في المؤسسة يُرفض افتراضيًا.
 */
object TenantIsolationPolicy {
    private val tenantPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun requireTenantId(value: String, label: String = "organizationId"): String {
        val normalized = value.trim()
        if (!tenantPattern.matches(normalized)) {
            throw SecurityPolicyViolation("Invalid $label")
        }
        return normalized
    }

    fun requireSameTenant(sessionOrganizationId: String, resourceOrganizationId: String): String {
        val session = requireTenantId(sessionOrganizationId, "sessionOrganizationId")
        val resource = requireTenantId(resourceOrganizationId, "resourceOrganizationId")
        if (!constantTimeEquals(session, resource)) {
            throw SecurityPolicyViolation("Cross-tenant access denied")
        }
        return session
    }

    fun requireAdmin(role: String) {
        if (role.trim().lowercase(Locale.US) != "admin") {
            throw SecurityPolicyViolation("Administrative access required")
        }
    }

    fun requireDifferentActor(actorUserId: String, targetUserId: String) {
        val actor = requireIdentifier(actorUserId, "actorUserId")
        val target = requireIdentifier(targetUserId, "targetUserId")
        if (constantTimeEquals(actor, target)) {
            throw SecurityPolicyViolation("Self-targeting operation denied")
        }
    }

    fun requireIdentifier(value: String, label: String = "identifier"): String {
        val normalized = value.trim()
        if (!tenantPattern.matches(normalized)) {
            throw SecurityPolicyViolation("Invalid $label")
        }
        return normalized
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}

/** Redacts credentials and direct personal identifiers before logs or crash reports. */
object SensitiveDataRedactor {
    private const val MAX_LENGTH = 1024
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+\\-/]+=*")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]{4,})?\\b")
    private val email = Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("(?<![A-Za-z0-9])\\+?[0-9][0-9 ()-]{6,18}[0-9](?![A-Za-z0-9])")
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val secretAssignment = Regex(
        "(?i)(password|passwd|secret|token|api[_-]?key|authorization|invite[_-]?code|recovery[_-]?code)\\s*[:=]\\s*([^,;\\s]+)"
    )

    fun redact(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var result = value.take(MAX_LENGTH)
        result = bearer.replace(result, "Bearer <redacted>")
        result = jwt.replace(result, "<redacted-token>")
        result = secretAssignment.replace(result) { "${it.groupValues[1]}=<redacted>" }
        result = email.replace(result, "<redacted-email>")
        result = phone.replace(result, "<redacted-phone>")
        result = uuid.replace(result, "<redacted-id>")
        return result
    }

    fun pseudonymousId(value: String): String {
        if (value.isBlank()) return "anonymous"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }
}

enum class ChatMediaKind { IMAGE, AUDIO }

data class ValidatedChatMedia(
    val kind: ChatMediaKind,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Int
)

/** Enforces media type, size, and tenant-scoped storage paths before upload. */
object SecureMediaPolicy {
    const val MAX_IMAGE_BYTES: Int = 10 * 1024 * 1024
    const val MAX_AUDIO_BYTES: Int = 20 * 1024 * 1024

    private val imageMimes = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp"
    )
    private val audioMimes = mapOf(
        "audio/mp4" to "m4a",
        "audio/aac" to "aac",
        "audio/m4a" to "m4a"
    )

    fun validate(type: String, mimeType: String, sizeBytes: Int): ValidatedChatMedia {
        val normalizedType = type.trim().uppercase(Locale.US)
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase(Locale.US)
        val kind = when (normalizedType) {
            "IMAGE" -> ChatMediaKind.IMAGE
            "AUDIO", "VOICE" -> ChatMediaKind.AUDIO
            else -> throw SecurityPolicyViolation("Unsupported media type")
        }
        val extension = when (kind) {
            ChatMediaKind.IMAGE -> imageMimes[normalizedMime]
            ChatMediaKind.AUDIO -> audioMimes[normalizedMime]
        } ?: throw SecurityPolicyViolation("Unsupported media MIME type")
        val limit = if (kind == ChatMediaKind.IMAGE) MAX_IMAGE_BYTES else MAX_AUDIO_BYTES
        if (sizeBytes !in 1..limit) throw SecurityPolicyViolation("Media size rejected")
        return ValidatedChatMedia(kind, normalizedMime, extension, sizeBytes)
    }

    fun storagePath(
        organizationId: String,
        userId: String,
        conversationId: String,
        mediaId: String,
        extension: String
    ): String {
        val org = TenantIsolationPolicy.requireTenantId(organizationId)
        val user = TenantIsolationPolicy.requireIdentifier(userId, "userId")
        val conversation = TenantIsolationPolicy.requireIdentifier(conversationId, "conversationId")
        val media = TenantIsolationPolicy.requireIdentifier(mediaId, "mediaId")
        val ext = extension.trim().lowercase(Locale.US)
        if (ext !in setOf("jpg", "png", "webp", "m4a", "aac")) {
            throw SecurityPolicyViolation("Unsupported media extension")
        }
        return "$org/$user/$conversation/$media.$ext"
    }
}
