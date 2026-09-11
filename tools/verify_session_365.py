#!/usr/bin/env python3
from pathlib import Path
import json, sys

ROOT = Path(__file__).resolve().parents[1]
DASH = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsDecisionDashboard.kt'
SCREEN = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsScreen.kt'
VM = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsViewModel.kt'
MODEL = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsModels.kt'
READ = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsReadModel.kt'
ADAPTER = ROOT / 'data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelQueryAdapter.kt'

texts = {}
for p in [DASH, SCREEN, VM, MODEL, READ, ADAPTER]:
    texts[p] = p.read_text(encoding='utf-8') if p.exists() else ''

def has(p, *parts):
    t = texts.get(p, '')
    return all(x in t for x in parts)

def absent(p, *parts):
    t = texts.get(p, '')
    return all(x not in t for x in parts)

checks = []
def check(name, ok, detail=''):
    checks.append({'name': name, 'pass': bool(ok), 'detail': detail})

# Decision dashboard contract
check('decision_dashboard_exists', DASH.exists())
for label in ['صافي المبيعات','الربح والهامش','صافي التدفق النقدي','متأخرات العملاء','مخاطر المخزون','المشتريات']:
    check('kpi_' + label, has(DASH, f'label = "{label}"'))
check('two_size_hierarchy', has(DASH, 'ReportsDimensions.dp120', 'ReportsDimensions.dp96'))
check('no_gradient_in_dashboard', absent(DASH, 'Brush.', 'gradient'))
check('fixed_top3_attention', has(DASH, 'buildAttentionItems(state).take(3)'))
check('no_auto_carousel', absent(DASH, 'Carousel', 'LaunchedEffect'))
check('purchase_comparison_is_neutral', has(DASH, 'ارتفاع المشتريات ليس جيدًا أو سيئًا بذاته'))
check('purchase_no_fake_previous_zero', has(DASH, 'لا توجد مقارنة سابقة'))
check('freshness_visible', has(DASH, 'آخر تحديث للعرض'))

# Drill-down/navigation contract
for target in ['SALES','PROFIT','CASH_FLOW','RECEIVABLES','INVENTORY','PURCHASES']:
    check('drilldown_' + target.lower(), has(SCREEN, f'ReportDecisionTarget.{target}'))
check('preserve_dashboard_scroll', has(SCREEN, 'val dashboardListState = rememberLazyListState()', 'state = if (detailTarget == null) dashboardListState else detailListState'))
check('custom_range_preserved', has(SCREEN, 'onCustomRange = { from, to -> vm.setCustomRange(from, to) }'))
check('filters_preserved', has(SCREEN, 'AdvancedFiltersSheet', 'ActiveFilterChipsRow'))
check('refresh_preserved', has(SCREEN, 'vm.refresh()'))
check('purchase_drilldown_has_suppliers', has(SCREEN, 'SuppliersAnalysisCard', 'AgedPayablesCard', 'InternationalSupplierStatementCard'))

# Number semantics
check('net_sales_separate_from_gross', has(READ, 'val netSalesTotal: Double', 'val netSalesChange: Float'))
check('net_sales_mapped_from_net_sales_minor', has(ADAPTER, 'netSalesTotal = major(netSalesMinor', 'netSalesChange = netSalesChange'))
check('net_sales_previous_same_method', has(ADAPTER, 'val prevNetSalesMinor = Math.subtractExact(prevSalesMinor, prevReturnsMinor)', 'calcChangeMinor(netSalesMinor, prevNetSalesMinor)'))
check('purchase_current_period_source', has(ADAPTER, 'val currentPurchaseAmounts = p2.purchases.map'))
check('purchase_previous_period_source', has(ADAPTER, 'val previousPurchaseAmounts = prev.purchases.map'))
check('purchase_comparison_guard', has(ADAPTER, 'previousPurchasesTotalMinor != 0L'))
check('purchase_not_payables_in_kpi_calc', 'agedPayables' not in texts[ADAPTER][texts[ADAPTER].find('// Session 365: purchase summary'):texts[ADAPTER].find('val periodDays')])
check('last_updated_state', has(MODEL, 'val lastUpdatedAt: Long = 0L'))
check('last_updated_mapped', has(VM, 'lastUpdatedAt = System.currentTimeMillis()'))

# Legacy removal / read-only report boundary
legacy_paths = [
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/tabs/FinancialTab.kt',
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/tabs/OperationsTab.kt',
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt',
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/SmartInsightCarousel.kt',
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/CashReconciliationCard.kt',
 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/bottomsheets/CashCountSheet.kt',
]
check('legacy_files_removed', all(not (ROOT / p).exists() for p in legacy_paths))
check('no_shift_write_from_reports_presentation', all(x not in '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation').rglob('*.kt')) for x in ['vm.startShift(', 'vm.closeShift(', 'onStartShift', 'onCloseShift']))
check('legacy_tab_navigation_removed', not (ROOT/'data/operations/src/main/kotlin/com/verto/app/data/model/ReportsPermissionRules.kt').exists() and 'ReportTab' not in (ROOT/'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsAccessModels.kt').read_text(encoding='utf-8'))

passed = sum(1 for c in checks if c['pass'])
result = {'session':365, 'passed':passed, 'total':len(checks), 'status':'PASS_STATIC' if passed == len(checks) else 'FAIL', 'checks':checks}
out = ROOT / 'docs/architecture/verification/SESSION_365_STATIC_GATE.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
sys.exit(0 if result['status'] == 'PASS_STATIC' else 1)
