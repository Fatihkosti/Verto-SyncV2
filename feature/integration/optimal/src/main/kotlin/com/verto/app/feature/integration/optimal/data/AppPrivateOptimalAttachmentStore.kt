package com.verto.app.feature.integration.optimal.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentException
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentFailure
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentPolicy
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentSelection
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentCategory
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioPolicy
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecordingTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalCameraCaptureTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment
import com.verto.app.feature.integration.optimal.domain.port.OptimalAttachmentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppPrivateOptimalAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OptimalAttachmentStore {

    override suspend fun prepareCameraTarget(
        organizationId: String,
        userId: String,
        mediaId: String,
    ): Result<OptimalCameraCaptureTarget> = withContext(Dispatchers.IO) {
        runCatching {
            validateIdentity(organizationId, userId, mediaId)
            val file = File(captureRoot(organizationId, userId), "$mediaId.jpg")
            file.parentFile?.mkdirs()
            if (file.exists() && !file.delete()) error("cannot replace camera target")
            check(file.createNewFile()) { "cannot create camera target" }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
            OptimalCameraCaptureTarget(
                mediaId = mediaId,
                captureUri = contentUri.toString(),
                displayName = "camera-$mediaId.jpg",
            )
        }
    }

    override suspend fun prepareAudioTarget(
        organizationId: String,
        userId: String,
        mediaId: String,
    ): Result<OptimalAudioRecordingTarget> = withContext(Dispatchers.IO) {
        runCatching {
            validateIdentity(organizationId, userId, mediaId)
            val root = audioRecordingRoot(organizationId, userId).also(File::mkdirs)
            cleanupStaleAudioTargets(root)
            val file = File(root, ".$mediaId.recording")
            if (file.exists() && !file.delete()) error("cannot replace audio target")
            check(file.createNewFile()) { "cannot create audio target" }
            check(isUnder(file, root)) { "audio target escaped tenant storage" }
            OptimalAudioRecordingTarget(
                mediaId = mediaId,
                recordingUri = file.toURI().toString(),
                mimeType = OptimalAudioPolicy.MIME_TYPE,
                displayName = "تسجيل صوتي-$mediaId.m4a",
            )
        }
    }

    override suspend fun finalizeAudioTarget(
        organizationId: String,
        userId: String,
        target: OptimalAudioRecordingTarget,
        elapsedDurationMs: Long,
    ): Result<OptimalStoredAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            validateIdentity(organizationId, userId, target.mediaId)
            val recordingRoot = audioRecordingRoot(organizationId, userId)
            val source = target.recordingUri.toOwnedFile(recordingRoot)
            val sizeBytes = source.length()
            if (sizeBytes <= 0L) {
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.EMPTY_FILE,
                    "empty audio recordings are not allowed",
                )
            }
            val encodedDurationMs = readEncodedAudioDuration(source)
            val durationMs = encodedDurationMs ?: elapsedDurationMs
            if (!OptimalAudioPolicy.isValidDuration(durationMs)) {
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.EMPTY_FILE,
                    "audio recording has no actual duration",
                )
            }
            val finalRoot = privateRoot(organizationId, userId).also(File::mkdirs)
            val destination = File(finalRoot, "${target.mediaId}.${OptimalAudioPolicy.FILE_EXTENSION}")
            if (destination.exists() && !destination.delete()) error("cannot replace private audio")
            if (!source.renameTo(destination)) {
                source.copyTo(destination, overwrite = true)
                check(source.delete()) { "cannot clean audio recording target" }
            }
            check(isUnder(destination, finalRoot)) { "audio escaped tenant storage" }
            OptimalStoredAttachment(
                mediaId = target.mediaId,
                privateUri = destination.toURI().toString(),
                mimeType = OptimalAudioPolicy.MIME_TYPE,
                sizeBytes = destination.length(),
                category = OptimalAttachmentCategory.AUDIO,
                displayName = target.displayName,
                durationMs = durationMs,
            )
        }.onFailure {
            discardAudioFile(organizationId, userId, target)
        }
    }

    override suspend fun discardAudioTarget(
        organizationId: String,
        userId: String,
        target: OptimalAudioRecordingTarget,
    ) = withContext(Dispatchers.IO) {
        discardAudioFile(organizationId, userId, target)
    }

    override suspend fun importAttachment(
        organizationId: String,
        userId: String,
        mediaId: String,
        selection: OptimalAttachmentSelection,
    ): Result<OptimalStoredAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            validateIdentity(organizationId, userId, mediaId)
            val sourceUri = selection.sourceUri.trim().takeIf(String::isNotBlank)
                ?: throw OptimalAttachmentException(
                    OptimalAttachmentFailure.CANCELLED,
                    "attachment selection was cancelled",
                )
            val parsedUri = Uri.parse(sourceUri)
            if (parsedUri.scheme != "content") {
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.SECURITY_VIOLATION,
                    "only content URIs may be imported",
                )
            }

            val mimeType = OptimalAttachmentPolicy.normalizeMimeType(selection.mimeType)
                ?: OptimalAttachmentPolicy.normalizeMimeType(context.contentResolver.getType(parsedUri))
                ?: throw OptimalAttachmentException(
                    OptimalAttachmentFailure.UNSUPPORTED_MIME,
                    "attachment MIME type is missing",
                )
            val category = OptimalAttachmentPolicy.categoryFor(mimeType)
                ?: throw OptimalAttachmentException(
                    OptimalAttachmentFailure.UNSUPPORTED_MIME,
                    "unsupported attachment MIME type: $mimeType",
                )
            val displayName = OptimalAttachmentPolicy.safeDisplayName(
                selection.displayName ?: queryDisplayName(parsedUri),
                category,
            )
            val extension = OptimalAttachmentPolicy.fileExtension(mimeType)
            val root = privateRoot(organizationId, userId).also(File::mkdirs)
            val destination = File(root, "$mediaId.$extension")
            val temporary = File(root, ".$mediaId-${UUID.randomUUID()}.part")

            try {
                val input = context.contentResolver.openInputStream(parsedUri)
                    ?: throw OptimalAttachmentException(
                        OptimalAttachmentFailure.SOURCE_UNAVAILABLE,
                        "attachment source cannot be opened",
                    )
                val copiedBytes = input.use { source ->
                    FileOutputStream(temporary).buffered().use { output ->
                        source.buffered().copyTo(output)
                    }
                }
                if (copiedBytes <= 0L) {
                    throw OptimalAttachmentException(
                        OptimalAttachmentFailure.EMPTY_FILE,
                        "empty attachments are not allowed",
                    )
                }
                if (destination.exists() && !destination.delete()) {
                    error("cannot replace private attachment")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    check(temporary.delete()) { "cannot clean temporary attachment" }
                }
                check(isUnder(destination, root)) { "attachment escaped tenant storage" }
                OptimalStoredAttachment(
                    mediaId = mediaId,
                    privateUri = destination.toURI().toString(),
                    mimeType = mimeType,
                    sizeBytes = copiedBytes,
                    category = category,
                    displayName = displayName,
                )
            } catch (error: OptimalAttachmentException) {
                temporary.delete()
                destination.takeIf { it.length() == 0L }?.delete()
                throw error
            } catch (error: SecurityException) {
                temporary.delete()
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.SOURCE_UNAVAILABLE,
                    "attachment permission is unavailable",
                    error,
                )
            } catch (error: java.io.FileNotFoundException) {
                temporary.delete()
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.SOURCE_UNAVAILABLE,
                    "attachment source is missing",
                    error,
                )
            } catch (error: java.io.IOException) {
                temporary.delete()
                throw OptimalAttachmentException(
                    OptimalAttachmentFailure.SOURCE_UNAVAILABLE,
                    "attachment could not be copied",
                    error,
                )
            }
        }
    }

    override suspend fun discardCameraTarget(
        organizationId: String,
        userId: String,
        target: OptimalCameraCaptureTarget,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            validateIdentity(organizationId, userId, target.mediaId)
            val root = captureRoot(organizationId, userId)
            val file = File(root, "${target.mediaId}.jpg")
            if (isUnder(file, root)) file.delete()
        }
        Unit
    }

    override suspend fun deleteStoredAttachment(
        organizationId: String,
        userId: String,
        attachment: OptimalStoredAttachment,
    ) = withContext(Dispatchers.IO) {
        if (owns(organizationId, userId, attachment.privateUri)) {
            val path = Uri.parse(attachment.privateUri).path
            if (!path.isNullOrBlank()) File(path).delete()
        }
    }

    override fun owns(
        organizationId: String,
        userId: String,
        privateUri: String,
    ): Boolean = runCatching {
        if (organizationId.isBlank() || userId.isBlank()) return@runCatching false
        val uri = Uri.parse(privateUri)
        if (uri.scheme != "file" || uri.path.isNullOrBlank()) return@runCatching false
        isUnder(File(requireNotNull(uri.path)), privateRoot(organizationId, userId))
    }.getOrDefault(false)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun discardAudioFile(
        organizationId: String,
        userId: String,
        target: OptimalAudioRecordingTarget,
    ) {
        runCatching {
            validateIdentity(organizationId, userId, target.mediaId)
            val root = audioRecordingRoot(organizationId, userId)
            target.recordingUri.toOwnedFile(root).delete()
        }
    }

    private fun String.toOwnedFile(root: File): File {
        val uri = Uri.parse(this)
        if (uri.scheme != "file" || uri.path.isNullOrBlank()) {
            throw OptimalAttachmentException(
                OptimalAttachmentFailure.SECURITY_VIOLATION,
                "audio target must be an app-private file URI",
            )
        }
        val file = File(requireNotNull(uri.path))
        if (!isUnder(file, root)) {
            throw OptimalAttachmentException(
                OptimalAttachmentFailure.SECURITY_VIOLATION,
                "audio target belongs to another tenant",
            )
        }
        return file
    }

    private fun readEncodedAudioDuration(file: File): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()?.takeIf { it > 0L }

    private fun cleanupStaleAudioTargets(root: File) {
        val cutoff = System.currentTimeMillis() - STALE_AUDIO_TARGET_AGE_MS
        root.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".recording") && it.lastModified() < cutoff }
            .forEach(File::delete)
    }

    private fun privateRoot(organizationId: String, userId: String): File = File(
        context.filesDir,
        "optimal_attachments/${identitySegment(organizationId)}/${identitySegment(userId)}",
    )

    private fun captureRoot(organizationId: String, userId: String): File = File(
        context.cacheDir,
        "optimal_capture/${identitySegment(organizationId)}/${identitySegment(userId)}",
    )

    private fun audioRecordingRoot(organizationId: String, userId: String): File = File(
        privateRoot(organizationId, userId),
        ".audio_recording",
    )

    private fun validateIdentity(organizationId: String, userId: String, mediaId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(userId.isNotBlank()) { "userId is required" }
        require(SAFE_MEDIA_ID.matches(mediaId)) { "invalid mediaId" }
    }

    private fun identitySegment(value: String): String {
        require(value.isNotBlank()) { "identity is required" }
        return MessageDigest.getInstance("SHA-256")
            .digest(value.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun isUnder(file: File, root: File): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalFile = file.canonicalFile
        return canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
    }

    private companion object {
        const val STALE_AUDIO_TARGET_AGE_MS = 24L * 60L * 60L * 1000L
        val SAFE_MEDIA_ID = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
