#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile
import hashlib, re, sys

ROOT = Path(__file__).resolve().parents[2]
BASELINE = Path(sys.argv[1]) if len(sys.argv) > 1 else None
results=[]
def check(name, cond, detail=''):
    results.append((name,bool(cond),detail))
    print(('PASS' if cond else 'FAIL'), name, detail)
def text(rel): return (ROOT/rel).read_text()

def has(rel,*parts):
    s=text(rel); return all(p in s for p in parts)

observe='feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/activityevent/ObserveActivityEventsUseCase.kt'
check('fresh current-time at merge', has(observe,'currentTimeProvider: () -> Long','val currentNow = currentTimeProvider()','occurredAtEpochMillis in sinceEpochMillis..nowEpochMillis'))
check('7-day window preserved', has(observe,'ACTIVITY_WINDOW_DAYS: Long = 7L'))
check('Home output bounded at 100', has(observe,'MAX_HOME_ACTIVITY_EVENTS: Int = 100','.take(ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS)'))
check('deterministic ranking', has(observe,'compareByDescending<ActivityEventContribution>','thenBy(ActivityEventContribution::providerId)','thenBy { contribution -> contribution.event.eventKey }'))
check('provider diagnostics and cancellation', has(observe,'if (error is CancellationException) throw error','ActivityEventFailureDiagnostics.record(providerId, error)','emit(emptyList())'))

queries={
'Invoice':'data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt',
'Payment':'data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt',
'Inventory':'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryMovementDao.kt',
'Party':'data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt',
'Shipment':'data/database/src/main/kotlin/com/verto/app/data/local/dao/LogisticsShipmentCoreDao.kt',
'Optimal':'data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalMaintenanceReadDao.kt',
'Audit':'data/database/src/main/kotlin/com/verto/app/data/local/dao/AuditLogDao.kt',
}
check('invoice tenant query', has(queries['Invoice'],'inv.organization_id = :organizationId','inv.createdAt >= :sinceEpochMillis','LIMIT :limit'))
check('payment tenant through invoice', has(queries['Payment'],'i.organization_id = :organizationId','p.paidAt >= :sinceEpochMillis','LIMIT :limit'))
check('inventory tenant + canonical timestamp', has(queries['Inventory'],'movement.organization_id = :organizationId','COALESCE(movement.occurred_at, movement.createdAt)','LIMIT :limit'))
check('party tenant ownership by role', has(queries['Party'],'pr.organization_id = :organizationId','pr.created_at AS roleCreatedAt','pr.role IN (\'CUSTOMER\', \'SUPPLIER\')','LIMIT :limit'))
shipment_source='feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/data/activityevent/RoomShipmentActivityEventSource.kt'
check('shipment no all-history activity read', 'observeShipments(' not in text(shipment_source) and has(shipment_source,'observeActivityShipments(organizationId, sinceEpochMillis, limit)'))
check('shipment lightweight bounded DAO', has(queries['Shipment'],'fun observeActivityShipments(','created_at >= :sinceEpochMillis','LIMIT :limit'))
check('optimal bounded tenant DAO', has(queries['Optimal'],'fun observeActivityRecords(','maintenance.organization_id = :organizationId','maintenance.created_at >= :sinceEpochMillis','LIMIT :limit'))
check('audit invoice ownership + canonical void', has(queries['Audit'],'i.organization_id = :organizationId',"a.sourceType = 'INVOICE_VOID'",'LIMIT :limit'))
audit_provider='app/src/main/kotlin/com/verto/app/core/audit/activityevent/AuditActivityEventProvider.kt'
check('void maps UPDATE INVOICE_VOID independently of DELETE', has(audit_provider,'sourceType != INVOICE_VOID_SOURCE && action != AuditActivityAction.DELETE','ActivityEventStatus.CANCELLED'))
check('void semantic dedupe key', has(audit_provider,'writeId.trim().ifEmpty { sourceId.trim() }.ifEmpty { recordId }'))
check('legacy inventory audit fail-closed', 'EVENT_PRICE_CHANGED' not in text(audit_provider), 'unowned price audit intentionally excluded')

