package com.verto.app.feature.invoice.application.purchase

import com.verto.app.feature.invoice.application.PurchaseCycleCoordinator
import com.verto.app.feature.invoice.domain.model.ClosePurchaseOrderCommand
import com.verto.app.feature.invoice.domain.model.CreatePurchaseOrderCommand
import com.verto.app.feature.invoice.domain.model.GoodsReceiptResult
import com.verto.app.feature.invoice.domain.model.PurchaseOrderResult
import com.verto.app.feature.invoice.domain.model.RecordGoodsReceiptCommand
import javax.inject.Inject

class CreatePurchaseOrderUseCase @Inject constructor(private val coordinator: PurchaseCycleCoordinator) {
    suspend operator fun invoke(command: CreatePurchaseOrderCommand): PurchaseOrderResult = coordinator.createOrder(command)
}

class RecordGoodsReceiptUseCase @Inject constructor(private val coordinator: PurchaseCycleCoordinator) {
    suspend operator fun invoke(command: RecordGoodsReceiptCommand): GoodsReceiptResult = coordinator.receive(command)
}

class ClosePurchaseOrderUseCase @Inject constructor(private val coordinator: PurchaseCycleCoordinator) {
    suspend operator fun invoke(command: ClosePurchaseOrderCommand) = coordinator.close(command)
}
