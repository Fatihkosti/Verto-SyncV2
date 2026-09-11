package com.verto.app.feature.shipment.bridge

import com.verto.app.core.error.BusinessRuleFailureException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.core.content.FileProvider
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsDocumentEntity
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.port.LogisticsDocumentStoragePort
import com.verto.app.feature.shipment.domain.port.LogisticsStoredDocument
import com.verto.app.feature.shipment.domain.validation.LogisticsAttachmentPolicy
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class LogisticsDocumentStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    suspend fun saveDocument(document: LogisticsDocument, event: LogisticsEvent) {
        LogisticsValidation.validateDocument(document)
        requireSameTenant(document.organizationId, event.organizationId)
        require(document.shipmentId == event.shipmentId) { "Document/event belongs to another shipment" }
        database.withTransaction {
            require(dao.getShipment(document.organizationId, document.shipmentId) != null) { "Shipment not found" }
            if (document.milestoneId != null) {
                require(dao.getMilestones(document.organizationId, document.shipmentId).any { it.id == document.milestoneId }) {
                    "Document milestone does not belong to shipment"
                }
            }
            if (document.sourceId != null) {
                require(dao.getSources(document.organizationId, document.shipmentId).any { it.id == document.sourceId }) {
                    "Document source does not belong to shipment"
                }
            }
            if (document.legId != null) {
                require(dao.getLegs(document.organizationId, document.shipmentId).any { it.id == document.legId }) {
                    "Document leg does not belong to shipment"
                }
            }
            if (document.handoffId != null) {
                require(dao.getCustodyHandoffs(document.organizationId, document.shipmentId).any { it.id == document.handoffId }) {
                    "Document handoff does not belong to shipment"
                }
            }
            if (document.costId != null) {
                require(dao.getCosts(document.organizationId, document.shipmentId).any { it.id == document.costId }) {
                    "Document cost does not belong to shipment"
                }
            }
            if (document.recoveryId != null) {
                require(dao.getRecoveries(document.organizationId, document.shipmentId).any { it.id == document.recoveryId }) {
                    "Document recovery does not belong to shipment"
                }
            }
            dao.insertDocument(document.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    suspend fun deleteDocument(
        organizationId: String,
        shipmentId: String,
        documentId: String,
    ): Boolean = database.withTransaction {
        dao.deleteDocument(organizationId, shipmentId, documentId) == 1
    }
}

class AppPrivateLogisticsDocumentStorageAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opener: AndroidLogisticsDocumentOpenAdapter,
) : LogisticsDocumentStoragePort {
    override suspend fun importPrivate(
        organizationId: String,
        shipmentId: String,
        documentId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String,
    ): LogisticsStoredDocument = withContext(Dispatchers.IO) {
        validateIdentity(organizationId, shipmentId, documentId)
        val uri = Uri.parse(sourceUri)
        require(uri.scheme == "content") { "Only Android SAF content URIs are accepted" }
        val declared = LogisticsAttachmentPolicy.normalizeMimeType(mimeType)
        val resolved = LogisticsAttachmentPolicy.normalizeMimeType(context.contentResolver.getType(uri))
        val canonical = resolved ?: declared ?: error("Attachment MIME type is missing")
        if (!LogisticsAttachmentPolicy.isSupported(canonical)) {
            throw BusinessRuleFailureException("LOGISTICS_DOCUMENT_UNSUPPORTED", target = canonical)
        }
        if (declared != null && resolved != null) {
            val markdownAlias = setOf(declared, resolved) == setOf("text/markdown", "text/x-markdown")
            require(declared == resolved || markdownAlias) { "Attachment MIME does not match selected file" }
        }
        val maxBytes = LogisticsAttachmentPolicy.maxBytes(canonical)
        querySize(uri)?.let { size ->
            if (size > maxBytes) throw BusinessRuleFailureException("LOGISTICS_DOCUMENT_TOO_LARGE", target = canonical)
        }

        val root = privateRoot(organizationId, shipmentId).also(File::mkdirs)
        val extension = LogisticsAttachmentPolicy.extensionFor(canonical)
        val destination = File(root, "$documentId.$extension")
        val temporary = File(root, ".$documentId.part")
        if (temporary.exists()) temporary.delete()
        var completed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val prefix = ByteArray(64)
            var prefixSize = 0
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw BusinessRuleFailureException("LOGISTICS_DOCUMENT_TOO_LARGE", target = canonical)
                        val prefixNeed = (64 - prefixSize).coerceAtLeast(0).coerceAtMost(count)
                        if (prefixNeed > 0) {
                            buffer.copyInto(prefix, destinationOffset = prefixSize, endIndex = prefixNeed)
                            prefixSize += prefixNeed
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    total
                }
            } ?: error("Attachment source cannot be opened")
            require(copied > 0L) { "Empty attachments are not allowed" }
            require(LogisticsAttachmentPolicy.signatureMatches(canonical, prefix.copyOf(prefixSize))) {
                "Attachment signature does not match MIME type"
            }
            if (destination.exists()) require(destination.delete()) { "Cannot replace attachment" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                require(temporary.delete()) { "Cannot clean attachment staging file" }
            }
            check(isUnder(destination, root)) { "Attachment escaped shipment storage" }
            LogisticsStoredDocument(
                privateUri = destination.toURI().toString(),
                displayName = safeDisplayName(displayName, canonical),
                mimeType = canonical,
                sizeBytes = copied,
                sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
            ).also { completed = true }
        } finally {
            if (!completed) temporary.delete()
        }
    }

    override suspend fun promoteDraft(
        organizationId: String,
        shipmentId: String,
        documentId: String,
        draftPrivateUri: String,
        displayName: String,
        mimeType: String,
    ): LogisticsStoredDocument = withContext(Dispatchers.IO) {
        validateIdentity(organizationId, shipmentId, documentId)
        require(ownsPrivate(organizationId, shipmentId, draftPrivateUri)) { "Draft document does not belong to this shipment" }
        val canonical = LogisticsAttachmentPolicy.normalizeMimeType(mimeType) ?: error("Attachment MIME type is missing")
        if (!LogisticsAttachmentPolicy.isSupported(canonical)) {
            throw BusinessRuleFailureException("LOGISTICS_DOCUMENT_UNSUPPORTED", target = canonical)
        }
        val source = File(URI(draftPrivateUri).path.orEmpty())
        require(source.isFile) { "Draft document file is missing" }
        if (source.length() !in 1..LogisticsAttachmentPolicy.maxBytes(canonical)) {
            throw BusinessRuleFailureException("LOGISTICS_DOCUMENT_TOO_LARGE", target = canonical)
        }
        val prefix = source.inputStream().buffered().use { input -> ByteArray(64).let { bytes -> bytes.copyOf(input.read(bytes).coerceAtLeast(0)) } }
        require(LogisticsAttachmentPolicy.signatureMatches(canonical, prefix)) { "Attachment signature does not match MIME type" }
        val destination = File(requireNotNull(source.parentFile), "$documentId.${LogisticsAttachmentPolicy.extensionFor(canonical)}")
        source.copyTo(destination, overwrite = true)
        check(ownsPrivate(organizationId, shipmentId, destination.toURI().toString())) { "Promoted document escaped shipment storage" }
        LogisticsStoredDocument(
            privateUri = destination.toURI().toString(),
            displayName = safeDisplayName(displayName, canonical),
            mimeType = canonical,
            sizeBytes = destination.length(),
            sha256 = sha256(destination),
        )
    }

    override suspend fun deleteDraft(organizationId: String, shipmentId: String, draftPrivateUri: String) = withContext(Dispatchers.IO) {
        require(ownsPrivate(organizationId, shipmentId, draftPrivateUri)) { "Draft document does not belong to this shipment" }
        val file = File(URI(draftPrivateUri).path.orEmpty())
        if (file.exists()) require(file.delete()) { "Could not delete staged document" }
    }

    override fun ownsPrivate(organizationId: String, shipmentId: String, privateUri: String): Boolean = runCatching {
        val uri = URI(privateUri)
        uri.scheme == "file" && isUnder(File(uri.path.orEmpty()), privateRoot(organizationId, shipmentId))
    }.getOrDefault(false)

    override suspend fun deletePrivate(privateUri: String) = withContext(Dispatchers.IO) {
        val uri = URI(privateUri)
        val file = File(uri.path.orEmpty()).canonicalFile
        require(uri.scheme == "file" && file.path.contains("${File.separator}logistics_v2_documents${File.separator}")) {
            "Logistics V2 document is outside private storage"
        }
        if (file.exists()) require(file.delete()) { "Could not delete logistics attachment" }
    }

    override fun exists(privateUri: String): Boolean = runCatching {
        val uri = URI(privateUri)
        uri.scheme == "file" && File(uri.path.orEmpty()).isFile
    }.getOrDefault(false)

    override fun open(privateUri: String, mimeType: String): Boolean = opener.open(privateUri, mimeType)

    private fun privateRoot(organizationId: String, shipmentId: String): File = File(
        context.filesDir,
        "logistics_v2_documents/${identitySegment(organizationId)}/${identitySegment(shipmentId)}",
    )

    private fun validateIdentity(organizationId: String, shipmentId: String, documentId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(documentId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "invalid documentId" }
    }

    private fun identitySegment(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    private fun safeDisplayName(value: String, mimeType: String): String = value
        .substringAfterLast('/').substringAfterLast('\\').trim().take(160)
        .ifBlank { "attachment.${LogisticsAttachmentPolicy.extensionFor(mimeType)}" }

    private fun isUnder(file: File, root: File): Boolean {
        val rootPath = root.canonicalFile.path + File.separator
        return file.canonicalFile.path.startsWith(rootPath)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun LogisticsDocument.toEntityV2() = LogisticsDocumentEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    milestoneId = milestoneId,
    sourceId = sourceId,
    legId = legId,
    handoffId = handoffId,
    costId = costId,
    recoveryId = recoveryId,
    employeeId = employeeId,
    employeeNameSnapshot = employeeNameSnapshot,
    type = type.name,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    privateUri = privateUri,
    sha256 = sha256,
    createdAt = createdAt,
)

internal fun LogisticsDocumentEntity.toDomainV2() = LogisticsDocument(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    milestoneId = milestoneId,
    sourceId = sourceId,
    legId = legId,
    handoffId = handoffId,
    costId = costId,
    recoveryId = recoveryId,
    employeeId = employeeId,
    employeeNameSnapshot = employeeNameSnapshot,
    type = LogisticsDocumentType.valueOf(type),
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    privateUri = privateUri,
    sha256 = sha256,
    createdAt = createdAt,
)

@Singleton
class AndroidLogisticsDocumentOpenAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun open(privateUri: String, mimeType: String): Boolean = runCatching {
        val uri = Uri.parse(privateUri)
        val path = uri.path ?: return@runCatching false
        val file = File(path)
        if (!file.isFile) return@runCatching false
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.recoverCatching { error ->
        if (error is ActivityNotFoundException) false else throw error
    }.getOrDefault(false)
}
