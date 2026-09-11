#!/usr/bin/env python3
from pathlib import Path
import json, sys
ROOT = Path(__file__).resolve().parents[2]
checks=[]
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def check(name, cond, detail):
    checks.append({'name':name,'pass':bool(cond),'detail':detail})

manager=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt')
drain=manager[manager.index('suspend fun drainOrchestration'):manager.index('/** يستعيد', manager.index('suspend fun drainOrchestration'))]
check('v2_drain_no_legacy_participant_transport', 'participants.flatMap' not in drain and 'executeSyncOperations' not in drain and 'SyncStage.PUSH' not in drain and 'SyncStage.DELETE' not in drain, 'drainOrchestration contains no legacy participant PUSH/DELETE execution')
check('v2_single_push_coordinator_boundary', 'v2Engine.pushAvailable(scope)' in drain, 'SyncManager pushes only through SyncV2EnginePort')
check('terminal_push_health_not_success', 'push.requiresReview > 0 || push.rejected > 0' in drain and 'PUSH_TERMINAL' in drain, 'terminal push states are recorded as failure, not success')
check('future_retry_delayed_continuation', 'push.backlog > 0L && nextEligibleAt != null && nextEligibleAt > now' in drain and 'enqueueContinuation(scope, nextEligibleAt - now)' in drain, 'future eligible retry schedules exact delayed continuation')
check('manager_cancellation_passthrough', 'catch (cancelled: CancellationException)' in drain and 'throw cancelled' in drain, 'CancellationException is not converted to generic sync failure')

outbox=text('data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOutboxDao.kt')
check('ack_cas_fingerprint', "AND semantic_fingerprint = :expectedSemanticFingerprint" in outbox and 'expectedSemanticFingerprint: String' in outbox, 'terminal ACK CAS requires mutation lease and sent semantic fingerprint')
check('dependency_terminalization', 'markDependencyRequiresReview' in outbox and "state = 'REQUIRES_REVIEW'" in outbox, 'failed/missing dependencies have a terminal review transition')

push=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt')
check('push_cancellation_passthrough', 'if (t is CancellationException) throw t' in push and 'catch (cancelled: CancellationException)' in push, 'generic push propagates coroutine cancellation')
check('dependency_failed_review', 'TERMINAL_DEPENDENCY_CODES' in push and 'DEPENDENCY_FAILED' in push and 'PREDECESSOR_REQUIRES_REVIEW' in push, 'dependency/predecessor hard failures leave runnable continuation set')
check('generic_runnable_count', 'countRunnableOutboxNow' in push, 'generic push continuation uses runnable rather than raw eligible count')
check('ack_calls_use_fingerprint', push.count('row.semanticFingerprint') >= 6, 'generic terminal transitions carry the leased semantic fingerprint')

coord=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/SyncV2PushCoordinator.kt')
check('coordinator_all_lanes', all(x in coord for x in ['generic.pushAvailable','party.pushAvailable','stronger.pushAvailable','specialized.sortedBy']), 'coordinator owns generic, Party, financial/inventory stronger, and specialized lanes')
check('coordinator_persistent_terminal_health', all(x in coord for x in ['countRequiresReview','countRejected','countFinancialOutboxReview','countInventoryStockReview','countInventoryCostReview']), 'health accounts for terminal states left from earlier runs')

strong=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedStrongerOutboxPushEngine.kt')
check('stronger_financial_lane', 'UnifiedStrongerSourceFactory.financial' in strong and 'getReadyFinancialOutbox' in strong, 'financial_outbox has explicit V2 sender')
check('stronger_inventory_stock_lane', 'UnifiedStrongerSourceFactory.inventoryMovement' in strong and 'getPendingInventoryStockOutbox' in strong, 'inventory_stock_outbox has explicit V2 sender')
check('stronger_inventory_cost_lane', 'UnifiedStrongerSourceFactory.inventoryCost' in strong and 'getPendingInventoryCostOutbox' in strong, 'inventory_cost_outbox has explicit V2 sender')
check('stronger_cancellation_passthrough', strong.count('CancellationException') >= 4, 'stronger lane does not swallow cancellation')
check('stronger_auth_separate', 'code == "AUTHENTICATION"' in strong and 'throw UnifiedSyncPushFailure(code' in strong, 'authentication is separated from transient retry and permanent review')

route=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedFinancialOwner310Route.kt')
for aggregate in ['CLIENT_CREDIT','COST_ALLOCATION','EXPENSE','CASH_MOVEMENT','CASH_RECONCILIATION','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE']:
    check('owner310_materializes_'+aggregate.lower(), f'"{aggregate}"' in route, f'{aggregate} has an explicit durable materialization route')
check('owner310_no_generic_upsert', 'parsed == SyncMutationOperation.UPSERT) SyncMutationOperation.COMMAND' in route and 'validateStrongerMutation' in route, 'owner310 generic intents are normalized through stronger COMMAND policy')

party=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPartyPushBridge.kt')
check('party_cancellation_passthrough', 'if (t is CancellationException) throw t' in party, 'Party bridge propagates cancellation')
check('party_rejection_terminal', 'SyncReceiptStatus.REJECTED' in party and '"REJECTED"' in party, 'Party permanent rejection is terminal')

worker=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt')
check('workmanager_delayed_retry', '.setInitialDelay(delay, TimeUnit.MILLISECONDS)' in worker and 'delayedContinuationWorkName(scope)' in worker and 'ExistingWorkPolicy.REPLACE' in worker, 'future retry uses a separate delayed WorkManager chain')

optimal=text('app/src/main/kotlin/com/verto/app/di/optimal/OptimalSyncBridge.kt')
optparticipant=text('feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/OptimalOutboxSyncParticipant.kt')
executor=text('feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/ContractGuardedOptimalOutboxEventExecutor.kt')
check('optimal_explicit_v2_bridge', 'SyncV2SpecializedPushBridge' in optimal and 'override suspend fun pushV2' in optimal, 'Optimal stronger outbox is invoked through V2 extension point, not SyncParticipant operations')
check('optimal_cancellation_passthrough', 'catch (cancelled: CancellationException)' in optparticipant and 'throw cancelled' in optparticipant, 'Optimal participant propagates cancellation')
check('optimal_permanent_vs_retry', 'OptimalRemoteDispatchResult.RetryableFailure' in executor and 'OptimalRemoteDispatchResult.Rejected' in executor and 'blocked(' in executor, 'Optimal contract separates retryable failure from rejection/conflict review')

tests=text('data/sync/src/test/kotlin/com/verto/app/data/sync/SyncManagerTest.kt')
for phrase,name in [
 ('future retry schedules delayed continuation','test_delayed_retry'),
 ('terminal push issue never records push health success','test_terminal_health'),
 ('v2 drain does not execute legacy participant push operations','test_no_legacy_participant_transport'),
]: check(name, phrase in tests, phrase)

failed=[c for c in checks if not c['pass']]
result={'result':'PASS' if not failed else 'FAIL','checks':checks,'passed':len(checks)-len(failed),'failed':len(failed)}
out=ROOT/'evidence/m05/M05_STATIC_GATE.json'
out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
for c in checks: print(('PASS' if c['pass'] else 'FAIL'), c['name'], '-', c['detail'])
print('RESULT',result['result'],f"({result['passed']}/{len(checks)})")
sys.exit(0 if not failed else 1)