providers=[
'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/activityevent/InvoiceActivityEventProvider.kt',
'feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/activityevent/PaymentActivityEventProvider.kt',
'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/activityevent/InventoryActivityEventProvider.kt',
'feature/party/src/main/kotlin/com/verto/app/feature/party/application/activityevent/PartyActivityEventProvider.kt',
'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/activityevent/ShipmentActivityEventProvider.kt',
'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/activityevent/MaintenanceActivityEventProvider.kt',
audit_provider,
]
check('all provider reads bounded to 100', all('SOURCE_LIMIT' in text(p) and '100' in text(p) for p in providers))
check('single-permission providers gate before source', all(text(p).index('!context.allows(') < text(p).index('source.observeSince') for p in providers[2:6]))
check('invoice/payment no-permission gate before source', all('!context.allows(HomePermissionKeys.SALES_VIEW) && !context.allows(HomePermissionKeys.PURCHASES_VIEW)' in text(p) and text(p).index('!context.allows(HomePermissionKeys.SALES_VIEW)') < text(p).index('source.observeSince') for p in providers[:2]))

policy='app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeedPolicy.kt'
vm='app/src/main/kotlin/com/verto/app/ui/screens/home/HomeViewModel.kt'
feed='app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeed.kt'
check('click-time permission and tenant recheck', has(policy,'event.organizationId != context.organizationId','!event.isAllowedBy(context)','isValidActivityDestination'))
check('ViewModel uses current permission context at click', has(vm,'val context = homePermissionContext.value ?: return rejectActivityEvent()','resolveVisibleActivityEventDestination(activityEvents.value, eventKey, context)'))
check('hourly lower-bound cadence', has(vm,'ACTIVITY_WINDOW_BOUNDARY_MILLIS: Long = 60L * 60L * 1_000L'))
check('relative time section clock', has(feed,'rememberActivityRelativeTimeTick()','RELATIVE_TIME_REFRESH_MILLIS = 60_000L','repeatOnLifecycle(Lifecycle.State.STARTED)','relativeTimeTickEpochMillis'))
check('relative tick does not touch provider reload', 'observeActivityEvents' not in text(feed) and 'Dao' not in text(feed))

schema=text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
check('Room schema remains 83', 'ROOM_SCHEMA_VERSION: Int = 83' in schema)
check('no Session 339 migration marker', '339' not in schema)
check('HomeDesignTokens unchanged hash', hashlib.sha256((ROOT/'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeDesignTokens.kt').read_bytes()).hexdigest()=='1c13fdc0b5bb3e30dd95e5d736958a04af88f4034851b521f6ed1823a17983ba')
check('visual structure preserved', all(x in text(feed) for x in ['LazyColumn(','HomeDesignTokens.activityCardHeight','RoundedCornerShape(VertoRadius.lg)','event.kind.icon()','event.kind.tint()','VertoStroke.thin','HomeActivityEmptyState']))

for rel in [
'feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/activityevent/ActivityEventHardening339Test.kt',
'app/src/test/kotlin/com/verto/app/ui/screens/home/HomeActivityFeedPolicy339Test.kt']:
    check(f'test added {Path(rel).name}', (ROOT/rel).exists())

if BASELINE and BASELINE.exists():
    with ZipFile(BASELINE) as z:
        prefix=next(n.split('/')[0] for n in z.namelist() if '/' in n)
        baseline={n[len(prefix)+1:]: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/') and n.startswith(prefix+'/')}
    current={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in ROOT.rglob('*') if p.is_file()}
    changed=sorted(k for k,v in current.items() if baseline.get(k)!=v)
    added=sorted(k for k in current if k not in baseline)
    allowed_prefixes=(
        'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/activityevent/',
        'feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/activityevent/',
        'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/',
        'feature/payment/src/main/kotlin/com/verto/app/feature/payment/',
        'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/',
        'feature/party/src/main/kotlin/com/verto/app/feature/party/',
        'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/',
        'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/',
        'app/src/main/kotlin/com/verto/app/core/audit/activityevent/',
        'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeViewModel.kt',
        'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeedPolicy.kt',
        'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeed.kt',
        'app/src/test/kotlin/com/verto/app/ui/screens/home/HomeActivityFeedPolicy339Test.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryMovementDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/LogisticsShipmentCoreDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalMaintenanceReadDao.kt',
        'data/database/src/main/kotlin/com/verto/app/data/local/dao/AuditLogDao.kt',
        'tools/quality/session339_verify.py',
        'VERTO_SESSION_339_VERIFICATION.md',
    )
    unexpected=[p for p in changed if not any(p.startswith(a) for a in allowed_prefixes)]
    deleted=sorted(set(baseline)-set(current))
    check('parallel-session scope safety', not unexpected and not deleted, f'unexpected={unexpected}; deleted={deleted}')
    print('CHANGED_FILES', *changed, sep='\n- ')

failed=[n for n,ok,_ in results if not ok]
print(f'RESULT: {len(results)-len(failed)}/{len(results)} PASS')
if failed:
    print('FAILED:', ', '.join(failed)); sys.exit(1)
