#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
checks = []

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def require(ok, code, detail):
    checks.append((bool(ok), code, detail))

manager = text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt')
worker = text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt')
realtime = text('data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt')
viewmodel = text('app/src/main/kotlin/com/verto/app/feature/sync/presentation/SyncViewModel.kt')
home = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeViewModel.kt')
application = text('app/src/main/kotlin/com/verto/app/VertoApplication.kt')
auth = text('app/src/main/kotlin/com/verto/app/feature/auth/integration/DefaultAuthSessionCoordinator.kt')
settings = text('app/src/main/kotlin/com/verto/app/feature/settings/bridge/SettingsOperationsGatewayAdapter.kt')
invoice = text('app/src/main/kotlin/com/verto/app/feature/invoice/bridge/InvoicePresentationBridge.kt')
party = text('feature/party/src/main/kotlin/com/verto/app/feature/party/data/PartyPresentationAdapter.kt')
health = text('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/SyncHealthSnapshot.kt')
reliability = text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncReliability.kt')
tests = text('data/sync/src/test/kotlin/com/verto/app/data/sync/SyncManagerTest.kt')

require('suspend fun request(reason:' in manager and 'orchestration.persistRequest(scope)' in manager,
        'M07_DURABLE_REQUEST_CONTRACT', 'public request persists generation before wake')
require('requestOrRun' not in manager and 'usesV2Orchestration' not in manager,
        'M07_NO_LEGACY_ROUTER', 'ambiguous runtime router removed')
require('manager.drainOrchestration(scope)' in worker and 'fullSync(' not in worker and 'usesV2Orchestration' not in worker,
        'M07_WORKER_V2_ONLY', 'worker always enters V2 coordinator')
require('ensurePeriodicIntent(scope)' in worker,
        'M07_PERIODIC_DURABLE_INTENT', 'periodic invocation records a generation')
require('SyncRequestReason.REALTIME' in realtime and 'requestSync(scope' in realtime,
        'M07_REALTIME_V2', 'realtime is an accelerator into durable V2 request')
require('SyncRequestReason.FOREGROUND' in application,
        'M07_FOREGROUND_V2', 'application foreground transition records V2 request')
require('syncManager.request(SyncRequestReason.FOREGROUND)' in home and 'syncManager.request(SyncRequestReason.MANUAL)' in home,
        'M07_HOME_V2', 'home startup/refresh route to V2 request contract')
require('syncOperations.requestSync()' in viewmodel and 'تم تسجيل طلب المزامنة' in viewmodel and 'تمت المزامنة بنجاح' not in viewmodel,
        'M07_UI_ACCEPTANCE_NOT_COMPLETION', 'UI distinguishes request acceptance from completion')
require('syncManager.request(SyncRequestReason.OUTBOX_WRITE)' in invoice,
        'M07_INVOICE_V2', 'invoice compatibility trigger routes to V2 durable request')
require('syncManager.request(SyncRequestReason.OUTBOX_WRITE)' in party,
        'M07_PARTY_V2', 'party compatibility trigger routes to V2 durable request')
require('syncManager.request(SyncRequestReason.MANUAL)' in settings and 'ensureSessionCanEnd()' in settings,
        'M07_SETTINGS_AND_LOGOUT', 'settings uses V2 and logout checks unconfirmed work')
require('SyncRequestReason.STARTUP' in auth and 'syncManager.prepareSessionForOrg' in auth,
        'M07_SESSION_START', 'auth start/restore routes into scoped V2 coordinator')
require('hasUnconfirmedWork(lastOrgId)' in manager and 'SYNC_PENDING_ORG_SWITCH' in manager,
        'M07_SCOPE_SWITCH_FAIL_CLOSED', 'organization switch preserves unconfirmed old-scope work')
require('SYNC_PENDING_SESSION_END' in manager and 'orchestration.cancelAll()' in manager,
        'M07_LOGOUT_FAIL_CLOSED', 'session end cannot silently clear unconfirmed V2 work')
require(all(k in reliability for k in ['pendingCount', 'requiresReviewCount', 'rejectedCount', 'coordinatorPhase']),
        'M07_REPORT_CONTRACT', 'persisted report exposes real coordinator phase and queues')
require(all(k in health for k in ['pendingCount', 'requiresReviewCount', 'rejectedCount']),
        'M07_HEALTH_COUNTS', 'health separates pending/review/rejected')
require('request arriving during active drain is not lost' in tests and 'stale session scope cannot apply work after epoch changes' in tests,
        'M07_CLOSURE_TESTS_PRESENT', 'race and stale-scope closure tests are present')

prod_legacy = []
for p in ROOT.rglob('*.kt'):
    rel = p.relative_to(ROOT).as_posix()
    if '/test/' in rel or '/androidTest/' in rel or rel.endswith('SyncManager.kt'):
        continue
    s = p.read_text(encoding='utf-8', errors='ignore')
    if 'requestOrRun(' in s or 'syncManager.fullSync(' in s:
        prod_legacy.append(rel)
require(not prod_legacy, 'M07_ZERO_PRODUCTION_LEGACY_ENTRY', f'legacy production callers={prod_legacy}')

failed = [c for c in checks if not c[0]]
for ok, code, detail in checks:
    print(f"{'PASS' if ok else 'FAIL'} {code}: {detail}")
print(f"SUMMARY: {len(checks)-len(failed)}/{len(checks)} PASS")
sys.exit(1 if failed else 0)
