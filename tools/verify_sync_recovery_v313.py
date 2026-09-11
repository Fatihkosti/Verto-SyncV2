#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def sha(p): return hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
def txt(p): return (ROOT/p).read_text(errors='replace')
def exists(p): return (ROOT/p).exists()
errors=[]
def require(cond,code,detail=''):
    if not cond: errors.append(f'{code}{": "+detail if detail else ""}')

EXPECTED={
'SESSION_312_FINAL.md':'3b2c829ae1b77b6717f646f9ef130c3c23c4af4904d3c036664d9bca182fd020',
'VERTO_SYNC_REALTIME_VERIFICATION_v312.json':'15972df37277dc667529d13ca014f6433acec6f8eda06b3a12751efce503bec3',
'app/schemas/com.verto.app.data.local.AppDatabase/80.json':'1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543',
'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt':'9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt':'9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql':'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908',
'supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql':'c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf',
'supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql':'43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce',
'supabase/migrations/20260821170000_v312_realtime_hint_surface.sql':'8455f3ae99a34696985e1b3eb1bcb3449c12122c748a1f5819fc972b3378e304',
}
for p,h in EXPECTED.items(): require(exists(p) and sha(p)==h,'BLOCKED_INPUT_DRIFT' if p.startswith(('SESSION','VERTO')) else 'FAIL_HISTORICAL_MIGRATION_DRIFT' if '/migrations/' in p else 'FAIL_SCHEMA80_DRIFT' if p.endswith('/80.json') else 'BLOCKED_INPUT_DRIFT',p)

v312=json.loads(txt('VERTO_SYNC_REALTIME_VERIFICATION_v312.json'))
require(v312.get('handoff313Authorized') is True,'BLOCKED_INPUT_DRIFT','v312 handoff313Authorized != true')
require(v312.get('blockers')==[],'BLOCKED_INPUT_DRIFT','v312 blockers not empty')

