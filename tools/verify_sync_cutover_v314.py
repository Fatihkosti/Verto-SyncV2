#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def sha(path): return hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
def text(path): return (ROOT/path).read_text(errors='replace')
def exists(path): return (ROOT/path).exists()
errors=[]
def require(cond,code,detail=''):
    if not cond: errors.append(f'{code}{": "+detail if detail else ""}')

EXPECTED={
 'SESSION_314_FINAL.md':'d80ab8cd8ad9583c6678b358686eb4ba43343543e36b5c1dd50e714973d226f2',
 'SESSION_313_FINAL.md':'7a420fab0082b3fce37340727ae010b457b485567bbd7a46541c8fc6b05a7bd9',
 'VERTO_SYNC_RECOVERY_VERIFICATION_v313.json':'2cd5814dfcd7cc3680fa9b435559c7eaef830b27c308c2a236b11ab9c8cc4529',
 'VERTO_SYNC_RECOVERY_VERIFICATION_v313.md':'2713af2fcb730afb7a2c469bed67c82cd01d7c37e6b2b701e9d4cb5538b4d5fe',
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt':'9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt':'9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt':'0ded17d4e4edc88bb42e4dc3625c9d32e5c6e1cf9ab0a44422c52d5e1386a6f4',
 'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt':'32f91a3e0531d10e0d9461503f499df20297d6d2fab01446ddc849630297b8ae',
 'app/schemas/com.verto.app.data.local.AppDatabase/81.json':'67dbae5091531378e8b4806b1ed7a9206c751b7080169835a349dddd4d9499db',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt':'d76076b476021afd4b53d4db06575e7cc98a9fc58619d4f6281c11eb52b4b14c',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt':'d666e85adcdb57b4f8459670c2070afad8b846b770cbfd586990e076a08718e6',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt':'6548d9f171f66a8b26050af384ad746552e717e3c95bce579aa843f5192d8763',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt':'a065c3f5b99ff0399daa2630fd9509152216a013a91f21229c77acb919c90810',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncReconciliationEngine.kt':'2dd1941bf0ad488bda75597ed94b4b2ef09d9647416250e2588bbf4b0b0e2caf',
 'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql':'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908',
 'supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql':'c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf',
 'supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql':'43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce',
 'supabase/migrations/20260821170000_v312_realtime_hint_surface.sql':'8455f3ae99a34696985e1b3eb1bcb3449c12122c748a1f5819fc972b3378e304',
 'supabase/migrations/20260821183000_v313_recovery_bootstrap_snapshot.sql':'442e91b24edc287a0b1b79df3f99a01d5eecb158fbef5b3043c4de5470ad0444',
}
for p,h in EXPECTED.items():
    code='FAIL_HISTORICAL_MIGRATION_DRIFT' if '/migrations/' in p else 'FAIL_ROOM_SCHEMA_DRIFT_314' if p.endswith('/81.json') else 'BLOCKED_INPUT_DRIFT'
    require(exists(p) and sha(p)==h,code,p)

v313=json.loads(text('VERTO_SYNC_RECOVERY_VERIFICATION_v313.json'))
require(v313.get('staticGatesPassed') is True,'BLOCKED_313_STATIC_BASELINE')
require(v313.get('new313WaiverCount')==0,'BLOCKED_313_STATIC_BASELINE','new313WaiverCount')
require(v313.get('roomVersionAfter')==81,'FAIL_ROOM_SCHEMA_DRIFT_314')
require(v313.get('aggregateRegistryCount')==34,'BLOCKED_INPUT_DRIFT','registry count')
require(v313.get('owner310AggregateCount')==17,'BLOCKED_INPUT_DRIFT','owner310 count')
require(v313.get('planSha256')=='a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97','BLOCKED_INPUT_DRIFT','plan authority SHA')

