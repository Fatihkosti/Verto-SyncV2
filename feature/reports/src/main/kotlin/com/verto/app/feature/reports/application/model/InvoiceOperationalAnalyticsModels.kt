package com.verto.app.feature.reports.application.model

/** F255 analytics are read-only projections over immutable invoice/purchase/logistics facts. */
data class AgingBucketsMinor(
    val days0To30: Long = 0L,
    val days31To60: Long = 0L,
    val days61To90: Long = 0L,
    val daysOver90: Long = 0L,
) {
    val total: Long get() = listOf(days0To30, days31To60, days61To90, daysOver90).fold(0L, Math::addExact)
}

data class AgedPayable(
    val supplierId: String,
    val supplierName: String,
    val buckets: AgingBucketsMinor = AgingBucketsMinor(),
    val oldestDueDays: Int = 0,
    val currencyCode: String = "",
) {
    val bucket0_30Minor: Long get() = buckets.days0To30
    val bucket31_60Minor: Long get() = buckets.days31To60
    val bucket61_90Minor: Long get() = buckets.days61To90
    val bucketOver90Minor: Long get() = buckets.daysOver90
    val totalMinor: Long get() = buckets.total
}

data class AgedPayablesData(
    val buckets: AgingBucketsMinor = AgingBucketsMinor(),
    val dpoDays: Float = 0f,
    val suppliers: List<AgedPayable> = emptyList(),
    val currencyCode: String = "",
) {
    val total0_30Minor: Long get() = buckets.days0To30
    val total31_60Minor: Long get() = buckets.days31To60
    val total61_90Minor: Long get() = buckets.days61To90
    val totalOver90Minor: Long get() = buckets.daysOver90
    val grandTotalMinor: Long get() = buckets.total
}

data class PurchasePriceSnapshot(
    val quantity: Int,
    val poUnitPriceMinor: Long,
    val invoiceUnitPriceMinor: Long,
)

data class PurchasePriceVarianceRow(
    val itemId: String,
    val itemName: String,
    val price: PurchasePriceSnapshot,
    /** Positive = unfavorable (invoice above PO); negative = favorable. */
    val varianceMinor: Long,
    val currencyCode: String,
) {
    val quantity: Int get() = price.quantity
    val poUnitPriceMinor: Long get() = price.poUnitPriceMinor
    val invoiceUnitPriceMinor: Long get() = price.invoiceUnitPriceMinor
}

data class PurchasePriceVarianceGroup(
    val currencyCode: String,
    val favorableMinor: Long,
    val unfavorableMinor: Long,
    val netVarianceMinor: Long,
    val rows: List<PurchasePriceVarianceRow>,
)

data class PurchasePriceVarianceData(
    val groups: List<PurchasePriceVarianceGroup> = emptyList(),
)

data class LandedCostShipmentVariance(
    val shipmentId: String,
    val shipmentNumber: String,
    val estimatedMinor: Long,
    val actualMinor: Long,
    val varianceMinor: Long,
    val currencyCode: String,
)

data class CostVarianceAmounts(
    val estimatedMinor: Long,
    val actualMinor: Long,
) {
    val varianceMinor: Long get() = Math.subtractExact(actualMinor, estimatedMinor)
}

data class LandedCostItemVariance(
    val shipmentId: String,
    val shipmentNumber: String,
    val inventoryItemId: String,
    val itemName: String,
    val amounts: CostVarianceAmounts,
    val currencyCode: String,
) {
    val estimatedAllocationMinor: Long get() = amounts.estimatedMinor
    val actualAllocationMinor: Long get() = amounts.actualMinor
    val varianceMinor: Long get() = amounts.varianceMinor
}

data class LandedCostVarianceData(
    val shipments: List<LandedCostShipmentVariance> = emptyList(),
    val items: List<LandedCostItemVariance> = emptyList(),
    val currencyCode: String = "SDG",
)

data class SupplierFxVarianceRow(
    val supplierId: String,
    val supplierName: String,
    val realizedGainLossMinor: Long,
    val historicalFunctionalMinor: Long,
    val currencyCode: String,
)

data class SupplierFxVarianceGroup(
    val currencyCode: String,
    val totalRealizedGainLossMinor: Long,
    val suppliers: List<SupplierFxVarianceRow>,
)

data class SupplierFxVarianceData(
    val groups: List<SupplierFxVarianceGroup> = emptyList(),
)

data class SupplierPaymentTimingRow(
    val supplierId: String,
    val supplierName: String,
    val settledInvoiceCount: Int,
    val averageDaysToPay: Float,
    val varianceFromPortfolioDays: Float,
)

data class SupplierPaymentTimingData(
    val portfolioAverageDays: Float = 0f,
    val suppliers: List<SupplierPaymentTimingRow> = emptyList(),
)

enum class OperationalAlertSeverity { INFO, WARNING, CRITICAL }
enum class OperationalAlertType {
    DUE,
    PRICE_INCREASE,
    FX_VARIANCE,
    PURCHASE_WITHOUT_MATCHED_RECEIPT,
    CONFLICT_REVIEW,
}

