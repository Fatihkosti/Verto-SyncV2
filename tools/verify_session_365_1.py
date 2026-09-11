#!/usr/bin/env python3
from pathlib import Path
import json, sys

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsScreen.kt'
FILTERS = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsFilters.kt'
SHEET = ROOT / 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/AdvancedFiltersSheet.kt'
STRINGS = ROOT / 'feature/reports/src/main/res/values/strings.xml'
texts = {p: p.read_text(encoding='utf-8') for p in [SCREEN, FILTERS, SHEET, STRINGS]}
checks=[]
def check(name, ok): checks.append({'name':name,'pass':bool(ok)})
S=texts[SCREEN]
check('viewing_temporarily_full_for_all', 'val accessLevel = ReportAccessLevel.FULL' in S)
check('legacy_permission_drilldown_gate_removed', 'val allowed = when (accessLevel)' not in S and 'reportAccessLevel(permissions)' not in S)
check('all_detail_targets_open_directly', 'fun openDetail(target: ReportDecisionTarget) {\n        detailTargetName = target.name\n    }' in S)
check('sales_filter_button_scoped', 'if (detailTarget == ReportDecisionTarget.SALES) {' in S and 'showFiltersSheet = true' in S)
check('sales_filter_chips_scoped', S.count('if (detailTarget == ReportDecisionTarget.SALES) {') >= 2 and 'ActiveFilterChipsRow(' in S)
check('sales_filter_sheet_scoped', 'if (showFiltersSheet && detailTarget == ReportDecisionTarget.SALES)' in S)
check('filters_clear_on_leave_sales', 'detailTarget != ReportDecisionTarget.SALES && filters.isActive' in S and 'vm.clearFilters()' in S)
check('filters_clear_on_back', 'if (filters.isActive) vm.clearFilters()' in S)
check('period_remains_global', 'PeriodSelectorPill(' in S and 'onSelect = { vm.selectPeriod(it) }' in S)
check('filter_contract_documented', 'NOT global dashboard filters' in texts[FILTERS])
check('sales_filter_title_resource', 'reports_v365_1_sales_filters_title' in texts[SHEET] and 'فلاتر المبيعات' in texts[STRINGS])
passed=sum(1 for c in checks if c['pass'])
result={'session':'365-1','passed':passed,'total':len(checks),'status':'PASS_STATIC' if passed==len(checks) else 'FAIL','checks':checks}
out=ROOT/'docs/architecture/verification/SESSION_365_1_STATIC_GATE.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
sys.exit(0 if result['status']=='PASS_STATIC' else 1)
