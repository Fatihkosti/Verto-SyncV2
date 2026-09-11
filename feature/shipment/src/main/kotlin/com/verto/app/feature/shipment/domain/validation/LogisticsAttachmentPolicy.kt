package com.verto.app.feature.shipment.domain.validation

/** v231 attachment contract. Validation is centralized so UI/storage cannot drift. */
object LogisticsAttachmentPolicy {
    const val MAX_MP4_BYTES: Long = 100L * 1024L * 1024L
    const val MAX_DOCUMENT_BYTES: Long = 20L * 1024L * 1024L

    private val supported = setOf(
        "image/jpeg",
        "image/png",
        "video/mp4",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/markdown",
        "text/x-markdown",
    )

    fun normalizeMimeType(value: String?): String? = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    fun isSupported(mimeType: String?): Boolean = normalizeMimeType(mimeType) in supported

    fun maxBytes(mimeType: String): Long =
        if (normalizeMimeType(mimeType) == "video/mp4") MAX_MP4_BYTES else MAX_DOCUMENT_BYTES

    fun extensionFor(mimeType: String): String = when (normalizeMimeType(mimeType)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "video/mp4" -> "mp4"
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "text/markdown", "text/x-markdown" -> "md"
        else -> error("Unsupported logistics attachment MIME: $mimeType")
    }

    fun signatureMatches(mimeType: String, prefix: ByteArray): Boolean = when (normalizeMimeType(mimeType)) {
        "image/jpeg" -> prefix.startsWith(0xFF, 0xD8, 0xFF)
        "image/png" -> prefix.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        "video/mp4" -> prefix.size >= 8 && prefix.copyOfRange(4, 8).decodeToString() == "ftyp"
        "application/pdf" -> prefix.startsWithAscii("%PDF-")
        "application/msword" -> prefix.startsWith(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> prefix.startsWith(0x50, 0x4B, 0x03, 0x04)
        "text/markdown", "text/x-markdown" -> prefix.none { it == 0.toByte() }
        else -> false
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { this[it].toInt() and 0xFF == bytes[it] }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        startsWith(*value.encodeToByteArray().map { it.toInt() and 0xFF }.toIntArray())
}
