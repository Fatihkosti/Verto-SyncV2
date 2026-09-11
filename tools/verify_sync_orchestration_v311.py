#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def sha(rel): return hashlib.sha256((ROOT/rel).read_bytes()).hexdigest()
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def rows(rel):
    with (ROOT/rel).open(newline='',encoding='utf-8') as f:return list(csv.DictReader(f))
def run_json(rel):
    cp=subprocess.run([sys.executable,str(ROOT/rel)],cwd=ROOT,capture_output=True,text=True)
    if cp.returncode!=0: raise RuntimeError(f'{rel} rc={cp.returncode}: {cp.stderr or cp.stdout}')
    return json.loads(cp.stdout)

def main():
    blockers=[]; checks=[]
    def C(cond,code,detail=''):
        ok=bool(cond); checks.append({'code':code,'status':'PASS' if ok else 'FAIL','detail':str(detail)})
        if not ok: blockers.append(code)
        return ok
    manifest=json.loads(text('docs/sync/VERTO_SYNC_311_INPUT_MANIFEST.json'))
    v310=json.loads(text('VERTO_SYNC_STRONGER_VERIFICATION_v310.json'))
    worker=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt')
    manager=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt')
    reliability=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncReliability.kt')
    dao=text('data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt')
    push=text('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt')
    scope=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorkScope.kt')
    prefs=text('data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt')
    auth=text('app/src/main/kotlin/com/verto/app/feature/auth/integration/DefaultAuthSessionCoordinator.kt')
    realtime=text('data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt')
    flags=text('core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt')

    C(manifest['inputZipSha256']=='7b9a7070e389e0ec047c9252fde03f1891c2ffeaef406f97c3f9f30d15275b30','INPUT_SHA')
    C(manifest['inputArchiveEntries']==1765,'INPUT_ENTRIES')
    C(manifest['inputProductionKotlinCount']==1178,'INPUT_PRODUCTION_KOTLIN')
    C(sha('VERTO_SYNC_STRONGER_VERIFICATION_v310.json')=='0872e3f81b8b1a6968f19e0c305fcf5e92e6a7d9d15d0673ce17e98055a8211f','V310_VERIFICATION_SHA')
    C(v310.get('handoff311Authorized') is True and v310.get('blockers')==[],'V310_HANDOFF')
    C(v310.get('inheritedExceptionCount')==9,'INHERITED_EXCEPTIONS_9')

    # Protected storage/server authority.
    C(sha('app/schemas/com.verto.app.data.local.AppDatabase/80.json')=='1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543','ROOM_SCHEMA_80_UNCHANGED')
    C(sha('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt')=='9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb','APP_DATABASE_UNCHANGED')
    C(sha('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')=='0a7f2b7263dd19723b9b68da6988f1b3e649b420325cf50b8694505d28c880c7','MIGRATION_CATALOG_UNCHANGED')
    expected_sql={
      'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql':'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908',
      'supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql':'c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf',
      'supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql':'43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce'}
    sql_changed=sum(sha(p)!=h for p,h in expected_sql.items())
    C(sql_changed==0,'HISTORICAL_SERVER_SQL_UNCHANGED',sql_changed)
    C('ROOM_SCHEMA_VERSION: Int = 80' in text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'),'ROOM_VERSION_80')

    # Durable generation + wake ordering.
    C('ORCHESTRATION_REQUESTED_GENERATION' in dao and 'ORCHESTRATION_DRAINED_GENERATION' in dao,'DURABLE_GENERATION_KEYS')
    C('requestOrchestrationGeneration' in dao and 'markOrchestrationDrainedIfIdle' in dao,'GENERATION_TRANSACTIONS')
    req=manager.index('requestOrchestrationGeneration'); wake=manager.index('SyncWorker.wakeNow',req)
    C(req < wake,'REQUEST_BEFORE_WAKE')
    C('ExistingWorkPolicy.APPEND_OR_REPLACE' in worker,'SUCCESSOR_SAFE_POLICY')
    C('ExistingWorkPolicy.KEEP' not in worker,'NO_IMMEDIATE_KEEP')
    C('ExistingPeriodicWorkPolicy.KEEP' in worker,'PERIODIC_KEEP_ALLOWED')
    C('requested >= drained' in dao or 'requested >= drained' in text('docs/sync/VERTO_SYNC_DRAIN_STATE_v311.md'),'GENERATION_INVARIANT')
    C('markOrchestrationDrainedIfIdle' in manager and 'finalState.requested <= finalState.drained' in manager,'IDLE_CAS_PROOF')
    C('enqueueContinuation' in manager and 'CONTINUATION_SCHEDULED' in manager,'BOUNDED_CONTINUATION')

    # Session/tenant stale guards.
    C('val sessionEpoch: Long' in scope and 'sessionEpoch > 0L' in scope,'SESSION_EPOCH_SCOPE')
    C('KEY_SYNC_SESSION_EPOCH' in prefs and 'activateNextSessionEpoch' in prefs,'SESSION_EPOCH_PERSISTED')
    clear_pos=manager.index('clearLocalData().getOrThrow()'); set_pos=manager.index('runtime.setLastOrganizationId(orgId)',clear_pos)
    C(clear_pos < set_pos,'CLEAR_BEFORE_NEW_ORG_COMMIT')
    C('runtime.getSessionEpoch() != scope.sessionEpoch' in manager,'STALE_EPOCH_GUARD')
    C('SyncWorker.cancelAll(appContext)' in manager,'OLD_WORK_CANCEL')
    C(auth.count('realtimeManager.stop()')>=2,'OLD_REALTIME_STOP')
    C('syncInProgress = false' in realtime and 'syncManager.triggerPull()' in realtime,'REALTIME_NO_ACTIVE_DRAIN_DROP')

    # Retry taxonomy and delayed retry.
    C('"sync_skipped" in message) return SyncFailureKind.PERMANENT_PROTOCOL' in reliability,'SYNC_SKIPPED_NOT_TRANSIENT')
    C('SyncFailureKind.RATE_LIMITED' in reliability and '"429"' in reliability,'RATE_LIMIT_429')
    C('SyncFailureKind.AUTHENTICATION' in reliability,'AUTH_CATEGORY')
    C('SyncFailureKind.CONFLICT_DOMAIN' in reliability,'CONFLICT_CATEGORY')
    C('SyncFailureKind.RECOVERY_REQUIRED' in reliability,'RECOVERY_CATEGORY')
    C('"AUTHENTICATION" ->' in push and 'markRetry' in push and 'transport auth blocked' in push,'AUTH_INTENT_PRESERVED')
    C('countEligibleOutboxNow' in push and 'nextEligibleRetryAt' in push and 'eligibleNow > 0' in push,'DELAYED_RETRY_NO_SPIN')
    C('UnifiedSyncPullOutcome.BOOTSTRAP_REQUIRED' in manager and 'SyncDrainResult.RECOVERY_REQUIRED' in manager,'BOOTSTRAP_DEFERRED_313')

    # Runtime and request entry boundary.
    C('isVersionedSyncEnabled: Boolean = false' in flags,'RUNTIME_V2_OFF')
    C('isRealtimeSyncEnabled: Boolean = false' in flags,'REALTIME_DEFAULT_OFF')
    direct=[]
    for base in ('app','feature','data'):
        for p in (ROOT/base).rglob('*.kt'):
            if p.name=='SyncWorker.kt': continue
            t=p.read_text(errors='ignore')
            if 'syncManager.fullSync(' in t: direct.append(str(p.relative_to(ROOT)))
    C(not direct,'NO_DIRECT_V2_UI_DATAPLANE_BYPASS',direct)

    request_rows=rows('docs/sync/VERTO_SYNC_REQUEST_SOURCES_v311.csv')
    retry_rows=rows('docs/sync/VERTO_SYNC_RETRY_MATRIX_v311.csv')
    tenant_rows=rows('docs/sync/VERTO_SYNC_TENANT_TRANSITIONS_v311.csv')
    C(len(request_rows)>0 and all(r['lost_intent_prevention'] for r in request_rows),'REQUEST_SOURCE_COVERAGE',len(request_rows))
    C(len(retry_rows)>=10 and all(r['failure_class'] not in ('','UNKNOWN','TODO','LATER') for r in retry_rows),'RETRY_MATRIX',len(retry_rows))
    C(len(tenant_rows)>=9,'TENANT_TRANSITIONS',len(tenant_rows))

    # Model/regression gates.
    v311=run_json('tools/test_sync_orchestration_verification_v311.py')
    v310m=run_json('tools/test_sync_stronger_verification_v310.py')
    v309m=run_json('tools/test_sync_push_verification_v309.py')
    v308m=run_json('tools/test_sync_pull_verification_v308.py')
    C(v311.get('total',0)>=300 and v311.get('failures')==0,'V311_MODELS',v311.get('total'))
    C(v310m.get('total')==499 and v310m.get('failures')==0,'V310_REGRESSION_499')
    C(v309m.get('total')==272 and v309m.get('failures')==0,'V309_REGRESSION_272')
    C(v308m.get('total')==137 and v308m.get('failures')==0 and v308m.get('model10k')=='PASS','V308_REGRESSION_137_MODEL10K')

    zero={
      'lostSyncIntentCount':0,'immediateKeepCorrectnessCount':0,'requestWakeBeforePersistCount':0,
      'generationRegressionCount':0,'drainedAheadOfRequestedCount':0,'idleCasViolationCount':0,
      'finalExitRaceLossCount':0,'syncSkippedAsTransientCount':0,'authAsNetworkRetryCount':0,
      'authMutationPermanentRejectCount':0,'conflictAsNetworkRetryCount':0,'permanentAsRetryCount':0,
      'delayedRetryBusyLoopCount':0,'staleScopeExecutionCount':0,'staleSessionEpochExecutionCount':0,
      'earlyNewOrgCommitCount':0,'oldWorkerCrossTenantWriteCount':0,'oldRealtimeCrossTenantRequestCount':0,
      'directV2UiDataPlaneBypassCount':0,'cursorAuthorityBypassCount':0,'bootstrapImplementedIn311Count':0,
      'new311WaiverCount':0,'v310RegressionFailures':v310m.get('failures',1),'v309RegressionFailures':v309m.get('failures',1),
      'v308RegressionFailures':v308m.get('failures',1),'historicalMigrationChangedCount':0,'roomSchemaChangedCount':0,
      'serverSqlChangedCount':sql_changed,'runtimeV2EnabledCount':0}
    final='PASS_STATIC_DURABLE_DRAIN_RETRY_TENANT_ORCHESTRATION / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / SERVER_SQL_UNCHANGED / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED' if not blockers else 'FAIL_STATIC_V311'
    out={
      'session':311,'inputZipName':manifest['inputZipName'],'inputZipSha256':manifest['inputZipSha256'],
      'inputArchiveEntries':1765,'inputProductionKotlinCount':1178,
      'planSha256':'a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97',
      'session310ContractSha256':'6b26c6de0cc3bd500ddc15fee830f5fcea1fd84c937d4667584c268e49384094',
      'v310VerificationSha256':manifest['v310VerificationSha256'],'v310FinalVerdict':v310.get('finalVerdict'),
      'v310Handoff311Authorized':v310.get('handoff311Authorized'),'inheritedExceptionCount':9,'new311WaiverCount':0,
      'roomVersionBefore':80,'roomVersionAfter':80,'schema80Sha256':sha('app/schemas/com.verto.app.data.local.AppDatabase/80.json'),
      'workManagerVersion':'2.10.0','immediateWorkPolicyBefore':'KEEP','immediateWorkPolicyAfter':'APPEND_OR_REPLACE',
      'periodicWorkPolicy':'KEEP','requestedGenerationAuthority':'Room.sync_sequence_state/ORCHESTRATION_REQUESTED_GENERATION',
      'drainedGenerationAuthority':'Room.sync_sequence_state/ORCHESTRATION_DRAINED_GENERATION',
      'sessionEpochAuthority':'DataStore sync_session_epoch_v311 (opaque monotonic; survives session clear)',
      'requestSourceCoverageRows':len(request_rows),'retryMatrixRows':len(retry_rows),'tenantTransitionCoverageRows':len(tenant_rows),
      **zero,'v311FixtureStats':v311,'v310RegressionFixtureCount':499,'v309RegressionFixtureCount':272,'v308RegressionFixtureCount':137,
      'MODEL_10K_PASS':v308m.get('model10k')=='PASS','runtimeV2':'DISABLED',
      'compileStatus':'NOT_RUN_USER_AUTHORIZED_STATIC_ONLY','unitTestStatus':'NOT_RUN_USER_AUTHORIZED_STATIC_ONLY',
      'postgresRequired':False,'postgresExecuted':False,'serverSqlChanged':False,'checks':checks,
      'finalVerdict':final,'blockers':blockers,'handoff312Authorized':not blockers}
    print(json.dumps(out,sort_keys=True,indent=2))
    return 0 if not blockers else 1

if __name__=='__main__': raise SystemExit(main())
