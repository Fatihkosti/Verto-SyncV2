package com.verto.app.core.audit.activityevent

import android.content.Context
import com.verto.app.R
import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventActor
import com.verto.feature.dashboard.api.ActivityEventKey
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.ActivityEventSubject
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class AuditActivityAction { INSERT, UPDATE, DELETE }
enum class AuditActivityTable { INVOICE, INVENTORY, OTHER }

data class AuditActivityRecord(
    val id: String,
    val action: AuditActivityAction,
    val table: AuditActivityTable,
    val recordId: String,
    val summary: String,
    val employeeId: String,
    val employeeName: String,
    val occurredAtEpochMillis: Long,
    val sourceType: String,
    val sourceId: String,
    val sourceVersion: Int,
    val writeId: String,
    val invoiceCategory: String?,
)

interface AuditActivityEventSource {
    fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<AuditActivityRecord>>
}

/** Tenant-provable audit fallback. Unowned legacy inventory-price audit is fail-closed in Session 339. */
class AuditActivityEventProvider @Inject constructor(
    private val source: AuditActivityEventSource,
    private val sessionReader: SessionReader,
    @ApplicationContext private val appContext: Context,
) : ActivityEventProvider {
    override val providerId: String = PROVIDER_ID

    override fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            (!context.allows(HomePermissionKeys.SALES_VIEW) && !context.allows(HomePermissionKeys.PURCHASES_VIEW))
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { entries ->
            entries.mapNotNull { it.toFallbackEvent(context) }
        })
    }

    private fun AuditActivityRecord.toFallbackEvent(context: HomePermissionContext): ActivityEvent? {
        if (table != AuditActivityTable.INVOICE) return null
        if (sourceType != INVOICE_VOID_SOURCE && action != AuditActivityAction.DELETE) return null
        return invoiceCancellation(context)
    }

    private fun AuditActivityRecord.invoiceCancellation(context: HomePermissionContext): ActivityEvent? {
        val permission = when (invoiceCategory) {
            "PURCHASE" -> HomePermissionKeys.PURCHASES_VIEW
            "SALE" -> HomePermissionKeys.SALES_VIEW
            else -> return null
        }
        if (!context.allows(permission)) return null
        val semanticWriteId = writeId.trim().ifEmpty { sourceId.trim() }.ifEmpty { recordId }
        return ActivityEvent(
            eventKey = ActivityEventKey.create(PROVIDER_ID, EVENT_INVOICE_CANCELLED, semanticWriteId),
            organizationId = context.organizationId,
            title = "إلغاء فاتورة",
            description = summary.trim().ifEmpty { "أُلغيت فاتورة محفوظة" },
            occurredAtEpochMillis = occurredAtEpochMillis.coerceAtLeast(0L),
            kind = ActivityEventKind.CANCELLATION,
            status = ActivityEventStatus.CANCELLED,
            destination = HomeDestination(
                id = HomeDestinationIds.INVOICE_DETAILS,
                arguments = mapOf("invoiceId" to recordId),
            ),
            actor = employeeName.trim().takeIf(String::isNotEmpty)?.let {
                ActivityEventActor(displayName = it, id = employeeId.takeIf(String::isNotBlank))
            },
            subject = ActivityEventSubject(
                label = summary.trim().ifEmpty { appContext.getString(R.string.legacy_ui_6c5f408664d4) },
                id = recordId,
            ),
            requiredPermission = permission,
        )
    }

    companion object {
        const val PROVIDER_ID = "audit.activity.fallback"
        const val EVENT_INVOICE_CANCELLED = "invoice_cancelled"
        const val INVOICE_VOID_SOURCE = "INVOICE_VOID"
        const val SOURCE_LIMIT = 100
    }
}
