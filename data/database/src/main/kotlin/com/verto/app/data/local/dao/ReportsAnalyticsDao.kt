package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.verto.app.data.local.entity.LogisticsCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsLateCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsReceivingLineEntity
import com.verto.app.data.local.entity.LogisticsShipmentLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceReceiptAllocationEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import kotlinx.coroutines.flow.Flow

/** Read-only projections required by F255 analytics. No financial write is owned here. */
@Dao
interface ReportsAnalyticsDao {
    @Query("SELECT * FROM purchase_orders WHERE organization_id = :organizationId ORDER BY created_at, id")
    fun observePurchaseOrders(organizationId: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_invoice_matches WHERE organization_id = :organizationId ORDER BY matched_at, id")
    fun observePurchaseInvoiceMatches(organizationId: String): Flow<List<PurchaseInvoiceMatchEntity>>

    @Query("SELECT ml.* FROM purchase_invoice_match_lines ml INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id WHERE m.organization_id = :organizationId ORDER BY m.matched_at, m.id, ml.id")
    fun observePurchaseInvoiceMatchLines(organizationId: String): Flow<List<PurchaseInvoiceMatchLineEntity>>

    @Query("SELECT * FROM purchase_invoice_receipt_allocations WHERE organization_id = :organizationId ORDER BY created_at, id")
    fun observePurchaseReceiptAllocations(organizationId: String): Flow<List<PurchaseInvoiceReceiptAllocationEntity>>

    @Query("SELECT * FROM logistics_shipment_lines WHERE organization_id = :organizationId ORDER BY shipment_id, id")
    fun observeShipmentLines(organizationId: String): Flow<List<LogisticsShipmentLineEntity>>

    @Query("SELECT * FROM logistics_cost_allocations WHERE organization_id = :organizationId ORDER BY shipment_id, id")
    fun observeLandedCostAllocations(organizationId: String): Flow<List<LogisticsCostAllocationEntity>>

    @Query("SELECT * FROM logistics_late_cost_allocations WHERE organization_id = :organizationId ORDER BY shipment_id, id")
    fun observeLateCostAllocations(organizationId: String): Flow<List<LogisticsLateCostAllocationEntity>>

    @Query("SELECT * FROM logistics_receiving_lines WHERE organization_id = :organizationId ORDER BY shipment_id, id")
    fun observeReceivingLines(organizationId: String): Flow<List<LogisticsReceivingLineEntity>>
}
