package com.verto.app.feature.party.application.query

import androidx.paging.PagingData
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.model.SupplierScope
import kotlinx.coroutines.flow.Flow

/** Android paging belongs to Application, never to the pure Domain contracts. */
interface PartyPagingGateway {
    fun pagedClientSummaries(
        query: String,
        showSuppliers: Boolean,
        supplierScope: SupplierScope? = null,
    ): Flow<PagingData<PartyClientSummary>>
}
