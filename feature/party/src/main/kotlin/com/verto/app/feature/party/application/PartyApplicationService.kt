package com.verto.app.feature.party.application

import androidx.paging.PagingData
import com.verto.app.feature.party.application.model.ClientReminderViewData
import com.verto.app.feature.party.application.model.PartyPaymentAllocationResult
import com.verto.app.feature.party.application.model.PartyPaymentTarget
import com.verto.app.feature.party.application.model.PartyPermissionsViewData
import com.verto.app.feature.party.application.port.PartyCashBalancePort
import com.verto.app.feature.party.application.port.PartyInvoiceVoidPort
import com.verto.app.feature.party.application.port.PartyPaymentAllocator
import com.verto.app.feature.party.application.port.PartyPresentationCommand
import com.verto.app.feature.party.application.port.PartyPresentationQuery
import com.verto.app.feature.party.application.query.PartyPagingGateway
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.PartyInvoiceItem
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.party.domain.repository.PartyFinancialQuery
import com.verto.app.feature.party.application.ledger.ObserveCustomerLedgerUseCase
import com.verto.app.feature.party.application.ledger.ObserveSupplierLedgerUseCase
import com.verto.app.feature.party.application.PartyRoleReadPort
import com.verto.app.feature.party.application.intelligence.CustomerDecisionSnapshot
import com.verto.app.feature.party.application.intelligence.ObserveCustomerDecisionUseCase
import com.verto.app.feature.party.application.intelligence.ObserveSupplierPerformanceUseCase
import com.verto.app.feature.party.application.intelligence.ObserveBestSupplierForItemUseCase
import com.verto.app.feature.party.application.intelligence.SupplierPerformanceSnapshot
import com.verto.app.feature.party.application.intelligence.SupplierItemRecommendation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PartyApplicationService @Inject constructor(
    private val directory: PartyDirectoryGateway,
    private val financial: PartyFinancialQuery,
    private val paging: PartyPagingGateway,
    private val presentationQuery: PartyPresentationQuery,
    private val presentationCommand: PartyPresentationCommand,
    private val paymentAllocator: PartyPaymentAllocator,
    private val cashBalancePort: PartyCashBalancePort,
    private val invoiceVoidPort: PartyInvoiceVoidPort,
    private val customerLedger: ObserveCustomerLedgerUseCase,
    private val supplierLedger: ObserveSupplierLedgerUseCase,
    private val partyRoles: PartyRoleReadPort,
    private val customerDecision: ObserveCustomerDecisionUseCase,
    private val supplierPerformance: ObserveSupplierPerformanceUseCase,
    private val bestSupplierForItem: ObserveBestSupplierForItemUseCase,
) {
    fun getClientById(id: String): Flow<PartyClient?> = directory.getClientById(id)
    suspend fun getClientByIdSync(id: String): PartyClient? = directory.getClientByIdSync(id)
    fun getAllClientSummaries(): Flow<List<PartyClientSummary>> = directory.getAllClientSummaries()
    fun searchClientSummaries(query: String): Flow<List<PartyClientSummary>> = directory.searchClientSummaries(query)
    suspend fun insertClient(client: PartyClient): Result<Unit> = directory.insertClient(client)
    suspend fun updateClient(client: PartyClient): Result<Unit> = directory.updateClient(client)
    suspend fun insertParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit> =
        directory.insertParty(client, customerProfile, supplierProfile)
    suspend fun updateParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit> =
        directory.updateParty(client, customerProfile, supplierProfile)
    suspend fun archiveRole(id: String, role: PartyRole): Result<Unit> = directory.archiveRole(id, role)
    suspend fun deleteClientPermanently(id: String): Result<Unit> = directory.deleteClientPermanently(id)

    fun pagedClientSummaries(query: String, showSuppliers: Boolean, supplierScope: com.verto.app.feature.party.domain.model.SupplierScope? = null): Flow<PagingData<PartyClientSummary>> =
        paging.pagedClientSummaries(query, showSuppliers, supplierScope)

    fun getInvoiceSummariesForClient(clientId: String): Flow<List<PartyInvoiceSummary>> =
        financial.getInvoiceSummariesForClient(clientId)
    fun getInvoiceSummariesForClientInRange(clientId: String, from: Long, to: Long): Flow<List<PartyInvoiceSummary>> =
        financial.getInvoiceSummariesForClientInRange(clientId, from, to)
    fun getItemsForClient(clientId: String): Flow<List<PartyInvoiceItem>> = financial.getItemsForClient(clientId)
    suspend fun getInvoiceItemsSync(invoiceId: String): List<PartyInvoiceItem> = financial.getInvoiceItemsSync(invoiceId)
    fun getTotalCollectedForClientInRange(clientId: String, from: Long, to: Long): Flow<Double> =
        financial.getTotalCollectedForClientInRange(clientId, from, to)

    fun getRemindersForClient(clientId: String): Flow<List<ClientReminderViewData>> =
        presentationQuery.getRemindersForClient(clientId)
    fun getNetCreditForClient(clientId: String): Flow<Double> = presentationQuery.getNetCreditForClient(clientId)
    val permissions: Flow<PartyPermissionsViewData?> = presentationQuery.permissions
    suspend fun canEditClientsNow(): Boolean = presentationQuery.canEditClientsNow()
    suspend fun insertReminder(reminder: ClientReminderViewData) = presentationCommand.insertReminder(reminder)
    suspend fun deleteReminder(reminder: ClientReminderViewData) = presentationCommand.deleteReminder(reminder)
    suspend fun markReminderDone(id: String) = presentationCommand.markReminderDone(id)
    suspend fun fullSync(): Result<Unit> = presentationCommand.fullSync()

    suspend fun voidInvoice(id: String, reason: String, refundPayments: Boolean) =
        invoiceVoidPort.void(id, reason, refundPayments)
    suspend fun allocatePayment(
        clientId: String,
        clientName: String,
        amount: Double,
        targets: List<PartyPaymentTarget>,
        note: String,
        moneyIn: Boolean,
        rate: Double = 1.0,
        requireSupplierPermission: Boolean = false,
        requestId: String,
    ): PartyPaymentAllocationResult = paymentAllocator(
        clientId = clientId,
        clientName = clientName,
        amount = amount,
        targets = targets,
        note = note,
        moneyIn = moneyIn,
        rate = rate,
        requireSupplierPermission = requireSupplierPermission,
        requestId = requestId,
    )

    val cashBalance: Flow<Double> get() = cashBalancePort.balance

    fun observeCustomerLedger(partyId: String, fromInclusive: Long = 0L, toExclusive: Long = Long.MAX_VALUE) =
        customerLedger(partyId, fromInclusive, toExclusive)

    fun observeSupplierLedger(partyId: String, fromInclusive: Long = 0L, toExclusive: Long = Long.MAX_VALUE) =
        supplierLedger(partyId, fromInclusive, toExclusive)

    fun observeCustomerSegment(partyId: String) = partyRoles.observeCustomerSegment(partyId)
    fun observeSupplierScope(partyId: String) = partyRoles.observeSupplierScope(partyId)
    fun observeCustomerProfile(partyId: String) = partyRoles.observeCustomerProfile(partyId)
    fun observeSupplierProfile(partyId: String) = partyRoles.observeSupplierProfile(partyId)
    fun observeIsCompetitor(partyId: String) = partyRoles.observeIsCompetitor(partyId)

    /** Party-owned full customer intelligence for Party presentation. */
    internal fun observeCustomerDecision(partyId: String): Flow<CustomerDecisionSnapshot> = customerDecision(partyId)

    /** Supplier scorecard derived from immutable purchase-cycle facts. */
    internal fun observeSupplierPerformance(partyId: String): Flow<SupplierPerformanceSnapshot> =
        supplierPerformance(partyId)

    /** Item-level supplier ranking for Party presentation. */
    internal fun observeBestSupplierForItem(inventoryItemId: String): Flow<SupplierItemRecommendation> =
        bestSupplierForItem(inventoryItemId)
}