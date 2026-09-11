#!/usr/bin/env python3
from __future__ import annotations
import json,re,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
checks=[]
def check(name, ok, detail):
    checks.append({"name":name,"status":"PASS" if ok else "FAIL","detail":detail})

def text(rel): return (ROOT/rel).read_text(encoding='utf-8')

writer=text('data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt')
check('writer_typed_payload', 'payload: Map<String, Any?>' in writer, 'UnifiedOutboxWriter accepts typed scalar payloads')
check('writer_transaction_guard', writer.count('requireProducerTransaction()') >= 3 and 'M04_PRODUCER_TRANSACTION_REQUIRED' in writer, 'enqueue + attachment enqueue require an open Room transaction')
check('canonical_boolean_number', 'is Boolean -> JsonPrimitive(this)' in writer and 'is Number -> JsonPrimitive(this)' in writer, 'Boolean/Number are emitted as native JSON scalars')
check('canonical_nested', 'is Map<*, *>' in writer and 'is Iterable<*>' in writer, 'nested payload values are canonicalized deterministically')

payment=text('app/src/main/kotlin/com/verto/app/feature/payment/bridge/PaymentAppAdapters.kt')
credit=payment[payment.index('class RoomAdvanceCreditAdapter'):payment.index('private fun PaymentRecord.toEntity')]
check('client_credit_atomic', 'database.withTransaction {' in credit and credit.index('dao.insert(') < credit.index('outbox.enqueue('), 'CLIENT_CREDIT row and V2 intent share one Room transaction')
check('client_credit_native_amount', '"amountMinor" to credit.amountMinor,' in credit, 'CLIENT_CREDIT amountMinor is numeric JSON')

obs=text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/RoomTeamObservationRepository.kt')
persist=obs[obs.index('private suspend fun persistWithUnifiedOutbox'):obs.index('internal suspend fun pushDirty')]
check('team_observation_always_captured', 'if (!v2OwnsTransport)' not in persist and 'database.withTransaction {' in persist and 'outboxWriter.enqueue(' in persist, 'TEAM_OBSERVATION always captures durable V2 intent')
check('team_observation_legacy_transport_only', 'isDirty = !v2OwnsTransport' in persist, 'legacy dirty marker is compatibility mirror until transport cutover')

registry=text('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt')
registry_ids=re.findall(r'\brecord\("([A-Z][A-Z0-9_]*)"', registry)
check('registry_35', len(registry_ids)==35 and len(set(registry_ids))==35, f'{len(registry_ids)} unique aggregates')
check('team_observation_registry', 'TEAM_OBSERVATION' in registry_ids, 'M04 audits the current 35-aggregate registry, not stale 34-aggregate evidence')

bridge=text('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedStrongerSyncBridge.kt')
stronger_ids=re.findall(r'\br\("([A-Z][A-Z0-9_]*)"', bridge)
check('stronger_17', len(stronger_ids)==17 and len(set(stronger_ids))==17, f'{len(stronger_ids)} stronger-domain bridge aggregates retained')
check('stronger_no_generic_duplicate', 'This prevents duplicate durable intents' in bridge and 'financial_outbox, inventory_*_outbox, or optimal_outbox' in bridge, 'specialized stronger outboxes remain single transport authorities')

flags=text('core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt')
check('no_global_cutover', 'isVersionedSyncEnabled: Boolean = false' in flags and 'isV2PushEnabled: Boolean = false' in flags and 'isLegacySyncFallbackEnabled: Boolean = true' in flags, 'M04 does not prematurely enable global V2 or remove legacy fallback')

migration=text('data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2MigrationCoordinator.kt')
check('legacy_fence_deferred', 'legacyWritesFenced = false' in migration, 'legacy producer fence remains deferred until transport replacement in M05')

# Typed-payload regression gate over files that produce unified intents.
sus=[]
for p in ROOT.rglob('*.kt'):
    s=p.read_text(encoding='utf-8',errors='ignore')
    if 'UnifiedOutboxWriter' not in s and '.enqueue(' not in s:
        continue
    for n,line in enumerate(s.splitlines(),1):
        if re.search(r'"(?:is[A-Z]|deleted|archived|amount(?:Minor)?|.*At|.*Count|.*Quantity|.*Price|.*Amount|.*Version)"\s+to\s+[^,]+\.toString\(\)', line):
            # Purchase match `lines` is an explicit contract encoding and is intentionally not matched by key policy.
            sus.append(f'{p.relative_to(ROOT)}:{n}:{line.strip()}')
        if re.search(r'"(?:deleted|archived|isDone)"\s+to\s+"(?:true|false)"',line):
            sus.append(f'{p.relative_to(ROOT)}:{n}:{line.strip()}')
check('no_scalar_stringification', not sus, 'no typed scalar payload field is stringified' if not sus else '; '.join(sus[:8]))

# Known direct generic producer families must remain transaction-owned.
transaction_files={
'data/operations/src/main/kotlin/com/verto/app/data/repository/BudgetRepository.kt':'database.withTransaction {',
'data/operations/src/main/kotlin/com/verto/app/data/repository/CashReconciliationRepository.kt':'database.withTransaction {',
'data/operations/src/main/kotlin/com/verto/app/data/repository/InvoiceRepository.kt':'database.withTransaction {',
'data/operations/src/main/kotlin/com/verto/app/data/repository/NoteRepository.kt':'database.withTransaction {',
'data/operations/src/main/kotlin/com/verto/app/utils/CashMovementSyncWriter.kt':'database.withTransaction {',
'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/education/RoomEducationalContentRepository.kt':'database.withTransaction {',
'feature/inventory/src/main/kotlin/com/verto/app/data/repository/InventoryRepository.kt':'database.withTransaction {',
'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryRoomAdapters.kt':'database.withTransaction {',
'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryPresentationAdapters.kt':'database.withTransaction {',
'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/RoomInventoryLandedCostAdjustmentAdapter.kt':'database.withTransaction {',
'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt':'transaction.inTransaction {',
'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/purchase/LinkPurchaseOrderToShipmentUseCase.kt':'transaction.inTransaction {',
'feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt':'database.withTransaction {',
'feature/party/src/main/kotlin/com/verto/app/feature/party/data/PartyPresentationAdapter.kt':'database.withTransaction {',
'app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt':'db.withTransaction {',
'app/src/main/kotlin/com/verto/app/feature/invoice/bridge/InvoicePresentationBridge.kt':'database.withTransaction {',
'app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsReceivingAdapters.kt':'database.withTransaction {',
'app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsShipmentStoreAdapters.kt':'database.withTransaction {',
'app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV2AppAdapters.kt':'database.withTransaction {',
}
missing=[rel for rel,marker in transaction_files.items() if marker not in text(rel)]
check('known_generic_producer_transactions', not missing, f'{len(transaction_files)-len(missing)}/{len(transaction_files)} producer files expose their required transaction boundary' if not missing else 'missing: '+','.join(missing))

result={
  'gate':'M04_SYNC_V2_PRODUCERS',
  'status':'PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL',
  'checks':checks,
  'registry_aggregate_count':len(registry_ids),
  'stronger_bridge_count':len(stronger_ids),
  'build_status':'BLOCKED_OFFLINE_GRADLE_DISTRIBUTION',
}
out=ROOT/'evidence/m04/verification/m04_verification.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for c in checks: print(f"{c['status']:4} {c['name']}: {c['detail']}")
print('RESULT',result['status'])
sys.exit(0 if result['status']=='PASS' else 1)
