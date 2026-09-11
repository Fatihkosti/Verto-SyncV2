package com.verto.app.feature.integration.optimal.domain.model

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage

enum class OptimalAttachmentCategory {
    IMAGE,
    VIDEO,
    DOCUMENT,
    AUDIO,
}

data class OptimalAttachmentSelection(
    val sourceUri: String,
    val mimeType: String? = null,
    val displayName: String? = null,
)

data class OptimalStoredAttachment(
    val mediaId: String,
    val privateUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: OptimalAttachmentCategory,
    val displayName: String,
    val durationMs: Long? = null,
)

data class OptimalCameraCaptureTarget(
    val mediaId: String,
    val captureUri: String,
    val mimeType: String = "image/jpeg",
    val displayName: String = "camera.jpg",
)

enum class OptimalAttachmentFailure {
    CANCELLED,
    SOURCE_UNAVAILABLE,
    EMPTY_FILE,
    UNSUPPORTED_MIME,
    SESSION_UNAVAILABLE,
    SECURITY_VIOLATION,
    PERSISTENCE_FAILED,
}

class OptimalAttachmentException(
    val failure: OptimalAttachmentFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

sealed interface PrepareOptimalCameraResult {
    data class Ready(val target: OptimalCameraCaptureTarget) : PrepareOptimalCameraResult
    data object PermissionDenied : PrepareOptimalCameraResult
    data object SessionUnavailable : PrepareOptimalCameraResult
    data object Failed : PrepareOptimalCameraResult
}

sealed interface SendOptimalAttachmentResult {
    data class Saved(
        val message: OptimalMessage,
        val attachment: OptimalStoredAttachment,
    ) : SendOptimalAttachmentResult

    data object Cancelled : SendOptimalAttachmentResult
    data object PermissionDenied : SendOptimalAttachmentResult
    data object InvalidConversation : SendOptimalAttachmentResult
    data object SourceUnavailable : SendOptimalAttachmentResult
    data object EmptyFile : SendOptimalAttachmentResult
    data object UnsupportedMime : SendOptimalAttachmentResult
    data object SessionUnavailable : SendOptimalAttachmentResult
    data object SecurityViolation : SendOptimalAttachmentResult
    data object PersistenceFailed : SendOptimalAttachmentResult
}

object OptimalAttachmentPolicy {
    private val documentMimeTypes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    fun normalizeMimeType(value: String?): String? = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    fun categoryFor(mimeType: String?): OptimalAttachmentCategory? {
        val normalized = normalizeMimeType(mimeType) ?: return null
        return when {
            normalized.startsWith("image/") && normalized.substringAfter('/') != "*" -> {
                OptimalAttachmentCategory.IMAGE
            }
            normalized.startsWith("video/") && normalized.substringAfter('/') != "*" -> {
                OptimalAttachmentCategory.VIDEO
            }
            normalized in documentMimeTypes -> OptimalAttachmentCategory.DOCUMENT
            else -> null
        }
    }

    fun fileExtension(mimeType: String): String = when (normalizeMimeType(mimeType)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/quicktime" -> "mov"
        "audio/mp4" -> "m4a"
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        else -> normalizeMimeType(mimeType)
            ?.substringAfter('/')
            ?.filter(Char::isLetterOrDigit)
            ?.take(12)
            ?.ifBlank { "bin" }
            ?: "bin"
    }

    fun safeDisplayName(value: String?, category: OptimalAttachmentCategory): String {
        val normalized = value
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.take(160)
            .orEmpty()
        if (normalized.isNotBlank()) return normalized
        return when (category) {
            OptimalAttachmentCategory.IMAGE -> "صورة"
            OptimalAttachmentCategory.VIDEO -> "فيديو"
            OptimalAttachmentCategory.DOCUMENT -> "مستند"
            OptimalAttachmentCategory.AUDIO -> "تسجيل صوتي"
        }
    }
}
