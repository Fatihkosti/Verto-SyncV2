package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.port.OptimalCompanyTimelinePort
import com.verto.app.feature.integration.optimal.domain.port.OptimalInvoiceTimelineItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTimelineItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTimelineKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalPaymentTimelineItem

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.CompanyEvent
import com.verto.app.feature.integration.optimal.domain.model.CompanyEventsSnapshot
import com.verto.app.feature.integration.optimal.domain.model.CompanyEventType
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyEventsReadModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionReader: SessionReader,
    private val guard: OptimalOperationGuard,
    private val timeline: OptimalCompanyTimelinePort,
) {
    operator fun invoke(clientId: String): Flow<CompanyEventsSnapshot> {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return flowOf(CompanyEventsSnapshot(accessDenied = true))

        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                when {
                    organizationId.isBlank() -> flowOf(CompanyEventsSnapshot(accessDenied = true))
                    else -> observeTenantTimeline(organizationId, normalizedClientId)
                }
            }
    }

    private fun observeTenantTimeline(
        organizationId: String,
        clientId: String,
    ): Flow<CompanyEventsSnapshot> = flow {
        val companyAccess = guard.check(
            operation = OptimalOperation.VIEW_COMPANIES,
            layer = OptimalGuardLayer.USE_CASE,
            details = "organizationId=$organizationId clientId=$clientId",
        )
        if (companyAccess != OptimalAccessDecision.Granted) {
            emit(CompanyEventsSnapshot(
                organizationId = organizationId,
                clientId = clientId,
                accessDenied = true,
            ))
            return@flow
        }

        val canViewInvoices = guard.check(
            operation = OptimalOperation.VIEW_INVOICES,
            layer = OptimalGuardLayer.USE_CASE,
            details = "organizationId=$organizationId clientId=$clientId",
        ) == OptimalAccessDecision.Granted
        val canViewMessages = guard.check(
            operation = OptimalOperation.VIEW_MESSAGES,
            layer = OptimalGuardLayer.USE_CASE,
            details = "organizationId=$organizationId clientId=$clientId",
        ) == OptimalAccessDecision.Granted

        val invoiceFlow = if (canViewInvoices) {
            timeline.observeInvoices(organizationId, clientId)
        } else {
            flowOf(emptyList())
        }
        val paymentFlow = if (canViewInvoices) {
            timeline.observePayments(organizationId, clientId)
        } else {
            flowOf(emptyList())
        }
        val messageFlow = if (canViewMessages) {
            timeline.observeMessages(organizationId, clientId)
        } else {
            flowOf(emptyList())
        }

        emitAll(
            combine(invoiceFlow, paymentFlow, messageFlow) { invoiceItems, paymentItems, messageItems ->
                val events = buildList {
                    addAll(invoiceItems
                        .filter { it.organizationId == organizationId && it.clientId == clientId }
                        .map(OptimalInvoiceTimelineItem::toCompanyEvent))
                    addAll(paymentItems
                        .filter { it.organizationId == organizationId && it.clientId == clientId }
                        .map { it.toCompanyEvent(appContext) })
                    addAll(messageItems
                        .filter { it.organizationId == organizationId && it.clientId == clientId }
                        .map { it.toCompanyEvent(appContext) })
                }.sortedWith(
                    compareByDescending<CompanyEvent> { it.occurredAt }
                        .thenBy { it.type.name }
                        .thenBy { it.eventId },
                )
                CompanyEventsSnapshot(
                    organizationId = organizationId,
                    clientId = clientId,
                    events = events,
                    canViewInvoices = canViewInvoices,
                    canViewMessages = canViewMessages,
                )
            },
        )
    }
}

private fun OptimalInvoiceTimelineItem.toCompanyEvent(): CompanyEvent = CompanyEvent(
    organizationId = organizationId,
    clientId = clientId,
    eventId = invoiceId,
    type = CompanyEventType.INVOICE,
    occurredAt = occurredAt,
    title = "فاتورة رقم $invoiceNumber",
    description = listOf(category, status, description)
        .filter(String::isNotBlank)
        .joinToString(" • ") + if (voided) " • ملغاة" else "",
    amount = totalAmount,
)

private fun OptimalPaymentTimelineItem.toCompanyEvent(context: Context): CompanyEvent = CompanyEvent(
    organizationId = organizationId,
    clientId = clientId,
    eventId = paymentId,
    type = CompanyEventType.PAYMENT,
    occurredAt = occurredAt,
    title = if (isReversal) context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_e2f803163b77_2) else context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_e2f803163b77),
    description = listOf(paymentMethod, note)
        .filter(String::isNotBlank)
        .joinToString(" • "),
    amount = amount,
)

private fun OptimalMessageTimelineItem.toCompanyEvent(context: Context): CompanyEvent = CompanyEvent(
    organizationId = organizationId,
    clientId = clientId,
    eventId = messageId,
    type = CompanyEventType.MESSAGE,
    occurredAt = occurredAt,
    title = when (kind) {
        OptimalMessageTimelineKind.TEXT -> context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_a4a3132ad269)
        OptimalMessageTimelineKind.IMAGE -> context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_3e5b5db19699)
        OptimalMessageTimelineKind.VOICE -> context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_85cc8edf7d80)
        OptimalMessageTimelineKind.VIDEO -> context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_a454b68eef4a)
        OptimalMessageTimelineKind.DOCUMENT -> context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_7e67e821420c)
    },
    description = listOf(senderType, body)
        .filter(String::isNotBlank)
        .joinToString(" • "),
)
