package com.verto.app.feature.invoice.application.activityevent

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.utils.CurrencyFormatter
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventActor
import com.verto.feature.dashboard.api.ActivityEventKey
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.ActivityEventSubject
import com.verto.feature.dashboard.api.ActivityEventValue
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class InvoiceActivityCategory { SALE, PURCHASE }

data class InvoiceActivityRecord(
    val id: String,
    val invoiceNumber: Int,
    val partyName: String,
    val description: String,
    val totalAmount: Double,
    val category: InvoiceActivityCategory,
    val createdAtEpochMillis: Long,
    val createdBy: String,
)

interface InvoiceActivityEventSource {
    fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<InvoiceActivityRecord>>
}

class InvoiceActivityEventProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: InvoiceActivityEventSource,
    private val sessionReader: SessionReader,
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
        emitAll(
            source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { records ->
                records.mapNotNull { record ->
                    val permission = record.viewPermission()
                    if (!context.allows(permission)) null else record.toEvent(context.organizationId, session.user.id, session.user.name, permission)
                }
            },
        )
    }

    private fun InvoiceActivityRecord.toEvent(
        organizationId: String,
        sessionUserId: String,
        sessionUserName: String,
        permission: String,
    ): ActivityEvent {
        val purchase = category == InvoiceActivityCategory.PURCHASE
        val eventType = if (purchase) EVENT_PURCHASE_CREATED else EVENT_SALE_CREATED
        val destination = HomeDestination(
            id = HomeDestinationIds.INVOICE_DETAILS,
            arguments = mapOf("invoiceId" to id),
        )
        return ActivityEvent(
            eventKey = ActivityEventKey.create(PROVIDER_ID, eventType, id),
            organizationId = organizationId,
            title = if (purchase) context.getString(com.verto.feature.invoice.R.string.invoice_v298_927f37382b3f_2, invoiceNumber) else context.getString(com.verto.feature.invoice.R.string.invoice_v298_927f37382b3f, invoiceNumber),
            description = description.trim().ifEmpty {
                if (purchase) "تم تسجيل فاتورة مشتريات" else "تم تسجيل فاتورة مبيعات"
            },
            occurredAtEpochMillis = createdAtEpochMillis.coerceAtLeast(0L),
            kind = if (purchase) ActivityEventKind.PURCHASE else ActivityEventKind.INVOICE,
            status = ActivityEventStatus.CREATED,
            destination = destination,
            actor = createdBy.takeIf(String::isNotBlank)?.let { actorId ->
                sessionUserName.takeIf { actorId == sessionUserId && it.isNotBlank() }
                    ?.let { ActivityEventActor(displayName = it, id = actorId) }
            },
            subject = ActivityEventSubject(
                label = partyName.trim().ifEmpty { context.getString(com.verto.feature.invoice.R.string.invoice_v298_c05f1eddf597, invoiceNumber) },
                id = null,
            ),
            value = ActivityEventValue(
                text = CurrencyFormatter.formatNoSymbol(totalAmount),
                label = "الإجمالي",
            ),
            requiredPermission = permission,
        )
    }

    private fun InvoiceActivityRecord.viewPermission(): String = when (category) {
        InvoiceActivityCategory.SALE -> HomePermissionKeys.SALES_VIEW
        InvoiceActivityCategory.PURCHASE -> HomePermissionKeys.PURCHASES_VIEW
    }

    companion object {
        const val PROVIDER_ID = "invoice.activity"
        const val EVENT_SALE_CREATED = "sale_invoice_created"
        const val EVENT_PURCHASE_CREATED = "purchase_invoice_created"
        const val SOURCE_LIMIT: Int = 100
    }
}
