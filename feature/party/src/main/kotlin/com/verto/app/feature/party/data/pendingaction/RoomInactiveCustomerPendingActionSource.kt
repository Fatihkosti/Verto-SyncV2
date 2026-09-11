package com.verto.app.feature.party.data.pendingaction

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.feature.party.application.pendingaction.InactiveCustomerPendingActionSource
import com.verto.app.feature.party.application.pendingaction.InactiveCustomerRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInactiveCustomerPendingActionSource @Inject constructor(
    private val clientDao: ClientDao,
) : InactiveCustomerPendingActionSource {
    override fun observeCustomers(
        organizationId: String,
        inactiveCutoffEpochMillis: Long,
        highPriorityCutoffEpochMillis: Long,
    ): Flow<List<InactiveCustomerRecord>> =
        clientDao.observeInactiveCustomerCandidates(
            organizationId = organizationId,
            inactiveCutoffEpochMillis = inactiveCutoffEpochMillis,
            highPriorityCutoffEpochMillis = highPriorityCutoffEpochMillis,
        ).map { rows ->
            rows.map { row ->
                InactiveCustomerRecord(
                    customerId = row.customerId,
                    customerName = row.customerName,
                    phone = row.phone,
                    createdAtEpochMillis = row.lastSaleAt,
                    lastSaleAtEpochMillis = row.lastSaleAt,
                    saleCount = row.saleCount,
                )
            }
        }
}
