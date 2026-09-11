package com.verto.app.feature.reports.presentation.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.verto.app.feature.reports.presentation.ReportsUiState
import com.verto.app.utils.CurrencyFormatter

object WhatsAppShareUtil {

    fun buildReportSummary(state: ReportsUiState): String {
        val period = state.period.label
        val currency = state.functionalCurrencyCode.ifBlank { "—" }
        val changeSign = if (state.salesChange > 0) "▲" else if (state.salesChange < 0) "▼" else ""
        val changePct = if (state.salesChange != 0f) " ($changeSign${kotlin.math.abs(state.salesChange).toInt()}%)" else ""
        fun amount(value: Double) = "${CurrencyFormatter.formatNoSymbol(value)} $currency"
        val netProfitText = if (state.profitBreakdown.isNetProfitReliable) amount(state.netProfit) else "غير متاح — يحتاج بيانات قابلة للمطابقة"
        val grossProfitText = if (state.pnl.isHistoricalCostComplete) amount(state.pnl.grossProfit) else "غير متاح"

        val budgetLine = if (state.budgetTarget > 0)
            "\nالهدف الشهري: ${(state.budgetProgress * 100).toInt()}%" else ""
        val overdueLine = if (state.agedReceivables.grandTotal > 0)
            "\nديون متأخرة: ${amount(state.agedReceivables.grandTotal)}" else ""
        val integrityLine = if (state.financialDiagnostics.isHealthy)
            "\nالمطابقة المالية: سليمة" else "\nالمطابقة المالية: تحتاج مراجعة"
        val international = state.internationalSupplierStatement.firstOrNull()?.let { row ->
            val txn = com.verto.app.money.Money.ofMinor(row.originalAmountMinor, row.transactionCurrencyCode.ifBlank { com.verto.app.money.Money.TRANSACTION_CURRENCY }).toPlainString()
            val local = com.verto.app.money.Money.ofMinor(row.functionalCashPaidMinor, row.functionalCurrencyCode.ifBlank { com.verto.app.money.Money.TRANSACTION_CURRENCY }).toPlainString()
            val fx = com.verto.app.money.Money.ofMinor(row.realizedFxDifferenceMinor, row.functionalCurrencyCode.ifBlank { com.verto.app.money.Money.TRANSACTION_CURRENCY }).toPlainString()
            "\nمورد دولي #${row.invoiceNumber}: $txn ${row.transactionCurrencyCode} | نقد محلي $local ${row.functionalCurrencyCode} | فرق صرف $fx ${row.functionalCurrencyCode}" +
                if (row.invoiceExchangeRateSnapshot.isBlank()) "" else " | سعر ${row.invoiceExchangeRateSnapshot}"
        }.orEmpty()

        val insightsText = if (state.insights.isNotEmpty()) {
            "\n\n" + state.insights.take(2).joinToString("\n") { it.title + ": " + it.message }
        } else ""

        return """
ملخص $period — Verto

المبيعات: ${amount(state.salesTotal)}$changePct
صافي الربح: $netProfitText
مجمل الربح: $grossProfitText${if (state.pnl.isHistoricalCostComplete) " (${state.pnl.grossMargin.toInt()}%)" else ""}
المصروفات: ${amount(state.pnl.operatingExpenses)}$overdueLine$budgetLine$integrityLine$international$insightsText
        """.trimIndent()
    }

    fun shareToWhatsApp(context: Context, state: ReportsUiState) {
        val message = buildReportSummary(state)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            // fallback: WhatsApp Business أو شارك عام
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(fallback, "مشاركة التقرير").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    fun shareAsText(context: Context, state: ReportsUiState) {
        val message = buildReportSummary(state)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
