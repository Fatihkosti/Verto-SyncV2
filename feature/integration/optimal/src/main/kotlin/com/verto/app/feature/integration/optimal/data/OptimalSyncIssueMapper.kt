package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.feature.integration.optimal.application.OptimalSyncErrorHumanizer
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssueState
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class OptimalSyncIssueMapper @Inject constructor(
    private val humanizer: OptimalSyncErrorHumanizer,
) {
    fun map(entity: OptimalOutboxEntity): OptimalSyncIssue {
        val payload = parsePayload(entity.payloadJson)
        return OptimalSyncIssue(
            organizationId = entity.organizationId,
            eventId = entity.eventId,
            aggregateType = entity.aggregateType,
            aggregateId = entity.aggregateId,
            operation = entity.operation,
            dataTypeLabel = dataTypeLabel(entity.aggregateType, entity.operation),
            operationLabel = operationLabel(entity.operation),
            clientId = payload.string("clientId", "client_id"),
            companyName = payload.string("companyName", "company_name"),
            invoiceId = payload.string("invoiceId", "invoice_id")
                ?: entity.aggregateId.takeIf { entity.aggregateType.equals("invoice", ignoreCase = true) },
            reason = humanizer.humanize(entity.lastError),
            state = when (entity.status) {
                OptimalOutboxStatus.BLOCKED -> OptimalSyncIssueState.BLOCKED
                OptimalOutboxStatus.FAILED -> OptimalSyncIssueState.FAILED
                OptimalOutboxStatus.PENDING,
                OptimalOutboxStatus.SYNCING,
                -> OptimalSyncIssueState.RETRYING
                else -> error("${entity.status} is not a visible sync issue state")
            },
            attemptCount = entity.attemptCount,
            lastAttemptAt = entity.lastAttemptAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun parsePayload(raw: String): JsonObject = runCatching {
        JSON.parseToJsonElement(raw) as? JsonObject
    }.getOrNull() ?: JsonObject(emptyMap())

    private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (get(key) as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun dataTypeLabel(aggregateType: String, operation: String): String = when {
        operation.contains("MAINTENANCE", ignoreCase = true) -> "صيانة"
        operation.contains("PAYMENT", ignoreCase = true) -> "دفعة"
        aggregateType.equals("invoice", ignoreCase = true) -> "فاتورة"
        aggregateType.equals("conversation", ignoreCase = true) -> "رسالة"
        operation.contains("FILE", ignoreCase = true) || operation.contains("ATTACHMENT", ignoreCase = true) -> "ملف"
        else -> "بيانات Optimal"
    }

    private fun operationLabel(operation: String): String = when (operation.uppercase()) {
        "INVOICE_CREATED" -> "إنشاء فاتورة"
        "INVOICE_UPDATED" -> "تحديث فاتورة"
        "PAYMENT_RECORDED" -> "تسجيل دفعة"
        "PAYMENT_REVERSED" -> "عكس دفعة"
        "INVOICE_VOIDED" -> "إلغاء فاتورة"
        "MAINTENANCE_UPSERTED" -> "تحديث سجل صيانة"
        "SEND_MESSAGE" -> "إرسال رسالة"
        "ARCHIVE_CONVERSATION" -> "أرشفة محادثة"
        "UNARCHIVE_CONVERSATION" -> "إلغاء أرشفة محادثة"
        else -> "مزامنة بيانات"
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
