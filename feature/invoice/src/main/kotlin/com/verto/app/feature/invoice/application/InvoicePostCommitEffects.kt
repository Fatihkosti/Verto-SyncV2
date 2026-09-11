package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.port.InvoiceSyncSchedulerPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal data class InvoiceCreateEffects(
    val organizationId: String,
    val actorId: String,
)

internal data class InvoiceEditEffects(
    val organizationId: String,
    val actorId: String,
    val invoiceId: String,
)

/**
 * Post-commit work that remains client-owned. Business notifications are server-owned and are
 * emitted from authoritative database events, so the Android write path only schedules sync.
 */
internal class InvoicePostCommitEffects @Inject constructor(
    private val syncScheduler: InvoiceSyncSchedulerPort,
) {
    suspend fun afterCreate(effects: InvoiceCreateEffects) {
        syncScheduler.requestSync(effects.organizationId, effects.actorId)
    }

    suspend fun afterEdit(effects: InvoiceEditEffects) {
        syncScheduler.requestSync(effects.organizationId, effects.actorId)
    }
}

@Serializable
internal data class InvoiceAuditDto(
    val id: String,
    val invoiceNumber: Int,
    val clientId: String,
    val category: String,
    val totalAmount: Double,
    val status: String,
    val lifecycleStatus: String,
    val lifecycleVersion: Int,
    val isOwedToMe: Boolean,
    val notes: String,
    val createdAt: Long,
    val postedAt: Long,
    val voidedAt: Long,
    val voidReason: String,
)

internal fun InvoiceRecord.toAuditJson(): String = Json.encodeToString(
    InvoiceAuditDto(
        id = id,
        invoiceNumber = invoiceNumber,
        clientId = clientId,
        category = category.name,
        totalAmount = totalAmount,
        status = status.name,
        lifecycleStatus = lifecycleStatus.name,
        lifecycleVersion = lifecycleVersion,
        isOwedToMe = isOwedToMe,
        notes = notes,
        createdAt = createdAt,
        postedAt = postedAt,
        voidedAt = voidedAt,
        voidReason = voidReason,
    ),
)
