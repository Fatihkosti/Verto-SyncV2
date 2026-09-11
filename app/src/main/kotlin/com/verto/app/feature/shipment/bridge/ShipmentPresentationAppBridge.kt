package com.verto.app.feature.shipment.bridge

import com.verto.app.feature.expenses.application.ExpensesGateway
import com.verto.app.feature.organization.domain.repository.OrganizationTeamGateway
import com.verto.app.feature.shipment.application.model.LogisticsEmployeeRef
import com.verto.app.feature.shipment.application.port.LogisticsPlanningReferencePort
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class AppLogisticsPlanningReferenceAdapter @Inject constructor(
    private val team: OrganizationTeamGateway,
    private val expenses: ExpensesGateway,
) : LogisticsPlanningReferencePort {
    override suspend fun activeEmployees(): List<LogisticsEmployeeRef> =
        team.getEmployees().getOrThrow()
            .filter { it.isActive }
            .map { LogisticsEmployeeRef(id = it.userId, name = it.name) }

    override suspend fun recentPurchaseInvoiceIds(fromInclusive: Long, toInclusive: Long): List<String> =
        expenses.observeRecentPurchaseInvoices(fromInclusive, toInclusive).first().map { it.id }
}
