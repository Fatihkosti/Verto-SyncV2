package com.verto.app.utils

import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.utils.MoneyMath.moneySum
import com.verto.app.utils.MoneyMath.multiply
import com.verto.app.utils.MoneyMath.subtract
import com.verto.app.utils.MoneyMath.divide

/**
 * ProfitCalculator — مركز حساب الأرباح
 *
 * ✅ الإصلاح — الثغرة #1:
 * جميع العمليات الحسابية تمر الآن عبر [MoneyMath] الذي يستخدم BigDecimal
 * داخلياً ويُعيد Double مُقرَّباً بدقة خانتين عشريتين HALF_UP.
 *
 * قبل الإصلاح:
 *   (item.sellPrice - item.buyPrice) * item.quantity  ← Double خام
 *   items.sumOf { itemProfit(it) }                    ← تراكم أخطاء التقريب
 *
 * بعد الإصلاح:
 *   MoneyMath.subtract(sell, buy) → MoneyMath.multiply(margin, qty)
 *   كل خطوة مُقرَّبة بشكل مستقل → لا تراكم.
 */
object ProfitCalculator {

    // ✅ بعد
    fun itemProfit(item: InvoiceItemEntity): Double {
        val margin = subtract(item.sellPrice, item.buyPrice).coerceAtLeast(0.0)
        return multiply(margin, item.quantity)
    }

    // ── إجمالي ربح قائمة بنود ─────────────────────────
    fun totalProfit(items: List<InvoiceItemEntity>): Double =
        items.map { itemProfit(it) }.moneySum()

    // ── إجمالي المبيعات من فواتير ─────────────────────
    fun totalSales(invoices: List<InvoiceEntity>): Double =
        invoices
            .filter { it.category == InvoiceCategory.SALE }
            .map { it.totalAmount }
            .moneySum()

    // ── إجمالي المشتريات من فواتير ────────────────────
    fun totalPurchases(invoices: List<InvoiceEntity>): Double =
        invoices
            .filter { it.category == InvoiceCategory.PURCHASE }
            .map { it.totalAmount }
            .moneySum()

    // ── صافي الربح = ربح المبيعات - المصاريف ──────────
    fun netProfit(
        salesItems: List<InvoiceItemEntity>,
        totalExpenses: Double
    ): Double = subtract(totalProfit(salesItems), totalExpenses)

    // ── هامش الربح % ──────────────────────────────────
    fun profitMargin(profit: Double, sales: Double): Float =
        if (sales == 0.0) 0f
        else multiply(divide(profit, sales), 100.0).toFloat()

    // ── تحليل حسب الصنف ───────────────────────────────
    data class ItemProfitSummary(
        val itemName: String,
        val itemCategory: String,
        val totalQty: Int,
        val totalRevenue: Double,
        val totalCost: Double,
        val totalProfit: Double,
        val profitPct: Float
    )

    fun profitByItem(items: List<InvoiceItemEntity>): List<ItemProfitSummary> {
        val totalProfitAll = totalProfit(items).takeIf { it > 0 } ?: 1.0
        return items
            .groupBy { it.itemName }
            .map { (name, group) ->
                val qty     = group.sumOf { it.quantity }
                val revenue = group.map { it.totalPrice }.moneySum()
                val cost    = group.map { multiply(it.buyPrice, it.quantity) }.moneySum()
// ✅ بعد
                // ✅ بعد
                val profit = subtract(revenue, cost).coerceAtLeast(0.0)
                ItemProfitSummary(
                    itemName     = name,
                    itemCategory = group.firstOrNull()?.itemCategory ?: "",
                    totalQty     = qty,
                    totalRevenue = revenue,
                    totalCost    = cost,
                    totalProfit  = profit,
                    profitPct    = multiply(divide(profit, totalProfitAll), 100.0).toFloat()
                )
            }
            .sortedByDescending { it.totalProfit }
    }

    // ── تحليل حسب تصنيف الصنف ─────────────────────────
    data class CategoryProfitSummary(
        val category: String,
        val totalQty: Int,
        val totalRevenue: Double,
        val totalProfit: Double,
        val profitPct: Float
    )

    fun profitByCategory(items: List<InvoiceItemEntity>): List<CategoryProfitSummary> {
        val totalProfitAll = totalProfit(items).takeIf { it > 0 } ?: 1.0
        return items
            .groupBy { it.itemCategory.ifBlank { "غير مصنف" } }
            .map { (cat, group) ->
                val qty     = group.sumOf { it.quantity }
                val revenue = group.map { it.totalPrice }.moneySum()
                val cost    = group.map { multiply(it.buyPrice, it.quantity) }.moneySum()
                val profit  = subtract(revenue, cost)
                CategoryProfitSummary(
                    category     = cat,
                    totalQty     = qty,
                    totalRevenue = revenue,
                    totalProfit  = profit,
                    profitPct    = multiply(divide(profit, totalProfitAll), 100.0).toFloat()
                )
            }
            .sortedByDescending { it.totalProfit }
    }

    // ── تحليل حسب العميل ──────────────────────────────
    data class ClientProfitSummary(
        val clientId: String,
        val clientName: String,
        val totalRevenue: Double,
        val totalProfit: Double,
        val invoiceCount: Int
    )

    fun profitByClient(
        invoices: List<InvoiceEntity>,
        items: List<InvoiceItemEntity>,
        clientNames: Map<String, String>
    ): List<ClientProfitSummary> {
        val itemsByInvoice = items.groupBy { it.invoiceId }
        return invoices
            .filter { it.category == InvoiceCategory.SALE }
            .groupBy { it.clientId }
            .map { (clientId, invList) ->
                val allItems = invList.flatMap { itemsByInvoice[it.id] ?: emptyList() }
                val revenue  = invList.map { it.totalAmount }.moneySum()
                val profit   = totalProfit(allItems)
                ClientProfitSummary(
                    clientId     = clientId,
                    clientName   = clientNames[clientId] ?: "غير معروف",
                    totalRevenue = revenue,
                    totalProfit  = profit,
                    invoiceCount = invList.size
                )
            }
            .sortedByDescending { it.totalProfit }
    }
}