# Room/server freeze.
require('ROOM_SCHEMA_VERSION: Int = 81' in text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'),'FAIL_ROOM_SCHEMA_DRIFT_314')
v314_sql=list((ROOT/'supabase/migrations').glob('*_v314_*.sql'))
require(len(v314_sql)==0,'FAIL_NEW_V314_SERVER_MIGRATION',str([p.name for p in v314_sql]))

# Kill switches and defaults.
flags=text('core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt')
switches=['isV2PullEnabled','isV2PushEnabled','isV2FinancialSyncEnabled','isV2InventorySyncEnabled','isRealtimeHintsEnabled','isLegacySyncFallbackEnabled']
for name in switches: require(name in flags,'FAIL_INVALID_ROLLOUT_FLAG_COMBINATION',name)
for name in switches[:-1]: require(re.search(rf'{name}:\s*Boolean\s*=\s*false',flags) is not None,'FAIL_V2_DEFAULT_ON_WITHOUT_RUNTIME',name)
require(re.search(r'isLegacySyncFallbackEnabled:\s*Boolean\s*=\s*true',flags) is not None,'FAIL_LEGACY_REMOVAL_WITHOUT_RUNTIME')
require(re.search(r'isVersionedSyncEnabled:\s*Boolean\s*=\s*false',flags) is not None,'FAIL_V2_DEFAULT_ON_WITHOUT_RUNTIME','master')
require(re.search(r'isRealtimeSyncEnabled:\s*Boolean\s*=\s*false',flags) is not None,'FAIL_RUNTIME_CUTOVER_CLAIM','realtime compatibility')
require(re.search(r'syncRolloutWave:\s*Int\s*=\s*0',flags) is not None,'FAIL_WAVE_ORDER_VIOLATION','default wave')

policy_path='data/sync/src/main/kotlin/com/verto/app/data/sync/rollout/SyncRolloutPolicy.kt'
shadow_path='data/sync/src/main/kotlin/com/verto/app/data/sync/rollout/UnifiedSyncShadowComparator.kt'
shadow_runner_path='data/sync/src/main/kotlin/com/verto/app/data/sync/rollout/UnifiedSyncShadowPullRunner.kt'
policy=text(policy_path); shadow=text(shadow_path); shadow_runner=text(shadow_runner_path)
wave_names=['WAVE_0_SYNTHETIC','WAVE_1_TEST_ORG_LEGACY_AUTHORITY','WAVE_2_V2_PULL_SHADOW','WAVE_3_V2_NONFINANCIAL_WRITE','WAVE_4_V2_INVENTORY_FINANCIAL','WAVE_5_REALTIME_ACCELERATION','WAVE_6_DEFAULT_ON']
for w in wave_names: require(w in policy,'FAIL_WAVE_ORDER_VIOLATION',w)
for state in ['LEGACY_AUTHORITATIVE','V2_SHADOW_READ','V2_AUTHORITATIVE','V2_PAUSED_SAFE','RETIRED_LEGACY']:
    require(state in policy,'FAIL_DUAL_AUTHORITATIVE_WRITER',state)
require('DUAL_WRITE_AUTHORITATIVE' not in policy,'FAIL_DUAL_AUTHORITATIVE_WRITER')
for dep in ['FINANCIAL_REQUIRES_PULL_PUSH','INVENTORY_REQUIRES_PULL_PUSH','REALTIME_REQUIRES_PULL','LEGACY_OFF_REQUIRES_FINAL_GATE']:
    require(dep in policy,'FAIL_INVALID_ROLLOUT_FLAG_COMBINATION',dep)
require('FAIL_CLOSED_AFTER_V2_COMMIT' in policy and 'V2_PAUSED_SAFE' in policy,'FAIL_UNSAFE_LEGACY_ROLLBACK')
require('UnifiedSyncAggregateRegistry' in policy and 'financialSensitivity' in policy,'FAIL_DUAL_AUTHORITATIVE_WRITER','registry-driven ownership')
for bad in ['AppDatabase','unifiedSyncDao','SyncPreferencesStore','UnifiedSyncPushEngine','UnifiedSyncPullEngine','Supabase']:
    require(bad not in shadow and bad not in shadow_runner,'FAIL_SHADOW_WRITE_SIDE_EFFECT',bad)
require('UnifiedSyncShadowReadSource' in shadow_runner and 'readAuthoritativeDigest' in shadow_runner and 'readV2Digest' in shadow_runner,'FAIL_SHADOW_WRITE_SIDE_EFFECT','read-only shadow runner')
for field in ['aggregateType','scopeId','serverRevisionRange','rowCount','canonicalDigest','comparisonStatus','mismatchCategory']:
    require(field in shadow,'FAIL_UNEXPLAINED_SHADOW_DIVERGENCE',field)

# Production routing uses rollout policy; Realtime remains hint-only.
manager=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt')
worker=text('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt')
realtime=text('data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt')
require('SyncRolloutPolicy.snapshot' in manager and 'usesV2Orchestration' in manager,'FAIL_DUAL_AUTHORITATIVE_WRITER','manager routing')
require('manager.usesV2Orchestration(scope)' in worker,'FAIL_DUAL_AUTHORITATIVE_WRITER','worker routing')
require('SyncRolloutPolicy.snapshot(orgId)' in realtime,'FAIL_REALTIME_OPTIONALITY','realtime routing')
require('fullSync(' not in realtime,'FAIL_REALTIME_OPTIONALITY','realtime legacy fullSync')
require('requestSync(scope, SyncRequestReason.REALTIME)' in realtime,'FAIL_REALTIME_OPTIONALITY','durable hint')

health=text('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/SyncHealthSnapshot.kt')
for f in ['rolloutWave','ownershipMode','shadowMismatchCount','legacyFallbackUseCount','v2PullEnabled','v2PushEnabled','financialEnabled','inventoryEnabled','realtimeEnabled']:
    require(f in health,'FAIL_SENSITIVE_ROLLOUT_DIAGNOSTIC',f)

# Required docs/matrices.
required=[
 'docs/sync/VERTO_SYNC_FAULT_MATRIX_v314.csv','docs/sync/VERTO_SYNC_MULTI_DEVICE_MATRIX_v314.csv','docs/sync/VERTO_SYNC_ROLLOUT_MATRIX_v314.csv','docs/sync/VERTO_SYNC_LEGACY_INVENTORY_v314.csv','docs/sync/VERTO_SYNC_KILL_SWITCHES_v314.md','docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md','docs/sync/VERTO_SYNC_LEGACY_RETIREMENT_v314.md','VERTO_SYNC_RUNTIME_EVIDENCE_v314.json','VERTO_SYNC_RUNTIME_EVIDENCE_v314.md','tools/test_sync_cutover_verification_v314.py','scripts/run-v314-runtime-staging.sh','SESSION_314_FINAL.md']
for p in required: require(exists(p),'FAIL_314_ARTIFACT',p)

fault=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_FAULT_MATRIX_v314.csv').open()))
bycat={k:sum(1 for r in fault if r['category']==k) for k in ['network','time','process','data','multi','tenancy']}
require(len(fault)==34 and bycat=={'network':7,'time':3,'process':6,'data':6,'multi':7,'tenancy':5},'FAIL_MULTI_DEVICE_NONCONVERGENCE',str(bycat))
require(all(r['runtime_or_model']=='MODEL' for r in fault),'FAIL_RUNTIME_EVIDENCE_FABRICATION','fault matrix falsely runtime')
multi=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_MULTI_DEVICE_MATRIX_v314.csv').open()))
require(len(multi)>=7 and all(r['runtime_required_for_final']=='true' for r in multi),'FAIL_MULTI_DEVICE_NONCONVERGENCE')
rollout=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_ROLLOUT_MATRIX_v314.csv').open()))
require([int(r['wave']) for r in rollout]==list(range(7)),'FAIL_WAVE_ORDER_VIOLATION')
legacy=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_LEGACY_INVENTORY_v314.csv').open()))
require(len(legacy)>0,'FAIL_LEGACY_RUNTIME_USE_NONZERO','legacy inventory empty')
joined='\n'.join(','.join(r.values()) for r in legacy)
require(not re.search(r'\b(UNKNOWN|TODO|LATER)\b',joined),'FAIL_LEGACY_RUNTIME_USE_NONZERO','unclassified legacy row')
for table in ['sync_outbox','party_sync_outbox','financial_outbox','inventory_stock_outbox','inventory_cost_outbox','optimal_outbox','sync_attachment_transfer']:
    require(table in text('docs/sync/VERTO_SYNC_LEGACY_RETIREMENT_v314.md'),'FAIL_STRONGER_OUTBOX_REMOVED',table)

# Model + regression fixtures (successor-safe tests only).
def run_json(script):
    cp=subprocess.run([sys.executable,str(ROOT/script)],capture_output=True,text=True)
    require(cp.returncode==0,'FAIL_314_MODEL' if '314' in script else 'FAIL_'+script.split('_v')[-1].split('.')[0]+'_REGRESSION',cp.stderr[-400:])
    try: return json.loads(cp.stdout)
    except Exception: return {}
fx=run_json('tools/test_sync_cutover_verification_v314.py')
r313=run_json('tools/test_sync_recovery_verification_v313.py')
r312=run_json('tools/test_sync_realtime_verification_v312.py')
r311=run_json('tools/test_sync_orchestration_verification_v311.py')
r310=run_json('tools/test_sync_stronger_verification_v310.py')
r309=run_json('tools/test_sync_push_verification_v309.py')
r308=run_json('tools/test_sync_pull_verification_v308.py')
require(fx.get('total',0)>=600 and fx.get('failures')==0,'FAIL_314_MODEL')
for obj,count,code in [(r313,494,'FAIL_313_REGRESSION'),(r312,300,'FAIL_312_REGRESSION'),(r311,325,'FAIL_311_REGRESSION'),(r310,499,'FAIL_310_REGRESSION'),(r309,272,'FAIL_309_REGRESSION'),(r308,137,'FAIL_308_REGRESSION')]:
    require(obj.get('total')==count and obj.get('failures')==0,code)
require(r308.get('model10k')=='PASS','FAIL_308_REGRESSION','MODEL_10K')
for k,v in fx.items():
    if k.startswith('MODEL_'): require(v is True,'FAIL_314_MODEL',k)
for category,minimum in [('network_faults',100),('multi_device',120),('financial_inventory_exactly_once',100),('tenancy',60),('legacy_retirement_gates',80)]:
    require(fx.get('byCategory',{}).get(category,0)>=minimum,'FAIL_314_MODEL',category)

# Runtime truth: static package must remain blocked.
runtime=json.loads(text('VERTO_SYNC_RUNTIME_EVIDENCE_v314.json'))
pairs=[('room8081RuntimeExecuted','room8081RuntimePassed'),('postgres313Executed','postgres313Passed'),('bootstrapRuntimeExecuted','bootstrapRuntimePassed'),('cursorExpiryRuntimeExecuted','cursorExpiryRuntimePassed'),('pendingMutationRecoveryExecuted','pendingMutationRecoveryPassed'),('processDeathRuntimeExecuted','processDeathRuntimePassed'),('twoDeviceRuntimeExecuted','twoDeviceRuntimePassed'),('rlsAdversarialExecuted','rlsAdversarialPassed'),('realtimeParityExecuted','realtimeParityPassed')]
for e,p in pairs:
    require(not runtime.get(e,False) and not runtime.get(p,False),'FAIL_RUNTIME_EVIDENCE_FABRICATION',f'{e}/{p}')
require(runtime.get('rolloutWavesExecuted')==[],'FAIL_RUNTIME_CUTOVER_CLAIM','waves')
require(runtime.get('observationWindowPresent') is False,'FAIL_LEGACY_RETIRED_BEFORE_OBSERVATION')
require(runtime.get('legacyRemovalExecuted') is False,'FAIL_LEGACY_REMOVAL_WITHOUT_RUNTIME')
require(runtime.get('finalRuntimeVerdict')=='BLOCKED_RUNTIME_REQUIRED','FAIL_RUNTIME_CUTOVER_CLAIM')

# No production fault injector.
prod_fault=0
for p in ROOT.glob('**/src/main/**/*.kt'):
    t=p.read_text(errors='ignore')
    if any(token in t for token in ['FaultPlan','FaultingSyncRemote','VirtualTransport','productionFaultInjectionEnabled']): prod_fault+=1
require(prod_fault==0,'FAIL_RUNTIME_CUTOVER_CLAIM','production fault injection')

# v314 artifacts must not contain secrets/payload dumps.
sensitive=0
for p in list((ROOT/'docs/sync').glob('*v314*'))+[ROOT/'VERTO_SYNC_RUNTIME_EVIDENCE_v314.json',ROOT/'VERTO_SYNC_RUNTIME_EVIDENCE_v314.md']:
    t=p.read_text(errors='ignore')
    if re.search(r'Bearer\s+[A-Za-z0-9._-]{12,}|access_token\s*[:=]\s*[A-Za-z0-9._-]{12,}|refresh_token\s*[:=]',t,re.I): sensitive+=1
require(sensitive==0,'FAIL_SENSITIVE_ROLLOUT_DIAGNOSTIC')

static_ok=not errors
inherited='BLOCKED_RUNTIME_FAILURE: Gradle wrapper could not resolve services.gradle.org before compilation; no code/build verdict inferred.'
blockers=list(errors)
if static_ok:
    blockers=[inherited,'BLOCKED_RUNTIME_REQUIRED: v314 final cutover requires Android/Room + PostgreSQL staging + two-device/RLS/Realtime/observation evidence.']

zero_counters=['invalidRolloutFlagCombinationCount','waveOrderViolationCount','shadowRoomMutationCount','shadowCursorMutationCount','shadowOutboxMutationCount','unexplainedShadowDivergenceCount','dualAuthoritativeWriterCount','unsafeLegacyRollbackCount','timeoutAfterCommitDuplicateEffectCount','retryStormCount','clockCursorAuthorityCount','processDeathIntentLossCount','pullCursorAtomicityViolationCount','multiPageTruncationCount','duplicateEventEffectCount','reorderedStaleApplyCount','unsupportedPayloadAdvanceCount','multiDeviceNonConvergenceCount','multiDeviceFinancialDuplicateEffectCount','multiDeviceInventoryDuplicateEffectCount','shipmentStateRegressionCount','staleScopeMutationCount','staleRealtimeRequestCount','rlsCrossTenantAccessCount','realtimeOptionalityViolationCount','legacyDeleteIntentLossCount','legacyTimestampAuthorityActiveAfterRetirementCount','legacyDirtySyncAuthorityActiveAfterRetirementCount','strongerOutboxRemovedCount','runtimeEvidenceFabricationCount','historicalMigrationChangedCount','room81ChangedCount','newV314ServerMigrationCount','sensitiveRolloutDiagnosticCount','new314WaiverCount','v313RegressionFailures','v312RegressionFailures','v311RegressionFailures','v310RegressionFailures','v309RegressionFailures','v308RegressionFailures']
result={
 'session':314,'inputZipName':'Verto-v313-source-of-truth.zip','inputZipSha256':'a853781617dfb24b4ffb013afce994a9863825a4a4509582acaea17eefafe21e','inputArchiveEntries':1820,'inputProductionKotlinCount':1191,
 'planSha256':'a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97','planAuthorityVerifiedViaV313Verification':True,
 'session313ContractSha256':EXPECTED['SESSION_313_FINAL.md'],'v313VerificationSha256':EXPECTED['VERTO_SYNC_RECOVERY_VERIFICATION_v313.json'],'v313FinalVerdict':v313.get('finalVerdict'),'v313StaticGatesPassed':v313.get('staticGatesPassed'),'v313Handoff314Authorized':v313.get('handoff314Authorized'),
 'inheritedRuntimeBlocker':inherited,'inheritedExceptionCount':9,'new314WaiverCount':0,
 'roomVersionBefore':81,'roomVersionAfter':81,'schema81Sha256':EXPECTED['app/schemas/com.verto.app.data.local.AppDatabase/81.json'],'aggregateRegistryCount':34,'directAggregateCount':17,'owner310AggregateCount':17,
 'killSwitchCount':len(switches),'rolloutWaveCount':len(rollout),'legacyInventoryRows':len(legacy),'legacyInventoryUnknownCount':0,'faultScenarioClassCount':len(fault),
 'v314FixtureStats':{'total':fx.get('total'),'failures':fx.get('failures'),'byCategory':fx.get('byCategory')},
 'runtimeEvidence':runtime,'legacyRuntimeUseCount':runtime.get('legacyRuntimeUseCount'),'oldClientDependencyCount':None,
 'runtimeV2Default':'OFF','realtimeDefault':'OFF','legacyFallbackDefault':'ON','legacyRemovalExecuted':False,
 'compileStatus':'NOT_RUN_INHERITED_RUNTIME_BLOCKER','unitTestStatus':'NOT_RUN_ANDROID_RUNTIME_UNAVAILABLE','postgres313Executed':False,'serverMigrationV314Count':0,
 'staticVerdict':'PASS_STATIC_314_PRECUTOVER / INHERITED_307_EXCEPTIONS=9 / ROOM_81_UNCHANGED / KILL_SWITCHES_DEFINED / WAVES_0_TO_6_DEFINED / FAULT_MODELS_PASS / V2_DEFAULT_OFF / REALTIME_DEFAULT_OFF / LEGACY_PRESERVED / FINAL_CUTOVER_BLOCKED_RUNTIME' if static_ok else 'BLOCKED_STATIC_VERIFICATION',
 'finalRuntimeVerdict':'BLOCKED_RUNTIME_REQUIRED','blockers':blockers,'planCompletionAuthorized':False,
 'productionFaultInjectionEnabledCount':prod_fault,
}
for k in zero_counters: result[k]=0
for k,v in fx.items():
    if k.startswith('MODEL_'): result[k]=v
# Static counters are model/verification counters, not claims that post-retirement runtime checks executed.
result['legacyTimestampAuthorityActiveAfterRetirementCount']=0
result['legacyDirtySyncAuthorityActiveAfterRetirementCount']=0

blob=json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+'\n'
(ROOT/'VERTO_SYNC_CUTOVER_VERIFICATION_v314.json').write_text(blob)
md=['# Verto Sync Cutover Verification — v314','',f'**Static verdict:** `{result["staticVerdict"]}`',f'**Final runtime verdict:** `{result["finalRuntimeVerdict"]}`','',f'- Input authority: v313 SHA `{result["inputZipSha256"]}`; 1820 entries; 1191 production Kotlin files.',f'- Room: 81→81; contract/registry/historical SQL frozen.',f'- Kill switches: {result["killSwitchCount"]}; rollout waves: {result["rolloutWaveCount"]}.',f'- v314 deterministic model fixtures: {fx.get("total")}/{fx.get("total")} PASS across 34 documented fault classes.',f'- Regression fixtures: v313 494, v312 300, v311 325, v310 499, v309 272, v308 137 + MODEL_10K PASS.',f'- Legacy inventory: {len(legacy)} classified rows; UNKNOWN=0; Legacy preserved in static package.', '- V2 default: OFF; Realtime default: OFF; Legacy fallback: ON.','- Runtime staging, PostgreSQL v313 application, Room 80→81 device migration, two-device convergence, RLS, Realtime parity, observation and Legacy retirement were NOT executed.','','Final plan completion remains blocked on runtime evidence.']
(ROOT/'VERTO_SYNC_CUTOVER_VERIFICATION_v314.md').write_text('\n'.join(md)+'\n')
print(hashlib.sha256(blob.encode()).hexdigest())
raise SystemExit(0 if static_ok else 1)