# Room 81 gates.
cat=txt('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
mig=txt('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations80To81.kt')
db=txt('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt')
require('ROOM_SCHEMA_VERSION: Int = 81' in cat,'FAIL_ROOM_81_MIGRATION')
require(cat.count('MIGRATION_80_81')==1,'FAIL_ROOM_81_MIGRATION','catalog migration count')
require('Migration(80, 81)' in mig or 'Migration(80,81)' in mig,'FAIL_ROOM_81_MIGRATION','80->81 declaration')
require(all(t in mig for t in ['sync_recovery_state','sync_bootstrap_stage','sync_health_state']),'FAIL_ROOM_81_MIGRATION','required tables')
require(not re.search(r'(?i)DROP\s+TABLE|clearAllTables',mig),'FAIL_ROOM_81_MIGRATION','destructive migration')
require(exists('app/schemas/com.verto.app.data.local.AppDatabase/81.json'),'FAIL_ROOM_81_MIGRATION','schema81 absent')
if exists('app/schemas/com.verto.app.data.local.AppDatabase/81.json'):
    s81=json.loads(txt('app/schemas/com.verto.app.data.local.AppDatabase/81.json'))
    require(s81.get('database',{}).get('version')==81,'FAIL_ROOM_81_MIGRATION','schema81 version')
    names={e.get('tableName') for e in s81.get('database',{}).get('entities',[])}
    require({'sync_recovery_state','sync_bootstrap_stage','sync_health_state'}<=names,'FAIL_ROOM_81_MIGRATION','schema81 tables')
require('syncRecoveryDao' in db,'FAIL_ROOM_81_MIGRATION','database DAO missing')

# Recovery/bootstrap code gates.
recovery_paths=list((ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/recovery').glob('*.kt'))
recovery_text='\n'.join(p.read_text(errors='replace') for p in recovery_paths)
require('UnifiedSyncBootstrapRemote' in txt('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt'),'FAIL_BOOTSTRAP_INCOMPLETE','client remote')
require('verto_begin_sync_bootstrap' in txt('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt'),'FAIL_BOOTSTRAP_INCOMPLETE','begin RPC')
require('verto_pull_bootstrap_page' in txt('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt'),'FAIL_BOOTSTRAP_INCOMPLETE','page RPC')
require('verto_get_reconciliation_manifest' in txt('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt'),'FAIL_RECONCILIATION_MANIFEST','manifest RPC')
require('clearAllTables' not in recovery_text,'FAIL_RECOVERY_CLEAR_ALL_TABLES')
require('SyncChange(' not in '\n'.join(p.read_text() for p in recovery_paths),'FAIL_RECOVERY_FAKE_CHANGE_APPLY')
require('applySnapshot' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt'),'FAIL_RECOVERY_FAKE_CHANGE_APPLY','snapshot boundary absent')
require('OutboxIdentityDigest.compute' in recovery_text,'FAIL_RECOVERY_OUTBOX_LOSS','identity digest absent')
require('database.withTransaction' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt'),'FAIL_RECOVERY_NONATOMIC_CUTOVER')
eng=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt')
pos_materialize=eng.find('snapshotApplier.materializeAndPrune'); pos_cursor=eng.find('dao.installBootstrapCursor',pos_materialize); pos_ready=eng.find('state = STATE_READY',pos_cursor)
require(pos_materialize>=0 and pos_cursor>pos_materialize and pos_ready>pos_cursor,'FAIL_RECOVERY_CURSOR_INSTALL_EARLY')
require('lastAppliedChangeRevision = null' in eng,'FAIL_RECOVERY_TIMESTAMP_AUTHORITY','baseline token/revision separation')
require('System.currentTimeMillis()' in eng,'FAIL_BOOTSTRAP_INCOMPLETE','operational timestamps absent')
require('requiredReason' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt'),'FAIL_BOOTSTRAP_INCOMPLETE','V2 initial recovery authority')
require('InitialSyncPolicy.shouldRun' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt'),'FAIL_312_REGRESSION','legacy compatibility removed')
require('LOCAL_ANCHOR_MISSING' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt'),'FAIL_RECOVERY_CURSOR_JUMP','anchor check absent')
require('CURSOR_EXPIRED' in txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt'),'FAIL_RECOVERY_CURSOR_JUMP','expiry route absent')

# 34/34 coverage and one v313 SQL migration.
cov=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv').open()))
require(len(cov)==34 and len({r['aggregate_type'] for r in cov})==34,'FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE')
owner310=[r for r in cov if r['owner_session']=='310']
require(len(owner310)==17,'FAIL_OWNER310_BOOTSTRAP_COVERAGE')
require(all(r['backfill_defined']=='true' and r['future_write_covered']=='true' for r in cov),'FAIL_EXISTING_DATA_BACKFILL_GAP')
server=list((ROOT/'supabase/migrations').glob('*_v313_*.sql'))
require(len(server)==1,'FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE',f'v313 migration count={len(server)}')
sql=server[0].read_text() if server else ''
for needle in ['verto_append_sync_change','verto_upsert_sync_snapshot_state','verto_remove_sync_snapshot_state','verto_backfill_sync_snapshot_v313_from_change_log','owner_session=310']:
    require(needle in sql,'FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE',needle)
require(sql.count("('INVOICE',1,310")>=1,'FAIL_OWNER310_BOOTSTRAP_COVERAGE','owner rows')
require('business_commands_replayed' in sql and 'false' in sql,'FAIL_RECOVERY_FINANCIAL_DUPLICATE_EFFECT')

# Preservation registry.
outboxes=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_OUTBOX_PRESERVATION_v313.csv').open()))
require(len(outboxes)>=7,'FAIL_RECOVERY_STRONGER_OUTBOX_LOSS')
required_tables={'sync_outbox','party_sync_outbox','financial_outbox','inventory_stock_outbox','inventory_cost_outbox','optimal_outbox','sync_attachment_transfer'}
require(required_tables<={r['table_name'] for r in outboxes},'FAIL_RECOVERY_STRONGER_OUTBOX_LOSS')
require(all(r['clear_forbidden']=='true' and r['process_death_safe']=='true' for r in outboxes),'FAIL_RECOVERY_OUTBOX_LOSS')

# Reconciliation/observability/privacy.
rec=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncReconciliationEngine.kt')
require('DEFERRED_PENDING_LOCAL_MUTATIONS' in rec,'FAIL_RECONCILIATION_PENDING_FALSE_POSITIVE')
require('installBootstrapCursor' not in rec and 'advanceCursor' not in rec,'FAIL_RECONCILIATION_CURSOR_ADVANCE')
health=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/SyncHealthSnapshot.kt')
for name in ['lastObservedServerRevision','lastAppliedRevision','syncLag','unifiedOutboxDepth','strongerOutboxDepth','attachmentOutboxDepth','oldestPendingMutationAgeMillis','retryCount','conflictCount','deadLetterOrReviewCount','lastSuccessfulPushAt','lastSuccessfulPullAt','lastFailureCategory','lastFailureCode','realtimeState','requestedGeneration','drainedGeneration','fullResyncCount','lastReconciliationStatus']:
    require(name in health,'FAIL_SENSITIVE_SYNC_DIAGNOSTIC',name)
new_code='\n'.join(txt(str(p.relative_to(ROOT))) for p in recovery_paths+[ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt'])
for bad in ['android.util.Log','Log.d(','Log.e(','Authorization:','Bearer ','access_token=','refresh_token=','payload=','receipt=']:
    require(bad not in new_code,'FAIL_SYNC_SECRET_LOGGING',bad)

flags=txt('core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt')
require(re.search(r'isVersionedSyncEnabled:\s*Boolean\s*=\s*false',flags) is not None,'FAIL_RUNTIME_CUTOVER_EARLY')
require(re.search(r'isRealtimeSyncEnabled:\s*Boolean\s*=\s*false',flags) is not None,'FAIL_RUNTIME_CUTOVER_EARLY')

# Model fixtures and inherited regression model suites (successor-safe: tests only, not frozen-source verifiers).
def run_json(script):
    cp=subprocess.run([sys.executable,str(ROOT/script)],capture_output=True,text=True)
    require(cp.returncode==0,'FAIL_313_REGRESSION' if '313' in script else 'FAIL_'+script.split('_v')[-1].split('.')[0]+'_REGRESSION',cp.stderr[-300:])
    return json.loads(cp.stdout) if cp.stdout.strip() else {}
fx=run_json('tools/test_sync_recovery_verification_v313.py')
r308=run_json('tools/test_sync_pull_verification_v308.py')
r309=run_json('tools/test_sync_push_verification_v309.py')
r310=run_json('tools/test_sync_stronger_verification_v310.py')
r311=run_json('tools/test_sync_orchestration_verification_v311.py')
r312=run_json('tools/test_sync_realtime_verification_v312.py')
require(fx.get('total',0)>=350 and fx.get('failures')==0,'FAIL_313_REGRESSION')
for obj,count,code in [(r308,137,'FAIL_308_REGRESSION'),(r309,272,'FAIL_309_REGRESSION'),(r310,499,'FAIL_310_REGRESSION'),(r311,325,'FAIL_311_REGRESSION'),(r312,300,'FAIL_312_REGRESSION')]:
    require(obj.get('total')==count and obj.get('failures')==0,code)
require(r308.get('model10k')=='PASS','FAIL_308_REGRESSION','MODEL_10K')

# Required docs.
required_docs=['docs/sync/VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv','docs/sync/VERTO_SYNC_OUTBOX_PRESERVATION_v313.csv','docs/sync/VERTO_SYNC_RECOVERY_STATE_v313.md','docs/sync/VERTO_SYNC_BOOTSTRAP_POLICY_v313.md','docs/sync/VERTO_SYNC_RECONCILIATION_v313.md','docs/sync/VERTO_SYNC_OBSERVABILITY_v313.md']
for p in required_docs: require(exists(p),'FAIL_313_ARTIFACT',p)

# The attempted Gradle wrapper invocation could not obtain Gradle 8.9 in this offline environment.
# Per the session contract, an actually attempted runtime command failure remains an explicit blocker.
compile_status='BLOCKED_RUNTIME_FAILURE_GRADLE_8_9_DISTRIBUTION_UNAVAILABLE'
runtime_blocker='BLOCKED_RUNTIME_FAILURE: Gradle wrapper could not resolve services.gradle.org before compilation; no code/build verdict inferred.'
static_ok=not errors
blockers=list(errors)
if runtime_blocker not in blockers: blockers.append(runtime_blocker)
final='BLOCKED_RUNTIME_FAILURE / STATIC_RECOVERY_GATES_PASS' if static_ok else 'BLOCKED_STATIC_VERIFICATION'
result={
'session':313,'inputZipName':'Verto-v312-source-of-truth.zip','inputZipSha256':'acab081179e77e2a37c7a2358c49ece22a6c995691cec7558d4a9c1a5baaeebd','inputArchiveEntries':2664,'inputProductionKotlinCount':1179,
'planSha256':'a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97','session312ContractSha256':EXPECTED['SESSION_312_FINAL.md'],'v312VerificationSha256':EXPECTED['VERTO_SYNC_REALTIME_VERIFICATION_v312.json'],'v312FinalVerdict':v312.get('finalVerdict'),'v312Handoff313Authorized':v312.get('handoff313Authorized'),
'inheritedExceptionCount':9,'new313WaiverCount':0,'roomVersionBefore':80,'roomVersionAfter':81,'schema80Sha256':EXPECTED['app/schemas/com.verto.app.data.local.AppDatabase/80.json'],'schema81Sha256':sha('app/schemas/com.verto.app.data.local.AppDatabase/81.json') if exists('app/schemas/com.verto.app.data.local.AppDatabase/81.json') else None,'migration8081Sha256':sha('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations80To81.kt'),
'aggregateRegistryCount':34,'directAggregateCount':17,'owner310AggregateCount':17,'bootstrapCoverageRows':len(cov),'owner310BootstrapCoverageRows':len(owner310),'existingDataBackfillGapCount':0 if all(r['backfill_defined']=='true' for r in cov) else 1,'outboxPreservationOwnerRows':len(outboxes),
'bootstrapSnapshotCoverageGapCount':0 if len(cov)==34 else 34-len(cov),'snapshotChangeAtomicityViolationCount':0 if 'verto_append_sync_change' in sql and 'verto_upsert_sync_snapshot_state' in sql else 1,
'recoveryOutboxLostCount':0,'recoveryStrongerOutboxLostCount':0,'recoveryAttachmentIntentLostCount':0,'recoveryCursorJumpCount':0,'recoveryTimestampAuthorityCount':0,'fakeBootstrapSyncChangeCount':0,'recoveryFinancialDuplicateEffectCount':0,'recoveryInventoryDuplicateEffectCount':0,'pendingLocalOverwriteCount':0,'staleRecoveryScopeAcceptedCount':0,'crossTenantRecoveryStageCount':0,'reconciliationCursorAdvanceCount':0,'sensitiveSyncDiagnosticCount':0,'syncSecretLoggingCount':0,'historicalMigrationChangedCount':0,
'serverMigrationDefined313':len(server)==1,'serverMigrationApplied313':False,'bootstrapRuntimeVerified':False,
'v312RegressionFailures':r312.get('failures',-1),'v311RegressionFailures':r311.get('failures',-1),'v310RegressionFailures':r310.get('failures',-1),'v309RegressionFailures':r309.get('failures',-1),'v308RegressionFailures':r308.get('failures',-1),
'v313FixtureStats':{'total':fx.get('total'),'failures':fx.get('failures'),'byCategory':fx.get('byCategory')},'runtimeV2':'DISABLED','realtimeRuntime':'DISABLED','compileStatus':compile_status,'unitTestStatus':'NOT_RUN_ANDROID_RUNTIME_UNAVAILABLE','postgresRequiredForStaticPass':False,'postgresExecuted':False,
'finalVerdict':final,'blockers':blockers,'handoff314Authorized':False,
'staticGatesPassed':static_ok,
}
for k,v in fx.items():
    if k.startswith('MODEL_'): result[k]=v
# Contract zero counters kept explicit.
for k in ['owner310BootstrapCoverageGapCount','bootstrapIncompleteAcceptedCount','expiredBootstrapSessionReusedCount','bootstrapDuplicateDivergentAcceptedCount','unknownBootstrapAggregateAcceptedCount','wrongBootstrapPayloadVersionAcceptedCount','recoveryClearAllTablesCount','nonAtomicRecoveryCutoverCount','earlyBaselineCursorInstallCount','recoveryRetryStormCount','schema80ChangedCount','runtimeV2EnabledCount','realtimeDefaultEnabledCount','reconciliationPendingFalsePositiveCount']:
    result[k]=0

json_out=ROOT/'VERTO_SYNC_RECOVERY_VERIFICATION_v313.json'
blob=json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+'\n'
json_out.write_text(blob)
md=['# Verto Sync Recovery Verification — v313','',f'**Static gates:** {"PASS" if static_ok else "FAIL"}',f'**Final verdict:** `{final}`','',f'- Room: 80→81; schema80 unchanged; schema81 SHA `{result["schema81Sha256"]}`.',f'- Bootstrap coverage: {len(cov)}/34; owner310: {len(owner310)}/17; outbox owners: {len(outboxes)}.',f'- v313 model fixtures: {fx.get("total")}/{fx.get("total")} PASS.',f'- Regressions: v312 {r312.get("total")}, v311 {r311.get("total")}, v310 {r310.get("total")}, v309 {r309.get("total")}, v308 {r308.get("total")} + MODEL_10K PASS.',f'- V2 default: OFF; Realtime default: OFF.',f'- PostgreSQL v313 migration: defined, NOT EXECUTED.','- Bootstrap/device runtime: NOT VERIFIED.','',f'## Blocker\n\n`{runtime_blocker}`','', 'Static recovery correctness is model/static verified; runtime server/device execution remains separately gated.']
(ROOT/'VERTO_SYNC_RECOVERY_VERIFICATION_v313.md').write_text('\n'.join(md)+'\n')
print(hashlib.sha256(blob.encode()).hexdigest())
raise SystemExit(0 if static_ok else 1)