data class OperationalAlertAmount(
    val minor: Long,
    val currencyCode: String,
)

data class OperationalAnalyticsAlert(
    val id: String,
    val type: OperationalAlertType,
    val severity: OperationalAlertSeverity,
    val title: String,
    val message: String,
    val relatedId: String? = null,
    val amount: OperationalAlertAmount? = null
) {
    val amountMinor: Long? get() = amount?.minor
    val currencyCode: String? get() = amount?.currencyCode
}

data class KpiGovernance(
    val currencyPolicy: String,
    val voidPolicy: String,
    val returnsPolicy: String,
)

data class KpiDefinition(
    val code: String,
    val label: String,
    val dataSource: String,
    val formula: String,
    val governance: KpiGovernance,
) {
    val currencyPolicy: String get() = governance.currencyPolicy
    val voidPolicy: String get() = governance.voidPolicy
    val returnsPolicy: String get() = governance.returnsPolicy
}

/** Auditable F255 definitions. No KPI is allowed to silently mix currencies. */
object InvoiceAnalyticsKpiCatalog {
    private fun policy(currency: String, void: String, returns: String) = KpiGovernance(currency, void, returns)

    val definitions: List<KpiDefinition> = listOf(
        KpiDefinition(
            code = "AR_AGING_DSO", label = "A/R Aging + DSO",
            dataSource = "posted credit sales + payment allocations + sales credit notes",
            formula = "period-end open receivable / net credit sales in selected period × period days; aging uses overdue balance as-of period end",
            governance = policy(
                "functional currency only; unknown/mismatched currency is excluded",
                "VOID invoices excluded",
                "sales credit notes reduce receivable and selected-period net credit sales",
            ),
        ),
        KpiDefinition(
            code = "AP_AGING_DPO", label = "A/P Aging + DPO",
            dataSource = "posted credit purchases + payment allocations + purchase debit notes",
            formula = "period-end open payable / net credit purchases in selected period × period days; aging uses overdue balance as-of period end",
            governance = policy(
                "functional currency only; unknown/mismatched currency is excluded",
                "VOID invoices excluded",
                "purchase debit notes reduce payable and selected-period net credit purchases",
            ),
        ),
        KpiDefinition(
            code = "PPV", label = "Purchase Price Variance",
            dataSource = "three-way-match line snapshots",
            formula = "(invoice unit price - PO unit price) × invoiced quantity; positive is unfavorable",
            governance = policy(
                "grouped by immutable PO transaction currency; groups are never summed together",
                "rows whose supplier invoice is VOID are excluded",
                "returns do not rewrite original PPV; they are separate correction facts",
            ),
        ),
        KpiDefinition(
            code = "REALIZED_GROSS_MARGIN", label = "Realized Gross Margin",
            dataSource = "sale line revenue snapshot + immutable unitCostAtSale/lineCostSnapshot",
            formula = "(historical revenue - historical COGS) / historical revenue",
            governance = policy(
                "functional currency only",
                "VOID sales excluded",
                "sales returns reverse revenue and historical COGS through immutable return snapshots",
            ),
        ),
        KpiDefinition(
            code = "CURRENT_REPLACEMENT_MARGIN", label = "Current Replacement Margin",
            dataSource = "historical sale revenue + current InventoryItem.buyPriceMinor (Last Purchase Price)",
            formula = "(historical revenue - current replacement cost) / historical revenue",
            governance = policy(
                "functional currency only",
                "VOID sales excluded",
                "reported separately from realized profit; returns do not mutate historical sale cost",
            ),
        ),
        KpiDefinition(
            code = "LANDED_COST_VARIANCE", label = "Landed Cost Variance",
            dataSource = "logistics ESTIMATED/ACTUAL base-currency costs + immutable landed-cost allocations",
            formula = "actual base cost - estimated base cost; item estimate allocated by accepted purchase-value basis",
            governance = policy(
                "logistics base currency SDG only",
                "cancelled-not-started shipments excluded; started/closed shipment facts retained",
                "returns do not rewrite settled shipment cost; shortage/recovery remains logistics facts",
            ),
        ),
        KpiDefinition(
            code = "REALIZED_FX", label = "Realized FX Gain/Loss",
            dataSource = "payment allocations realizedFxDifferenceMinor",
            formula = "functional cash at settlement - historical functional carrying amount",
            governance = policy(
                "grouped by functional currency; groups are never summed together",
                "VOID invoices excluded; reversal facts carry reversals",
                "returns do not rewrite realized FX; refund/reversal payment facts determine any reversal",
            ),
        ),
        KpiDefinition(
            code = "SUPPLIER_PAYMENT_TIME", label = "Supplier Payment Time",
            dataSource = "supplier invoice recognition + ordered payment allocations + purchase debit notes",
            formula = "days from invoice recognition until chronological payment + debit-note events settle the invoice; fully returned invoices are excluded",
            governance = policy(
                "time KPI; no monetary currencies are aggregated",
                "VOID invoices excluded",
                "purchase debit notes apply only at occurredAt; fully returned invoices are excluded from payment-time averages",
            ),
        ),
    )
}
