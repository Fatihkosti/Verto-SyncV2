package com.verto.app.feature.payment.application.port

import com.verto.app.feature.payment.application.model.PurchaseShipmentCreateCommand
import com.verto.app.feature.payment.application.model.PurchaseShipmentOption
import kotlinx.coroutines.flow.Flow

interface PurchaseShipmentGateway {
    fun observeActiveShipments(): Flow<List<PurchaseShipmentOption>>
    suspend fun createShipment(command: PurchaseShipmentCreateCommand)
}
