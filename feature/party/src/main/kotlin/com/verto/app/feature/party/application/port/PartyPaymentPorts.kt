package com.verto.app.feature.party.application.port

import kotlinx.coroutines.flow.Flow

typealias PartyPaymentTarget = com.verto.app.feature.party.application.model.PartyPaymentTarget
typealias PartyPaymentAllocationResult = com.verto.app.feature.party.application.model.PartyPaymentAllocationResult

interface PartyPaymentAllocator {
    suspend operator fun invoke(
        clientId: String,
        clientName: String,
        amount: Double,
        targets: List<PartyPaymentTarget>,
        note: String,
        moneyIn: Boolean,
        rate: Double = 1.0,
        requireSupplierPermission: Boolean = false,
        requestId: String,
    ): PartyPaymentAllocationResult
}

interface PartyCashBalancePort {
    val balance: Flow<Double>
}
