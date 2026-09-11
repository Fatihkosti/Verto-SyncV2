from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = []

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def check(name, condition):
    checks.append((name, bool(condition)))

observe = read('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/pendingaction/ObservePendingActionsUseCase.kt')
check('aggregate has no MAX_WORK_ACTIONS cap', 'MAX_WORK_ACTIONS' not in observe and '.take(ObservePendingActionsUseCase' not in observe)
check('snooze does not hide open event', 'snoozedUntilEpochMillis?.let' not in observe and 'dismissedAtEpochMillis != null' in observe)

provider_paths = [
    'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/pendingaction/MaintenancePendingActionProvider.kt',
    'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/pendingaction/InventoryPendingActionProvider.kt',
    'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/pendingaction/FinancialPendingActionProvider.kt',
    'feature/party/src/main/kotlin/com/verto/app/feature/party/application/pendingaction/InactiveCustomerPendingActionProvider.kt',
    'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/pendingaction/ShipmentOperationalPendingActionProvider.kt',
    'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/pendingaction/ShipmentReceiptIssuePendingActionProvider.kt',
]
check('providers have no five-event caps', all('MAX_PROVIDER_ACTIONS' not in read(p) and '.take(MAX_PROVIDER_ACTIONS)' not in read(p) for p in provider_paths))

dao_text = '\n'.join(read(p) for p in [
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt',
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryCatalogReadDao.kt',
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/LogisticsShipmentCoreDao.kt',
])
check('pending DAO fixed LIMIT 5 removed', 'LIMIT 5' not in dao_text)

cards = read('app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionCards.kt')
check('Home shows one highest-ranked work event', 'pendingActions.firstOrNull()' in cards and 'MAX_PENDING_WORK_CARDS: Int = 1' in cards)

home_pending = read('app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActions.kt')
check('Home exposes read more only for remaining events', 'pendingActions.size > 1' in home_pending and 'onReadMoreEvents' in home_pending)

screen = read('app/src/main/kotlin/com/verto/app/ui/screens/home/PendingActionsScreen.kt')
check('all-events screen is LazyColumn', 'LazyColumn(' in screen and 'items(' in screen)
check('all-events screen has close action wiring', 'vm.dismissPendingAction(event.eventKey)' in screen)

nav = read('app/src/main/kotlin/com/verto/app/ui/navigation/HomeClientsNavGraph.kt') + read('app/src/main/kotlin/com/verto/app/ui/navigation/Screen.kt')
check('all-events route is wired', 'HomePendingActions' in nav and 'PendingActionsScreen(' in nav)

strings = read('app/src/main/res/values/strings.xml')
check('dismiss language is close', '<string name="home_pending_hide">إغلاق</string>' in strings)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
print(f'RESULT: {len(checks)-len(failed)}/{len(checks)} PASS')
if failed:
    raise SystemExit(1)
