#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []
checks = []

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def require(name, condition, detail=''):
    checks.append((name, bool(condition), detail))
    if not condition:
        errors.append(name + (f': {detail}' if detail else ''))

observe = text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/pendingaction/ObservePendingActionsUseCase.kt')
home_cards = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionCards.kt')
home_comp = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActionComponents.kt')
tokens = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeDesignTokens.kt')
ship_provider = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/pendingaction/ShipmentOperationalPendingActionProvider.kt')
ship_source = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/data/pendingaction/RoomShipmentOperationalPendingActionSource.kt')
receipt_source = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/data/pendingaction/RoomShipmentReceiptIssuePendingActionSource.kt')
logistics_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/LogisticsShipmentCoreDao.kt')
client_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt')
invoice_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt')
inv_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryCatalogReadDao.kt')
maintenance_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalMaintenanceFollowUpDao.kt')
schema = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')

require('MAX_WORK_ACTIONS == 5', 'const val MAX_WORK_ACTIONS: Int = 5' in observe)
require('Home work cards == 5', 'MAX_PENDING_WORK_CARDS: Int = 5' in home_cards)
require('Education independent of work cap', 'pendingActions.take(MAX_PENDING_WORK_CARDS)' in home_cards and 'educationalContent?.let' in home_cards)
require('Shipment provider no per-row invoke', 'evaluateDelay?.invoke(' not in ship_provider)
require('Shipment pending source no getShipment', '.getShipment(' not in ship_source and 'getShipment(' not in ship_provider)
require('Shipment pending source no listPartners', '.listPartners(' not in ship_source and 'listPartners(' not in ship_provider)
require('Shipment source uses Home projections', all(token in ship_source for token in ['observeHomeOperationalShipments', 'observeHomeOperationalMilestones', 'observeHomeOperationalLegs', 'observeHomeOperationalCustomsPlans']))
require('Receipt source no all shipment combine', 'observeShipments(' not in receipt_source and 'observeShortages(' not in receipt_source)
require('Receipt issues joined and open only', 'INNER JOIN logistics_shipments' in logistics_dao and 'remaining_missing_quantity > 0' in logistics_dao)
require('Inventory service/archive filtered in SQL', 'i.isService = 0' in inv_dao and 'i.is_archived = 0' in inv_dao)
require('Inventory stale cutoff pushed to SQL', 'staleCutoffEpochMillis' in inv_dao and '<= :staleCutoffEpochMillis' in inv_dao)
require('Invoice tenant scoped', 'inv.organization_id = :organizationId' in invoice_dao)
require('Invoice due/void filtering pushed to SQL', 'inv.voided = 0' in invoice_dao and 'inv.dueDate <= :nowEpochMillis' in invoice_dao and "inv.status = 'CLOSED_CREDIT'" in invoice_dao)
require('Customer role tenant scoped', 'pr.organization_id = :organizationId' in client_dao)
require('Customer inactive cutoff pushed to SQL', 'MAX(inv.createdAt) <= :inactiveCutoffEpochMillis' in client_dao)
require('Optimal invoice join tenant scoped', 'i.organization_id = f.organization_id' in maintenance_dao)
require('Touch targets >= 48dp', 'pendingActionButtonHeight = 48.dp' in tokens and 'pendingInlineAction = 48.dp' in tokens)
require('Secondary actions reachable', 'secondaryActions.forEach' in home_comp and 'DropdownMenuItem' in home_comp)
require('Snooze reachable', 'onSnooze()' in home_comp)
require('Dismiss reachable', 'onDismiss()' in home_comp)
require('Large font adaptive card', '.heightIn(min = HomeDesignTokens.pendingActionCardHeight)' in home_comp)
require('CancellationException rethrown', 'if (error is CancellationException) throw error' in observe)
require('Provider failure logged', 'pending_action_provider_failure' in observe and 'providerId=' in observe)
require('Room schema version == 83', re.search(r'ROOM_SCHEMA_VERSION:\s*Int\s*=\s*83', schema) is not None)
require('No migration 83_84 introduced', 'MIGRATION_83_84' not in schema)

# Required tests are present as source even when Android/Gradle runtime is unavailable.
required_tests = [
    'feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/pendingaction/ObservePendingActionsUseCase337Test.kt',
    'feature/inventory/src/test/kotlin/com/verto/app/feature/inventory/application/pendingaction/InventoryPendingActionProvider337Test.kt',
    'feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/pendingaction/FinancialPendingActionProvider337Test.kt',
    'feature/party/src/test/kotlin/com/verto/app/feature/party/application/pendingaction/InactiveCustomerPendingActionProvider337Test.kt',
    'feature/shipment/src/test/kotlin/com/verto/app/feature/shipment/application/pendingaction/ShipmentPendingActionProvider337Test.kt',
    'feature/integration/optimal/src/test/kotlin/com/verto/app/feature/integration/optimal/application/pendingaction/MaintenancePendingActionProvider337Test.kt',
    'data/database/src/androidTest/kotlin/com/verto/app/data/local/HomePendingActionReadModel337Test.kt',
    'app/src/androidTest/kotlin/com/verto/app/ui/screens/home/HomePendingActionCard337Test.kt',
]
for rel in required_tests:
    require(f'test exists: {Path(rel).name}', (ROOT / rel).is_file())

for name, ok, detail in checks:
    print(('PASS' if ok else 'FAIL') + ' | ' + name + (f' | {detail}' if detail else ''))
print(f'\nchecks={len(checks)} failures={len(errors)}')
if errors:
    sys.exit(1)
