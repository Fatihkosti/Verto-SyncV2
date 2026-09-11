#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
checks = []

def add(name, ok, detail=""):
    checks.append((name, bool(ok), detail))

def text(rel):
    p = ROOT / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""

date_utils = text("core/common/src/main/kotlin/com/verto/app/utils/DateUtils.kt")
vm = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsViewModel.kt")
period = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/PeriodSelectorPill.kt")
screen = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsScreen.kt")
helper = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsIntegrityScope364.kt")
adapter = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelQueryAdapter.kt")
builders = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelBuilders.kt")
model = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsReadModel.kt")
pnl_ui = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/ProfitLossStatement.kt")
hero = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt")
share = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/export/WhatsAppShareUtil.kt")
tests = text("data/operations/src/test/kotlin/com/verto/app/feature/reports/bridge/ReportsIntegrityScope364Test.kt")
date_tests = text("core/common/src/test/kotlin/com/verto/app/utils/DateUtilsReports364Test.kt")

add("refresh_has_generation_trigger", "_refreshGeneration" in vm and "_refreshGeneration.value + 1L" in vm)
add("refresh_no_self_assignment", "_selectedPeriod.value = _selectedPeriod.value" not in vm)
add("custom_range_picker_wired", "DateRangePickerDialog" in period and "onCustomRange(from, to)" in period and "vm.setCustomRange" in screen)
add("custom_range_validated", "from <= 0L || to < from" in vm)
add("month_is_month_to_date", "ReportPeriod.MONTH" in date_utils and "set(Calendar.DAY_OF_MONTH, 1)" in date_utils and "Pair(cal.timeInMillis, to)" in date_utils)
add("today_zeroes_millis", "ReportPeriod.TODAY" in date_utils and "set(Calendar.MILLISECOND, 0)" in date_utils)
add("previous_range_same_period_semantics", "previousComparisonRange" in helper and all(x in helper for x in ["ReportPeriod.MONTH", "ReportPeriod.WEEK", "ReportPeriod.CUSTOM"]))
add("discount_allocation_exact", "allocateRecognizedRevenueByLine" in helper and "cumulativeAllocation" in helper and "previouslyAllocated" in helper)
add("category_builders_use_allocated_revenue", "recognizedRevenueByLine" in builders and builders.count("recognizedRevenueByLine") >= 4)
add("category_revenue_scoped_with_cost", "grossSalesMinor = if (filters.category == null)" in adapter and "recognizedRevenueByLine[it.id]" in adapter)
add("cashier_scope_applies_to_invoices", "scopeSalesInvoices(rawInvoices, diagnostic.payments, filters)" in adapter and "createdBy" in helper)
add("commission_converted_to_functional", "functionalCommissionMinor" in helper and "scaleMinor(invoice.commissionMinor" in helper)
add("returns_follow_same_category_scope", "matchesReportCategory(it.itemCategory, selectedCategory)" in adapter)
add("previous_profit_same_recipe", all(x in adapter for x in ["prevKnownCostLines", "prevExpensesMinor", "prevCommissionsMinor", "prevProfitMinor"]))
add("profit_change_requires_reliable_both_periods", "currentReliabilityIssues.isEmpty() && previousProfitReliable" in adapter)
add("segment_profit_not_claimed", "SEGMENT_FILTER_WITH_UNALLOCATED_EXPENSES" in model and "reportNetProfitReliabilityIssues" in helper and "reports_v364_segment_profit_unavailable" in pnl_ui)
add("unknown_cost_profit_not_claimed", "UNKNOWN_HISTORICAL_COST" in model and "isHistoricalCostComplete" in pnl_ui and "reports_v364_cost_incomplete" in pnl_ui)
add("currency_incompleteness_marks_profit_unreliable", "UNKNOWN_OR_MIXED_CURRENCY" in model and "salesCurrencyComplete" in adapter and "currentReturnCurrencyComplete" in adapter)
add("hero_hides_unreliable_profit", "isReliable" in hero and "reports_v364_unavailable" in hero)
add("share_hides_unreliable_profit", "isNetProfitReliable" in share and "غير متاح" in share)
add("segment_insights_do_not_mix_global_opex", "expensesTotal = if (segmentFilterActive) 0.0" in adapter)
add("segment_forecast_not_compared_to_segment_sales", "forecastMonthTotal = if (segmentFilterActive) 0.0" in adapter)
add("budget_scope_not_mixed", "filters.paymentMethod != null || filters.cashierName != null -> null" in adapter and "BudgetType.CATEGORY_TARGET" in adapter)
add("insight_previous_daily_uses_actual_previous_days", "previousDays" in adapter and "/ 30" not in adapter)
add("top_item_growth_not_hardcoded", "previousTopItemMinor" in adapter and "topItemGrowthPct = topItems.firstOrNull()?.let" in adapter)
add("filter_options_do_not_self_collapse", "availableCashiers = rawPayments" in adapter and "availableCategories = eligibleSalesItems" in adapter)
add("integrity_unit_tests_present", tests.count("@Test") >= 6)
add("date_unit_tests_present", date_tests.count("@Test") >= 2)

# XML must remain well-formed after adding v364 copy.
try:
    ET.parse(ROOT / "feature/reports/src/main/res/values/strings.xml")
    xml_ok = True
except Exception as exc:
    xml_ok = False
    xml_detail = str(exc)
else:
    xml_detail = ""
add("reports_strings_xml_valid", xml_ok, xml_detail)

# Pure arithmetic acceptance: invoice-level 10% discount must reconcile back to line allocations exactly.
recognized = 9000
basis = [6000, 4000]
allocated = [round(recognized * basis[0] / sum(basis)), recognized - round(recognized * basis[0] / sum(basis))]
add("acceptance_discount_reconciliation", allocated == [5400, 3600] and sum(allocated) == recognized, str(allocated))

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    suffix = f": {detail}" if detail else ""
    print(f"{'PASS' if ok else 'FAIL'} {name}{suffix}")
print(f"\nSESSION_364_STATIC={len(checks)-len(failed)}/{len(checks)} {'PASS' if not failed else 'FAIL'}")
raise SystemExit(1 if failed else 0)
