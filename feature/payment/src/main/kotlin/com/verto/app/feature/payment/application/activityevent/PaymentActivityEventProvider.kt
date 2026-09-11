package com.verto.app.feature.payment.application.activityevent

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
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class PaymentActivityCategory { SALE, PURCHASE }

data class PaymentActivityRecord(
    val id: String,
    val invoiceId: String,
    val invoiceNumber: Int,
    val partyName: String,
    val amount: Double,
    val paidAtEpochMillis: Long,
    val employeeId: String,
    val employeeName: String,
    val reversedPaymentId: String?,
    val invoiceCategory: PaymentActivityCategory,
    val invoiceVoided: Boolean,
)

interface PaymentActivityEventSource {
    fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<PaymentActivityRecord>>
}

class PaymentActivityEventProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: PaymentActivityEventSource,
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
                    if (!context.allows(permission)) null else record.toEvent(context.organizationId, permission)
                }
            },
        )
    }

    private fun PaymentActivityRecord.toEvent(
        organizationId: String,
        permission: String,
    ): ActivityEvent {
        val reversed = reversedPaymentId != null || amount < 0.0
        return ActivityEvent(
            eventKey = ActivityEventKey.create(
                PROVIDER_ID,
                if (reversed) EVENT_REVERSED else EVENT_RECORDED,
                id,
            ),
            organizationId = organizationId,
            title = if (reversed) context.getString(com.verto.feature.payment.R.string.payment_v298_311c9d6223e9_2) else context.getString(com.verto.feature.payment.R.string.payment_v298_311c9d6223e9),
            description = "فاتورة #$invoiceNumber • ${partyName.trim().ifEmpty { "طرف الفاتورة" }}",
            occurredAtEpochMillis = paidAtEpochMillis.coerceAtLeast(0L),
            kind = ActivityEventKind.PAYMENT,
            status = if (reversed) ActivityEventStatus.REVERSED else ActivityEventStatus.COMPLETED,
            destination = HomeDestination(
                id = HomeDestinationIds.INVOICE_DETAILS,
                arguments = mapOf("invoiceId" to invoiceId),
            ),
            actor = employeeName.trim().takeIf(String::isNotEmpty)?.let {
                ActivityEventActor(displayName = it, id = employeeId.takeIf(String::isNotBlank))
            },
            subject = ActivityEventSubject(label = "فاتورة #$invoiceNumber", id = invoiceId),
            value = ActivityEventValue(
                text = CurrencyFormatter.formatNoSymbol(abs(amount)),
                label = if (reversed) context.getString(com.verto.feature.payment.R.string.payment_v298_154af6afc58b) else context.getString(com.verto.core.common.R.string.common_amount),
            ),
            requiredPermission = permission,
        )
    }

    private fun PaymentActivityRecord.viewPermission(): String = when (invoiceCategory) {
        PaymentActivityCategory.SALE -> HomePermissionKeys.SALES_VIEW
        PaymentActivityCategory.PURCHASE -> HomePermissionKeys.PURCHASES_VIEW
    }

    companion object {
        const val PROVIDER_ID = "payment.activity"
        const val EVENT_RECORDED = "payment_recorded"
        const val EVENT_REVERSED = "payment_reversed"
        const val SOURCE_LIMIT = 100
    }
}
