package com.verto.app.feature.party.bridge

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.GoodsReceiptEntity
import com.verto.app.data.local.entity.GoodsReceiptLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import com.verto.app.data.local.entity.PurchaseOrderLineEntity
import com.verto.app.feature.party.application.port.SupplierIntelligenceSource
import com.verto.app.feature.party.application.port.SupplierOrderEvidence
import com.verto.app.feature.party.application.port.SupplierOrderLineEvidence
import com.verto.app.feature.party.application.port.SupplierPriceEvidence
import com.verto.app.feature.party.application.port.SupplierReceiptEvidence
import com.verto.app.feature.party.application.port.SupplierReturnEvidence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class AppSupplierIntelligenceSource @Inject constructor(
    database: AppDatabase,
) : SupplierIntelligenceSource {
    private val dao = database.purchaseCycleDao()

    override fun observeSupplierOrders(supplierId: String): Flow<List<SupplierOrderEvidence>> =
        combine(
            dao.observeOrdersForSupplierIntelligence(supplierId),
            dao.observeOrderLinesForSupplierIntelligence(supplierId),
            dao.observeReceiptsForSupplierIntelligence(supplierId),
            dao.observeReceiptLinesForSupplierIntelligence(supplierId),
            dao.observeMatchLinesForSupplierIntelligence(supplierId),
        ) { orders, lines, receipts, receiptLines, matches ->
            assemble(orders, lines, receipts, receiptLines, matches)
        }.combine(dao.observePurchaseReturnsForSupplierIntelligence(supplierId)) { orders, returns ->
            attachReturns(orders, returns.map { row ->
                row.purchaseOrderId to SupplierReturnEvidence(
                    id = row.returnId,
                    inventoryItemId = row.inventoryItemId,
                    quantity = row.quantity,
                    occurredAt = row.occurredAt,
                )
            })
        }

    override fun observeItemOrders(inventoryItemId: String): Flow<List<SupplierOrderEvidence>> =
        combine(
            dao.observeOrdersForSupplierItemIntelligence(inventoryItemId),
            dao.observeOrderLinesForSupplierItemIntelligence(inventoryItemId),
            dao.observeReceiptsForSupplierItemIntelligence(inventoryItemId),
            dao.observeReceiptLinesForSupplierItemIntelligence(inventoryItemId),
            dao.observeMatchLinesForSupplierItemIntelligence(inventoryItemId),
        ) { orders, lines, receipts, receiptLines, matches ->
            assemble(orders, lines, receipts, receiptLines, matches)
        }.combine(dao.observePurchaseReturnsForSupplierItemIntelligence(inventoryItemId)) { orders, returns ->
            attachReturns(orders, returns.map { row ->
                row.purchaseOrderId to SupplierReturnEvidence(
                    id = row.returnId,
                    inventoryItemId = row.inventoryItemId,
                    quantity = row.quantity,
                    occurredAt = row.occurredAt,
                )
            })
        }

    private fun attachReturns(
        orders: List<SupplierOrderEvidence>,
        returns: List<Pair<String, SupplierReturnEvidence>>,
    ): List<SupplierOrderEvidence> {
        val byOrder = returns.groupBy({ it.first }, { it.second })
        return orders.map { order -> order.copy(returns = byOrder[order.orderId].orEmpty()) }
    }

    private fun assemble(
        orders: List<PurchaseOrderEntity>,
        lines: List<PurchaseOrderLineEntity>,
        receipts: List<GoodsReceiptEntity>,
        receiptLines: List<GoodsReceiptLineEntity>,
        matches: List<PurchaseInvoiceMatchLineEntity>,
    ): List<SupplierOrderEvidence> {
        val linesByOrder = lines.groupBy { it.purchaseOrderId }
        val receiptById = receipts.associateBy { it.id }
        val receiptLinesByOrderLine = receiptLines.groupBy { it.purchaseOrderLineId }
        val matchesByOrderLine = matches.groupBy { it.purchaseOrderLineId }

        return orders.map { order ->
            val orderLines = linesByOrder[order.id].orEmpty()
            SupplierOrderEvidence(
                orderId = order.id,
                supplierId = order.supplierId,
                currencyCode = order.currencyCode.trim().uppercase(),
                status = order.status,
                createdAt = order.createdAt,
                promisedDeliveryAt = order.promisedDeliveryAt,
                lines = orderLines.map { line ->
                    SupplierOrderLineEvidence(
                        id = line.id,
                        inventoryItemId = line.inventoryItemId,
                        orderedQuantity = line.orderedQuantity,
                        poUnitPriceMinor = line.unitPriceMinor,
                    )
                },
                receipts = orderLines.flatMap { line ->
                    receiptLinesByOrderLine[line.id].orEmpty().mapNotNull { receiptLine ->
                        val receipt = receiptById[receiptLine.goodsReceiptId] ?: return@mapNotNull null
                        SupplierReceiptEvidence(
                            id = receiptLine.id,
                            orderLineId = line.id,
                            receivedAt = receipt.receivedAt,
                            receivedQuantity = receiptLine.receivedQuantity,
                            acceptedQuantity = receiptLine.acceptedQuantity,
                            rejectedQuantity = receiptLine.rejectedQuantity,
                            unitCostMinor = receiptLine.unitCostMinor,
                        )
                    }
                },
                priceMatches = orderLines.flatMap { line ->
                    matchesByOrderLine[line.id].orEmpty().map { match ->
                        SupplierPriceEvidence(
                            id = match.id,
                            orderLineId = line.id,
                            invoicedQuantity = match.invoicedQuantity,
                            poUnitPriceMinor = match.poUnitPriceMinor,
                            invoiceUnitPriceMinor = match.invoiceUnitPriceMinor,
                        )
                    }
                },
            )
        }
    }
}
