package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.InsightInput
import com.verto.app.feature.reports.application.model.InsightTarget
import com.verto.app.feature.reports.application.model.InsightType
import com.verto.app.feature.reports.application.model.SmartInsight
import com.verto.app.utils.CurrencyFormatter

class SmartInsightsEngine {

    fun generate(input: InsightInput): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()
        with(input) {
            checkLowSales(insights)
            checkHighExpenseRatio(insights)
            checkOverdueReceivables(insights)
            checkDeadStock(insights)
            checkTopItemGrowth(insights)
            checkBudgetOnTrack(insights)
            checkClientChurn(insights)
            checkForecastGrowth(insights)
        }
        return insights
    }

    private fun InsightInput.checkLowSales(out: MutableList<SmartInsight>) {
        if (avgLast30DaySales <= 0) return
        if (todaySales < avgLast30DaySales * 0.5) {
            out += SmartInsight(
                id = "low_sales",
                icon = "⚠️",
                title = "مبيعات منخفضة",
                message = "مبيعاتك اليوم أقل من نصف المعتاد (${CurrencyFormatter.formatNoSymbol(todaySales)} مقابل ${CurrencyFormatter.formatNoSymbol(avgLast30DaySales)} متوسط)",
                type = InsightType.WARNING,
                actionLabel = "تفاصيل المبيعات",
                actionTarget = InsightTarget.SALES
            )
        }
    }

    private fun InsightInput.checkHighExpenseRatio(out: MutableList<SmartInsight>) {
        if (periodSales <= 0) return
        val ratio = expensesTotal / periodSales
        if (ratio > 0.30) {
            val pct = (ratio * 100).toInt()
            out += SmartInsight(
                id = "high_expenses",
                icon = "💸",
                title = "مصروفات مرتفعة",
                message = "المصروفات تجاوزت ${pct}% من المبيعات — راجع التكاليف",
                type = InsightType.WARNING,
                actionLabel = "تقرير الأرباح والخسائر",
                actionTarget = InsightTarget.FINANCIAL
            )
        }
    }

    private fun InsightInput.checkOverdueReceivables(out: MutableList<SmartInsight>) {
        if (totalCreditAmount <= 0) return
        val ratio = overdueAmount / totalCreditAmount
        if (ratio > 0.40) {
            val pct = (ratio * 100).toInt()
            out += SmartInsight(
                id = "overdue_high",
                icon = "🚨",
                title = "ديون متأخرة",
                message = "${pct}% من ديونك متأخرة — ابدأ التحصيل (${CurrencyFormatter.formatNoSymbol(overdueAmount)})",
                type = InsightType.WARNING,
                actionLabel = "الذمم المدينة",
                actionTarget = InsightTarget.RECEIVABLES
            )
        }
    }

    private fun InsightInput.checkDeadStock(out: MutableList<SmartInsight>) {
        if (deadStockValue > 10_000) {
            out += SmartInsight(
                id = "dead_stock",
                icon = "📦",
                title = "مخزون راكد",
                message = "عندك ${CurrencyFormatter.formatNoSymbol(deadStockValue)} مخزون مش بيتحرك من 90 يوم",
                type = InsightType.WARNING,
                actionLabel = "صحة المخزون",
                actionTarget = InsightTarget.INVENTORY
            )
        }
    }

    private fun InsightInput.checkTopItemGrowth(out: MutableList<SmartInsight>) {
        val topGrowth = topItemGrowthPct
        if (topGrowth > 200 && topItemName.isNotEmpty()) {
            out += SmartInsight(
                id = "top_item_growth",
                icon = "🔥",
                title = "صنف رائج",
                message = "صنف \"$topItemName\" مبيعاته زادت ${topGrowth.toInt()}%  هذه الفترة",
                type = InsightType.OPPORTUNITY,
                actionLabel = "تفاصيل الأصناف",
                actionTarget = InsightTarget.SALES
            )
        }
    }

    private fun InsightInput.checkBudgetOnTrack(out: MutableList<SmartInsight>) {
        if (budgetTarget <= 0) return
        val progress = periodSales / budgetTarget
        when {
            progress >= 1.0 -> out += SmartInsight(
                id = "budget_achieved",
                icon = "🎯",
                title = "هدف محقق!",
                message = "حققت هدف الفترة — مبيعات ${CurrencyFormatter.formatNoSymbol(periodSales)} من ${CurrencyFormatter.formatNoSymbol(budgetTarget)}",
                type = InsightType.ACHIEVEMENT
            )
            progress >= 0.8 -> out += SmartInsight(
                id = "budget_on_track",
                icon = "🎯",
                title = "على المسار",
                message = "أنت على المسار لتحقيق هدف الفترة (${(progress * 100).toInt()}%)",
                type = InsightType.ACHIEVEMENT,
                actionLabel = "عرض الهدف",
                actionTarget = InsightTarget.BUDGET
            )
        }
    }

    private fun InsightInput.checkClientChurn(out: MutableList<SmartInsight>) {
        if (atRiskClientsCount > 0) {
            out += SmartInsight(
                id = "client_churn",
                icon = "💔",
                title = "عملاء في خطر",
                message = "$atRiskClientsCount من عملائك المهمين لم يشتروا منذ فترة — راجع تصنيف العملاء",
                type = InsightType.WARNING,
                actionLabel = "تصنيف العملاء",
                actionTarget = InsightTarget.SALES
            )
        }
    }

    private fun InsightInput.checkForecastGrowth(out: MutableList<SmartInsight>) {
        if (forecastMonthTotal <= 0 || periodSales <= 0) return
        val growthPct = ((forecastMonthTotal - periodSales) / periodSales * 100)
        if (growthPct > 20) {
            out += SmartInsight(
                id = "forecast_growth",
                icon = "📈",
                title = "توقع نمو",
                message = "التوقع: مبيعاتك الشهر القادم ستزيد ${growthPct.toInt()}% (${CurrencyFormatter.formatNoSymbol(forecastMonthTotal)})",
                type = InsightType.OPPORTUNITY,
                actionLabel = "عرض التوقعات",
                actionTarget = InsightTarget.SALES
            )
        }
    }
}
